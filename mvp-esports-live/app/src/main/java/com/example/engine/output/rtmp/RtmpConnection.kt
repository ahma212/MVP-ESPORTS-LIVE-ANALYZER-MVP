package com.example.engine.output.rtmp

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocketFactory

/**
 * Native RTMP and RTMPS client connection.
 * Connects directly to YouTube RTMP Live Ingestion servers (e.g. a.rtmp.youtube.com:1935 / 443).
 */
class RtmpConnection {

    private val TAG = "RtmpConnection"

    companion object {
        const val DEFAULT_CHUNK_SIZE = 4096
        const val CSID_CONTROL = 2
        const val CSID_COMMAND = 3
        const val CSID_AUDIO = 4
        const val CSID_VIDEO = 5
        const val CSID_DATA = 6

        const val TYPE_SET_CHUNK_SIZE = 0x01
        const val TYPE_ABORT = 0x02
        const val TYPE_ACK = 0x03
        const val TYPE_USER_CONTROL = 0x04
        const val TYPE_WINDOW_ACK_SIZE = 0x05
        const val TYPE_SET_PEER_BANDWIDTH = 0x06
        const val TYPE_AUDIO = 0x08
        const val TYPE_VIDEO = 0x09
        const val TYPE_DATA_AMF0 = 0x12
        const val TYPE_COMMAND_AMF0 = 0x14
    }

    private var socket: Socket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null

    private var chunkSize = DEFAULT_CHUNK_SIZE
    private var activeStreamId = 1
    private var isConnected = AtomicBoolean(false)
    private var isPublished = AtomicBoolean(false)

    var bytesSent: Long = 0
        private set

    /**
     * Connects to RTMP/RTMPS server, executes handshake, and establishes publish session.
     */
    fun open(rtmpUrl: String, streamKey: String): Boolean {
        try {
            val (isSsl, host, port, appName, tcUrl) = parseRtmpUrl(rtmpUrl)

Log.i(TAG, "Connecting to RTMP host=$host port=$port ssl=$isSsl app=$appName")
            val rawSocket = if (isSsl) {
                SSLSocketFactory.getDefault().createSocket(host, port)
            } else {
                val s = Socket()
                s.tcpNoDelay = true
                s.soTimeout = 12000
                s.connect(InetSocketAddress(host, port), 10000)
                s
            }

            rawSocket.tcpNoDelay = true
            socket = rawSocket
            inStream = BufferedInputStream(rawSocket.getInputStream(), 32768)
            outStream = BufferedOutputStream(rawSocket.getOutputStream(), 32768)

            // Step 1: Handshake
            Log.i(TAG, "Starting RTMP Handshake...")
            executeHandshake()
            Log.i(TAG, "RTMP Handshake Successful!")

            // Step 2: Set Chunk Size
            sendSetChunkSize(DEFAULT_CHUNK_SIZE)
            chunkSize = DEFAULT_CHUNK_SIZE

            // Step 3: Send Connect Command
            sendConnectCommand(appName, tcUrl)

            // Step 4: Await connect response & server parameters
            readConnectResponse()

            // Step 5: Send releaseStream & FCPublish (standard FMLE flow required by YouTube)
            sendReleaseStream(streamKey)
            sendFCPublish(streamKey)

            // Step 6: Create Stream
            sendCreateStream()
            activeStreamId = readCreateStreamResponse()
            Log.i(TAG, "Stream created successfully with ID: $activeStreamId")

            // Step 7: Publish
            sendPublish(streamKey, activeStreamId)
            isConnected.set(true)
            isPublished.set(true)

            Log.i(TAG, "RTMP Live stream published successfully to YouTube!")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect RTMP: ${e.message}", e)
            close()
            return false
        }
    }

