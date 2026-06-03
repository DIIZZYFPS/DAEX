package com.daex.android.services

data class Model(
    val id: String,
    val name: String,
    val size: Long,
    val description: String,
    val requiredRAM: Long,
    val downloadUrl: String,
    val extension: String,
    val isEmbedding: Boolean = false,
    val supportedBackends: List<BackendType> = listOf(BackendType.CPU, BackendType.GPU)
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
        isEmbedding = true,
        supportedBackends = listOf(BackendType.CPU)
    )

    val generativeModels: List<Model>
        get() = models.filter { !it.isEmbedding }

    val models: List<Model> = listOf(
        Model(
            id = "gemma-3-1b-it-google-tensor-g5",
            name = "Gemma 3-1B-it (Google Tensor G5)",
            size = 1_704_000_000L,
            description = "Gemma 3-1B-it AOT compiled for Google Tensor G5 NPU (Pixel 10 Pro Fold). Officially supported.",
            requiredRAM = 1_000_000_000L,
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_q8_ekv1280_Google_Tensor_G5.litertlm",
            extension = "litertlm",
            supportedBackends = listOf(BackendType.NPU)
        ),
        Model(
            id = "gemma-3-1b-it-qualcomm-sm8750",
            name = "Gemma 3-1B-it (Qualcomm SM8750)",
            size = 658_000_000L,
            description = "Gemma 3-1B-it AOT compiled for Snapdragon 8 Elite NPU (SM8750 / Samsung Galaxy S26 Ultra). Officially supported.",
            requiredRAM = 1_000_000_000L,
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm",
            extension = "litertlm",
            supportedBackends = listOf(BackendType.NPU)
        ),
        Model(
            id = "gemma-3-1b-it-qualcomm-sm8650",
            name = "Gemma 3-1B-it (Qualcomm SM8650)",
            size = 658_000_000L,
            description = "Gemma 3-1B-it AOT compiled for Snapdragon 8 Gen 3 NPU (SM8650 / Samsung Galaxy S24 Ultra). Officially supported.",
            requiredRAM = 1_000_000_000L,
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_q4_ekv1280_sm8650.litertlm",
            extension = "litertlm",
            supportedBackends = listOf(BackendType.NPU)
        ),
        Model(
            id = "gemma-4-E2B-it-litert-lm",
            name = "Gemma 4-E2B-it (LiteRT)",
            size = 2_590_000_000L,
            description = "Gemma 4-E2B-it compiled and quantized for LiteRT-LM. Runs extremely fast on mobile CPU/GPU.",
            requiredRAM = 1_500_000_000L,
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            extension = "litertlm",
            supportedBackends = listOf(BackendType.CPU, BackendType.GPU)
        ),
        Model(
            id = "gemma-4-E2B-it-qualcomm-sm8750",
            name = "Gemma 4-E2B-it (Qualcomm SM8750)",
            size = 2_590_000_000L,
            description = "Gemma 4-E2B-it AOT compiled for Snapdragon 8 Elite (SM8750). Testing NPU compatibility.",
            requiredRAM = 1_500_000_000L,
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_qualcomm_sm8750.litertlm",
            extension = "litertlm",
            supportedBackends = listOf(BackendType.NPU)
        ),
        Model(
            id = "gemma-4-E2B-it-google-tensor-g5",
            name = "Gemma 4-E2B-it (Google Tensor G5)",
            size = 2_590_000_000L,
            description = "Gemma 4-E2B-it AOT compiled for Google Tensor G5. Testing NPU compatibility.",
            requiredRAM = 1_500_000_000L,
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_Google_Tensor_G5.litertlm",
            extension = "litertlm",
            supportedBackends = listOf(BackendType.NPU)
        ),
        Model(
            id = "gemma-4-E4B-it-litert-lm",
            name = "Gemma 4-E4B-it (LiteRT)",
            size = 3_660_000_000L,
            description = "Gemma 4-E4B-it compiled and quantized for LiteRT-LM. Runs extremely fast on mobile CPU/GPU.",
            requiredRAM = 6_000_000_000L,
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            extension = "litertlm",
            supportedBackends = listOf(BackendType.CPU, BackendType.GPU)
        ),
        embeddingModel
    )
}
