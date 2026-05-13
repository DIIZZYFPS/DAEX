# DaexLlama JNI Bridge — Safety Audit

**File:** `daex_llama_bridge.cpp` (655 lines)
**llama.cpp version:** gguf-v0.19.0-79-gdded58b45 (v2+)
**Date:** 2026-05-12

---

## CRITICAL ISSUES

### 1. Double-free / Use-after-free in nativeUnloadModel

**File:** `daex_llama_bridge.cpp` lines 539-547

```cpp
// nativeUnloadModel (line 539-547)
std::lock_guard<std::mutex> lock(g_contexts_mutex);
auto it = g_contexts.find(ctx_id);
if (it != g_contexts.end()) {
    LOGI("Unloading model from context %d", ctx_id);
    it->second.reset();  // DESTROYS entire LlamaContext (model, ctx, sampler, batch, chat_templates)
}
```

**Problem:** `it->second.reset()` destroys the entire `LlamaContext` object via `unique_ptr::reset()`. The destructor runs `llama_free(ctx)`, `llama_model_free(model)`, `common_sampler_free(sampler)`, `chat_templates.reset()`, and `llama_batch_free(batch)`. But the context ID remains in `g_contexts` map. When `nativeDestroyContext` is later called (line 188-195), it finds the ID in the map and calls `g_contexts.erase(it)` — the unique_ptr is already destroyed, so `erase` attempts to destroy it again (double-free). Even if erase doesn't double-free (unique_ptr already null after reset), subsequent JNI calls like `nativeLoadModel` or `nativePrepareContext` will call `get_context()` which returns a dangling pointer to freed memory.

Additionally, the Kotlin side calls `destroyContext()` after `unloadModel()` in the destroy flow, guaranteeing this path is hit.

**Fix:** Either:
- (A) Remove the entry from the map in `nativeUnloadModel`: `g_contexts.erase(it);`
- (B) Don't destroy the context — only free model+context+batch, leave the LlamaContext object alive so `nativeLoadModel` can reuse it. This requires restructuring the destructor to not run on partial cleanup.

Option (A) is simpler and matches the semantic of "unload = destroy this context entirely."

---

### 2. State machine inconsistency — unloadModel sets wrong state

**File:** `DaexLlamaEngineImpl.kt` line 242

```kotlin
override fun unloadModel(ctxId: Int) {
    nativeUnloadModel(ctxId)
    _state.update { DaexLlamaEngine.EngineState.ModelReady }  // WRONG
}
```

**Problem:** After unloading the model, the state is set to `ModelReady` instead of an unloaded state. If the interface has an `Unloaded` or `Error` state, this is misleading. If not, the state machine is incomplete — there's no way to distinguish "model loaded" from "model unloaded" after `unloadModel()` is called.

**Fix:** Add an `Unloaded` state to `EngineState` enum and set it here. Or if model re-loading is expected, keep the state as `ModelReady` but ensure the native layer can handle re-loading into a destroyed context (which currently it cannot — see Issue 1).

---

## HIGH PRIORITY ISSUES

### 3. Null pointer dereference in nativeResetConversation

**File:** `daex_llama_bridge.cpp` lines 496-509

```cpp
auto* ctx = get_context(env, ctx_id);
if (!ctx) return;  // NULL CHECK — BUT...

llama_memory_clear(llama_get_memory(ctx->ctx), false);  // ctx->ctx may be null!
```

**Problem:** The null check only verifies `ctx` (the LlamaContext struct pointer), not `ctx->ctx` (the `llama_context*`). If `nativeResetConversation` is called before `nativePrepareContext` (which is where `ctx->ctx` is initialized at line 250), `llama_get_memory(nullptr)` dereferences a null pointer and crashes. Same applies to the destructor at line 72 — `llama_free(ctx)` is called without checking if `ctx` is non-null... wait, it IS checked. But `nativeResetConversation` does NOT check `ctx->ctx`.

**Fix:** Add `if (!ctx->ctx) return;` after the ctx null check in `nativeResetConversation`.

---

### 4. Null pointer dereference in chat_add_and_format

**File:** `daex_llama_bridge.cpp` lines 326-341

```cpp
bool has_explicit = common_chat_templates_was_explicit(ctx->chat_templates.get());
```

**Problem:** `ctx->chat_templates` is initialized to `nullptr` (line 42) and only set in `nativePrepareContext` (line 257). If `common_chat_templates_init` fails, `chat_templates` remains `nullptr`. `common_chat_templates_was_explicit(nullptr)` dereferences null. The Kotlin side calls `processSystemPrompt` and `processUserPrompt` which both call `chat_add_and_format`, and there's no guarantee `prepareContext` succeeded.

**Fix:** Add null check: `if (!ctx->chat_templates) has_explicit = false;`

---

