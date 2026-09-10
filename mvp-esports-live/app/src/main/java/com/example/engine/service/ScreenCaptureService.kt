package com.example.engine.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.engine.capture.ScreenCaptureManager

/**
 * Owns the MediaProjection session and its foreground-service lifecycle.
 *
 * The Activity/Compose layer only requests user consent.
 * The ViewModel provides the fresh consent result to this service.
 * This service then:
 *
 * 1. Starts itself in the required foreground-service mode.
 * 2. Obtains the MediaProjection from the fresh consent result.
 * 3. Keeps that projection for one capture session only.
 * 4. Delegates VirtualDisplay creation/cleanup to ScreenCaptureManager.
 */
class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.example.action.START_CAPTURE"
        const val ACTION_STOP = "com.example.action.STOP_CAPTURE"

        private const val CHANNEL_ID = "mvp_capture_channel"
        private const val NOTIFICATION_ID = 9001

        private const val EXTRA_RESULT_CODE =
            "mvp_esports_media_projection_result_code"

        private const val EXTRA_RESULT_DATA =
            "mvp_esports_media_projection_result_data"

        fun startService(
            context: Context,
            resultCode: Int,
            resultData: Intent
        ) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, Intent(resultData))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(
                Intent(context, ScreenCaptureService::class.java)
            )
        }
    }

    interface CaptureListener {
        fun onProjectionReady()
        fun onCaptureStarted()
        fun onCaptureStopped()
        fun onCaptureError(message: String)
    }

    inner class LocalBinder : android.os.Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }

    private val binder = LocalBinder()

    private lateinit var captureManager: ScreenCaptureManager

    private var mediaProjection: MediaProjection? = null
private var isCaptureStarted = false
private var captureListener: CaptureListener? = null

private val mediaProjectionCallback =
    object : MediaProjection.Callback() {

        override fun onStop() {
    Log.w(
        "ScreenCaptureService",
        "MediaProjection was stopped by the system or user."
    )

    /*
     * ScreenCaptureManager owns the capture session and its
     * onCaptureStopped callback. Do not manually deliver a
     * second callback from the service.
     */
    try {
        captureManager.stopCapture()
    } catch (e: Exception) {
        Log.w(
            "ScreenCaptureService",
            "Failed to stop capture after MediaProjection.onStop(): ${e.message}"
        )
    }

    isCaptureStarted = false
    mediaProjection = null

    stopForegroundAndSelf()
}
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        captureManager = ScreenCaptureManager(applicationContext)

        captureManager.setCallback(
            object : ScreenCaptureManager.CaptureCallback {

                override fun onCaptureStarted() {
                    isCaptureStarted = true
                    captureListener?.onCaptureStarted()
                }

                override fun onCaptureStopped() {
                    isCaptureStarted = false
                    mediaProjection = null
                    captureListener?.onCaptureStopped()
                }

                override fun onCaptureError(message: String) {
    isCaptureStarted = false
    mediaProjection = null

    captureListener?.onCaptureError(message)

    stopForegroundAndSelf()
}
            }
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {
                if (mediaProjection != null || isCaptureStarted) {
                    return START_NOT_STICKY
                }

                val resultCode =
                    intent.getIntExtra(
                        EXTRA_RESULT_CODE,
                        android.app.Activity.RESULT_CANCELED
                    )

                val resultData = getProjectionResultData(intent)

                if (
                    resultCode != android.app.Activity.RESULT_OK ||
                    resultData == null
                ) {
                    captureListener?.onCaptureError(
                        "Screen capture permission result is invalid or missing."
                    )
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                }

                try {
                    /*
                     * Android requires the mediaProjection foreground
                     * service to be active before getMediaProjection().
                     */
                    val notification =
                        buildForegroundNotification(
                            "MVP Esports Live Recording Active"
                        )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        )
                    } else {
                        startForeground(
                            NOTIFICATION_ID,
                            notification
                        )
                    }

                    val projectionManager =
                        getSystemService(
                            Context.MEDIA_PROJECTION_SERVICE
                        ) as? MediaProjectionManager
                            ?: throw IllegalStateException(
                                "MediaProjection service unavailable."
                            )

                    val projection =
                        projectionManager.getMediaProjection(
                            resultCode,
                            resultData
                        )
                            ?: throw IllegalStateException(
                                "Unable to create MediaProjection."
                            )

                    mediaProjection = projection

mediaProjection.registerCallback(
    mediaProjectionCallback,
    android.os.Handler(mainLooper)
)

/*
 * The projection is fresh and has not yet been used
 * to create a VirtualDisplay. CaptureManager will
 * register its callback before createVirtualDisplay().
 */
captureListener?.onProjectionReady()

                } catch (e: Exception) {
                    mediaProjection = null

                    captureListener?.onCaptureError(
                        "Failed to initialize MediaProjection: ${
                            e.localizedMessage ?: "Unknown error"
                        }"
                    )

                    stopForegroundAndSelf()
                }
            }

            ACTION_STOP -> {
                stopCapture()
                stopForegroundAndSelf()
            }
        }

        return START_NOT_STICKY
    }

    fun setCaptureListener(listener: CaptureListener?) {
        captureListener = listener
    }

    fun isProjectionReady(): Boolean {
        return mediaProjection != null
    }

    fun getMediaProjection(): MediaProjection? {
        return mediaProjection
    }

    fun startCapture(
        targetSurface: Surface,
        width: Int,
        height: Int,
        densityDpi: Int
    ): Boolean {

        val projection = mediaProjection
            ?: return false

        if (isCaptureStarted) {
            return false
        }

        return captureManager.startCapture(
            projection = projection,
            targetSurface = targetSurface,
            width = width,
            height = height,
            densityDpi = densityDpi
        )
    }

    @Synchronized