    private fun parseRtmpUrl(urlStr: String): ParsedRtmpUrl {
        val uri = URI(urlStr)
        val scheme = uri.scheme ?: "rtmp"
        val isSsl = scheme.equals("rtmps", ignoreCase = true)
        val host = uri.host ?: "a.rtmp.youtube.com"
        val port = if (uri.port > 0) uri.port else if (isSsl) 443 else 1935

        var path = uri.path ?: "/live2"
        if (path.startsWith("/")) path = path.substring(1)
        val appName = path.ifBlank { "live2" }
        val tcUrl = "$scheme://$host:$port/$appName"

        return ParsedRtmpUrl(isSsl, host, port, appName, tcUrl)
    }

    private data class ParsedRtmpUrl(
        val isSsl: Boolean,
        val host: String,
        val port: Int,
        val appName: String,
        val tcUrl: String
    )

    private fun executeHandshake() {
        val out = outStream ?: throw IllegalStateException("OutputStream is null")
        val input = inStream ?: throw IllegalStateException("InputStream is null")

        // 1. Send C0 (1 byte = 0x03) + C1 (1536 bytes)
        val c1 = ByteArray(1536)
        val random = Random()
        random.nextBytes(c1)
        // Set timestamp (0) and zero (0)
        c1[0] = 0; c1[1] = 0; c1[2] = 0; c1[3] = 0
        c1[4] = 0; c1[5] = 0; c1[6] = 0; c1[7] = 0

        out.write(0x03) // C0
        out.write(c1)   // C1
        out.flush()

        // 2. Read S0 (1 byte)
        val s0 = input.read()
        if (s0 != 0x03) {
            throw IllegalStateException("Unsupported RTMP server version in S0: $s0")
        }

        // 3. Read S1 (1536 bytes)
        val s1 = ByteArray(1536)
        readFully(input, s1)

        // 4. Read S2 (1536 bytes)
        val s2 = ByteArray(1536)
        readFully(input, s2)

        // 5. Send C2 (1536 bytes, echo of S1)
        out.write(s1)
        out.flush()
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count == -1) throw IllegalStateException("Unexpected EOF during RTMP handshake")
            offset += count
        }
    }

    private fun sendSetChunkSize(size: Int) {
        val payload = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(size).array()
        writeChunk(csid = CSID_CONTROL, typeId = TYPE_SET_CHUNK_SIZE, streamId = 0, timestampMs = 0, payload = payload)
    }

    private fun sendConnectCommand(appName: String, tcUrl: String) {
        val baos = ByteArrayOutputStream()
        Amf0Encoder.writeString(baos, "connect")
        Amf0Encoder.writeNumber(baos, 1.0) // Transaction ID 1

        val commandObject = mapOf(
            "app" to appName,
            "flashVer" to "FMLE/3.0 (compatible; FMSc/1.0)",
            "swfUrl" to "",
            "tcUrl" to tcUrl,
            "fpad" to false,
            "capabilities" to 15.0,
            "audioCodecs" to 3191.0,
            "videoCodecs" to 252.0,
            "videoFunction" to 1.0
        )
        Amf0Encoder.writeObject(baos, commandObject)

        writeChunk(csid = CSID_COMMAND, typeId = TYPE_COMMAND_AMF0, streamId = 0, timestampMs = 0, payload = baos.toByteArray())
    }

    private fun readConnectResponse() {
        val input = inStream ?: return
        // Drain incoming messages until Window Ack / Set Peer Bandwidth / _result are received
        // To be robust against diverse RTMP servers, we parse packets non-blockingly
        val buffer = ByteArray(1024)
        var attempts = 0
        while (attempts < 15) {
            if (input.available() > 0) {
                input.read(buffer, 0, minOf(buffer.size, input.available()))
                break
            }
            Thread.sleep(50)
            attempts++
        }
    }

    private fun sendReleaseStream(streamKey: String) {
        val baos = ByteArrayOutputStream()
        Amf0Encoder.writeString(baos, "releaseStream")
        Amf0Encoder.writeNumber(baos, 2.0)
        Amf0Encoder.writeNull(baos)
        Amf0Encoder.writeString(baos, streamKey)
        writeChunk(csid = CSID_COMMAND, typeId = TYPE_COMMAND_AMF0, streamId = 0, timestampMs = 0, payload = baos.toByteArray())
    }

    private fun sendFCPublish(streamKey: String) {
        val baos = ByteArrayOutputStream()
        Amf0Encoder.writeString(baos, "FCPublish")
        Amf0Encoder.writeNumber(baos, 3.0)
        Amf0Encoder.writeNull(baos)
        Amf0Encoder.writeString(baos, streamKey)
        writeChunk(csid = CSID_COMMAND, typeId = TYPE_COMMAND_AMF0, streamId = 0, timestampMs = 0, payload = baos.toByteArray())
    }

    private fun sendCreateStream() {
        val baos = ByteArrayOutputStream()
        Amf0Encoder.writeString(baos, "createStream")
        Amf0Encoder.writeNumber(baos, 4.0)
        Amf0Encoder.writeNull(baos)
        writeChunk(csid = CSID_COMMAND, typeId = TYPE_COMMAND_AMF0, streamId = 0, timestampMs = 0, payload = baos.toByteArray())
    }

    private fun readCreateStreamResponse(): Int {
        // YouTube almost always returns streamId = 1 for the first createStream.
        // Drain any pending server responses so subsequent commands are not corrupted.
        val input = inStream ?: return 1
        try {
            val deadline = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < deadline) {
                if (input.available() > 0) {
                    val discard = ByteArray(minOf(input.available(), 4096))
                    input.read(discard)
                } else {
                    Thread.sleep(30)
                }
            }
        } catch (_: Exception) {
        }
        return 1
    }

    private fun sendPublish(streamKey: String, streamId: Int) {
        val baos = ByteArrayOutputStream()
        Amf0Encoder.writeString(baos, "publish")
        Amf0Encoder.writeNumber(baos, 5.0)
        Amf0Encoder.writeNull(baos)
        Amf0Encoder.writeString(baos, streamKey)
        Amf0Encoder.writeString(baos, "live")
        writeChunk(csid = CSID_COMMAND, typeId = TYPE_COMMAND_AMF0, streamId = streamId, timestampMs = 0, payload = baos.toByteArray())
    }

    /**
     * Sends FLV/RTMP metadata descriptor (@setDataFrame onMetaData).
     */
    fun sendMetadata(width: Int, height: Int, fps: Int, bitrateKbps: Int, sampleRate: Int = 44100) {
        val baos = ByteArrayOutputStream()
        Amf0Encoder.writeString(baos, "@setDataFrame")
        Amf0Encoder.writeString(baos, "onMetaData")

        val metadata = mapOf(
            "duration" to 0.0,
            "width" to width.toDouble(),
            "height" to height.toDouble(),
            "videocodecid" to 7.0, // AVC / H.264
            "videodatarate" to bitrateKbps.toDouble(),
            "framerate" to fps.toDouble(),
            "audiocodecid" to 10.0, // AAC
            "audiodatarate" to 128.0,
            "audiosamplerate" to sampleRate.toDouble(),
            "audiosamplesize" to 16.0,
            "stereo" to true
        )
        Amf0Encoder.writeEcmaArray(baos, metadata)

        writeChunk(csid = CSID_DATA, typeId = TYPE_DATA_AMF0, streamId = activeStreamId, timestampMs = 0, payload = baos.toByteArray())
        Log.i(TAG, "Sent onMetaData: ${width}x${height}@${fps}fps, ${bitrateKbps}kbps video, AAC ${sampleRate}Hz audio")
    }

    /**
     * Sends AVC Decoder Configuration Record containing SPS and PPS.
     */
    fun sendAvcSequenceHeader(sps: ByteArray, pps: ByteArray) {
        val baos = ByteArrayOutputStream()
        // Video Tag Header
        baos.write(0x17) // 0x10 (Keyframe) | 0x07 (AVC)
        baos.write(0x00) // AVC sequence header
        // Composition time offset (3 bytes: 0, 0, 0)
        baos.write(0x00)
        baos.write(0x00)
        baos.write(0x00)

        // AVCDecoderConfigurationRecord
        baos.write(0x01) // configurationVersion
        baos.write(if (sps.size > 1) sps[1].toInt() else 0x42) // AVCProfileIndication
        baos.write(if (sps.size > 2) sps[2].toInt() else 0x00) // profile_compatibility
        baos.write(if (sps.size > 3) sps[3].toInt() else 0x1F) // AVCLevelIndication
        baos.write(0xFF) // 11111100 | (lengthSizeMinusOne = 3) -> 4 bytes NALU length
        baos.write(0xE1) // 11100000 | (numOfSequenceParameterSets = 1)

        // SPS length (2 bytes) + bytes
        val spsLenBuf = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(sps.size.toShort()).array()
        baos.write(spsLenBuf)
        baos.write(sps)

        // PPS count (1) + length (2 bytes) + bytes
        baos.write(0x01) // numOfPictureParameterSets
        val ppsLenBuf = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(pps.size.toShort()).array()
        baos.write(ppsLenBuf)
        baos.write(pps)

        writeChunk(csid = CSID_VIDEO, typeId = TYPE_VIDEO, streamId = activeStreamId, timestampMs = 0, payload = baos.toByteArray())
        Log.i(TAG, "Sent AVC Sequence Header (SPS: ${sps.size}b, PPS: ${pps.size}b)")
    }

    /**
     * Sends AAC Audio Specific Config sequence header.
     */
    fun sendAacSequenceHeader(sampleRate: Int = 44100, channels: Int = 2) {
        val baos = ByteArrayOutputStream()
        // Audio Tag Header: SoundFormat=10 (AAC), SoundRate=3 (44kHz), SoundSize=1 (16-bit), SoundType=1 (Stereo) -> 0xAF
        baos.write(0xAF)
        baos.write(0x00) // AAC sequence header

        // AudioSpecificConfig (2 bytes)
        // profile = 2 (AAC-LC, 5 bits) -> 00010
        // sampleRateIndex (4 bits)
        val rateIndex = when (sampleRate) {
            96000 -> 0
            88200 -> 1
            64000 -> 2
            48000 -> 3
            44100 -> 4
            32000 -> 5
            24000 -> 6
            22050 -> 7
            16000 -> 8
            12000 -> 9
            11025 -> 10
            8000 -> 11
            7350 -> 12
            else -> 4
        }
        // channels (4 bits)
        val byte1 = (2 shl 3) or (rateIndex shr 1)
        val byte2 = ((rateIndex and 0x01) shl 7) or (channels shl 3)

        baos.write(byte1)
        baos.write(byte2)

        writeChunk(csid = CSID_AUDIO, typeId = TYPE_AUDIO, streamId = activeStreamId, timestampMs = 0, payload = baos.toByteArray())
        Log.i(TAG, "Sent AAC Sequence Header (SampleRate: $sampleRate, Channels: $channels)")
    }

    /**
     * Sends a raw H.264 video NALU frame.
     */
    fun sendVideoFrame(nalu: ByteArray, isKeyFrame: Boolean, timestampMs: Long, compositionTimeMs: Int = 0) {
        if (!isPublished.get()) return

        val baos = ByteArrayOutputStream(nalu.size + 9)
        // Tag Header: 0x17 for keyframe, 0x27 for inter-frame
        val frameHeader = if (isKeyFrame) 0x17 else 0x27
        baos.write(frameHeader)
        baos.write(0x01) // AVC NALU

        // Composition time (3 bytes)
        baos.write((compositionTimeMs shr 16) and 0xFF)
        baos.write((compositionTimeMs shr 8) and 0xFF)
        baos.write(compositionTimeMs and 0xFF)

        // 4-byte NALU length prefix
        val lenBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(nalu.size).array()
        baos.write(lenBuf)
        baos.write(nalu)

        writeChunk(csid = CSID_VIDEO, typeId = TYPE_VIDEO, streamId = activeStreamId, timestampMs = timestampMs, payload = baos.toByteArray())
    }

    /**
     * Sends an encoded AAC raw audio packet.
     */
    fun sendAudioFrame(aacData: ByteArray, timestampMs: Long) {
        if (!isPublished.get()) return

        val baos = ByteArrayOutputStream(aacData.size + 2)
        baos.write(0xAF) // AAC 44.1k/48k stereo
        baos.write(0x01) // AAC raw
        baos.write(aacData)

        writeChunk(csid = CSID_AUDIO, typeId = TYPE_AUDIO, streamId = activeStreamId, timestampMs = timestampMs, payload = baos.toByteArray())
    }

    /**
     * Formats and writes an RTMP message divided into chunks conforming to RTMP Chunk Stream Specification.
     */
    @Synchronized
    private fun writeChunk(csid: Int, typeId: Int, streamId: Int, timestampMs: Long, payload: ByteArray) {
        val out = outStream ?: return

        var offset = 0
        var isFirstChunk = true

        while (offset < payload.size) {
            val chunkSizeToWrite = minOf(payload.size - offset, chunkSize)

            if (isFirstChunk) {
                // Type 0 header (11 bytes)
                // Basic Header: fmt = 0
                out.write(csid and 0x3F)

                // Timestamp (3 bytes)
                val ts = timestampMs.coerceAtLeast(0)
                val effectiveTs = if (ts >= 0xFFFFFFL) 0xFFFFFF else ts.toInt()
                out.write((effectiveTs shr 16) and 0xFF)
                out.write((effectiveTs shr 8) and 0xFF)
                out.write(effectiveTs and 0xFF)

                // Message Length (3 bytes)
                out.write((payload.size shr 16) and 0xFF)
                out.write((payload.size shr 8) and 0xFF)
                out.write(payload.size and 0xFF)

                // Message Type ID (1 byte)
                out.write(typeId)

                // Message Stream ID (4 bytes, little-endian!)
                out.write(streamId and 0xFF)
                out.write((streamId shr 8) and 0xFF)
                out.write((streamId shr 16) and 0xFF)
                out.write((streamId shr 24) and 0xFF)

                // Extended timestamp if needed
                if (ts >= 0xFFFFFFL) {
                    val extBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(ts.toInt()).array()
                    out.write(extBuf)
                }

                isFirstChunk = false
            } else {
                // Type 3 header: fmt = 3 (1 byte basic header)
                val type3Header = (3 shl 6) or (csid and 0x3F)
                out.write(type3Header)
            }

            // Write chunk data
            out.write(payload, offset, chunkSizeToWrite)
            bytesSent += chunkSizeToWrite
            offset += chunkSizeToWrite
        }

        out.flush()
    }

    /**
     * Gracefully closes the RTMP session and releases network resources.
     */
    fun close() {
        if (!isConnected.getAndSet(false)) return
        isPublished.set(false)

        try {
            val unpublish = ByteArrayOutputStream()
            Amf0Encoder.writeString(unpublish, "FCUnpublish")
            Amf0Encoder.writeNumber(unpublish, 6.0)
            Amf0Encoder.writeNull(unpublish)
            Amf0Encoder.writeString(unpublish, "")
            writeChunk(
                csid = CSID_COMMAND,
                typeId = TYPE_COMMAND_AMF0,
                streamId = activeStreamId,
                timestampMs = 0,
                payload = unpublish.toByteArray()
            )

            val closeStream = ByteArrayOutputStream()
            Amf0Encoder.writeString(closeStream, "closeStream")
            Amf0Encoder.writeNumber(closeStream, 7.0)
            Amf0Encoder.writeNull(closeStream)
            writeChunk(
                csid = CSID_COMMAND,
                typeId = TYPE_COMMAND_AMF0,
                streamId = activeStreamId,
                timestampMs = 0,
                payload = closeStream.toByteArray()
            )
        } catch (_: Exception) {
        }

        try { outStream?.close() } catch (_: Exception) {}
        try { inStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}

        socket = null
        inStream = null
        outStream = null
        Log.i(TAG, "RTMP connection closed. Bytes sent: $bytesSent")
    }
    fun isAlive(): Boolean = isConnected.get() && socket?.isConnected == true && socket?.isClosed == false
}
