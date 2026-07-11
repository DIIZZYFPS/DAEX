package com.daex.android.services

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// What the speaker is doing right now, as seen by the mic-side VAD gate.
// SPEAKING: TTS audio is physically playing — normal speech detection is blocked,
//   only a sustained barge-in can start a chunk.
// COOLDOWN: TTS just went silent — normal detection runs with a stricter frame
//   requirement while the acoustic tail dies out.
// CLEAR: normal VAD.
enum class TtsGateState { CLEAR, COOLDOWN, SPEAKING }

class AudioRecorder(private val outputFile: File) {
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false
    private var recordingJob: Job? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize: Int = run {
        val min = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (min > 0) min else {
            Log.w("AudioRecorder", "getMinBufferSize returned $min; falling back to 4096 bytes")
            4096
        }
    }

    companion object {
        // Acoustic speaker→mic bleed is ~10-15% on a handheld device
        private const val ECHO_COUPLING_FACTOR = 0.12f
        // Barge-in during TTS must clear an absolute floor (2x the default speech
        // threshold) AND 4x the echo estimate, sustained — echo alone can't do that.
        // Calibrated from 2026-07-10 device logs: user speech peaks 0.04-0.15.
        private const val BARGE_IN_FLOOR = 0.06f
        private const val BARGE_IN_SUSTAIN_MS = 450L
        // A chunk whose speech overlapped playing TTS longer than this (without being
        // a barge-in) is almost certainly echo — the VM discards it.
        private const val TTS_OVERLAP_DISCARD_MS = 1500L
    }

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        speechThreshold: Float = 0.015f,
        silenceThreshold: Float = 0.008f,
        silenceDurationMs: Long = 1500L,
        currentPlaybackRms: () -> Float = { 0f },
        ttsGateState: () -> TtsGateState = { TtsGateState.CLEAR },
        // Barge-in (interrupting active TTS by talking over it) is parked until the
        // rest of the live-voice loop is solid; when false, nothing can start a
        // chunk while the gate reports SPEAKING.
        bargeInEnabled: Boolean = false,
        onSpeechStarted: (() -> Unit)? = null,
        onBargeIn: (() -> Unit)? = null,
        onSilenceDetected: ((contaminatedByTts: Boolean) -> Unit)? = null,
        onAmplitude: (Float) -> Unit
    ) {
        if (isRecording) return
        isRecording = true

        recordingJob = scope.launch(Dispatchers.IO) {
            var record: AudioRecord? = null
            var fos: FileOutputStream? = null
            var totalBytesWritten = 0
            try {
                // Ensure output file directories exist
                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                // VOICE_RECOGNITION: speech-optimized mic signal without digital playback routing.
                val rec = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                record = rec
                audioRecord = rec

                if (rec.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("AudioRecorder", "AudioRecord not initialized")
                    return@launch
                }

                // Deliberately NO AcousticEchoCanceler/NoiseSuppressor here. Field
                // testing on Samsung (2026-07-10 logcat): NS crushes sustained speech
                // to the noise floor within ~500ms (mic 0.12 → 0.003 mid-sentence),
                // making chunks unintelligible to the model, and AEC is half-duplex —
                // it mutes the user's voice whenever TTS is playing, killing barge-in.
                // Echo is handled by the software TTS gate + RMS ducking floor below.

                val out = FileOutputStream(outputFile)
                fos = out
                // Write dummy WAV header placeholder
                writeWavHeader(out, 0)

                rec.startRecording()
                val buffer = ShortArray(bufferSize)

                var hasSpeechStarted = false
                var startedAsBargeIn = false
                var silenceStartTime = 0L
                var consecutiveSpeechFrames = 0
                var bargeInSpeechMs = 0L
                var ttsOverlapMs = 0L
                val preRollBuffers = java.util.LinkedList<ByteArray>()
                var lastLogTime = 0L
                var smoothedTtsRms = 0f // Envelope follower state

                while (isRecording) {
                    val readSize = rec.read(buffer, 0, buffer.size)
                    if (readSize > 0) {
                        val frameMs = readSize * 1000L / sampleRate

                        // Calculate amplitude for waves visualization
                        var sum = 0.0
                        for (i in 0 until readSize) {
                            val value = buffer[i].toInt()
                            sum += value.toDouble() * value
                        }
                        val rms = Math.sqrt(sum / readSize)
                        // Normalize RMS to 0f..1f range for waves
                        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                        onAmplitude(normalized)

                        // Real-time TTS playback volume drives a dynamic ducking floor
                        val kokoroRms = currentPlaybackRms()

                        // Peak envelope follower: fast attack, slow release (~350ms)
                        // so the floor stays raised through the acoustic tail after
                        // playback stops. (No instant-release shortcut — that defeats
                        // the masking exactly when the tail needs it.)
                        smoothedTtsRms = if (kokoroRms > smoothedTtsRms) kokoroRms else smoothedTtsRms * 0.92f

                        // Acoustic echo estimation (no digital routing with VOICE_RECOGNITION)
                        val echoEstimate = smoothedTtsRms * ECHO_COUPLING_FACTOR
                        // Mic signal must be 1.5x louder than estimated acoustic echo
                        val dynamicSilenceFloor = maxOf(speechThreshold, echoEstimate * 1.5f)
                        val dynamicSilenceThreshold = maxOf(silenceThreshold, echoEstimate * 0.8f)

                        val gate = ttsGateState()
                        val requiredSpeechFrames = if (gate == TtsGateState.CLEAR) 2 else 3

                        val nowTime = System.currentTimeMillis()
                        if (nowTime - lastLogTime > 500L) {
                            Log.d("AudioRecorder", "VAD: mic=$normalized, tts_rms=$kokoroRms, env=$smoothedTtsRms, duck_floor=$dynamicSilenceFloor, gate=$gate")
                            lastLogTime = nowTime
                        }

                        val frameBytes = toLittleEndianBytes(buffer, readSize)

                        if (!hasSpeechStarted) {
                            // Keep rolling pre-roll buffer to avoid cutting off speech onset
                            preRollBuffers.add(frameBytes)
                            if (preRollBuffers.size > 4) {
                                preRollBuffers.removeFirst()
                            }

                            if (gate == TtsGateState.SPEAKING) {
                                // TTS is physically playing: normal detection would just
                                // hear our own voice. Only a sustained, dominant signal
                                // (a real barge-in) can start a chunk here — and only
                                // when barge-in is enabled at all.
                                consecutiveSpeechFrames = 0
                                val bargeInThreshold = maxOf(BARGE_IN_FLOOR, echoEstimate * 4f)
                                if (!bargeInEnabled) {
                                    bargeInSpeechMs = 0L
                                } else if (normalized > bargeInThreshold) {
                                    bargeInSpeechMs += frameMs
                                    if (bargeInSpeechMs >= BARGE_IN_SUSTAIN_MS) {
                                        hasSpeechStarted = true
                                        startedAsBargeIn = true
                                        totalBytesWritten += writePreRoll(out, preRollBuffers)
                                        Log.i("AudioRecorder", "VAD: Barge-in over TTS (mic=$normalized, threshold=$bargeInThreshold)")
                                        onBargeIn?.invoke()
                                        onSpeechStarted?.invoke()
                                    }
                                } else {
                                    // Decay instead of hard reset so natural speech
                                    // dips (between words) don't zero the evidence.
                                    bargeInSpeechMs = maxOf(0L, bargeInSpeechMs - 2 * frameMs)
                                }
                            } else {
                                // CLEAR or COOLDOWN: normal VAD. During COOLDOWN the
                                // envelope-raised floor and the extra required frame
                                // filter the residual tail.
                                bargeInSpeechMs = 0L
                                if (normalized > dynamicSilenceFloor) {
                                    consecutiveSpeechFrames++
                                    if (consecutiveSpeechFrames >= requiredSpeechFrames) {
                                        hasSpeechStarted = true
                                        totalBytesWritten += writePreRoll(out, preRollBuffers)
                                        Log.i("AudioRecorder", "VAD: Speech started. Wrote pre-roll buffers to WAV file.")
                                        onSpeechStarted?.invoke()
                                    }
                                } else {
                                    consecutiveSpeechFrames = 0
                                }
                            }
                        } else {
                            // Speech is active: write current samples to the WAV file
                            out.write(frameBytes)
                            totalBytesWritten += frameBytes.size

                            if (gate == TtsGateState.SPEAKING) {
                                // No auto-barge-in here: a false speech-start would make
                                // the model cancel itself the moment it starts talking.
                                // Overlap is only tracked so contaminated chunks get
                                // discarded on silence.
                                ttsOverlapMs += frameMs
                            }

                            if (normalized < dynamicSilenceThreshold) {
                                if (silenceStartTime == 0L) {
                                    silenceStartTime = System.currentTimeMillis()
                                } else {
                                    val silentDuration = System.currentTimeMillis() - silenceStartTime
                                    if (silentDuration >= silenceDurationMs) {
                                        val contaminated = !startedAsBargeIn && ttsOverlapMs > TTS_OVERLAP_DISCARD_MS
                                        Log.d("AudioRecorder", "VAD: Silence detected ($silentDuration ms, ttsOverlap=${ttsOverlapMs}ms, contaminated=$contaminated)")
                                        // Reset states before notifying to prevent multiple calls
                                        hasSpeechStarted = false
                                        startedAsBargeIn = false
                                        silenceStartTime = 0L
                                        consecutiveSpeechFrames = 0
                                        bargeInSpeechMs = 0L
                                        ttsOverlapMs = 0L
                                        onSilenceDetected?.invoke(contaminated)
                                    }
                                }
                            } else {
                                // Reset silence start since amplitude is above silence threshold
                                silenceStartTime = 0L
                            }
                        }
                    } else if (readSize < 0) {
                        Log.e("AudioRecorder", "Error reading PCM frames: $readSize")
                        break
                    }
                }

                Log.d("AudioRecorder", "Audio recorded successfully to ${outputFile.absolutePath}, size=$totalBytesWritten")
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Error recording audio", e)
            } finally {
                isRecording = false
                try { record?.stop() } catch (e: Exception) {}
                try { record?.release() } catch (e: Exception) {}
                try { fos?.close() } catch (e: Exception) {}
                if (totalBytesWritten > 0) {
                    try {
                        RandomAccessFile(outputFile, "rw").use { wavFile ->
                            updateWavHeader(wavFile, totalBytesWritten)
                        }
                    } catch (e: Exception) {
                        Log.e("AudioRecorder", "Failed to finalize WAV header", e)
                    }
                }
            }
        }
    }

    private fun toLittleEndianBytes(src: ShortArray, count: Int): ByteArray {
        val out = ByteArray(count * 2)
        var j = 0
        for (i in 0 until count) {
            val v = src[i].toInt()
            out[j++] = (v and 0xFF).toByte()
            out[j++] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun writePreRoll(fos: FileOutputStream, preRoll: java.util.LinkedList<ByteArray>): Int {
        var written = 0
        try {
            for (bytes in preRoll) {
                fos.write(bytes)
                written += bytes.size
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "VAD: Failed to write pre-roll buffers", e)
        }
        preRoll.clear()
        return written
    }

    suspend fun stop() {
        isRecording = false
        recordingJob?.join()
        recordingJob = null
        val recordToRelease = audioRecord
        audioRecord = null
        if (recordToRelease != null) {
            withContext(Dispatchers.IO) {
                try {
                    recordToRelease.stop()
                } catch (e: Exception) {}
                try {
                    recordToRelease.release()
                } catch (e: Exception) {}
            }
        }
    }

    fun stopAsync() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        val recordToRelease = audioRecord
        audioRecord = null
        if (recordToRelease != null) {
            // DIIZZY: Release AudioRecord on a background thread to prevent UI thread stutter/lock
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    recordToRelease.stop()
                } catch (e: Exception) {}
                try {
                    recordToRelease.release()
                } catch (e: Exception) {}
            }
        }
    }

    private fun writeWavHeader(fos: FileOutputStream, totalAudioLen: Int) {
        val totalDataLen = totalAudioLen + 36
        val sampleRateLong = sampleRate.toLong()
        val byteRate = (sampleRate * 2).toLong() // 16-bit mono -> 2 bytes per sample

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte() // WAVE
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = 1 // number of channels = 1 (mono)
        header[23] = 0
        header[24] = (sampleRateLong and 0xff).toByte()
        header[25] = ((sampleRateLong shr 8) and 0xff).toByte()
        header[26] = ((sampleRateLong shr 16) and 0xff).toByte()
        header[27] = ((sampleRateLong shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2 // block align = channels * bytes per sample
        header[33] = 0
        header[34] = 16 // bits per sample = 16
        header[35] = 0
        header[36] = 'd'.code.toByte() // 'data' chunk
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        fos.write(header, 0, 44)
    }

    private fun updateWavHeader(file: RandomAccessFile, totalAudioLen: Int) {
        val totalDataLen = totalAudioLen + 36
        // Chunk size
        file.seek(4)
        file.write(totalDataLen and 0xff)
        file.write((totalDataLen shr 8) and 0xff)
        file.write((totalDataLen shr 16) and 0xff)
        file.write((totalDataLen shr 24) and 0xff)

        // Subchunk2 size
        file.seek(40)
        file.write(totalAudioLen and 0xff)
        file.write((totalAudioLen shr 8) and 0xff)
        file.write((totalAudioLen shr 16) and 0xff)
        file.write((totalAudioLen shr 24) and 0xff)
    }
}
