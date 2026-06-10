package com.daex.android.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper

class SpeechManager(
    private val context: Context,
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onResult: (String) -> Unit,
    private val onStateChanged: (VoiceState) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun startListening() {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                onStateChanged(VoiceState.LISTENING)
                            }

                            override fun onBeginningOfSpeech() {}

                            override fun onRmsChanged(rmsdB: Float) {
                                // rmsdB usually ranges from -2f to 10f+ depending on volume
                                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                                onAmplitudeChanged(normalized)
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                onStateChanged(VoiceState.PROCESSING)
                            }

                            override fun onError(error: Int) {
                                onAmplitudeChanged(0f)
                                onStateChanged(VoiceState.IDLE)
                            }

                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty()) {
                                    onResult(matches[0])
                                }
                                onAmplitudeChanged(0f)
                                onStateChanged(VoiceState.IDLE)
                            }

                            override fun onPartialResults(partialResults: Bundle?) {}

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(intent)
                onStateChanged(VoiceState.LISTENING)
            } catch (e: Exception) {
                android.util.Log.e("SpeechManager", "Failed to start speech recognition", e)
                onStateChanged(VoiceState.IDLE)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                android.util.Log.e("SpeechManager", "Failed to stop speech recognition", e)
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                android.util.Log.e("SpeechManager", "Failed to destroy speech recognizer", e)
            }
        }
    }
}
