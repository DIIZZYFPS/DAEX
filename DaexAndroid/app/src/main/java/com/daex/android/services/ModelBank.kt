package com.daex.android.services

data class Model(
    val id: String,
    val name: String,
    val size: Long,
    val description: String,
    val requiredRAM: Long,
    val downloadUrl: String
)

object ModelBank {
    val models: List<Model> = listOf(
        Model(
            id = "gemma-4-E4B-it-Q4_K_M",
            name = "Gemma 4-E4B-it-Q4_K_M",
            size = 2_500_000_000L,
            description = "Gemma 4-E4B-it-Q4_K_M is a 4-billion parameter language model. It is a 4-bit quantized model optimized for mobile performance.",
            requiredRAM = 6_000_000_000L,
            downloadUrl = "https://huggingface.co/bartowski/google_gemma-4-E4B-it-GGUF/resolve/main/google_gemma-4-E4B-it-Q4_K_M.gguf"
        ),
        Model(
            id = "gemma-4-E2B-it-Q4_K_M",
            name = "Gemma 4-E2B-it-Q4_K_M",
            size = 1_500_000_000L,
            description = "Gemma 4-E2B-it-Q4_K_M is a 2-billion parameter lightweight model, perfect for entry-level devices with limited RAM.",
            requiredRAM = 3_500_000_000L,
            downloadUrl = "https://huggingface.co/bartowski/google_gemma-4-E2B-it-GGUF/resolve/main/google_gemma-4-E2B-it-Q4_K_M.gguf"
        )
    )
}
