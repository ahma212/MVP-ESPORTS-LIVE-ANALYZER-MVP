package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LiveChatMessage
import com.example.model.MvpStationUiState
import com.example.model.RecordingState
import com.example.model.VideoResolution
import com.example.model.YouTubeLiveUiState
import com.example.ui.theme.EsportsBackground
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
import kotlin.math.roundToInt

enum class ControlHudTab(val label: String, val icon: ImageVector) {
    LIVE("LIVE", Icons.Default.Sensors),
    RECORD("REC", Icons.Default.RadioButtonChecked),
    AUDIO("AUDIO", Icons.Default.Mic),
    MUSIC("MUSIC", Icons.Default.MusicNote),
    OVERLAY("GFX", Icons.Default.Layers),
    CHAT("CHAT", Icons.Outlined.Chat),
    QUALITY("QUALITY", Icons.Outlined.HighQuality)
}

/**
 * FloatingPointerControlUI implements both:
 * 1. Small, movable, lightweight floating pointer bubble (Control-Only).
 * 2. Floating Control HUD Panel opened on pointer tap.
 *
 * CRITICAL RULE:
 * This UI is built specifically for the CONTROL SURFACE.
 * It is excluded from the hardware output encoder surface via WindowManager.LayoutParams.FLAG_SECURE
 * and offscreen composition pipeline separation.
 */
