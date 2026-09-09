package com.example.engine.composition

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.example.model.CompositionConfig
import com.example.model.CompositionElement
import com.example.model.CompositionElementType
import com.example.model.GameScaleMode
import com.example.model.GameVideoConfig
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GlesCompositionCompositor is the REAL GPU-accelerated composition engine.
 *
 * It combines:
 * 1. Native Game / Device Screen Capture (Zero-copy OES texture via VirtualDisplay)
 * 2. User-Authorized Visual Elements in Z-order:
 *    - Photos from gallery (with GPU crop, resize, move, opacity)
 *    - PNG Graphics & Logos
 *    - Memes & Reaction Stingers
 *    - Video Overlay Clips (loopable hardware OES video decode)
 *    - Top Sponsor Banners
 *    - Bottom Marquee Strips
 *    - Custom Esports Graphic Frames
 *
 * HARDWARE ZERO-OVERHEAD GUARANTEE:
 * - All blending, cropping, and transformations execute directly on GPU fragment shaders.
 * - ZERO CPU pixel copies.
 * - Control UI, pointers, chat drawers, and settings NEVER enter this OpenGL pipeline.
 */
class GlesCompositionCompositor(
    private val context: Context,
    private val outputWidth: Int,
    private val outputHeight: Int
) : SurfaceTexture.OnFrameAvailableListener {

    private val TAG = "GlesCompositionCompositor"

    private var glThread: HandlerThread? = null
    private var glHandler: Handler? = null

    // EGL Handles
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    // Input Screen Capture Texture & Surface (VirtualDisplay feeds into this)
    private var inputOesTexId: Int = 0
    private var inputSurfaceTexture: SurfaceTexture? = null
    var inputSurface: Surface? = null
        private set

    // GPU Shaders & Generators
    private var shaders: GlesShaderPrograms? = null
    private var textureGenerator: GraphicOverlayTextureGenerator? = null
    private var videoOverlayPlayer: VideoClipOverlayPlayer? = null

    // Active Composition Config
    @Volatile
    private var currentConfig: CompositionConfig = CompositionConfig()
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    // Full-screen Quad Buffers for Game Capture
    private val fullScreenVertexBuffer: FloatBuffer = GlesUtils.createFloatBuffer(
        floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        )
    )

    private val fullScreenTexCoordBuffer: FloatBuffer = GlesUtils.createFloatBuffer(
        floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
    )

    private val baseTexMatrix = FloatArray(16)
    private val identityMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    /**
     * Initializes the OpenGL EGL context bound to the Hardware Video Encoder's input surface.
     * Returns the Input Surface for the MediaProjection VirtualDisplay.
     */
    fun start(encoderInputSurface: Surface): Surface {
        stop()

        val thread = HandlerThread("GlesCompositorThread").apply { start() }
        val handler = Handler(thread.looper)
        this.glThread = thread
        this.glHandler = handler

        val syncLock = Object()
        var createdInputSurface: Surface? = null

        handler.post {
            synchronized(syncLock) {
                try {
                    initEgl(encoderInputSurface)
                    initGlResources()
                    createdInputSurface = inputSurface
                    isRunning.set(true)
                    Log.i(TAG, "GlesCompositionCompositor GPU engine initialized (${outputWidth}x${outputHeight})")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize GLES Compositor: ${e.message}", e)
                } finally {
                    syncLock.notifyAll()
                }
            }
        }

        synchronized(syncLock) {
            if (createdInputSurface == null) {
                syncLock.wait(3000)
            }
        }

        return this.inputSurface ?: throw IllegalStateException("Failed to create GLES input surface")
    }

    private fun initEgl(encoderInputSurface: Surface) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL14")
        }

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, configs.size, numConfigs, 0) || numConfigs[0] == 0) {
            throw RuntimeException("Unable to find suitable EGL config")
        }
        val eglConfig = configs[0]

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Failed to create EGL context")
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, encoderInputSurface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create EGL window surface")
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("Failed to make EGL context current")
        }
    }

    private fun initGlResources() {
        shaders = GlesShaderPrograms().apply { init() }
        textureGenerator = GraphicOverlayTextureGenerator(context)
        videoOverlayPlayer = VideoClipOverlayPlayer(context)

        inputOesTexId = GlesUtils.generateOesTexture()
        inputSurfaceTexture = SurfaceTexture(inputOesTexId).apply {
            setDefaultBufferSize(outputWidth, outputHeight)
            setOnFrameAvailableListener(this@GlesCompositionCompositor, glHandler)
        }
        inputSurface = Surface(inputSurfaceTexture)

        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        if (!isRunning.get() || isPaused.get()) return
        renderFrame()
    }

    private fun renderFrame() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) return

        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        // 1. Update Game / Screen Capture Texture
        val st = inputSurfaceTexture ?: return
        st.updateTexImage()
        st.getTransformMatrix(baseTexMatrix)
        val ptsNano = st.timestamp

        // 2. Clear Screen Viewport with configurable background color
        val bg = parseHexColor(currentConfig.gameVideoConfig.backgroundColorHex)
        GLES20.glClearColor(bg[0], bg[1], bg[2], 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // 3. Render Base Game / Screen Capture with Custom Crop, Zoom, Scale & Position
        renderGameVideo(currentConfig.gameVideoConfig)

        // 4. Render User-Authorized Composition Overlays in Ascending Z-Order
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val elements = currentConfig.elements.filter { it.isVisible }.sortedBy { it.zIndex }
        for (element in elements) {
            renderCompositionElement(element)
        }

        // 5. Present to Hardware Video Encoder Surface
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNano)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun renderGameVideo(config: GameVideoConfig) {
        if (!config.isVisible || config.opacity <= 0.01f) return

        // Compute base width and height based on chosen scaling mode
        val baseW: Float
        val baseH: Float
        when (config.scaleMode) {
            GameScaleMode.FIT -> {
                baseW = 1.0f
                baseH = 1.0f
            }
            GameScaleMode.FILL -> {
                baseW = 1.0f
                baseH = 1.0f
            }
            GameScaleMode.FULLSCREEN -> {
                baseW = 1.0f
                baseH = 1.0f
            }
            GameScaleMode.CUSTOM -> {
                baseW = config.widthPercent.coerceIn(0.1f, 2.5f)
                baseH = config.heightPercent.coerceIn(0.1f, 2.5f)
            }
        }

        // Calculate Normalized Device Coordinates (NDC: [-1.0, 1.0])
        val centerX = (config.xPercent * 2.0f) - 1.0f
        val centerY = 1.0f - (config.yPercent * 2.0f)
        val halfW = baseW * config.scale
        val halfH = baseH * config.scale

        val left = (centerX - halfW).coerceIn(-3.0f, 3.0f)
        val right = (centerX + halfW).coerceIn(-3.0f, 3.0f)
        val bottom = (centerY - halfH).coerceIn(-3.0f, 3.0f)
        val top = (centerY + halfH).coerceIn(-3.0f, 3.0f)

        val vertexBuffer = GlesUtils.createFloatBuffer(
            floatArrayOf(
                left,  bottom,
                right, bottom,
                left,  top,
                right, top
            )
        )

        // Calculate Crop Texture Coordinates (Independent 4-way trimming)
        val uMin = config.cropLeft.coerceIn(0f, 0.45f)
        val uMax = (1.0f - config.cropRight).coerceIn(0.55f, 1.0f)
        val vMin = config.cropTop.coerceIn(0f, 0.45f)
        val vMax = (1.0f - config.cropBottom).coerceIn(0.55f, 1.0f)

        val texCoordBuffer = GlesUtils.createFloatBuffer(
            floatArrayOf(
                uMin, vMin,
                uMax, vMin,
                uMin, vMax,
                uMax, vMax
            )
        )

        // Translation and Rotation MVP Matrix
        val mvpMatrix = FloatArray(16)
        Matrix.setIdentityM(mvpMatrix, 0)
        if (config.rotationDeg != 0f) {
            Matrix.translateM(mvpMatrix, 0, centerX, centerY, 0f)
            Matrix.rotateM(mvpMatrix, 0, config.rotationDeg, 0f, 0f, 1f)
            Matrix.translateM(mvpMatrix, 0, -centerX, -centerY, 0f)
        }

        if (config.opacity < 0.99f) {
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            GLES20.glDisable(GLES20.GL_BLEND)
        }

        val adjust = currentConfig.videoAdjustmentConfig
        shaders?.drawOesTexture(
            texId = inputOesTexId,
            vertexBuffer = vertexBuffer,
            texCoordBuffer = texCoordBuffer,
            texMatrix = baseTexMatrix,
            mvpMatrix = mvpMatrix,
            opacity = config.opacity,
            brightness = adjust.effectiveBrightness,
            contrast = adjust.effectiveContrast,
            saturation = adjust.effectiveSaturation
        )
    }

    private fun parseHexColor(
        colorHex: String,
        defaultRed: Float = 0.02f,
        defaultGreen: Float = 0.04f,
        defaultBlue: Float = 0.08f
    ): FloatArray {
        return try {
            val cleanHex = colorHex.trim().removePrefix("#")
            val colorInt = if (cleanHex.length == 8) {
                cleanHex.toLong(16).toInt()
            } else if (cleanHex.length == 6) {
                ("FF$cleanHex").toLong(16).toInt()
            } else {
                0
            }
            val r = ((colorInt shr 16) and 0xFF) / 255.0f
            val g = ((colorInt shr 8) and 0xFF) / 255.0f
            val b = (colorInt and 0xFF) / 255.0f
            floatArrayOf(r, g, b, 1.0f)
        } catch (_: Exception) {
            floatArrayOf(defaultRed, defaultGreen, defaultBlue, 1.0f)
        }
    }

    private fun renderCompositionElement(element: CompositionElement) {
        // Calculate Normalized Device Coordinates (NDC: [-1.0, 1.0])
        val centerX = (element.xPercent * 2.0f) - 1.0f
        val centerY = 1.0f - (element.yPercent * 2.0f)
        val halfW = (element.widthPercent * element.scale)
        val halfH = (element.heightPercent * element.scale)

        val left = (centerX - halfW).coerceIn(-1.5f, 1.5f)
        val right = (centerX + halfW).coerceIn(-1.5f, 1.5f)
        val bottom = (centerY - halfH).coerceIn(-1.5f, 1.5f)
        val top = (centerY + halfH).coerceIn(-1.5f, 1.5f)

        val vertexBuffer = GlesUtils.createFloatBuffer(
            floatArrayOf(
                left,  bottom,
                right, bottom,
                left,  top,
                right, top
            )
        )

        // Calculate Crop Texture Coordinates
        val uMin = element.cropLeft.coerceIn(0f, 0.49f)
        val uMax = (1.0f - element.cropRight).coerceIn(0.51f, 1.0f)
        val vMin = element.cropTop.coerceIn(0f, 0.49f)
        val vMax = (1.0f - element.cropBottom).coerceIn(0.51f, 1.0f)

        val texCoordBuffer = GlesUtils.createFloatBuffer(
            floatArrayOf(
                uMin, vMax,
                uMax, vMax,
                uMin, vMin,
                uMax, vMin
            )
        )

        // Rotation & Translation MVP Matrix
        val mvpMatrix = FloatArray(16)
        Matrix.setIdentityM(mvpMatrix, 0)
        if (element.rotationDeg != 0f) {
            Matrix.translateM(mvpMatrix, 0, centerX, centerY, 0f)
            Matrix.rotateM(mvpMatrix, 0, element.rotationDeg, 0f, 0f, 1f)
            Matrix.translateM(mvpMatrix, 0, -centerX, -centerY, 0f)
        }

        if (element.type == CompositionElementType.VIDEO) {
            val player = videoOverlayPlayer ?: return
            player.syncElement(element)
            val videoTexMatrix = player.updateTexImage()
            shaders?.drawOesTexture(
                texId = player.oesTexId,
                vertexBuffer = vertexBuffer,
                texCoordBuffer = texCoordBuffer,
                texMatrix = videoTexMatrix,
                mvpMatrix = mvpMatrix,
                opacity = element.opacity
            )
        } else {
            val texId = textureGenerator?.getOrCreateTexture(element) ?: 0
            if (texId > 0) {
                shaders?.drawRgbaTexture(
                    texId = texId,
                    vertexBuffer = vertexBuffer,
                    texCoordBuffer = texCoordBuffer,
                    mvpMatrix = mvpMatrix,
                    opacity = element.opacity
                )
            }
        }
    }

    /**
     * Updates active composition elements in real-time.
     */
    fun updateCompositionConfig(config: CompositionConfig) {
        this.currentConfig = config
        // Trigger re-render
        glHandler?.post {
            if (isRunning.get()) {
                renderFrame()
            }
        }
    }

    /**
     * Updates video color enhancements (Brightness, Contrast, Saturation) in real-time on the GPU.
     */
    fun updateVideoAdjustmentConfig(adjustmentConfig: com.example.model.VideoAdjustmentConfig) {
        this.currentConfig = this.currentConfig.copy(videoAdjustmentConfig = adjustmentConfig)
        glHandler?.post {
            if (isRunning.get()) {
                renderFrame()
            }
        }
    }

    fun pause() {
        isPaused.set(true)
        videoOverlayPlayer?.pause()
    }

    fun resume() {
        isPaused.set(false)
        videoOverlayPlayer?.resume()
    }

    fun stop() {
        isRunning.set(false)
        val handler = glHandler
        val thread = glThread

        handler?.post {
            videoOverlayPlayer?.release()
            videoOverlayPlayer = null

            textureGenerator?.release()
            textureGenerator = null

            shaders?.release()
            shaders = null

            if (inputOesTexId > 0) {
                GlesUtils.deleteTexture(inputOesTexId)
                inputOesTexId = 0
            }

            inputSurface?.release()
            inputSurface = null
            inputSurfaceTexture?.release()
            inputSurfaceTexture = null

            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    eglSurface = EGL14.EGL_NO_SURFACE
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                    eglContext = EGL14.EGL_NO_CONTEXT
                }
                EGL14.eglTerminate(eglDisplay)
                eglDisplay = EGL14.EGL_NO_DISPLAY
            }
        }

        thread?.quitSafely()
        glThread = null
        glHandler = null
    }

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
