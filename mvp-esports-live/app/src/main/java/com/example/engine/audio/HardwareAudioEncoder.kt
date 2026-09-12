package com.example.engine.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import com.example.engine.output.MediaMuxerSink
import com.example.engine.output.rtmp.RtmpStreamSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Hardware AAC Audio Encoder.
 *
 * Input:
 *     Mixed 44.1kHz / stereo / 16-bit PCM
 *
 * Output:
 *     AAC-LC elementary audio stream
 *
 * Destinations:
 *     - Shared MediaMuxerSink for local MP4 recording
 *     - RtmpStreamSink for YouTube/live streaming
 *
 * Architecture:
 *
 * AudioMixerEngine
 *        ↓
 * HardwareAudioEncoder
 *        ↓
 *   ┌────┴─────┐
 *   ↓          ↓
 * MP4       RTMP
 *
 * Shutdown ownership:
 *
 * OutputCompositionPipeline owns the final lifecycle of the complete
 * output system and the shared MediaMuxer.
 *
 * HardwareAudioEncoder is responsible for:
 * - accepting mixed PCM while running
 * - encoding PCM to AAC
 * - reporting the AAC output format
 * - draining final AAC buffers
 * - queueing encoder EOS during shutdown
 * - stopping/releasing MediaCodec
 *
 * HardwareAudioEncoder MUST NOT stop or release the shared MediaMuxer.
 */