@Composable
fun FloatingPointerControlUI(
    stationState: MvpStationUiState,
    youtubeState: YouTubeLiveUiState,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onStartLive: () -> Unit,
    onEndLive: () -> Unit,
    onToggleMic: () -> Unit,
    onMicVolumeChange: (Float) -> Unit,
    onToggleInternalAudio: () -> Unit,
    onInternalAudioVolumeChange: (Float) -> Unit,
    onToggleMusic: () -> Unit,
    onMusicPlayPause: () -> Unit,
    onMusicVolumeChange: (Float) -> Unit,
    onToggleOverlay: () -> Unit,
    onToggleBannerStrip: () -> Unit,
    onToggleFacecam: () -> Unit,
    onToggleWatermark: () -> Unit,
    onSendChat: (String) -> Unit,
    onSetResolution: (VideoResolution) -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(20f) }
    var offsetY by remember { mutableFloatStateOf(160f) }
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(ControlHudTab.LIVE) }

    val isRecording = stationState.recordingState == RecordingState.RECORDING
    val isPaused = stationState.recordingState == RecordingState.PAUSED
    val isLive = youtubeState.telemetry.isLive

    // Pulse animation for active broadcast/recording
    val infiniteTransition = rememberInfiniteTransition(label = "pointer_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
    ) {
        Column(horizontalAlignment = Alignment.Start) {

            // 1. SMALL MOVABLE FLOATING POINTER BUBBLE
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                EsportsSurfaceVariant,
                                EsportsSurface
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.horizontalGradient(
                            colors = when {
                                isLive -> listOf(LiveOnAirRed, EsportsRed)
                                isRecording -> listOf(RecordActiveAmber, EsportsGold)
                                else -> listOf(EsportsCyan, Color(0xFF0088FF))
                            }
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Pulsing Status Dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .scale(if (isLive || isRecording) pulseScale else 1.0f)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isLive -> LiveOnAirRed
                                    isRecording -> RecordActiveAmber
                                    else -> EsportsCyan
                                }
                            )
                    )

                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "MVP Pointer",
                        tint = EsportsTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )

                    if (isLive) {
                        Text(
                            text = "LIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = LiveOnAirRed
                        )
                    } else if (isRecording) {
                        Text(
                            text = formatTime(stationState.recordingSeconds),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = RecordActiveAmber
                        )
                    } else {
                        Text(
                            text = "MVP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = EsportsCyan
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Move Pointer",
                        tint = EsportsTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. CONTROL HUD PANEL (EXPANDED ON TAP)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .width(340.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, EsportsCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    color = EsportsSurface,
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                    ) {
                        // Header Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isLive || isRecording) EsportsGreen else EsportsCyan)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "MVP CONTROL HUD",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = EsportsCyan,
                                    letterSpacing = 1.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "CONTROL-ONLY",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EsportsTextMuted,
                                    modifier = Modifier
                                        .background(
                                            EsportsSurfaceVariant,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { isExpanded = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close HUD",
                                        tint = EsportsTextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Navigation Tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EsportsBackground, RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ControlHudTab.values().forEach { tab ->
                                val isSelected = selectedTab == tab
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) EsportsCyan.copy(alpha = 0.2f) else Color.Transparent)
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) EsportsCyan else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { selectedTab = tab }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tab.label,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) EsportsCyan else EsportsTextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Tab Content
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            when (selectedTab) {
                                ControlHudTab.LIVE -> LiveTabContent(
                                    youtubeState = youtubeState,
                                    onStartLive = onStartLive,
                                    onEndLive = onEndLive
                                )
                                ControlHudTab.RECORD -> RecordTabContent(
                                    stationState = stationState,
                                    onStartRecording = onStartRecording,
                                    onPauseRecording = onPauseRecording,
                                    onResumeRecording = onResumeRecording,
                                    onStopRecording = onStopRecording
                                )
                                ControlHudTab.AUDIO -> AudioTabContent(
                                    stationState = stationState,
                                    onToggleMic = onToggleMic,
                                    onMicVolumeChange = onMicVolumeChange,
                                    onToggleInternalAudio = onToggleInternalAudio,
                                    onInternalAudioVolumeChange = onInternalAudioVolumeChange
                                )
                                ControlHudTab.MUSIC -> MusicTabContent(
                                    stationState = stationState,
                                    onToggleMusic = onToggleMusic,
                                    onMusicPlayPause = onMusicPlayPause,
                                    onMusicVolumeChange = onMusicVolumeChange
                                )
                                ControlHudTab.OVERLAY -> OverlayTabContent(
                                    stationState = stationState,
                                    onToggleOverlay = onToggleOverlay,
                                    onToggleBannerStrip = onToggleBannerStrip,
                                    onToggleFacecam = onToggleFacecam,
                                    onToggleWatermark = onToggleWatermark
                                )
                                ControlHudTab.CHAT -> ChatTabContent(
                                    youtubeState = youtubeState,
                                    onSendChat = onSendChat
                                )
                                ControlHudTab.QUALITY -> QualityTabContent(
                                    stationState = stationState,
                                    onSetResolution = onSetResolution
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTabContent(
    youtubeState: YouTubeLiveUiState,
    onStartLive: () -> Unit,
    onEndLive: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "YouTube Live Status",
                    fontSize = 11.sp,
                    color = EsportsTextMuted
                )
                Text(
                    text = if (youtubeState.telemetry.isLive) "LIVE ON AIR" else "OFF-AIR / READY",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (youtubeState.telemetry.isLive) LiveOnAirRed else EsportsCyan
                )
            }

            Box(
                modifier = Modifier
                    .background(
                        if (youtubeState.telemetry.isLive) LiveOnAirRed.copy(alpha = 0.2f) else EsportsSurfaceVariant,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = youtubeState.telemetry.health.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (youtubeState.telemetry.isLive) LiveOnAirRed else EsportsTextSecondary
                )
            }
        }

        if (youtubeState.streamConfig.title.isNotBlank()) {
            Text(
                text = "Title: ${youtubeState.streamConfig.title}",
                fontSize = 11.sp,
                color = EsportsTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStartLive,
                enabled = !youtubeState.telemetry.isLive && !youtubeState.isLoadingBroadcasts,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiveOnAirRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Go Live", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onEndLive,
                enabled = youtubeState.telemetry.isLive,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EsportsSurfaceVariant,
                    contentColor = EsportsRed
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "End Live", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RecordTabContent(
    stationState: MvpStationUiState,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val isRecording = stationState.recordingState == RecordingState.RECORDING
    val isPaused = stationState.recordingState == RecordingState.PAUSED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Local Recording Engine", fontSize = 11.sp, color = EsportsTextMuted)
                Text(
                    text = when (stationState.recordingState) {
                        RecordingState.RECORDING -> "RECORDING ACTIVE"
                        RecordingState.PAUSED -> "RECORDING PAUSED"
                        RecordingState.SAVING -> "SAVING MP4..."
                        else -> "READY TO RECORD"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (stationState.recordingState) {
                        RecordingState.RECORDING -> RecordActiveAmber
                        RecordingState.PAUSED -> EsportsGold
                        else -> EsportsCyan
                    }
                )
            }

            Text(
                text = formatTime(stationState.recordingSeconds),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = EsportsTextPrimary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!isRecording && !isPaused) {
                Button(
                    onClick = onStartRecording,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RecordActiveAmber,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RadioButtonChecked,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start Local Recording", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                if (isRecording) {
                    Button(
                        onClick = onPauseRecording,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EsportsSurfaceVariant,
                            contentColor = EsportsGold
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause", fontSize = 11.sp)
                    }
                } else if (isPaused) {
                    Button(
                        onClick = onResumeRecording,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EsportsGold,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume", fontSize = 11.sp)
                    }
                }

                Button(
                    onClick = onStopRecording,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EsportsRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop & Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AudioTabContent(
    stationState: MvpStationUiState,
    onToggleMic: () -> Unit,
    onMicVolumeChange: (Float) -> Unit,
    onToggleInternalAudio: () -> Unit,
    onInternalAudioVolumeChange: (Float) -> Unit
) {
    val audio = stationState.audioConfig
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Microphone Control
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (audio.micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = null,
                        tint = if (audio.micEnabled) EsportsCyan else EsportsTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Microphone Audio", fontSize = 11.sp, color = EsportsTextPrimary)
                }
                Switch(
                    checked = audio.micEnabled,
                    onCheckedChange = { onToggleMic() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = EsportsCyan,
                        checkedThumbColor = Color.Black
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }
            if (audio.micEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = audio.micVolume,
                        onValueChange = onMicVolumeChange,
                        valueRange = 0f..1.5f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = EsportsCyan,
                            activeTrackColor = EsportsCyan
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(audio.micVolume * 100).roundToInt()}%",
                        fontSize = 10.sp,
                        color = EsportsTextMuted
                    )
                }
            }
        }

        // Internal Audio Control
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = if (audio.internalAudioEnabled) EsportsGreen else EsportsTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Internal Game Audio", fontSize = 11.sp, color = EsportsTextPrimary)
                }
                Switch(
                    checked = audio.internalAudioEnabled,
                    onCheckedChange = { onToggleInternalAudio() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = EsportsGreen,
                        checkedThumbColor = Color.Black
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }
            if (audio.internalAudioEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = audio.internalAudioVolume,
                        onValueChange = onInternalAudioVolumeChange,
                        valueRange = 0f..1.5f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = EsportsGreen,
                            activeTrackColor = EsportsGreen
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(audio.internalAudioVolume * 100).roundToInt()}%",
                        fontSize = 10.sp,
                        color = EsportsTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicTabContent(
    stationState: MvpStationUiState,
    onToggleMusic: () -> Unit,
    onMusicPlayPause: () -> Unit,
    onMusicVolumeChange: (Float) -> Unit
) {
    val audio = stationState.audioConfig
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (audio.musicEnabled) EsportsGold else EsportsTextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("BGM Music Stream", fontSize = 11.sp, color = EsportsTextPrimary)
            }

            Switch(
                checked = audio.musicEnabled,
                onCheckedChange = { onToggleMusic() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = EsportsGold,
                    checkedThumbColor = Color.Black
                ),
                modifier = Modifier.scale(0.8f)
            )
        }

        if (audio.musicEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onMusicPlayPause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EsportsSurfaceVariant,
                        contentColor = EsportsGold
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = if (audio.musicPlaybackState == com.example.engine.audio.MusicPlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (audio.musicPlaybackState == com.example.engine.audio.MusicPlaybackState.PLAYING) "Pause Music" else "Play Music",
                        fontSize = 10.sp
                    )
                }

                Text(
                    text = audio.musicTrackTitle ?: "Esports Anthem BGM",
                    fontSize = 10.sp,
                    color = EsportsTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = audio.musicVolume,
                    onValueChange = onMusicVolumeChange,
                    valueRange = 0f..1.0f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = EsportsGold,
                        activeTrackColor = EsportsGold
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(audio.musicVolume * 100).roundToInt()}%",
                    fontSize = 10.sp,
                    color = EsportsTextMuted
                )
            }
        }
    }
}

