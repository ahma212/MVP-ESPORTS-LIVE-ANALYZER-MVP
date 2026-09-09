package com.example.engine.composition

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import java.nio.FloatBuffer

/**
 * Encapsulates compiled OpenGL shader programs for OES external camera/screen/video textures
 * and standard 2D RGBA bitmap/graphics textures.
 */
class GlesShaderPrograms {

    private var oesProgramId: Int = 0
    private var rgbaProgramId: Int = 0

    // OES Program Uniforms / Attributes
    private var oesPosHandle = 0
    private var oesTexCoordHandle = 0
    private var oesMvpMatrixHandle = 0
    private var oesTexMatrixHandle = 0
    private var oesOpacityHandle = 0
    private var oesBrightnessHandle = 0
    private var oesContrastHandle = 0
    private var oesSaturationHandle = 0
    private var oesSamplerHandle = 0

    // RGBA Program Uniforms / Attributes
    private var rgbaPosHandle = 0
    private var rgbaTexCoordHandle = 0
    private var rgbaMvpMatrixHandle = 0
    private var rgbaOpacityHandle = 0
    private var rgbaSamplerHandle = 0

    private val identityMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    fun init() {
        initOesProgram()
        initRgbaProgram()
    }

    private fun initOesProgram() {
        val vertexShaderSource = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
        """.trimIndent()

        val fragmentShaderSource = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            uniform float uOpacity;
            uniform float uBrightness;
            uniform float uContrast;
            uniform float uSaturation;

            void main() {
                vec4 color = texture2D(sTexture, vTextureCoord);
                vec3 rgb = color.rgb;

                // 1. Hardware Accelerated Brightness Offset
                rgb += uBrightness;

                // 2. Hardware Accelerated Contrast Scaling around Mid-Point
                rgb = (rgb - 0.5) * uContrast + 0.5;

                // 3. Hardware Accelerated Saturation (Rec.709 Luminance Standard)
                float luminance = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
                rgb = mix(vec3(luminance), rgb, uSaturation);

                // Clamp to safe color space [0.0, 1.0]
                rgb = clamp(rgb, 0.0, 1.0);

                gl_FragColor = vec4(rgb, color.a * uOpacity);
            }
        """.trimIndent()

        val vs = GlesUtils.compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fs = GlesUtils.compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
        oesProgramId = GlesUtils.linkProgram(vs, fs)

        oesPosHandle = GLES20.glGetAttribLocation(oesProgramId, "aPosition")
        oesTexCoordHandle = GLES20.glGetAttribLocation(oesProgramId, "aTextureCoord")
        oesMvpMatrixHandle = GLES20.glGetUniformLocation(oesProgramId, "uMVPMatrix")
        oesTexMatrixHandle = GLES20.glGetUniformLocation(oesProgramId, "uTexMatrix")
        oesOpacityHandle = GLES20.glGetUniformLocation(oesProgramId, "uOpacity")
        oesBrightnessHandle = GLES20.glGetUniformLocation(oesProgramId, "uBrightness")
        oesContrastHandle = GLES20.glGetUniformLocation(oesProgramId, "uContrast")
        oesSaturationHandle = GLES20.glGetUniformLocation(oesProgramId, "uSaturation")
        oesSamplerHandle = GLES20.glGetUniformLocation(oesProgramId, "sTexture")
    }

