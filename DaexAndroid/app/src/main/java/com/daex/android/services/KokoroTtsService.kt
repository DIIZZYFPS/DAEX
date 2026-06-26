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

@OptIn(ExperimentalCoroutinesApi::class)
class KokoroTtsService(private val context: Context) {
    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private val mutex = Mutex()
    private val ttsScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var activeSpeakJob: Job? = null
    private val speakChannel = Channel<SpeakRequest>(Channel.UNLIMITED)
    @Volatile private var stopped = false

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

    @Volatile
    var systemChimeStyle = 0

    class SpeakRequest(val text: String, val voiceId: Int)

    init {
        startSynthesisPipeline()
    }

    fun initTts() {
        ttsScope.launch {
            mutex.withLock {
                if (tts != null) return@launch
                try {
                    android.util.Log.i("KokoroTtsService", "Initializing Kokoro TTS engine...")
                    
                    val kokoroDir = File(context.filesDir, "kokoro-en-v0_19")
                    val modelFile = File(kokoroDir, "model.onnx")
                    val voicesFile = File(kokoroDir, "voices.bin")
                    val tokensFile = File(kokoroDir, "tokens.txt")

                    if (!modelFile.exists() || !voicesFile.exists() || !tokensFile.exists()) {
                        android.util.Log.w("KokoroTtsService", "Cannot initialize Kokoro TTS engine: model files are not downloaded yet.")
                        return@launch
                    }

                    val localDataDir = File(context.filesDir, "kokoro/espeak-ng-data")
                    if (!localDataDir.exists() || localDataDir.listFiles().isNullOrEmpty()) {
                        localDataDir.mkdirs()
                        android.util.Log.i("KokoroTtsService", "Extracting espeak-ng-data from assets to $localDataDir...")
                        copyAssetsFolder("espeak-ng-data", localDataDir)
                        android.util.Log.i("KokoroTtsService", "Extraction complete.")
                    }

                    val config = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            kokoro = OfflineTtsKokoroModelConfig(
                                model = modelFile.absolutePath,
                                voices = voicesFile.absolutePath,
                                tokens = tokensFile.absolutePath,
                                dataDir = localDataDir.absolutePath
                            ),
                            numThreads = 2,
                            debug = false
                        )
                    )
                    // Pass null as the assetManager since we are loading all files from local storage absolute paths
                    tts = OfflineTts(null, config)
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
            copyAssetFile(srcPath, destDir)
        } else {
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
        stopped = false
        android.util.Log.i("KokoroTtsService", "Queuing text: \"$text\"")
        speakChannel.trySend(SpeakRequest(text, voiceId))
    }

    private fun startSynthesisPipeline() {
        ttsScope.launch {
            for (request in speakChannel) {
                isSpeaking = true
                val job = launch {
                    val ttsInstance = mutex.withLock { tts }
                    if (ttsInstance == null) {
                        initTts()
                        var attempts = 0
                        var resolvedTts: OfflineTts? = null
                        while (attempts < 15) {
                            delay(300)
                            resolvedTts = mutex.withLock { tts }
                            if (resolvedTts != null) break
                            attempts++
                        }
                        if (resolvedTts == null) return@launch
                    }

                    val activeTts = mutex.withLock { tts } ?: return@launch
                    try {
                        mutex.withLock {
                            val currentTts = tts
                            if (currentTts != null && currentTts == activeTts) {
                                withContext(Dispatchers.Default) {
                                    currentTts.generateWithCallback(
                                        text = request.text,
                                        sid = request.voiceId,
                                        speed = 1.0f,
                                        callback = TtsCallback { samples ->
                                            ttsCallback(samples)
                                        } as (FloatArray) -> Int
                                    )
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        android.util.Log.i("KokoroTtsService", "Pipeline: Synthesis canceled")
                    } catch (e: Exception) {
                        android.util.Log.e("KokoroTtsService", "Pipeline: Synthesis failed", e)
                    } finally {
                        if (speakChannel.isEmpty) {
                            isSpeaking = false
                        }
                    }
                }
                activeSpeakJob = job
                job.join()
            }
        }
    }

    private fun ttsCallback(samples: FloatArray): Int {
        playAudioSamples(samples)
        return 1
    }

    private fun playAudioSamples(samples: FloatArray) {
        try {
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

            val chunkSize = 4800
            var offset = 0
            try {
                while (offset < samples.size && !stopped) {
                    val writeLen = minOf(chunkSize, samples.size - offset)
                    
                    var sum = 0f
                    for (i in offset until (offset + writeLen)) {
                        val s = samples[i]
                        sum += s * s
                    }
                    val newRms = Math.sqrt((sum / writeLen).toDouble()).toFloat()
                    
                    val written = track.write(samples, offset, writeLen, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) break
                    
                    currentPlaybackRms = maxOf(newRms, currentPlaybackRms * 0.85f)
                    offset += written
                }
                if (!stopped) {
                    waitForPlaybackComplete(track, samples.size)
                }
            } finally {
                currentPlaybackRms = 0f
            }

        } catch (e: Exception) {
            android.util.Log.e("KokoroTtsService", "Pipeline: Playback failed", e)
        }
    }

    private fun waitForPlaybackComplete(track: AudioTrack, sampleCount: Int) {
        Thread.sleep(150L)
    }

    fun stopPlayback() {
        android.util.Log.i("KokoroTtsService", "Stopping playback and clearing TTS queue...")
        stopped = true
        activeSpeakJob?.cancel()
        activeSpeakJob = null

        while (speakChannel.tryReceive().isSuccess) {}

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
        CoroutineScope(Dispatchers.IO + NonCancellable).launch {
            mutex.withLock {
                try {
                    tts?.release()
                    tts = null
                } catch (e: Exception) {}
            }
        }
    }

    fun playSystemSound(startFreq: Float, endFreq: Float, durationMs: Long) {
        ttsScope.launch {
            try {
                val sampleRate = 24000
                val samples = synthesizeChirp(startFreq, endFreq, durationMs, sampleRate)
                val bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(bufferSize.coerceAtLeast(samples.size * 4))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                track.play()
                delay(durationMs + 300L) // Allow the 200ms tail to play out
                track.stop()
                track.release()
            } catch (e: Exception) {
                android.util.Log.e("KokoroTtsService", "Failed to play system sound", e)
            }
        }
    }

    private fun synthesizeChirp(startFreq: Float, endFreq: Float, durationMs: Long, sampleRate: Int): FloatArray {
        val tailMs = 200L
        val totalDurationMs = durationMs + tailMs
        val durationSec = durationMs / 1000f
        val totalDurationSec = totalDurationMs / 1000f
        val numSamples = (sampleRate * totalDurationSec).toInt()
        val samples = FloatArray(numSamples)
        val twoPi = 2.0 * Math.PI
        val isAscending = startFreq < endFreq

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = (t / durationSec).toFloat().coerceAtMost(1.0f)
            val tailVolume = if (t <= durationSec) 1.0 else Math.exp(-22.0 * (t - durationSec))

            var rawSample = 0.0

            when (systemChimeStyle) {
                0 -> { // Option 0: Cozy Ambient Glow (Lush Aura Pad)
                    val fBase = if (isAscending) 320.0 else 240.0
                    
                    var combined = 0.0
                    val ratios = doubleArrayOf(1.002, 1.503, 1.998, 2.505)
                    val gains = doubleArrayOf(0.45, 0.25, 0.15, 0.10)
                    for (idx in ratios.indices) {
                        val phase = twoPi * fBase * ratios[idx] * t
                        val wave = Math.sin(phase) * 0.6 + triangleWave(phase) * 0.4
                        combined += wave * gains[idx]
                    }
                    
                    val envelope = if (progress < 0.40f) progress / 0.40f else 1.0f
                    rawSample = combined * 0.15 * envelope
                }
                1 -> { // Option 1: Crystal Prism (Cascading Glass Arpeggio)
                    val f1 = if (isAscending) 440.0 else 659.25
                    val f2 = 554.37 // C#5
                    val f3 = if (isAscending) 659.25 else 440.0
                    
                    // Trigger note 1 at 0ms
                    val tau1 = t
                    if (tau1 >= 0.0) {
                        val phase = twoPi * f1 * tau1
                        val wave = Math.sin(phase) * 0.8 + triangleWave(phase) * 0.2
                        val overtone1 = (Math.sin(phase * 1.96) * 0.8 + triangleWave(phase * 1.96) * 0.2) * 0.25 * Math.exp(-12.0 * tau1)
                        val overtone2 = Math.sin(phase * 3.0) * 0.15 * Math.exp(-24.0 * tau1)
                        val noteEnv = Math.exp(-6.0 * tau1)
                        rawSample += (wave + overtone1 + overtone2) * noteEnv * 0.08
                    }
                    
                    // Trigger note 2 at 70ms
                    val tau2 = t - 0.07
                    if (tau2 >= 0.0) {
                        val phase = twoPi * f2 * tau2
                        val wave = Math.sin(phase) * 0.8 + triangleWave(phase) * 0.2
                        val overtone1 = (Math.sin(phase * 1.96) * 0.8 + triangleWave(phase * 1.96) * 0.2) * 0.25 * Math.exp(-12.0 * tau2)
                        val overtone2 = Math.sin(phase * 3.0) * 0.15 * Math.exp(-24.0 * tau2)
                        val noteEnv = Math.exp(-6.0 * tau2)
                        rawSample += (wave + overtone1 + overtone2) * noteEnv * 0.08
                    }
                    
                    // Trigger note 3 at 140ms
                    val tau3 = t - 0.14
                    if (tau3 >= 0.0) {
                        val phase = twoPi * f3 * tau3
                        val wave = Math.sin(phase) * 0.8 + triangleWave(phase) * 0.2
                        val overtone1 = (Math.sin(phase * 1.96) * 0.8 + triangleWave(phase * 1.96) * 0.2) * 0.25 * Math.exp(-12.0 * tau3)
                        val overtone2 = Math.sin(phase * 3.0) * 0.15 * Math.exp(-24.0 * tau3)
                        val noteEnv = Math.exp(-6.0 * tau3)
                        rawSample += (wave + overtone1 + overtone2) * noteEnv * 0.08
                    }
                }
                2 -> { // Option 2: Cosmic Shimmer (AI Consciousness Swell)
                    val fSub = if (isAscending) 160.0 else 130.0
                    val subPhase1 = twoPi * fSub * t
                    val subPhase2 = twoPi * fSub * 1.006 * t
                    
                    val sub1 = Math.sin(subPhase1) * 0.5 + triangleWave(subPhase1) * 0.5
                    val sub2 = Math.sin(subPhase2) * 0.5 + triangleWave(subPhase2) * 0.5
                    val subCombined = (sub1 + sub2 * 0.8) / 1.8
                    val subEnv = if (progress < 0.35f) progress / 0.35f else 1.0f
                    
                    val fShim = 1100.0
                    val shimPhase = twoPi * fShim * t + 1.2 * Math.sin(twoPi * 18.0 * t)
                    val shimWave = Math.sin(shimPhase)
                    val shimEnv = (Math.max(0.0, Math.sin(progress * Math.PI) - 0.2) / 0.8).toFloat()
                    
                    rawSample = subCombined * 0.14 * subEnv + shimWave * 0.03 * shimEnv
                }
                3 -> { // Option 3: Zen Breath (Meditation Bowl Strike)
                    val fBase = 440.0
                    val phase = twoPi * fBase * t - (2.5 / 4.0) * Math.cos(twoPi * 4.0 * t)
                    
                    val baseStrike = Math.sin(phase) * 0.7 + triangleWave(phase) * 0.3
                    val overtone1 = (Math.sin(phase * 1.5) * 0.7 + triangleWave(phase * 1.5) * 0.3) * 0.25 * Math.exp(-5.0 * t)
                    val overtone2 = Math.sin(phase * 2.0) * 0.12 * Math.exp(-10.0 * t)
                    val combined = baseStrike + overtone1 + overtone2
                    
                    val envelope = if (progress < 0.02f) {
                        progress / 0.02f
                    } else {
                        Math.exp(-4.0 * progress).toFloat()
                    }
                    rawSample = combined * 0.15 * envelope
                }
                4 -> { // Option 4: Siri Soothing Hum (Double sub-bass hum)
                    val fBase = if (isAscending) 120.0 else 100.0
                    var sampleVal = 0.0

                    // First hum at 0ms
                    val tau1 = t
                    if (tau1 >= 0.0) {
                        val phase1 = twoPi * fBase * 0.996 * tau1
                        val phase2 = twoPi * fBase * 1.004 * tau1
                        val osc = (Math.sin(phase1) + Math.sin(phase2)) * 0.5
                        
                        val attack = if (tau1 < 0.015) Math.sin((tau1 / 0.015) * Math.PI / 2) else 1.0
                        val decay = Math.exp(-22.0 * tau1)
                        sampleVal += osc * attack * decay * 0.5
                    }

                    // Second hum at 70ms (only for ascending/activation)
                    if (isAscending) {
                        val tau2 = t - 0.07
                        if (tau2 >= 0.0) {
                            val phase1 = twoPi * fBase * 0.996 * tau2
                            val phase2 = twoPi * fBase * 1.004 * tau2
                            val osc = (Math.sin(phase1) + Math.sin(phase2)) * 0.5
                            
                            val attack = if (tau2 < 0.015) Math.sin((tau2 / 0.015) * Math.PI / 2) else 1.0
                            val decay = Math.exp(-22.0 * tau2)
                            sampleVal += osc * attack * decay * 0.5
                        }
                    }
                    
                    rawSample = sampleVal
                }
            }

            samples[i] = (rawSample * tailVolume).toFloat()
        }
        return samples
    }

    private fun vocalFormant(t: Double, fBase: Double, twoPi: Double): Double {
        val f1 = 420.0; val w1 = 90.0
        val f2 = 850.0; val w2 = 130.0
        val f3 = 2200.0; val w3 = 220.0
        var signal = 0.0
        for (k in 1..8) {
            val hFreq = fBase * k
            val g1 = Math.exp(-((hFreq - f1) * (hFreq - f1)) / (w1 * w1))
            val g2 = 0.6 * Math.exp(-((hFreq - f2) * (hFreq - f2)) / (w2 * w2))
            val g3 = 0.3 * Math.exp(-((hFreq - f3) * (hFreq - f3)) / (w3 * w3))
            val gain = g1 + g2 + g3
            
            val phase = twoPi * hFreq * t
            val wave = Math.sin(phase) * 0.75 + triangleWave(phase) * 0.25
            signal += gain * wave
        }
        return signal
    }

    private fun triangleWave(phase: Double): Double {
        val p = (phase / (2.0 * Math.PI)) % 1.0
        val normalizedP = if (p < 0.0) p + 1.0 else p
        return if (normalizedP < 0.25) {
            normalizedP * 4.0
        } else if (normalizedP < 0.75) {
            2.0 - normalizedP * 4.0
        } else {
            (normalizedP - 1.0) * 4.0
        }
    }

    private fun normalizeSamples(samples: FloatArray, targetPeak: Float = 0.8f) {
        var maxVal = 0f
        for (s in samples) {
            val absVal = Math.abs(s)
            if (absVal > maxVal) maxVal = absVal
        }
        if (maxVal > 0f) {
            val scale = targetPeak / maxVal
            for (i in samples.indices) samples[i] *= scale
        }
    }

    fun release() {
        releaseTts()
        ttsScope.cancel()
        synchronized(this) {
            try {
                audioTrack?.release()
                audioTrack = null
            } catch (e: Exception) {}
        }
    }
}