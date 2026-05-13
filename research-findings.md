# DAEX JNI Bridge Research Findings

## 1. JNI Bridge Architecture

The inference stack has three layers:

```
DaexAndroid (Kotlin app)
  -> LlamaService (Kotlin abstraction)
    -> DaexLlamaEngine (Kotlin interface)
      -> DaexLlamaEngineImpl (Kotlin impl)
        -> native methods (JNI)
          -> daex_llama_bridge.cpp (C++)
            -> llama.cpp (bundled)
```

### Layer 1: DaexAndroid/Kotlin
- `LlamaServiceImpl` in `DaexAndroid/app/src/main/java/com/daex/android/services/LlamaService.kt`
- Manages lifecycle: `initContext()`, `releaseContext()`, `generateResponse()`, `generateSilent()`
- Uses `Flow<String>` for token streaming — no race-condition buffer
- Builds system prompt with RAG context injection
- Default params: n_ctx=8192, n_batch=512, temp=0.7f, n_predict=1024

### Layer 2: DaexLlamaEngine
- `DaexLlamaEngine.kt` in `DaexLlama/src/main/java/com/daex/llama/DaexLlamaEngine.kt`
- Interface with 16 native methods across 4 categories:
  - **Lifecycle**: nativeInit(), nativeShutdown(), nativeCreateContext(), nativeDestroyContext()
  - **Model**: nativeLoadModel(), nativeUnloadModel(), nativeLoadEmbeddingModel(), nativeGetEmbedding()
  - **Generation**: nativePrepareContext(), nativeSetConfig(), nativeProcessSystemPrompt(), nativeProcessUserPrompt(), nativeGenerateNextToken()
  - **Query**: nativeCancelGeneration(), nativeResetConversation(), nativeSystemInfo(), nativeActiveBackends()

### Layer 3: JNI Bridge (C++)
- `daex_llama_bridge.cpp` in `DaexLlama/src/main/cpp/src/`
- 655 lines, implements all JNI methods declared in `DaexLlamaEngineImpl`
- Uses `std::map<int, std::unique_ptr<LlamaContext>>` for multi-context management
- Thread-safe via `std::mutex g_contexts_mutex`
- `LlamaContext` struct manages: model, context, batch, chat templates, sampler, conversation state

## 2. llama.cpp Version & API Compatibility

### Bundled Version: 2805 (commit hash 2805)
- Located at: `DaexLlama/src/main/cpp/external/llama.cpp/`
- Modern API with `common_sampler`, `common_chat_templates`, `ggml-backend`

### API Audit: 47/48 functions found
All llama.cpp API calls in the bridge are compatible with the bundled version:

| Category | Functions | Status |
|----------|-----------|--------|
| Model loading | llama_model_default_params, llama_model_load_from_file, llama_model_free, llama_model_size, llama_model_n_params, llama_model_n_ctx_train, llama_model_n_embd, llama_model_get_vocab | All FOUND |
| Context | llama_context_default_params, llama_init_from_model, llama_free | All FOUND |
| Batch | llama_batch_init, llama_batch_free, llama_decode | All FOUND |
| Memory | llama_get_memory, llama_memory_seq_rm, llama_memory_seq_add, llama_memory_clear, llama_get_embeddings_seq | All FOUND |
| Vocabulary | llama_vocab_is_eog, llama_vocab_get_text | All FOUND |
| Backend | ggml_backend_reg_count, ggml_backend_reg_get, ggml_backend_reg_name, ggml_backend_load_all_from_path | All FOUND |
| Common | common_tokenize, common_batch_clear, common_batch_add, common_token_to_piece | All FOUND |
| Sampling | common_sampler_init, common_sampler_free, common_sampler_sample, common_sampler_accept | All FOUND |
| Chat | common_chat_templates_init, common_chat_templates_was_explicit, common_chat_format_single, common_chat_templates_ptr, common_chat_msg | All FOUND |
| Logging | llama_log_set, llama_print_system_info | All FOUND |
| Backend init | llama_backend_init, llama_backend_free | All FOUND |

### One enum rename detected:
- `GGML_LOG_LEVEL_VERBOSE` was renamed to `GGML_LOG_LEVEL_DEBUG` in newer llama.cpp
- The bridge does NOT use this enum directly — it uses `__android_log_print` with Android log levels, so this is non-breaking

### Key API signatures verified:
- `common_batch_add(batch, id, pos, {0}, logits)` — vector seq_ids param matches
- `common_tokenize(ctx, text, add_special, parse_special)` — overload resolution correct
- `llama_decode(ctx, batch)` — signature unchanged
- `common_sampler_sample(gsmpl, ctx, idx, grammar_first)` — default param matches

## 3. Backend Auto-Detection Audit

### How backends are loaded:

1. **nativeInit()** receives `nativeLibDir` from Kotlin
2. Calls `ggml_backend_load_all_from_path(path)` — loads .so plugins from the lib directory
3. Calls `llama_backend_init()` — initializes default backends (CPU, etc.)
4. `get_active_backends()` iterates `ggml_backend_reg_count()` and returns names

### Backend registration (ggml-backend-reg.cpp):

| Backend | Build Flag | Android Relevance |
|---------|-----------|-------------------|
| CPU | GGML_USE_CPU | Always enabled on Android |
| Hexagon | GGML_USE_HEXAGON | Samsung/Qualcomm NPU — conditionally enabled |
| CUDA | GGML_USE_CUDA | N/A (Android GPU) |
| Vulkan | GGML_USE_VULKAN | Possible Android GPU path |
| OpenCL | GGML_USE_OPENCL | Possible Android GPU path |

