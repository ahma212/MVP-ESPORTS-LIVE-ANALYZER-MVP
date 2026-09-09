package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.RecordingState
import com.example.ui.components.EsportsCard
import com.example.ui.components.EsportsHeader
import com.example.ui.components.EsportsSectionTitle
import com.example.ui.components.FloatingPointerControlUI
import com.example.ui.permissions.InitialLaunchPermissionFlow
import com.example.ui.station.MvpStationScreen
import com.example.ui.theme.EsportsBackground
import com.example.ui.theme.EsportsCardSurface
import com.example.ui.theme.EsportsCyan
import com.example.ui.theme.EsportsGold
import com.example.ui.theme.EsportsGreen
import com.example.ui.theme.EsportsRed
import com.example.ui.theme.EsportsSurface
import com.example.ui.theme.EsportsSurfaceBorder
import com.example.ui.theme.EsportsSurfaceVariant
import com.example.ui.theme.EsportsTextMuted
import com.example.ui.theme.EsportsTextPrimary
import com.example.ui.theme.EsportsTextSecondary
import com.example.ui.theme.LiveOnAirRed
import com.example.ui.theme.RecordActiveAmber
import com.example.ui.youtube.YouTubeLiveScreen
import com.example.viewmodel.MvpStationViewModel
import com.example.viewmodel.YouTubeLiveViewModel

enum class MainDestination(val title: String, val icon: ImageVector) {
    MVP_STATION("MVP STATION", Icons.Default.SportsEsports),
    YOUTUBE_LIVE("YOUTUBE LIVE", Icons.Default.Sensors)
}

@Composable
fun MainScaffold(
    mvpStationViewModel: MvpStationViewModel,
    youTubeLiveViewModel: YouTubeLiveViewModel
) {
    val stationState by mvpStationViewModel.uiState.collectAsStateWithLifecycle()
    val youtubeState by youTubeLiveViewModel.uiState.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainDestination.MVP_STATION) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPermissionFlowManually by remember { mutableStateOf(false) }

    val isRecording = stationState.recordingState == RecordingState.RECORDING
    val isLive = youtubeState.telemetry.isLive

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(EsportsBackground),
        topBar = {
            EsportsHeader(
                isRecording = isRecording,
                isLive = isLive,
                onSettingsClick = { showSettingsDialog = true },
                onPermissionStatusClick = { showPermissionFlowManually = true },
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            EsportsBottomNavigation(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
                isRecording = isRecording,
                isLive = isLive,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        containerColor = EsportsBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                MainDestination.MVP_STATION -> {
                    MvpStationScreen(
                        viewModel = mvpStationViewModel,
                        uiState = stationState
                    )
                }
                MainDestination.YOUTUBE_LIVE -> {
                    YouTubeLiveScreen(
                        viewModel = youTubeLiveViewModel,
                        uiState = youtubeState
                    )
                }
            }

            if (stationState.floatingControlEnabled) {
                FloatingPointerControlUI(
                    stationState = stationState,
                    youtubeState = youtubeState,
                    onStartRecording = { /* Handled via MvpStationScreen permission flow or VM */ },
                    onPauseRecording = { mvpStationViewModel.pauseRecording() },
                    onResumeRecording = { mvpStationViewModel.resumeRecording() },
                    onStopRecording = { mvpStationViewModel.stopRecording() },
                    onStartLive = { youTubeLiveViewModel.startLiveBroadcastLifecycle() },
                    onEndLive = { youTubeLiveViewModel.endLiveBroadcastLifecycle() },
                    onToggleMic = { mvpStationViewModel.toggleMic() },
                    onMicVolumeChange = { mvpStationViewModel.setMicVolume(it) },
                    onToggleInternalAudio = { mvpStationViewModel.toggleInternalAudio() },
                    onInternalAudioVolumeChange = { mvpStationViewModel.setInternalAudioVolume(it) },
                    onToggleMusic = { mvpStationViewModel.toggleMusic() },
                    onMusicPlayPause = { mvpStationViewModel.toggleMusicPlayPause() },
                    onMusicVolumeChange = { mvpStationViewModel.setMusicVolume(it) },
                    onToggleOverlay = { mvpStationViewModel.toggleFacecam() },
                    onToggleBannerStrip = { mvpStationViewModel.toggleBannerStrip() },
                    onToggleFacecam = { mvpStationViewModel.toggleFacecam() },
                    onToggleWatermark = { mvpStationViewModel.toggleWatermark() },
                    onSendChat = { youTubeLiveViewModel.sendChatMessage(it) },
                    onSetResolution = { mvpStationViewModel.setResolution(it) }
                )
            }
        }
    }

    if (showSettingsDialog) {
        NativeEngineSettingsDialog(
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Initial App Launch Permission Flow (Microphone & Camera via Accompanist)
    InitialLaunchPermissionFlow(
        forceShow = showPermissionFlowManually,
        onFlowDismissed = { showPermissionFlowManually = false }
    )
}

@Composable
private fun EsportsBottomNavigation(
    currentTab: MainDestination,
    onTabSelected: (MainDestination) -> Unit,
    isRecording: Boolean,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(EsportsSurface)
            .border(width = 1.dp, color = EsportsSurfaceBorder)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MainDestination.values().forEach { destination ->
            val isSelected = currentTab == destination
            val hasBadge = (destination == MainDestination.MVP_STATION && isRecording) ||
                    (destination == MainDestination.YOUTUBE_LIVE && isLive)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) EsportsCyan.copy(alpha = 0.15f) else Color.Transparent
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) EsportsCyan else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onTabSelected(destination) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                        tint = if (isSelected) EsportsCyan else EsportsTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = destination.title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = if (isSelected) EsportsCyan else EsportsTextMuted
                    )

                    if (hasBadge) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (destination == MainDestination.YOUTUBE_LIVE) LiveOnAirRed else RecordActiveAmber
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeEngineSettingsDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EsportsCyan,
                    contentColor = Color(0xFF001A24)
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("DONE", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = EsportsCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STUDIO ENGINE ARCHITECTURE",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                        color = EsportsTextPrimary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = EsportsTextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArchitectureBadge(
                    title = "MediaProjection Ready",
                    desc = "Direct Android 14+ virtual display capture pipeline with token authorization.",
                    status = "READY"
                )
                ArchitectureBadge(
                    title = "MediaCodec Hardware DSP",
                    desc = "Hardware accelerated H.264 & HEVC AVC encoders configured for 120 FPS high refresh gaming.",
                    status = "OPTIMIZED"
                )
                ArchitectureBadge(
                    title = "Audio PlaybackCapture",
                    desc = "Internal audio routing via AudioPlaybackCaptureConfiguration (API 29+) with 48kHz audio buffer.",
                    status = "ACTIVE"
                )
                ArchitectureBadge(
                    title = "YouTube RTMP / RTMPS Live Pipeline",
                    desc = "Standard ingest URL and low-latency chunk stream configuration ready for RTMP protocol.",
                    status = "COMPLIANT"
                )
                ArchitectureBadge(
                    title = "System Floating Window",
                    desc = "Android WindowManager overlay with drag-and-drop controller ball.",
                    status = "ENABLED"
                )
            }
        },
        containerColor = EsportsCardSurface,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun ArchitectureBadge(
    title: String,
    desc: String,
    status: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(EsportsSurfaceVariant)
            .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EsportsTextPrimary
                )
                Text(
                    text = status,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = EsportsCyan
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 10.sp,
                color = EsportsTextSecondary
            )
        }
    }
}
