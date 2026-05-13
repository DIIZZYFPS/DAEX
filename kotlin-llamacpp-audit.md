================================================================================
                    kotlin-llamacpp JNI LAYER AUDIT
                    Architecture & Pattern Analysis
================================================================================

Date: 2026-05-12
Target: /home/diizzy/Projects/kotlinllamacpp/
Comparison: DaexLlama (DAEX custom JNI bridge)

================================================================================
1. PROJECT STRUCTURE
================================================================================

kotlin-llamacpp
  app/                          # Demo app (Jetpack Compose UI)
  llamaCpp/                     # Library module
    src/main/java/org/nehuatl/llamacpp/
      LlamaAndroid.kt           # Kotlin JNI facade (230 lines)
      LlamaContext.kt           # Per-context JNI bindings (331 lines)
      LlamaHelper.kt            # High-level orchestrator (175 lines)
    src/main/cpp/
      jni.cpp                   # JNI bridge (645 lines)
      CMakeLists.txt            # Multi-variant build config
      lib/                      # Synced llama.cpp + llama.rn bridge
        rn-llama.cpp            # React Native llama API (20,961 chars)
        rn-completion.cpp       # React Native completion API (8,210 chars)
        rn-common.hpp           # Shared RN utils (8,698 chars)
        rn-tts.cpp              # TTS synthesis support
        rn-slot.cpp             # Slot-based API
        rn-slot-manager.cpp     # Slot manager
        *.h                     # Header files for RN bridge

================================================================================
2. JNI/FFI ARCHITECTURE
================================================================================

2.1 Native Library Loading Strategy
-----------------------------------
kotlin-llamacpp uses a MULTI-VARIANT build approach:
  - Detects CPU features at runtime via /proc/cpuinfo
  - Loads the most optimized .so variant:
    * librnllama.so           (generic)
    * librnllama_v8.so        (armv8-a)
    * librnllama_v8_2.so      (armv8.2-a)
    * librnllama_v8_2_dotprod.so             (armv8.2-a+dotprod)
    * librnllama_v8_2_i8mm.so                (armv8.2-a+i8mm)
    * librnllama_v8_2_dotprod_i8mm.so        (armv8.2-a+dotprod+i8mm)
    * librnllama_x86_64.so                    (x86_64)

  This is done in LlamaContext.kt companion init block via a cascading
  if/else chain checking CPU features (dotprod, i8mm, asimd, crc32, aes).

DaexLlama uses a SINGLE-VARIANT approach:
  - One libdaex-llama.so built for arm64-v8a (or x86_64)
  - Backend libraries (KleidiAI, HTP, etc.) are dynamically loaded at
    runtime from assets/backends/ directory via ggml_backend_load_all_from_path()

2.2 Native Context Management
-----------------------------
kotlin-llamacpp:
  - Context stored as `llama_rn_context*` pointer (opaque C++ object)
  - Context map: `std::map<long, std::unique_ptr<llama_rn_context>> context_map`
  - Context ID is a random int generated in Kotlin, passed as jlong to JNI
  - Completion stored as `std::unique_ptr<llama_rn_context_completion>`
  - Lifecycle: initContextWithFd() -> doCompletion() -> freeContext()

DaexLlama:
  - Context stored as `LlamaContext` struct (explicit C++ struct)
  - Context map: `std::map<int, std::unique_ptr<LlamaContext>> g_contexts`
  - Context ID is auto-incremented integer (1, 2, 3, ...)
  - Explicit createContext() / destroyContext() lifecycle
  - Context struct owns model, ctx, batch, sampler, chat_templates, state

2.3 JNI Function Naming Convention
-----------------------------------
kotlin-llamacpp: Java_org_nehuatl_llamacpp_LlamaContext_<method>
  - All JNI functions are static, called on the LlamaContext class
  - Signature: JNI<jtype> JNICALL Java_<package>_<class>_<method>(JNIEnv*, jobject, ...)

DaexLlama: Java_com_daex_llama_internal_DaexLlamaEngineImpl_<method>
  - All JNI functions are static, called on the impl class
  - Same naming convention (standard JNI pattern)

