package com.example.engine.output.rtmp

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.example.model.StreamHealth
import com.example.model.StreamTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class RtmpConnectionState(val label: String) {
    IDLE("Idle"),
    CONNECTING("Connecting to YouTube..."),
    HANDSHAKING("Securing RTMP Handshake..."),
    CONFIGURING("Configuring Stream Ingest..."),
    LIVE("LIVE ON YOUTUBE"),
    RECONNECTING("Reconnecting..."),
    ERROR("Connection Error"),
    DISCONNECTED("Stream Ended")
}

class RtmpStreamSink(
    private val rtmpUrl: String,
    private val streamKey: String,
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val targetBitrateKbps: Int
) {
    private val TAG = "RtmpStreamSink"

    private val rtmpConnection = RtmpConnection()
    private val isRunning = AtomicBoolean(false)
    private var streamStartUs: Long = -1L
    private val frameCounter = AtomicLong(0)
    private val droppedFrames = AtomicLong(0)
    private var senderJob: Job? = null
    private var telemetryJob: Job? = null

    private var spsBytes: ByteArray? = null
    private var ppsBytes: ByteArray? = null
    private var isHeaderSent = false

    private val _connectionState = MutableStateFlow(RtmpConnectionState.IDLE)
    val connectionState: StateFlow<RtmpConnectionState> = _connectionState.asStateFlow()

    private val _telemetry = MutableStateFlow(StreamTelemetry())
    val telemetry: StateFlow<StreamTelemetry> = _telemetry.asStateFlow()

    private sealed class StreamPacket {
        data class Video(val data: ByteArray, val isKeyFrame: Boolean, val timestampMs: Long) : StreamPacket()
        data class Audio(val data: ByteArray, val timestampMs: Long) : StreamPacket()
    }

    private val packetQueue = LinkedBlockingQueue<StreamPacket>(200)

    fun start(): Boolean {
        if (isRunning.get()) return true
        isRunning.set(true)
        _connectionState.value = RtmpConnectionState.CONNECTING

        val connected = rtmpConnection.open(rtmpUrl, streamKey)
        if (!connected) {
            _connectionState.value = RtmpConnectionState.ERROR
            isRunning.set(false)
            return false
        }

        _connectionState.value = RtmpConnectionState.CONFIGURING

        // Send metadata descriptor
        rtmpConnection.sendMetadata(
            width = width,
            height = height,
            fps = fps,
            bitrateKbps = targetBitrateKbps,
            sampleRate = 44100
        )

        // Send AAC Sequence Header (44.1kHz stereo)
        rtmpConnection.sendAacSequenceHeader(sampleRate = 44100, channels = 2)

        _connectionState.value = RtmpConnectionState.LIVE

        // Start worker coroutine for network transmission
        startNetworkSender()
        startTelemetryMonitor()

        Log.i(TAG, "RtmpStreamSink successfully started live streaming!")
        return true
    }

    private fun startNetworkSender() {
        senderJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isRunning.get()) {
                val packet = try {
                    packetQueue.poll(50, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    break
                }

                if (packet != null) {
                    try {
                        when (packet) {
                            is StreamPacket.Video -> {
                                rtmpConnection.sendVideoFrame(
                                    nalu = packet.data,
                                    isKeyFrame = packet.isKeyFrame,
                                    timestampMs = packet.timestampMs
                                )
                                frameCounter.incrementAndGet()
                            }
                            is StreamPacket.Audio -> {
                                rtmpConnection.sendAudioFrame(
                                    aacData = packet.data,
                                    timestampMs = packet.timestampMs
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error transmitting RTMP packet: ${e.message}")
                        _connectionState.value = RtmpConnectionState.ERROR
                    }
                }
            }
        }
    }

    private fun startTelemetryMonitor() {
        telemetryJob = CoroutineScope(Dispatchers.Default).launch {
            var lastBytes = 0L
            var lastFrames = 0L
            var elapsedSec = 0L

            while (isActive && isRunning.get()) {
                kotlinx.coroutines.delay(1000)
                elapsedSec++

                val currentBytes = rtmpConnection.bytesSent
                val currentFrames = frameCounter.get()
                val currentDropped = droppedFrames.get()

                val bytesPerSec = (currentBytes - lastBytes).coerceAtLeast(0)
                val currentBitrateKbps = ((bytesPerSec * 8) / 1000).toInt()
                val currentFps = (currentFrames - lastFrames).toInt().coerceAtLeast(0)

                lastBytes = currentBytes
                lastFrames = currentFrames

                val health = when {
                    !rtmpConnection.isAlive() -> StreamHealth.OFFLINE
                    currentDropped > 30 -> StreamHealth.POOR
                    currentBitrateKbps < (targetBitrateKbps * 0.5) -> StreamHealth.WARNING
                    currentBitrateKbps >= (targetBitrateKbps * 0.8) -> StreamHealth.EXCELLENT
                    else -> StreamHealth.GOOD
                }

                _telemetry.value = StreamTelemetry(
                    isLive = isRunning.get(),
                    elapsedSeconds = elapsedSec,
                    health = health,
                    currentBitrateKbps = currentBitrateKbps,
                    currentFps = currentFps,
                    droppedFrames = currentDropped.toInt()
                )
            }
        }
    }

    /**
     * Consumes SPS and PPS from MediaCodec MediaFormat.
     */
    fun onVideoFormatChanged(format: MediaFormat) {
        val spsBuf = format.getByteBuffer("csd-0")
        val ppsBuf = format.getByteBuffer("csd-1")

        if (spsBuf != null) {
            val sps = ByteArray(spsBuf.remaining())
            spsBuf.get(sps)
            spsBuf.rewind()
            this.spsBytes = cleanStartCode(sps)
        }

        if (ppsBuf != null) {
            val pps = ByteArray(ppsBuf.remaining())
            ppsBuf.get(pps)
            ppsBuf.rewind()
            this.ppsBytes = cleanStartCode(pps)
        }

        checkAndSendHeaders()
    }

    /**
     * Consumes AAC Audio Specific Config from MediaCodec format.
     */
    fun onAudioFormatChanged(format: MediaFormat) {
        val csd0 = format.getByteBuffer("csd-0")
        if (csd0 != null) {
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            Log.i(TAG, "Audio format configured: $sampleRate Hz, $channelCount channels")
            if (isRunning.get()) {
                rtmpConnection.sendAacSequenceHeader(sampleRate, channelCount)
            }
        }
    }

    private fun checkAndSendHeaders() {
        val sps = spsBytes
        val pps = ppsBytes
        if (sps != null && pps != null && !isHeaderSent && isRunning.get()) {
            rtmpConnection.sendAvcSequenceHeader(sps, pps)
            isHeaderSent = true
            Log.i(TAG, "Sent AVC SPS/PPS sequence headers to YouTube RTMP sink.")
        }
    }

    /**
     * Delivers an encoded H.264 video buffer to the live stream queue.
     */
    fun onVideoSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isRunning.get()) return

        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // Codec config / SPS / PPS buffer
            val configBytes = ByteArray(bufferInfo.size)
            buffer.position(bufferInfo.offset)
            buffer.get(configBytes)
            parseConfigBytes(configBytes)
            return
        }

        if (streamStartUs < 0) {
            streamStartUs = bufferInfo.presentationTimeUs
        }

        val timestampMs = ((bufferInfo.presentationTimeUs - streamStartUs) / 1000L).coerceAtLeast(0L)
        val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

        val naluData = ByteArray(bufferInfo.size)
        buffer.position(bufferInfo.offset)
        buffer.get(naluData)

        val cleanNalu = cleanStartCode(naluData)
        val packet = StreamPacket.Video(cleanNalu, isKeyFrame, timestampMs)

        if (!packetQueue.offer(packet)) {
            // Buffer full, drop non-keyframes if possible
            droppedFrames.incrementAndGet()
            Log.w(TAG, "Live streaming queue full! Dropped frame #$droppedFrames")
        }
    }

    /**
     * Delivers an encoded AAC audio buffer to the live stream queue.
     */
    fun onAudioSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isRunning.get()) return

        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            return
        }

        if (streamStartUs < 0) {
            streamStartUs = bufferInfo.presentationTimeUs
        }

        val timestampMs = ((bufferInfo.presentationTimeUs - streamStartUs) / 1000L).coerceAtLeast(0L)
        val audioData = ByteArray(bufferInfo.size)
        buffer.position(bufferInfo.offset)
        buffer.get(audioData)

        packetQueue.offer(StreamPacket.Audio(audioData, timestampMs))
    }

    private fun cleanStartCode(data: ByteArray): ByteArray {
        // Strip 00 00 00 01 or 00 00 01 start code if present
        if (data.size >= 4 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 0.toByte() && data[3] == 1.toByte()) {
            return data.copyOfRange(4, data.size)
        }
        if (data.size >= 3 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 1.toByte()) {
            return data.copyOfRange(3, data.size)
        }
        return data
    }

    private fun parseConfigBytes(data: ByteArray) {
        // Look for SPS / PPS boundary if combined in csd-0
        val spsStart = findStartCode(data, 0)
        if (spsStart != -1) {
            val ppsStart = findStartCode(data, spsStart + 4)
            if (ppsStart != -1) {
                spsBytes = cleanStartCode(data.copyOfRange(spsStart, ppsStart))
                ppsBytes = cleanStartCode(data.copyOfRange(ppsStart, data.size))
            } else {
                spsBytes = cleanStartCode(data)
            }
        }
        checkAndSendHeaders()
    }

    private fun findStartCode(data: ByteArray, startOffset: Int): Int {
        for (i in startOffset until data.size - 4) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) {
                return i
            }
        }
        return -1
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        _connectionState.value = RtmpConnectionState.DISCONNECTED

        senderJob?.cancel()
        telemetryJob?.cancel()
        packetQueue.clear()

        rtmpConnection.close()
        Log.i(TAG, "RtmpStreamSink stopped. Live stream concluded.")
    }

    fun isStreaming(): Boolean = isRunning.get() && rtmpConnection.isAlive()
}
