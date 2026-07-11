package com.daex.android.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
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

    // Guards speak()/stopPlayback()'s state transitions (stopped, playbackEpoch,
    // pendingUtterances, isSpeaking) so the two can't interleave into an
    // inconsistent state when called from different threads (e.g. a stop racing
    // an in-flight token callback that's about to queue another sentence).
    private val stateLock = Any()

    // Bumped by stopPlayback(). Each SpeakRequest/PlayItem captures the epoch that
    // was current when it was created; the playback pipeline drops any item whose
    // epoch has gone stale by the time it's processed — a straggler from an
    // already-cancelled request — instead of letting it corrupt isSpeaking/
    // pendingUtterances bookkeeping for whatever is playing now.
    private val playbackEpoch = java.util.concurrent.atomic.AtomicInteger(0)

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

    // Frames written to the AudioTrack since play()/flush(). Compared against
    // playbackHeadPosition to know when the speaker has actually gone silent.
    @Volatile
    private var framesWritten = 0

    class SpeakRequest(val text: String, val voiceId: Int, val epoch: Int)

    // Synthesis and playback are decoupled so sentence N+1's inference runs while
    // sentence N is still playing. Kokoro's inference is ~1x real-time on-device
    // (2026-07-11 field logs: ~4.5s to synthesize ~4.5s of audio), so the old
    // serial synth→play→synth→play loop stalled for a full sentence-length between
    // every sentence. Synthesized PCM flows through playbackChannel to a dedicated
    // playback worker; UtteranceEnd markers keep the utterance count accurate.
    private sealed interface PlayItem {
        class Pcm(val samples: FloatArray, val epoch: Int) : PlayItem
        class UtteranceEnd(val epoch: Int) : PlayItem
    }
    private val playbackChannel = Channel<PlayItem>(Channel.UNLIMITED)
    private val pendingUtterances = java.util.concurrent.atomic.AtomicInteger(0)

    // System chimes (session wake/close) are short premade assets played through
    // SoundPool, which is built for exactly this: low trigger latency, decoded once
    // at load time. No procedural synthesis involved. Declared here, above init{},
    // because Kotlin runs property initializers and init blocks in textual order —
    // loadChimes() (called from init{} below) would otherwise see chimePool as null.
    private val chimePool = android.media.SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    @Volatile private var wakeChimeId: Int = 0
    @Volatile private var closeChimeId: Int = 0
    // load() returns a sound ID synchronously but decoding happens async on a
    // background thread; play() on a not-yet-decoded (or failed) sound is a silent
    // no-op. These track real readiness so we don't drop the very first playback.
    @Volatile private var wakeChimeReady = false
    @Volatile private var closeChimeReady = false
    // Real durations measured from the assets at load time (they're multi-second
    // files, not the 250ms the old synth produced).
    @Volatile private var wakeChimeDurationMs = 2500L
    @Volatile private var closeChimeDurationMs = 3400L
    // While now < this, the chime is audible on the speaker. The recorder's TTS
    // gate treats it like TTS playback so it can't false-trigger a speech start —
    // SoundPool audio is invisible to isSpeaking/currentPlaybackRms otherwise.
    @Volatile var chimeActiveUntilMs = 0L
        private set

    init {
        startSynthesisPipeline()
        startPlaybackPipeline()
        loadChimes()
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
        val epoch: Int
        synchronized(stateLock) {
            stopped = false
            // isSpeaking goes true at queue time so the mic gate covers the whole
            // response — including the synthesis-only stretch before any audio plays.
            pendingUtterances.incrementAndGet()
            isSpeaking = true
            epoch = playbackEpoch.get()
        }
        android.util.Log.i("KokoroTtsService", "Queuing text: \"$text\"")
        speakChannel.trySend(SpeakRequest(text, voiceId, epoch))
    }

    private fun startSynthesisPipeline() {
        ttsScope.launch {
            for (request in speakChannel) {
                val epoch = request.epoch
                val job = launch {
                    try {
                        var activeTts = mutex.withLock { tts }
                        if (activeTts == null) {
                            initTts()
                            var attempts = 0
                            while (activeTts == null && attempts < 15) {
                                delay(300)
                                activeTts = mutex.withLock { tts }
                                attempts++
                            }
                        }
                        if (activeTts == null) {
                            android.util.Log.w("KokoroTtsService", "Pipeline: TTS engine unavailable, dropping request")
                            return@launch
                        }

                        // The lock is held for the whole synthesis, but a stop request
                        // aborts quickly: the callback returns 0 once `stopped` is set
                        // or the request's epoch has been superseded by a newer stop.
                        mutex.withLock {
                            val currentTts = tts
                            if (currentTts != null && currentTts == activeTts && !stopped && epoch == playbackEpoch.get()) {
                                withContext(Dispatchers.Default) {
                                    currentTts.generateWithCallback(
                                        text = request.text,
                                        sid = request.voiceId,
                                        speed = 1.0f,
                                        callback = TtsCallback { samples ->
                                            if (stopped || epoch != playbackEpoch.get()) {
                                                0
                                            } else {
                                                // Copy: sherpa-onnx may reuse the
                                                // buffer after the callback returns.
                                                playbackChannel.trySend(PlayItem.Pcm(samples.copyOf(), epoch))
                                                1
                                            }
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
                        // Exactly one end-marker per request — also on failure or
                        // cancel — so the playback worker's utterance count stays
                        // honest and isSpeaking can never get stuck true. Tagged
                        // with this request's epoch so a marker that lands after a
                        // stop (and a possible new speak()) can't be mistaken for
                        // belonging to whatever plays next.
                        playbackChannel.trySend(PlayItem.UtteranceEnd(epoch))
                    }
                }
                activeSpeakJob = job
                job.join()
            }
        }
    }

    // Consumes synthesized PCM. Runs concurrently with the synthesis worker: while
    // one sentence plays here, the next is already being synthesized upstream.
    private fun startPlaybackPipeline() {
        ttsScope.launch {
            for (item in playbackChannel) {
                when (item) {
                    is PlayItem.Pcm -> {
                        if (!stopped && item.epoch == playbackEpoch.get()) {
                            playAudioSamples(item.samples)
                        }
                    }
                    is PlayItem.UtteranceEnd -> {
                        if (item.epoch != playbackEpoch.get()) {
                            // Straggler from a request that was already cancelled —
                            // stopPlayback() already reset pendingUtterances/isSpeaking
                            // for the current epoch, so this marker must not touch them.
                            continue
                        }
                        val remaining = pendingUtterances.updateAndGet { if (it > 0) it - 1 else 0 }
                        if (remaining == 0) {
                            // Last queued utterance fully played: wait for the track
                            // to physically drain before the mic gate opens.
                            if (!stopped) {
                                drainPlayback()
                            } else {
                                currentPlaybackRms = 0f
                            }
                            if (pendingUtterances.get() == 0) {
                                isSpeaking = false
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun drainPlayback() {
        val track = synchronized(this) { audioTrack }
        if (track != null) {
            try {
                val deadline = System.currentTimeMillis() + 5000L
                while (!stopped && System.currentTimeMillis() < deadline) {
                    val remainingFrames = framesWritten - track.playbackHeadPosition
                    if (remainingFrames <= 0) break
                    delay((remainingFrames * 1000L / 24000).coerceIn(10L, 100L))
                }
            } catch (e: Exception) {
                // Track may have been released mid-drain
            }
        }
        currentPlaybackRms = 0f
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

                framesWritten += written
                // RMS is intentionally never zeroed here: write() returns before the
                // audio is heard, so only drainPlayback()/stopPlayback() may clear it.
                currentPlaybackRms = maxOf(newRms, currentPlaybackRms * 0.85f)
                offset += written
            }

        } catch (e: Exception) {
            android.util.Log.e("KokoroTtsService", "Pipeline: Playback failed", e)
        }
    }

    fun stopPlayback() {
        android.util.Log.i("KokoroTtsService", "Stopping playback and clearing TTS queue...")
        synchronized(stateLock) {
            stopped = true
            // Invalidates the epoch any in-flight speak()/synthesis job captured —
            // their PCM/end markers get ignored by the playback pipeline even if
            // they land after this call returns (see startPlaybackPipeline).
            playbackEpoch.incrementAndGet()
            pendingUtterances.set(0)
            isSpeaking = false
        }
        activeSpeakJob?.cancel()
        activeSpeakJob = null

        while (speakChannel.tryReceive().isSuccess) {}
        while (playbackChannel.tryReceive().isSuccess) {}

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
            // stop()/flush() reset playbackHeadPosition, so the write counter resets with it
            framesWritten = 0
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

    private fun loadChimes() {
        try {
            chimePool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status != 0) {
                    android.util.Log.e("KokoroTtsService", "Chime decode failed: sampleId=$sampleId status=$status")
                    return@setOnLoadCompleteListener
                }
                when (sampleId) {
                    wakeChimeId -> {
                        wakeChimeReady = true
                        android.util.Log.i("KokoroTtsService", "Wake chime loaded (id=$sampleId)")
                    }
                    closeChimeId -> {
                        closeChimeReady = true
                        android.util.Log.i("KokoroTtsService", "Close chime loaded (id=$sampleId)")
                    }
                }
            }
            wakeChimeId = chimePool.load(context, com.daex.android.R.raw.chime_wake, 1)
            closeChimeId = chimePool.load(context, com.daex.android.R.raw.chime_close, 1)
            wakeChimeDurationMs = measureRawDurationMs(com.daex.android.R.raw.chime_wake, wakeChimeDurationMs)
            closeChimeDurationMs = measureRawDurationMs(com.daex.android.R.raw.chime_close, closeChimeDurationMs)
        } catch (e: Exception) {
            android.util.Log.e("KokoroTtsService", "Failed to load system chimes", e)
        }
    }

    private fun measureRawDurationMs(resId: Int, fallbackMs: Long): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            context.resources.openRawResourceFd(resId).use { afd ->
                retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            durationMs ?: fallbackMs
        } catch (e: Exception) {
            android.util.Log.w("KokoroTtsService", "Failed to measure chime duration for res $resId", e)
            fallbackMs
        }
    }

    fun playWakeChime() {
        if (wakeChimeReady) {
            chimeActiveUntilMs = System.currentTimeMillis() + wakeChimeDurationMs + 200L
            chimePool.play(wakeChimeId, 1f, 1f, 1, 0, 1f)
        } else {
            android.util.Log.w("KokoroTtsService", "playWakeChime: chime not loaded yet, skipping")
        }
    }

    fun playCloseChime() {
        if (closeChimeReady) {
            chimeActiveUntilMs = System.currentTimeMillis() + closeChimeDurationMs + 200L
            chimePool.play(closeChimeId, 1f, 1f, 1, 0, 1f)
        } else {
            android.util.Log.w("KokoroTtsService", "playCloseChime: chime not loaded yet, skipping")
        }
    }

    fun release() {
        releaseTts()
        ttsScope.cancel()
        chimePool.release()
        synchronized(this) {
            try {
                audioTrack?.release()
                audioTrack = null
            } catch (e: Exception) {}
        }
    }
}