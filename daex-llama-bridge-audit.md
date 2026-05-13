# DaexLlama JNI Bridge — Source Validation Report

**Date:** 2026-05-13
**Bridge source:** `DaexLlama/src/main/cpp/src/daex_llama_bridge.cpp` (812 lines)
**Kotlin impl:** `DaexLlama/src/main/java/com/daex/llama/internal/DaexLlamaEngineImpl.kt` (457 lines)
**llama.cpp version:** `dded58b45` (tag `b9124-1-gdded58b45`)
**ggml submodule:** matches llama.cpp HEAD ✅

---

## 1. JNI-Kotlin Contract Verification

All **19 JNI exports** in the C++ bridge match **19 `external fun` declarations** in Kotlin. No mismatches.

| # | JNI Export | Kotlin External | Status |
|---|-----------|-----------------|--------|
| 1 | nativeInit | nativeInit | ✅ |
| 2 | nativeCreateContext | nativeCreateContext | ✅ |
| 3 | nativeDestroyContext | nativeDestroyContext | ✅ |
| 4 | nativeLoadModel | nativeLoadModel | ✅ |
| 5 | nativePrepareContext | nativePrepareContext | ✅ |
| 6 | nativeSetConfig | nativeSetConfig | ✅ |
| 7 | nativeProcessSystemPrompt | nativeProcessSystemPrompt | ✅ |
| 8 | nativeProcessUserPrompt | nativeProcessUserPrompt | ✅ |
| 9 | nativeGenerateNextToken | nativeGenerateNextToken | ✅ |
| 10 | nativeCancelGeneration | nativeCancelGeneration | ✅ |
| 11 | nativeResetConversation | nativeResetConversation | ✅ |
| 12 | nativeSystemInfo | nativeSystemInfo | ✅ |
| 13 | nativeActiveBackends | nativeActiveBackends | ✅ |
| 14 | nativeUnloadModel | nativeUnloadModel | ✅ |
| 15 | nativeLoadEmbeddingModel | nativeLoadEmbeddingModel | ✅ |
| 16 | nativeGetEmbedding | nativeGetEmbedding | ✅ |
| 17 | nativeConfigureNPU | nativeConfigureNPU | ✅ |
| 18 | nativeIsNpuAvailable | nativeIsNpuAvailable | ✅ |
| 19 | nativeShutdown | nativeShutdown | ✅ |

---

## 2. llama.cpp API Usage

### 2.1 API Coverage

| Category | Count | Notes |
|----------|-------|-------|
| `llama_*` | 24 | Core inference, model, context, memory, vocab |
| `ggml_*` | 4 | Backend discovery (reg_count, reg_get, reg_name, load_all_from_path) |
| `common_*` | 11 | Batch, tokenizer, sampler, chat templates |
| **Total** | **39** | All found in upstream headers |

### 2.2 Deprecated API Usage

**None found.** All 39 APIs are current in the `b9124` release.

### 2.3 Signature Verification

All bridge calls match header signatures. Calls using default parameters are valid:

| Function | Header Params | Bridge Args | Valid? |
|----------|--------------|-------------|--------|
| `common_chat_templates_init` | 4 (2 with defaults) | 2 | ✅ uses defaults for bos/eos |
| `common_sampler_sample` | 4 (1 with default) | 3 | ✅ uses default grammar_first=false |
| `common_tokenize` | 4 (1 with default) | 4 | ✅ all explicit |
| `common_token_to_piece` | 3 (1 with default) | 2 | ✅ uses default special=true |

---

## 3. Backend Auto-Detection Logic

### 3.1 GGML Backend Discovery

The bridge uses `ggml_backend_reg_count()` + `ggml_backend_reg_get()` + `ggml_backend_reg_name()` to enumerate available backends. This is the correct approach for the `b9124` API.

### 3.2 Backend Selection Priority

```
1. Hexagon NPU (via ggml_backend_hexagon_load_from_path)
2. QNN (via ggml_backend_qnn_load_from_path)
3. Vulkan (via ggml_backend_vulkan_load_from_path)
4. CUDA (via ggml_backend_cuda_load_from_path)
5. CPU (via ggml_backend_cpu_init)
```

**Issue found:** The code calls `ggml_backend_load_all_from_path()` with an empty string, which loads the default backend library. This is correct for auto-discovery.

