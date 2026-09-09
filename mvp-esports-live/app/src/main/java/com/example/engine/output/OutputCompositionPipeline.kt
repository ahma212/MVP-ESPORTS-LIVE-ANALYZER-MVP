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

    private fun initializePipelineInternal(
        outputFile: File?,
        rtmpSink: RtmpStreamSink?,
        audioMixer: AudioMixerEngine?,
        mediaProjection: MediaProjection?
    ): Surface {
        Log.i(TAG, "Starting Output Layer Pipeline: ${outputDimensions.width}x${outputDimensions.height} (${recordingConfig.fps.fpsValue} FPS, ${recordingConfig.bitrateMbps} Mbps, Live: ${rtmpSink != null})")

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

            override fun onError(message: String) {
                listener?.onPipelineError(message)
            }

            override fun onEncoderStopped() {
                listener?.onPipelineStopped(outputFile?.absolutePath, videoEncoder.encodedFrames.get())
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

    fun stopPipeline(): Long {
        val totalFrames = hardwareEncoder?.encodedFrames?.get() ?: 0L

        try {
            glesCompositor?.stop()
        } catch (_: Exception) {}
        glesCompositor = null

        try {
            audioEncoder?.stop()
        } catch (_: Exception) {}
        audioEncoder = null

        try {
            audioMixer?.setFrameConsumer(null)
        } catch (_: Exception) {}

        try {
            rtmpSink?.stop()
        } catch (_: Exception) {}
        rtmpSink = null

        hardwareEncoder?.stop()
        hardwareEncoder = null
        encoderSurface = null

        try {
            muxerSink?.stopAndRelease()
        } catch (_: Exception) {}
        muxerSink = null

        return totalFrames
    }

    fun isRunning(): Boolean = hardwareEncoder?.isEncoding() == true

    fun getHardwareCodecName(): String = hardwareEncoder?.codecName ?: "MediaCodec H.264"

    fun isHardwareAccelerated(): Boolean = hardwareEncoder?.isHardwareAccelerated ?: true

    fun getEncodedFrames(): Long = hardwareEncoder?.encodedFrames?.get() ?: 0L
}

