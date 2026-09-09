package com.example.engine.composition

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * High-performance OpenGL ES 2.0 / 3.0 utility suite for GPU-accelerated stream composition.
 */
object GlesUtils {
    private const val TAG = "GlesUtils"

    const val BYTES_PER_FLOAT = 4

    fun createFloatBuffer(coords: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(coords.size * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(coords)
                position(0)
            }
    }

    fun compileShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Failed to create GL shader of type $type")
            return 0
        }
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val errorLog = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "GL shader compilation error:\n$errorLog\nCode:\n$shaderCode")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
        val program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Failed to create GL program")
            return 0
        }
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val errorLog = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "GL program link error: $errorLog")
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    fun generateOesTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val texId = textures[0]
        GLES20.glBindTexture(36197 /* GL_TEXTURE_EXTERNAL_OES */, texId)
        GLES20.glTexParameteri(36197, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(36197, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(36197, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(36197, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(36197, 0)
        return texId
    }

    fun loadBitmapToTexture(bitmap: Bitmap, existingTexId: Int = 0): Int {
        val texId = if (existingTexId > 0) {
            existingTexId
        } else {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textures[0]
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return texId
    }

    fun deleteTexture(texId: Int) {
        if (texId > 0) {
            GLES20.glDeleteTextures(1, intArrayOf(texId), 0)
        }
    }

    fun checkGlError(operation: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "GL Error in $operation: $error (0x${Integer.toHexString(error)})")
        }
    }
}
