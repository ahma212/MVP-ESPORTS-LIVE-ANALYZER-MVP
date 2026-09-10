package com.example.engine.output

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import com.example.engine.output.rtmp.RtmpStreamSink
import com.example.model.VideoCodec
import com.example.model.VideoFps
import com.example.model.VideoResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * HardwareVideoEncoder configures and manages the Android hardware-accelerated MediaCodec
 * encoder using zero-copy Surface input.
 */
class HardwareVideoEncoder(
    private val width: Int,
    private val height: Int,
    private val fps: VideoFps = VideoFps.FPS_60,
    private val bitrateMbps: Int = 12,
    private val codec: VideoCodec = VideoCodec.H264,
    private val keyframeIntervalSeconds: Int = 2
) {
    private val TAG = "HardwareVideoEncoder"
    private val TIMEOUT_US = 10_000L

    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var muxerSink: MediaMuxerSink? = null
    private var rtmpSink: RtmpStreamSink? = null

    private var drainJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val isEndOfStreamSignaled = AtomicBoolean(false)

    @Volatile
    private var pauseStartTimeUs: Long = 0L
    @Volatile
    private var totalPausedDurationUs: Long = 0L

    val encodedFrames = AtomicLong(0)
    var isHardwareAccelerated: Boolean = false
        private set
    var codecName: String = "Unknown"
        private set

    interface EncoderCallback {
        fun onFormatChanged(format: MediaFormat)
        fun onFrameEncoded(frameIndex: Long, isKeyFrame: Boolean, sizeBytes: Int)
        fun onError(message: String)
        fun onEncoderStopped()
    }

    private var callback: EncoderCallback? = null

    fun setCallback(callback: EncoderCallback) {
        this.callback = callback
    }

    fun setRtmpSink(sink: RtmpStreamSink?) {
        this.rtmpSink = sink
    }

    fun setMuxerSink(sink: MediaMuxerSink?) {
        this.muxerSink = sink
    }

    fun getMuxerSink(): MediaMuxerSink? = muxerSink

    /**
     * Returns a sensible bitrate for the selected resolution + FPS.
     *
     * The user's selected bitrate is treated as the preferred target,
     * but we prevent obviously excessive bitrate for lower resolutions.
     *
     * This keeps gameplay detail good without creating unnecessary
     * encoder and network load.
     */
    private fun getEffectiveBitrateMbps(): Int {
        val requestedMbps = bitrateMbps.coerceAtLeast(1)

        val resolutionLimitMbps = when {
            width <= 640 && height <= 360 -> 4
            width <= 854 && height <= 480 -> 6
            width <= 1280 && height <= 720 -> 10
            width <= 1920 && height <= 1080 -> 16
            width <= 2560 && height <= 1440 -> 24
            else -> 35
        }

        val fpsMultiplier = when {
            fps.fpsValue <= 30 -> 1.0f
            fps.fpsValue <= 60 -> 1.15f
            fps.fpsValue <= 90 -> 1.30f
            else -> 1.45f
        }

        val calculatedLimit =
            (resolutionLimitMbps * fpsMultiplier).toInt()

        return requestedMbps.coerceAtMost(calculatedLimit)
    }

    /**
     * Prepares and starts the MediaCodec hardware encoder.
     * Returns the input Surface that MediaProjection VirtualDisplay (or EGL) renders into.
     */
    fun start(outputFile: File? = null): Surface {
        val mimeType = codec.mimeType

        // Select an encoder that truly supports the requested
        // resolution + FPS combination.
        val selectedCodecInfo = selectCodec(mimeType)

        if (selectedCodecInfo == null) {
            val errorMessage =
                "No encoder supports ${width}x${height} @ ${fps.fpsValue}fps for $mimeType"

            Log.e(TAG, errorMessage)
            callback?.onError(errorMessage)
            throw IllegalStateException(errorMessage)
        }

        codecName = selectedCodecInfo.name
        isHardwareAccelerated = checkIsHardwareAccelerated(selectedCodecInfo)
        val effectiveBitrateMbps = getEffectiveBitrateMbps()

        Log.i(
            TAG,
            "Selected Codec: $codecName " +
                "(Hardware: $isHardwareAccelerated) " +
                "for ${width}x${height} @ ${fps.fpsValue}fps, " +
                "${effectiveBitrateMbps}Mbps effective bitrate"
        )

        val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(
                MediaFormat.KEY_BIT_RATE,
                effectiveBitrateMbps * 1_000_000
            )
            setInteger(MediaFormat.KEY_FRAME_RATE, fps.fpsValue)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(
                MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
            )

            if (codec == VideoCodec.H264) {
                applySupportedH264ProfileAndLevel(
                    format = this,
                    codecInfo = selectedCodecInfo
                )
            }
        }

        val encoder = MediaCodec.createByCodecName(selectedCodecInfo.name)

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()
            encoder.start()

            mediaCodec = encoder
            inputSurface = surface
            isRunning.set(true)
            isPaused.set(false)
            isEndOfStreamSignaled.set(false)
            pauseStartTimeUs = 0L
            totalPausedDurationUs = 0L

            // Setup MediaMuxer sink for MP4 recording if outputFile is provided
            if (outputFile != null) {
                muxerSink = MediaMuxerSink(outputFile)
            }

            // Launch drainage loop
            startDrainingLoop()

            return surface
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaCodec: ${e.message}", e)
            encoder.release()
            throw e
        }
    }

    /**
     * Applies the highest sensible H.264 profile/level that the selected
     * encoder explicitly reports as supported.
     *
     * We do not blindly force High Profile / Level 4.2 because some
     * hardware encoders expose different supported combinations.
     */
    private fun applySupportedH264ProfileAndLevel(
        format: MediaFormat,
        codecInfo: MediaCodecInfo?
    ) {
        if (codecInfo == null) {
            Log.w(
                TAG,
                "No codec information available; leaving H.264 profile/level at codec defaults."
            )
            return
        }

        try {
            val capabilities =
                codecInfo.getCapabilitiesForType(VideoCodec.H264.mimeType)

            val profileLevels = capabilities.profileLevels

            if (profileLevels.isNullOrEmpty()) {
                Log.w(
                    TAG,
                    "No H.264 profile/level information reported by ${codecInfo.name}"
                )
                return
            }

            val preferredProfiles = listOf(
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                MediaCodecInfo.CodecProfileLevel.AVCProfileMain,
                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
            )

            val selectedProfile = preferredProfiles.firstOrNull { profile ->
                profileLevels.any { it.profile == profile }
            }

            if (selectedProfile == null) {
                Log.w(
                    TAG,
                    "No preferred H.264 profile supported by ${codecInfo.name}; using codec defaults."
                )
                return
            }

            val supportedLevelsForProfile = profileLevels
                .filter { it.profile == selectedProfile }
                .map { it.level }

            val preferredLevels = listOf(
                MediaCodecInfo.CodecProfileLevel.AVCLevel51,
                MediaCodecInfo.CodecProfileLevel.AVCLevel42,
                MediaCodecInfo.CodecProfileLevel.AVCLevel41,
                MediaCodecInfo.CodecProfileLevel.AVCLevel4,
                MediaCodecInfo.CodecProfileLevel.AVCLevel31
            )

            val selectedLevel = preferredLevels.firstOrNull {
                it in supportedLevelsForProfile
            }

            format.setInteger(
                MediaFormat.KEY_PROFILE,
                selectedProfile
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                selectedLevel != null
            ) {
                format.setInteger(
                    MediaFormat.KEY_LEVEL,
                    selectedLevel
                )
            }

            Log.i(
                TAG,
                "H.264 profile/level selected: " +
                    "profile=$selectedProfile, level=${selectedLevel ?: "codec-default"}"
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Unable to determine supported H.264 profile/level: ${e.message}"
            )
        }
    }

    private fun startDrainingLoop() {
        drainJob = CoroutineScope(Dispatchers.Default).launch {
            val bufferInfo = MediaCodec.BufferInfo()

            while (isActive && isRunning.get()) {
                val encoder = mediaCodec ?: break
                val outputBufferIndex = try {
                    encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                } catch (e: Exception) {
                    Log.e(TAG, "Error dequeuing buffer: ${e.message}")
                    break
                }

                when (outputBufferIndex) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No buffer ready yet
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = encoder.outputFormat
                        Log.i(TAG, "Encoder output format changed: $newFormat")
                        muxerSink?.addVideoTrack(newFormat)
                        rtmpSink?.onVideoFormatChanged(newFormat)
                        callback?.onFormatChanged(newFormat)
                    }
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                        // Deprecated in API 21, no action needed
                    }
                    else -> {
                        if (outputBufferIndex >= 0) {
                            val encodedBuffer = encoder.getOutputBuffer(outputBufferIndex)
                            if (encodedBuffer != null) {
                                if (isPaused.get()) {
                                    // Drop frames while recording is paused
                                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                                } else {
                                    val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                                    // Compensate timestamps for pauses so video does not freeze
                                    val adjustedPts = (bufferInfo.presentationTimeUs - totalPausedDurationUs).coerceAtLeast(0L)
                                    bufferInfo.presentationTimeUs = adjustedPts

                                    // Write sample to MP4 muxer
                                    muxerSink?.writeSampleData(encodedBuffer, bufferInfo)

                                    // Write sample to YouTube Live RTMP stream
                                    rtmpSink?.onVideoSample(encodedBuffer, bufferInfo)

                                    val frameIdx = encodedFrames.incrementAndGet()
                                    callback?.onFrameEncoded(frameIdx, isKeyFrame, bufferInfo.size)

                                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                                }

                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    Log.i(TAG, "Encountered BUFFER_FLAG_END_OF_STREAM")
                                    break
                                }
                            }
                        }
                    }
                }
            }

            // Cleanup muxer when loop exits
            muxerSink?.stopAndRelease()
            callback?.onEncoderStopped()
        }
    }

    /**
     * Pauses video frame encoding.
     */
    fun pause() {
        if (!isRunning.get() || isPaused.get()) return
        pauseStartTimeUs = System.nanoTime() / 1000L
        isPaused.set(true)
        Log.i(TAG, "Hardware encoder paused at $pauseStartTimeUs us")
        try {
            val params = android.os.Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 1)
            }
            mediaCodec?.setParameters(params)
        } catch (e: Exception) {
            Log.w(TAG, "Codec parameter suspend not supported: ${e.message}")
        }
    }

    /**
     * Resumes video frame encoding with PTS timestamp compensation.
     */
    fun resume() {
        if (!isRunning.get() || !isPaused.get()) return
        val resumeTimeUs = System.nanoTime() / 1000L
        if (pauseStartTimeUs > 0) {
            val delta = resumeTimeUs - pauseStartTimeUs
            totalPausedDurationUs += delta
            Log.i(TAG, "Hardware encoder resumed. Pause delta: $delta us, total paused: $totalPausedDurationUs us")
            pauseStartTimeUs = 0L
        }
        isPaused.set(false)
        try {
            val params = android.os.Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 0)
            }
            mediaCodec?.setParameters(params)
        } catch (e: Exception) {
            Log.w(TAG, "Codec parameter resume not supported: ${e.message}")
        }
    }

    fun isPaused(): Boolean = isPaused.get()

    /**
     * Gracefully signals End Of Stream and releases resources.
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        try {
            if (!isEndOfStreamSignaled.getAndSet(true)) {
                mediaCodec?.signalEndOfInputStream()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to signal end of input stream: ${e.message}")
        }

        drainJob?.cancel()

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping/releasing MediaCodec: ${e.message}")
        } finally {
            mediaCodec = null
        }

        try {
            inputSurface?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing input surface: ${e.message}")
        } finally {
            inputSurface = null
        }

        muxerSink?.stopAndRelease()
        muxerSink = null
        Log.i(TAG, "HardwareVideoEncoder fully stopped and released.")
    }
    /**
     * Selects a codec only when it can actually support the requested
     * resolution + frame-rate combination.
     *
     * Hardware encoders are preferred. If no hardware encoder can satisfy
     * the exact configuration, a compatible software encoder may be returned
     * so the caller can still report the real capability problem clearly.
     */
    private fun selectCodec(mimeType: String): MediaCodecInfo? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

        var compatibleSoftwareEncoder: MediaCodecInfo? = null

        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue

            val supportsMimeType = info.supportedTypes.any {
                it.equals(mimeType, ignoreCase = true)
            }

            if (!supportsMimeType) continue

            if (!supportsVideoConfiguration(info, mimeType)) {
                Log.d(
                    TAG,
                    "Skipping ${info.name}: unsupported ${width}x${height} @ ${fps.fpsValue}fps"
                )
                continue
            }

            if (checkIsHardwareAccelerated(info)) {
                Log.i(
                    TAG,
                    "Compatible hardware encoder found: ${info.name} " +
                        "for ${width}x${height} @ ${fps.fpsValue}fps"
                )
                return info
            }

            if (compatibleSoftwareEncoder == null) {
                compatibleSoftwareEncoder = info
            }
        }

        if (compatibleSoftwareEncoder != null) {
            Log.w(
                TAG,
                "No compatible hardware encoder found. " +
                    "Using compatible software encoder: ${compatibleSoftwareEncoder.name}"
            )
        }

        return compatibleSoftwareEncoder
    }

    /**
     * Verifies that the codec really supports the requested output size
     * and requested frame rate.
     *
     * Android MediaCodec exposes this information through VideoCapabilities.
     */
    private fun supportsVideoConfiguration(
        info: MediaCodecInfo,
        mimeType: String
    ): Boolean {
        return try {
            val capabilities = info.getCapabilitiesForType(mimeType)
            val videoCapabilities = capabilities.videoCapabilities
                ?: return false

            videoCapabilities.areSizeAndRateSupported(
                width,
                height,
                fps.fpsValue.toDouble()
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Could not query capabilities for ${info.name}: ${e.message}"
            )
            false
        }
    }

    private fun checkIsHardwareAccelerated(info: MediaCodecInfo?): Boolean {
        if (info == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            val name = info.name.lowercase()
            !name.startsWith("omx.google.") && !name.startsWith("c2.android.")
        }
    }

    fun isEncoding(): Boolean = isRunning.get()
}
