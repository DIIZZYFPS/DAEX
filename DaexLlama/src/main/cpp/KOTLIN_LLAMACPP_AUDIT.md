# kotlin-llamacpp JNI Audit — Patterns & Comparison

> Reference implementation: `/home/diizzy/Projects/kotlinllamacpp/`
> This is the React Native bridge (llama.rn) that DAEX replaces with a pure Kotlin + JNI approach.

---

## 1. Architecture Overview

### kotlin-llamacpp (React Native bridge)
```
Kotlin (RN Bridge) → JNI (jni.cpp) → lib/rn-llama.cpp → llama.cpp C API
```

### DaexLlama (pure Kotlin + JNI)
```
Kotlin (Compose) → JNI (daex_llama_bridge.cpp) → llama.cpp C API
```

**Key difference:** kotlin-llamacpp uses a C++ wrapper library (`lib/rn-llama.cpp`) as an intermediate layer between JNI and llama.cpp. DaexLlama calls llama.cpp directly from the JNI bridge.

---

## 2. JNI Layer Patterns

### 2.1 Context Management

**kotlin-llamacpp:**
- Uses `RNContext` struct with `shared_ptr<RNLLama>` pointer
- Context ID is `int` returned from `createContext()`
- Global map: `std::map<int, std::shared_ptr<RNContext>> g_contexts`
- Thread-safe: `std::mutex g_contexts_mutex`

**DaexLlama:**
- Uses `LlamaContext` struct with direct `llama_model*` and `llama_context*`
- Context ID is `int` returned from `nativeCreateContext()`
- Global map: `std::map<int, std::unique_ptr<LlamaContext>> g_contexts`
- Thread-safe: `std::mutex g_contexts_mutex`

**Comparison:** Both use the same ID-based context tracking with mutex protection. DaexLlama uses `unique_ptr` (better memory semantics), kotlin-llamacpp uses `shared_ptr` (allows sharing).

### 2.2 String Handling

**kotlin-llamacpp:**
```cpp
const auto* model_path = env->GetStringUTFChars(model_path_j, nullptr);
// ... use ...
env->ReleaseStringUTFChars(model_path_j, model_path);
```

**DaexLlama:**
```cpp
const auto* model_path = env->GetStringUTFChars(jmodel_path, nullptr);
// ... use ...
env->ReleaseStringUTFChars(jmodel_path, model_path);
```

**Comparison:** Identical pattern. Both correctly release UTF chars.

### 2.3 Error Handling

**kotlin-llamacpp:**
- Returns `jint` with -1 for errors
- Returns `jstring` (nullptr for errors)
- Returns `jfloatArray` (nullptr for errors)

**DaexLlama:**
- Returns `jint` with -1 for errors (load/prepare), 0 for ok, 1 for decode errors
- Returns `jstring` (nullptr for errors or generation stop)
- Returns `jfloatArray` (nullptr for errors)

**Comparison:** Consistent error return patterns.

### 2.4 Threading

**kotlin-llamacpp:**
- `std::mutex g_contexts_mutex` protects global state
- `std::mutex completion_mutex` protects per-context completion state
- Uses `std::lock_guard` for RAII locking

**DaexLlama:**
- `std::mutex g_contexts_mutex` protects global state
- Uses `std::lock_guard` for RAII locking
- No per-context mutex (generation state is single-threaded per context)

**Comparison:** DaexLlama is simpler — no per-context mutex needed since generation is sequential per context.

---

## 3. RNLLama Wrapper Layer (kotlin-llamacpp only)

kotlin-llamacpp has an intermediate C++ library (`lib/rn-llama.cpp`) that wraps llama.cpp:

### RNLLama API Surface
```cpp
RNLLama* rn_llama_create();                                    // Factory
void rn_llama_destroy(RNLLama* llama);                         // Destructor
int rn_llama_load_model(RNLLama* llama, const char* path);     // Model load
int rn_llama_create_context(RNLLama* llama);                   // Context creation
int rn_llama_prepare_context(RNLLama* llama, int ctx_id);      // Context setup
void rn_llama_set_config(RNLLama* llama, int ctx_id, ...);     // Config
int rn_llama_process_system_prompt(RNLLama* llama, int ctx_id, const char*); // Prompt
int rn_llama_process_user_prompt(RNLLama* llama, int ctx_id, const char*);   // Prompt
const char* rn_llama_generate_next_token(RNLLama* llama, int ctx_id);        // Generate
void rn_llama_cancel_generation(RNLLama* llama, int ctx_id);                 // Cancel
void rn_llama_reset_conversation(RNLLama* llama, int ctx_id);                // Reset
const char* rn_llama_system_info(RNLLama* llama);                            // Info
void rn_llama_shutdown(RNLLama* llama);                                      // Cleanup
```

