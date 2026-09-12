package com.example

import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModelProvider
import com.example.engine.service.FloatingControlService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.MainScaffold
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MvpStationViewModel
import com.example.viewmodel.YouTubeLiveViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handlePointerAction(intent)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val mvpStationViewModel: MvpStationViewModel = viewModel()
        val youTubeLiveViewModel: YouTubeLiveViewModel = viewModel()

        MainScaffold(
          mvpStationViewModel = mvpStationViewModel,
          youTubeLiveViewModel = youTubeLiveViewModel
        )
      }
    }
  }
override fun onResume() {
  super.onResume()

  val overlayPrefs = getSharedPreferences(
    "mvp_overlay_state",
    MODE_PRIVATE
  )

  val isWaitingForPermission =
    overlayPrefs.getBoolean("awaiting_overlay_permission", false)

  if (!isWaitingForPermission) {
    return
  }

  val permissionGranted =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        Settings.canDrawOverlays(this)

  if (permissionGranted) {
    overlayPrefs.edit()
      .putBoolean("awaiting_overlay_permission", false)
      .apply()

    try {
      FloatingControlService.startService(this)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handlePointerAction(intent)
  }

  private fun handlePointerAction(intent: Intent?) {
    when (intent?.getStringExtra("mvp_action")) {
      "start_recording" ->
        com.example.engine.control.FloatingControlBridge.requestStartRecordingPermission()
      "start_live" ->
        com.example.engine.control.FloatingControlBridge.requestStartLivePermission()
      "select_music" ->
        com.example.engine.control.FloatingControlBridge.requestSelectMusic()
      "select_break_video" ->
        com.example.engine.control.FloatingControlBridge.requestSelectBreakVideo()
      "select_overlay_photo" ->
        com.example.engine.control.FloatingControlBridge.requestSelectOverlayPhoto()
      "open_youtube" -> Unit
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

