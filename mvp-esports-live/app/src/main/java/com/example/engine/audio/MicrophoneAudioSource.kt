package com.example.engine.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MicrophoneAudioSource captures live player commentary with hardware/software DSP:
 * - Acoustic Echo Cancellation (AEC)
 * - Noise Suppression (NS)
 * - Automatic Gain Control (AGC) / Voice Clarity
 */
class MicrophoneAudioSource(
    val sampleRate: Int = 44100,
    val channelCount: Int = 2
) {
    private val TAG = "MicrophoneAudioSource"

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var gainControl: AutomaticGainControl? = null

    val isRunning = AtomicBoolean(false)
    val isEnabled = AtomicBoolean(true)
    val isMuted = AtomicBoolean(false)
    var volume: Float = 1.0f

    var enableNoiseSuppression: Boolean = true
        set(value) {
            field = value
            try {
                noiseSuppressor?.enabled = value
            } catch (_: Exception) {}
        }

    var enableEchoCancellation: Boolean = true
        set(value) {
            field = value
            try {
                echoCanceler?.enabled = value
            } catch (_: Exception) {}
        }

    var enableVoiceClarity: Boolean = true
        set(value) {
            field = value
            try {
                gainControl?.enabled = value
            } catch (_: Exception) {}
        }

    var isNoiseSuppressorActive: Boolean = false
        private set
    var isAcousticEchoCancelerActive: Boolean = false
        private set
    var isAutomaticGainControlActive: Boolean = false
        private set

    @Volatile
    var currentPeakLevel: Float = 0f
        private set

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRunning.get()) return true

        try {
            val channelConfig = if (channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(8192)

            val audioSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            } else {
                MediaRecorder.AudioSource.MIC
            }

            val record = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord state not initialized, fallback to MIC audio source.")
                record.release()
                return startFallback(channelConfig, audioFormat, bufferSize)
            }

            val sessionId = record.audioSessionId
            attachAudioEffects(sessionId)

            record.startRecording()
            this.audioRecord = record
            this.isRunning.set(true)
            Log.i(TAG, "MicrophoneAudioSource started ($sampleRate Hz, $channelCount ch, Session $sessionId)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MicrophoneAudioSource: ${e.message}", e)
            stop()
            return false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startFallback(channelConfig: Int, audioFormat: Int, bufferSize: Int): Boolean {
        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }
            attachAudioEffects(record.audioSessionId)
            record.startRecording()
            this.audioRecord = record
            this.isRunning.set(true)
            Log.i(TAG, "MicrophoneAudioSource started via standard MIC source.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fallback MIC start failed: ${e.message}")
            false
        }
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        if (audioSessionId == 0) return

        // 1. Noise Suppressor
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = enableNoiseSuppression
                }
                isNoiseSuppressorActive = noiseSuppressor != null
                Log.i(TAG, "Hardware NoiseSuppressor active: $isNoiseSuppressorActive")
            }
        } catch (e: Exception) {
            Log.w(TAG, "NoiseSuppressor not supported: ${e.message}")
        }

        // 2. Acoustic Echo Canceler
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = enableEchoCancellation
                }
                isAcousticEchoCancelerActive = echoCanceler != null
                Log.i(TAG, "Hardware AcousticEchoCanceler active: $isAcousticEchoCancelerActive")
            }
        } catch (e: Exception) {
            Log.w(TAG, "AcousticEchoCanceler not supported: ${e.message}")
        }

        // 3. Automatic Gain Control / Voice Clarity
        try {
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = enableVoiceClarity
                }
                isAutomaticGainControlActive = gainControl != null
                Log.i(TAG, "Hardware AutomaticGainControl active: $isAutomaticGainControlActive")
            }
        } catch (e: Exception) {
            Log.w(TAG, "AutomaticGainControl not supported: ${e.message}")
        }
    }

    /**
     * Reads PCM short samples into provided destination buffer.
     * Returns the count of samples read.
     */
    fun read(targetBuffer: ShortArray, offset: Int, length: Int): Int {
        val record = audioRecord ?: return 0
        if (!isRunning.get() || !isEnabled.get()) {
            targetBuffer.fill(0, offset, offset + length)
            currentPeakLevel = 0f
            return length
        }

        val samplesRead = record.read(targetBuffer, offset, length)
        if (samplesRead > 0) {
            var peak: Float = 0f
            if (isMuted.get()) {
                targetBuffer.fill(0, offset, offset + samplesRead)
                currentPeakLevel = 0f
            } else {
                val vol = volume
                for (i in offset until (offset + samplesRead)) {
                    var s = targetBuffer[i].toFloat() * vol
                    val abs = kotlin.math.abs(s)
                    if (abs > peak) peak = abs
                    targetBuffer[i] = s.toInt().coerceIn(-32768, 32767).toShort()
                }
                currentPeakLevel = (peak / 32768f).coerceIn(0f, 1f)
            }
        }
        return samplesRead
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        noiseSuppressor = null
        isNoiseSuppressorActive = false

        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        echoCanceler = null
        isAcousticEchoCancelerActive = false

        try {
            gainControl?.release()
        } catch (_: Exception) {}
        gainControl = null
        isAutomaticGainControlActive = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        currentPeakLevel = 0f
        Log.i(TAG, "MicrophoneAudioSource stopped.")
    }
}
