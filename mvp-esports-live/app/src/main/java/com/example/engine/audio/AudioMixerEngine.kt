package com.example.engine.audio

import android.content.Context
import android.media.projection.MediaProjection
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AudioMixerEngine coordinates real-time mixing of:
 * 1. Internal/Device/Game Audio (AudioPlaybackCapture)
 * 2. Microphone Commentary (AudioRecord + Hardware DSP: AEC, NS, AGC)
 * 3. Music Player (Gallery/Device File Decoder)
 *
 * It produces a single mixed, soft-limited 44.1kHz stereo 16-bit PCM stream
 * for the Hardware AAC Audio Encoder (feeding MP4 Muxer & YouTube RTMP).
 */
class AudioMixerEngine(
    val sampleRate: Int = 44100,
    val channelCount: Int = 2
) {
    private val TAG = "AudioMixerEngine"

    val internalAudioSource = InternalAudioCaptureSource(sampleRate, channelCount)
    val microphoneSource = MicrophoneAudioSource(sampleRate, channelCount)
    val musicSource = MusicAudioSource(sampleRate, channelCount)

    private val isRunning = AtomicBoolean(false)
    private var mixingJob: Job? = null
    private var telemetryJob: Job? = null

    val isMasterMuted = AtomicBoolean(false)
    var masterVolume: Float = 1.0f
    var enableDucking: Boolean = true
    var duckingStrength: Float = 0.60f

    private val _mixerState = MutableStateFlow(AudioMixerState())
    val mixerState: StateFlow<AudioMixerState> = _mixerState.asStateFlow()

    interface AudioFrameConsumer {
        fun onMixedAudioPcm(pcmBytes: ByteArray, sampleCount: Int, ptsUs: Long)
    }

    private var frameConsumer: AudioFrameConsumer? = null

    fun setFrameConsumer(consumer: AudioFrameConsumer?) {
        this.frameConsumer = consumer
    }

    fun start(mediaProjection: MediaProjection? = null): Boolean {
        if (isRunning.get()) {
            if (mediaProjection != null) {
                internalAudioSource.updateMediaProjection(mediaProjection)
            }
            return true
        }

        internalAudioSource.start(mediaProjection)
        microphoneSource.start()

        isRunning.set(true)
        _mixerState.update { it.copy(isMixing = true) }

        // Start Audio Mixing Loop
        startMixingLoop()
        startTelemetryLoop()

        Log.i(TAG, "AudioMixerEngine started ($sampleRate Hz, $channelCount ch stereo)")
        return true
    }

    fun updateMediaProjection(mediaProjection: MediaProjection?) {
        if (mediaProjection != null) {
            internalAudioSource.updateMediaProjection(mediaProjection)
        }
    }

    private fun startMixingLoop() {
        mixingJob = CoroutineScope(Dispatchers.IO).launch {
            val frameSize = 1024 * channelCount // 2048 shorts (4096 bytes)
            val internalBuf = ShortArray(frameSize)
            val micBuf = ShortArray(frameSize)
            val musicBuf = ShortArray(frameSize)
            val mixedBuf = ShortArray(frameSize)
            val byteBuffer = ByteBuffer.allocateDirect(frameSize * 2).order(ByteOrder.LITTLE_ENDIAN)
            val pcmBytes = ByteArray(frameSize * 2)

            var frameTimestampUs = System.nanoTime() / 1000L

            while (isActive && isRunning.get()) {
                val startTimeNs = System.nanoTime()

                // Read individual source buffers in parallel / sequence
                internalAudioSource.read(internalBuf, 0, frameSize)
                microphoneSource.read(micBuf, 0, frameSize)
                musicSource.read(musicBuf, 0, frameSize)

                val micPeak = microphoneSource.currentPeakLevel
                val duckingFactor = if (enableDucking && micPeak > 0.08f) (1f - duckingStrength).coerceIn(0.1f, 1f) else 1f

                val isMutedMaster = isMasterMuted.get()
                val masterVol = if (isMutedMaster) 0f else masterVolume
                var masterPeak = 0f

                // Mix samples with soft limiting
                for (i in 0 until frameSize) {
                    val intSample = internalBuf[i].toFloat()
                    val micSample = micBuf[i].toFloat()
                    val musSample = musicBuf[i].toFloat() * duckingFactor

                    val sum = (intSample + micSample + musSample) * masterVol
                    val clamped = sum.toInt().coerceIn(-32768, 32767).toShort()
                    mixedBuf[i] = clamped

                    val abs = kotlin.math.abs(clamped.toFloat())
                    if (abs > masterPeak) masterPeak = abs
                }

                // Copy to byte buffer
                byteBuffer.clear()
                val shortView = byteBuffer.asShortBuffer()
                shortView.put(mixedBuf)
                byteBuffer.position(0)
                byteBuffer.get(pcmBytes)

                frameTimestampUs = System.nanoTime() / 1000L

                // Deliver to consumer (Hardware AAC Audio Encoder)
                frameConsumer?.onMixedAudioPcm(pcmBytes, frameSize, frameTimestampUs)

                // Precise frame sleep to match real-time cadence (e.g. ~23.2ms for 1024 samples @ 44.1kHz)
                val targetDurationNs = (1024L * 1_000_000_000L) / sampleRate
                val elapsedNs = System.nanoTime() - startTimeNs
                val sleepNs = targetDurationNs - elapsedNs
                if (sleepNs > 1_000_000L) {
                    Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                }
            }
        }
    }

    private fun startTelemetryLoop() {
        telemetryJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isRunning.get()) {
                kotlinx.coroutines.delay(100) // 10 Hz UI telemetry refresh

                _mixerState.update { current ->
                    current.copy(
                        masterVolume = masterVolume,
                        isMasterMuted = isMasterMuted.get(),
                        internalAudio = InternalAudioState(
                            isEnabled = internalAudioSource.isEnabled.get(),
                            isMuted = internalAudioSource.isMuted.get(),
                            volume = internalAudioSource.volume,
                            peakLevel = internalAudioSource.currentPeakLevel
                        ),
                        microphone = MicrophoneState(
                            isEnabled = microphoneSource.isEnabled.get(),
                            isMuted = microphoneSource.isMuted.get(),
                            volume = microphoneSource.volume,
                            noiseSuppression = microphoneSource.enableNoiseSuppression,
                            echoCancellation = microphoneSource.enableEchoCancellation,
                            voiceClarity = microphoneSource.enableVoiceClarity,
                            isNoiseSuppressorActive = microphoneSource.isNoiseSuppressorActive,
                            isAcousticEchoCancelerActive = microphoneSource.isAcousticEchoCancelerActive,
                            isAutomaticGainControlActive = microphoneSource.isAutomaticGainControlActive,
                            peakLevel = microphoneSource.currentPeakLevel
                        ),
                        music = MusicPlayerState(
                            isEnabled = musicSource.isEnabled.get(),
                            isMuted = musicSource.isMuted.get(),
                            volume = musicSource.volume,
                            isLooping = musicSource.isLooping.get(),
                            playbackState = musicSource.getPlaybackState(),
                            trackTitle = musicSource.trackTitle,
                            trackArtist = musicSource.trackArtist,
                            trackUri = musicSource.trackUri,
                            durationMs = musicSource.durationMs,
                            currentPositionMs = musicSource.currentPositionMs,
                            peakLevel = musicSource.currentPeakLevel
                        ),
                        masterPeakLevel = if (isMasterMuted.get()) 0f else (
                            (internalAudioSource.currentPeakLevel + microphoneSource.currentPeakLevel + musicSource.currentPeakLevel) * masterVolume
                        ).coerceIn(0f, 1f)
                    )
                }
            }
        }
    }

    // --- Control Methods ---

    // Master Controls
    fun setMasterVol(volume: Float) {
        masterVolume = volume.coerceIn(0f, 2.0f)
    }

    fun toggleMasterMute() {
        isMasterMuted.set(!isMasterMuted.get())
    }

    // Internal Audio Controls
    fun toggleInternalAudio(enabled: Boolean? = null) {
        val next = enabled ?: !internalAudioSource.isEnabled.get()
        internalAudioSource.isEnabled.set(next)
    }

    fun toggleInternalAudioMute() {
        internalAudioSource.isMuted.set(!internalAudioSource.isMuted.get())
    }

    fun setInternalAudioVolume(volume: Float) {
        internalAudioSource.volume = volume.coerceIn(0f, 2.0f)
    }

    // Microphone Controls
    fun toggleMicrophone(enabled: Boolean? = null) {
        val next = enabled ?: !microphoneSource.isEnabled.get()
        microphoneSource.isEnabled.set(next)
    }

    fun toggleMicrophoneMute() {
        microphoneSource.isMuted.set(!microphoneSource.isMuted.get())
    }

    fun setMicrophoneVolume(volume: Float) {
        microphoneSource.volume = volume.coerceIn(0f, 2.0f)
    }

    fun toggleNoiseSuppression() {
        microphoneSource.enableNoiseSuppression = !microphoneSource.enableNoiseSuppression
    }

    fun toggleEchoCancellation() {
        microphoneSource.enableEchoCancellation = !microphoneSource.enableEchoCancellation
    }

    fun toggleVoiceClarity() {
        microphoneSource.enableVoiceClarity = !microphoneSource.enableVoiceClarity
    }

    // Music Player Controls
    fun loadMusicTrack(context: Context, uri: Uri): Boolean {
        return musicSource.loadTrack(context, uri)
    }

    fun playMusic() {
        musicSource.play()
    }

    fun pauseMusic() {
        musicSource.pause()
    }

    fun stopMusic() {
        musicSource.stop()
    }

    fun toggleMusicPlayPause() {
        musicSource.togglePlayPause()
    }

    fun toggleMusicLoop() {
        musicSource.isLooping.set(!musicSource.isLooping.get())
    }

    fun toggleMusicMute() {
        musicSource.isMuted.set(!musicSource.isMuted.get())
    }

    fun toggleMusicEnabled(enabled: Boolean? = null) {
        val next = enabled ?: !musicSource.isEnabled.get()
        musicSource.isEnabled.set(next)
    }

    fun setMusicVolume(volume: Float) {
        musicSource.volume = volume.coerceIn(0f, 2.0f)
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        mixingJob?.cancel()
        telemetryJob?.cancel()

        internalAudioSource.stop()
        microphoneSource.stop()
        musicSource.stop()

        _mixerState.update { it.copy(isMixing = false) }
        Log.i(TAG, "AudioMixerEngine stopped.")
    }
}