================================================================================
3. JNI PATTERNS
================================================================================

3.1 Callback Pattern
--------------------
kotlin-llamacpp: CALLBACK-BASED (React Native style)
  - Token streaming uses a PartialCompletionCallback inner class in LlamaContext.kt
  - The callback is passed as a Java object reference to JNI
  - JNI calls back into Java: env->CallObjectMethod(callback, onPartialCompletion, map)
  - The map is built via createHashMap() helper with token text
  - This is the React Native bridge pattern: JS callbacks become Java callbacks
    become JNI callbacks into Java code
  - Token data is serialized into a HashMap<String, Any> on each callback

DaexLlama: FLOW-BASED (Kotlin-native)
  - Token streaming uses Kotlin callbackFlow
  - Native code returns one token string per call (nativeGenerateNextToken)
  - Kotlin side loops: while(true) { token = nativeGenerateNextToken(); emit(token) }
  - No Java callback objects passed to native code
  - Streaming loop runs on nativeDispatcher (limitedParallelism(1))
  - Much simpler: JNI is stateless per-call, Kotlin drives the loop

3.2 Streaming Pattern
---------------------
kotlin-llamacpp:
  - doCompletion() is a BLOCKING call that runs the entire generation loop
  - The loop internally calls the PartialCompletionCallback for each token
  - Callback fires on the native thread (no thread switching)
  - Token text is accumulated in LlamaHelper.allText
  - Events emitted via MutableSharedFlow<LLMEvent>

DaexLlama:
  - nativeProcessUserPrompt() prepares the prompt, then returns 0
  - nativeGenerateNextToken() is called iteratively in a Kotlin while loop
  - Each call does ONE token generation + decode + return
  - UTF-8 validation buffer (cached_token_chars) to avoid partial UTF-8
  - Flow completes when token is null (generation done or cancelled)

3.3 Error Handling
------------------
kotlin-llamacpp:
  - JNI returns HashMap with "error" key on failure
  - Kotlin checks result.containsKey("error") and throws IllegalStateException
  - Native uses LOGE/LOGW macros for logging
  - Exception catch in native doCompletion catches std::exceptions
  - No structured error codes; relies on HashMap presence check

DaexLlama:
  - JNI returns int: 0 = success, -1 = failure
  - Kotlin checks result == 0 and maps to Boolean return
  - State transitions to EngineState.Error on failure
  - Native uses LOGE/LOGW macros
  - More structured: explicit success/failure codes

3.4 Lifecycle Management
------------------------
kotlin-llamacpp:
  - Model loading: LlamaHelper.load() -> LlamaAndroid.startEngine() ->
    LlamaContext(context) [JNI initContextWithFd] -> model loaded via FD
  - Completion: LlamaHelper.predict() -> LlamaAndroid.launchCompletion() ->
    LlamaContext.completion() [JNI doCompletion]
  - Cleanup: LlamaHelper.release() -> LlamaAndroid.releaseContext() ->
    JNI freeContext()
  - Single context limit by default (llamaContextLimit = 1)
  - Context ID is random int

DaexLlama:
  - Init: DaexLlamaEngineImpl.nativeInit() -> llama_backend_init()
  - Context: createContext() -> nativeCreateContext() (auto-increment ID)
  - Model: loadModel() -> nativeLoadModel() (returns int status)
  - Prepare: prepareContext() -> nativePrepareContext() (allocates llama_context)
  - Prompt: processSystemPrompt() -> nativeProcessSystemPrompt()
  - Generate: processUserPrompt() -> nativeProcessUserPrompt() +
              loop nativeGenerateNextToken()
  - Cleanup: destroyContext() -> nativeDestroyContext(), destroy() -> nativeShutdown()
  - Multiple contexts supported via createContext()