class HardwareAudioEncoder(
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val bitrateBps: Int = 128000
) : AudioMixerEngine.AudioFrameConsumer {

    private val TAG = "HardwareAudioEncoder"

    /**
     * Short MediaCodec polling interval keeps the encoder responsive
     * while avoiding a continuously busy CPU loop.
     */
    private val TIMEOUT_US = 10_000L

    /**
     * AudioMixerEngine currently produces approximately 1024 samples
     * per channel for each mixed callback.
     *
     * 1024 samples at 44.1kHz ≈ 23.22ms.
     */
    private val FRAME_SAMPLES_PER_CHANNEL = 1024

    private val FRAME_DURATION_US =
        (
            FRAME_SAMPLES_PER_CHANNEL.toLong() *
                1_000_000L
            ) / sampleRate.coerceAtLeast(1)

    /**
     * Maximum amount of PCM waiting to be encoded.
     *
     * At 44.1kHz with 1024 samples/frame, 60 frames is roughly
     * 1.39 seconds of PCM. This protects the app from unbounded
     * memory growth if the encoder temporarily falls behind.
     */
    private val PCM_QUEUE_CAPACITY = 60

    /**
     * Maximum amount of encoded AAC samples retained before the
     * shared MediaMuxer has started.
     *
     * The muxer starts only after both audio and video tracks exist.
     * Audio may become ready first, so a small pending buffer prevents
     * initial AAC samples from being silently lost.
     */
    private val PENDING_MUXER_SAMPLE_CAPACITY = 120

    private var mediaCodec: MediaCodec? = null

    private var drainJob: Job? = null

    /**
     * isRunning means the encoder session has not been fully shut down.
     *
     * During graceful shutdown it intentionally remains true while
     * final queued PCM is encoded and output EOS is drained.
     */
    private val isRunning = AtomicBoolean(false)

    /**
     * Once shutdown starts, no new PCM frames are accepted from the mixer.
     */
    private val acceptingInput = AtomicBoolean(false)

    /**
     * True once the shutdown procedure has been requested.
     */
    private val shutdownRequested = AtomicBoolean(false)

    /**
     * Prevents EOS from being queued more than once.
     */
    private val inputEosQueued = AtomicBoolean(false)

    /**
     * True after output BUFFER_FLAG_END_OF_STREAM is observed.
     */
    private val outputEosReached = AtomicBoolean(false)

    /**
     * Counts successfully encoded AAC output samples/frames.
     */
    val encodedFrames = AtomicLong(0)

    /**
     * Total encoded AAC bytes delivered by the encoder.
     */
    val encodedBytes = AtomicLong(0)

    /**
     * Number of mixed PCM frames dropped because the bounded input
     * queue was full.
     */
    val droppedPcmFrames = AtomicLong(0)

    /**
     * Kept as compatibility fields for older callers.
     *
     * Actual microphone mute/volume control belongs to
     * AudioMixerEngine/MicrophoneAudioSource.
     */
    val isMicMuted = AtomicBoolean(false)

    @Volatile
    var volumeScale: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 2.0f)
        }

    private var rtmpSink: RtmpStreamSink? = null

    /**
     * Shared muxer injected by OutputCompositionPipeline.
     *
     * This encoder NEVER stops/releases the shared muxer.
     */
    private var muxerSink: MediaMuxerSink? = null

    /**
     * Immutable PCM frame placed into the encoder queue.
     *
     * The ByteArray is owned by this object and therefore safe from
     * later modification by AudioMixerEngine.
     */
    private data class PcmFrame(
        val data: ByteArray,
        val ptsUs: Long
    )

    /**
     * Encoded AAC sample retained temporarily until the shared MP4
     * muxer has started.
     */
    private data class PendingMuxerSample(
        val data: ByteArray,
        val bufferInfo: MediaCodec.BufferInfo
    )
    private val pcmQueue =
        ArrayBlockingQueue<PcmFrame>(
            PCM_QUEUE_CAPACITY
        )

    private val pendingMuxerSamples =
        ArrayBlockingQueue<PendingMuxerSample>(
            PENDING_MUXER_SAMPLE_CAPACITY
        )

    @Volatile
    private var lastQueuedPtsUs: Long = -1L

    /**
     * Sets the optional live RTMP sink.
     */
    fun setRtmpSink(
        sink: RtmpStreamSink?
    ) {
        rtmpSink = sink
    }

    /**
     * Sets the shared MP4 muxer.
     *
     * OutputCompositionPipeline owns the actual muxer lifecycle.
     */
    fun setMuxerSink(
        sink: MediaMuxerSink?
    ) {
        muxerSink = sink

        /*
         * If the muxer is already started at the moment the sink is
         * attached, try to flush anything that was queued earlier.
         */
        if (sink?.isMuxerStarted() == true) {
            flushPendingMuxerSamples()
        }
    }

    /**
     * Starts the AAC encoder.
     *
     * The method configures a standard AAC-LC encoder and launches
     * the input/output processing loop.
     */
    @Synchronized
    fun start(): Boolean {

        if (isRunning.get()) {
            return true
        }

        /*
         * A new session starts from a clean state.
         */
        acceptingInput.set(false)
        shutdownRequested.set(false)
        inputEosQueued.set(false)
        outputEosReached.set(false)

        pcmQueue.clear()
        pendingMuxerSamples.clear()

        lastQueuedPtsUs = -1L
        encodedFrames.set(0L)
        encodedBytes.set(0L)
        droppedPcmFrames.set(0L)

        val format =
            MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            ).apply {

                /*
                 * AAC-LC is the normal AAC profile expected by MP4
                 * recording and the RTMP audio path.
                 */
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel
                        .AACObjectLC
                )

                setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    bitrateBps.coerceAtLeast(16_000)
                )

                /*
                 * The mixer normally supplies only a few KB per frame.
                 * This leaves enough headroom for MediaCodec input.
                 */
                setInteger(
                    MediaFormat.KEY_MAX_INPUT_SIZE,
                    16384
                )

                /*
                 * Explicit PCM input format.
                 *
                 * AudioMixerEngine produces signed 16-bit PCM.
                 */
                setInteger(
                    MediaFormat.KEY_PCM_ENCODING,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )
            }

        var encoder: MediaCodec? = null

        try {

            encoder =
                MediaCodec.createEncoderByType(
                    MediaFormat.MIMETYPE_AUDIO_AAC
                )

            encoder.configure(
                format,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )

            encoder.start()

            mediaCodec = encoder

            isRunning.set(true)
            acceptingInput.set(true)
            shutdownRequested.set(false)
            inputEosQueued.set(false)
            outputEosReached.set(false)

            drainJob =
                CoroutineScope(
                    Dispatchers.IO
                ).launch {

                    runEncodingAndDrainLoop(
                        encoder
                    )
                }

            Log.i(
                TAG,
                "AAC encoder started: " +
                    "${sampleRate}Hz, " +
                    "${channelCount}ch, " +
                    "${bitrateBps}bps"
            )

            return true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to start HardwareAudioEncoder: " +
                    e.message,
                e
            )

            /*
             * Explicit startup cleanup.
             *
             * Do not use the normal graceful stop path here because
             * the encoder has not entered a running session yet.
             */
            acceptingInput.set(false)
            shutdownRequested.set(false)
            inputEosQueued.set(false)
            outputEosReached.set(false)
            isRunning.set(false)

            pcmQueue.clear()
            pendingMuxerSamples.clear()

            try {
                encoder?.stop()
            } catch (_: Exception) {
            }

            try {
                encoder?.release()
            } catch (_: Exception) {
            }

            mediaCodec = null
            drainJob = null

            return false
        }
    }

    /**
     * Receives one mixed PCM frame from AudioMixerEngine.
     *
     * The mixer may reuse its own PCM buffer, therefore a private
     * copy is made before putting data into the asynchronous queue.
     */
    override fun onMixedAudioPcm(
        pcmBytes: ByteArray,
        sampleCount: Int,
        ptsUs: Long
    ) {

        if (!acceptingInput.get()) {
            return
        }

        if (pcmBytes.isEmpty()) {
            return
        }

        val normalizedPtsUs =
            synchronized(this) {

                val pts =
                    if (lastQueuedPtsUs < 0L) {
                        ptsUs
                    } else {
                        maxOf(
                            ptsUs,
                            lastQueuedPtsUs + 1L
                        )
                    }

                lastQueuedPtsUs = pts

                pts
            }

        val frameCopy =
            pcmBytes.copyOf()

        val frame =
            PcmFrame(
                data = frameCopy,
                ptsUs = normalizedPtsUs
            )

        if (!pcmQueue.offer(frame)) {

            val dropped =
                droppedPcmFrames.incrementAndGet()

            /*
             * Do not block AudioMixerEngine when the encoder falls
             * behind. Blocking the mixer could stall microphone,
             * internal audio and music processing.
             */
            if (
                dropped == 1L ||
                dropped % 100L == 0L
            ) {
                Log.w(
                    TAG,
                    "PCM encoder queue full. " +
                        "Dropped frames=$dropped"
                )
            }
        }
    }

    /**
     * Main AAC input/output loop.
     *
     * Normal operation:
     *   PCM queue → encoder input → AAC output
     *
     * Graceful shutdown:
     *   finish queued PCM
     *       ↓
     *   queue input EOS
     *       ↓
     *   continue draining
     *       ↓
     *   wait for output EOS
     */
    private suspend fun runEncodingAndDrainLoop(
        encoder: MediaCodec
    ) {

        val bufferInfo =
            MediaCodec.BufferInfo()

        var pendingInputFrame: PcmFrame? = null

        try {

            while (
                    isRunning.get()
            ) {

                var madeProgress = false

                /*
                 * ---------------------------------------------------------
                 * STEP 1 — Obtain a PCM frame.
                 * ---------------------------------------------------------
                 *
                 * Keep one pending frame locally so it is never lost just
                 * because MediaCodec temporarily has no free input buffer.
                 */
                if (
                    pendingInputFrame == null &&
                        acceptingInput.get()
                ) {
                    pendingInputFrame =
                        pcmQueue.poll(
                            5L,
                            TimeUnit.MILLISECONDS
                        )
                }

                /*
                 * During graceful shutdown, stop accepting new PCM.
                 * Existing queued PCM must still be encoded before EOS.
                 */
                if (
                    pendingInputFrame == null &&
                        shutdownRequested.get() &&
                        pcmQueue.isNotEmpty()
                ) {
                    pendingInputFrame =
                        pcmQueue.poll()
                }

                /*
                 *
                 ---------------------------------------------------------
                 * STEP 2 — Feed one PCM frame to MediaCodec.
                 * ---------------------------------------------------------
                 */
                                val frame =
                    pendingInputFrame

                if (frame != null) {

                    val inputIndex =
                        try {
                            encoder.dequeueInputBuffer(
                                TIMEOUT_US
                            )
                        } catch (e: Exception) {
                            Log.e(
                                TAG,
                                "Failed to dequeue AAC input buffer: " +
                                    e.message,
                                e
                            )
                            -1
                        }

                    if (inputIndex >= 0) {

                        val inputBuffer =
                            encoder.getInputBuffer(
                                inputIndex
                            )

                        if (inputBuffer != null) {

                            if (
                                frame.data.size <=
                                    inputBuffer.capacity()
                            ) {

                                inputBuffer.clear()

                                inputBuffer.put(
                                    frame.data
                                )

                                try {

                                    encoder.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        frame.data.size,
                                        frame.ptsUs,
                                        0
                                    )

                                    pendingInputFrame = null
                                    madeProgress = true

                                } catch (e: Exception) {

                                    Log.e(
                                        TAG,
                                        "Failed to queue AAC PCM input: " +
                                            e.message,
                                        e
                                    )

                                    /*
                                     * Do not spin forever on an input
                                     * buffer that cannot accept the data.
                                     */
                                }

                            } else {

                                /*
                                 * The mixer frame should normally be much
                                 * smaller than this. If an unexpected
                                 * oversized frame arrives, do not overflow
                                 * the MediaCodec input buffer.
                                 */
                                Log.w(
                                    TAG,
                                    "PCM frame too large for AAC input buffer: " +
                                        "${frame.data.size} > " +
                                        "${inputBuffer.capacity()}"
                                )

                                pendingInputFrame = null
                            }
                        }
                    }
                }

                /*
                 * ---------------------------------------------------------
                 * STEP 3 — If shutdown was requested and no PCM remains,
                 * queue encoder input EOS exactly once.
                 * ---------------------------------------------------------
                 */
                if (
                    shutdownRequested.get() &&
                        pendingInputFrame == null &&
                        pcmQueue.isEmpty() &&
                        !inputEosQueued.get()
                ) {

                    val inputIndex =
                        try {
                            encoder.dequeueInputBuffer(
                                0L
                            )
                        } catch (_: Exception) {
                            -1
                        }

                    if (inputIndex >= 0) {

                        try {

                            val eosPtsUs =
                                synchronized(this) {
                                    if (lastQueuedPtsUs < 0L) {
                                        System.nanoTime() / 1000L
                                    } else {
                                        lastQueuedPtsUs +
                                            FRAME_DURATION_US
                                    }
                                }

                            encoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                eosPtsUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )

                            inputEosQueued.set(true)
                            madeProgress = true

                            Log.i(
                                TAG,
                                "AAC input EOS queued."
                            )

                        } catch (e: Exception) {

                            Log.w(
                                TAG,
                                "Failed to queue AAC input EOS: " +
                                    e.message
                            )
                        }
                    }
                }

                /*
                 * ---------------------------------------------------------
                 * STEP 4 — Drain as many AAC output buffers as are ready.
                 * ---------------------------------------------------------
                 */
                while (
                        isRunning.get()
                ) {

                    val outputIndex =
                        try {
                            encoder.dequeueOutputBuffer(
                                bufferInfo,
                                0L
                            )
                        } catch (e: Exception) {

                            Log.e(
                                TAG,
                                "AAC output dequeue failed: " +
                                    e.message,
                                e
                            )

                            -1
                        }

                    when (outputIndex) {

                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            break
                        }

                      MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                            val rawFormat =
                                encoder.outputFormat

                            Log.i(
                                TAG,
                                "AAC output format changed: " +
                                    rawFormat
                            )

                            /*
                             * Clean format for MediaMuxer:
                             * keep only AAC MP4-safe keys + csd-0.
                             * Strip PCM / max-input junk that can make
                             * players report "audio format Unknown".
                             */
                            val cleanFormat = android.media.MediaFormat().apply {
                                setString(
                                    android.media.MediaFormat.KEY_MIME,
                                    rawFormat.getString(android.media.MediaFormat.KEY_MIME)
                                        ?: "audio/mp4a-latm"
                                )
                                setInteger(
                                    android.media.MediaFormat.KEY_SAMPLE_RATE,
                                    rawFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                                )
                                setInteger(
                                    android.media.MediaFormat.KEY_CHANNEL_COUNT,
                                    rawFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                                )
                                if (rawFormat.containsKey(android.media.MediaFormat.KEY_AAC_PROFILE)) {
                                    setInteger(
                                        android.media.MediaFormat.KEY_AAC_PROFILE,
                                        rawFormat.getInteger(android.media.MediaFormat.KEY_AAC_PROFILE)
                                    )
                                }
                                if (rawFormat.containsKey(android.media.MediaFormat.KEY_BIT_RATE)) {
                                    setInteger(
                                        android.media.MediaFormat.KEY_BIT_RATE,
                                        rawFormat.getInteger(android.media.MediaFormat.KEY_BIT_RATE)
                                    )
                                }
                                if (rawFormat.containsKey("csd-0")) {
                                    val csd = rawFormat.getByteBuffer("csd-0")
                                    if (csd != null) {
                                        setByteBuffer("csd-0", csd.duplicate())
                                    }
                                }
                            }

                            muxerSink?.addAudioTrack(
                                cleanFormat
                            )

                            /*
                             * RTMP sink gets AAC AudioSpecificConfig.
                             */
                            rtmpSink?.onAudioFormatChanged(
                                cleanFormat
                            )

                            /*
                             * If the video track was already registered,
                             * the muxer may now be started. Flush anything
                             * retained from before muxer startup.
                             */
                            flushPendingMuxerSamples()

                            madeProgress = true
                        }

                        MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                            /*
                             * Deprecated on modern Android.
                             */
                            madeProgress = true
                        }

                        else -> {

                            if (outputIndex >= 0) {

                                val outputFlags =
                                    bufferInfo.flags

                                val outputSize =
                                    bufferInfo.size

                                try {

                                    val outputBuffer =
                                        encoder.getOutputBuffer(
                                            outputIndex
                                        )

                                    if (
                                        outputBuffer != null &&
                                            outputSize > 0 &&
                                            (
                                                outputFlags and
                                                    MediaCodec
                                                        .BUFFER_FLAG_CODEC_CONFIG
                                                ) == 0
                                    ) {

                                        /*
                                         * -------------------------------------------------
                                         * RTMP
                                         * -------------------------------------------------
                                         *
                                         * RtmpStreamSink copies the AAC data into
                                         * its own packet queue, so it is safe to
                                         * release this codec output buffer afterwards.
                                         */
                                        try {
                                            rtmpSink?.onAudioSample(
                                                outputBuffer,
                                                bufferInfo
                                            )
                                        } catch (e: Exception) {
                                            Log.w(
                                                TAG,
                                                "RTMP AAC sample delivery failed: " +
                                                    e.message
                                            )
                                        }

                                        /*
                                         * -------------------------------------------------
                                         * MP4 / MediaMuxer
                                         * -------------------------------------------------
                                         */
                                        val currentMuxer =
                                            muxerSink

                                        if (
                                            currentMuxer != null &&
                                                currentMuxer.isMuxerStarted()
                                        ) {

                                            try {
                                                currentMuxer
                                                    .writeAudioSampleData(
                                                        outputBuffer,
                                                        bufferInfo
                                                    )
                                            } catch (e: Exception) {
                                                Log.w(
                                                    TAG,
                                                    "MP4 AAC sample write failed: " +
                                                        e.message
                                                )
                                            }

                                        } else {

                                            /*
                                             * Muxer is not ready yet because
                                             * video track may not have arrived.
                                             *
                                             * Copy the exact AAC sample now,
                                             * because the MediaCodec output buffer
                                             * becomes invalid after release.
                                             */
                                            val sampleBytes =
                                                copyCodecBuffer(
                                                    outputBuffer,
                                                    bufferInfo
                                                )

                                            if (
                                                sampleBytes != null
                                            ) {

                                                val pendingInfo =
                                                    MediaCodec.BufferInfo()

                                                pendingInfo.set(
                                                    0,
                                                    sampleBytes.size,
                                                    bufferInfo
                                                        .presentationTimeUs,
                                                    bufferInfo.flags
                                                )

                                                if (
                                                    pendingMuxerSamples.remainingCapacity() >
                                                        0
                                                ) {

                                                    pendingMuxerSamples.offer(
                                                        PendingMuxerSample(
                                                            data = sampleBytes,
                                                            bufferInfo = pendingInfo
                                                        )
                                                    )

                                                } else {

                                                    /*
                                                     * This should only occur if
                                                     * the video track never becomes
                                                     * available for an unusually
                                                     * long period.
                                                     */
                                                    Log.w(
                                                        TAG,
                                                        "Pending AAC muxer buffer full; " +
                                                            "dropping oldest sample."
                                                    )

                                                    pendingMuxerSamples.poll()

                                                    pendingMuxerSamples.offer(
                                                        PendingMuxerSample(
                                                            data = sampleBytes,
                                                            bufferInfo = pendingInfo
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        val frameIndex =
                                            encodedFrames.incrementAndGet()

                                        encodedBytes.addAndGet(
                                            outputSize.toLong()
                                        )

                                        madeProgress = true
                                    }

                                } catch (e: Exception) {

                                    Log.e(
                                        TAG,
                                        "Error processing AAC output buffer: " +
                                            e.message,
                                        e
                                    )

                                } finally {

                                    try {
                                        encoder.releaseOutputBuffer(
                                            outputIndex,
                                            false
                                        )
                                    } catch (e: Exception) {
                                        Log.w(
                                            TAG,
                                            "Failed to release AAC output buffer: " +
                                                e.message
                                        )
                                    }
                                }

                                /*
                                 * Output EOS is authoritative.
                                 *
                                 * Do not stop the drain loop merely because
                                 * stop() was called.
                                 */
                                if (
                                    (
                                        outputFlags and
                                            MediaCodec
                                                .BUFFER_FLAG_END_OF_STREAM
                                        ) != 0
                                ) {

                                    outputEosReached.set(true)

                                    Log.i(
                                        TAG,
                                        "AAC output END_OF_STREAM reached."
                                    )

                                    break
                                }
                            }
                        }
                    }
                }

                /*
                 * Flush pending AAC samples whenever the shared muxer
                 * becomes available.
                 */
                flushPendingMuxerSamples()

                /*
                 * During graceful shutdown, once encoder output EOS has
                 * arrived, there is nothing more to do.
                 */
                if (
                    shutdownRequested.get() &&
                        outputEosReached.get()
                ) {
                    break
                }

                /*
                 * Prevent a hot CPU loop when there is temporarily
                 * no PCM input and no AAC output.
                 */
                if (!madeProgress) {
                    try {
                        Thread.sleep(2L)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "AAC encoding/drain loop failed: " +
                    e.message,
                e
            )

        } finally {

            Log.i(
                TAG,
                "AAC encoding/drain loop finished."
            )
        }
    }

    /**
     * Copies only the valid MediaCodec output range.
     *
     * Needed only when the MP4 muxer has not started yet.
     */
    private fun copyCodecBuffer(
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ): ByteArray? {

        if (bufferInfo.size <= 0) {
            return null
        }

        return try {

            val duplicate =
                buffer.duplicate()

            val start =
                bufferInfo.offset

            val end =
                bufferInfo.offset +
                    bufferInfo.size

            if (
                start < 0 ||
                    end > duplicate.capacity() ||
                    start >= end
            ) {
                Log.w(
                    TAG,
                    "Invalid AAC output buffer range: " +
                        "offset=${bufferInfo.offset}, " +
                        "size=${bufferInfo.size}, " +
                        "capacity=${duplicate.capacity()}"
                )

                return null
            }

            duplicate.position(start)
            duplicate.limit(end)

            val bytes =
                ByteArray(
                    bufferInfo.size
                )

            duplicate.get(bytes)

            bytes

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Failed to copy AAC output buffer: " +
                    e.message
            )

            null
        }
    }

    /**
     * Writes any AAC samples that were temporarily retained because
     * the shared MediaMuxer had not started yet.
     */
    private fun flushPendingMuxerSamples() {

        val muxer =
            muxerSink
                ?: return

        if (!muxer.isMuxerStarted()) {
            return
        }

        while (true) {

            val pending =
                pendingMuxerSamples.poll()
                    ?: break

            try {

                val buffer =
                    ByteBuffer.wrap(
                        pending.data
                    )

                muxer.writeAudioSampleData(
                    buffer,
                    pending.bufferInfo
                )

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Failed to flush pending AAC sample to MP4: " +
                        e.message
                )
            }
        }
    }

    /**
     * Gracefully stops the AAC encoder.
     *
     * Shutdown sequence:
     *
     * 1. Stop accepting new PCM.
     * 2. Keep existing queued PCM.
     * 3. Queue MediaCodec input EOS.
     * 4. Continue draining AAC output.
     * 5. Wait for output EOS.
     * 6. Release MediaCodec.
     * 7. Clear local queues/state.
     *
     * IMPORTANT:
     *
     * This method NEVER stops or releases MediaMuxerSink.
     * OutputCompositionPipeline owns the shared muxer.
     */
    @Synchronized
    fun stop() {

        if (
            !isRunning.get() &&
                !shutdownRequested.get()
        ) {
            return
        }

        Log.i(
            TAG,
            "Stopping HardwareAudioEncoder gracefully..."
        )

        /*
         * Step 1:
         *
         * Prevent the mixer from adding new PCM frames.
         */
        acceptingInput.set(false)

        /*
         * Step 2:
         *
         * Tell the encoding loop to finish existing data and then
         * queue encoder EOS.
         */
        shutdownRequested.set(true)

        val job =
            drainJob

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
                            "AAC drain loop did not finish within " +
                                "5 seconds. Cancelling for safety."
                        )

                        job.cancel()

                        withTimeoutOrNull(
                            1_000L
                        ) {
                            job.join()
                        }
                    }
                }

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Waiting for AAC drain loop failed: " +
                        e.message
                )

                try {
                    job.cancel()
                } catch (_: Exception) {
                }
            }
        }

        /*
         * If the muxer became ready during final draining,
         * flush all retained AAC samples now.
         */
        try {
            flushPendingMuxerSamples()
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Final AAC muxer flush failed: " +
                    e.message
            )
        }

        /*
         * MediaCodec is released only after the drain loop has
         * completed or the safety timeout forced it to stop.
         */
        try {
            mediaCodec?.stop()
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to stop AAC MediaCodec: " +
                    e.message
            )
        }

        try {
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to release AAC MediaCodec: " +
                    e.message
            )
        }

        mediaCodec = null
        drainJob = null

        pcmQueue.clear()
        pendingMuxerSamples.clear()

        acceptingInput.set(false)
        shutdownRequested.set(false)
        inputEosQueued.set(false)
        outputEosReached.set(false)
        isRunning.set(false)

        lastQueuedPtsUs = -1L

        /*
         * IMPORTANT:
         *
         * Do not call:
         *
         *     muxerSink?.stopAndRelease()
         *
         * because the muxer is shared with the video encoder and is
         * owned by OutputCompositionPipeline.
         */

        muxerSink = null
        rtmpSink = null

        Log.i(
            TAG,
            "HardwareAudioEncoder fully stopped."
        )
    }

    /**
     * True while the encoder session is active.
     */
    fun isRunning(): Boolean =
        isRunning.get()
}