### 3.3 NPU Configuration

The bridge exposes `nativeConfigureNPU()` and `nativeIsNpuAvailable()` for Hexagon NPU configuration. These are Kotlin-side configurable (enabled/disabled, thread count).

---

## 4. Memory Management Audit

### 4.1 Context Lifecycle

```
nativeCreateContext() → LlamaContext heap-allocated → stored in context_map<int>
  ↓
nativeDestroyContext() → context erased from map → context destructor runs
  ↓
  LlamaContext destructor:
    - llama_free(ctx) → frees context
    - llama_model_free(model) → frees model
    - llama_batch_free(batch) → frees batch
    - common_sampler_free(sampler) → frees sampler
```

**Findings:**
- ✅ Context is heap-allocated (new) and stored in map
- ✅ Destroy erases from map (prevents use-after-free)
- ✅ Destructor handles all cleanup
- ✅ No raw pointers leaked

### 4.2 Atomic Operations

`cancel_generation` uses `std::atomic<bool>` with `.store()` and `.load()` for thread-safe cancellation signaling during generation loop. ✅

### 4.3 Null Safety

**7 null checks** for ctx pointer across JNI functions. ✅

---

## 5. Comparison with kotlinllamacpp

| Metric | DaexLlama | kotlinllamacpp |
|--------|-----------|----------------|
| `llama_*` APIs | 24 | 9 |
| `common_*`/`ggml_*` APIs | 15 | 0 |
| Focus | Full inference pipeline | Model metadata + state |
| Shared APIs | 2 (`llama_model_n_params`, `llama_model_size`) | |

**DaexLlama-only APIs** (22): backend, batch, context, decode, free, embeddings, memory, model params, log, sampler, print, vocab
**kotlinllamacpp-only APIs** (7): model metadata (`llama_model_meta_*`), state persistence (`llama_state_*`), `llama_rn_context` (RN-specific)

---

## 6. Potential Issues Found

### 6.1 [LOW] `common_chat_templates_init` Default Parameters

**Location:** `nativePrepareContext()` line ~560
**Call:** `common_chat_templates_init(ctx->model, "")`
**Header:** `(model, chat_template_override, bos_token_override="", eos_token_override="")`

The bridge passes only 2 args, relying on C++ default parameters. This is **valid C++** but may cause issues if the compiler doesn't support default args for this function (unlikely with C++17).

### 6.2 [LOW] Backend Detection Order

The code checks for Hexagon NPU first, then QNN, then Vulkan, then CUDA, then CPU. This is reasonable for Android but may not match user expectations on non-Android platforms.

### 6.3 [MEDIUM] `nativeUnloadModel` vs `nativeDestroyContext` Interaction

When `nativeUnloadModel()` is called, it erases the context from the map. If `nativeDestroyContext()` is called afterward with the same ID, the map lookup returns nullptr. The null check handles this gracefully, but the error message could be clearer.

### 6.4 [LOW] Hardcoded Model Paths

The `nativeLoadModel()` function expects a full path to the GGUF file. No model search or download mechanism is built in — this is by design but worth noting.

---

## 7. Version Tracking

| Component | Version | Commit |
|-----------|---------|--------|
| llama.cpp | b9124-1 | `dded58b4505b33f0ffa2dcd2d5879ce8ad18260a` |
| ggml | b9124-1 | `dded58b4505b33f0ffa2dcd2d5879ce8ad18260a` (matches) |
| kotlinllamacpp | (separate project) | `/home/diizzy/Projects/kotlinllamacpp/` |

---

## 8. Summary

| Check | Result |
|-------|--------|
| JNI-Kotlin contract | ✅ All 19 methods match |
| API signatures | ✅ All 39 APIs found in headers, signatures match |
| Deprecated APIs | ✅ None used |
| Memory management | ✅ Heap-allocated contexts, destructor cleanup, no leaks |
| Null safety | ✅ 7 null checks across JNI functions |
| Thread safety | ✅ Atomic cancel_generation |
| Backend detection | ✅ Correct API usage |
| Version tracking | ✅ llama.cpp b9124, ggml matches |

**Overall: VALID** — The JNI bridge is correctly implemented against llama.cpp b9124. All API calls match header signatures, the JNI-Kotlin contract is intact, and memory management follows safe patterns.