3.5 Parameter Passing Pattern
-----------------------------
kotlin-llamacpp:
  - Model loading: params passed as Map<String, Any> to JNI
  - FD-based file loading: model_fd (Int) passed directly to JNI
  - Completion: params passed as Map<String, Any> with many fields
  - Image support: image_fds passed as IntArray
  - MMProj support: mmproj_fd passed as Int
  - Callback: PartialCompletionCallback object passed to JNI

DaexLlama:
  - Model loading: explicit jstring modelPath + jint ctxId
  - Context params: set via nativeSetConfig() (separate call)
  - Prompts: explicit jstring per call
  - No FD-based loading (uses absolute file paths)
  - No multimodal/image support in current implementation
  - No callback objects (drives generation from Kotlin side)

================================================================================
4. LLAMA.CPP API WRAPPING
================================================================================

kotlin-llamacpp (via llama.rn bridge):
  - Wraps llama.cpp through the rn-llama.cpp and rn-completion.cpp layer
  - Uses llama_rn_context and llama_rn_context_completion classes
  - These classes encapsulate:
    * llama_init_from_model() -> llama_rn_context::loadModel()
    * llama_encode() -> llama_rn_context_completion::loadPrompt()
    * llama_decode() -> llama_rn_context_completion::nextToken()
    * common_sampler_sample() -> llama_rn_context_completion::nextToken()
    * common_chat_parse() -> llama_rn_context_completion::parseChatOutput()
  - Uses common_params, common_sampler, common_chat_format
  - Supports: TTS, multimodal (MTMD), slot-based API, chat templates
  - Grammar-based sampling support
  - Logit bias support
  - Session save/load support
  - Benchmarking support

DaexLlama:
  - Direct llama.cpp API usage (no intermediate RN bridge layer)
  - Wraps:
    * llama_model_load_from_file() -> nativeLoadModel()
    * llama_init_from_model() -> nativePrepareContext()
    * llama_decode() -> decode_tokens_batched() + nativeGenerateNextToken()
    * common_sampler_sample() -> nativeGenerateNextToken()
    * common_chat_format_single() -> chat_add_and_format()
    * common_tokenize() -> used in prompt processing
  - Simpler: fewer abstraction layers
  - Supports: chat templates, sampling, embeddings, context shifting
  - Missing: TTS, multimodal, grammar, sessions, benchmarking

================================================================================
5. LLAMA.RN INTEGRATION (React Native Bridge)
================================================================================

kotlin-llamacpp directly incorporates llama.rn bridge files:
  - rn-llama.cpp: Core React Native llama API
    * llama_rn_context class wraps model loading, context creation
    * Handles GGUF validation, model parameters, KV cache
    * Processes multimodal images via MTMD
    * TTS synthesis via rn-tts.cpp
    * Slot management via rn-slot-manager.cpp

  - rn-completion.cpp: React Native completion API
    * llama_rn_context_completion class wraps the inference loop
    * Handles prompt loading, token generation, chat parsing
    * Stop string detection, context shifting
    * Grammar-based sampling
    * Chat output parsing with tool call support

  - rn-common.hpp: Shared utilities
    * Token string helpers, parameter parsing
    * Chat template formatting
    * Common llama.cpp wrapper utilities

  - rn-tts.cpp: Text-to-speech synthesis
  - rn-slot.cpp / rn-slot-manager.cpp: Slot-based API management

The llama.rn bridge was designed for React Native (JavaScript bridge), but
kotlin-llamacpp repurposes the C++ code for Android JNI. The React Native
callback pattern (JS callback -> Java callback -> JNI callback) is adapted
to pass Java callback objects through JNI.

Key integration points:
  - CMakeLists.txt includes rn-llama.cpp and rn-completion.cpp in the build
  - Symbols are prefixed with LM_ / lm_ to avoid collisions (via sync script)
  - The sync script pulls from cui-llama.rn fork (community-maintained)
  - RNLLAMA_USE_FD_FILE compile flag enables FD-based loading

================================================================================
6. COMPARISON: kotlin-llamacpp vs DaexLlama
================================================================================

