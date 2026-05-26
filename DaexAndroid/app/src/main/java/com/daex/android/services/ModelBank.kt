package com.daex.android.services

data class Model(
    val id: String,
    val name: String,
    val size: Long,
    val description: String,
    val requiredRAM: Long,
    val downloadUrl: String,
    val extension: String,
    val isEmbedding: Boolean = false
)

object ModelBank {
    val embeddingModel = Model(
        id = "nomic-embed-text-v1.5-q4_k_m",
        name = "Nomic Embed Text v1.5",
        size = 85_000_000L,
        description = "High-performance embedding model for vector search and RAG.",
        requiredRAM = 500_000_000L,
        downloadUrl = "https://huggingface.co/nomic-ai/nomic-embed-text-v1.5-GGUF/resolve/main/nomic-embed-text-v1.5.Q4_K_M.gguf",
        extension = "tflite",
        isEmbedding = true
    )

    val generativeModels: List<Model>
        get() = models.filter { !it.isEmbedding }

    val models: List<Model> = listOf(
        Model(
            id = "gemma-4-E2B-it-litert-lm",
            name = "Gemma 4-E2B-it (LiteRT)",
            size = 2_590_000_000L,
            description = "Gemma 4-E2B-it compiled and quantized for LiteRT-LM. Runs extremely fast on mobile CPU/GPU.",
            requiredRAM = 1_500_000_000L,
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            extension = "litertlm"
        ),
        Model(
            id = "gemma-4-E4B-it-litert-lm",
            name = "Gemma 4-E4B-it (LiteRT)",
            size = 3_660_000_000L,
            description = "Gemma 4-E4B-it compiled and quantized for LiteRT-LM. Runs extremely fast on mobile CPU/GPU.",
            requiredRAM = 6_000_000_000L,
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            extension = "litertlm"
        ),
        embeddingModel
    )
}