    private fun initRgbaProgram() {
        val vertexShaderSource = """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoord;
            uniform mat4 uMVPMatrix;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = aTextureCoord;
            }
        """.trimIndent()

        val fragmentShaderSource = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            uniform float uOpacity;
            void main() {
                vec4 color = texture2D(sTexture, vTextureCoord);
                gl_FragColor = vec4(color.rgb, color.a * uOpacity);
            }
        """.trimIndent()

        val vs = GlesUtils.compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fs = GlesUtils.compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
        rgbaProgramId = GlesUtils.linkProgram(vs, fs)

        rgbaPosHandle = GLES20.glGetAttribLocation(rgbaProgramId, "aPosition")
        rgbaTexCoordHandle = GLES20.glGetAttribLocation(rgbaProgramId, "aTextureCoord")
        rgbaMvpMatrixHandle = GLES20.glGetUniformLocation(rgbaProgramId, "uMVPMatrix")
        rgbaOpacityHandle = GLES20.glGetUniformLocation(rgbaProgramId, "uOpacity")
        rgbaSamplerHandle = GLES20.glGetUniformLocation(rgbaProgramId, "sTexture")
    }

    /**
     * Draws an OES texture (Screen/Game capture or Video Clip) with GPU color enhancements.
     */
    fun drawOesTexture(
        texId: Int,
        vertexBuffer: FloatBuffer,
        texCoordBuffer: FloatBuffer,
        texMatrix: FloatArray? = null,
        mvpMatrix: FloatArray? = null,
        opacity: Float = 1.0f,
        brightness: Float = 0.0f,
        contrast: Float = 1.0f,
        saturation: Float = 1.0f
    ) {
        if (oesProgramId == 0) return

        GLES20.glUseProgram(oesProgramId)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(36197 /* GL_TEXTURE_EXTERNAL_OES */, texId)
        GLES20.glUniform1i(oesSamplerHandle, 0)

        GLES20.glUniform1f(oesOpacityHandle, opacity.coerceIn(0f, 1f))
        GLES20.glUniform1f(oesBrightnessHandle, brightness.coerceIn(-0.5f, 0.5f))
        GLES20.glUniform1f(oesContrastHandle, contrast.coerceIn(0.2f, 2.0f))
        GLES20.glUniform1f(oesSaturationHandle, saturation.coerceIn(0.0f, 2.5f))
        GLES20.glUniformMatrix4fv(oesMvpMatrixHandle, 1, false, mvpMatrix ?: identityMatrix, 0)
        GLES20.glUniformMatrix4fv(oesTexMatrixHandle, 1, false, texMatrix ?: identityMatrix, 0)

        GLES20.glEnableVertexAttribArray(oesPosHandle)
        GLES20.glVertexAttribPointer(oesPosHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)

        GLES20.glEnableVertexAttribArray(oesTexCoordHandle)
        GLES20.glVertexAttribPointer(oesTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(oesPosHandle)
        GLES20.glDisableVertexAttribArray(oesTexCoordHandle)
        GLES20.glBindTexture(36197, 0)
        GLES20.glUseProgram(0)
    }

    /**
     * Draws a 2D RGBA texture (Photo, PNG, Meme, Banner, Bottom Strip).
     */
    fun drawRgbaTexture(
        texId: Int,
        vertexBuffer: FloatBuffer,
        texCoordBuffer: FloatBuffer,
        mvpMatrix: FloatArray? = null,
        opacity: Float = 1.0f
    ) {
        if (rgbaProgramId == 0) return

        GLES20.glUseProgram(rgbaProgramId)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(rgbaSamplerHandle, 0)

        GLES20.glUniform1f(rgbaOpacityHandle, opacity.coerceIn(0f, 1f))
        GLES20.glUniformMatrix4fv(rgbaMvpMatrixHandle, 1, false, mvpMatrix ?: identityMatrix, 0)

        GLES20.glEnableVertexAttribArray(rgbaPosHandle)
        GLES20.glVertexAttribPointer(rgbaPosHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)

        GLES20.glEnableVertexAttribArray(rgbaTexCoordHandle)
        GLES20.glVertexAttribPointer(rgbaTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(rgbaPosHandle)
        GLES20.glDisableVertexAttribArray(rgbaTexCoordHandle)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    fun release() {
        if (oesProgramId != 0) {
            GLES20.glDeleteProgram(oesProgramId)
            oesProgramId = 0
        }
        if (rgbaProgramId != 0) {
            GLES20.glDeleteProgram(rgbaProgramId)
            rgbaProgramId = 0
        }
    }
}
