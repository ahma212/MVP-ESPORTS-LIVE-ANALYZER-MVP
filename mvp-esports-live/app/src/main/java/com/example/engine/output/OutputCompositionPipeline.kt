package com.example.engine.output

import android.content.Context
import android.media.projection.MediaProjection
import android.util.Log
import android.view.Surface
import com.example.engine.audio.AudioMixerEngine
import com.example.engine.audio.HardwareAudioEncoder
import com.example.engine.composition.GlesCompositionCompositor
import com.example.engine.core.CaptureOutputContract
import com.example.engine.output.rtmp.RtmpStreamSink
import com.example.model.BannerStripConfig
import com.example.model.CompositionConfig
import com.example.model.OverlayConfig
import com.example.model.RecordingConfig
import com.example.model.VideoAdjustmentConfig
import java.io.File

/**
 * OutputCompositionPipeline manages the complete OUTPUT LAYER:
 *
 *   [Capture Input (MediaProjection)]
 *                 ↓
 *     [GPU OpenGL Composition Engine (GlesCompositionCompositor)]
 *     (Overlays: Photos, PNGs, Memes, Loopable Videos, Banners, Bottom Strips)
 *                 ↓
 *   [Hardware MediaCodec Encoder] (H.264 / AVC Zero-Copy Input Surface)
 *                 ↓
 *     [Output Sinks: MP4 Muxer & YouTube RTMP Live Stream]
 *
 *   [Audio Mixer Engine: Game Audio + Mic DSP + Gallery Music]
 *                 ↓
 *      [Hardware AAC Encoder] → [MP4 Muxer (Audio Track) & RTMP Stream]
 *
 * CRITICAL ARCHITECTURAL GUARANTEE:
 * Floating controls, pointer menus, chat drawers, and audio mixer UI are physically
 * excluded from this pipeline and only reside on the device screen CONTROL LAYER.
 */
