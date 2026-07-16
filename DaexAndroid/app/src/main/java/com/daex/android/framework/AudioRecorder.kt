package com.daex.android.framework

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

// What the speaker is doing right now, as seen by the mic-side VAD gate.
// SPEAKING: TTS audio is physically playing — speech detection is blocked entirely
//   so the model doesn't hear its own voice.
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

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        speechThreshold: Float = 0.015f,
        silenceThreshold: Float = 0.008f,
        silenceDurationMs: Long = 1500L,
        ttsGateState: () -> TtsGateState = { TtsGateState.CLEAR },
        onSpeechStarted: (() -> Unit)? = null,
        onSilenceDetected: (() -> Unit)? = null,
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
                // it mutes the user's voice whenever TTS is playing. Echo is instead
                // handled by the software TTS gate above (speech detection is blocked
                // outright while TTS is SPEAKING).

                val out = FileOutputStream(outputFile)
                fos = out
                // Write dummy WAV header placeholder
                writeWavHeader(out, 0)

                rec.startRecording()
                val buffer = ShortArray(bufferSize)

                var hasSpeechStarted = false
                var silenceStartTime = 0L
                var consecutiveSpeechFrames = 0
                val preRollBuffers = java.util.LinkedList<ByteArray>()
                var lastLogTime = 0L

                while (isRecording) {
                    val readSize = rec.read(buffer, 0, buffer.size)
                    if (readSize > 0) {
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

                        val gate = ttsGateState()
                        val requiredSpeechFrames = if (gate == TtsGateState.CLEAR) 2 else 3

                        val nowTime = System.currentTimeMillis()
                        if (nowTime - lastLogTime > 500L) {
                            Log.d("AudioRecorder", "VAD: mic=$normalized, gate=$gate")
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
                                // hear our own voice, so no speech-start can begin here.
                                consecutiveSpeechFrames = 0
                            } else {
                                // CLEAR or COOLDOWN: normal VAD against the static
                                // thresholds. During COOLDOWN the extra required frame
                                // filters the residual acoustic tail.
                                if (normalized > speechThreshold) {
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

                            if (normalized < silenceThreshold) {
                                if (silenceStartTime == 0L) {
                                    silenceStartTime = System.currentTimeMillis()
                                } else {
                                    val silentDuration = System.currentTimeMillis() - silenceStartTime
                                    if (silentDuration >= silenceDurationMs) {
                                        Log.d("AudioRecorder", "VAD: Silence detected ($silentDuration ms)")
                                        // Reset states before notifying to prevent multiple calls
                                        hasSpeechStarted = false
                                        silenceStartTime = 0L
                                        consecutiveSpeechFrames = 0
                                        onSilenceDetected?.invoke()
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
                // The sole place AudioRecord.stop()/release() run - stop()/stopAsync() used to
                // also do this from a second, independently-launched coroutine, racing this one
                // on the same native object with no synchronization. audioRecord is nulled here
                // too, once cleanup has actually happened, instead of preemptively from the
                // caller's thread.
                try { record?.stop() } catch (e: Exception) {}
                try { record?.release() } catch (e: Exception) {}
                audioRecord = null
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
        // join() suspends until the recording coroutine's own finally block has actually run
        // (see start()) - that's the sole place AudioRecord.stop()/release() happen now, so by
        // the time this returns, cleanup is guaranteed complete.
        recordingJob?.join()
        recordingJob = null
    }

    fun stopAsync() {
        // Fire-and-forget: isRecording=false makes the recording loop in start() exit on its
        // next iteration and reach its own finally block, which does the real AudioRecord
        // cleanup on the coroutine's own dispatcher (already off the caller's thread). This
        // used to also launch a second, independent coroutine to redundantly stop/release the
        // same AudioRecord, racing the original coroutine's cleanup on the same native object.
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
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