### RNContext struct (wrapper)
```cpp
struct RNContext {
    int id;
    common_chat_templates_ptr chat_templates;
    common_sampler* sampler;
    llama_batch batch;
    llama_model* model;
    llama_context* ctx;
    std::vector<common_chat_msg> chat_msgs;
    llama_pos system_prompt_position;
    llama_pos current_position;
    llama_pos stop_generation_position;
    llama_pos gen_stop_position;
    std::string cached_token_chars;
    std::ostringstream assistant_ss;
    bool cancel_generation;
    int n_ctx;
    int n_batch;
    int n_threads;
    float sampler_temp;
    int n_predict;
};
```

**Key observation:** The RNContext struct is nearly identical to DaexLlama's LlamaContext struct. Same fields, same purpose. This confirms the design is sound.

### RNLLama singleton
```cpp
class RNLLama {
    static RNLLama* instance;
    llama_cpp_wrapper* wrapper;
    std::map<int, std::shared_ptr<RNContext>> contexts;
    std::mutex contexts_mutex;
    // ... methods ...
};
```

**Key observation:** kotlin-llamacpp uses a singleton pattern (`RNLLama::instance`) which is unnecessary in JNI. DaexLlama correctly uses static global state instead.

---

## 4. Key Differences — kotlin-llamacpp vs DaexLlama

| Aspect | kotlin-llamacpp | DaexLlama | Notes |
|---|---|---|---|
| **Architecture** | JNI → RNLLama wrapper → llama.cpp | JNI → llama.cpp | DaexLlama removes unnecessary layer |
| **Context storage** | `shared_ptr<RNContext>` | `unique_ptr<LlamaContext>` | unique_ptr is more appropriate |
| **Singleton pattern** | RNLLama singleton | Static globals | Static is better for JNI |
| **Model loading** | `llama_model_load_from_file` | `llama_model_load_from_file` | Same |
| **Context creation** | `llama_init_from_model` | `llama_init_from_model` | Same |
| **Sampling** | `common_sampler_*` | `common_sampler_*` | Same |
| **Chat templates** | `common_chat_templates_*` | `common_chat_templates_*` | Same |
| **Batch API** | `common_batch_*` | `common_batch_*` | Same |
| **Embeddings** | Not implemented | `nativeGetEmbedding()` | DaexLlama adds this |
| **Backend loading** | Not present | `ggml_backend_load_all_from_path` | DaexLlama adds this |
| **JNI string handling** | Correct | Correct | Both match |
| **Thread safety** | 2 mutexes (global + per-context) | 1 mutex (global) | DaexLlama is simpler |
| **Context shifting** | `llama_memory_seq_rm/add` | `llama_memory_seq_rm/add` | Same approach |
| **UTF-8 validation** | Custom `is_valid_utf8` | Custom `is_valid_utf8` | Same approach |
| **Thread count** | `sysconf(_SC_NPROCESSORS_ONLN) - 2` | `sysconf(_SC_NPROCESSORS_ONLN) - 2` | Same |
| **Default n_ctx** | 8192 | 8192 | Same |
| **Default n_batch** | 512 | 512 | Same |
| **Default temp** | 0.3 | 0.3 | Same |
| **Default n_predict** | 1024 | 1024 | Same |

---

## 5. kotlin-llamacpp JNI Function List

All JNI functions in `jni.cpp` (21 functions):

