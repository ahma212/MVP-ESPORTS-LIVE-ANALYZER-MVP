package com.example.engine.capture

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface

/**
 * ScreenCaptureManager encapsulates Android's native MediaProjection lifecycle
 * and VirtualDisplay creation to feed the hardware encoder surface.
 */
class ScreenCaptureManager(
    private val context: Context
) {
    private val TAG = "ScreenCaptureManager"

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    interface CaptureCallback {
        fun onCaptureStarted()
        fun onCaptureStopped()
        fun onCaptureError(message: String)
    }

    private var callback: CaptureCallback? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.w(TAG, "MediaProjection session terminated by system or user revoke.")
            releaseVirtualDisplay()
            callback?.onCaptureStopped()
        }
    }

    fun setCallback(callback: CaptureCallback) {
        this.callback = callback
    }

    /**
     * Attaches an authorized MediaProjection and creates the VirtualDisplay
     * rendering into the hardware encoder's input surface.
     */
    fun startCapture(
        projection: MediaProjection,
        targetSurface: Surface,
        width: Int,
        height: Int,
        densityDpi: Int = 320
    ): Boolean {
        stopCapture()

        this.mediaProjection = projection
        projection.registerCallback(projectionCallback, mainHandler)

        return try {
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC

            virtualDisplay = projection.createVirtualDisplay(
                "MVP_ESPORTS_CAPTURE",
                width,
                height,
                densityDpi,
                flags,
                targetSurface,
                null,
                mainHandler
            )

            Log.i(TAG, "VirtualDisplay successfully created: ${width}x${height} @ ${densityDpi}dpi")
            callback?.onCaptureStarted()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualDisplay: ${e.message}", e)
            callback?.onCaptureError("Failed to initialize screen display capture: ${e.message}")
            stopCapture()
            false
        }
    }

    /**
     * Cleanly releases VirtualDisplay and MediaProjection session.
     */
    fun stopCapture() {
        releaseVirtualDisplay()

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MediaProjection: ${e.message}")
        } finally {
            mediaProjection = null
        }
    }

    private fun releaseVirtualDisplay() {
        try {
            virtualDisplay?.surface = null
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing VirtualDisplay: ${e.message}")
        } finally {
            virtualDisplay = null
        }
    }

    fun isCapturing(): Boolean = virtualDisplay != null
}
