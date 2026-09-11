package com.example.engine.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import com.example.engine.control.FloatingControlBridge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.engine.control.FloatingControlBridge
import com.example.engine.core.CaptureOutputContract
import com.example.ui.components.FloatingPointerControlUI
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * FloatingControlService runs in the foreground and manages the system overlay
 * Floating Pointer and Floating Control Panel on top of any game or app.
 *
 * CRITICAL ISOLATION RULE:
 * Both the pointer and the control panel use WindowManager.LayoutParams.FLAG_SECURE.
 * SurfaceFlinger and MediaProjection automatically exclude them from:
 * - Local MP4 recordings
 * - YouTube Live Streams
 * - Exported Gallery videos
 */
class FloatingControlService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private var windowManager: WindowManager? = null
    private var composeOverlayView: ComposeView? = null
    private var isOverlayAttached = false

    companion object {
        const val ACTION_START_OVERLAY = "com.example.action.START_FLOATING_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.example.action.STOP_FLOATING_OVERLAY"
        private const val CHANNEL_ID = "mvp_floating_control_channel"
        private const val NOTIFICATION_ID = 9002

        fun startService(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                return
            }
            val intent = Intent(context, FloatingControlService::class.java).apply {
                action = ACTION_START_OVERLAY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FloatingControlService::class.java).apply {
                action = ACTION_STOP_OVERLAY
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        createNotificationChannel()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OVERLAY -> {
                val notification = buildNotification("Floating Control HUD Active (Control-Only)")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                attachOverlayWindow()
            }
            ACTION_STOP_OVERLAY -> {
                detachOverlayWindow()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun attachOverlayWindow() {
        if (isOverlayAttached || windowManager == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return

        try {
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                CaptureOutputContract.ISOLATED_CONTROL_WINDOW_FLAGS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 20
                y = 200
            }

            composeOverlayView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingControlService)
                setViewTreeSavedStateRegistryOwner(this@FloatingControlService)
                setViewTreeViewModelStoreOwner(this@FloatingControlService)
                setContent {
                    MyApplicationTheme {
                        val stationState by FloatingControlBridge.stationState.collectAsState()
                        val youtubeState by FloatingControlBridge.youtubeState.collectAsState()

                        FloatingPointerControlUI(
                            stationState = stationState,
                            youtubeState = youtubeState,
                            onStartRecording = { FloatingControlBridge.startRecording() },
                            onPauseRecording = { FloatingControlBridge.pauseRecording() },
                            onResumeRecording = { FloatingControlBridge.resumeRecording() },
                            onStopRecording = { FloatingControlBridge.stopRecording() },
                            onStartLive = { FloatingControlBridge.startLive() },
                            onEndLive = { FloatingControlBridge.endLive() },
                            onToggleMic = { FloatingControlBridge.toggleMic() },
                            onMicVolumeChange = { v -> FloatingControlBridge.setMicVolume(v) },
                            onToggleInternalAudio = { FloatingControlBridge.toggleInternalAudio() },
                            onInternalAudioVolumeChange = { v -> FloatingControlBridge.setInternalAudioVolume(v) },
                            onToggleMusic = { FloatingControlBridge.toggleMusic() },
                            onMusicPlayPause = { FloatingControlBridge.toggleMusicPlayPause() },
                            onMusicVolumeChange = { v -> FloatingControlBridge.setMusicVolume(v) },
                            onToggleMusicLoop = { FloatingControlBridge.toggleMusicLoop() },
                            onSelectMusic = {
                                FloatingControlBridge.requestSelectMusic()
                                val launch = Intent(this@FloatingControlService, com.example.MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    putExtra("mvp_action", "select_music")
                                }
                                startActivity(launch)
                            },
                            onSeekMusic = { fraction ->
    val durationMs =
        FloatingControlBridge.stationState.value.audioConfig.musicDurationMs

    if (durationMs > 0L) {
        FloatingControlBridge.seekMusic(
            (fraction * durationMs).toLong()
        )
    }
},
                            onToggleOverlay = { FloatingControlBridge.toggleOverlay() },
                            onToggleBannerStrip = { FloatingControlBridge.toggleBannerStrip() },
                            onToggleFacecam = { FloatingControlBridge.toggleFacecam() },
                            onToggleWatermark = { FloatingControlBridge.toggleWatermark() },
                     onSelectBreakVideo = {
                                FloatingControlBridge.requestSelectBreakVideo()
                                startActivity(
                                    Intent(this@FloatingControlService, com.example.MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                        putExtra("mvp_action", "select_break_video")
                                    }
                                )
                            },
                            onClearBreakVideo = { FloatingControlBridge.clearBreakVideo() },
                            onSelectOverlayPhoto = {
                                FloatingControlBridge.requestSelectOverlayPhoto()
                                startActivity(
                                    Intent(this@FloatingControlService, com.example.MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                        putExtra("mvp_action", "select_overlay_photo")
                                    }
                                )
                            },
                            onRemoveSelectedOverlay = { FloatingControlBridge.removeSelectedOverlay() },       onSendChat = { msg -> FloatingControlBridge.sendChat(msg) },
                            onSetResolution = { res -> FloatingControlBridge.setResolution(res) },
                            onSetFps = { fps -> FloatingControlBridge.setFps(fps) },
                            onSetBitrate = { br -> FloatingControlBridge.setBitrate(br) },
                            onSetBrightness = { v -> FloatingControlBridge.setBrightness(v) },
                            onSetContrast = { v -> FloatingControlBridge.setContrast(v) },
                            onSetSaturation = { v -> FloatingControlBridge.setSaturation(v) }
                        )
                    }
                }
            }

            windowManager?.addView(composeOverlayView, layoutParams)
            isOverlayAttached = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun detachOverlayWindow() {
        if (!isOverlayAttached || composeOverlayView == null) return
        try {
            windowManager?.removeView(composeOverlayView)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            composeOverlayView = null
            isOverlayAttached = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MVP Floating Control HUD",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating pointer and control HUD over gaming screen"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MVP ESPORTS LIVE - FLOATING HUD")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        detachOverlayWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
