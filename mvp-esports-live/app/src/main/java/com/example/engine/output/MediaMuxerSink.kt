package com.example.engine.output

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * MediaMuxerSink owns the MP4 container used by the output pipeline.
 *
 * The muxer is started only after BOTH video and audio tracks have been
 * registered. This prevents the video encoder from starting the muxer before
 * the AAC audio track is ready.
 *
 * The sink is thread-safe because video and audio encoders drain on separate
 * coroutines/threads.
 */
class MediaMuxerSink(
    private val outputFile: File
) {
    private val TAG = "MediaMuxerSink"

    private var muxer: MediaMuxer? = null
    private var videoTrackIndex: Int = -1
    private var audioTrackIndex: Int = -1
    private var isStarted = false
    private var isReleased = false
    private val lock = Any()

    var totalBytesWritten: Long = 0
        private set

    init {
        outputFile.parentFile?.mkdirs()

        try {
            muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            Log.i(
                TAG,
                "MediaMuxer initialized for path: ${outputFile.absolutePath}"
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to initialize MediaMuxer: ${e.message}",
                e
            )
        }
    }

    /**
     * Registers the video track when the video encoder reports its output
     * format.
     */
    fun addVideoTrack(format: MediaFormat): Boolean {
        synchronized(lock) {
            if (isReleased) {
                Log.w(TAG, "Cannot add video track: muxer already released")
                return false
            }

            if (isStarted) {
                Log.w(TAG, "Cannot add video track after muxer has started")
                return false
            }

            if (videoTrackIndex >= 0) {
                Log.w(TAG, "Video track has already been added")
                return false
            }

            return try {
                val currentMuxer = muxer ?: run {
                    Log.e(TAG, "Cannot add video track: muxer is null")
                    return false
                }

                videoTrackIndex = currentMuxer.addTrack(format)

                Log.i(
                    TAG,
                    "Added video track index $videoTrackIndex"
                )

                checkAndStartMuxerLocked()
                true
            } catch (e: Exception) {
                videoTrackIndex = -1
                Log.e(
                    TAG,
                    "Failed to add video track: ${e.message}",
                    e
                )
                false
            }
        }
    }

    /**
     * Registers the AAC audio track when the audio encoder reports its output
     * format.
     */
    fun addAudioTrack(format: MediaFormat): Boolean {
        synchronized(lock) {
            if (isReleased) {
                Log.w(TAG, "Cannot add audio track: muxer already released")
                return false
            }

            if (isStarted) {
                Log.w(TAG, "Cannot add audio track after muxer has started")
                return false
            }

            if (audioTrackIndex >= 0) {
                Log.w(TAG, "Audio track has already been added")
                return false
            }

            return try {
                val currentMuxer = muxer ?: run {
                    Log.e(TAG, "Cannot add audio track: muxer is null")
                    return false
                }

                audioTrackIndex = currentMuxer.addTrack(format)

                Log.i(
                    TAG,
                    "Added audio track index $audioTrackIndex"
                )

                checkAndStartMuxerLocked()
                true
            } catch (e: Exception) {
                audioTrackIndex = -1
                Log.e(
                    TAG,
                    "Failed to add audio track: ${e.message}",
                    e
                )
                false
            }
        }
    }

    /**
     * Starts MediaMuxer only when both elementary streams have registered
     * their output formats.
     *
     * MediaMuxer requires all tracks to be added before start().
     */
    private fun checkAndStartMuxerLocked() {
        if (isReleased || isStarted) {
            return
        }

        val hasVideoTrack = videoTrackIndex >= 0
        val hasAudioTrack = audioTrackIndex >= 0

        if (!hasVideoTrack || !hasAudioTrack) {
            Log.d(
                TAG,
                "Waiting for both tracks before starting muxer. " +
                    "videoTrack=$videoTrackIndex, audioTrack=$audioTrackIndex"
            )
            return
        }

        val currentMuxer = muxer ?: run {
            Log.e(TAG, "Cannot start muxer: muxer is null")
            return
        }

        try {
            currentMuxer.start()
            isStarted = true

            Log.i(
                TAG,
                "MediaMuxer started successfully. " +
                    "video=$videoTrackIndex, audio=$audioTrackIndex"
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to start MediaMuxer: ${e.message}",
                e
            )
        }
    }

    /**
     * Writes an encoded video sample buffer to the MP4 file.
     */
    fun writeVideoSampleData(
        encodedData: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        writeSampleDataInternal(
            trackIndex = videoTrackIndex,
            encodedData = encodedData,
            bufferInfo = bufferInfo
        )
    }

    /**
     * Writes an encoded AAC audio sample buffer to the MP4 file.
     */
    fun writeAudioSampleData(
        encodedData: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        writeSampleDataInternal(
            trackIndex = audioTrackIndex,
            encodedData = encodedData,
            bufferInfo = bufferInfo
        )
    }

    /**
     * Legacy/direct video sample writer kept for compatibility.
     */
    fun writeSampleData(
        encodedData: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        writeVideoSampleData(encodedData, bufferInfo)
    }

    /**
     * Thread-safe sample writer used by both audio and video encoders.
     */
    private fun writeSampleDataInternal(
        trackIndex: Int,
        encodedData: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        synchronized(lock) {
            if (isReleased || !isStarted || trackIndex < 0) {
                return
            }

            if (bufferInfo.size <= 0) {
                return
            }

            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                return
            }

            val currentMuxer = muxer ?: return

            try {
                val buffer = encodedData.duplicate()
                val start = bufferInfo.offset
                val end = bufferInfo.offset + bufferInfo.size

                if (start < 0 || end > buffer.capacity() || start >= end) {
                    Log.w(
                        TAG,
                        "Ignoring invalid sample range: " +
                            "offset=${bufferInfo.offset}, size=${bufferInfo.size}, " +
                            "capacity=${buffer.capacity()}"
                    )
                    return
                }

                buffer.position(start)
                buffer.limit(end)

                currentMuxer.writeSampleData(
                    trackIndex,
                    buffer,
                    bufferInfo
                )

                totalBytesWritten += bufferInfo.size.toLong()
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Error writing sample data to track $trackIndex: ${e.message}",
                    e
                )
            }
        }
    }

    fun isMuxerStarted(): Boolean = synchronized(lock) {
        isStarted
    }

    fun getFilePath(): String = outputFile.absolutePath

    /**
     * Stops and releases MediaMuxer exactly once.
     *
     * If both tracks never became available, MediaMuxer is released without
     * calling stop(), because stop() is only valid after start().
     */
    fun stopAndRelease() {
        synchronized(lock) {
            if (isReleased) {
                return
            }

            try {
                if (isStarted) {
                    try {
                        muxer?.stop()
                        Log.i(
                            TAG,
                            "MediaMuxer stopped successfully. " +
                                "File size: ${outputFile.length()} bytes, " +
                                "total sample bytes: $totalBytesWritten"
                        )
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Failed to stop MediaMuxer: ${e.message}",
                            e
                        )
                    } finally {
                        isStarted = false
                    }
                } else if (videoTrackIndex >= 0 || audioTrackIndex >= 0) {
                    Log.w(
                        TAG,
                        "MediaMuxer was never started because both tracks were not ready. " +
                            "videoTrack=$videoTrackIndex, audioTrack=$audioTrackIndex"
                    )
                }
            } finally {
                try {
                    muxer?.release()
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Failed to release MediaMuxer: ${e.message}",
                        e
                    )
                }

                muxer = null
                videoTrackIndex = -1
                audioTrackIndex = -1
                isReleased = true
            }
        }
    }
}
