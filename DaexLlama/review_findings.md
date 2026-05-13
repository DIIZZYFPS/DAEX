# DaexLlama JNI Bridge — Code Review Findings

## Architecture Overview

The bridge (`daex_llama_bridge.cpp`) implements a stateful inference engine with:
- **Model loading** via `llama_model_load_from_file()` (deprecated, should use `llama_model_init_from_file()`)
- **Context management** with a single global context, batch, and sampler
- **Chat template** support via `common_chat_templates_init()` with Jinja
- **Tokenization** via `common_tokenize()` (context-based overload)
- **Inference** via `llama_decode()` in batched loops
- **Sampling** via `common_sampler` API
- **Context shifting** for overflow (removes half of non-system tokens)

llama.cpp version: b9124-1-gdded58b45 (very recent, ~May 2026)

---

## CRITICAL ISSUES

### 1. JNI function names don't match any Java interface
**File:** `daex_llama_bridge.cpp` lines 49-50, 72-73, 94-95, 119-120, 143-144, 168-169, 193-194, 218-219, 243-244, 268-269, 293-294

All JNI function names use the pattern `Java_com_arm_aichat_internal_InferenceEngineImpl_*`.

**Problem:** There is no Java file `com/arm/aichat/internal/InferenceEngineImpl.java` in the project. The Android app (DaexAndroid) references this JNI library but the Java interface is missing or uses a different package path.

**Impact:** The bridge will not be callable from the Android app. The JNI function names must match the actual Java class and package.

**Fix:** Either:
- Create the Java interface at `src/main/java/com/arm/aichat/internal/InferenceEngineImpl.java` with matching `native` method declarations
- Or update the JNI function names to match the actual Java class path used by DaexAndroid

### 2. Deprecated API: `llama_model_load_from_file()`
**File:** `daex_llama_bridge.cpp` line 68

```cpp
auto *model = llama_model_load_from_file(model_path, model_params);
```

**Upstream:** llama.h line 529 marks this as DEPRECATED.

**Fix:** Replace with `llama_model_init_from_file(model_path, model_params)`.

### 3. Deprecated API: `llama_n_ctx()`
**File:** `daex_llama_bridge.cpp` line 137

```cpp
int context_size = llama_n_ctx(context);
```

**Upstream:** llama.h line 533 marks this as DEPRECATED.

**Fix:** Replace with `llama_context_n_ctx(context)`.

### 4. Deprecated API: `llama_token_is_eog()`
**File:** `daex_llama_bridge.cpp` line 246

```cpp
if (!llama_token_is_eog(llama_model_get_vocab(model_), new_token_id)) {
```

**Upstream:** llama.h line 1080 marks this as DEPRECATED.

**Fix:** Replace with `llama_vocab_is_eog(llama_model_get_vocab(model_), new_token_id)`.

### 5. Memory leak: `llama_batch_init()` size mismatch
**File:** `daex_llama_bridge.cpp` line 100

```cpp
batch_ = llama_batch_init(64, 0, 1);
```

The batch is initialized with size 64, but `common_batch_add()` will grow the batch internally. However, the batch size (64) is much smaller than the context (2048). When processing prompts longer than 64 tokens, the batch needs to be replenished multiple times. The code does handle this correctly in `processPrompt()` by looping, but the initial capacity is very small.

**Recommendation:** Increase initial batch capacity to match the context size or at least the max prompt size (e.g., 2048).

### 6. Missing `llama_backend_init()` and `llama_backend_free()`
**File:** `daex_llama_bridge.cpp`

- `init_engine()` does NOT call `llama_backend_init()`
- `cleanup_engine()` does NOT call `llama_backend_free()`

**Upstream:** The official `ai_chat.cpp` calls `llama_backend_init()` after loading backends and `llama_backend_free()` in shutdown.

**Impact:** Missing backend initialization can cause issues with thread pools, NUMA awareness, and backend registration.

**Fix:** Add `llama_backend_init()` to `init_engine()` and `llama_backend_free()` to `cleanup_engine()`.

---

## HIGH PRIORITY ISSUES

### 7. No error handling for `common_chat_templates_init()`
**File:** `daex_llama_bridge.cpp` line 106

```cpp
chat_templates_ = common_chat_templates_init(model_, "");
```

