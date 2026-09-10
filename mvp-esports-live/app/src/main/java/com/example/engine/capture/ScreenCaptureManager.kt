package com.example.engine.capture

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.util.Collections
import java.util.WeakHashMap

/**
 * Owns one MediaProjection capture session at a time.
 *
 * A MediaProjection instance is allowed to create only one
 * VirtualDisplay capture session on Android 14+.
 *
 * Every new recording must therefore arrive with a fresh
 * MediaProjection instance from a fresh user-consent result.
 */
class ScreenCaptureManager(
    private val context: Context
) {

    private val TAG = "ScreenCaptureManager"

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var callback: CaptureCallback? = null

    private var stopCallbackDelivered = true

    /**
     * Tracks MediaProjection instances that have already been used
     * by this manager. Weak keys avoid keeping old projection objects
     * alive longer than necessary.
     */
    private val usedProjections =
        Collections.newSetFromMap(
            WeakHashMap<MediaProjection, Boolean>()
        )

    interface CaptureCallback {
        fun onCaptureStarted()
        fun onCaptureStopped()
        fun onCaptureError(message: String)
    }

    private val projectionCallback =
        object : MediaProjection.Callback() {

            override fun onStop() {
                super.onStop()

                Log.w(
                    TAG,
                    "MediaProjection session terminated by system or user."
                )

                releaseVirtualDisplay()

                mediaProjection = null

                notifyCaptureStopped()
            }
        }

    fun setCallback(
        callback: CaptureCallback?
    ) {
        this.callback = callback
    }

    /**
     * Starts one capture session with one fresh MediaProjection.
     */
    fun startCapture(
        projection: MediaProjection,
        targetSurface: Surface,
        width: Int,
        height: Int,
        densityDpi: Int
    ): Boolean {

        stopCapture()

        if (!targetSurface.isValid) {
            callback?.onCaptureError(
                "Encoder surface is invalid."
            )
            return false
        }

        synchronized(usedProjections) {
            if (usedProjections.contains(projection)) {
                callback?.onCaptureError(
                    "This MediaProjection session has already been used. " +
                        "Request fresh screen-capture permission."
                )
                return false
            }

            usedProjections.add(projection)
        }

        mediaProjection = projection
        stopCallbackDelivered = false

        return try {

            /*
             * Android requires the callback to be registered before
             * createVirtualDisplay().
             */
            projection.registerCallback(
                projectionCallback,
                mainHandler
            )

            val flags =
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR

            virtualDisplay =
                projection.createVirtualDisplay(
                    "MVP_ESPORTS_CAPTURE",
                    width,
                    height,
                    densityDpi,
                    flags,
                    targetSurface,
                    null,
                    mainHandler
                )

            Log.i(
                TAG,
                "VirtualDisplay created: " +
                    "${width}x${height} @ ${densityDpi}dpi"
            )

            callback?.onCaptureStarted()

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to create VirtualDisplay: ${e.message}",
                e
            )

            cleanupProjection()

            callback?.onCaptureError(
                "Failed to initialize screen capture: ${
                    e.localizedMessage ?: "Unknown error"
                }"
            )

            false
        }
    }

    /**
     * Stops this capture session and releases every resource.
     */
    fun stopCapture() {

        val projection = mediaProjection

        releaseVirtualDisplay()

        mediaProjection = null

        if (projection != null) {
            try {
                projection.unregisterCallback(
                    projectionCallback
                )
            } catch (_: Exception) {
            }

            try {
                projection.stop()
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Error stopping MediaProjection: ${e.message}"
                )
            }
        }

        notifyCaptureStopped()
    }

    private fun cleanupProjection() {

        releaseVirtualDisplay()

        val projection = mediaProjection

        mediaProjection = null

        if (projection != null) {
            try {
                projection.unregisterCallback(
                    projectionCallback
                )
            } catch (_: Exception) {
            }

            try {
                projection.stop()
            } catch (_: Exception) {
            }
        }
    }

    private fun releaseVirtualDisplay() {

        val display = virtualDisplay
            ?: return

        virtualDisplay = null

        try {
            display.surface = null
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Error clearing VirtualDisplay surface: ${e.message}"
            )
        }

        try {
            display.release()
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Error releasing VirtualDisplay: ${e.message}"
            )
        }
    }

    private fun notifyCaptureStopped() {

        if (stopCallbackDelivered) {
            return
        }

        stopCallbackDelivered = true

        callback?.onCaptureStopped()
    }

    fun isCapturing(): Boolean {
        return virtualDisplay != null
    }
}