### 5. Null pointer dereference in nativeGetEmbedding

**File:** `daex_llama_bridge.cpp` lines 597-638

```cpp
auto* ctx = get_context(env, ctx_id);
if (!ctx || !ctx->ctx) return nullptr;  // checks ctx and ctx->ctx...

int n_embd = llama_model_n_embd(ctx->model);  // ... but NOT ctx->model!
```

**Problem:** The null check verifies `ctx` and `ctx->ctx`, but not `ctx->model`. If `nativeLoadEmbeddingModel` fails to load the model (line 564 returns null), the function returns -1. But if `nativeUnloadModel` was called (destroying the model), `ctx->model` is null. Subsequent `nativeGetEmbedding` calls dereference `ctx->model` at line 633.

**Fix:** Add `ctx->model` to the null check: `if (!ctx || !ctx->ctx || !ctx->model) return nullptr;`

---

### 6. Data race on cancel_generation flag

**File:** `daex_llama_bridge.cpp` lines 55, 439, 487

```cpp
bool cancel_generation = false;  // line 55 — no atomic, no mutex

// Line 439 — read in generation loop (JNI thread)
if (ctx->cancel_generation || ctx->current_position >= ctx->gen_stop_position) {

// Line 487 — write from nativeCancelGeneration (potentially different thread)
if (ctx) ctx->cancel_generation = true;
```

**Problem:** `cancel_generation` is a plain `bool` accessed from two threads without synchronization:
- The generation loop in `nativeGenerateNextToken` reads it
- `nativeCancelGeneration` writes to it

On ARM64 (Android), this is a data race per the C++ standard. The compiler may cache the value in a register, meaning the flag may never be observed as changed by the generation loop. The Kotlin side also calls `cancelGeneration` from coroutine context while the generation loop runs on `nativeDispatcher` — these are different threads.

**Fix:** Change to `std::atomic<bool> cancel_generation{false};` and use atomic load/store. Alternatively, use a `std::mutex` or `std::atomic_thread_fence`.

---

### 7. Sampler not re-initialized after context reuse

**File:** `daex_llama_bridge.cpp` line 264

```cpp
ctx->sampler = common_sampler_init(ctx->model, sparams);
```

**Problem:** If `nativeUnloadModel` is fixed to NOT destroy the full context (option B above), the sampler is freed in the destructor path or by `nativeUnloadModel`. After re-loading a model via `nativeLoadModel`, `nativePrepareContext` re-initializes the sampler. But if `nativeResetConversation` is called between unload and reload, the sampler is already freed. There's no guard against calling `common_sampler_sample` on a null sampler.

**Fix:** Add `if (!ctx->sampler) return nullptr;` at the start of `nativeGenerateNextToken`.

---

## MEDIUM PRIORITY ISSUES

### 8. Batch double-free potential in destructor vs nativePrepareContext

**File:** `daex_llama_bridge.cpp` lines 64-74, 256

```cpp
// Constructor (line 64-66)
LlamaContext(int ctx_id) : id(ctx_id) {
    batch = {};  // zero-initialized
}

// nativePrepareContext (line 256)
ctx->batch = llama_batch_init(ctx->n_batch, 0, 1);  // overwrites with new allocation

// Destructor (line 68-74)
~LlamaContext() {
    llama_batch_free(batch);  // frees whatever batch points to
}
```

**Problem:** If `nativePrepareContext` is called multiple times (e.g., after a failed `llama_decode`), `llama_batch_init` overwrites `ctx->batch` without freeing the old batch first. This leaks the previous batch allocation. The destructor only frees the last batch.

**Fix:** Free the existing batch before re-initializing:
```cpp
llama_batch_free(ctx->batch);
ctx->batch = llama_batch_init(ctx->n_batch, 0, 1);
```

---

### 9. No validation of ctx_id bounds

**File:** `daex_llama_bridge.cpp` line 118-126

```cpp
static LlamaContext* get_context(JNIEnv* env, jint ctx_id) {
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    auto it = g_contexts.find(ctx_id);
    if (it == g_contexts.end()) {
        LOGE("Context %d not found", ctx_id);
        return nullptr;
    }
    return it->second.get();
}
```

**Problem:** `get_context` returns `nullptr` on failure, and most callers check for this. However, the Kotlin side wraps JNI calls in `withContext(nativeDispatcher)` + `nativeMutex.withLock`, so the mutex is held at the Kotlin level too. This creates a nested lock: Kotlin mutex → JNI call → `get_context` acquires `g_contexts_mutex`. This is safe from deadlocks (different mutexes, always acquired in same order), but it means `nativeCreateContext` and `nativeDestroyContext` acquire `g_contexts_mutex` while the Kotlin mutex is already held. Any other JNI call that acquires `g_contexts_mutex` while Kotlin mutex is held would deadlock. Currently this doesn't happen because `nativeCreateContext`/`nativeDestroyContext` are not called inside `nativeMutex.withLock`, but it's a latent risk.

