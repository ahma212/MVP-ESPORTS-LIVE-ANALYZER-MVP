package com.example.engine.output.rtmp

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AMF0 (Action Message Format 0) encoder and decoder for RTMP command transactions.
 */
object Amf0Encoder {

    const val MARKER_NUMBER: Byte = 0x00
    const val MARKER_BOOLEAN: Byte = 0x01
    const val MARKER_STRING: Byte = 0x02
    const val MARKER_OBJECT: Byte = 0x03
    const val MARKER_NULL: Byte = 0x05
    const val MARKER_ECMA_ARRAY: Byte = 0x08
    const val MARKER_OBJECT_END: Byte = 0x09
    const val MARKER_STRICT_ARRAY: Byte = 0x0A

    fun writeNumber(out: OutputStream, value: Double) {
        out.write(MARKER_NUMBER.toInt())
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        buffer.putDouble(value)
        out.write(buffer.array())
    }

    fun writeBoolean(out: OutputStream, value: Boolean) {
        out.write(MARKER_BOOLEAN.toInt())
        out.write(if (value) 1 else 0)
    }

    fun writeString(out: OutputStream, value: String) {
        out.write(MARKER_STRING.toInt())
        val bytes = value.toByteArray(Charsets.UTF_8)
        val lenBuf = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
        lenBuf.putShort(bytes.size.toShort())
        out.write(lenBuf.array())
        out.write(bytes)
    }

    fun writePropertyKey(out: OutputStream, key: String) {
        val bytes = key.toByteArray(Charsets.UTF_8)
        val lenBuf = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
        lenBuf.putShort(bytes.size.toShort())
        out.write(lenBuf.array())
        out.write(bytes)
    }

    fun writeNull(out: OutputStream) {
        out.write(MARKER_NULL.toInt())
    }

    fun writeObject(out: OutputStream, properties: Map<String, Any?>) {
        out.write(MARKER_OBJECT.toInt())
        for ((key, value) in properties) {
            writePropertyKey(out, key)
            writeValue(out, value)
        }
        // Object end marker: 2 bytes 0x00 0x00 + 0x09
        out.write(0x00)
        out.write(0x00)
        out.write(MARKER_OBJECT_END.toInt())
    }

    fun writeEcmaArray(out: OutputStream, properties: Map<String, Any?>) {
        out.write(MARKER_ECMA_ARRAY.toInt())
        val countBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        countBuf.putInt(properties.size)
        out.write(countBuf.array())

        for ((key, value) in properties) {
            writePropertyKey(out, key)
            writeValue(out, value)
        }
        // Array end marker: 2 bytes 0x00 0x00 + 0x09
        out.write(0x00)
        out.write(0x00)
        out.write(MARKER_OBJECT_END.toInt())
    }

    fun writeValue(out: OutputStream, value: Any?) {
        when (value) {
            null -> writeNull(out)
            is Double -> writeNumber(out, value)
            is Number -> writeNumber(out, value.toDouble())
            is Boolean -> writeBoolean(out, value)
            is String -> writeString(out, value)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                writeObject(out, value as Map<String, Any?>)
            }
            else -> writeString(out, value.toString())
        }
    }

    /**
     * Read an AMF0 string from input stream.
     */
    fun readString(input: InputStream): String? {
        val marker = input.read()
        if (marker != MARKER_STRING.toInt()) return null
        val lenBuf = ByteArray(2)
        if (input.read(lenBuf) != 2) return null
        val len = ByteBuffer.wrap(lenBuf).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
        val bytes = ByteArray(len)
        var totalRead = 0
        while (totalRead < len) {
            val r = input.read(bytes, totalRead, len - totalRead)
            if (r == -1) break
            totalRead += r
        }
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * Read an AMF0 number from input stream.
     */
    fun readNumber(input: InputStream): Double? {
        val marker = input.read()
        if (marker != MARKER_NUMBER.toInt()) return null
        val numBuf = ByteArray(8)
        var totalRead = 0
        while (totalRead < 8) {
            val r = input.read(numBuf, totalRead, 8 - totalRead)
            if (r == -1) break
            totalRead += r
        }
        if (totalRead != 8) return null
        return ByteBuffer.wrap(numBuf).order(ByteOrder.BIG_ENDIAN).double
    }
}
