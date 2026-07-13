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
 * and any DaexInferenceViewModel instance observing progress. Mirrors the two independent
 * lanes ModelManager already enforces: one generative-model slot shared by chat models and
 * the embedding model, and one Kokoro TTS slot.
 */
object ModelDownloadState {
    private val _generative = MutableStateFlow<GenerativeDownloadStatus?>(null)
    val generative = _generative.asStateFlow()

    private val _kokoro = MutableStateFlow<KokoroDownloadStatus?>(null)
    val kokoro = _kokoro.asStateFlow()

    fun updateGenerative(status: GenerativeDownloadStatus) {
        _generative.value = status
    }

    fun updateKokoro(status: KokoroDownloadStatus) {
        _kokoro.value = status
    }
}
