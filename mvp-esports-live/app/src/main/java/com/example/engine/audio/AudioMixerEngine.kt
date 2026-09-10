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
import kotlin.math.abs

/**
 * AudioMixerEngine coordinates real-time mixing of:
 * 1. Internal / Device / Game Audio
 * 2. Microphone Commentary
 * 3. User-selected Gallery / Device Music
 *
 * It produces a single mixed 44.1kHz stereo 16-bit PCM stream
 * for HardwareAudioEncoder.
 *
 * Part 2-D lifecycle goals:
 * - Start sources before the mixer loop.
 * - Keep internal audio, microphone and music independently controllable.
 * - Keep telemetry available for UI level meters.
 * - Stop accepting new mixed frames before sources are released.
 * - Detach the encoder consumer during shutdown to avoid new-frame races.
 */
class AudioMixerEngine(
    val sampleRate: Int = 44100,
    val channelCount: Int = 2
) {
    private val TAG = "AudioMixerEngine"

    val internalAudioSource =
        InternalAudioCaptureSource(sampleRate, channelCount)

    val microphoneSource =
        MicrophoneAudioSource(sampleRate, channelCount)

    val musicSource =
        MusicAudioSource(sampleRate, channelCount)

    private val isRunning = AtomicBoolean(false)

    private var mixingJob: Job? = null
    private var telemetryJob: Job? = null

    val isMasterMuted = AtomicBoolean(false)

    @Volatile
    var masterVolume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 2.0f)
        }

    @Volatile
    var enableDucking: Boolean = true

    @Volatile
    var duckingStrength: Float = 0.60f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    private val _mixerState =
        MutableStateFlow(AudioMixerState())

    val mixerState: StateFlow<AudioMixerState> =
        _mixerState.asStateFlow()

    interface AudioFrameConsumer {
        fun onMixedAudioPcm(
            pcmBytes: ByteArray,
            sampleCount: Int,
            ptsUs: Long
        )
    }

    @Volatile
    private var frameConsumer: AudioFrameConsumer? = null

    fun setFrameConsumer(
        consumer: AudioFrameConsumer?
    ) {
        frameConsumer = consumer
    }

  @Synchronized
fun start(
    mediaProjection: MediaProjection? = null
) : Boolean {
        if (isRunning.get()) {
            if (mediaProjection != null) {
                internalAudioSource.updateMediaProjection(
                    mediaProjection
                )
            }
            return true
        }

        try {
            val internalStarted =
                internalAudioSource.start(mediaProjection)

            val microphoneStarted =
                microphoneSource.start()

            if (!internalStarted) {
                Log.w(
                    TAG,
                    "Internal audio source did not start normally."
                )
            }

            if (!microphoneStarted) {
                Log.w(
                    TAG,
                    "Microphone source did not start normally."
                )
            }

            isRunning.set(true)

            _mixerState.update {
                it.copy(
                    isMixing = true
                )
            }

            startMixingLoop()
            startTelemetryLoop()

            Log.i(
                TAG,
                "AudioMixerEngine started " +
                    "($sampleRate Hz, $channelCount ch stereo)"
            )

            return true
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to start AudioMixerEngine: ${e.message}",
                e
            )

            stop()

            return false
        }
    }

   @Synchronized
