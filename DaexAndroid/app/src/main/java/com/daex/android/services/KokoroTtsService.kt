package com.daex.android.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class KokoroTtsService(private val context: Context) {
    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private val mutex = Mutex()
    private val ttsScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var activeSpeakJob: Job? = null
    private val speakChannel = Channel<SpeakRequest>(Channel.UNLIMITED)
    private val playbackChannel = Channel<PlaybackRequest>(Channel.UNLIMITED)
    private var activePlaybackJob: Job? = null

    // Speaking state callback
    var onSpeakingStateChanged: ((Boolean) -> Unit)? = null

    @Volatile
    private var _isSpeaking = false

    var isSpeaking: Boolean
        get() = _isSpeaking
        private set(value) {
            if (_isSpeaking != value) {
                _isSpeaking = value
                onSpeakingStateChanged?.invoke(value)
            }
        }

    @Volatile
    var currentPlaybackRms = 0f

    class SpeakRequest(val text: String, val voiceId: Int)
    class PlaybackRequest(val samples: FloatArray, val text: String)

    init {
        startSynthesisPipeline()
        startPlaybackConsumer()
    }

    fun initTts() {
        ttsScope.launch {
            mutex.withLock {
                if (tts != null) return@launch
                try {
                    android.util.Log.i("KokoroTtsService", "Initializing Kokoro TTS engine...")
                    
                    // Native piper-phonemize requires espeak-ng-data to be in the local file system.
                    // Copy it from assets to internal storage if not already present.
                    val localDataDir = File(context.filesDir, "kokoro/espeak-ng-data")
                    if (!localDataDir.exists() || localDataDir.listFiles().isNullOrEmpty()) {
                        localDataDir.mkdirs()
                        android.util.Log.i("KokoroTtsService", "Extracting espeak-ng-data from assets to $localDataDir...")
                        copyAssetsFolder("kokoro-en-v0_19/espeak-ng-data", localDataDir)
                        android.util.Log.i("KokoroTtsService", "Extraction complete.")
                    }

                    val config = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            kokoro = OfflineTtsKokoroModelConfig(
                                model = "kokoro-en-v0_19/model.onnx",
                                voices = "kokoro-en-v0_19/voices.bin",
                                tokens = "kokoro-en-v0_19/tokens.txt",
                                dataDir = localDataDir.absolutePath
                            ),
                            numThreads = 2,
                            debug = false
                        )
                    )
                    tts = OfflineTts(context.assets, config)
                    android.util.Log.i("KokoroTtsService", "Kokoro TTS engine initialized successfully.")
                } catch (e: Exception) {
                    android.util.Log.e("KokoroTtsService", "Failed to initialize OfflineTts", e)
                }
            }
        }
    }

    private fun copyAssetsFolder(srcPath: String, destDir: File) {
        val assetManager = context.assets
        var files: Array<String>? = null
        try {
            files = assetManager.list(srcPath)
        } catch (e: IOException) {
            android.util.Log.e("KokoroTtsService", "Failed to list assets at: $srcPath", e)
        }

        if (files.isNullOrEmpty()) {
            // It's a file, copy directly
            copyAssetFile(srcPath, destDir)
        } else {
            // It's a directory, create it and recurse
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            for (filename in files) {
                val nextSrc = if (srcPath.isEmpty()) filename else "$srcPath/$filename"
                val nextDest = File(destDir, filename)
                copyAssetsFolder(nextSrc, nextDest)
            }
        }
    }

    private fun copyAssetFile(srcPath: String, destFile: File) {
        try {
            context.assets.open(srcPath).use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    val buffer = ByteArray(4096)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("KokoroTtsService", "Error copying file: $srcPath to ${destFile.absolutePath}", e)
        }
    }

    fun speak(text: String, voiceId: Int) {
        if (text.isBlank()) return
        android.util.Log.i("KokoroTtsService", "Queuing text for speak: \"$text\" with voiceId: $voiceId")
        speakChannel.trySend(SpeakRequest(text, voiceId))
    }

    private fun startSynthesisPipeline() {
        ttsScope.launch {
            for (request in speakChannel) {
                // When text enters the queue, mark speaking = true immediately so we are in speaking state
                isSpeaking = true
                val job = launch {
                    val ttsInstance = mutex.withLock { tts }
                    if (ttsInstance == null) {
                        android.util.Log.w("KokoroTtsService", "TTS instance is null, attempting automatic lazy initialization...")
                        initTts()
                        var attempts = 0
                        var resolvedTts: OfflineTts? = null
                        while (attempts < 15) {
                            delay(300)
                            resolvedTts = mutex.withLock { tts }
                            if (resolvedTts != null) break
                            attempts++
                        }
                        if (resolvedTts == null) {
                            android.util.Log.e("KokoroTtsService", "TTS initialization timed out, cannot synthesize.")
                            return@launch
                        }
                    }

                    val activeTts = mutex.withLock { tts } ?: return@launch
                    try {
                        android.util.Log.i("KokoroTtsService", "Pipeline: Synthesizing: \"${request.text}\"")
                        val generatedAudio = withContext(Dispatchers.Default) {
                            activeTts.generate(request.text, request.voiceId, 1.0f)
                        }
                        yield()
                        val samples = generatedAudio.samples
                        if (samples.isNotEmpty()) {
                            normalizeSamples(samples)
                            playbackChannel.send(PlaybackRequest(samples, request.text))
                        } else {
                            android.util.Log.w("KokoroTtsService", "Pipeline: Generated audio samples are empty for: \"${request.text}\"")
                        }
                    } catch (e: CancellationException) {
                        android.util.Log.i("KokoroTtsService", "Pipeline: Synthesis canceled for: \"${request.text}\"")
                    } catch (e: Exception) {
                        android.util.Log.e("KokoroTtsService", "Pipeline: Synthesis failed for: \"${request.text}\"", e)
                    }
                }
                activeSpeakJob = job
                job.join()
            }
        }
    }

    private fun startPlaybackConsumer() {
        ttsScope.launch {
            for (playbackReq in playbackChannel) {
                isSpeaking = true
                val job = launch {
                    playAudioSamples(playbackReq.samples, playbackReq.text)
                }
                activePlaybackJob = job
                job.join()
                
                // Only clear speaking when both queues are drained
                if (speakChannel.isEmpty && playbackChannel.isEmpty) {
                    isSpeaking = false
                }
            }
        }
    }

    private suspend fun playAudioSamples(samples: FloatArray, text: String) {
        try {
            android.util.Log.i("KokoroTtsService", "Pipeline: Playing ${samples.size} samples for: \"$text\"")

            val sampleRate = 24000
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )

            synchronized(this) {
                if (audioTrack == null) {
                    audioTrack = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                }
            }

            val track = audioTrack ?: return

            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }

            // Stream samples in chunks of 4800 (200ms) to allow responsive interruption
            val chunkSize = 4800
            var offset = 0
            try {
                while (offset < samples.size) {
                    yield() // Handle immediate interruption
                    val writeLen = minOf(chunkSize, samples.size - offset)
                    
                    // Calculate RMS of currently playing chunk
                    var sum = 0f
                    for (i in offset until (offset + writeLen)) {
                        val s = samples[i]
                        sum += s * s
                    }
                    val newRms = Math.sqrt((sum / writeLen).toDouble()).toFloat()
                    
                    val written = track.write(samples, offset, writeLen, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) {
                        break
                    }
                    // Update RMS *after* track.write returns, so the active RMS aligns with physical playback
                    currentPlaybackRms = maxOf(newRms, currentPlaybackRms * 0.85f)
                    offset += written
                }
                // Wait for the AudioTrack buffer to fully drain before releasing threshold
                waitForPlaybackComplete(track, samples.size)
            } finally {
                currentPlaybackRms = 0f
            }

        } catch (e: CancellationException) {
            android.util.Log.i("KokoroTtsService", "Pipeline: Playback canceled for: \"$text\"")
        } catch (e: Exception) {
            android.util.Log.e("KokoroTtsService", "Pipeline: Playback failed for: \"$text\"", e)
        }
    }

    /**
     * Blocks until all buffered samples have been consumed by the audio hardware.
     * Uses a simple duration-based wait: 24kHz mono PCM floats means ~0.17ms per sample.
     * Waits for the estimated playback duration plus a 100ms safety margin.
     */
    private fun waitForPlaybackComplete(track: AudioTrack, sampleCount: Int) {
        // Sleep for 150ms to allow the final AudioTrack buffer to drain.
        // We do not sleep for the entire sentence duration, since track.write in blocking mode 
        // already throttles/blocks to play the audio samples in real time.
        Thread.sleep(150L)
    }

    fun stopPlayback() {
        android.util.Log.i("KokoroTtsService", "Stopping playback and clearing TTS queue...")
        activeSpeakJob?.cancel()
        activeSpeakJob = null
        activePlaybackJob?.cancel()
        activePlaybackJob = null

        // Drain/clear the queues
        while (speakChannel.tryReceive().isSuccess) {
            // Discard pending sentences
        }
        while (playbackChannel.tryReceive().isSuccess) {
            // Discard pending synthesized samples
        }

        synchronized(this) {
            try {
                audioTrack?.let { track ->
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                    track.flush()
                }
            } catch (e: Exception) {
                android.util.Log.e("KokoroTtsService", "Error stopping AudioTrack", e)
            }
            isSpeaking = false
            currentPlaybackRms = 0f
        }
    }

    fun releaseTts() {
        stopPlayback()
        ttsScope.launch {
            mutex.withLock {
                try {
                    tts?.release()
                    tts = null
                    android.util.Log.i("KokoroTtsService", "Released Kokoro TTS engine.")
                } catch (e: Exception) {
                    android.util.Log.e("KokoroTtsService", "Error releasing OfflineTts", e)
                }
            }
        }
    }

    fun playSystemSound(startFreq: Float, endFreq: Float, durationMs: Long) {
        ttsScope.launch {
            try {
                val sampleRate = 24000
                val samples = synthesizeChirp(startFreq, endFreq, durationMs, sampleRate)
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize.coerceAtLeast(samples.size * 4))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                track.play()
                delay(durationMs + 100L)
                track.stop()
                track.release()
            } catch (e: Exception) {
                android.util.Log.e("KokoroTtsService", "Failed to play system sound", e)
            }
        }
    }

    private fun synthesizeChirp(startFreq: Float, endFreq: Float, durationMs: Long, sampleRate: Int): FloatArray {
        val durationSec = durationMs / 1000f
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = FloatArray(numSamples)
        val twoPi = 2.0 * Math.PI

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val phase = twoPi * (startFreq * t + (endFreq - startFreq) / (2f * durationSec) * t * t)
            
            val envelope = when {
                t < 0.1f * durationSec -> t / (0.1f * durationSec)
                t > 0.8f * durationSec -> (durationSec - t) / (0.2f * durationSec)
                else -> 1f
            }
            
            samples[i] = (Math.sin(phase) * 0.15 * envelope).toFloat()
        }
        return samples
    }

    private fun normalizeSamples(samples: FloatArray, targetPeak: Float = 0.8f) {
        var maxVal = 0f
        for (s in samples) {
            val absVal = Math.abs(s)
            if (absVal > maxVal) {
                maxVal = absVal
            }
        }
        if (maxVal > 0f) {
            val scale = targetPeak / maxVal
            for (i in samples.indices) {
                samples[i] *= scale
            }
        }
    }

    fun release() {
        releaseTts()
        ttsScope.cancel()
        synchronized(this) {
            try {
                audioTrack?.release()
                audioTrack = null
            } catch (e: Exception) {
                android.util.Log.e("KokoroTtsService", "Error releasing AudioTrack", e)
            }
        }
    }
}
