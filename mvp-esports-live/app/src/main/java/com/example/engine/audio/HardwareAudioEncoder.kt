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
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hardware AAC Audio Encoder.
 * Encodes mixed 44.1kHz 16-bit stereo PCM from the AudioMixerEngine into AAC-LC,
 * and streams to YouTube Live (via RtmpStreamSink) and/or local MP4 recorder (via MediaMuxerSink).
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

    val isMicMuted = AtomicBoolean(false)
    var volumeScale: Float = 1.0f

    private var rtmpSink: RtmpStreamSink? = null
    private var muxerSink: MediaMuxerSink? = null

    // Queue of PCM frames to feed to the encoder
    private class PcmFrame(val data: ByteArray, val ptsUs: Long)
    private val pcmQueue = ArrayBlockingQueue<PcmFrame>(60)

    fun setRtmpSink(sink: RtmpStreamSink?) {
        this.rtmpSink = sink
    }

    fun setMuxerSink(sink: MediaMuxerSink?) {
        this.muxerSink = sink
    }

    fun start(): Boolean {
        if (isRunning.get()) return true

        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            this.mediaCodec = encoder
            this.isRunning.set(true)
            this.pcmQueue.clear()

            // Launch Audio Encoding & Drainage Loop
            drainJob = CoroutineScope(Dispatchers.IO).launch {
                runEncodingAndDrainLoop(encoder)
            }

            Log.i(TAG, "Hardware AAC Audio Encoder started ($sampleRate Hz, $channelCount ch, $bitrateBps bps)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HardwareAudioEncoder: ${e.message}", e)
            stop()
            return false
        }
    }

    override fun onMixedAudioPcm(pcmBytes: ByteArray, sampleCount: Int, ptsUs: Long) {
        if (!isRunning.get()) return
        val frameCopy = ByteArray(pcmBytes.size)
        System.arraycopy(pcmBytes, 0, frameCopy, 0, pcmBytes.size)
        pcmQueue.offer(PcmFrame(frameCopy, ptsUs))
    }

    private fun runEncodingAndDrainLoop(encoder: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L

        while (isRunning.get()) {
            // 1. Feed input buffers from PCM queue
            val frame = pcmQueue.poll()
            if (frame != null) {
                val inputIndex = try {
                    encoder.dequeueInputBuffer(timeoutUs)
                } catch (e: Exception) {
                    -1
                }

                if (inputIndex >= 0) {
                    val inputBuf = encoder.getInputBuffer(inputIndex)
                    if (inputBuf != null) {
                        inputBuf.clear()
                        inputBuf.put(frame.data)
                        encoder.queueInputBuffer(inputIndex, 0, frame.data.size, frame.ptsUs, 0)
                    }
                }
            }

            // 2. Drain output AAC buffers
            while (isRunning.get()) {
                val outputIndex = try {
                    encoder.dequeueOutputBuffer(bufferInfo, 0)
                } catch (e: Exception) {
                    -1
                }

                when (outputIndex) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> break
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = encoder.outputFormat
                        Log.i(TAG, "Audio Encoder output format changed: $newFormat")
                        muxerSink?.addAudioTrack(newFormat)
                        rtmpSink?.onAudioFormatChanged(newFormat)
                    }
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {}
                    else -> {
                        if (outputIndex >= 0) {
                            val outputBuf = encoder.getOutputBuffer(outputIndex)
                            if (outputBuf != null && bufferInfo.size > 0) {
                                // Feed to YouTube Live
                                rtmpSink?.onAudioSample(outputBuf, bufferInfo)

                                // Feed to MP4 Muxer
                                muxerSink?.writeAudioSampleData(outputBuf, bufferInfo)
                            }
                            encoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }

            if (frame == null) {
                Thread.sleep(5)
            }
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        drainJob?.cancel()
        pcmQueue.clear()

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (_: Exception) {}

        mediaCodec = null
        rtmpSink = null
        muxerSink = null
        Log.i(TAG, "HardwareAudioEncoder stopped.")
    }

    fun isRunning(): Boolean = isRunning.get()
}
