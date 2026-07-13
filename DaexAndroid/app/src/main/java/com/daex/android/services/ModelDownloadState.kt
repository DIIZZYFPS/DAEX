package com.daex.android.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DownloadPhase { DOWNLOADING, COMPLETED, ERROR, CANCELLED }

data class GenerativeDownloadStatus(
    val requestId: String,
    val modelId: String,
    val phase: DownloadPhase,
    val percent: Int = 0,
    val error: String? = null
)

data class KokoroDownloadStatus(
    val requestId: String,
    val phase: DownloadPhase,
    val percent: Int = 0,
    val error: String? = null
)

/**
 * Process-wide bridge between [ModelDownloadService] (the sole owner of download I/O,
 * so its own [ModelManager] instance is the only one whose single-flight guards matter)
 * and any DaexInferenceViewModel instance observing progress. Mirrors the three independent
 * lanes ModelManager enforces: one generative-model slot for chat models, one Kokoro TTS
 * slot, and one embedding-model slot - the embedding model gets its own lane (rather than
 * sharing the chat-model slot) specifically so it can download concurrently with the
 * mandatory first-run chat model download instead of waiting for it to finish.
 */
object ModelDownloadState {
    private val _generative = MutableStateFlow<GenerativeDownloadStatus?>(null)
    val generative = _generative.asStateFlow()

    private val _kokoro = MutableStateFlow<KokoroDownloadStatus?>(null)
    val kokoro = _kokoro.asStateFlow()

    // Independent lane for the (much smaller) embedding model, mirroring the Kokoro lane -
    // lets it download concurrently with a mandatory chat-model download during onboarding
    // instead of sharing the single generative slot and being deferred to "next launch".
    private val _embedding = MutableStateFlow<GenerativeDownloadStatus?>(null)
    val embedding = _embedding.asStateFlow()

    fun updateGenerative(status: GenerativeDownloadStatus) {
        _generative.value = status
    }

    fun updateKokoro(status: KokoroDownloadStatus) {
        _kokoro.value = status
    }

    fun updateEmbedding(status: GenerativeDownloadStatus) {
        _embedding.value = status
    }
}
