package com.example.engine.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * MusicAudioSource decodes and streams user-selected audio tracks (MP3/AAC/WAV/FLAC/OGG)
 * from device gallery or local storage into raw 44.1kHz 16-bit stereo PCM samples.
 */
class MusicAudioSource(
    val sampleRate: Int = 44100,
    val channelCount: Int = 2
) {
    private val TAG = "MusicAudioSource"

    val isEnabled = AtomicBoolean(true)
    val isMuted = AtomicBoolean(false)
    val isLooping = AtomicBoolean(true)
    var volume: Float = 0.40f

    private val playbackState = AtomicReference(MusicPlaybackState.STOPPED)

    @Volatile
    var trackTitle: String? = null
        private set

    @Volatile
    var trackArtist: String? = null
        private set

    @Volatile
    var trackUri: Uri? = null
        private set

    @Volatile
    var durationMs: Long = 0L
        private set

    @Volatile
    var currentPositionMs: Long = 0L
        private set

    @Volatile
    var currentPeakLevel: Float = 0f
        private set

    private var contextRef: Context? = null
    private var decodeJob: Job? = null
    private val isDecoderRunning = AtomicBoolean(false)

    // PCM queue holding 1024-sample chunks (2048 shorts = ~23ms per chunk)
    private val pcmChunkQueue = ArrayBlockingQueue<ShortArray>(40)
    private var currentChunk: ShortArray? = null
    private var currentChunkIndex = 0

    fun getPlaybackState(): MusicPlaybackState = playbackState.get()

    /**
     * Loads a music file selected by user from the Android gallery or storage.
     */
    fun loadTrack(context: Context, uri: Uri): Boolean {
        stop()
        this.contextRef = context.applicationContext
        this.trackUri = uri

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            this.trackTitle = if (!title.isNullOrBlank()) title else uri.lastPathSegment?.substringAfterLast("/") ?: "Music Track"
            this.trackArtist = if (!artist.isNullOrBlank()) artist else "Gallery Audio"
            this.durationMs = durStr?.toLongOrNull() ?: 0L
            this.currentPositionMs = 0L

            Log.i(TAG, "Loaded music track: $trackTitle - $trackArtist (${durationMs}ms)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect audio metadata: ${e.message}", e)
            this.trackTitle = uri.lastPathSegment?.substringAfterLast("/") ?: "Selected Audio"
            this.trackArtist = "Gallery Audio"
            this.durationMs = 180_000L
            return false
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun play() {
        if (trackUri == null) {
            Log.w(TAG, "Cannot play: No music track selected.")
            return
        }

        if (playbackState.get() == MusicPlaybackState.PLAYING) return

        playbackState.set(MusicPlaybackState.PLAYING)
        if (!isDecoderRunning.get()) {
            startDecodingThread()
        }
    }

    fun pause() {
        if (playbackState.get() == MusicPlaybackState.PLAYING) {
            playbackState.set(MusicPlaybackState.PAUSED)
            currentPeakLevel = 0f
        }
    }

    fun stop() {
        playbackState.set(MusicPlaybackState.STOPPED)
        isDecoderRunning.set(false)
        decodeJob?.cancel()
        decodeJob = null
        pcmChunkQueue.clear()
        currentChunk = null
        currentChunkIndex = 0
        currentPositionMs = 0L
        currentPeakLevel = 0f
    }
/**
     * Seek to position in milliseconds. Restarts decoder from that point.
     */
    fun seekTo(positionMs: Long) {
        val uri = trackUri ?: return
        val context = contextRef ?: return
        val target = positionMs.coerceIn(0L, if (durationMs > 0L) durationMs else Long.MAX_VALUE)

        val wasPlaying = playbackState.get() == MusicPlaybackState.PLAYING

        // Stop decoder without wiping track metadata
        isDecoderRunning.set(false)
        decodeJob?.cancel()
        decodeJob = null
        pcmChunkQueue.clear()
        currentChunk = null
        currentChunkIndex = 0
        currentPositionMs = target
        currentPeakLevel = 0f

        if (wasPlaying) {
            playbackState.set(MusicPlaybackState.PLAYING)
            startDecodingThreadFrom(target)
        } else {
            playbackState.set(MusicPlaybackState.PAUSED)
            pendingSeekMs = target
        }
    }

    @Volatile
    private var pendingSeekMs: Long = 0L

    private fun startDecodingThreadFrom(startMs: Long) {
        val uri = trackUri ?: return
        val context = contextRef ?: return

        pendingSeekMs = startMs
        isDecoderRunning.set(true)
        decodeJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isDecoderRunning.get()) {
                if (playbackState.get() == MusicPlaybackState.STOPPED) break

                decodeAudioTrack(context, uri, pendingSeekMs)
                pendingSeekMs = 0L

                if (!isLooping.get() || playbackState.get() == MusicPlaybackState.STOPPED) {
                    playbackState.set(MusicPlaybackState.STOPPED)
                    break
                }
                Log.i(TAG, "Music track ended. Looping to start...")
                currentPositionMs = 0L
            }
            isDecoderRunning.set(false)
        }
    }
    fun togglePlayPause() {
        if (playbackState.get() == MusicPlaybackState.PLAYING) {
            pause()
        } else {
            play()
        }
    }

    private fun startDecodingThread() {
        startDecodingThreadFrom(pendingSeekMs)
    }
    private fun decodeAudioTrack(context: Context, uri: Uri, startMs: Long = 0L) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIdx = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIdx < 0 || audioFormat == null) {
                Log.e(TAG, "No audio track found in selected file: $uri")
                return
            }

            extractor.selectTrack(audioTrackIdx)
            if (startMs > 0L) {
                extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                currentPositionMs = startMs
            }
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_AUDIO_AAC
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()
            codec = decoder

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEos = false
            var isOutputEos = false
            val timeoutUs = 10_000L

            while (isDecoderRunning.get() && !isOutputEos) {
                // Throttle decode queue size so we don't consume excess memory
                while (pcmChunkQueue.size >= 30 && isDecoderRunning.get()) {
                    Thread.sleep(15)
                }

                // Feed input buffers to decoder
                if (!isInputEos) {
                    val inIdx = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isInputEos = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // Drain decoded PCM buffers
                val outIdx = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIdx >= 0) {
                    val outBuf = decoder.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        currentPositionMs = bufferInfo.presentationTimeUs / 1000L
                        convertAndEnqueuePcm(outBuf, bufferInfo)
                    }
                    decoder.releaseOutputBuffer(outIdx, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEos = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio decoding error: ${e.message}", e)
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    private fun convertAndEnqueuePcm(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        byteBuffer.position(bufferInfo.offset)
        byteBuffer.limit(bufferInfo.offset + bufferInfo.size)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val shortBuf = byteBuffer.asShortBuffer()
        val shortArray = ShortArray(shortBuf.remaining())
        shortBuf.get(shortArray)

        // Split into chunks of 1024 shorts for predictable mixing
        val chunkSize = 1024
        var pos = 0
        while (pos < shortArray.size && isDecoderRunning.get()) {
            val len = (shortArray.size - pos).coerceAtMost(chunkSize)
            val chunk = ShortArray(len)
            System.arraycopy(shortArray, pos, chunk, 0, len)
            pcmChunkQueue.offer(chunk)
            pos += len
        }
    }

    /**
     * Reads PCM samples into destination mixer buffer.
     */
    fun read(targetBuffer: ShortArray, offset: Int, length: Int): Int {
        if (!isEnabled.get() || playbackState.get() != MusicPlaybackState.PLAYING) {
            targetBuffer.fill(0, offset, offset + length)
            currentPeakLevel = 0f
            return length
        }

        var filled = 0
        val isMute = isMuted.get()
        val vol = volume
        var peak = 0f

        while (filled < length) {
            var chunk = currentChunk
            if (chunk == null || currentChunkIndex >= chunk.size) {
                chunk = pcmChunkQueue.poll()
                if (chunk == null) {
                    // Underflow: zero out remaining and break
                    targetBuffer.fill(0, offset + filled, offset + length)
                    break
                }
                currentChunk = chunk
                currentChunkIndex = 0
            }

            val toCopy = (length - filled).coerceAtMost(chunk.size - currentChunkIndex)
            for (i in 0 until toCopy) {
                val destIdx = offset + filled + i
                if (isMute) {
                    targetBuffer[destIdx] = 0
                } else {
                    val s = chunk[currentChunkIndex + i].toFloat() * vol
                    val abs = kotlin.math.abs(s)
                    if (abs > peak) peak = abs
                    targetBuffer[destIdx] = s.toInt().coerceIn(-32768, 32767).toShort()
                }
            }

            currentChunkIndex += toCopy
            filled += toCopy
        }

        currentPeakLevel = if (isMute) 0f else (peak / 32768f).coerceIn(0f, 1f)
        return length
    }
}