fun start(
    mediaProjection: MediaProjection? = null
) {
        if (mediaProjection != null) {
            internalAudioSource.updateMediaProjection(
                mediaProjection
            )
        }
    }

    private fun startMixingLoop() {
        mixingJob = CoroutineScope(
            Dispatchers.IO
        ).launch {

            val frameSize =
                1024 * channelCount

            val internalBuf =
                ShortArray(frameSize)

            val micBuf =
                ShortArray(frameSize)

            val musicBuf =
                ShortArray(frameSize)

            val mixedBuf =
                ShortArray(frameSize)

            val byteBuffer =
                ByteBuffer
                    .allocateDirect(frameSize * 2)
                    .order(ByteOrder.LITTLE_ENDIAN)

            val pcmBytes =
                ByteArray(frameSize * 2)

            while (
                isActive &&
                isRunning.get()
            ) {
                val startTimeNs =
                    System.nanoTime()

                /*
                 * Read all three sources.
                 *
                 * Each source already applies its own:
                 * - enabled/disabled state
                 * - mute state
                 * - volume
                 * - source-specific processing
                 */
                internalAudioSource.read(
                    internalBuf,
                    0,
                    frameSize
                )

                microphoneSource.read(
                    micBuf,
                    0,
                    frameSize
                )

                musicSource.read(
                    musicBuf,
                    0,
                    frameSize
                )

                val micPeak =
                    microphoneSource.currentPeakLevel

                val duckingFactor =
                    if (
                        enableDucking &&
                        micPeak > 0.08f
                    ) {
                        (
                            1f - duckingStrength
                        ).coerceIn(
                            0.1f,
                            1f
                        )
                    } else {
                        1f
                    }

                val masterVol =
                    if (isMasterMuted.get()) {
                        0f
                    } else {
                        masterVolume
                    }

                for (i in 0 until frameSize) {
                    val internalSample =
                        internalBuf[i].toFloat()

                    val micSample =
                        micBuf[i].toFloat()

                    val musicSample =
                        musicBuf[i].toFloat() *
                            duckingFactor

                    val mixed =
                        (
                            internalSample +
                                micSample +
                                musicSample
                            ) * masterVol

                    /*
                     * Hard sample ceiling keeps 16-bit PCM valid.
                     * Source-level volume controls are applied before
                     * this final mix stage.
                     */
                    val clamped =
                        mixed
                            .toInt()
                            .coerceIn(
                                -32768,
                                32767
                            )
                            .toShort()

                    mixedBuf[i] = clamped
                }

                /*
                 * Convert ShortArray -> little-endian PCM bytes.
                 */
                byteBuffer.clear()

                val shortView =
                    byteBuffer.asShortBuffer()

                shortView.put(mixedBuf)

                byteBuffer.position(0)

                byteBuffer.get(
                    pcmBytes
                )

                /*
                 * Use a fresh timestamp for the mixed frame.
                 */
                val frameTimestampUs =
                    System.nanoTime() / 1000L

                /*
                 * Read the current consumer only once.
                 *
                 * This avoids a race if the consumer is detached
                 * during shutdown.
                 */
                val consumer =
                    frameConsumer

                if (
                    consumer != null &&
                    isRunning.get()
                ) {
                    consumer.onMixedAudioPcm(
                        pcmBytes,
                        frameSize,
                        frameTimestampUs
                    )
                }

                /*
                 * Maintain approximately real-time 1024-sample cadence.
                 *
                 * At 44.1kHz:
                 * 1024 / 44100 ~= 23.22ms
                 */
                val targetDurationNs =
                    (
                        1024L *
                            1_000_000_000L
                        ) / sampleRate

                val elapsedNs =
                    System.nanoTime() -
                        startTimeNs

                val sleepNs =
                    targetDurationNs -
                        elapsedNs

                if (sleepNs > 1_000_000L) {
                    Thread.sleep(
                        sleepNs /
                            1_000_000L,
                        (
                            sleepNs %
                                1_000_000L
                            ).toInt()
                    )
                }
            }
        }
    }

    private fun startTelemetryLoop() {
        telemetryJob = CoroutineScope(
            Dispatchers.Default
        ).launch {

            while (
                isActive &&
                isRunning.get()
            ) {
                kotlinx.coroutines.delay(
                    100
                )

                val internalPeak =
                    internalAudioSource.currentPeakLevel

                val micPeak =
                    microphoneSource.currentPeakLevel

                val musicPeak =
                    musicSource.currentPeakLevel

                val masterPeak =
                    if (isMasterMuted.get()) {
                        0f
                    } else {
                        (
                            internalPeak +
                                micPeak +
                                musicPeak
                            ) * masterVolume
                    }.coerceIn(
                        0f,
                        1f
                    )

                _mixerState.update { current ->
                    current.copy(
                        masterVolume = masterVolume,
                        isMasterMuted =
                            isMasterMuted.get(),

                        internalAudio =
                            InternalAudioState(
                                isEnabled =
                                    internalAudioSource
                                        .isEnabled
                                        .get(),

                                isMuted =
                                    internalAudioSource
                                        .isMuted
                                        .get(),

                                volume =
                                    internalAudioSource
                                        .volume,

                                peakLevel =
                                    internalPeak
                            ),

                        microphone =
                            MicrophoneState(
                                isEnabled =
                                    microphoneSource
                                        .isEnabled
                                        .get(),

                                isMuted =
                                    microphoneSource
                                        .isMuted
                                        .get(),

                                volume =
                                    microphoneSource
                                        .volume,

                                noiseSuppression =
                                    microphoneSource
                                        .enableNoiseSuppression,

                                echoCancellation =
                                    microphoneSource
                                        .enableEchoCancellation,

                                voiceClarity =
                                    microphoneSource
                                        .enableVoiceClarity,

                                isNoiseSuppressorActive =
                                    microphoneSource
                                        .isNoiseSuppressorActive,

                                isAcousticEchoCancelerActive =
                                    microphoneSource
                                        .isAcousticEchoCancelerActive,

                                isAutomaticGainControlActive =
                                    microphoneSource
                                        .isAutomaticGainControlActive,

                                peakLevel =
                                    micPeak
                            ),

                        music =
                            MusicPlayerState(
                                isEnabled =
                                    musicSource
                                        .isEnabled
                                        .get(),

                                isMuted =
                                    musicSource
                                        .isMuted
                                        .get(),

                                volume =
                                    musicSource
                                        .volume,

                                isLooping =
                                    musicSource
                                        .isLooping
                                        .get(),

                                playbackState =
                                    musicSource
                                        .getPlaybackState(),

                                trackTitle =
                                    musicSource
                                        .trackTitle,

                                trackArtist =
                                    musicSource
                                        .trackArtist,

                                trackUri =
                                    musicSource
                                        .trackUri,

                                durationMs =
                                    musicSource
                                        .durationMs,

                                currentPositionMs =
                                    musicSource
                                        .currentPositionMs,

                                peakLevel =
                                    musicPeak
                            ),

                        masterPeakLevel =
                            masterPeak
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Master Controls
    // ---------------------------------------------------------------------

    fun setMasterVol(
        volume: Float
    ) {
        masterVolume =
            volume.coerceIn(
                0f,
                2.0f
            )
    }

    fun toggleMasterMute() {
        isMasterMuted.set(
            !isMasterMuted.get()
        )
    }

    // ---------------------------------------------------------------------
    // Internal / Game Audio Controls
    // ---------------------------------------------------------------------

    fun toggleInternalAudio(
        enabled: Boolean? = null
    ) {
        val next =
            enabled ?: !internalAudioSource
                .isEnabled
                .get()

        internalAudioSource
            .isEnabled
            .set(next)
    }

    fun toggleInternalAudioMute() {
        internalAudioSource
            .isMuted
            .set(
                !internalAudioSource
                    .isMuted
                    .get()
            )
    }

    fun setInternalAudioVolume(
        volume: Float
    ) {
        internalAudioSource.volume =
            volume.coerceIn(
                0f,
                2.0f
            )
    }

    // ---------------------------------------------------------------------
    // Microphone Controls
    // ---------------------------------------------------------------------

    fun toggleMicrophone(
        enabled: Boolean? = null
    ) {
        val next =
            enabled ?: !microphoneSource
                .isEnabled
                .get()

        microphoneSource
            .isEnabled
            .set(next)
    }

    fun toggleMicrophoneMute() {
        microphoneSource
            .isMuted
            .set(
                !microphoneSource
                    .isMuted
                    .get()
            )
    }

    fun setMicrophoneVolume(
        volume: Float
    ) {
        microphoneSource.volume =
            volume.coerceIn(
                0f,
                2.0f
            )
    }

    fun toggleNoiseSuppression() {
        microphoneSource
            .enableNoiseSuppression =
            !microphoneSource
                .enableNoiseSuppression
    }

    fun toggleEchoCancellation() {
        microphoneSource
            .enableEchoCancellation =
            !microphoneSource
                .enableEchoCancellation
    }

    fun toggleVoiceClarity() {
        microphoneSource
            .enableVoiceClarity =
            !microphoneSource
                .enableVoiceClarity
    }

    // ---------------------------------------------------------------------
    // Music Player Controls
    // ---------------------------------------------------------------------

    fun loadMusicTrack(
        context: Context,
        uri: Uri
    ): Boolean {
        return musicSource.loadTrack(
            context,
            uri
        )
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
        musicSource.isLooping.set(
            !musicSource.isLooping.get()
        )
    }

    fun toggleMusicMute() {
        musicSource.isMuted.set(
            !musicSource.isMuted.get()
        )
    }

    fun toggleMusicEnabled(
        enabled: Boolean? = null
    ) {
        val next =
            enabled ?: !musicSource
                .isEnabled
                .get()

        musicSource
            .isEnabled
            .set(next)
    }

    fun setMusicVolume(
        volume: Float
    ) {
        musicSource.volume =
            volume.coerceIn(
                0f,
                2.0f
            )
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    /**
 * Gracefully stops the complete audio mixing engine.
 *
 * Shutdown order:
 *
 * 1. Stop the mixer from producing new output frames.
 * 2. Detach the downstream encoder consumer.
 * 3. Cancel mixer + telemetry jobs.
 * 4. Wait for those jobs to finish.
 * 5. Stop all audio sources.
 * 6. Publish the final non-mixing state.
 *
 * The HardwareAudioEncoder / MediaMuxer lifecycle is owned by
 * OutputCompositionPipeline and is intentionally NOT touched here.
 */
@Synchronized
fun stop() {
    if (!isRunning.getAndSet(false)) {
        return
    }

    Log.i(
        TAG,
        "Stopping AudioMixerEngine gracefully..."
    )

    /*
     * Step 1:
     * Prevent the mixer loop from sending any more frames downstream.
     */
    frameConsumer = null

    /*
     * Step 2:
     * Capture the current jobs before cancellation so we can wait for
     * both coroutines to actually finish.
     */
    val mixerJob = mixingJob
    val telemetry = telemetryJob

    /*
     * Step 3:
     * Request both jobs to stop.
     */
    mixerJob?.cancel()
    telemetry?.cancel()

    /*
     * Step 4:
     * Wait for both jobs to finish before releasing the audio sources.
     *
     * This prevents a mixer iteration from still reading from a source
     * while that source is being released.
     */
    try {
        if (mixerJob != null) {
            kotlinx.coroutines.runBlocking {
                mixerJob.join()
            }
        }
    } catch (e: Exception) {
        Log.w(
            TAG,
            "Mixer job shutdown wait failed: ${e.message}"
        )
    }

    try {
        if (telemetry != null) {
            kotlinx.coroutines.runBlocking {
                telemetry.join()
            }
        }
    } catch (e: Exception) {
        Log.w(
            TAG,
            "Telemetry job shutdown wait failed: ${e.message}"
        )
    }

    /*
     * Step 5:
     * Now that the jobs are finished, safely release all audio sources.
     */
    try {
        internalAudioSource.stop()
    } catch (e: Exception) {
        Log.w(
            TAG,
            "Failed to stop internal audio source: ${e.message}"
        )
    }

    try {
        microphoneSource.stop()
    } catch (e: Exception) {
        Log.w(
            TAG,
            "Failed to stop microphone source: ${e.message}"
        )
    }

    try {
        musicSource.stop()
    } catch (e: Exception) {
        Log.w(
            TAG,
            "Failed to stop music source: ${e.message}"
        )
    }

    /*
     * Clear completed job references.
     */
    mixingJob = null
    telemetryJob = null

    /*
     * Step 6:
     * Publish the final mixer state.
     */
    _mixerState.update {
        it.copy(
            isMixing = false
        )
    }

    Log.i(
        TAG,
        "AudioMixerEngine stopped safely."
    )
}