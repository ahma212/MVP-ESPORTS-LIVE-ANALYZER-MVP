package com.example.ui.station

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.engine.control.FloatingControlBridge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ColorLutPreset
import com.example.model.FacecamShape
import com.example.model.MvpStationUiState
import com.example.model.PointerStyle
import com.example.model.RecordingState
import com.example.model.VideoAdjustmentConfig
import com.example.model.VideoCodec
import com.example.model.VideoFps
import com.example.model.VideoOrientation
import com.example.model.VideoResolution
import com.example.ui.theme.EsportsMagenta
import com.example.ui.components.EsportsCard
import com.example.ui.components.EsportsMetricPill
import com.example.ui.components.EsportsSectionTitle
import com.example.ui.components.EsportsSegmentedRow
import com.example.ui.components.EsportsSlider
import com.example.ui.components.EsportsToggleRow
import com.example.ui.theme.EsportsBackground
import com.example.ui.theme.EsportsCardSurface
import com.example.ui.theme.EsportsCyan
import com.example.ui.theme.EsportsGold
import com.example.ui.theme.EsportsGreen
import com.example.ui.theme.EsportsPurple
import com.example.ui.theme.EsportsRed
import com.example.ui.theme.EsportsSurface
import com.example.ui.theme.EsportsSurfaceBorder
import com.example.ui.theme.EsportsSurfaceVariant
import com.example.ui.theme.EsportsTextMuted
import com.example.ui.theme.EsportsTextPrimary
import com.example.ui.theme.EsportsTextSecondary
import com.example.ui.theme.RecordActiveAmber
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import java.io.File
import com.example.viewmodel.MvpStationViewModel

