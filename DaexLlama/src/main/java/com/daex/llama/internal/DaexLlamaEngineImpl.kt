package com.daex.llama.internal

import android.content.Context
import com.daex.llama.DaexLlamaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.util.Log

/**
 * JNI-backed LLM inference engine for DaexLlama.
 * 
 * Architecture:
 *   - All native calls are serialized through a single-threaded IO dispatcher
 *     with limited parallelism (1) to prevent JNI thread conflicts.
 *   - Generation is exposed as a Flow<String> that emits token pieces.
 *   - Context IDs are managed by the native layer; the default context is -1.
 * 
 * Native library: libdaex-llama.so
 */
class DaexLlamaEngineImpl(
    private val context: Context,
) : DaexLlamaEngine {

    companion object {
        private const val TAG = "DaexLlama"
        private const val DEFAULT_CTX_ID = -1
        private const val BACKENDS_DIR = "backends"
    }

    // State management
    private val _state = MutableStateFlow<DaexLlamaEngine.EngineState>(
        DaexLlamaEngine.EngineState.Uninitialized
    )
    override val state: StateFlow<DaexLlamaEngine.EngineState> = _state.asStateFlow()

    // Native context ID for the default context
    private var nativeCtxId = DEFAULT_CTX_ID

    // Single-threaded dispatcher for all native calls
    private val nativeDispatcher = Dispatchers.IO.limitedParallelism(1)

    // Mutex to prevent concurrent suspend calls
    private val nativeMutex = Mutex()

    // Config defaults
    private var configNctx = 8192
    private var configNbatch = 512
    private var configTemp = 0.3f
    private var configNpredict = 1024

    // System prompt (stored for reset)
    private var systemPrompt: String? = null

    // --------------------------------------------------------------------------
    // Native method declarations
    // --------------------------------------------------------------------------

    init {
        System.loadLibrary("daex-llama")
    }

    // Called by init() via coroutine
    private suspend fun nativeInit() {
        val libDir = extractBackendsDir()
        init(libDir)
    }

    private external fun init(nativeLibDir: String)
    private external fun createContext(): Int
    private external fun destroyContext(ctxId: Int)
    private external fun loadModel(ctxId: Int, modelPath: String): Int
    private external fun prepareContext(ctxId: Int): Int
    private external fun setConfig(
        ctxId: Int,
        n_ctx: Int,
        n_batch: Int,
        temp: Float,
        n_predict: Int,
    )
    private external fun processSystemPrompt(ctxId: Int, systemPrompt: String): Int
    private external fun processUserPrompt(ctxId: Int, userPrompt: String): Int
    private external fun generateNextToken(ctxId: Int): String?
    private external fun cancelGeneration(ctxId: Int)
    private external fun resetConversation(ctxId: Int)
    private external fun unloadModel(ctxId: Int)
    private external fun shutdown()
    private external fun systemInfo(): String
    private external fun activeBackends(): String
    private external fun loadEmbeddingModel(ctxId: Int, modelPath: String): Int
    private external fun getEmbedding(ctxId: Int, text: String): FloatArray

    // --------------------------------------------------------------------------
    // Backend extraction
    // --------------------------------------------------------------------------

    private fun extractBackendsDir(): String {
        val backendsDir = File(context.filesDir, BACKENDS_DIR)
        if (!backendsDir.exists()) {
            backendsDir.mkdirs()
            extractBackendLibs(backendsDir)
        }
        return backendsDir.absolutePath
    }

    /**
     * Extract backend .so files from APK assets to a writable directory.
     * The CMakeLists.txt expects backends in a directory for
     * ggml_backend_load_all_from_path() to find them.
     * 
     * Expected assets:
     *   assets/backends/libggml-kleidiai.so  (if available)
     *   assets/backends/libggml-htp-v81.so   (if Hexagon SDK available)
     */
    private fun extractBackendLibs(destDir: File) {
        context.assets.list("backends")?.forEach { fileName ->
            if (fileName.endsWith(".so")) {
                val destFile = File(destDir, fileName)
                if (!destFile.exists()) {
                    context.assets.open("backends/$fileName").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Extracted backend: $fileName")
                }
            }
        }
    }

    // --------------------------------------------------------------------------
    // Engine lifecycle
    // --------------------------------------------------------------------------

    override suspend fun loadModel(ctxId: Int, modelPath: String): Boolean {
        return nativeMutex.withLock(nativeDispatcher) {
            _state.update { DaexLlamaEngine.EngineState.LoadingModel }
            try {
                val result = loadModel(ctxId, modelPath)
                if (result == 0) {
                    _state.update { DaexLlamaEngine.EngineState.ModelReady }
                    true
                } else {
                    _state.update { DaexLlamaEngine.EngineState.Error("Failed to load model: $modelPath") }
                    false
                }
            } catch (e: Exception) {
                _state.update { DaexLlamaEngine.EngineState.Error(e.message ?: "loadModel failed") }
                false
            }
        }
    }

    override suspend fun prepareContext(ctxId: Int): Boolean {
        return nativeMutex.withLock(nativeDispatcher) {
            _state.update { DaexLlamaEngine.EngineState.PreparingContext }
            try {
                val result = prepareContext(ctxId)
                if (result == 0) {
                    setConfig(ctxId, configNctx, configNbatch, configTemp, configNpredict)
                    _state.update { DaexLlamaEngine.EngineState.ModelReady }
                    true
                } else {
                    _state.update { DaexLlamaEngine.EngineState.Error("Failed to prepare context") }
                    false
                }
            } catch (e: Exception) {
                _state.update { DaexLlamaEngine.EngineState.Error(e.message ?: "prepareContext failed") }
                false
            }
        }
    }

    override fun setConfig(
        ctxId: Int,
        n_ctx: Int,
        n_batch: Int,
        temp: Float,
        n_predict: Int,
    ) {
        configNctx = n_ctx
        configNbatch = n_batch
        configTemp = temp
        configNpredict = n_predict
        // Apply to native layer
        setConfig(ctxId, n_ctx, n_batch, temp, n_predict)
    }

    override suspend fun processSystemPrompt(ctxId: Int, systemPrompt: String): Boolean {
        return nativeMutex.withLock(nativeDispatcher) {
            _state.update { DaexLlamaEngine.EngineState.ProcessingPrompt }
            try {
                this.systemPrompt = systemPrompt
                val result = processSystemPrompt(ctxId, systemPrompt)
                if (result == 0) {
                    _state.update { DaexLlamaEngine.EngineState.Idle }
                    true
                } else {
                    _state.update { DaexLlamaEngine.EngineState.Error("System prompt processing failed") }
                    false
                }
            } catch (e: Exception) {
                _state.update { DaexLlamaEngine.EngineState.Error(e.message ?: "processSystemPrompt failed") }
                false
            }
        }
    }

    override fun processUserPrompt(ctxId: Int, userPrompt: String): Flow<String> {
        return callbackFlow {
            // Process prompt in background
            val job = kotlinx.coroutines.GlobalScope.launch(nativeDispatcher) {
                nativeMutex.withLock {
                    _state.update { DaexLlamaEngine.EngineState.ProcessingPrompt }
                    val result = processUserPrompt(ctxId, userPrompt)
                    if (result != 0) {
                        _state.update { DaexLlamaEngine.EngineState.Error("User prompt processing failed") }
                        trySend(null) // signal error
                        close()
                        return@withLock
                    }
                    _state.update { DaexLlamaEngine.EngineState.Generating }

                    // Generate tokens in a loop
                    while (true) {
                        val token = generateNextToken(ctxId)
                        if (token == null) {
                            // Generation complete or error
                            _state.update { DaexLlamaEngine.EngineState.Idle }
                            break
                        }
                        if (token.isNotEmpty()) {
                            trySend(token)
                        }
                    }
                    close()
                }
            }

            awaitClose {
                // If the flow is cancelled before completion, cancel generation
                job.cancel()
                cancelGeneration(ctxId)
            }
        }
    }

    override fun cancelGeneration(ctxId: Int) {
        cancelGeneration(ctxId)
    }

    override fun resetConversation(ctxId: Int) {
        resetConversation(ctxId)
        _state.update { DaexLlamaEngine.EngineState.Idle }
    }

    override fun unloadModel(ctxId: Int) {
        nativeMutex.withLock(nativeDispatcher) {
            _state.update { DaexLlamaEngine.EngineState.UnloadingModel }
            unloadModel(ctxId)
            _state.update { DaexLlamaEngine.EngineState.ModelReady }
        }
    }

    override fun createContext(): Int {
        return createContext()
    }

    override fun destroyContext(ctxId: Int) {
        destroyContext(ctxId)
    }

    override fun getActiveBackends(): String {
        return try {
            activeBackends()
        } catch (e: UnsatisfiedLinkError) {
            "CPU"
        }
    }

    override fun getSystemInfo(): String {
        return try {
            systemInfo()
        } catch (e: UnsatisfiedLinkError) {
            "Native library not loaded"
        }
    }

    override suspend fun loadEmbeddingModel(ctxId: Int, modelPath: String): Boolean {
        return nativeMutex.withLock(nativeDispatcher) {
            try {
                val result = loadEmbeddingModel(ctxId, modelPath)
                result == 0
            } catch (e: Exception) {
                Log.e(TAG, "loadEmbeddingModel failed", e)
                false
            }
        }
    }

    override suspend fun getEmbedding(ctxId: Int, text: String): FloatArray {
        return nativeMutex.withLock(nativeDispatcher) {
            try {
                val embedding = getEmbedding(ctxId, text)
                embedding ?: floatArrayOf()
            } catch (e: Exception) {
                Log.e(TAG, "getEmbedding failed", e)
                floatArrayOf()
            }
        }
    }

    override fun destroy() {
        shutdown()
        _state.update { DaexLlamaEngine.EngineState.Uninitialized }
    }

    // --------------------------------------------------------------------------
    // Companion factory
    // --------------------------------------------------------------------------

    companion object {
        // Singleton instance (created via create())
        @Volatile
        private var instance: DaexLlamaEngineImpl? = null

        /**
         * Create and initialize the engine singleton.
         * Must be called on the main thread or a thread with a valid Context.
         */
        suspend fun create(appContext: Context): DaexLlamaEngineImpl {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val engine = DaexLlamaEngineImpl(appContext)
                    // Initialize native layer
                    engine._state.update { DaexLlamaEngine.EngineState.Initializing }
                    try {
                        engine.nativeInit()
                        engine._state.update { DaexLlamaEngine.EngineState.Initialized }
                        // Create default context
                        engine.nativeCtxId = engine.createContext()
                    } catch (e: Exception) {
                        engine._state.update { DaexLlamaEngine.EngineState.Error(e.message ?: "Init failed") }
                    }
                    engine
                }
            }
        }

        /**
         * Get the singleton instance, or null if not created.
         */
        fun get(): DaexLlamaEngineImpl? = instance

        /**
         * Destroy the singleton.
         */
        fun destroy() {
            instance?.destroy()
            instance = null
        }
    }
}
