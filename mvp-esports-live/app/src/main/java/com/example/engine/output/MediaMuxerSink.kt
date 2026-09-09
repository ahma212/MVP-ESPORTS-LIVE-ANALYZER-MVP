package com.example.engine.output

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * MediaMuxerSink encapsulates Android native MediaMuxer for recording
 * hardware-encoded video frames (H.264/HEVC) and mixed audio streams (AAC) into an MP4 container.
 */
class MediaMuxerSink(
    private val outputFile: File
) {
    private val TAG = "MediaMuxerSink"

    private var muxer: MediaMuxer? = null
    private var videoTrackIndex: Int = -1
    private var audioTrackIndex: Int = -1
    private var isStarted = false
    private val lock = Any()

    var totalBytesWritten: Long = 0
        private set

    init {
        outputFile.parentFile?.mkdirs()
        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            Log.i(TAG, "MediaMuxer initialized for path: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaMuxer: ${e.message}", e)
        }
    }

    /**
     * Registers the video track when the video encoder outputs format change.
     */
    fun addVideoTrack(format: MediaFormat): Boolean {
        synchronized(lock) {
            if (isStarted) {
                Log.w(TAG, "Cannot add video track after muxer has already started")
                return false
            }
            return try {
                videoTrackIndex = muxer?.addTrack(format) ?: -1
                Log.i(TAG, "Added video track index $videoTrackIndex")
                checkAndStartMuxerLocked()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add video track: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Registers the audio track when the AAC audio encoder outputs format change.
     */
    fun addAudioTrack(format: MediaFormat): Boolean {
        synchronized(lock) {
            if (isStarted) {
                Log.w(TAG, "Cannot add audio track after muxer has already started")
                return false
            }
            return try {
                audioTrackIndex = muxer?.addTrack(format) ?: -1
                Log.i(TAG, "Added audio track index $audioTrackIndex")
                checkAndStartMuxerLocked()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add audio track: ${e.message}", e)
                false
            }
        }
    }

    private fun checkAndStartMuxerLocked() {
        if (!isStarted && videoTrackIndex != -1) {
            try {
                muxer?.start()
                isStarted = true
                Log.i(TAG, "MediaMuxer started (video=$videoTrackIndex, audio=$audioTrackIndex)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MediaMuxer: ${e.message}", e)
            }
        }
    }

    /**
     * Writes an encoded video sample buffer to the MP4 file.
     */
    fun writeVideoSampleData(encodedData: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        writeSampleDataInternal(videoTrackIndex, encodedData, bufferInfo)
    }

    /**
     * Writes an encoded AAC audio sample buffer to the MP4 file.
     */
    fun writeAudioSampleData(encodedData: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        writeSampleDataInternal(audioTrackIndex, encodedData, bufferInfo)
    }

    /**
     * Legacy / Direct video sample writer.
     */
    fun writeSampleData(encodedData: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        writeVideoSampleData(encodedData, bufferInfo)
    }

    private fun writeSampleDataInternal(trackIndex: Int, encodedData: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (!isStarted || trackIndex < 0) {
                return
            }

            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                bufferInfo.size = 0
                return
            }

            if (bufferInfo.size == 0) return

            try {
                encodedData.position(bufferInfo.offset)
                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                muxer?.writeSampleData(trackIndex, encodedData, bufferInfo)
                totalBytesWritten += bufferInfo.size
            } catch (e: Exception) {
                Log.e(TAG, "Error writing sample data to track $trackIndex: ${e.message}")
            }
        }
    }

    fun isMuxerStarted(): Boolean = isStarted

    fun getFilePath(): String = outputFile.absolutePath

    /**
     * Cleanly stops and releases the MediaMuxer.
     */
    fun stopAndRelease() {
        synchronized(lock) {
            if (isStarted) {
                try {
                    muxer?.stop()
                    Log.i(TAG, "MediaMuxer stopped successfully. File size: ${outputFile.length()} bytes")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop MediaMuxer: ${e.message}")
                } finally {
                    isStarted = false
                }
            }
            try {
                muxer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release MediaMuxer: ${e.message}")
            }
            muxer = null
            videoTrackIndex = -1
            audioTrackIndex = -1
        }
    }
}