**Fix:** Document the lock ordering. Consider removing the mutex from `nativeCreateContext`/`nativeDestroyContext` if the Kotlin layer guarantees single-threaded access.

---

### 10. llama_batch_free in destructor may free uninitialized batch

**File:** `daex_llama_bridge.cpp` line 71

```cpp
llama_batch_free(batch);
```

**Problem:** The constructor initializes `batch = {}` (line 65), which zero-initializes the struct. `llama_batch_free` on a zero-initialized batch should be safe (it checks for null n_tokens), but this is implementation-dependent. If llama.cpp's `llama_batch_free` doesn't handle zero-initialized batches, this crashes on context creation failure before `llama_batch_init` is called.

**Fix:** Verify that `llama_batch_free` handles zero-initialized batches, or add a guard:
```cpp
if (batch.n_tokens > 0) llama_batch_free(batch);
```

---

## LOW PRIORITY / COSMETIC ISSUES

### 11. Magic numbers throughout

**File:** `daex_llama_bridge.cpp` multiple locations

```cpp
ctx->n_ctx - 4    // line 297, 410
ctx->n_ctx = 8192 // line 58
ctx->n_batch = 512 // line 59
ctx->n_predict = 1024 // line 62
```

**Problem:** Magic numbers for context size, batch size, prediction limit, and the `-4` offset for overflow safety are hardcoded. These should be named constants.

**Fix:** Define `constexpr` constants at file scope.

---

### 12. StringUTFChars not released on early returns

**File:** `daex_llama_bridge.cpp` lines 208-213, 362-369, etc.

```cpp
const auto* model_path = env->GetStringUTFChars(jmodel_path, nullptr);
// ...
if (!ctx->model) {
    LOGE("Failed to load model from: %s", model_path);
    return -1;  // ReleaseStringUTFChars NOT called!
}
env->ReleaseStringUTFChars(jmodel_path, model_path);
```

**Problem:** In `nativeLoadModel` (line 213), the `ReleaseStringUTFChars` call is after the null check but before the return. Actually, looking more carefully:

```cpp
env->ReleaseStringUTFChars(jmodel_path, model_path);  // line 213 — BEFORE null check

if (!ctx->model) {  // line 215
    LOGE("Failed to load model from: %s", model_path);  // model_path is dangling!
    return -1;
}
```

Wait — `ReleaseStringUTFChars` is called at line 213, BEFORE the null check at line 215. So `model_path` is released but then used in `LOGE` at line 216. This is a use-after-release of the JNI string pointer.

**Fix:** Move `ReleaseStringUTFChars` to after all uses of `model_path`, or copy the string to a `std::string` first.

Same pattern appears in `nativeLoadEmbeddingModel` (lines 560-569).

---

### 13. Missing JNI exception handling

**File:** `daex_llama_bridge.cpp` lines 467-474

```cpp
jstring result = nullptr;
if (is_valid_utf8(ctx->cached_token_chars.c_str())) {
    result = env->NewStringUTF(ctx->cached_token_chars.c_str());
    // ...
}
return result;
```

**Problem:** `env->NewStringUTF` can throw a `OutOfMemoryError` if the heap is exhausted. If this happens inside the JNI bridge without being caught, it causes a native crash rather than a graceful Kotlin exception.

**Fix:** Wrap `NewStringUTF` calls in try/catch and check `env->ExceptionCheck()`.

---

## RECOMMENDATIONS (Priority Order)

1. **Fix double-free in nativeUnloadModel** (Issue 1) — Causes crashes on context destroy after unload. Must fix before merge.
2. **Add null check for ctx->ctx in nativeResetConversation** (Issue 3) — Crash vector if reset called before prepare.
3. **Add null check for chat_templates in chat_add_and_format** (Issue 4) — Crash vector with failed template init.
4. **Add ctx->model null check in nativeGetEmbedding** (Issue 5) — Crash vector after unload.
5. **Make cancel_generation atomic** (Issue 6) — Data race, silent failure on ARM64.
6. **Fix StringUTFChars use-after-release** (Issue 12) — Undefined behavior, potential crash.
7. **Add batch free before re-init in nativePrepareContext** (Issue 8) — Memory leak.
8. **Guard sampler null check in nativeGenerateNextToken** (Issue 7) — Crash vector.
9. **Add batch free guard in destructor** (Issue 10) — Potential crash on failed init.
10. **Add JNI OOM exception handling** (Issue 13) — Graceful degradation.
11. **Replace magic numbers with named constants** (Issue 11) — Maintainability.
12. **Fix state machine after unload** (Issue 2) — Correctness.
13. **Document lock ordering** (Issue 9) — Prevent future deadlocks.