@Composable
fun MvpStationScreen(
    viewModel: MvpStationViewModel,
    uiState: MvpStationUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val mediaProjectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            viewModel.startNativeCapture(
                resultCode = result.resultCode,
                intentData = result.data!!,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
                densityDpi = metrics.densityDpi
            )
        } else {
            viewModel.clearRecordingError()
        }
}

    val musicPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectMusicTrack(context, uri)
        }
    }

    val breakVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setBreakVideo(uri)
        }
    }

    val overlayPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addOverlayPhoto(uri)
        }
    }

    val pendingCaptureAction by FloatingControlBridge.pendingCaptureAction.collectAsState()

    LaunchedEffect(pendingCaptureAction) {
        when (pendingCaptureAction) {
            FloatingControlBridge.PendingCaptureAction.START_RECORDING -> {
                FloatingControlBridge.clearPendingCaptureAction()
                val mgr = mediaProjectionManager
                if (mgr != null) {
                    screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
                } else {
                    viewModel.setRecordingError("MediaProjection service is unavailable.")
                }
            }
            FloatingControlBridge.PendingCaptureAction.SELECT_MUSIC -> {
                FloatingControlBridge.clearPendingCaptureAction()
                musicPickerLauncher.launch("audio/*")
            }
            FloatingControlBridge.PendingCaptureAction.SELECT_BREAK_VIDEO -> {
                FloatingControlBridge.clearPendingCaptureAction()
                breakVideoPickerLauncher.launch("video/*")
            }
            FloatingControlBridge.PendingCaptureAction.SELECT_OVERLAY_PHOTO -> {
                FloatingControlBridge.clearPendingCaptureAction()
                overlayPhotoPickerLauncher.launch("image/*")
            }
            else -> Unit
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EsportsBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Error Notification Banner
        if (uiState.recordingErrorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(EsportsRed.copy(alpha = 0.15f))
                    .border(1.dp, EsportsRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = EsportsRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.recordingErrorMessage,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "DISMISS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EsportsRed,
                        modifier = Modifier.clickable { viewModel.clearRecordingError() }
                    )
                }
            }
        }

        // Architecture Isolation & Pipeline Telemetry Card
        ArchitectureIsolationCard(uiState = uiState)

        // 1. Primary Screen Recording Cockpit
        RecordingCockpitCard(
            uiState = uiState,
            onStart = {
                if (mediaProjectionManager != null) {
                    screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
             } else {
    viewModel.setRecordingError(
        "MediaProjection service is unavailable."
    )
},
            onPause = { viewModel.pauseRecording() },
            onResume = { viewModel.resumeRecording() },
            onStop = { viewModel.stopRecording() }
        )

        // Storage & Saved File Notification
        if (uiState.lastRecordedFilePath != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(EsportsGreen.copy(alpha = 0.12f))
                    .border(1.dp, EsportsGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EsportsGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SAVED TO ANDROID GALLERY (MEDIASTORE)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = EsportsGreen
                            )
                        }
                        if (uiState.lastRecordedFileSizeMb > 0f) {
                            Text(
                                text = "${"%.1f".format(uiState.lastRecordedFileSizeMb)} MB",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = EsportsGold
                            )
                        }
                    }

                    Text(
                        text = uiState.lastRecordedFilePath,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = EsportsTextPrimary,
                        maxLines = 2
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val uri = uiState.lastRecordedUri?.let { Uri.parse(it) }
                                    ?: Uri.fromFile(File(uiState.lastRecordedFilePath))
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "video/mp4")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.w("MvpStationScreen", "No video player found: ${e.message}")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EsportsGreen,
                                contentColor = Color(0xFF001F0E)
                            )
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PLAY VIDEO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val uri = uiState.lastRecordedUri?.let { Uri.parse(it) }
                                    ?: Uri.fromFile(File(uiState.lastRecordedFilePath))
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Screen Recording"))
                                } catch (e: Exception) {
                                    Log.w("MvpStationScreen", "Share failed: ${e.message}")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EsportsSurfaceVariant,
                                contentColor = EsportsCyan
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SHARE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Video Quality & FPS Controls
        VideoQualityCard(
            uiState = uiState,
            onResolutionChanged = { viewModel.setResolution(it) },
            onFpsChanged = { viewModel.setFps(it) },
            onBitrateChanged = { viewModel.setBitrate(it) },
            onCodecChanged = { viewModel.setCodec(it) },
            onOrientationChanged = { viewModel.setOrientation(it) }
        )

        // 3. Audio Studio (Internal Game Sound + Mic + Music)
        AudioStudioCard(
            uiState = uiState,
            viewModel = viewModel
        )

        // 4. Real Output Visual Composition Studio (Game Video Transformations + Photos, PNGs, Memes, Videos, Banners, Bottom Strips)
        VisualCompositionStudioCard(
            compositionConfig = uiState.compositionConfig,
            onSelectLayer = { viewModel.selectCompositionLayer(it) },
            onSelectElement = { viewModel.selectCompositionElement(it) },
            onAddElement = { type, name, uri, title, sub ->
                viewModel.addCompositionElement(type, name, uri, title, sub)
            },
            onRemoveElement = { viewModel.removeCompositionElement(it) },
            onToggleVisibility = { viewModel.toggleElementVisibility(it) },
            onUpdatePosition = { id, x, y -> viewModel.updateElementPosition(id, x, y) },
            onUpdateSize = { id, w, h -> viewModel.updateElementSize(id, w, h) },
            onUpdateCrop = { id, l, t, r, b -> viewModel.updateElementCrop(id, l, t, r, b) },
            onUpdateScale = { id, s -> viewModel.updateElementScale(id, s) },
            onUpdateRotation = { id, r -> viewModel.updateElementRotation(id, r) },
            onUpdateOpacity = { id, o -> viewModel.updateElementOpacity(id, o) },
            onMoveLayerUp = { viewModel.moveElementLayerUp(it) },
            onMoveLayerDown = { viewModel.moveElementLayerDown(it) },
            onUpdateText = { id, t, st -> viewModel.updateElementText(id, t, st) },
            onUpdateColors = { id, ac, bg -> viewModel.updateElementColors(id, ac, bg) },
            onUpdateVideoLoop = { id, l -> viewModel.updateElementVideoLoop(id, l) },
            onSetContentUri = { id, uri -> viewModel.setElementContentUri(id, uri) },
            // Game Video Transformation callbacks
            onSetGameScaleMode = { viewModel.setGameScaleMode(it) },
            onUpdateGamePosition = { x, y -> viewModel.updateGamePosition(x, y) },
            onMoveGame = { dx, dy -> viewModel.moveGame(dx, dy) },
            onUpdateGameSize = { w, h -> viewModel.updateGameSize(w, h) },
            onUpdateGameScale = { viewModel.updateGameScale(it) },
            onUpdateGameCrop = { l, t, r, b -> viewModel.updateGameCrop(l, t, r, b) },
            onUpdateGameRotation = { viewModel.updateGameRotation(it) },
            onUpdateGameOpacity = { viewModel.updateGameOpacity(it) },
            onToggleGameVisibility = { viewModel.toggleGameVisibility() },
            onResetGameTransform = { viewModel.resetGameTransform() },
            onSetGameFitPreset = { viewModel.setGameFitPreset() },
            onSetGameFillPreset = { viewModel.setGameFillPreset() },
            onSetGameFullscreenPreset = { viewModel.setGameFullscreenPreset() },
            onSetGameBackgroundColor = { viewModel.setGameBackgroundColor(it) },
            onToggleGridOverlay = { viewModel.toggleGridOverlay() }
        )

        // 5. Overlay & Facecam Controls
        OverlayStudioCard(
            uiState = uiState,
            viewModel = viewModel
        )

        // 5. Esports Bottom Strip & Sponsor Banner Controls
        BannerStripCard(
            uiState = uiState,
            viewModel = viewModel
        )

        // 6. Video Adjustment & Game Color Filters (LUT)
        VideoEnhancementCard(
            uiState = uiState,
            viewModel = viewModel
        )

        // 7. Touch Pointer & Gaming Crosshair Controls
        PointerControlsCard(
            uiState = uiState,
            viewModel = viewModel
        )

        // 8. Gallery MediaStore & System Overlay Controls
        StorageSettingsCard(
            uiState = uiState,
            viewModel = viewModel
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun RecordingCockpitCard(
    uiState: MvpStationUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val isRecording = uiState.recordingState == RecordingState.RECORDING
    val isPaused = uiState.recordingState == RecordingState.PAUSED
    val isSaving = uiState.recordingState == RecordingState.SAVING

    val hours = uiState.recordingSeconds / 3600
    val minutes = (uiState.recordingSeconds % 3600) / 60
    val seconds = uiState.recordingSeconds % 60
    val timeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isRecording -> RecordActiveAmber
            isPaused -> EsportsGold
            isSaving -> EsportsCyan
            else -> EsportsSurfaceBorder
        }, label = "cockpitBorder"
    )

    EsportsCard(
        headerColor = when {
            isRecording -> RecordActiveAmber
            isPaused -> EsportsGold
            isSaving -> EsportsCyan
            else -> EsportsCyan
        },
        accentBorder = isRecording
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SCREEN RECORDING COCKPIT",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = EsportsTextPrimary
                )
                Text(
                    text = "Hardware-accelerated native MediaProjection encoder",
                    style = MaterialTheme.typography.bodySmall,
                    color = EsportsTextSecondary
                )
            }

            // State Pill
            Box(
                modifier = Modifier
                    .background(
                        when {
                            isRecording -> RecordActiveAmber.copy(alpha = 0.2f)
                            isPaused -> EsportsGold.copy(alpha = 0.2f)
                            isSaving -> EsportsCyan.copy(alpha = 0.2f)
                            else -> EsportsSurfaceVariant
                        },
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        1.dp,
                        when {
                            isRecording -> RecordActiveAmber
                            isPaused -> EsportsGold
                            isSaving -> EsportsCyan
                            else -> EsportsSurfaceBorder
                        },
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            color = EsportsCyan,
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = when {
                                isRecording -> RecordActiveAmber
                                isPaused -> EsportsGold
                                else -> EsportsTextMuted
                            },
                            modifier = Modifier.size(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isRecording -> "RECORDING"
                            isPaused -> "PAUSED"
                            isSaving -> "SAVING TO GALLERY..."
                            else -> "STANDBY"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isRecording -> RecordActiveAmber
                            isPaused -> EsportsGold
                            isSaving -> EsportsCyan
                            else -> EsportsTextMuted
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Timer & Telemetry HUD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF070B12))
                .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(8.dp))
                .padding(vertical = 14.dp, horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SESSION TIME",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EsportsTextMuted,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = timeFormatted,
                        fontSize = 28.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = if (isRecording) RecordActiveAmber else EsportsTextPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EsportsMetricPill(
                        label = "Quality",
                        value = uiState.recordingConfig.resolution.label.split(" ")[0],
                        color = EsportsCyan
                    )
                    EsportsMetricPill(
                        label = "FPS",
                        value = "${uiState.recordingConfig.fps.fpsValue}",
                        color = EsportsGold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isSaving) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EsportsSurfaceVariant,
                        disabledContainerColor = EsportsSurfaceVariant,
                        disabledContentColor = EsportsCyan
                    )
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = EsportsCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("FINALIZING MP4 & MEDIASTORE...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else if (!isRecording && !isPaused) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EsportsCyan,
                        contentColor = Color(0xFF001A24)
                    )
                ) {
                    Icon(imageVector = Icons.Default.FiberManualRecord, contentDescription = null, tint = Color(0xFF001A24))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("START SCREEN RECORDING", fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                }
            } else {
                if (isRecording) {
                    Button(
                        onClick = onPause,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EsportsSurfaceVariant,
                            contentColor = EsportsGold
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PAUSE", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onResume,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EsportsGold,
                            contentColor = Color(0xFF1F1200)
                        )
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RESUME", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EsportsRed,
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("STOP & SAVE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun VideoQualityCard(
    uiState: MvpStationUiState,
    onResolutionChanged: (VideoResolution) -> Unit,
    onFpsChanged: (VideoFps) -> Unit,
    onBitrateChanged: (Int) -> Unit,
    onCodecChanged: (VideoCodec) -> Unit,
    onOrientationChanged: (VideoOrientation) -> Unit
) {
    val bitratePresets = listOf(
        Pair(2, "2M Eco"),
        Pair(4, "4M SD"),
        Pair(8, "8M HD"),
        Pair(12, "12M FHD"),
        Pair(18, "18M 2K"),
        Pair(28, "28M Max")
    )

    EsportsCard {
        EsportsSectionTitle(
            title = "Video Quality & Framerate",
            icon = Icons.Default.Videocam,
            badgeText = "${uiState.recordingConfig.fps.fpsValue} FPS • ${uiState.recordingConfig.bitrateMbps} Mbps"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Resolution
        Text(
            text = "RESOLUTION",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = EsportsTextMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        EsportsSegmentedRow(
            items = VideoResolution.values().toList(),
            selectedItem = uiState.recordingConfig.resolution,
            labelProvider = { it.label },
            onItemSelected = onResolutionChanged
        )

        Spacer(modifier = Modifier.height(14.dp))

        // FPS
        Text(
            text = "FRAMERATE (FPS)",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = EsportsTextMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        EsportsSegmentedRow(
            items = VideoFps.values().toList(),
            selectedItem = uiState.recordingConfig.fps,
            labelProvider = { it.label },
            onItemSelected = onFpsChanged
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Bitrate Presets
        Text(
            text = "BITRATE PRESETS",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = EsportsTextMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        EsportsSegmentedRow(
            items = bitratePresets,
            selectedItem = bitratePresets.firstOrNull { it.first == uiState.recordingConfig.bitrateMbps } ?: bitratePresets[3],
            labelProvider = { it.second },
            onItemSelected = { onBitrateChanged(it.first) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Bitrate Slider
        EsportsSlider(
            title = "Fine-tune Target Bitrate",
            value = uiState.recordingConfig.bitrateMbps.toFloat(),
            onValueChange = { onBitrateChanged(it.toInt()) },
            valueRange = 2f..30f,
            valueLabel = "${uiState.recordingConfig.bitrateMbps} Mbps (CBR)",
            icon = Icons.Default.Speed
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Codec & Orientation
        Text(
            text = "VIDEO ENCODER CODEC",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = EsportsTextMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        EsportsSegmentedRow(
            items = VideoCodec.values().toList(),
            selectedItem = uiState.recordingConfig.codec,
            labelProvider = { it.name },
            onItemSelected = onCodecChanged
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "ORIENTATION LOCK",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = EsportsTextMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        EsportsSegmentedRow(
            items = VideoOrientation.values().toList(),
            selectedItem = uiState.recordingConfig.orientation,
            labelProvider = { it.label },
            onItemSelected = onOrientationChanged
        )
    }
}

@Composable
private fun AudioStudioCard(
    uiState: MvpStationUiState,
    viewModel: MvpStationViewModel
) {
    val audio = uiState.audioConfig
    val context = LocalContext.current

    // Local gallery picker for music button inside this card
    val musicPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectMusicTrack(context, uri)
        }
    }

    EsportsCard {
        EsportsSectionTitle(
            title = "Real-Time 3-Channel Audio Mixer",
            icon = Icons.Default.Headset,
            badgeText = "44.1KHZ PCM STEREO"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // --- MASTER BUS SECTION ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(EsportsSurfaceVariant.copy(alpha = 0.7f))
                .border(1.dp, EsportsCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (audio.isMasterMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Master Volume",
                            tint = if (audio.isMasterMuted) EsportsRed else EsportsCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MASTER MIX BUS",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                            color = EsportsTextPrimary
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Master Mute Button
                        Button(
                            onClick = { viewModel.toggleMasterMute() },
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (audio.isMasterMuted) EsportsRed else EsportsSurfaceVariant,
                                contentColor = if (audio.isMasterMuted) Color.White else EsportsTextSecondary
                            )
                        ) {
                            Text(
                                text = if (audio.isMasterMuted) "MUTED" else "MUTE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Master Peak Level VU Bar
                AudioLevelVuBar(
                    level = if (audio.isMasterMuted) 0f else audio.masterPeakLevel,
                    accentColor = EsportsCyan
                )

                // Master Volume Slider
                EsportsSlider(
                    title = "Master Output Level",
                    value = if (audio.isMasterMuted) 0f else audio.masterVolume,
                    onValueChange = { viewModel.setMasterVolume(it) },
                    valueLabel = "${(audio.masterVolume * 100).toInt()}%",
                    icon = Icons.Default.VolumeUp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- CHANNEL 1: INTERNAL GAME AUDIO ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(EsportsCardSurface)
                .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Game Audio",
                            tint = if (audio.internalAudioEnabled && !audio.internalAudioMuted) EsportsGreen else EsportsTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "1. INTERNAL GAME AUDIO",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = EsportsTextPrimary
                            )
                            Text(
                                text = "Direct Android 10+ capture (Zero mic bleed)",
                                style = MaterialTheme.typography.bodySmall,
                                color = EsportsTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Channel Mute Button
                        if (audio.internalAudioEnabled) {
                            Button(
                                onClick = { viewModel.toggleInternalAudioMute() },
                                modifier = Modifier.height(26.dp),
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (audio.internalAudioMuted) EsportsRed else EsportsSurfaceVariant,
                                    contentColor = if (audio.internalAudioMuted) Color.White else EsportsTextSecondary
                                )
                            ) {
                                Text(
                                    text = if (audio.internalAudioMuted) "MUTED" else "MUTE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Enable/Disable Switch
                        Button(
                            onClick = { viewModel.toggleInternalAudio() },
                            modifier = Modifier.height(26.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (audio.internalAudioEnabled) EsportsGreen.copy(alpha = 0.2f) else EsportsSurfaceVariant,
                                contentColor = if (audio.internalAudioEnabled) EsportsGreen else EsportsTextMuted
                            )
                        ) {
                            Text(
                                text = if (audio.internalAudioEnabled) "ON" else "OFF",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (audio.internalAudioEnabled) {
                    AudioLevelVuBar(
                        level = if (audio.internalAudioMuted) 0f else audio.internalPeakLevel,
                        accentColor = EsportsGreen
                    )

                    EsportsSlider(
                        title = "Game Sound Volume",
                        value = if (audio.internalAudioMuted) 0f else audio.internalAudioVolume,
                        onValueChange = { viewModel.setInternalAudioVolume(it) },
                        valueLabel = "${(audio.internalAudioVolume * 100).toInt()}%"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- CHANNEL 2: MICROPHONE COMMENTARY ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(EsportsCardSurface)
                .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (audio.micMuted || !audio.micEnabled) Icons.Default.VolumeOff else Icons.Default.Mic,
                            contentDescription = "Mic",
                            tint = if (audio.micEnabled && !audio.micMuted) EsportsCyan else EsportsTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "2. MICROPHONE COMMENTARY",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = EsportsTextPrimary
                            )
                            Text(
                                text = "Hardware DSP filter chain & noise rejection",
                                style = MaterialTheme.typography.bodySmall,
                                color = EsportsTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (audio.micEnabled) {
                            Button(
                                onClick = { viewModel.toggleMicMute() },
                                modifier = Modifier.height(26.dp),
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (audio.micMuted) EsportsRed else EsportsSurfaceVariant,
                                    contentColor = if (audio.micMuted) Color.White else EsportsTextSecondary
                                )
                            ) {
                                Text(
                                    text = if (audio.micMuted) "MUTED" else "MUTE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.toggleMic() },
                            modifier = Modifier.height(26.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (audio.micEnabled) EsportsCyan.copy(alpha = 0.2f) else EsportsSurfaceVariant,
                                contentColor = if (audio.micEnabled) EsportsCyan else EsportsTextMuted
                            )
                        ) {
                            Text(
                                text = if (audio.micEnabled) "ON" else "OFF",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (audio.micEnabled) {
                    AudioLevelVuBar(
                        level = if (audio.micMuted) 0f else audio.micPeakLevel,
                        accentColor = EsportsCyan
                    )

                    EsportsSlider(
                        title = "Mic Gain Level",
                        value = if (audio.micMuted) 0f else audio.micVolume,
                        onValueChange = { viewModel.setMicVolume(it) },
                        valueLabel = "${(audio.micVolume * 100).toInt()}%"
                    )

                    Text(
                        text = "CLEAR VOICE DSP FILTERS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = EsportsTextMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChipToggle(
                            label = "Noise Gate",
                            active = audio.noiseSuppression,
                            onClick = { viewModel.toggleNoiseSuppression() }
                        )
                        FilterChipToggle(
                            label = "Echo Cancel",
                            active = audio.echoCancellation,
                            onClick = { viewModel.toggleEchoCancellation() }
                        )
                        FilterChipToggle(
                            label = "Voice Clarity",
                            active = audio.voiceClarityBoost,
                            onClick = { viewModel.toggleVoiceClarity() }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- CHANNEL 3: MUSIC FROM GALLERY / DEVICE ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(EsportsCardSurface)
                .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music",
                            tint = if (audio.musicEnabled && !audio.musicMuted) EsportsPurple else EsportsTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "3. GALLERY BGM & MUSIC",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = EsportsTextPrimary
                            )
                            Text(
                                text = "Plays local songs with auto-ducking",
                                style = MaterialTheme.typography.bodySmall,
                                color = EsportsTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (audio.musicEnabled) {
                            Button(
                                onClick = { viewModel.toggleMusicMute() },
                                modifier = Modifier.height(26.dp),
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (audio.musicMuted) EsportsRed else EsportsSurfaceVariant,
                                    contentColor = if (audio.musicMuted) Color.White else EsportsTextSecondary
                                )
                            ) {
                                Text(
                                    text = if (audio.musicMuted) "MUTED" else "MUTE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.toggleMusic() },
                            modifier = Modifier.height(26.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (audio.musicEnabled) EsportsPurple.copy(alpha = 0.2f) else EsportsSurfaceVariant,
                                contentColor = if (audio.musicEnabled) EsportsPurple else EsportsTextMuted
                            )
                        ) {
                            Text(
                                text = if (audio.musicEnabled) "ON" else "OFF",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (audio.musicEnabled) {
                    // Track Picker & Info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(EsportsSurfaceVariant)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = audio.musicTrackTitle ?: "No audio track chosen",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (audio.musicTrackTitle != null) EsportsCyan else EsportsTextMuted,
                                maxLines = 1
                            )
                            if (audio.musicTrackArtist != null) {
                                Text(
                                    text = audio.musicTrackArtist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EsportsTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                musicPickerLauncher.launch("audio/*")
                            },
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EsportsCyan.copy(alpha = 0.15f),
                                contentColor = EsportsCyan
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "SELECT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Music Transport Controls (Play, Pause, Stop, Loop)
                    val isPlaying = audio.musicPlaybackState == com.example.engine.audio.MusicPlaybackState.PLAYING
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.toggleMusicPlayPause() },
                            modifier = Modifier.weight(1f).height(32.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlaying) EsportsGold else EsportsGreen,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPlaying) "PAUSE" else "PLAY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Button(
                            onClick = { viewModel.stopMusic() },
                            modifier = Modifier.weight(1f).height(32.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EsportsSurfaceVariant,
                                contentColor = EsportsRed
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "STOP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        // Loop Toggle Button
                        Button(
                            onClick = { viewModel.toggleMusicLoop() },
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (audio.musicLooping) EsportsCyan.copy(alpha = 0.2f) else EsportsSurfaceVariant,
                                contentColor = if (audio.musicLooping) EsportsCyan else EsportsTextMuted
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "Loop",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (audio.musicLooping) "LOOP ON" else "LOOP OFF",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Music VU Level Bar
                    AudioLevelVuBar(
                        level = if (audio.musicMuted) 0f else audio.musicPeakLevel,
                        accentColor = EsportsPurple
                    )

                    // Music Volume Slider
                    EsportsSlider(
                        title = "Music Volume Level",
                        value = if (audio.musicMuted) 0f else audio.musicVolume,
                        onValueChange = { viewModel.setMusicVolume(it) },
                        valueLabel = "${(audio.musicVolume * 100).toInt()}%"
                    )

                    // Auto-Ducking Controls
                    EsportsToggleRow(
                        title = "Auto-Ducking (Voice Priority)",
                        subtitle = "Automatically attenuates BGM when mic input is detected",
                        checked = audio.audioDucking,
                        onCheckedChange = { viewModel.toggleAudioDucking() }
                    )

                    if (audio.audioDucking) {
                        EsportsSlider(
                            title = "Ducking Attenuation Strength",
                            value = audio.duckingStrength,
                            onValueChange = { viewModel.setDuckingStrength(it) },
                            valueLabel = "${(audio.duckingStrength * 100).toInt()}%"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Architectural Isolation Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(EsportsSurfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = EsportsGreen,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "ISOLATED PIPELINE: Audio is mixed as PCM data. UI never enters video recordings or YouTube Live streams.",
                style = MaterialTheme.typography.bodySmall,
                color = EsportsTextSecondary,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun AudioLevelVuBar(
    level: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val clamped = level.coerceIn(0f, 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(EsportsSurfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = if (clamped < 0.02f) 0.02f else clamped)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.7f),
                            accentColor,
                            if (clamped > 0.85f) EsportsRed else accentColor
                        )
                    )
                )
        )
    }
}

@Composable
private fun FilterChipToggle(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) EsportsCyan.copy(alpha = 0.15f) else EsportsSurfaceVariant)
            .border(
                1.dp,
                if (active) EsportsCyan else EsportsSurfaceBorder,
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) EsportsCyan else EsportsTextMuted
        )
    }
}

@Composable
private fun OverlayStudioCard(
    uiState: MvpStationUiState,
    viewModel: MvpStationViewModel
) {
    val overlay = uiState.overlayConfig

    EsportsCard {
        EsportsSectionTitle(
            title = "Facecam & Graphic Overlays",
            icon = Icons.Default.PictureInPicture,
            badgeText = "CAMERA PIP"
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Facecam PiP
        EsportsToggleRow(
            title = "Front Camera Facecam",
            subtitle = "Picture-in-picture streamer facecam with custom frames",
            checked = overlay.facecamEnabled,
            onCheckedChange = { viewModel.toggleFacecam() }
        )

        if (overlay.facecamEnabled) {
            Text(
                text = "FACECAM FRAME SHAPE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = EsportsTextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            EsportsSegmentedRow(
                items = FacecamShape.values().toList(),
                selectedItem = overlay.facecamShape,
                labelProvider = { it.label },
                onItemSelected = { viewModel.setFacecamShape(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            EsportsToggleRow(
                title = "Neon Border Glow",
                subtitle = "Vibrant esports border glow around facecam frame",
                checked = overlay.facecamBorderNeon,
                onCheckedChange = { viewModel.toggleFacecamBorderNeon() }
            )

            EsportsSlider(
                title = "Facecam Opacity",
                value = overlay.facecamOpacity,
                onValueChange = { viewModel.setFacecamOpacity(it) },
                valueRange = 0.3f..1.0f,
                valueLabel = "${(overlay.facecamOpacity * 100).toInt()}%"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Watermark & Meme Stingers
        EsportsToggleRow(
            title = "Broadcast Watermark",
            subtitle = "Brand badge overlay in top corner",
            checked = overlay.watermarkEnabled,
            onCheckedChange = { viewModel.toggleWatermark() }
        )

        if (overlay.watermarkEnabled) {
            OutlinedTextField(
                value = overlay.watermarkText,
                onValueChange = { viewModel.setWatermarkText(it) },
                label = { Text("Watermark Text") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EsportsCyan,
                    unfocusedBorderColor = EsportsSurfaceBorder,
                    focusedTextColor = EsportsTextPrimary,
                    unfocusedTextColor = EsportsTextPrimary
                ),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        EsportsToggleRow(
            title = "Meme Stingers & Soundboard Overlay",
            subtitle = "On-screen quick reaction memes and tournament sound effects",
            checked = overlay.memeStingersEnabled,
            onCheckedChange = { viewModel.toggleMemeStingers() }
        )
    }
}

@Composable
private fun BannerStripCard(
    uiState: MvpStationUiState,
    viewModel: MvpStationViewModel
) {
    val banner = uiState.bannerStripConfig

    EsportsCard {
        EsportsSectionTitle(
            title = "Esports Bottom Strip & Ticker",
            icon = Icons.Default.ViewCarousel,
            badgeText = "LOWER THIRD"
        )

        Spacer(modifier = Modifier.height(8.dp))

        EsportsToggleRow(
            title = "Bottom Banner Strip",
            subtitle = "Broadcast lower-third ticker with social handles and sponsors",
            checked = banner.stripEnabled,
            onCheckedChange = { viewModel.toggleBannerStrip() }
        )

        if (banner.stripEnabled) {
            OutlinedTextField(
                value = banner.tickerText,
                onValueChange = { viewModel.setTickerText(it) },
                label = { Text("Live Ticker Announcement") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EsportsCyan,
                    unfocusedBorderColor = EsportsSurfaceBorder,
                    focusedTextColor = EsportsTextPrimary,
                    unfocusedTextColor = EsportsTextPrimary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = banner.socialHandle,
                    onValueChange = { viewModel.setSocialHandle(it) },
                    label = { Text("Social Handle") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EsportsCyan,
                        unfocusedBorderColor = EsportsSurfaceBorder,
                        focusedTextColor = EsportsTextPrimary,
                        unfocusedTextColor = EsportsTextPrimary
                    )
                )

                OutlinedTextField(
                    value = banner.sponsorName,
                    onValueChange = { viewModel.setSponsorName(it) },
                    label = { Text("Team Sponsor") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EsportsCyan,
                        unfocusedBorderColor = EsportsSurfaceBorder,
                        focusedTextColor = EsportsTextPrimary,
                        unfocusedTextColor = EsportsTextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            EsportsToggleRow(
                title = "Animate Marquee Scroll",
                subtitle = "Smooth horizontal ticker animation during broadcast",
                checked = banner.animateTicker,
                onCheckedChange = { viewModel.toggleAnimateTicker() }
            )

            // Live Preview Box of the Lower Third
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF04060B))
                    .border(1.dp, EsportsCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(EsportsGold, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = banner.socialHandle,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1000)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = banner.tickerText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = EsportsTextPrimary,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = banner.sponsorName,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = EsportsCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoEnhancementCard(
    uiState: MvpStationUiState,
    viewModel: MvpStationViewModel
) {
    val adjust = uiState.videoAdjustmentConfig

    EsportsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EsportsSectionTitle(
                title = "GPU Color Enhancements",
                icon = Icons.Default.Palette,
                badgeText = if (adjust.isEnabled) "GPU ACTIVE" else "BYPASSED"
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (adjust.isEnabled) "ON" else "OFF",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (adjust.isEnabled) EsportsCyan else EsportsTextMuted
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = adjust.isEnabled,
                    onCheckedChange = { viewModel.toggleColorEnhancementMaster() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EsportsCyan,
                        checkedTrackColor = EsportsCyan.copy(alpha = 0.3f),
                        uncheckedThumbColor = EsportsTextMuted,
                        uncheckedTrackColor = EsportsSurfaceVariant
                    ),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hardware Pipeline Status & Zero-Leak Badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF07111E))
                .border(1.dp, EsportsCyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = EsportsCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "HARDWARE SHADER • ZERO CPU COPY • ZERO UI LEAK",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = EsportsCyan,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(
                    text = if (adjust.isEnabled) "APPLIED TO OUTPUT" else "RAW PASSTHROUGH",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (adjust.isEnabled) EsportsGreen else EsportsTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ----------------------------------------------------
        // 1. BRIGHTNESS CHANNEL CONTROL
        // ----------------------------------------------------
        ColorChannelControlBlock(
            channelName = "Brightness (Shadow Elevation)",
            icon = Icons.Default.WbSunny,
            isEnabled = adjust.isEnabled && adjust.brightnessEnabled,
            onToggleEnabled = { viewModel.toggleBrightness() },
            currentValue = adjust.brightness,
            defaultValue = VideoAdjustmentConfig.DEFAULT_BRIGHTNESS,
            valueRange = VideoAdjustmentConfig.MIN_BRIGHTNESS..VideoAdjustmentConfig.MAX_BRIGHTNESS,
            valueFormatted = String.format("%+.2f", adjust.brightness),
            valueStatus = when {
                !adjust.brightnessEnabled || !adjust.isEnabled -> "Disabled (Neutral)"
                adjust.brightness > 0.02f -> "Shadows Boosted (${(adjust.brightness * 200).toInt()}%)"
                adjust.brightness < -0.02f -> "Dimmed (${(adjust.brightness * 200).toInt()}%)"
                else -> "Neutral (0.00)"
            },
            onValueChange = { viewModel.setBrightness(it) },
            onReset = { viewModel.resetBrightness() },
            quickChips = listOf(
                -0.20f to "-0.20 (Dim)",
                -0.10f to "-0.10",
                0.00f to "0.00 (Reset)",
                0.10f to "+0.10",
                0.20f to "+0.20 (Boost)"
            ),
            accentColor = EsportsGold
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ----------------------------------------------------
        // 2. CONTRAST CHANNEL CONTROL
        // ----------------------------------------------------
        ColorChannelControlBlock(
            channelName = "Contrast (Dynamic Range & Depth)",
            icon = Icons.Default.Contrast,
            isEnabled = adjust.isEnabled && adjust.contrastEnabled,
            onToggleEnabled = { viewModel.toggleContrast() },
            currentValue = adjust.contrast,
            defaultValue = VideoAdjustmentConfig.DEFAULT_CONTRAST,
            valueRange = VideoAdjustmentConfig.MIN_CONTRAST..VideoAdjustmentConfig.MAX_CONTRAST,
            valueFormatted = String.format("%.2fx", adjust.contrast),
            valueStatus = when {
                !adjust.contrastEnabled || !adjust.isEnabled -> "Disabled (1.00x)"
                adjust.contrast > 1.05f -> "High Contrast Depth (${((adjust.contrast - 1f) * 100).toInt()}% Boost)"
                adjust.contrast < 0.95f -> "Softened Dynamic Range"
                else -> "Standard 1:1 (1.00x)"
            },
            onValueChange = { viewModel.setContrast(it) },
            onReset = { viewModel.resetContrast() },
            quickChips = listOf(
                0.75f to "0.75x (Soft)",
                1.00f to "1.00x (Default)",
                1.25f to "1.25x (Punchy)",
                1.50f to "1.50x (Crisp)",
                1.75f to "1.75x (Intense)"
            ),
            accentColor = EsportsCyan
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ----------------------------------------------------
        // 3. SATURATION CHANNEL CONTROL
        // ----------------------------------------------------
        ColorChannelControlBlock(
            channelName = "Saturation (Rec.709 Vibrance)",
            icon = Icons.Default.Palette,
            isEnabled = adjust.isEnabled && adjust.saturationEnabled,
            onToggleEnabled = { viewModel.toggleSaturation() },
            currentValue = adjust.saturation,
            defaultValue = VideoAdjustmentConfig.DEFAULT_SATURATION,
            valueRange = VideoAdjustmentConfig.MIN_SATURATION..VideoAdjustmentConfig.MAX_SATURATION,
            valueFormatted = String.format("%.2fx", adjust.saturation),
            valueStatus = when {
                !adjust.saturationEnabled || !adjust.isEnabled -> "Disabled (1.00x)"
                adjust.saturation < 0.05f -> "Monochrome (B&W Grayscale)"
                adjust.saturation < 0.95f -> "Muted Palette"
                adjust.saturation > 1.05f -> "Esports Vibrant (${((adjust.saturation - 1f) * 100).toInt()}% Saturated)"
                else -> "Rec.709 Natural (1.00x)"
            },
            onValueChange = { viewModel.setSaturation(it) },
            onReset = { viewModel.resetSaturation() },
            quickChips = listOf(
                0.00f to "0.00x (B&W)",
                0.75f to "0.75x",
                1.00f to "1.00x (Default)",
                1.30f to "1.30x (Esports)",
                1.60f to "1.60x (Hyper)"
            ),
            accentColor = EsportsMagenta
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ----------------------------------------------------
        // ESPORTS COLOR PRESETS / LUT SELECTION
        // ----------------------------------------------------
        Text(
            text = "QUICK ESPORTS COLOR PRESETS",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = EsportsTextMuted
        )
        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ColorLutPreset.values().forEach { preset ->
                val isSelected = adjust.colorLutPreset == preset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) EsportsCyan.copy(alpha = 0.15f) else EsportsSurfaceVariant)
                        .border(
                            1.dp,
                            if (isSelected) EsportsCyan else EsportsSurfaceBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            viewModel.setColorLutPreset(preset)
                            when (preset) {
                                ColorLutPreset.NATURAL -> {
                                    viewModel.setBrightness(0.00f)
                                    viewModel.setContrast(1.00f)
                                    viewModel.setSaturation(1.00f)
                                }
                                ColorLutPreset.VIBRANT_ESPORTS -> {
                                    viewModel.setBrightness(0.05f)
                                    viewModel.setContrast(1.20f)
                                    viewModel.setSaturation(1.35f)
                                }
                                ColorLutPreset.HIGH_CONTRAST_FPS -> {
                                    viewModel.setBrightness(0.12f)
                                    viewModel.setContrast(1.30f)
                                    viewModel.setSaturation(1.10f)
                                }
                                ColorLutPreset.NIGHT_OPS -> {
                                    viewModel.setBrightness(-0.05f)
                                    viewModel.setContrast(1.10f)
                                    viewModel.setSaturation(0.90f)
                                }
                                ColorLutPreset.CYBERPUNK -> {
                                    viewModel.setBrightness(0.02f)
                                    viewModel.setContrast(1.30f)
                                    viewModel.setSaturation(1.50f)
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = preset.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) EsportsCyan else EsportsTextPrimary
                        )
                        Text(
                            text = preset.description,
                            fontSize = 10.sp,
                            color = EsportsTextSecondary
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EsportsCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Master Reset All Button
        Button(
            onClick = { viewModel.resetAllColorEnhancements() },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EsportsSurfaceVariant,
                contentColor = EsportsTextPrimary
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = EsportsCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RESET ALL COLOR ENHANCEMENTS TO DEFAULT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = EsportsTextPrimary
                )
            }
        }
    }
}

@Composable
private fun ColorChannelControlBlock(
    channelName: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isEnabled: Boolean,
    onToggleEnabled: () -> Unit,
    currentValue: Float,
    defaultValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatted: String,
    valueStatus: String,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    quickChips: List<Pair<Float, String>>,
    accentColor: Color
) {
    val isDefault = kotlin.math.abs(currentValue - defaultValue) < 0.005f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(EsportsSurfaceVariant.copy(alpha = 0.7f))
            .border(
                1.dp,
                if (isEnabled && !isDefault) accentColor.copy(alpha = 0.5f) else EsportsSurfaceBorder,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        // Channel Top Header: Icon + Name + Enable Toggle + Reset Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) accentColor.copy(alpha = 0.15f) else EsportsSurfaceBorder.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isEnabled) accentColor else EsportsTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = channelName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) EsportsTextPrimary else EsportsTextMuted
                    )
                    Text(
                        text = "Default: ${if (defaultValue == 0f) "0.00" else String.format("%.2fx", defaultValue)}",
                        fontSize = 10.sp,
                        color = EsportsTextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Reset Button
                IconButton(
                    onClick = onReset,
                    enabled = isEnabled && !isDefault,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset $channelName",
                        tint = if (isEnabled && !isDefault) EsportsCyan else EsportsTextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Toggle Enable / Disable
                IconButton(
                    onClick = onToggleEnabled,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (isEnabled) "Disable $channelName" else "Enable $channelName",
                        tint = if (isEnabled) accentColor else EsportsTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Readout & Contextual Status Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = valueStatus,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isEnabled && !isDefault) accentColor else EsportsTextSecondary
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isEnabled) accentColor.copy(alpha = 0.15f) else EsportsSurfaceBorder.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = valueFormatted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = if (isEnabled) accentColor else EsportsTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Precision Hardware Slider
        Slider(
            value = currentValue,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = isEnabled,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = EsportsSurfaceBorder,
                disabledThumbColor = EsportsTextMuted,
                disabledActiveTrackColor = EsportsSurfaceBorder,
                disabledInactiveTrackColor = EsportsSurfaceBorder.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Quick Stepping Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            quickChips.forEach { (targetVal, label) ->
                val isTargetActive = kotlin.math.abs(currentValue - targetVal) < 0.02f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isTargetActive) accentColor.copy(alpha = 0.25f) else Color(0xFF0B1422))
                        .border(
                            1.dp,
                            if (isTargetActive) accentColor else EsportsSurfaceBorder.copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable(enabled = isEnabled) { onValueChange(targetVal) }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        fontWeight = if (isTargetActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (!isEnabled) EsportsTextMuted else if (isTargetActive) accentColor else EsportsTextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PointerControlsCard(
    uiState: MvpStationUiState,
    viewModel: MvpStationViewModel
) {
    val pointer = uiState.pointerConfig

    EsportsCard {
        EsportsSectionTitle(
            title = "Touch Pointer & Crosshairs",
            icon = Icons.Default.TouchApp,
            badgeText = if (pointer.showTouches) "ENABLED" else "DISABLED"
        )

        Spacer(modifier = Modifier.height(8.dp))

        EsportsToggleRow(
            title = "Show Screen Touches & Pointers",
            subtitle = "Visual touch indicator for gameplay tutorials and competitive reviews",
            checked = pointer.showTouches,
            onCheckedChange = { viewModel.togglePointer() }
        )

        if (pointer.showTouches) {
            Text(
                text = "POINTER STYLE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = EsportsTextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            EsportsSegmentedRow(
                items = PointerStyle.values().toList(),
                selectedItem = pointer.pointerStyle,
                labelProvider = { it.label },
                onItemSelected = { viewModel.setPointerStyle(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            EsportsSlider(
                title = "Pointer Size",
                value = pointer.pointerSizeDp.toFloat(),
                onValueChange = { viewModel.setPointerSize(it.toInt()) },
                valueRange = 16f..56f,
                valueLabel = "${pointer.pointerSizeDp} dp"
            )
        }
    }
}

@Composable
private fun StorageSettingsCard(
    uiState: MvpStationUiState,
    viewModel: MvpStationViewModel
) {
    val storage = uiState.storageConfig

    EsportsCard {
        EsportsSectionTitle(
            title = "Save to Gallery & System Floating Ball",
            icon = Icons.Default.FolderSpecial,
            badgeText = "${storage.availableSpaceGb} GB FREE"
        )

        Spacer(modifier = Modifier.height(8.dp))

        EsportsToggleRow(
            title = "Auto-Save to Media Gallery",
            subtitle = "Saves directly into Android MediaStore under Movies/MVP_Recordings",
            checked = storage.autoSaveToGallery,
            onCheckedChange = { viewModel.toggleAutoSave() }
        )

        OutlinedTextField(
            value = storage.targetDirectory,
            onValueChange = { viewModel.setTargetDirectory(it) },
            label = { Text("Destination Directory") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EsportsCyan,
                unfocusedBorderColor = EsportsSurfaceBorder,
                focusedTextColor = EsportsTextPrimary,
                unfocusedTextColor = EsportsTextPrimary
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        EsportsToggleRow(
            title = "Floating Control Button (Overlay)",
            subtitle = "Quick floating button on screen to start/pause/stop while in full-screen games",
            checked = uiState.floatingControlEnabled,
            onCheckedChange = { viewModel.toggleFloatingControl() }
        )
    }
}

@Composable
private fun ArchitectureIsolationCard(
    uiState: MvpStationUiState
) {
    EsportsCard(
        headerColor = EsportsPurple
    ) {
        EsportsSectionTitle(
            title = "Hardware Encoder & Layer Isolation",
            icon = Icons.Default.Layers,
            badgeText = if (uiState.isHardwareEncoderActive) "ENCODER ACTIVE" else "STANDBY"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Telemetry Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(EsportsSurfaceVariant)
                    .padding(8.dp)
            ) {
                Column {
                    Text("HARDWARE CODEC", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EsportsTextMuted)
                    Text(
                        text = if (uiState.isHardwareEncoderActive) uiState.codecHardwareName else "MediaCodec H.264/AVC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EsportsCyan,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(EsportsSurfaceVariant)
                    .padding(8.dp)
            ) {
                Column {
                    Text("ENCODER CANVAS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EsportsTextMuted)
                    Text(
                        text = "${uiState.configuredWidth}x${uiState.configuredHeight}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EsportsGold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(EsportsSurfaceVariant)
                    .padding(8.dp)
            ) {
                Column {
                    Text("KEYFRAME INTERVAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EsportsTextMuted)
                    Text(
                        text = "${uiState.activeKeyframeIntervalSeconds}s GOP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EsportsGreen
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Strict Separation Matrix
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF060D18))
                .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = EsportsGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CONTROL LAYER SEPARATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = EsportsGreen
                        )
                    }
                    Text(
                        text = "FLAG_SECURE ISOLATED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = EsportsCyan
                    )
                }

                Text(
                    text = "Control chrome (floating pointer, action ball, pointer menu, chat HUD, audio sliders, settings) is physically excluded from screen capture and will NEVER appear in the recorded video or YouTube live stream.",
                    fontSize = 10.sp,
                    color = EsportsTextSecondary,
                    lineHeight = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Output Layer: Game Screen + Facecam PiP + Overlays + Final Audio Mix",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EsportsGold
                    )
                    if (uiState.isHardwareEncoderActive) {
                        Text(
                            text = "Frames: ${uiState.encodedFramesCount}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = RecordActiveAmber
                        )
                    }
                }
            }
        }
    }
}