fun stopCapture() {
    val projection = mediaProjection

    try {
        captureManager.stopCapture()
    } catch (e: Exception) {
        Log.w(
            "ScreenCaptureService",
            "Error stopping capture manager: ${e.message}"
        )
    }

    try {
        projection?.unregisterCallback(
            mediaProjectionCallback
        )
    } catch (e: Exception) {
        Log.w(
            "ScreenCaptureService",
            "Error unregistering MediaProjection callback: ${e.message}"
        )
    }

    isCaptureStarted = false
    mediaProjection = null
}

    private fun stopForegroundAndSelf() {
        stopForeground(true)
        stopSelf()
    }
      override fun onTaskRemoved(
    rootIntent: Intent?
) {
    /*
     * IMPORTANT:
     *
     * Removing/swiping away the app task must NOT stop an active
     * MediaProjection recording session.
     *
     * ScreenCaptureService is a started foreground service, so the
     * recording lifecycle is intentionally independent from the
     * Activity task lifecycle.
     *
     * The actual recording should only stop when:
     * 1. The user explicitly stops recording, or
     * 2. MediaProjection itself is revoked/stopped, or
     * 3. The service/process is actually terminated by the system.
     *
     * Do not call stopCapture(), stopForeground(), or stopSelf()
     * from onTaskRemoved().
     */
    Log.i(
        "ScreenCaptureService",
        "App task removed; keeping active screen capture running."
    )

    super.onTaskRemoved(rootIntent)
}
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MVP Esports Screen Recorder",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Notifies when active screen capture or esports broadcast is running"
            }

            val manager =
                getSystemService(NotificationManager::class.java)

            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(
        content: String
    ): Notification {

        val launchIntent =
            Intent(this, MainActivity::class.java)

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("MVP ESPORTS LIVE")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun getProjectionResultData(
        intent: Intent
    ): Intent? {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                EXTRA_RESULT_DATA,
                Intent::class.java
            )
        } else {
            intent.getParcelableExtra(
                EXTRA_RESULT_DATA
            )
        }
    }

    override fun onDestroy() {
    try {
        stopCapture()
    } catch (e: Exception) {
        Log.w(
            "ScreenCaptureService",
            "Error stopping capture during service destroy: ${e.message}"
        )
    }

    captureListener = null

    super.onDestroy()
}

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}