class OutputCompositionPipeline(
    private val recordingConfig: RecordingConfig,
    private val overlayConfig: OverlayConfig = OverlayConfig(),
    private val bannerConfig: BannerStripConfig = BannerStripConfig(),
    private val videoAdjustmentConfig: VideoAdjustmentConfig = VideoAdjustmentConfig(),
    private val deviceScreenWidth: Int = 1080,
    private val deviceScreenHeight: Int = 2400,
    private val context: Context? = null,
    private var initialCompositionConfig: CompositionConfig = CompositionConfig()
) {
    private val TAG = "OutputCompositionPipeline"

    private var hardwareEncoder: HardwareVideoEncoder? = null
    private var glesCompositor: GlesCompositionCompositor? = null
    private var audioEncoder: HardwareAudioEncoder? = null
    private var audioMixer: AudioMixerEngine? = null
    private var rtmpSink: RtmpStreamSink? = null
    private var muxerSink: MediaMuxerSink? = null
    private var encoderSurface: Surface? = null
    private var activeCompositionConfig: CompositionConfig = initialCompositionConfig.copy(videoAdjustmentConfig = videoAdjustmentConfig)
    private var activeOutputFilePath: String? = null

    val outputDimensions: ResolutionAdapter.OutputDimensions = ResolutionAdapter.calculateOptimalDimensions(
        targetResolution = recordingConfig.resolution,
        deviceScreenWidth = deviceScreenWidth,
        deviceScreenHeight = deviceScreenHeight,
        orientation = recordingConfig.orientation
    )

    interface PipelineListener {
        fun onPipelineStarted(width: Int, height: Int, codecName: String, isHardware: Boolean)
        fun onFrameEncoded(frameIndex: Long, isKeyFrame: Boolean)
        fun onPipelineStopped(outputFilePath: String?, totalFrames: Long)
        fun onPipelineError(error: String)
    }

    private var listener: PipelineListener? = null

    fun setListener(listener: PipelineListener) {
        this.listener = listener
    }

    /**
     * Initializes the Output Layer pipeline for local MP4 recording.
     */
    fun startPipeline(
        outputFile: File,
        audioMixer: AudioMixerEngine? = null,
        mediaProjection: MediaProjection? = null
    ): Surface {
        return initializePipelineInternal(
            outputFile = outputFile,
            rtmpSink = null,
            audioMixer = audioMixer,
            mediaProjection = mediaProjection
        )
    }

    /**
     * Initializes the Output Layer pipeline for YouTube Live streaming (with optional local recording).
     */
    fun startLiveStreamPipeline(
        rtmpSink: RtmpStreamSink,
        outputFile: File? = null,
        audioMixer: AudioMixerEngine? = null,
        mediaProjection: MediaProjection? = null
    ): Surface {
        return initializePipelineInternal(
            outputFile = outputFile,
            rtmpSink = rtmpSink,
            audioMixer = audioMixer,
            mediaProjection = mediaProjection
        )
    }

@Synchronized
private fun initializePipelineInternal(
    outputFile: File?,
    rtmpSink: RtmpStreamSink?,
    audioMixer: AudioMixerEngine?,
    mediaProjection: MediaProjection?
): Surface {
    if (
        hardwareEncoder != null ||
        audioEncoder != null ||
        audioMixer != null ||
        glesCompositor != null ||
        rtmpSink != null ||
        muxerSink != null ||
        encoderSurface != null
    ) {
        throw IllegalStateException(
            "OutputCompositionPipeline is already active. Stop the current pipeline before starting a new one."
        )
    }

    activeOutputFilePath = outputFile?.absolutePath

    Log.i(
    TAG,
    "Starting Output Layer Pipeline: " +
        "${outputDimensions.width}x${outputDimensions.height} " +
        "(${recordingConfig.fps.fpsValue} FPS, " +
        "${recordingConfig.bitrateMbps} Mbps, " +
        "Live: ${rtmpSink != null})"
)
        this.rtmpSink = rtmpSink

        // 1. Shared MediaMuxerSink if recording to file
        val sharedMuxer = if (outputFile != null) MediaMuxerSink(outputFile) else null
        this.muxerSink = sharedMuxer

        // 2. Setup Hardware Video Encoder
        val videoEncoder = HardwareVideoEncoder(
            width = outputDimensions.width,
            height = outputDimensions.height,
            fps = recordingConfig.fps,
            bitrateMbps = recordingConfig.bitrateMbps,
            codec = recordingConfig.codec,
            keyframeIntervalSeconds = 2
        )
        videoEncoder.setRtmpSink(rtmpSink)
        if (sharedMuxer != null) {
            videoEncoder.setMuxerSink(sharedMuxer)
        }

        videoEncoder.setCallback(object : HardwareVideoEncoder.EncoderCallback {
            override fun onFormatChanged(format: android.media.MediaFormat) {
                Log.i(TAG, "Output Video Format: $format")
            }

            override fun onFrameEncoded(frameIndex: Long, isKeyFrame: Boolean, sizeBytes: Int) {
                listener?.onFrameEncoded(frameIndex, isKeyFrame)
            }

            override fun onEncoderStopped() {
    Log.i(
        TAG,
        "HardwareVideoEncoder stopped; pipeline shutdown will complete after shared output ownership is finalized."
    )
}
        })

        // 3. Setup Hardware AAC Audio Encoder & Mixer
        val audioEnc = HardwareAudioEncoder(sampleRate = 44100, channelCount = 2, bitrateBps = 128000)
        audioEnc.setRtmpSink(rtmpSink)
        if (sharedMuxer != null) {
            audioEnc.setMuxerSink(sharedMuxer)
        }
        audioEnc.start()
        this.audioEncoder = audioEnc

        // 4. Connect Audio Mixer Engine
        val mixer = audioMixer ?: AudioMixerEngine(sampleRate = 44100, channelCount = 2)
        mixer.setFrameConsumer(audioEnc)
        mixer.start(mediaProjection)
        this.audioMixer = mixer

        // 5. Start Video Encoder Surface
        val encoderRawSurface = videoEncoder.start(outputFile = null)
        this.hardwareEncoder = videoEncoder
        this.encoderSurface = encoderRawSurface

        // 6. Connect GPU OpenGL Visual Composition Engine
        val targetCaptureSurface = if (context != null) {
            val compositor = GlesCompositionCompositor(
                context = context,
                outputWidth = outputDimensions.width,
                outputHeight = outputDimensions.height
            )
            val captureSurface = compositor.start(encoderRawSurface)
            compositor.updateCompositionConfig(activeCompositionConfig)
            this.glesCompositor = compositor
            Log.i(TAG, "GPU Compositor connected between Screen Capture and Video Encoder.")
            captureSurface
        } else {
            encoderRawSurface
        }

        listener?.onPipelineStarted(
            width = outputDimensions.width,
            height = outputDimensions.height,
            codecName = videoEncoder.codecName,
            isHardware = videoEncoder.isHardwareAccelerated
        )

        return targetCaptureSurface
    }

    /**
     * Updates composition elements in real-time on the GPU.
     */
    fun updateCompositionConfig(config: CompositionConfig) {
        this.activeCompositionConfig = config
        glesCompositor?.updateCompositionConfig(config)
    }

    /**
     * Updates GPU color enhancements (Brightness, Contrast, Saturation) in real-time.
     */
    fun updateVideoAdjustmentConfig(config: VideoAdjustmentConfig) {
        this.activeCompositionConfig = this.activeCompositionConfig.copy(videoAdjustmentConfig = config)
        glesCompositor?.updateVideoAdjustmentConfig(config)
    }

    /**
     * Validates that an element is legitimate for output composition.
     * Prevents accidental insertion of control UI.
     */
    fun registerOverlayElement(elementName: String): Boolean {
        if (!CaptureOutputContract.isAllowedInOutputPipeline(elementName)) {
            Log.e(TAG, "VIOLATION: Attempted to add CONTROL-ONLY component '$elementName' to Output Layer! Rejected.")
            return false
        }
        Log.i(TAG, "Authorized element added to Output Layer: $elementName")
        return true
    }

    fun pausePipeline() {
        glesCompositor?.pause()
        hardwareEncoder?.pause()
    }

    fun resumePipeline() {
        glesCompositor?.resume()
        hardwareEncoder?.resume()
    }

    fun isPaused(): Boolean = hardwareEncoder?.isPaused() == true

        /**
     * Gracefully shuts down the complete output pipeline.
     *
     * Ownership:
     *
     * AudioMixerEngine
     *      ↓
     * HardwareAudioEncoder
     *
     * GlesCompositionCompositor
     *      ↓
     * HardwareVideoEncoder
     *
     * Both encoders finish their final output first.
     * The shared MediaMuxerSink is closed LAST.
     *
     * This prevents the MP4 muxer from being stopped while
     * audio/video encoders are still trying to write samples.
     */
    @Synchronized
    fun stopPipeline(): Long {
        Log.i(
            TAG,
            "Stopping OutputCompositionPipeline gracefully..."
        )

        val videoEncoder =
            hardwareEncoder

        val audioEncoderInstance =
            audioEncoder

        val mixer =
            audioMixer

        val compositor =
            glesCompositor

        val liveSink =
            rtmpSink

        val sharedMuxer =
            muxerSink

             /*
         * IMPORTANT:
         *
         * Do NOT capture totalFrames here.
         *
         * HardwareVideoEncoder may still produce final encoded
         * frames while it is draining after EOS.
         *
         * The final frame count must therefore be read only AFTER
         * videoEncoder.stop() has completed.
         */
        /*
         * -------------------------------------------------------------
         * STEP 1 — Stop new audio frames at the source.
         * -------------------------------------------------------------
         *
         * The mixer must stop delivering PCM to HardwareAudioEncoder
         * before the audio encoder starts its final EOS drain.
         */
        try {
            mixer?.setFrameConsumer(null)
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to detach audio mixer consumer: ${e.message}"
            )
        }

        /*
         * Stop the mixer itself.
         *
         * This also stops internal audio, microphone and music sources.
         */
        try {
            mixer?.stop()
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to stop AudioMixerEngine: ${e.message}"
            )
        }

        /*
         * -------------------------------------------------------------
         * STEP 2 — Stop GPU compositor.
         * -------------------------------------------------------------
         *
         * The compositor is the producer of video frames for the
         * HardwareVideoEncoder.
         *
         * It must stop producing new frames before either encoder
         * enters its final shutdown/drain phase.
         *
         * This prevents the video producer from continuing to feed
         * the encoder while the output pipeline is being finalized.
         */
        try {
            compositor?.stop()
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to stop GlesCompositionCompositor: ${e.message}"
            )
        }

        /*
         * -------------------------------------------------------------
         * STEP 3 — Finalize AAC audio.
         * -------------------------------------------------------------
         *
         * At this point the AudioMixerEngine has already been stopped,
         * so HardwareAudioEncoder receives no new PCM frames.
         *
         * HardwareAudioEncoder can now safely perform:
         *
         * queued PCM → AAC input EOS → final AAC drain → codec release
         *
         * The shared muxer is NOT closed here.
         */
        try {
            audioEncoderInstance?.stop()
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to finalize HardwareAudioEncoder: ${e.message}"
            )
        }

        /*
         * -------------------------------------------------------------
         * STEP 4 — Finalize H.264 video.
         * -------------------------------------------------------------
         *
         * The compositor has already stopped producing new video
         * frames.
         *
         * HardwareVideoEncoder now performs:
         *
         * EOS → final encoded buffers → END_OF_STREAM
         * → codec stop/release
         *
         * IMPORTANT:
         * This relies on the D-3 graceful EOS implementation.
         */
        try {
            videoEncoder?.stop()
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to finalize HardwareVideoEncoder: ${e.message}"
            )
        }