The return value is a `common_chat_templates_ptr` (unique_ptr with deleter). If initialization fails, the pointer will be nullptr but there's no check. The `common_chat_templates_init()` can return nullptr on failure.

**Fix:** Check if `chat_templates_` is null after initialization and handle gracefully.

### 8. No error handling for `common_sampler_init()`
**File:** `daex_llama_bridge.cpp` line 109

```cpp
sampler_ = common_sampler_init(model_, sampling_params);
```

If `common_sampler_init()` returns nullptr (possible if model vocab is invalid), subsequent calls to `common_sampler_sample()` will crash.

**Fix:** Check for nullptr and return error code to Java.

### 9. No error handling for `llama_model_load_from_file()`
**File:** `daex_llama_bridge.cpp` line 68

The return value is stored in `model_` but never checked for nullptr before use. If the model file doesn't exist or is corrupt, `model_` will be nullptr and all subsequent calls will crash.

**Fix:** Check for nullptr after loading.

### 10. Context shifting is incomplete
**File:** `daex_llama_bridge.cpp` lines 215-226

The `shift_context()` function only removes tokens and adjusts positions. It does NOT re-compute the logits for the shifted context. After removing tokens from the KV cache, the model needs to re-process the remaining tokens to update the KV cache state.

**Compare with official ai_chat.cpp:** The official example also uses `shift_context()` without re-computation — but this works because the KV cache is position-based and llama.cpp handles position shifts via `llama_memory_seq_add()`. However, the approach is a heuristic that may produce degraded quality for long contexts.

**Recommendation:** Document this limitation. Consider implementing a proper sliding window or NTK-aware scaling.

### 11. `processPrompt()` doesn't handle system prompts separately
**File:** `daex_llama_bridge.cpp` lines 170-191

The official `ai_chat.cpp` has separate `processSystemPrompt()` and `processUserPrompt()` functions that track `system_prompt_position` for context shifting. This bridge treats all prompts uniformly.

**Impact:** When context shifting occurs, system prompt tokens may be discarded, breaking the conversation context.

**Fix:** Add separate system prompt handling with position tracking.

---

## MEDIUM PRIORITY ISSUES

### 12. No thread safety
**File:** `daex_llama_bridge.cpp`

All state is global/static. If Java calls inference methods from multiple threads (e.g., UI thread + background thread), there will be data races on `model_`, `context_`, `batch_`, `sampler_`, `current_position_`, `chat_history_`, etc.

**Fix:** Add mutex protection or ensure single-threaded access from Java.

### 13. No support for `n_predict` parameter
**File:** `daex_llama_bridge.cpp` line 197

The `generateToken()` method has a hardcoded `max_tokens_ = 256`. The Java interface should expose this as a configurable parameter.

### 14. Sampling parameters are hardcoded
**File:** `daex_llama_bridge.cpp` lines 97-99

Temperature, top_k, top_p, etc. are hardcoded. The Java interface should allow configuration.

### 15. No support for multiple sequences
**File:** `daex_llama_bridge.cpp` line 186

```cpp
common_batch_add(batch, token_id, current_position + i, {0}, want_logit);
```

Always uses seq_id=0. No support for concurrent conversations.

### 16. UTF-8 validation is incomplete
**File:** `daex_llama_bridge.cpp` lines 239-254

The `is_valid_utf8()` function checks for valid UTF-8 byte sequences but does NOT handle the case where `cached_token_chars` contains partial multi-byte sequences that only complete on the next token. The current caching approach handles this, but the validation function itself is overly strict — it returns `false` for any invalid byte, which could cause silent token drops.

### 17. No logging on critical errors
**File:** `daex_llama_bridge.cpp`

Several error paths (model load failure, context init failure, sampler init failure) just set internal state to nullptr without logging. The `__android_log_print` macros are defined but not used consistently.

---

## LOW PRIORITY / COSMETIC ISSUES

### 18. Magic numbers
**File:** `daex_llama_bridge.cpp`

Multiple hardcoded values: `2048` (context), `64` (batch), `256` (max tokens), `128` (overflow headroom). These should be `constexpr` or configurable.

### 19. No model info exposure
**File:** `daex_llama_bridge.cpp`

The bridge doesn't expose model metadata (n_ctx_train, n_embd, n_params, model description) to Java. The official `ai_chat.cpp` has `systemInfo()` and `benchModel()` JNI methods for this.

