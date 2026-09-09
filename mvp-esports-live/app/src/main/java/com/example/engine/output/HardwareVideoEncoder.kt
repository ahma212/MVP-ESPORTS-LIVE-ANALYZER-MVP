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
     * Prepares and starts the MediaCodec hardware encoder.
     * Returns the input Surface that MediaProjection VirtualDisplay (or EGL) renders into.
     */
    fun start(outputFile: File? = null): Surface {
        val mimeType = codec.mimeType

        // Select the optimal hardware encoder
        val selectedCodecInfo = selectCodec(mimeType)
        codecName = selectedCodecInfo?.name ?: mimeType
        isHardwareAccelerated = checkIsHardwareAccelerated(selectedCodecInfo)
        Log.i(TAG, "Selected Codec: $codecName (Hardware: $isHardwareAccelerated) for ${width}x${height} @ ${fps.fpsValue}fps, ${bitrateMbps}Mbps")

        val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateMbps * 1_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps.fpsValue)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyframeIntervalSeconds) // Keyframe interval (1 or 2s)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)

            // High Profile H.264 if supported
            if (codec == VideoCodec.H264) {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel42)
                }
            }
        }

        val encoder = if (selectedCodecInfo != null) {
            MediaCodec.createByCodecName(selectedCodecInfo.name)
        } else {
            MediaCodec.createEncoderByType(mimeType)
        }

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

    private fun selectCodec(mimeType: String): MediaCodecInfo? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    // Prefer hardware-accelerated codecs
                    if (checkIsHardwareAccelerated(info)) {
                        return info
                    }
                }
            }
        }
        // Fallback to first available encoder if no hardware acceleration flag found
        for (info in codecList.codecInfos) {
            if (info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) {
                return info
            }
        }
        return null
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