### KleidiAI Integration:

- Located at `ggml/src/ggml-cpu/kleidiai/`
- ARM-optimized CPU kernels for Samsung Exynos chips
- Enabled via `GGML_CPU_KLEIDIAI` CMake flag
- Fetches KleidiAI v1.24.0 from GitHub releases at build time
- Provides `ggml_backend_cpu_kleidiai_buffer_type()` — alternative buffer type for CPU
- If available, the CPU backend automatically registers the KleidiAI buffer type
- This is a **CPU optimization**, not a separate backend — it still runs on CPU but with ARM NEON/SVE optimized kernels

### Hexagon Backend:

- Registered via `ggml_backend_hexagon_reg()` when `GGML_USE_HEXAGON` is defined
- Qualcomm Hexagon DSP NPU — used on Samsung Galaxy S24/S25 series
- Provides dedicated GPU/NPU acceleration for llama.cpp inference
- The bridge does NOT explicitly select it — it relies on ggml's automatic backend selection

### Key Finding: No explicit backend selection

The bridge relies entirely on ggml's automatic backend selection:
- `llama_model_load_from_file()` takes no backend params
- `llama_init_from_model()` takes no backend params
- No `ggml_backend_t` or `ggml_backend_sched` is used in the bridge
- The `useGPU` parameter in `LlamaService.initContext()` is **declared but never used** — it's passed to the Kotlin API but has no effect on the C++ bridge

### Potential Issue: useGPU parameter is dead code

In `LlamaService.kt`:
```kotlin
suspend fun initContext(modelPath: String, useGPU: Boolean = false)
```

The `useGPU` parameter is never forwarded to the native layer. The bridge always uses the default backend selection (CPU on most Android devices, possibly Hexagon/Vulkan if the device has it and the build supports it).

### Thread count calculation:

```cpp
int n_threads = std::max(2, (int)sysconf(_SC_NPROCESSORS_ONLN) - 2);
```

This reserves 2 cores for the system. On a typical 8-core Android SoC, this gives 6 threads for inference.

## 4. kotlinllamacpp (Separate Kotlin/Native Library)

### Structure:
- Located at: `DaexLlama/src/main/cpp/external/llama.cpp/` (shared with bridge)
- The kotlinllamacpp library is a separate Kotlin/Native binding that wraps llama.cpp
- Uses CMake to build llama.cpp as a static library, then binds via Kotlin/Native `expect`/`actual` pattern
- Platform-specific code: `commonMain/` (expect declarations) and `androidMain/` (actual implementations)

### Key kotlinllamacpp classes:
- `LlamaCppBackend` — main entry point
- `LlamaCppModel` — model wrapper
- `LlamaCppContext` — context wrapper
- `LlamaCppBatch` — batch wrapper
- `LlamaCppSampler` — sampler wrapper

### kotlinllamacpp does NOT appear to be used by the current DaexAndroid codebase.

The `LlamaServiceImpl` uses `DaexLlamaEngineImpl` which calls JNI methods directly — it does NOT use the kotlinllamacpp library. This suggests kotlinllamacpp is either:
1. A planned replacement for the JNI bridge
2. An alternative binding for non-Android platforms
3. A work-in-progress that was abandoned in favor of the JNI approach

## 5. Security & Stability Notes

### Memory Management:
- `LlamaContext` destructor properly frees all resources in reverse allocation order
- `std::mutex` protects the global context map
- No raw pointers leaked — all contexts are `std::unique_ptr`

### Potential Issues:
1. **useGPU parameter dead code** — Kotlin API accepts it but native layer ignores it
2. **No KV cache offloading** — `llama_context_params.offload_kqv` is never set (defaults to false)
3. **No backend selection** — relies entirely on ggml auto-detection, no way to force CPU vs GPU
4. **Thread count heuristic** — `sysconf(_SC_NPROCESSORS_ONLN) - 2` may not be optimal on all Android SoCs
5. **Context shift logic** — when context overflows, the bridge discards half the system+conversation history. This may cause loss of important context.

### RAG Context Handling:
- `LlamaServiceImpl.buildSystemPrompt()` wraps RAG context in `### CONTEXT_START ###` / `### CONTEXT_END ###` delimiters
- This is a custom format, not a standard llama.cpp chat template
- The bridge passes this through `common_chat_format_single()` which respects the GGUF-native template if available

## 6. Version Tracking

| Component | Version | Location |
|-----------|---------|----------|
| llama.cpp | commit 2805 | DaexLlama/src/main/cpp/external/llama.cpp/ |
| ggml | bundled with llama.cpp | DaexLlama/src/main/cpp/external/llama.cpp/ggml/ |
| KleidiAI | v1.24.0 (fetched at build) | ggml/src/ggml-cpu/kleidiai/ |
| DaexLlamaEngine | N/A (local) | DaexLlama/src/main/java/com/daex/llama/ |
| LlamaService | N/A (local) | DaexAndroid/app/src/main/java/com/daex/android/services/ |

## 7. Recommendations

1. **Wire up useGPU parameter** — pass it through to set `cparams.offload_kqv = useGPU` or select a GPU backend
2. **Add explicit backend selection** — allow user to force CPU/GPU/HW accelerator
3. **Improve context shift** — use a more sophisticated eviction strategy (e.g., keep system prompt, evict oldest conversation)
4. **Consider using ggml_backend_sched** — the bridge uses raw `llama_decode()` without a scheduler, missing out on automatic backend selection per tensor
5. **Document the RAG context format** — the `### CONTEXT_START ###` delimiters are non-standard and may conflict with GGUF-native chat templates