### 20. `cleanup_engine()` doesn't free batch
**File:** `daex_llama_bridge.cpp` lines 268-274

The cleanup function calls `llama_free(context_)` and `llama_model_free(model_)` but does NOT call `llama_batch_free(batch_)` or `common_sampler_free(sampler_)`.

**Fix:** Add `llama_batch_free(batch_)` and `common_sampler_free(sampler_)` before freeing context.

### 21. No support for grammar/JSON mode
**File:** `daex_llama_bridge.cpp`

The official `ai_chat.cpp` supports grammar constraining via `common_chat_params`. This bridge has no way to set grammar constraints for structured output.

### 22. No support for embeddings
**File:** `daex_llama_bridge.cpp`

The `llama.cpp` API supports `llama_get_embeddings_seq()`. This bridge has no embedding extraction capability.

### 23. `common_tokenize` with `add_special=true` for system prompt
**File:** `daex_llama_bridge.cpp` line 177

```cpp
auto tokens = common_tokenize(context_, formatted_prompt, true, false);
```

The `add_special=true` means BOS/EOS tokens are added. For chat templates, the template itself handles BOS/EOS, so adding them again may produce duplicate tokens. The official `ai_chat.cpp` uses `has_chat_template` to conditionally set this.

### 24. No Android-specific optimizations
**File:** `CMakeLists.txt`

- `GGML_CPU_KLEIDIAI` is enabled for arm64-v8a (Google's CPU optimizations for Samsung KleidiAI) — good
- `GGML_OPENMP` is enabled for arm64-v8a — this may cause issues on Android since OpenMP runtime isn't always bundled
- No Hexagon NPU backend support (documented as optional, requires env vars)

---

## BUILD CONFIGURATION

### CMakeLists.txt observations:

1. **`GGML_OPENMP ON` for arm64-v8a** — OpenMP on Android requires the OpenMP runtime library. This is available via `android-ndk-r25+` but should be verified.

2. **`add_subdirectory(${LLAMA_SRC} build-llama)`** — This builds llama.cpp as a subdirectory. The `build-llama` output goes into the CMake build tree. Ensure this doesn't conflict with other modules.

3. **Missing `GGML_CUDA` / `GGML_VULKAN` / `GGML_METAL`** — No GPU backend options exposed. On Android, only CPU (KleidiAI) and optionally Hexagon are relevant.

4. **`target_include_directories` includes `${LLAMA_SRC}/ggml/src`** — This exposes internal ggml headers that may change without warning. Consider using only public headers.

---

## COMPARISON WITH OFFICIAL llama.android EXAMPLE

| Feature | DaexLlama Bridge | Official ai_chat.cpp |
|---------|-----------------|---------------------|
| Backend loading | ✅ `ggml_backend_load_all_from_path` | ✅ Same |
| `llama_backend_init` | ❌ Missing | ✅ Present |
| `llama_backend_free` | ❌ Missing | ✅ Present |
| Deprecated APIs | ❌ Uses 3 deprecated | ❌ Also uses 2 deprecated |
| System prompt handling | ❌ Unified | ✅ Separate with position tracking |
| Context shifting | ✅ Basic | ✅ Same approach |
| Error handling | ❌ Minimal | ✅ Moderate |
| Multi-sequence | ❌ Single | ❌ Single |
| Grammar support | ❌ None | ✅ Via chat templates |
| Model info API | ❌ None | ✅ `systemInfo()`, `benchModel()` |
| Cleanup completeness | ❌ Missing batch/sampler free | ✅ Complete |
| Thread safety | ❌ None | ❌ None |
| Configurable params | ❌ Hardcoded | ❌ Hardcoded |

---

## RECOMMENDATIONS (Priority Order)

1. **Fix JNI function names** to match actual Java interface
2. **Add `llama_backend_init()` / `llama_backend_free()`**
3. **Add error handling** for model load, context init, sampler init
4. **Fix cleanup** — add `llama_batch_free()` and `common_sampler_free()`
5. **Update deprecated APIs** (`llama_model_load_from_file` → `llama_model_init_from_file`, etc.)
6. **Add system prompt position tracking** for proper context shifting
7. **Add thread safety** (mutex or documented single-thread constraint)
8. **Make sampling params configurable** from Java
9. **Add model info JNI methods** (n_ctx_train, n_embd, n_params, description)
10. **Increase batch init size** to match context