+----------------------------------+------------------------+------------------------+
| Aspect                           | kotlin-llamacpp        | DaexLlama              |
+----------------------------------+------------------------+------------------------+
| Native Bridge                    | llama.rn (RN bridge)   | Custom JNI (direct)    |
|                                  | + JNI wrapper          |                        |
+----------------------------------+------------------------+------------------------+
| JNI Code Size                    | ~645 lines (jni.cpp)   | ~655 lines             |
| + RN bridge                      | + ~30K lines (rn-*)    |                        |
+----------------------------------+------------------------+------------------------+
| Abstraction Layers               | Kotlin -> JNI ->       | Kotlin -> JNI ->       |
|                                  | RN bridge -> llama.cpp | llama.cpp              |
+----------------------------------+------------------------+------------------------+
| Context Management               | Opaque pointer         | Explicit struct        |
|                                  | HashMap routing        | std::map + mutex       |
+----------------------------------+------------------------+------------------------+
| Streaming                        | Callback-based         | Flow-based (pull)      |
|                                  | (push from native)     | (pull from Kotlin)     |
+----------------------------------+------------------------+------------------------+
| Error Handling                   | HashMap "error" key    | Int return codes       |
|                                  | + exceptions           | + state machine        |
+----------------------------------+------------------------+------------------------+
| File Loading                     | FD-based (scoped       | Path-based             |
|                                  | storage compliant)     |                        |
+----------------------------------+------------------------+------------------------+
| Multi-ABI                        | 7 variants (CPU        | 1 variant + dynamic    |
|                                  | feature detection)     | backend loading        |
+----------------------------------+------------------------+------------------------+
| Multimodal (Vision)              | YES (MTMD)             | NO                     |
+----------------------------------+------------------------+------------------------+
| TTS Support                      | YES                    | NO                     |
+----------------------------------+------------------------+------------------------+
| Grammar/Constrained Sampling     | YES                    | NO                     |
+----------------------------------+------------------------+------------------------+
| Session Save/Load                | YES                    | NO                     |
+----------------------------------+------------------------+------------------------+
| Benchmarking                     | YES                    | NO                     |
+----------------------------------+------------------------+------------------------+
| Multiple Contexts                | Limited (default 1)    | YES (explicit IDs)     |
+----------------------------------+------------------------+------------------------+
| Dependencies                     | llama.rn bridge        | None (pure llama.cpp)  |
+----------------------------------+------------------------+------------------------+
| Kotlin API Style                 | MutableSharedFlow      | Kotlin Flow +          |
|                                  | + callback objects     | callbackFlow           |
+----------------------------------+------------------------+------------------------+
| Thread Safety                    | Native mutex           | Kotlin Mutex +         |
|                                  | (std::mutex)           | limitedParallelism     |
+----------------------------------+------------------------+------------------------+
| Backend Loading                  | Compile-time selection | Runtime dynamic        |
+----------------------------------+------------------------+------------------------+

================================================================================
7. PROS AND CONS
================================================================================

kotlin-llamacpp (llama.rn bridge approach)
-------------------------------------------

PROS:
  [P1] Feature-rich: multimodal, TTS, grammar, sessions, benchmarking
  [P2] FD-based file loading: works with Android scoped storage natively
  [P3] Multi-variant builds: optimized for specific CPU features
  [P4] Battle-tested: llama.rn is widely used in React Native ecosystem
  [P5] Chat parsing: built-in tool call parsing via common_chat_parse
  [P6] Context shifting: automatic KV cache management for long contexts

CONS:
  [C1] React Native dependency: carries RN bridge code that's unnecessary
       for pure Android. Adds ~30K lines of RN-specific code.
  [C2] Callback complexity: Java callback objects through JNI are fragile,
       harder to reason about, and prone to memory leaks.
  [C3] HashMap overhead: every token callback serializes into a HashMap,
       creating garbage on every emission.
  [C4] Opaque context management: llama_rn_context is opaque, making
       debugging and extension harder.
  [C5] Synchronous blocking: doCompletion() blocks the calling thread for
       the entire generation, relying on callbacks for streaming.
  [C6] Single context default: llamaContextLimit = 1 limits concurrency.
  [C7] Build complexity: 7 library variants, complex CMake, feature detection.
  [C8] Symbol prefixing: requires sync script to prefix all llama.cpp
       symbols, adding maintenance overhead.