@Composable
private fun OverlayTabContent(
    stationState: MvpStationUiState,
    onToggleOverlay: () -> Unit,
    onToggleBannerStrip: () -> Unit,
    onToggleFacecam: () -> Unit,
    onToggleWatermark: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Quick Broadcast Graphics", fontSize = 11.sp, color = EsportsTextMuted)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bottom Ticker Marquee", fontSize = 11.sp, color = EsportsTextPrimary)
            Switch(
                checked = stationState.bannerStripConfig.stripEnabled,
                onCheckedChange = { onToggleBannerStrip() },
                colors = SwitchDefaults.colors(checkedTrackColor = EsportsCyan),
                modifier = Modifier.scale(0.75f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Camera PiP Overlay", fontSize = 11.sp, color = EsportsTextPrimary)
            Switch(
                checked = stationState.overlayConfig.facecamEnabled,
                onCheckedChange = { onToggleFacecam() },
                colors = SwitchDefaults.colors(checkedTrackColor = EsportsCyan),
                modifier = Modifier.scale(0.75f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Watermark Logo", fontSize = 11.sp, color = EsportsTextPrimary)
            Switch(
                checked = stationState.overlayConfig.watermarkEnabled,
                onCheckedChange = { onToggleWatermark() },
                colors = SwitchDefaults.colors(checkedTrackColor = EsportsCyan),
                modifier = Modifier.scale(0.75f)
            )
        }
    }
}

@Composable
private fun ChatTabContent(
    youtubeState: YouTubeLiveUiState,
    onSendChat: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        // Read Chat Feed
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(EsportsBackground, RoundedCornerShape(8.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (youtubeState.liveChatMessages.isEmpty()) {
                item {
                    Text(
                        text = "No live chat messages yet.",
                        fontSize = 10.sp,
                        color = EsportsTextMuted,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                items(youtubeState.liveChatMessages.takeLast(15)) { msg ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${msg.author}:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (msg.isSuperChat) EsportsGold else EsportsCyan
                        )
                        Text(
                            text = msg.message,
                            fontSize = 10.sp,
                            color = EsportsTextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Send Chat Field
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Send quick live chat...", fontSize = 10.sp) },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EsportsCyan,
                    unfocusedBorderColor = EsportsSurfaceBorder
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputText.isNotBlank()) {
                        onSendChat(inputText)
                        inputText = ""
                    }
                })
            )

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendChat(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .background(EsportsCyan, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun QualityTabContent(
    stationState: MvpStationUiState,
    onSetResolution: (VideoResolution) -> Unit
) {
    val recConfig = stationState.recordingConfig
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Stream & Recording Output Quality", fontSize = 11.sp, color = EsportsTextMuted)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QualityBadge(title = "Resolution", value = recConfig.resolution.label, color = EsportsCyan)
            QualityBadge(title = "FPS", value = recConfig.fps.label, color = EsportsGreen)
            QualityBadge(title = "Bitrate", value = "${recConfig.bitrateMbps} Mbps", color = EsportsGold)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text("Select Preset Resolution", fontSize = 10.sp, color = EsportsTextSecondary)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(VideoResolution.RES_720P, VideoResolution.RES_1080P, VideoResolution.RES_1440P).forEach { res ->
                val isSelected = recConfig.resolution == res
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) EsportsCyan else EsportsSurfaceVariant)
                        .clickable { onSetResolution(res) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = res.label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.Black else EsportsTextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityBadge(title: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .background(EsportsSurfaceVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, fontSize = 8.sp, color = EsportsTextMuted)
        Text(text = value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
