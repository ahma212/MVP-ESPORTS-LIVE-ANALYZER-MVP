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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * HardwareVideoEncoder configures and manages the Android MediaCodec
 * video encoder using a zero-copy Surface input.
 *
 * Shutdown ownership:
 *
 * OutputCompositionPipeline
 *          ↓
 * HardwareVideoEncoder
 *          ↓
 * MediaMuxerSink
 *
 * HardwareVideoEncoder is responsible for:
 * - signalling video EOS
 * - draining final encoded video buffers
 * - stopping/releasing MediaCodec
 * - releasing the encoder input Surface
 *
 * HardwareVideoEncoder does NOT own final MediaMuxer shutdown.
 * OutputCompositionPipeline closes the shared muxer LAST.
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

    /**
     * True after shutdown has been requested.
     *
     * This is intentionally separate from isRunning because the
     * drain loop must remain alive after normal encoding stops so
     * that final encoded buffers and EOS can be consumed.
     */
    private val isShutdownRequested = AtomicBoolean(false)

    /**
     * Prevents signalling video EOS more than once.
     */
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
        fun onFrameEncoded(
            frameIndex: Long,
            isKeyFrame: Boolean,
            sizeBytes: Int
        )
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
     * but obviously excessive bitrate for lower resolutions is capped.
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
     * Prepares and starts the MediaCodec encoder.
     *
     * Returns the encoder input Surface that the compositor/
     * capture pipeline renders into.
     */
    fun start(outputFile: File? = null): Surface {
        /*
         * A new encoder instance/session starts from a clean state.
         */
        isRunning.set(false)
        isShutdownRequested.set(false)
        isEndOfStreamSignaled.set(false)
        isPaused.set(false)

        pauseStartTimeUs = 0L
        totalPausedDurationUs = 0L

        val mimeType = codec.mimeType

        /*
         * Select an encoder that explicitly supports the requested
         * resolution + FPS combination.
         */
        val selectedCodecInfo = selectCodec(mimeType)

        if (selectedCodecInfo == null) {
            val errorMessage =
                "No encoder supports " +
                    "${width}x${height} @ ${fps.fpsValue}fps for $mimeType"

            Log.e(TAG, errorMessage)
            callback?.onError(errorMessage)

            throw IllegalStateException(errorMessage)
        }

        codecName = selectedCodecInfo.name
        isHardwareAccelerated =
            checkIsHardwareAccelerated(selectedCodecInfo)

        val effectiveBitrateMbps = getEffectiveBitrateMbps()

        Log.i(
            TAG,
            "Selected Codec: $codecName " +
                "(Hardware: $isHardwareAccelerated) " +
                "for ${width}x${height} @ ${fps.fpsValue}fps, " +
                "${effectiveBitrateMbps}Mbps effective bitrate"
        )

        val format =
            MediaFormat.createVideoFormat(
                mimeType,
                width,
                height
            ).apply {

                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities
                        .COLOR_FormatSurface
                )

                setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    effectiveBitrateMbps * 1_000_000
                )

                setInteger(
                    MediaFormat.KEY_FRAME_RATE,
                    fps.fpsValue
                )

                setInteger(
                    MediaFormat.KEY_I_FRAME_INTERVAL,
                    keyframeIntervalSeconds
                )

                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities
                        .BITRATE_MODE_CBR
                )

                if (codec == VideoCodec.H264) {
                    applySupportedH264ProfileAndLevel(
                        format = this,
                        codecInfo = selectedCodecInfo
                    )
                }
            }

        val encoder =
            MediaCodec.createByCodecName(
                selectedCodecInfo.name
            )

        try {
        encoder.configure(
                format,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )

            val surface =
                encoder.createInputSurface()

            encoder.start()

            mediaCodec = encoder
            inputSurface = surface

            isRunning.set(true)
            isShutdownRequested.set(false)
            isEndOfStreamSignaled.set(false)
            isPaused.set(false)

            pauseStartTimeUs = 0L
            totalPausedDurationUs = 0L

            /*
             * OutputCompositionPipeline owns the shared muxer.
             *
             * This optional outputFile path is retained only for
             * compatibility with the existing API. The current
             * OutputCompositionPipeline passes outputFile = null
             * and injects the shared muxer through setMuxerSink().
             */
            if (outputFile != null) {
                muxerSink = MediaMuxerSink(outputFile)
            }

            startDrainingLoop()

            return surface

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to initialize MediaCodec: ${e.message}",
                e
            )

            try {
                surfaceCleanupAfterStartFailure()
            } catch (_: Exception) {
            }

            try {
                encoder.release()
            } catch (_: Exception) {
            }

            throw e
        }
    }

    /**
     * Cleans partial encoder state if start() fails.
     */
    private fun surfaceCleanupAfterStartFailure() {
        try {
            inputSurface?.release()
        } catch (_: Exception) {
        } finally {
            inputSurface = null
        }

        mediaCodec = null
        isRunning.set(false)
        isShutdownRequested.set(false)
        isEndOfStreamSignaled.set(false)
    }

    /**
     * Applies the highest sensible H.264 profile/level that the
     * selected codec explicitly reports as supported.
     *
     * We do not blindly force a profile or level that the device
     * may not support.
     */
    private fun applySupportedH264ProfileAndLevel(
        format: MediaFormat,
        codecInfo: MediaCodecInfo?
    ) {
        if (codecInfo == null) {
            Log.w(
                TAG,
                "No codec information available; " +
                    "leaving H.264 profile/level at codec defaults."
            )
            return
        }

        try {
            val capabilities =
                codecInfo.getCapabilitiesForType(
                    VideoCodec.H264.mimeType
                )

            val profileLevels =
                capabilities.profileLevels

            if (profileLevels.isNullOrEmpty()) {
                Log.w(
                    TAG,
                    "No H.264 profile/level information reported " +
                        "by ${codecInfo.name}"
                )
                return
            }

            val preferredProfiles = listOf(
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                MediaCodecInfo.CodecProfileLevel.AVCProfileMain,
                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
            )

            val selectedProfile =
                preferredProfiles.firstOrNull { profile ->
                    profileLevels.any {
                        it.profile == profile
                    }
                }

            if (selectedProfile == null) {
                Log.w(
                    TAG,
                    "No preferred H.264 profile supported by " +
                        "${codecInfo.name}; using codec defaults."
                )
                return
            }

            val supportedLevelsForProfile =
                profileLevels
                    .filter {
                        it.profile == selectedProfile
                    }
                    .map {
                        it.level
                    }

            val preferredLevels = listOf(
                MediaCodecInfo.CodecProfileLevel.AVCLevel51,
                MediaCodecInfo.CodecProfileLevel.AVCLevel42,
                MediaCodecInfo.CodecProfileLevel.AVCLevel41,
                MediaCodecInfo.CodecProfileLevel.AVCLevel4,
                MediaCodecInfo.CodecProfileLevel.AVCLevel31
            )

            val selectedLevel =
                preferredLevels.firstOrNull {
                    it in supportedLevelsForProfile
                }

            format.setInteger(
                MediaFormat.KEY_PROFILE,
                selectedProfile
            )

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
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
                    "profile=$selectedProfile, " +
                    "level=${selectedLevel ?: "codec-default"}"
            )

        } catch (e: Exception) {
            Log.w(
                TAG,
                "Unable to determine supported H.264 " +
                    "profile/level: ${e.message}"
            )
        }
    }

    /**
     * Continuously drains encoded H.264 output.
     *
     * Normal recording:
     *     isRunning == true
     *
     * Graceful shutdown:
     *     isRunning == false
     *     isShutdownRequested == true
     *
     * During graceful shutdown the loop MUST stay alive until the
     * codec returns its final BUFFER_FLAG_END_OF_STREAM output.
     */
    private fun startDrainingLoop() {
        drainJob =
            CoroutineScope(Dispatchers.Default).launch {

                val bufferInfo =
                    MediaCodec.BufferInfo()

                var reachedEndOfStream = false

                try {
                    while (
                        isActive &&
                            (
                                isRunning.get() ||
                                    isShutdownRequested.get()
                                ) &&
                            !reachedEndOfStream
                    ) {
                        val encoder =
                            mediaCodec
                                ?: break

                        val outputBufferIndex =
                            try {
                                encoder.dequeueOutputBuffer(
                                    bufferInfo,
                                    TIMEOUT_US
                                )
                            } catch (e: Exception) {
                                Log.e(
                                    TAG,
                                    "Error dequeuing encoder buffer: " +
                                        e.message,
                                    e
                                )
                                break
                            }

                        when (outputBufferIndex) {

                            MediaCodec.INFO_TRY_AGAIN_LATER -> {
                                /*
                                 * No encoded output is ready yet.
                                 *
                                 * During shutdown we deliberately
                                 * keep polling for final EOS output.
                                 */
                            }

                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val newFormat =
                                    encoder.outputFormat

                                Log.i(
                                    TAG,
                                    "Encoder output format changed: " +
                                        newFormat
                                )

                                /*
                                 * IMPORTANT:
                                 * The encoder only reports the track.
                                 * It does NOT start or stop the muxer.
                                 */
                                muxerSink?.addVideoTrack(
                                    newFormat
                                )

                                rtmpSink?.onVideoFormatChanged(
                                    newFormat
                                )

                                callback?.onFormatChanged(
                                    newFormat
                                )
                            }

                            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                                /*
                                 * Deprecated since API 21.
                                 * No action required.
                                 */
                            }

                            else -> {
                                if (outputBufferIndex >= 0) {

                                    val outputFlags =
                                        bufferInfo.flags

                                    val encodedBuffer =
                                        encoder.getOutputBuffer(
                                            outputBufferIndex
                                        )

                                    if (encodedBuffer != null) {

                                        if (isPaused.get()) {

                                            /*
                                             * Paused video frames are
                                             * intentionally excluded
                                             * from the final output.
                                             */
                                            encoder.releaseOutputBuffer(
                                                outputBufferIndex,
                                                false
                                            )

                                        } else {

                                            val isKeyFrame =
                                                (
                                                    outputFlags and
                                                        MediaCodec
                                                            .BUFFER_FLAG_KEY_FRAME
                                                    ) != 0

                                            val adjustedPts =
                                                (
                                                    bufferInfo
                                                        .presentationTimeUs -
                                                        totalPausedDurationUs
                                                    ).coerceAtLeast(0L)

                                            bufferInfo
                                                .presentationTimeUs =
                                                adjustedPts

                                            /*
                                             * IMPORTANT:
                                             * MediaMuxerSink itself verifies
                                             * that its tracks are ready and
                                             * that the muxer is started.
                                             */
                                            muxerSink?.writeSampleData(
                                                encodedBuffer,
                                                bufferInfo
                                            )

                                            /*
                                             * Forward the same encoded video
                                             * sample to RTMP when configured.
                                             */
                                            rtmpSink?.onVideoSample(
                                                encodedBuffer,
                                                bufferInfo
                                            )

                                            val frameIndex =
                                                encodedFrames
                                                    .incrementAndGet()

                                            callback?.onFrameEncoded(
                                                frameIndex,
                                                isKeyFrame,
                                                bufferInfo.size
                                            )

                                            encoder.releaseOutputBuffer(
                                                outputBufferIndex,
                                                false
                                            )
                                        }

                                    } else {
                                        encoder.releaseOutputBuffer(
                                            outputBufferIndex,
                                            false
                                        )
                                    }

                                    /*
                                     * EOS is detected from the OUTPUT
                                     * buffer, not merely from the request
                                     * to stop.
                                     */
                                    if (
                                        (
                                            outputFlags and
                                                MediaCodec
                                                    .BUFFER_FLAG_END_OF_STREAM
                                            ) != 0
                                    ) {
                                        reachedEndOfStream = true

                                        Log.i(
                                            TAG,
                                            "Video encoder reached " +
                                                "END_OF_STREAM."
                                        )
                                    }
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Video encoder drain loop failed: " +
                            e.message,
                        e
                    )

                } finally {
                    Log.i(
                        TAG,
                        "Video encoder drain loop finished."
                    )
                }
            }
    }

    /**
     * Pauses video frame encoding.
     */
    fun pause() {
        if (
            !isRunning.get() ||
            isPaused.get()
        ) {
            return
        }

        pauseStartTimeUs =
            System.nanoTime() / 1000L

        isPaused.set(true)

        Log.i(
            TAG,
            "Hardware encoder paused at " +
                "$pauseStartTimeUs us"
        )

        try {
            val params =
                android.os.Bundle().apply {
                    putInt(
                        MediaCodec.PARAMETER_KEY_SUSPEND,
                        1
                    )
                }

            mediaCodec?.setParameters(params)

        } catch (e: Exception) {
            Log.w(
                TAG,
                "Codec parameter suspend not supported: " +
                    e.message
            )
        }
    }

    /**
     * Resumes video frame encoding with PTS timestamp compensation.
     */
    fun resume() {
        if (
            !isRunning.get() ||
            !isPaused.get()
        ) {
            return
        }

        val resumeTimeUs =
            System.nanoTime() / 1000L

        if (pauseStartTimeUs > 0) {
            val pauseDuration =
                resumeTimeUs - pauseStartTimeUs

            totalPausedDurationUs +=
                pauseDuration

            Log.i(
                TAG,
                "Hardware encoder resumed. " +
                    "Pause delta: $pauseDuration us, " +
                    "total paused: " +
                    "$totalPausedDurationUs us"
            )

            pauseStartTimeUs = 0L
        }

        isPaused.set(false)

        try {
            val params =
                android.os.Bundle().apply {
                    putInt(
                        MediaCodec.PARAMETER_KEY_SUSPEND,
                        0
                    )
                }
mediaCodec?.setParameters(params)

        } catch (e: Exception) {
            Log.w(
                TAG,
                "Codec parameter resume not supported: " +
                    e.message
            )
        }
    }

    fun isPaused(): Boolean =
        isPaused.get()

    /**
     * Gracefully stops the video encoder.
     *
     * Shutdown order:
     *
     * 1. Mark shutdown requested.
     * 2. Signal MediaCodec input EOS.
     * 3. Keep drainJob alive.
     * 4. Wait for final encoded output + EOS.
     * 5. Stop/release MediaCodec.
     * 6. Release encoder input Surface.
     *
     * IMPORTANT:
     *
     * This method does NOT call:
     *
     *     muxerSink?.stopAndRelease()
     *
     * because the shared MediaMuxer belongs to
     * OutputCompositionPipeline and must be closed LAST.
     */
    fun stop() {

        if (
            !isRunning.get() &&
            !isShutdownRequested.get()
        ) {
            return
        }

        Log.i(
            TAG,
            "Stopping HardwareVideoEncoder gracefully..."
        )

        /*
         * Keep the drain loop alive after normal running state
         * becomes false.
         */
        isShutdownRequested.set(true)

        /*
         * The compositor should already be stopped by
         * OutputCompositionPipeline, but signal EOS here as
         * the authoritative final video input operation.
         */
        try {
            if (
                !isEndOfStreamSignaled
                    .getAndSet(true)
            ) {
                mediaCodec?.signalEndOfInputStream()

                Log.i(
                    TAG,
                    "Video encoder EOS signaled."
                )
            }

        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to signal video EOS: " +
                    e.message
            )
        }

        /*
         * Do NOT cancel drainJob here.
         *
         * The codec may still have final encoded output waiting
         * after signalEndOfInputStream().
         */
        val job = drainJob

        if (job != null) {
            try {
                runBlocking {

                    val completed =
                        withTimeoutOrNull(
                            5_000L
                        ) {
                            job.join()
                            true
                        } ?: false

                    if (!completed) {
                        Log.w(
                            TAG,
                            "Video drain loop did not finish " +
                                "within 5 seconds. " +
                                "Cancelling drain job for safety."
                        )

                        job.cancel()
                        job.join()
                    }
                }

            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Waiting for video drain loop failed: " +
                        e.message
                )

                try {
                    job.cancel()
                } catch (_: Exception) {
                }
            }
        }

        /*
         * The drain loop has completed or the emergency timeout
         * has forced it to stop.
         *
         * Only now is MediaCodec released.
         */
        try {
            mediaCodec?.stop()

        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to stop MediaCodec: " +
                    e.message
            )
        }

        try {
            mediaCodec?.release()

        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to release MediaCodec: " +
                    e.message
            )

        } finally {
            mediaCodec = null
        }

        /*
         * Release the encoder input surface only AFTER codec
         * shutdown has completed.
         */
        try {
            inputSurface?.release()

        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to release encoder input Surface: " +
                    e.message
            )

        } finally {
            inputSurface = null
        }

        drainJob = null

        isRunning.set(false)
        isShutdownRequested.set(false)
        isEndOfStreamSignaled.set(false)
        isPaused.set(false)

        pauseStartTimeUs = 0L
        totalPausedDurationUs = 0L

        /*
         * IMPORTANT:
         *
         * No muxer shutdown here.
         * No muxer release here.
         *
         * OutputCompositionPipeline owns the final shared muxer
         * shutdown and closes it after both audio and video
         * encoders have completed.
         */
        Log.i(
            TAG,
            "HardwareVideoEncoder fully stopped and finalized."
        )

        callback?.onEncoderStopped()
    }

    /**
     * Selects a codec only when it can actually support the
     * requested resolution + frame-rate combination.
     *
     * Hardware encoders are preferred.
     * A compatible software encoder is used only when needed.
     */
    private fun selectCodec(
        mimeType: String
    ): MediaCodecInfo? {

        val codecList =
            MediaCodecList(
                MediaCodecList.REGULAR_CODECS
            )

        var compatibleSoftwareEncoder:
            MediaCodecInfo? = null

        for (info in codecList.codecInfos) {

            if (!info.isEncoder) {
                continue
            }

            val supportsMimeType =
                info.supportedTypes.any {
                    it.equals(
                        mimeType,
                        ignoreCase = true
                    )
                }

            if (!supportsMimeType) {
                continue
            }

            if (
                !supportsVideoConfiguration(
                    info,
                    mimeType
                )
            ) {
                Log.d(
                    TAG,
                    "Skipping ${info.name}: unsupported " +
                        "${width}x${height} @ " +
                        "${fps.fpsValue}fps"
                )

                continue
            }

            if (
                checkIsHardwareAccelerated(info)
            ) {
                Log.i(
                    TAG,
                    "Compatible hardware encoder found: " +
                        "${info.name} for " +
                        "${width}x${height} @ " +
                        "${fps.fpsValue}fps"
                )

                return info
            }

            if (
                compatibleSoftwareEncoder == null
            ) {
                compatibleSoftwareEncoder = info
            }
        }

        if (
            compatibleSoftwareEncoder != null
        ) {
            Log.w(
                TAG,
                "No compatible hardware encoder found. " +
                    "Using compatible software encoder: " +
                    "${compatibleSoftwareEncoder.name}"
            )
        }

        return compatibleSoftwareEncoder
    }

    /**
     * Verifies that the codec really supports the requested
     * output size and frame rate.
     */
    private fun supportsVideoConfiguration(
        info: MediaCodecInfo,
        mimeType: String
    ): Boolean {

        return try {

            val capabilities =
                info.getCapabilitiesForType(
                    mimeType
                )

            val videoCapabilities =
                capabilities.videoCapabilities
                    ?: return false

            videoCapabilities.areSizeAndRateSupported(
                width,
                height,
                fps.fpsValue.toDouble()
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Could not query capabilities for " +
                    "${info.name}: ${e.message}"
            )

            false
        }
    }

    /**
     * Determines whether the selected encoder is hardware accelerated.
     */
    private fun checkIsHardwareAccelerated(
        info: MediaCodecInfo?
    ): Boolean {

        if (info == null) {
            return false
        }

        return if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
        ) {
            info.isHardwareAccelerated

        } else {
            val name =
                info.name.lowercase()

            !name.startsWith("omx.google.") &&
                !name.startsWith("c2.android.")
        }
    }

    fun isEncoding(): Boolean =
        isRunning.get()
}