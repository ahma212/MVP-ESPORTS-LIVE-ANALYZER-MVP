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
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hardware AAC Audio Encoder.
 *
 * Encodes mixed 44.1kHz 16-bit stereo PCM from the AudioMixerEngine into AAC-LC.
 *
 * The encoded AAC stream can be sent to:
 * 1. YouTube Live through RtmpStreamSink
 * 2. Local MP4 recording through MediaMuxerSink
 *
 * Part 2-D improvements:
 * - Stop no longer cancels the drain loop immediately.
 * - New PCM input is blocked when shutdown begins.
 * - Already queued PCM frames are encoded first.
 * - AAC EOS is queued after pending PCM has been consumed.
 * - Final AAC output is drained before MediaCodec is released.
 * - RTMP and muxer sinks remain attached until the pipeline performs
 *   the coordinated final shutdown.
 */
class HardwareAudioEncoder(
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val bitrateBps: Int = 128000
) : AudioMixerEngine.AudioFrameConsumer {

    private val TAG = "HardwareAudioEncoder"

    private var mediaCodec: MediaCodec? = null
    private var drainJob: Job? = null

    private val isRunning = AtomicBoolean(false)

    /**
     * True while new PCM frames are allowed into the queue.
     *
     * Once stop() starts, this becomes false so no new audio frames
     * can arrive while the encoder is finalizing its remaining queue.
     */
    private val acceptingInput = AtomicBoolean(false)

    /**
     * Becomes true when stop() requests graceful AAC finalization.
     */
    private val eosRequested = AtomicBoolean(false)

    /**
     * Used to make sure AAC EOS is queued only once.
     */
    private val eosQueued = AtomicBoolean(false)

    val isMicMuted = AtomicBoolean(false)

    var volumeScale: Float = 1.0f

    private var rtmpSink: RtmpStreamSink? = null
    private var muxerSink: MediaMuxerSink? = null

    // Queue of PCM frames to feed to the AAC encoder.
    private class PcmFrame(
        val data: ByteArray,
        val ptsUs: Long
    )

    private val pcmQueue = ArrayBlockingQueue<PcmFrame>(60)

    fun setRtmpSink(sink: RtmpStreamSink?) {
        this.rtmpSink = sink
    }

    fun setMuxerSink(sink: MediaMuxerSink?) {
        this.muxerSink = sink
    }

    /**
     * Starts the AAC encoder.
     */
    fun start(): Boolean {
        if (isRunning.get()) {
            return true
        }

        try {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            ).apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )

                setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    bitrateBps
                )

                setInteger(
                    MediaFormat.KEY_MAX_INPUT_SIZE,
                    16384
                )
            }

            val encoder = MediaCodec.createEncoderByType(
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

            pcmQueue.clear()

            eosRequested.set(false)
            eosQueued.set(false)
            acceptingInput.set(true)
            isRunning.set(true)

            /**
             * Launch the encoding/drain loop.
             *
             * The loop stays alive during graceful shutdown until:
             * - all queued PCM has been consumed
             * - AAC EOS has been queued
             * - final AAC output has been drained
             */
            drainJob = CoroutineScope(Dispatchers.IO).launch {
                runEncodingAndDrainLoop(encoder)
            }

            Log.i(
                TAG,
                "Hardware AAC Audio Encoder started " +
                    "($sampleRate Hz, $channelCount ch, $bitrateBps bps)"
            )

            return true
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to start HardwareAudioEncoder: ${e.message}",
                e
            )

            try {
                mediaCodec?.release()
            } catch (_: Exception) {
            }

            mediaCodec = null
            acceptingInput.set(false)
            isRunning.set(false)

            return false
        }
    }

    /**
     * Receives mixed PCM audio from AudioMixerEngine.
     *
     * New frames are rejected after graceful shutdown begins.
     */
    override fun onMixedAudioPcm(
        pcmBytes: ByteArray,
        sampleCount: Int,
        ptsUs: Long
    ) {
        if (!isRunning.get() || !acceptingInput.get()) {
            return
        }

        val frameCopy = ByteArray(pcmBytes.size)

        System.arraycopy(
            pcmBytes,
            0,
            frameCopy,
            0,
            pcmBytes.size
        )

        /**
         * If the queue is full, do not block the audio callback forever.
         *
         * The encoder loop will continue consuming queued frames.
         */
        pcmQueue.offer(
            PcmFrame(
                frameCopy,
                ptsUs
            )
        )
    }

    /**
     * Main AAC encode + output drain loop.
     *
     * During normal recording:
     *   PCM queue -> AAC encoder -> AAC output -> RTMP / MP4
     *
     * During shutdown:
     *   stop()
     *      -> reject new PCM
     *      -> finish queued PCM
     *      -> queue AAC EOS
     *      -> drain final AAC buffers
     *      -> finish loop
     */
    private fun runEncodingAndDrainLoop(
        encoder: MediaCodec
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L

        var localEosQueued = false
        var finished = false

        try {
            while (
                isActive &&
                !finished &&
                (
                    isRunning.get() ||
                        eosRequested.get()
                    )
            ) {

                var suppliedFrame = false

                // ---------------------------------------------------------
                // 1. Feed queued PCM into the AAC encoder.
                // ---------------------------------------------------------
                val frame = pcmQueue.poll()

                if (frame != null && !localEosQueued) {
                    val inputIndex = try {
                        encoder.dequeueInputBuffer(timeoutUs)
                    } catch (e: Exception) {
                        Log.w(
                            TAG,
                            "Error getting AAC input buffer: ${e.message}"
                        )
                        -1
                    }

                    if (inputIndex >= 0) {
                        val inputBuf = encoder.getInputBuffer(inputIndex)

                        if (inputBuf != null) {
                            inputBuf.clear()

                            val bytesToWrite = minOf(
                                frame.data.size,
                                inputBuf.remaining()
                            )

                            inputBuf.put(
                                frame.data,
                                0,
                                bytesToWrite
                            )

                            encoder.queueInputBuffer(
                                inputIndex,
                                0,
                                bytesToWrite,
                                frame.ptsUs,
                                0
                            )

                            suppliedFrame = true
                        } else {
                            /**
                             * Do not silently lose the PCM frame if the
                             * codec did not return a usable input buffer.
                             */
                            pcmQueue.offer(frame)
                        }
                    } else {
                        /**
                         * Input buffer is temporarily unavailable.
                         * Put the frame back into the queue.
                         */
                        pcmQueue.offer(frame)
                    }
                }

                // ---------------------------------------------------------
                // 2. After all queued PCM has been consumed, queue EOS.
                // ---------------------------------------------------------
                if (
                    eosRequested.get() &&
                    pcmQueue.isEmpty() &&
                    !localEosQueued
                ) {
                    val inputIndex = try {
                        encoder.dequeueInputBuffer(timeoutUs)
                    } catch (e: Exception) {
                        Log.w(
                            TAG,
                            "Error getting AAC EOS input buffer: ${e.message}"
                        )
                        -1
                    }

                    if (inputIndex >= 0) {
                        try {
                            encoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )

                            localEosQueued = true
                            eosQueued.set(true)

                            Log.i(
                                TAG,
                                "AAC encoder EOS queued."
                            )
                        } catch (e: Exception) {
                            Log.e(
                                TAG,
                                "Failed to queue AAC EOS: ${e.message}",
                                e
                            )

                            finished = true
                        }
                    }
                }

                // ---------------------------------------------------------
                // 3. Drain all currently available AAC output.
                // ---------------------------------------------------------
                var drainedSomething = false

                while (isActive) {
                    val outputIndex = try {
                        encoder.dequeueOutputBuffer(
                            bufferInfo,
                            0
                        )
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Error draining AAC encoder: ${e.message}",
                            e
                        )

                        finished = true
                        break
                    }

                    when (outputIndex) {

                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            break
                        }

                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFormat = encoder.outputFormat

                            Log.i(
                                TAG,
                                "Audio Encoder output format changed: $newFormat"
                            )

                            /**
                             * MediaMuxerSink will wait for its required
                             * tracks before starting the MP4 muxer.
                             */
                            muxerSink?.addAudioTrack(
                                newFormat
                            )

                            /**
                             * RTMP receives the final AAC format too.
                             */
                            rtmpSink?.onAudioFormatChanged(
                                newFormat
                            )
                        }

                        MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                            /**
                             * Deprecated since API 21.
                             * No action required.
                             */
                        }

                        else -> {
                            if (outputIndex >= 0) {
                                drainedSomething = true

                                val outputBuf =
                                    encoder.getOutputBuffer(
                                        outputIndex
                                    )

                                val outputFlags = bufferInfo.flags

                                if (
                                    outputBuf != null &&
                                    bufferInfo.size > 0
                                ) {
                                    /**
                                     * Send encoded AAC to YouTube Live.
                                     */
                                    rtmpSink?.onAudioSample(
                                        outputBuf,
                                        bufferInfo
                                    )

                                    /**
                                     * Send encoded AAC to local MP4 muxer.
                                     */
                                    muxerSink?.writeAudioSampleData(
                                        outputBuf,
                                        bufferInfo
                                    )
                                }

                                encoder.releaseOutputBuffer(
                                    outputIndex,
                                    false
                                )

                                if (
                                    (
                                        outputFlags and
                                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    ) != 0
                                ) {
                                    Log.i(
                                        TAG,
                                        "AAC encoder reached END_OF_STREAM."
                                    )

                                    finished = true
                                    break
                                }
                            }
                        }
                    }
                }

                /**
                 * Avoid busy looping when no PCM was available
                 * and no AAC output was immediately ready.
                 */
                if (!suppliedFrame && !drainedSomething) {
                    Thread.sleep(5)
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()

            Log.w(
                TAG,
                "AAC encoding loop interrupted."
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "AAC encoding loop failed: ${e.message}",
                e
            )
        } finally {
            acceptingInput.set(false)
            isRunning.set(false)

            Log.i(
                TAG,
                "AAC encoding/drain loop finished."
            )
        }
    }

    /**
     * Gracefully stops the AAC encoder.
     *
     * Important:
     * We do NOT cancel the drain job immediately.
     *
     * Instead:
     * 1. Stop accepting new PCM.
     * 2. Finish PCM already waiting in the queue.
     * 3. Queue AAC EOS.
     * 4. Drain final AAC output.
     * 5. Wait for the drain loop to finish.
     * 6. Release MediaCodec.
     *
     * RTMP and MediaMuxer sinks are intentionally NOT nulled here.
     * The parent OutputCompositionPipeline owns the overall final
     * shutdown order and will close those sinks after video/audio
     * finalization is complete.
     */
    fun stop() {
        if (
            !isRunning.get() &&
            drainJob?.isActive != true
        ) {
            return
        }

        Log.i(
            TAG,
            "Stopping HardwareAudioEncoder gracefully..."
        )

        /**
         * No new PCM frames should enter the queue from this point.
         */
        acceptingInput.set(false)

        /**
         * Tell the drain loop to finish queued PCM and then
         * send AAC EOS.
         */
        eosRequested.set(true)

        val job = drainJob

        if (job != null) {
            try {
                /**
                 * Wait for the encoding loop to consume:
                 * - queued PCM
                 * - AAC EOS
                 * - final AAC output
                 */
                runBlocking {
                    job.join()
                }
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Audio drain wait interrupted: ${e.message}"
                )
            }
        }

        /**
         * Safety cleanup if the drain loop exited unexpectedly.
         */
        try {
            mediaCodec?.stop()
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to stop AAC MediaCodec: ${e.message}"
            )
        }

        try {
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Failed to release AAC MediaCodec: ${e.message}"
            )
        }

        mediaCodec = null
        drainJob = null

        pcmQueue.clear()

        isRunning.set(false)
        acceptingInput.set(false)
        eosRequested.set(false)
        eosQueued.set(false)

        Log.i(
            TAG,
            "HardwareAudioEncoder stopped and finalized."
        )
    }

    fun isRunning(): Boolean = isRunning.get()
}