| JNI Function | Signature | Purpose |
|---|---|---|
| `Java_ai_chat_LlamaCpp_nativeInit` | `(Ljava/lang/String;)V` | Init backends from lib dir |
| `Java_ai_chat_LlamaCpp_nativeCreateContext` | `()I` | Create new context |
| `Java_ai_chat_LlamaCpp_nativeDestroyContext` | `(I)V` | Destroy context |
| `Java_ai_chat_LlamaCpp_nativeLoadModel` | `(ILjava/lang/String;)I` | Load GGUF model |
| `Java_ai_chat_LlamaCpp_nativePrepareContext` | `(I)I` | Setup context + sampler |
| `Java_ai_chat_LlamaCpp_nativeSetConfig` | `(IIIF)V` | Set generation params |
| `Java_ai_chat_LlamaCpp_nativeProcessSystemPrompt` | `(ILjava/lang/String;)I` | Process system prompt |
| `Java_ai_chat_LlamaCpp_nativeProcessUserPrompt` | `(ILjava/lang/String;)I` | Process user prompt |
| `Java_ai_chat_LlamaCpp_nativeGenerateNextToken` | `(I)Ljava/lang/String;` | Generate one token |
| `Java_ai_chat_LlamaCpp_nativeCancelGeneration` | `(I)V` | Cancel generation |
| `Java_ai_chat_LlamaCpp_nativeResetConversation` | `(I)V` | Reset conversation |
| `Java_ai_chat_LlamaCpp_nativeSystemInfo` | `()Ljava/lang/String;` | Print system info |
| `Java_ai_chat_LlamaCpp_nativeUnloadModel` | `(I)V` | Unload model |
| `Java_ai_chat_LlamaCpp_nativeShutdown` | `()V` | Cleanup |
| `Java_ai_chat_LlamaCpp_nativeClearCache` | `(IZ)V` | Clear KV cache |

---

## 6. kotlin-llamacpp Build Configuration

From `llamaCpp/build.gradle.kts`:
- Uses `llamacpp-*.aar` from Maven Central (not submodule)
- CMake target: `llamaCpp`
- ABIs: `arm64-v8a`, `x86_64`
- C++ standard: C++17
- Includes: `src/main/cpp/lib/rn-llama.cpp` + `src/main/cpp/jni.cpp`

From `llamaCpp/CMakeLists.txt`:
- Links against `llamacpp` AAR (pre-built)
- No llama.cpp submodule
- No ggml backend loading

**Key difference:** kotlin-llamacpp uses a pre-built AAR from Maven Central, while DaexLlama builds llama.cpp from source via submodule. This gives DaexLlama full control over build flags (KleidiAI, Hexagon, OpenMP) but requires maintaining the submodule.

---

## 7. Safety Audit Notes for T1

### Patterns that carry over safely
1. **Context ID tracking** — Both use int IDs with mutex-protected maps
2. **String handling** — Both correctly use `GetStringUTFChars` / `ReleaseStringUTFChars`
3. **Lock_guard RAII** — Both use mutex-protected critical sections
4. **Batch lifecycle** — `llama_batch_init` → use → `llama_batch_free`
5. **Sampler lifecycle** — `common_sampler_init` → use → `common_sampler_free`
6. **Chat template lifecycle** — `common_chat_templates_init` → use → `.reset()`

### Patterns that differ (DaexLlama improvements)
1. **No unnecessary wrapper layer** — Direct llama.cpp calls are simpler
2. **unique_ptr over shared_ptr** — Better ownership semantics for contexts
3. **Static globals over singleton** — Cleaner JNI pattern
4. **Embedding support** — DaexLlama adds `nativeGetEmbedding()` for RAG
5. **Backend auto-detection** — DaexLlama loads GPU backends via `ggml_backend_load_all_from_path`
6. **Context shifting on overflow** — Both have it, but DaexLlama's implementation is cleaner

### Potential issues to audit in T1
1. **`llama_memory_seq_rm` return value ignored** — Could silently fail on invalid ranges
2. **No `llama_get_model` validation** — `nativeGetEmbedding` uses `ctx->model` which could be stale after `nativeUnloadModel`
3. **`GetStringUTFChars` on null jstring** — Would crash; Kotlin side should guard
4. **No `llama_supports_gpu_offload` check** — Backend loading might silently fail on devices without GPU
5. **Context destructor order** — `llama_batch_free` before `llama_free` is correct, but `llama_model_free` should NOT be called if model is shared (DaexLlama calls it in destructor — potential issue if model is shared across contexts)
