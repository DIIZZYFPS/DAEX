package com.daex.llama

import kotlinx.coroutines.flow.Flow

/**
 * Core LLM inference interface for DaexLlama.
 * 
 * Thread-safe: all methods can be called from any thread.
 * Internal serialization via single-threaded dispatcher.
 */
interface DaexLlamaEngine {

    /**
     * Current state of the engine.
     */
    val state: EngineState

    /**
     * Load a GGUF model into the given context.
     * @param ctxId context ID (created via createContext, or -1 for default)
     * @param modelPath absolute path to the GGUF file
     * @return true if loaded successfully
     */
    suspend fun loadModel(ctxId: Int, modelPath: String): Boolean

    /**
     * Prepare the context (allocate llama_context, batch, sampler, chat templates).
     * Must be called after loadModel and before any inference.
     * @param ctxId context ID
     * @return true if prepared successfully
     */
    suspend fun prepareContext(ctxId: Int): Boolean

    /**
     * Set inference configuration for a context.
     * @param ctxId context ID
     * @param n_ctx context size (max tokens)
     * @param n_batch batch size
     * @param temp sampling temperature (0.0-2.0, 0.0 = greedy)
     * @param n_predict max tokens to generate per call
     */
    fun setConfig(
        ctxId: Int,
        n_ctx: Int = 8192,
        n_batch: Int = 512,
        temp: Float = 0.3f,
        n_predict: Int = 1024,
    )

    /**
     * Process a system prompt. Formats it via the model's chat template
     * and decodes it into the context.
     */
    suspend fun processSystemPrompt(ctxId: Int, systemPrompt: String): Boolean

    /**
     * Process a user prompt. Formats it, decodes it, then starts generation.
     * @return a Flow of token strings. Each emission is a piece of the generated text.
     *         The flow completes (emits nothing more) when generation finishes or is cancelled.
     */
    fun processUserPrompt(ctxId: Int, userPrompt: String): Flow<String>

    /**
     * Cancel ongoing generation for a context.
     */
    fun cancelGeneration(ctxId: Int)

    /**
     * Reset conversation history for a context (keeps model loaded).
     */
    fun resetConversation(ctxId: Int)

    /**
     * Unload the model from a context.
     */
    fun unloadModel(ctxId: Int)

    /**
     * Create a new context and return its ID.
     */
    fun createContext(): Int

    /**
     * Destroy a context.
     */
    fun destroyContext(ctxId: Int)

    /**
     * Get the active backends detected at init time.
     * Example: "CPU, KleidiAI, HTP" or "CPU"
     */
    fun getActiveBackends(): String

    /**
     * Configure Hexagon NPU backend parameters.
     * For best results, call this before the engine's nativeInit() is invoked.
     * 
     * @param nDevices Number of NPU sessions. Use 1 for <4B models, 2 for 8B, 4 for 20B.
     * @param nHvxThreads Number of HVX hardware threads. 0 = use all available.
     * @param verbose Verbosity level: 0=off, 1=on (logs NPU operations).
     * @return true if the native library is loaded (settings applied), false if not.
     */
    fun configureNpu(nDevices: Int = 1, nHvxThreads: Int = 0, verbose: Int = 0): Boolean

    /**
     * Check if any NPU/GPU backend is available (Hexagon/HTP, OpenCL, etc.).
     * @return true if a non-CPU backend is registered.
     */
    fun isNpuAvailable(): Boolean

    /**
     * Get system info from llama.cpp.
     */
    fun getSystemInfo(): String

    /**
     * Load an embedding model into a context.
     * @param ctxId context ID (create one first with createContext())
     * @param modelPath absolute path to the GGUF embedding model
     * @return true if loaded successfully
     */
    suspend fun loadEmbeddingModel(ctxId: Int, modelPath: String): Boolean

    /**
     * Compute an embedding vector for the given text.
     * @param ctxId context ID (must have an embedding model loaded)
     * @param text text to embed
     * @return float array embedding vector
     */
    suspend fun getEmbedding(ctxId: Int, text: String): FloatArray

    /**
     * Clean up all resources.
     */
    fun destroy()

    /**
     * Engine states
     */
    sealed class EngineState {
        object Uninitialized : EngineState()
        object Initializing : EngineState()
        object Initialized : EngineState()
        object LoadingModel : EngineState()
        object UnloadingModel : EngineState()
        object PreparingContext : EngineState()
        object ModelReady : EngineState()
        object ProcessingPrompt : EngineState()
        object Generating : EngineState()
        object Idle : EngineState()
        data class Error(val message: String) : EngineState()
    }
}
