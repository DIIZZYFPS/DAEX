# DaexLlama

On-device LLM inference engine for Android, backed by [llama.cpp](https://github.com/ggerganov/llama.cpp).

Runs GGUF models natively on Android devices using ARM NEON, with optional acceleration via Samsung KleidiAI and Qualcomm HTP (Hexagon NPU).

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Android App Layer                       │
│  (ViewModel, Compose UI, RAG, etc.)                         │
└──────────────────────────┬──────────────────────────────────┘
                           │ DaexLlamaEngine (interface)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              Kotlin Layer (app module)                       │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  DaexLlamaEngineImpl                                │    │
│  │  - Singleton lifecycle (create/get/destroy)         │    │
│  │  - Coroutines + callbackFlow for streaming          │    │
│  │  - Native mutex + single-threaded dispatcher        │    │
│  │  - Backend .so extraction from assets               │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────┘
                           │ JNI (Java_com_daex_llama_*)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              Native Layer (C++ shared library)              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  daex_llama_bridge.cpp                              │    │
│  │  - Context management (LlamaContext per ctxId)      │    │
│  │  - Chat template formatting (common_chat_*)         │    │
│  │  - Tokenization + batched decode                    │    │
│  │  - Streaming generation (common_sampler_*)          │    │
│  │  - UTF-8 incremental token assembly                 │    │
│  │  - Context shifting on overflow                     │    │
│  │  - Embedding model support                          │    │
│  └─────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────┘
                           │ llama.cpp API
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              llama.cpp (git submodule)                      │
│  ┌──────────┬────────────┬────────────┬──────────────────┐  │
│  │  llama   │  common    │  ggml      │  ggml-backend    │  │
│  │  (model  │  (chat     │  (tensor    │  (runtime        │  │
│  │  loading│   templates│   compute)   │   dispatch)      │  │
│  │  & vocab│   format)   │            │  ┌───┬───┬───┐   │  │
│  └──────────┴────────────┴────────────┘  │CPU│KAI│HTP│   │  │
│                                          └───┴───┴───┘   │  │
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```
DaexLlama/
├── build.gradle.kts                    # Android library module config
├── src/main/
│   ├── java/com/daex/llama/
│   │   ├── DaexLlamaEngine.kt          # Public interface (sealed state machine)
│   │   └── internal/
│   │       └── DaexLlamaEngineImpl.kt  # JNI-backed implementation + singleton
│   ├── cpp/
│   │   ├── CMakeLists.txt              # CMake build: llama.cpp + bridge lib
│   │   ├── external/llama.cpp/         # Git submodule (gguf-v0.19.0)
│   │   └── src/
│   │       └── daex_llama_bridge.cpp   # JNI bridge (648 lines)
│   └── assets/
│       └── backends/                   # Pre-built .so files (KleidiAI, HTP)
└── review_findings.md
```

## Components

### 1. Kotlin Interface — `DaexLlamaEngine`

Public contract. All methods are thread-safe. Internal serialization is handled via a single-threaded dispatcher.

| Method | Description |
|--------|-------------|
| `loadModel(ctxId, modelPath)` | Load a GGUF model into the given context |
| `prepareContext(ctxId)` | Allocate `llama_context`, batch, sampler, chat templates |
| `setConfig(ctxId, n_ctx, n_batch, temp, n_predict)` | Tune inference params |
| `processSystemPrompt(ctxId, prompt)` | Format via chat template, tokenize, decode into context |
| `processUserPrompt(ctxId, prompt)` | Format user message, decode, return `Flow<String>` for streaming |
| `cancelGeneration(ctxId)` | Flag to stop ongoing generation |
| `resetConversation(ctxId)` | Clear KV cache and chat history (model stays loaded) |
| `unloadModel(ctxId)` | Free model from context |
| `createContext()` / `destroyContext(ctxId)` | Create/destroy inference contexts |
| `loadEmbeddingModel(ctxId, modelPath)` | Load a separate GGUF embedding model |
| `getEmbedding(ctxId, text)` | Compute embedding vector for text |
| `getActiveBackends()` | Returns detected backends (e.g. "CPU, KleidiAI, HTP") |
| `getSystemInfo()` | Returns `llama_print_system_info()` string |

### 2. Kotlin Implementation — `DaexLlamaEngineImpl`

- **Singleton pattern**: Created via `DaexLlamaEngineImpl.create(appContext)`, accessed via `.get()`, destroyed via `.destroy()`
- **Lifecycle**: Extracts backend `.so` files from `assets/backends/` to app's files directory, passes path to `nativeInit()`
- **Concurrency**: All native calls go through a single-threaded `Dispatchers.IO.limitedParallelism(1)` with a `Mutex` to prevent concurrent JNI calls
- **Streaming**: `processUserPrompt` returns a `callbackFlow` that polls `nativeGenerateNextToken()` in a loop until generation completes or the flow is cancelled
- **State machine**: `MutableStateFlow<EngineState>` tracks lifecycle states (Uninitialized → Initializing → Initialized → LoadingModel → ModelReady → ProcessingPrompt → Generating → Idle / Error)

### 3. C++ JNI Bridge — `daex_llama_bridge.cpp`

#### Context Management
- `LlamaContext` struct holds: `llama_model*`, `llama_context*`, `llama_batch`, `common_sampler*`, `common_chat_templates_ptr`, conversation state (chat messages, positions), config (n_ctx, n_batch, n_predict, temp, threads)
- Global `std::map<int, std::unique_ptr<LlamaContext>>` with mutex for thread safety
- Context IDs start at 1; -1 is reserved for the default context

#### Backend Loading
- `nativeInit()` extracts `.so` files from assets, calls `ggml_backend_load_all_from_path(path)` to dynamically load hardware backends, then `llama_backend_init()`
- Detects available backends via `ggml_backend_reg_count()` — reports non-CPU backends (KleidiAI, HTP, etc.)

#### Inference Pipeline
1. **System prompt**: Clears KV cache → formats via `common_chat_format_single()` → tokenizes → batched decode → records position
2. **User prompt**: Formats via chat template → tokenizes → batched decode with `compute_last_logit=true` → starts generation loop
3. **Token generation**: `common_sampler_sample()` → `common_sampler_accept()` → `llama_decode()` for single token → `common_token_to_piece()` for text
4. **UTF-8 assembly**: Accumulates token pieces in `cached_token_chars`, only emits when valid UTF-8 complete; appends to `assistant_ss` for full response buffering
5. **EOG detection**: Stops when `llama_vocab_is_eog()` returns true; saves assistant message to chat history
6. **Context shifting**: On overflow (`start_pos + batch_size >= n_ctx - 4`), discards half the system prompt KV entries and shifts remaining positions

#### Embedding Support
- Separate context with `cparams.embeddings = true`
- Tokenizes input → batched decode → extracts `llama_get_embeddings_seq()` → returns as `FloatArray`

### 4. Build System — `CMakeLists.txt`

- **llama.cpp submodule**: Built via `add_subdirectory()`, forces `LLAMA_BUILD_COMMON=ON` for chat template, tokenizer, sampler support
- **ABI**: `arm64-v8a` (KleidiAI + OpenMP enabled), `x86_64` (CPU only)
- **Hexagon/HTP**: Optional — enabled when `HEXAGON_SDK_ROOT` and `HEXAGON_TOOLS_ROOT` env vars are set
- **Linking**: Links against `llama`, `llama-common`, `android`, `log`
- **C++ standard**: C++17, C standard C11

### 5. Android Module — `build.gradle.kts`

- Android Library module (`com.android.library`)
- Min SDK 26 (Android 8.0)
- Compile SDK 36, Java/Kotlin 17
- NDK ABI filter: `arm64-v8a` only
- C++ shared STL (`c++_shared`)
- Dependencies: Kotlin coroutines 1.9.0

## Engine State Machine

```
Uninitialized → Initializing → Initialized
                                    ↓
                    ┌─── LoadingModel
                    ↓                  ↓
              ModelReady ← PreparingContext
                    ↓
              ProcessingPrompt
                    ↓
                  Generating
                    ↓
                     Idle
                    /
              Error (terminal, requires reset)
```

## Backend Architecture

| Backend | Condition | Description |
|---------|-----------|-------------|
| **CPU** | Always available | ARM NEON + SVE2 via ggml-cpu |
| **KleidiAI** | arm64-v8a | Samsung's AI library — auto-loaded via `ggml_backend_load_all_from_path()` |
| **HTP (Hexagon)** | `HEXAGON_SDK_ROOT` set + Qualcomm SM8850+ | Qualcomm NPU — pre-built `.so` in assets |

Backends are dynamically loaded at init time. The bridge queries `ggml_backend_reg_count()` to report active backends. llama.cpp's backend dispatch automatically routes operations to the best available backend.

## Usage Example

```kotlin
// Initialization (call once)
val engine = DaexLlamaEngineImpl.create(appContext)

// Load model
engine.loadModel(DaexLlamaEngineImpl.DEFAULT_CTX_ID, "/path/to/model.gguf")
engine.prepareContext(DaexLlamaEngineImpl.DEFAULT_CTX_ID)

// Configure
engine.setConfig(
    ctxId = -1,
    n_ctx = 8192,
    n_batch = 512,
    temp = 0.7f,
    n_predict = 1024
)

// Chat
engine.processSystemPrompt(-1, "You are a helpful assistant.")

engine.processUserPrompt(-1, "What is machine learning?")
    .collect { token ->
        // Stream token to UI
        StringBuilder().append(token)
    }

// Cleanup
engine.destroy()
```

## Technical Details

### Thread Safety
- JNI is not thread-safe for concurrent calls to the same context
- Kotlin layer serializes all native calls through a single-threaded dispatcher + Mutex
- C++ layer uses `std::mutex` for context map access and per-context state

### Memory Management
- `LlamaContext` destructor handles cleanup: `common_sampler_free()` → `chat_templates.reset()` → `llama_batch_free()` → `llama_free()` → `llama_model_free()`
- Contexts are stored in `std::unique_ptr` within a global map
- `nativeUnloadModel()` resets the context (freeing model + context), `nativeDestroyContext()` removes from map

### Streaming Implementation
- `callbackFlow` spawns a coroutine that locks the native mutex, sends user prompt, then enters a polling loop
- Each iteration calls `nativeGenerateNextToken()` which: samples → accepts → decodes → tokenizes → assembles UTF-8
- Flow cancellation triggers `nativeCancelGeneration()` and coroutine cancellation
- Tokens are emitted incrementally (not batched), enabling real-time UI updates

### Context Shifting (KV Cache Management)
- When context is near overflow (`position + batch >= n_ctx - 4`), the bridge:
  1. Removes KV entries for the first half of the system prompt
  2. Shifts remaining positions to maintain continuity
  3. Discards `n_discard = (currentPosition - systemPromptPosition) / 2` tokens
- This allows long conversations without running out of context

### UTF-8 Token Assembly
- llama.cpp tokenization can split multi-byte UTF-8 characters across tokens
- Bridge accumulates token pieces in `cached_token_chars`
- Validates UTF-8 completeness with `is_valid_utf8()` before emitting
- Incomplete sequences are buffered for the next token

## Submodules

| Submodule | Version | Purpose |
|-----------|---------|---------|
| `external/llama.cpp` | `gguf-v0.19.0-79-gdded58b45` | Core inference engine, GGUF loader, tokenizer, sampler, ggml tensor library |

## Build Requirements

- Android NDK r27+ (via Android Studio)
- CMake 3.22.1+
- For Hexagon/HTP support: Qualcomm Hexagon SDK + Hexagon Tools installed, env vars set
