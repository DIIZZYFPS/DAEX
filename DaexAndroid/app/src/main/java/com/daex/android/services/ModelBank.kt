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
    val supportedBackends: List<BackendType> = listOf(BackendType.CPU, BackendType.GPU),
    val provider: String,
    val familyId: String,
    val familyName: String,
    val sizeName: String,
    val variantName: String,
    val targetHardware: String? = null
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
        supportedBackends = listOf(BackendType.CPU),
        provider = "Nomic",
        familyId = "nomic-embed",
        familyName = "Nomic Embed",
        sizeName = "v1.5",
        variantName = "CPU",
        targetHardware = null
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
            extension = "litertlm",
            supportedBackends = listOf(BackendType.CPU, BackendType.GPU),
            provider = "Google",
            familyId = "gemma-4",
            familyName = "Gemma 4",
            sizeName = "E2B",
            variantName = "LiteRT CPU/GPU",
            targetHardware = null
        ),
        Model(
            id = "gemma-4-E2B-it-qualcomm-sm8750",
            name = "Gemma 4-E2B-it (Qualcomm SM8750)",
            size = 2_590_000_000L,
            description = "Gemma 4-E2B-it AOT compiled for Snapdragon 8 Elite (SM8750). Testing NPU compatibility.",
            requiredRAM = 1_500_000_000L,
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_qualcomm_sm8750.litertlm",
            extension = "litertlm",
            supportedBackends = listOf(BackendType.NPU),
            provider = "Google",
            familyId = "gemma-4",
            familyName = "Gemma 4",
            sizeName = "E2B",
            variantName = "Qualcomm NPU (S25)",
            targetHardware = "SM8750"
        ),
        Model(
            id = "gemma-4-E2B-it-google-tensor-g5",
            name = "Gemma 4-E2B-it (Google Tensor G5)",
            size = 2_590_000_000L,
            description = "Gemma 4-E2B-it AOT compiled for Google Tensor G5. Testing NPU compatibility.",
            requiredRAM = 1_500_000_000L,
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_Google_Tensor_G5.litertlm",
            extension = "litertlm",
            supportedBackends = listOf(BackendType.NPU),
            provider = "Google",
            familyId = "gemma-4",
            familyName = "Gemma 4",
            sizeName = "E2B",
            variantName = "Tensor G5 NPU",
            targetHardware = "Tensor G5"
        ),
        Model(
            id = "gemma-4-E4B-it-litert-lm",
            name = "Gemma 4-E4B-it (LiteRT)",
            size = 3_660_000_000L,
            description = "Gemma 4-E4B-it compiled and quantized for LiteRT-LM. Runs extremely fast on mobile CPU/GPU.",
            requiredRAM = 6_000_000_000L,
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            extension = "litertlm",
            supportedBackends = listOf(BackendType.CPU, BackendType.GPU),
            provider = "Google",
            familyId = "gemma-4",
            familyName = "Gemma 4",
            sizeName = "E4B",
            variantName = "LiteRT CPU/GPU",
            targetHardware = null
        ),
        embeddingModel
    )
}