DaexLlama (custom JNI approach)
-------------------------------

PROS:
  [P1] Clean architecture: direct JNI -> llama.cpp, no intermediate layer.
  [P2] Kotlin-native streaming: callbackFlow is idiomatic Kotlin, type-safe.
  [P3] Explicit context management: LlamaContext struct is transparent,
       easy to debug, extend, and reason about.
  [P4] Structured errors: int return codes + state machine is cleaner than
       HashMap presence checks.
  [P5] Pull-based generation: Kotlin drives the loop, giving full control
       over cancellation, timing, and buffering.
  [P6] Multiple contexts: explicit createContext()/destroyContext() with
       auto-increment IDs.
  [P7] Dynamic backends: runtime loading of KleidiAI, HTP etc. from assets.
  [P8] Thread safety: Kotlin Mutex + limitedParallelism(1) is cleaner than
       raw std::mutex scattered across JNI code.
  [P9] Simpler build: single .so, straightforward CMake.
  [P10] No RN baggage: zero React Native code or dependencies.

CONS:
  [C1] Fewer features: no multimodal, TTS, grammar, sessions, benchmarking.
  [C2] Path-based loading: uses absolute file paths, not FD-based.
       Requires different file access patterns than kotlin-llamacpp.
  [C3] Single ABI variant: no CPU feature-specific optimizations (relies
       on dynamic backend loading for acceleration).
  [C4] No chat parsing: doesn't parse tool calls or structured output.

================================================================================
8. KEY ARCHITECTURAL DIFFERENCES SUMMARY
================================================================================

The fundamental difference is in the streaming/callback model:

kotlin-llamacpp (Push Model):
  Kotlin: LlamaHelper.predict() -> llama.launchCompletion()
  JNI:    doCompletion() -> [blocking loop] -> callback.onPartialCompletion()
  Kotlin: callback -> sharedFlow.tryEmit() -> UI collects

  The native code PUSHES tokens to Kotlin via callbacks.
  Generation loop is entirely in C++ (rn-completion.cpp::doCompletion).

DaexLlama (Pull Model):
  Kotlin: processUserPrompt() -> nativeProcessUserPrompt() -> returns 0
  Kotlin: while(true) { token = nativeGenerateNextToken() } -> callbackFlow
  Native: nativeGenerateNextToken() does ONE token step -> returns string

  Kotlin PULLS tokens from native code iteratively.
  Generation loop is in Kotlin, native code is stateless per-call.

The pull model (DaexLlama) is more idiomatic for Android/Kotlin:
  - Easier to cancel (just break the Kotlin loop)
  - Easier to test (each JNI call is independent)
  - No callback object lifecycle management
  - Better integration with Kotlin coroutines/Flow
  - Lower memory pressure (no HashMap per token)

================================================================================
9. RECOMMENDATIONS FOR DAEX
================================================================================

1. KEEP: The pull-based streaming model (DaexLlama approach) - it's cleaner
   and more idiomatic for Kotlin/Android.

2. CONSIDER ADDING: FD-based file loading from kotlin-llamacpp. DaexLlama
   uses absolute paths, which may not work with ContentResolver/URI-based
   file access. The FD approach in kotlin-llamacpp is the correct pattern
   for Android scoped storage.

3. CONSIDER ADDING: Multimodal support (MTMD) if DAEX needs vision capabilities.
   This is a significant feature gap.

4. CONSIDER ADDING: Session save/load for context persistence.

5. KEEP: The explicit context struct approach (DaexLlama) over opaque pointers.

6. KEEP: The structured error handling (int return codes + state machine).

7. CONSIDER: Dynamic backend loading (KleidiAI, HTP) from assets - DaexLlama
   already does this well.

8. CONSIDER: Grammar/constrained sampling if structured output is needed.

================================================================================
END OF AUDIT
================================================================================
