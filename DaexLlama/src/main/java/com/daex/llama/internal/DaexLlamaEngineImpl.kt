package com.daex.llama.internal

import android.content.Context
import android.os.Looper
import android.util.Log
import com.daex.llama.DaexLlamaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * JNI-backed LLM inference engine for DaexLlama.
 */
class DaexLlamaEngineImpl(
    private val context: Context,
) : DaexLlamaEngine {

    // State management
    private val _state = MutableStateFlow<DaexLlamaEngine.EngineState>(
        DaexLlamaEngine.EngineState.Uninitialized
    )
    
    // Match interface property exactly
    override val state: DaexLlamaEngine.EngineState get() = _state.value

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

    private suspend fun nativeInit() {
        val libDir = extractBackendsDir()
        withContext(nativeDispatcher) {
            nativeInit(libDir)
        }
    }

    private external fun nativeInit(nativeLibDir: String)
    private external fun nativeCreateContext(): Int
    private external fun nativeDestroyContext(ctxId: Int)
    private external fun nativeLoadModel(ctxId: Int, modelPath: String): Int
    private external fun nativePrepareContext(ctxId: Int): Int
    private external fun nativeSetConfig(
        ctxId: Int,
        n_ctx: Int,
        n_batch: Int,
        temp: Float,
        n_predict: Int,
    )
    private external fun nativeProcessSystemPrompt(ctxId: Int, systemPrompt: String): Int
    private external fun nativeProcessUserPrompt(ctxId: Int, userPrompt: String): Int
    private external fun nativeGenerateNextToken(ctxId: Int): String?
    private external fun nativeCancelGeneration(ctxId: Int)
    private external fun nativeResetConversation(ctxId: Int)
    private external fun nativeUnloadModel(ctxId: Int)
    private external fun nativeShutdown()
    private external fun nativeSystemInfo(): String
    private external fun nativeActiveBackends(): String
    private external fun nativeConfigureNPU(nDevices: Int, nHvxThreads: Int, verbose: Int): Int
    private external fun nativeIsNpuAvailable(): Int
    private external fun nativeLoadEmbeddingModel(ctxId: Int, modelPath: String): Int
    private external fun nativeGetEmbedding(ctxId: Int, text: String): FloatArray

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
                val result = nativeLoadModel(ctxId, modelPath)
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
                val result = nativePrepareContext(ctxId)
                if (result == 0) {
                    nativeSetConfig(ctxId, configNctx, configNbatch, configTemp, configNpredict)
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
        nativeSetConfig(ctxId, n_ctx, n_batch, temp, n_predict)
    }

    override suspend fun processSystemPrompt(ctxId: Int, systemPrompt: String): Boolean {
        return nativeMutex.withLock(nativeDispatcher) {
            _state.update { DaexLlamaEngine.EngineState.ProcessingPrompt }
            try {
                this.systemPrompt = systemPrompt
                val result = nativeProcessSystemPrompt(ctxId, systemPrompt)
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
            val job = kotlinx.coroutines.GlobalScope.launch(nativeDispatcher) {
                nativeMutex.withLock {
                    _state.update { DaexLlamaEngine.EngineState.ProcessingPrompt }
                    val result = nativeProcessUserPrompt(ctxId, userPrompt)
                    if (result != 0) {
                        _state.update { DaexLlamaEngine.EngineState.Error("User prompt processing failed") }
                        close()
                        return@withLock
                    }
                    _state.update { DaexLlamaEngine.EngineState.Generating }

                    while (true) {
                        val token = nativeGenerateNextToken(ctxId)
                        if (token == null) {
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
                job.cancel()
                nativeCancelGeneration(ctxId)
            }
        }
    }

    override fun cancelGeneration(ctxId: Int) {
        nativeCancelGeneration(ctxId)
    }

    override fun resetConversation(ctxId: Int) {
        nativeResetConversation(ctxId)
        _state.update { DaexLlamaEngine.EngineState.Idle }
    }

    override fun unloadModel(ctxId: Int) {
        nativeUnloadModel(ctxId)
        _state.update { DaexLlamaEngine.EngineState.ModelReady }
    }

    override fun createContext(): Int {
        return nativeCreateContext()
    }

    override fun destroyContext(ctxId: Int) {
        nativeDestroyContext(ctxId)
    }

    override fun getActiveBackends(): String {
        return try {
            nativeActiveBackends()
        } catch (e: UnsatisfiedLinkError) {
            "CPU"
        }
    }

    override fun configureNpu(nDevices: Int, nHvxThreads: Int, verbose: Int): Boolean {
        // NOTE: This sets env vars that the Hexagon backend reads at init time.
        // For best results, call this before nativeInit() is called.
        // If called after init, the native side logs a warning but still applies the settings.
        return try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Log.w(TAG, "configureNpu() called on main thread; this call is synchronous")
            }
            val result = runBlocking(nativeDispatcher) {
                nativeMutex.withLock {
                    nativeConfigureNPU(nDevices, nHvxThreads, verbose)
                }
            }
            if (result == 0) {
                Log.i(TAG, "NPU config applied: $nDevices devices, $nHvxThreads HVX threads")
                true
            } else {
                Log.w(TAG, "NPU config rejected or failed (code=$result)")
                false
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "NPU config failed: native library not loaded")
            false
        }
    }

    override fun isNpuAvailable(): Boolean {
        return try {
            nativeIsNpuAvailable() == 1
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    override fun getSystemInfo(): String {
        return try {
            nativeSystemInfo()
        } catch (e: UnsatisfiedLinkError) {
            "Native library not loaded"
        }
    }

    override suspend fun loadEmbeddingModel(ctxId: Int, modelPath: String): Boolean {
        return nativeMutex.withLock(nativeDispatcher) {
            try {
                val result = nativeLoadEmbeddingModel(ctxId, modelPath)
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
                nativeGetEmbedding(ctxId, text)
            } catch (e: Exception) {
                Log.e(TAG, "getEmbedding failed", e)
                floatArrayOf()
            }
        }
    }

    override fun destroy() {
        nativeShutdown()
        _state.update { DaexLlamaEngine.EngineState.Uninitialized }
    }

    companion object {
        private const val TAG = "DaexLlama"
        private const val DEFAULT_CTX_ID = -1
        private const val BACKENDS_DIR = "backends"

        @Volatile
        private var instance: DaexLlamaEngineImpl? = null
        
        private val creationMutex = Mutex()

        suspend fun create(appContext: Context): DaexLlamaEngineImpl {
            return instance ?: creationMutex.withLock {
                instance ?: run {
                    val engine = DaexLlamaEngineImpl(appContext)
                    engine._state.update { DaexLlamaEngine.EngineState.Initializing }
                    try {
                        engine.nativeInit()
                        engine._state.update { DaexLlamaEngine.EngineState.Initialized }
                        engine.nativeCtxId = engine.createContext()
                    } catch (e: Exception) {
                        engine._state.update { DaexLlamaEngine.EngineState.Error(e.message ?: "Init failed") }
                    }
                    instance = engine
                    engine
                }
            }
        }

        fun get(): DaexLlamaEngineImpl? = instance

        fun destroy() {
            instance?.destroy()
            instance = null
        }
    }
}