/*
         * IMPORTANT:
         *
         * Capture the final encoded video frame count only AFTER
         * HardwareVideoEncoder.stop() has completed.
         *
         * The final EOS drain can produce encoded buffers that were
         * not yet counted when shutdown began.
         *
         * HardwareVideoEncoder.stop() performs:
         *
         *     EOS
         *       ↓
         *     final encoded buffers
         *       ↓
         *     output END_OF_STREAM
         *       ↓
         *     MediaCodec release
         *
         * Only after that process has completed is encodedFrames
         * guaranteed to represent the final video output count.
         */
        val totalFrames =
            videoEncoder?.encodedFrames?.get() ?: 0L
        /*
         * -------------------------------------------------------------
         * STEP 5 — Stop future YouTube/RTMP output.
         * -------------------------------------------------------------
         *
         * At this point both local audio/video encoders have finished.
         */
        try {
            liveSink?.stop()
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to stop RTMP sink: ${e.message}"
            )
        }

        /*
         * -------------------------------------------------------------
         * STEP 6 — Close the shared MP4 muxer LAST.
         * -------------------------------------------------------------
         *
         * This is the single final owner of MediaMuxer shutdown.
         *
         * Both audio and video encoders must already have completed
         * their final output before this call.
         */
        try {
            sharedMuxer?.stopAndRelease()
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to finalize MediaMuxer: ${e.message}"
            )
        }

        /*
        *  -------------------------------------------------------------
         * STEP 7 — Clear references only after shutdown is complete.
         * -------------------------------------------------------------
         */
        val finalOutputFilePath =
    activeOutputFilePath

/*
 * Clear references only after every output owner has finished.
 */
glesCompositor = null
audioEncoder = null
audioMixer = null
hardwareEncoder = null
encoderSurface = null
rtmpSink = null
muxerSink = null
activeOutputFilePath = null

Log.i(
    TAG,
    "OutputCompositionPipeline stopped successfully. " +
        "Final video frames=$totalFrames"
)

/*
 * This is the real pipeline completion point:
 *
 * 1. Audio mixer detached/stopped.
 * 2. Audio encoder finalized.
 * 3. Compositor stopped.
 * 4. Video encoder finalized.
 * 5. RTMP sink stopped.
 * 6. Shared muxer closed LAST.
 */
listener?.onPipelineStopped(
    finalOutputFilePath,
    totalFrames
)

return totalFrames
    }
    fun isRunning(): Boolean = hardwareEncoder?.isEncoding() == true

    fun getHardwareCodecName(): String = hardwareEncoder?.codecName ?: "MediaCodec H.264"

    fun isHardwareAccelerated(): Boolean = hardwareEncoder?.isHardwareAccelerated ?: true

    fun getEncodedFrames(): Long = hardwareEncoder?.encodedFrames?.get() ?: 0L
}

