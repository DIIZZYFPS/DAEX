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

class AudioRecorder(private val outputFile: File) {
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false
    private var recordingJob: Job? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        speechThreshold: Float = 0.03f,
        silenceThreshold: Float = 0.015f,
        silenceDurationMs: Long = 1500L,
        currentPlaybackRms: () -> Float = { 0f }, // DIIZZY: Injected dynamic RMS ducking lambda
        onSpeechStarted: (() -> Unit)? = null,
        onSilenceDetected: (() -> Unit)? = null,
        onAmplitude: (Float) -> Unit
    ) {
        if (isRecording) return
        isRecording = true

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                // Ensure output file directories exist
                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                // VOICE_RECOGNITION: speech-optimized mic signal without digital playback routing.
                val record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                audioRecord = record

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("AudioRecorder", "AudioRecord not initialized")
                    return@launch
                }

                val fos = FileOutputStream(outputFile)
                // Write dummy WAV header placeholder
                writeWavHeader(fos, 0)

                record.startRecording()
                val buffer = ShortArray(bufferSize)
                var totalBytesWritten = 0

                var hasSpeechStarted = false
                var silenceStartTime = 0L
                var consecutiveSpeechFrames = 0
                val preRollBuffers = java.util.LinkedList<ShortArray>()
                var lastLogTime = 0L
                var smoothedTtsRms = 0f // Envelope follower state

                while (isRecording) {
                    val readSize = record.read(buffer, 0, buffer.size)
                    if (readSize > 0) {
                        // Calculate amplitude for waves visualization
                        var sum = 0.0
                        for (i in 0 until readSize) {
                            val value = buffer[i].toInt()
                            sum += value * value
                        }
                        val rms = Math.sqrt(sum / readSize)
                        // Normalize RMS to 0f..1f range for waves
                        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                        onAmplitude(normalized)

                        // DIIZZY: Fetch real-time TTS volume to calculate software ducking floor
                        val kokoroRms = currentPlaybackRms()
                        
                        // Asymmetric Peak Envelope Follower to mask acoustic latency
                        if (kokoroRms > smoothedTtsRms) {
                            // Fast Attack: instantly track rising volume
                            smoothedTtsRms = kokoroRms
                        } else if (kokoroRms == 0f) {
                            // Instant Release: collapse dynamic floor instantly when TTS stops
                            smoothedTtsRms = 0f
                        } else {
                            // Slow Release: decay by 8% per frame (~350ms masking window)
                            smoothedTtsRms *= 0.92f
                        }

                        // Acoustic echo estimation (no digital routing with VOICE_RECOGNITION)
                        val ECHO_COUPLING_FACTOR = 0.12f // acoustic speaker→mic bleed is ~10-15%
                        val echoEstimate = smoothedTtsRms * ECHO_COUPLING_FACTOR
                        // Mic signal must be 1.5x louder than estimated acoustic echo
                        val dynamicSilenceFloor = maxOf(speechThreshold, echoEstimate * 1.5f)
                        val dynamicSilenceThreshold = maxOf(silenceThreshold, echoEstimate * 0.8f)
                        val requiredSpeechFrames = if (smoothedTtsRms > 0.01f) 3 else 2

                        val nowTime = System.currentTimeMillis()
                        if (nowTime - lastLogTime > 500L) {
                            Log.d("AudioRecorder", "VAD: mic=$normalized, tts_rms=$kokoroRms, env=$smoothedTtsRms, duck_floor=$dynamicSilenceFloor")
                            lastLogTime = nowTime
                        }

                        // Keep rolling pre-roll buffer in memory to avoid cutting off speech onset
                        val bufferCopy = ShortArray(readSize)
                        System.arraycopy(buffer, 0, bufferCopy, 0, readSize)
                        preRollBuffers.add(bufferCopy)
                        if (preRollBuffers.size > 4) {
                            preRollBuffers.removeFirst()
                        }

                        // VAD Logic: Require N consecutive frames above dynamic threshold
                        // Gate: completely block speech detection while TTS is actively playing
                        val isTtsActive = kokoroRms > 0.01f
                        if (!hasSpeechStarted) {
                            if (isTtsActive) {
                                // TTS is playing — don't detect speech (would be echo)
                                consecutiveSpeechFrames = 0
                            } else if (normalized > dynamicSilenceFloor) {
                                consecutiveSpeechFrames++
                                if (consecutiveSpeechFrames >= requiredSpeechFrames) {
                                    hasSpeechStarted = true
                                    
                                    // Speech started: Write the pre-roll buffers to the WAV file first
                                    try {
                                        for (prevBuffer in preRollBuffers) {
                                            for (i in 0 until prevBuffer.size) {
                                                val sample = prevBuffer[i]
                                                fos.write(sample.toInt() and 0xFF)
                                                fos.write((sample.toInt() shr 8) and 0xFF)
                                                totalBytesWritten += 2
                                            }
                                        }
                                        preRollBuffers.clear()
                                        Log.i("AudioRecorder", "VAD: Speech started. Wrote pre-roll buffers to WAV file.")
                                    } catch (e: Exception) {
                                        Log.e("AudioRecorder", "VAD: Failed to write pre-roll buffers", e)
                                    }

                                    onSpeechStarted?.invoke()
                                }
                            } else {
                                consecutiveSpeechFrames = 0
                            }
                        } else {
                            // Speech is active: Write current samples directly to the WAV file
                            for (i in 0 until readSize) {
                                val sample = buffer[i]
                                fos.write(sample.toInt() and 0xFF)
                                fos.write((sample.toInt() shr 8) and 0xFF)
                                totalBytesWritten += 2
                            }

                            if (normalized < dynamicSilenceThreshold) {
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

                fos.close()
                try {
                    record.stop()
                } catch (e: Exception) {}
                record.release()

                // Overwrite the dummy header with correct sizes
                val wavFile = RandomAccessFile(outputFile, "rw")
                updateWavHeader(wavFile, totalBytesWritten)
                wavFile.close()

                Log.d("AudioRecorder", "Audio recorded successfully to ${outputFile.absolutePath}, size=$totalBytesWritten")
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Error recording audio", e)
            } finally {
                isRecording = false
            }
        }
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