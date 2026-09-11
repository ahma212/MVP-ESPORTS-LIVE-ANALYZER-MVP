
package com.example.ui.youtube
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.IntentSender
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.engine.control.FloatingControlBridge
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.ChatConnectionStatus
import com.example.model.ChatFilterMode
import com.example.model.ColorLutPreset
import com.example.model.LatencyMode
import com.example.model.LiveBroadcastSummary
import com.example.model.LiveChatMessage
import com.example.model.LiveStreamConfig
import com.example.model.StreamHealth
import com.example.model.StreamPrivacy
import com.example.model.VideoAdjustmentConfig
import com.example.model.VideoFps
import com.example.model.VideoResolution
import com.example.model.YouTubeLiveUiState
import com.example.ui.components.EsportsCard
import com.example.ui.components.EsportsMetricPill
import com.example.ui.components.EsportsSectionTitle
import com.example.ui.components.EsportsSegmentedRow
import com.example.ui.theme.EsportsBackground
import com.example.ui.theme.EsportsCyan
import com.example.ui.theme.EsportsGold
import com.example.ui.theme.EsportsGreen
import com.example.ui.theme.EsportsRed
import com.example.ui.theme.EsportsSurfaceBorder
import com.example.ui.theme.EsportsSurfaceVariant
import com.example.ui.theme.EsportsTextMuted
import com.example.ui.theme.EsportsTextPrimary
import com.example.ui.theme.EsportsTextSecondary
import com.example.ui.theme.LiveOnAirRed
import com.example.ui.theme.YouTubeBrandRed
import com.example.viewmodel.YouTubeLiveViewModel

@Composable
fun YouTubeLiveScreen(
    viewModel: YouTubeLiveViewModel,
    uiState: YouTubeLiveUiState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val mediaProjectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        val windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = android.util.DisplayMetrics()

        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val projection = mediaProjectionManager?.getMediaProjection(
            result.resultCode,
            result.data!!
        )

        viewModel.startLiveStream(
    mediaProjection = projection,
    screenWidth = metrics.widthPixels,
    screenHeight = metrics.heightPixels,
    densityDpi = metrics.densityDpi,
    resultCode = result.resultCode,
    resultData = result.data
)
    } else {
        viewModel.startLiveStream()
    }
}
// Part 4B: Floating pointer requested Start Live
    // Photo picker launcher for custom thumbnail selection
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setThumbnailUri(uri)
        }
    }

    var showSelectBroadcastDialog by remember { mutableStateOf(false) }
    var showEditBroadcastDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.pendingConsentIntent) {
        val pendingIntent = uiState.pendingConsentIntent
            ?: return@LaunchedEffect

        val intentSender = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pendingIntent.getParcelableExtra(
                "mvp_esports_youtube_consent_intent_sender",
                IntentSender::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            pendingIntent.getParcelableExtra<IntentSender>(
                "mvp_esports_youtube_consent_intent_sender"
            )
        }

        if (intentSender != null) {
            try {
                consentLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            } catch (e: Exception) {
                viewModel.onConsentResult(
                    activityContext = context,
                    isSuccess = false
                )
            }
        } else {
            viewModel.onConsentResult(
                activityContext = context,
                isSuccess = false
            )
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
        // Output Composition Isolation Banner
        OutputIsolationGuaranteeBanner()

        // Error Banner
        if (uiState.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(EsportsRed.copy(alpha = 0.15f))
                    .border(1.dp, EsportsRed, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
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
                            text = uiState.errorMessage,
                            color = EsportsRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearErrorMessage() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = EsportsRed
                        )
                    }
                }
            }
        }

        // Auth Success Banner
        if (uiState.authSuccessMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(EsportsGreen.copy(alpha = 0.15f))
                    .border(1.dp, EsportsGreen, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EsportsGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.authSuccessMessage,
                            color = EsportsGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearErrorMessage() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = EsportsGreen
                        )
                    }
                }
            }
        }

        // 1. YouTube Live Master Control & Telemetry HUD
        LiveMasterControlCard(
            uiState = uiState,
            onStartLive = {
                if (mediaProjectionManager != null) {
                    screenCaptureLauncher.launch(
                        mediaProjectionManager.createScreenCaptureIntent()
                    )
                } else {
                    viewModel.startLiveStream()
                }
            },
            onStopLive = { viewModel.stopLiveStream() }
        )

        // 2. Real YouTube Live Broadcast Management Card
        LiveBroadcastManagementCard(
            uiState = uiState,
            onOpenSelectBroadcastDialog = {
                viewModel.loadUpcomingBroadcasts()
                showSelectBroadcastDialog = true
            },
            onOpenEditBroadcastDialog = {
                showEditBroadcastDialog = true
            },
            onCreateBroadcast = {
                viewModel.createRealYouTubeBroadcast()
            },
            onStartLifecycle = {
                viewModel.startLiveBroadcastLifecycle()
            },
            onEndLifecycle = {
                viewModel.endLiveBroadcastLifecycle()
            },
            onPickThumbnail = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            onOpenUrl = { url ->
                try {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(url)
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            }
        )
        // 3. Real YouTube Live Chat System Card
        LiveChatSectionCard(
            uiState = uiState,
            viewModel = viewModel
        )

        // 4. Real YouTube Account & Channel Connection Card
        YouTubeAccountCard(
            uiState = uiState,
            onConnect = { viewModel.openAuthDialog() },
            onDisconnect = { viewModel.disconnectYouTubeAccount() },
            onSwitchAccount = { viewModel.switchAccount(context) }
        )

        // 5. Hardware Video & Audio Quality Presets
        StreamQualityCard(
            uiState = uiState,
            viewModel = viewModel
        )

        // 6. Real GPU Output Color Enhancements (Brightness, Contrast, Saturation)
        LiveColorEnhancementCard(
            uiState = uiState,
            viewModel = viewModel
        )

        // 7. Live Broadcast Metadata & Details (Title, Description, Game Tag)
        StreamMetadataCard(
            uiState = uiState,
            viewModel = viewModel
        )

        // 8. Stream Thumbnail Studio (with Photo Picker)
        ThumbnailStudioCard(
            thumbnailUri = uiState.streamConfig.customThumbnailUri,
            onPickThumbnail = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemoveThumbnail = { viewModel.setThumbnailUri(null) }
        )

        // 9. Ingest Server & Stream Key Configuration
        RtmpIngestCard(
            uiState = uiState,
            viewModel = viewModel
        )

        // 10. Live Stream Quick Controls (Emergency Slate, Mute, Chat filters)
        StreamControlsCard(
            uiState = uiState,
            viewModel = viewModel
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Modal Authentication Dialog
    if (uiState.showAuthDialog) {
        YouTubeAuthDialog(
            viewModel = viewModel,
            uiState = uiState,
            onDismiss = { viewModel.closeAuthDialog() }
        )
    }

    // Modal Select Channel Broadcast Dialog
    if (showSelectBroadcastDialog) {
        SelectBroadcastDialog(
            uiState = uiState,
            onSelect = { summary ->
                viewModel.selectBroadcast(summary)
                showSelectBroadcastDialog = false
            },
            onRefresh = { viewModel.loadUpcomingBroadcasts() },
            onDismiss = { showSelectBroadcastDialog = false }
        )
    }

    // Modal Edit Broadcast Details Dialog
    if (showEditBroadcastDialog) {
        EditBroadcastDialog(
            currentTitle = uiState.streamConfig.title,
            currentDescription = uiState.streamConfig.description,
            currentPrivacy = uiState.streamConfig.privacy,
            isUpdating = uiState.isUpdatingBroadcast,
            onSave = { title, desc, privacy ->
                viewModel.updateCurrentBroadcast(title, desc, privacy)
                showEditBroadcastDialog = false
            },
            onDismiss = { showEditBroadcastDialog = false }
        )
    }
}

@Composable
private fun OutputIsolationGuaranteeBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF04060B))
            .border(1.dp, EsportsCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = EsportsCyan,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = "OUTPUT COMPOSITION ENGINE: CONTROL ISOLATION ACTIVE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = EsportsCyan,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Control UI, chat drawers, floating buttons, settings, and auth dialogs are physically isolated and will NEVER appear in the recorded or streamed video.",
                    fontSize = 10.sp,
                    color = EsportsTextSecondary,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun LiveMasterControlCard(
    uiState: YouTubeLiveUiState,
    onStartLive: () -> Unit,
    onStopLive: () -> Unit
) {
    val isLive = uiState.telemetry.isLive
    val telemetry = uiState.telemetry

    val hours = telemetry.elapsedSeconds / 3600
    val minutes = (telemetry.elapsedSeconds % 3600) / 60
    val seconds = telemetry.elapsedSeconds % 60
    val liveTimeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    EsportsCard(
        headerColor = if (isLive) LiveOnAirRed else YouTubeBrandRed,
        accentBorder = isLive
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "YOUTUBE BROADCAST DESK",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = EsportsTextPrimary
                )
                Text(
                    text = if (isLive) "TRANSMITTING TO YOUTUBE LIVE INGEST" else "READY FOR BROADCAST",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isLive) LiveOnAirRed else EsportsCyan
                )
            }

            // Live status badge
            Box(
                modifier = Modifier
                    .background(
                        if (isLive) LiveOnAirRed.copy(alpha = 0.2f) else EsportsSurfaceVariant,
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        1.dp,
                        if (isLive) LiveOnAirRed else EsportsSurfaceBorder,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = if (isLive) LiveOnAirRed else EsportsTextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLive) "LIVE ON AIR" else "OFFLINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLive) LiveOnAirRed else EsportsTextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Telemetry readout box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF070B12))
                .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LIVE DURATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EsportsTextMuted,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (isLive) liveTimeFormatted else "00:00:00",
                            fontSize = 26.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = if (isLive) LiveOnAirRed else EsportsTextPrimary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EsportsMetricPill(
                            label = "Health",
                            value = telemetry.health.label,
                            color = if (isLive) EsportsGreen else EsportsTextMuted
                        )
                        EsportsMetricPill(
                            label = "Viewers",
                            value = "${telemetry.viewerCount}",
                            icon = Icons.Default.Visibility,
                            color = EsportsGold
                        )
                    }
                }

                if (isLive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        EsportsMetricPill(
                            label = "Bitrate",
                            value = "${telemetry.currentBitrateKbps} Kbps",
                            color = EsportsCyan
                        )
                        EsportsMetricPill(
                            label = "Stream FPS",
                            value = "${telemetry.currentFps} FPS",
                            color = EsportsCyan
                        )
                        EsportsMetricPill(
                            label = "Dropped",
                            value = "${telemetry.droppedFrames} frames",
                            color = EsportsGreen
                        )
                        EsportsMetricPill(
                            label = "Likes",
                            value = "${telemetry.likesCount}",
                            icon = Icons.Default.ThumbUp,
                            color = EsportsGold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start / End Live Buttons
        if (!isLive) {
            Button(
                onClick = onStartLive,
                enabled = !uiState.isStartingStream,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = YouTubeBrandRed,
                    contentColor = Color.White
                )
            ) {
                if (uiState.isStartingStream) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("CONNECTING TO YOUTUBE...", fontWeight = FontWeight.Black)
                } else {
                    Icon(imageVector = Icons.Default.Sensors, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GO LIVE ON YOUTUBE",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        } else {
            Button(
                onClick = onStopLive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EsportsRed,
                    contentColor = Color.White
                )
            ) {
                Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("END LIVE BROADCAST", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun YouTubeAccountCard(
    uiState: YouTubeLiveUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSwitchAccount: () -> Unit
) {
    val channel = uiState.channelInfo

    EsportsCard {
        EsportsSectionTitle(
            title = "YouTube Channel Account",
            icon = Icons.Default.AccountCircle,
            badgeText = if (channel.isConnected) "AUTHENTICATED" else "NOT CONNECTED"
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (channel.isConnected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(EsportsSurfaceVariant)
                    .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(8.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with Channel Avatar & Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!channel.channelAvatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = channel.channelAvatarUrl,
                                contentDescription = "Channel Avatar",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, EsportsCyan, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(YouTubeBrandRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = channel.channelTitle.firstOrNull()?.toString() ?: "Y",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = channel.channelTitle,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = EsportsTextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified Channel",
                                    tint = EsportsCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "${channel.channelHandle} • ${channel.subscriberCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = EsportsTextSecondary
                            )
                        }
                    }
                }

                // Account Email & Capabilities Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (channel.accountEmail != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF0A101C))
                                .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = channel.accountEmail,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = EsportsTextSecondary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (channel.isLiveStreamingEnabled) EsportsGreen.copy(alpha = 0.15f) else EsportsRed.copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (channel.isLiveStreamingEnabled) EsportsGreen.copy(alpha = 0.4f) else EsportsRed.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (channel.isLiveStreamingEnabled) "✓ Live Stream Enabled" else "⚠ Live Stream Pending",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (channel.isLiveStreamingEnabled) EsportsGreen else EsportsRed
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSwitchAccount,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EsportsCyan)
                    ) {
                        Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Switch Account", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EsportsTextMuted)
                    ) {
                        Text("Disconnect", fontSize = 11.sp)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(EsportsSurfaceVariant)
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Connect your real YouTube Channel via Google authentication to manage live streams, read live chat, and upload esports thumbnails.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EsportsTextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Button(
                    onClick = onConnect,
                    enabled = !uiState.isConnectingAccount,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YouTubeBrandRed,
                        contentColor = Color.White
                    )
                ) {
                    if (uiState.isConnectingAccount) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting...")
                    } else {
                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CONNECT YOUTUBE ACCOUNT", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamMetadataCard(
    uiState: YouTubeLiveUiState,
    viewModel: YouTubeLiveViewModel
) {
    val config = uiState.streamConfig

    EsportsCard {
        EsportsSectionTitle(
            title = "Live Broadcast Details",
            icon = Icons.Default.LiveTv,
            badgeText = "METADATA"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Title with character counter
        OutlinedTextField(
            value = config.title,
            onValueChange = { if (it.length <= 100) viewModel.updateStreamTitle(it) },
            label = { Text("Stream Title") },
            supportingText = {
                Text(
                    text = "${config.title.length}/100 characters",
                    color = EsportsTextMuted,
                    fontSize = 11.sp
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EsportsCyan,
                unfocusedBorderColor = EsportsSurfaceBorder,
                focusedTextColor = EsportsTextPrimary,
                unfocusedTextColor = EsportsTextPrimary
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Preset Templates Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PresetTitleChip(
                label = "Tournament",
                onClick = { viewModel.updateStreamTitle("MVP ESPORTS GRAND CHAMPIONSHIP | ROAD TO PRO") }
            )
            PresetTitleChip(
                label = "Ranked Push",
                onClick = { viewModel.updateStreamTitle("🔴 LIVE: RANK 1 PUSH! ROAD TO CONQUEROR | NO LOSS DAY") }
            )
            PresetTitleChip(
                label = "Scrims",
                onClick = { viewModel.updateStreamTitle("TIER-1 PRO SCRIMS & HIGHLIGHTS | MVP ESPORTS") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Game Category
        OutlinedTextField(
            value = config.gameTitle,
            onValueChange = { viewModel.updateGameTitle(it) },
            label = { Text("Game Title / Category") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EsportsCyan,
                unfocusedBorderColor = EsportsSurfaceBorder,
                focusedTextColor = EsportsTextPrimary,
                unfocusedTextColor = EsportsTextPrimary
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Description
        OutlinedTextField(
            value = config.description,
            onValueChange = { viewModel.updateStreamDescription(it) },
            label = { Text("Stream Description & Socials") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EsportsCyan,
                unfocusedBorderColor = EsportsSurfaceBorder,
                focusedTextColor = EsportsTextPrimary,
                unfocusedTextColor = EsportsTextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Privacy Selector
        Text(
            text = "BROADCAST PRIVACY",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = EsportsTextMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        EsportsSegmentedRow(
            items = StreamPrivacy.values().toList(),
            selectedItem = config.privacy,
            labelProvider = { it.name },
            onItemSelected = { viewModel.updatePrivacy(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Latency Mode
        Text(
            text = "STREAM LATENCY",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = EsportsTextMuted
        )
        Spacer(modifier = Modifier.height(6.dp))
        EsportsSegmentedRow(
            items = LatencyMode.values().toList(),
            selectedItem = config.latencyMode,
            labelProvider = { it.name.replace("_", " ") },
            onItemSelected = { viewModel.updateLatency(it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = { viewModel.createRealYouTubeBroadcast() },
            enabled = !uiState.isStartingStream && uiState.channelInfo.isConnected,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EsportsCyan,
                contentColor = Color.Black
            )
        ) {
            if (uiState.isStartingStream) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CREATING BROADCAST ON YOUTUBE...", fontWeight = FontWeight.Black, fontSize = 12.sp)
            } else {
                Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("CREATE REAL YOUTUBE BROADCAST", fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PresetTitleChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(EsportsCyan.copy(alpha = 0.1f))
            .border(1.dp, EsportsCyan.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = "+ $label", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EsportsCyan)
    }
}

@Composable
private fun LiveBroadcastManagementCard(
    uiState: YouTubeLiveUiState,
    onOpenSelectBroadcastDialog: () -> Unit,
    onOpenEditBroadcastDialog: () -> Unit,
    onCreateBroadcast: () -> Unit,
    onStartLifecycle: () -> Unit,
    onEndLifecycle: () -> Unit,
    onPickThumbnail: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val config = uiState.streamConfig
    val hasBroadcast = !config.broadcastId.isNullOrBlank()
    val isLive = uiState.telemetry.isLive

    val hours = uiState.telemetry.elapsedSeconds / 3600
    val minutes = (uiState.telemetry.elapsedSeconds % 3600) / 60
    val seconds = uiState.telemetry.elapsedSeconds % 60
    val liveTimeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    EsportsCard(headerColor = if (isLive) LiveOnAirRed else EsportsCyan, accentBorder = true) {
        EsportsSectionTitle(
            title = "YouTube Live Management",
            icon = Icons.Default.LiveTv,
            badgeText = if (hasBroadcast) config.liveStatus.uppercase() else "NOT BOUND"
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (hasBroadcast) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF070B12))
                    .border(1.dp, EsportsCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title and Privacy Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CURRENT BROADCAST",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = EsportsTextMuted,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = config.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = EsportsTextPrimary,
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(EsportsSurfaceVariant, RoundedCornerShape(4.dp))
                            .border(1.dp, EsportsCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = config.privacy.label.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = EsportsCyan
                        )
                    }
                }

                if (config.description.isNotBlank()) {
                    Text(
                        text = config.description,
                        fontSize = 11.sp,
                        color = EsportsTextSecondary,
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Metadata Metrics Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Broadcast ID", fontSize = 10.sp, color = EsportsTextMuted)
                        Text(
                            text = config.broadcastId ?: "N/A",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = EsportsCyan
                        )
                    }
                    Column {
                        Text("Live Status", fontSize = 10.sp, color = EsportsTextMuted)
                        Text(
                            text = config.liveStatus,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLive) LiveOnAirRed else EsportsGreen
                        )
                    }
                    Column {
                        Text("Duration", fontSize = 10.sp, color = EsportsTextMuted)
                        Text(
                            text = if (isLive) liveTimeFormatted else "00:00:00",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isLive) LiveOnAirRed else EsportsTextPrimary
                        )
                    }
                    Column {
                        Text("Chat Ingest", fontSize = 10.sp, color = EsportsTextMuted)
                        Text(
                            text = if (config.liveChatId != null) "Bound" else "Unbound",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (config.liveChatId != null) EsportsGreen else EsportsGold
                        )
                    }
                }

                // Action buttons inside broadcast card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenEditBroadcastDialog,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EsportsCyan)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Details", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onPickThumbnail,
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EsportsGold)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Set Thumbnail", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!config.watchUrl.isNullOrBlank()) {
                    Button(
                        onClick = { onOpenUrl(config.watchUrl) },
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EsportsSurfaceVariant,
                            contentColor = EsportsCyan
                        )
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open on YouTube: ${config.watchUrl}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Lifecycle Transition Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (config.liveStatus.equals("testing", ignoreCase = true) || config.liveStatus.equals("ready", ignoreCase = true)) {
                    Button(
                        onClick = onStartLifecycle,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LiveOnAirRed,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GO LIVE (YOUTUBE API)", fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }

                if (isLive || config.liveStatus.equals("live", ignoreCase = true)) {
                    Button(
                        onClick = onEndLifecycle,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EsportsRed,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("END BROADCAST (API)", fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
            }
        } else {
            // Unbound State Info Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF070B12))
                    .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = EsportsTextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "No YouTube Broadcast Bound",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EsportsTextPrimary
                        )
                        Text(
                            text = "Create a new broadcast or pick an upcoming scheduled stream from your channel.",
                            fontSize = 11.sp,
                            color = EsportsTextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Broadcast Discovery & Creation Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenSelectBroadcastDialog,
                enabled = uiState.channelInfo.isConnected,
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EsportsCyan)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Select Scheduled", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onCreateBroadcast,
                enabled = !uiState.isStartingStream && uiState.channelInfo.isConnected,
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EsportsCyan,
                    contentColor = Color.Black
                )
            ) {
                if (uiState.isStartingStream) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Broadcast", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun SelectBroadcastDialog(
    uiState: YouTubeLiveUiState,
    onSelect: (LiveBroadcastSummary) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EsportsSurfaceVariant,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select YouTube Broadcast",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = EsportsTextPrimary
                )
                IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = EsportsCyan)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Pick an active or upcoming scheduled broadcast from your YouTube Studio channel:",
                    fontSize = 12.sp,
                    color = EsportsTextSecondary
                )

                if (uiState.isLoadingBroadcasts) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = EsportsCyan, modifier = Modifier.size(28.dp))
                    }
                } else if (uiState.upcomingBroadcasts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF070B12))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No scheduled broadcasts found. Click 'New Broadcast' to create one.",
                            fontSize = 11.sp,
                            color = EsportsTextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(uiState.upcomingBroadcasts, key = { it.id }) { broadcast ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF070B12))
                                    .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(6.dp))
                                    .clickable { onSelect(broadcast) }
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = broadcast.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EsportsTextPrimary,
                                            maxLines = 1
                                        )
                                         Text(
                                            text = "ID: ${broadcast.id} • ${broadcast.lifeCycleStatus.uppercase()}",
                                            fontSize = 10.sp,
                                            color = EsportsCyan,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(EsportsCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = (broadcast.privacyStatus ?: "PUBLIC").uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EsportsCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EsportsSurfaceBorder, contentColor = EsportsTextPrimary)
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun EditBroadcastDialog(
    currentTitle: String,
    currentDescription: String,
    currentPrivacy: StreamPrivacy,
    isUpdating: Boolean,
    onSave: (String, String, StreamPrivacy) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }
    var description by remember { mutableStateOf(currentDescription) }
    var privacy by remember { mutableStateOf(currentPrivacy) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EsportsSurfaceVariant,
        title = {
            Text(
                text = "Edit YouTube Broadcast",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                color = EsportsTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Broadcast Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EsportsCyan,
                        unfocusedBorderColor = EsportsSurfaceBorder,
                        focusedTextColor = EsportsTextPrimary,
                        unfocusedTextColor = EsportsTextPrimary
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Broadcast Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EsportsCyan,
                        unfocusedBorderColor = EsportsSurfaceBorder,
                        focusedTextColor = EsportsTextPrimary,
                        unfocusedTextColor = EsportsTextPrimary
                    )
                )

                Text("Privacy Setting:", fontSize = 11.sp, color = EsportsTextMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StreamPrivacy.values().forEach { p ->
                        val selected = privacy == p
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selected) EsportsCyan.copy(alpha = 0.2f) else Color(0xFF070B12))
                                .border(1.dp, if (selected) EsportsCyan else EsportsSurfaceBorder, RoundedCornerShape(4.dp))
                                .clickable { privacy = p }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p.label,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) EsportsCyan else EsportsTextMuted
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, description, privacy) },
                enabled = !isUpdating && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EsportsCyan, contentColor = Color.Black)
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save to YouTube", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = EsportsTextMuted)
            }
        }
    )
}

@Composable
private fun StreamQualityCard(
    uiState: YouTubeLiveUiState,
    viewModel: YouTubeLiveViewModel
) {
    val config = uiState.streamConfig

    EsportsCard {
        EsportsSectionTitle(
            title = "Hardware Stream Quality & Output Format",
            icon = Icons.Default.Speed,
            badgeText = "H.264 / AAC"
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "BROADCAST RESOLUTION",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = EsportsTextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        EsportsSegmentedRow(
            items = listOf(VideoResolution.RES_720P, VideoResolution.RES_1080P),
            selectedItem = config.resolution,
            labelProvider = { it.label },
            onItemSelected = { viewModel.updateResolution(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "FRAME RATE (FPS)",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = EsportsTextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        EsportsSegmentedRow(
            items = listOf(VideoFps.FPS_30, VideoFps.FPS_60),
            selectedItem = config.fps,
            labelProvider = { "${it.fpsValue} FPS" },
            onItemSelected = { viewModel.updateFps(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "ENCODING BITRATE",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = EsportsTextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        EsportsSegmentedRow(
            items = listOf(4, 6, 8, 12),
            selectedItem = config.bitrateMbps,
            labelProvider = { "$it Mbps" },
            onItemSelected = { viewModel.updateBitrate(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF070B12))
                .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(6.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "ISOLATED ENCODER SPECIFICATIONS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = EsportsCyan,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "• Codec: H.264 / AVC Hardware Encoder (Surface direct input)\n" +
                           "• Keyframe Cadence: 2.0s GOP interval\n" +
                           "• Audio: Hardware AAC-LC @ 128 Kbps, 44.1 kHz Stereo\n" +
                           "• Isolation: Control UI and overlay panels never appear in video stream",
                    fontSize = 10.sp,
                    color = EsportsTextSecondary,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ThumbnailStudioCard(
    thumbnailUri: Uri?,
    onPickThumbnail: () -> Unit,
    onRemoveThumbnail: () -> Unit
) {
    EsportsCard {
        EsportsSectionTitle(
            title = "Broadcast Thumbnail Studio",
            icon = Icons.Default.Image,
            badgeText = if (thumbnailUri != null) "CUSTOM 16:9" else "DEFAULT"
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (thumbnailUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, EsportsCyan, RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = "Selected YouTube Thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Overlay badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("1920x1080 FHD", color = EsportsCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPickThumbnail,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change Image", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onRemoveThumbnail,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EsportsRed)
                ) {
                    Text("Remove", fontSize = 12.sp)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(EsportsSurfaceVariant)
                    .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(8.dp))
                    .clickable { onPickThumbnail() }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = EsportsCyan,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Upload Custom Esports Thumbnail (16:9)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EsportsTextPrimary
                    )
                    Text(
                        text = "Tap to choose from Android Media Gallery",
                        fontSize = 10.sp,
                        color = EsportsTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun RtmpIngestCard(
    uiState: YouTubeLiveUiState,
    viewModel: YouTubeLiveViewModel
) {
    val config = uiState.streamConfig
    var isKeyVisible by remember { mutableStateOf(false) }

    EsportsCard {
        EsportsSectionTitle(
            title = "RTMP Server & Stream Key",
            icon = Icons.Default.Lock,
            badgeText = "RTMPS SECURE"
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = config.rtmpServerUrl,
            onValueChange = { viewModel.updateRtmpServerUrl(it) },
            label = { Text("Stream URL (YouTube RTMP Ingest)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EsportsCyan,
                unfocusedBorderColor = EsportsSurfaceBorder,
                focusedTextColor = EsportsTextPrimary,
                unfocusedTextColor = EsportsTextPrimary
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = config.streamKey,
            onValueChange = { viewModel.updateStreamKey(it) },
            label = { Text("Stream Key") },
            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                    Icon(
                        imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle Stream Key Visibility",
                        tint = EsportsTextMuted
                    )
                }
            },
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
}

@Composable
private fun StreamControlsCard(
    uiState: YouTubeLiveUiState,
    viewModel: YouTubeLiveViewModel
) {
    val flags = uiState.controlFlags

    EsportsCard {
        EsportsSectionTitle(
            title = "Live Stream Broadcast Controls",
            icon = Icons.Default.Speed,
            badgeText = "HOTKEYS"
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.toggleMuteMic() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (flags.isMicMuted) EsportsRed else EsportsSurfaceVariant,
                    contentColor = if (flags.isMicMuted) Color.White else EsportsTextPrimary
                )
            ) {
                Icon(
                    imageVector = if (flags.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (flags.isMicMuted) "MIC MUTED" else "MUTE MIC", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.toggleEmergencySlate() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (flags.isEmergencySlateActive) EsportsGold else EsportsSurfaceVariant,
                    contentColor = if (flags.isEmergencySlateActive) Color(0xFF1E1200) else EsportsTextPrimary
                )
            ) {
                Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (flags.isEmergencySlateActive) "SLATE ON" else "PAUSE SLATE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LiveChatSectionCard(
    uiState: YouTubeLiveUiState,
    viewModel: YouTubeLiveViewModel
) {
    val messages = uiState.liveChatMessages
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val maxChars = 200

    // Auto-scroll when new message arrives if auto-scroll is enabled
    LaunchedEffect(messages.size, uiState.isChatAutoScroll) {
        if (uiState.isChatAutoScroll && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Filter messages based on active filter
    val filteredMessages = remember(messages, uiState.chatFilterMode) {
        when (uiState.chatFilterMode) {
            ChatFilterMode.ALL -> messages
            ChatFilterMode.SUPER_CHATS -> messages.filter { it.isSuperChat }
            ChatFilterMode.MODERATORS -> messages.filter { it.isModerator || it.isOwner }
            ChatFilterMode.MEMBERS -> messages.filter { it.isSponsor || it.isOwner }
        }
    }

    val superChatCount = remember(messages) { messages.count { it.isSuperChat } }

    EsportsCard(headerColor = EsportsCyan, accentBorder = true) {
        // Chat Header & Connection Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EsportsSectionTitle(
                title = "YouTube Live Chat Feed",
                icon = Icons.Default.Chat,
                badgeText = "${messages.size} MSG"
            )

            // Connection Status Pill
            val (statusText, statusColor) = when (uiState.chatConnectionStatus) {
                ChatConnectionStatus.LIVE_CONNECTED -> "CONNECTED" to EsportsGreen
                ChatConnectionStatus.CONNECTING -> "CONNECTING..." to EsportsCyan
                ChatConnectionStatus.POLLING -> "SYNCING..." to EsportsCyan
                ChatConnectionStatus.PAUSED -> "PAUSED" to EsportsGold
                ChatConnectionStatus.NO_CHAT_ACTIVE -> "NO CHAT BOUND" to EsportsTextMuted
                ChatConnectionStatus.AUTH_ERROR -> "AUTH ERROR (401)" to EsportsRed
                ChatConnectionStatus.NETWORK_ERROR -> "NETWORK ERROR" to EsportsRed
                ChatConnectionStatus.DISCONNECTED -> "DISCONNECTED" to EsportsTextMuted
            }

            Box(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = statusText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Chat Toolbar (Refresh, Polling Pause/Resume, Auto-scroll Lock, Clear)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Manual Refresh
                IconButton(
                    onClick = { viewModel.refreshChatManually() },
                    enabled = !uiState.isChatRefreshing,
                    modifier = Modifier
                        .size(32.dp)
                        .background(EsportsSurfaceVariant, RoundedCornerShape(6.dp))
                ) {
                    if (uiState.isChatRefreshing) {
                        CircularProgressIndicator(color = EsportsCyan, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Chat", tint = EsportsCyan, modifier = Modifier.size(16.dp))
                    }
                }

                // Auto-scroll toggle
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (uiState.isChatAutoScroll) EsportsCyan.copy(alpha = 0.2f) else EsportsSurfaceVariant)
                        .border(1.dp, if (uiState.isChatAutoScroll) EsportsCyan else EsportsSurfaceBorder, RoundedCornerShape(6.dp))
                        .clickable { viewModel.toggleChatAutoScroll() }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = if (uiState.isChatAutoScroll) EsportsCyan else EsportsTextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (uiState.isChatAutoScroll) "Auto-Scroll ON" else "Auto-Scroll OFF",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isChatAutoScroll) EsportsCyan else EsportsTextMuted
                        )
                    }
                }
            }

            // Clear chat action
            IconButton(
                onClick = { viewModel.clearLocalChat() },
                modifier = Modifier
                    .size(32.dp)
                    .background(EsportsSurfaceVariant, RoundedCornerShape(6.dp))
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Chat", tint = EsportsTextMuted, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Mode Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChatFilterTab(
                label = "All (${messages.size})",
                selected = uiState.chatFilterMode == ChatFilterMode.ALL,
                onClick = { viewModel.setChatFilterMode(ChatFilterMode.ALL) },
                modifier = Modifier.weight(1f)
            )
            ChatFilterTab(
                label = "Super Chat ($superChatCount)",
                selected = uiState.chatFilterMode == ChatFilterMode.SUPER_CHATS,
                onClick = { viewModel.setChatFilterMode(ChatFilterMode.SUPER_CHATS) },
                accentGold = true,
                modifier = Modifier.weight(1f)
            )
            ChatFilterTab(
                label = "Mods",
                selected = uiState.chatFilterMode == ChatFilterMode.MODERATORS,
                onClick = { viewModel.setChatFilterMode(ChatFilterMode.MODERATORS) },
                modifier = Modifier.weight(1f)
            )
            ChatFilterTab(
                label = "Members",
                selected = uiState.chatFilterMode == ChatFilterMode.MEMBERS,
                onClick = { viewModel.setChatFilterMode(ChatFilterMode.MEMBERS) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Chat Messages Display Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF070B12))
                .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (filteredMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            tint = EsportsTextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = when {
                                uiState.chatConnectionStatus == ChatConnectionStatus.AUTH_ERROR -> "Authentication error (401). Connect your YouTube Account."
                                uiState.chatConnectionStatus == ChatConnectionStatus.NETWORK_ERROR -> "Network error connecting to YouTube Chat."
                                uiState.streamConfig.liveChatId == null -> "No active YouTube live chat bound. Create or bind a live broadcast."
                                else -> "Waiting for live chat messages..."
                            },
                            fontSize = 11.sp,
                            color = EsportsTextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredMessages, key = { it.id }) { msg ->
                        ChatMessageItem(msg = msg)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Esports Macro Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("GG WP!", "🔥 LETS GO!", "👑 MVP CHAMPION!", "⚡ Match Point!").forEach { macro ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(EsportsCyan.copy(alpha = 0.12f))
                        .border(1.dp, EsportsCyan.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .clickable {
                            inputText = macro
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = macro,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EsportsCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Message Input & Send Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { if (it.length <= maxChars) inputText = it },
                placeholder = { Text("Chat as broadcaster...", fontSize = 12.sp, color = EsportsTextMuted) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                trailingIcon = {
                    Text(
                        text = "${inputText.length}/$maxChars",
                        fontSize = 10.sp,
                        color = if (inputText.length >= maxChars) EsportsRed else EsportsTextMuted,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EsportsCyan,
                    unfocusedBorderColor = EsportsSurfaceBorder,
                    focusedTextColor = EsportsTextPrimary,
                    unfocusedTextColor = EsportsTextPrimary
                )
            )

            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendChatMessage(inputText.trim())
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() && !uiState.isSendingChatMessage,
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EsportsCyan,
                    contentColor = Color.Black
                )
            ) {
                if (uiState.isSendingChatMessage) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Send, contentDescription = "Send Message", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatFilterTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentGold: Boolean = false,
    modifier: Modifier = Modifier
) {
    val activeColor = if (accentGold) EsportsGold else EsportsCyan
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) activeColor.copy(alpha = 0.2f) else Color(0xFF070B12))
            .border(1.dp, if (selected) activeColor else EsportsSurfaceBorder, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) activeColor else EsportsTextMuted
        )
    }
}

@Composable
private fun ChatMessageItem(msg: LiveChatMessage) {
    if (msg.isSuperChat) {
        // Highlighted Super Chat Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1F1600))
                .border(1.dp, EsportsGold, RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (msg.authorAvatarUrl != null) {
                            AsyncImage(
                                model = msg.authorAvatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = EsportsGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = msg.author,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EsportsGold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(EsportsGold, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = msg.superChatAmount ?: "$5.00",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }

                if (msg.message.isNotBlank()) {
                    Text(
                        text = msg.message,
                        fontSize = 11.sp,
                        color = EsportsTextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    } else {
        // Standard Chat Message Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF070B12))
                .padding(6.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (msg.authorAvatarUrl != null) {
                AsyncImage(
                    model = msg.authorAvatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = EsportsCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val authorColor = when {
                        msg.isOwner -> EsportsGold
                        msg.isModerator -> EsportsGreen
                        msg.isSponsor -> Color(0xFF00E5FF)
                        else -> EsportsCyan
                    }

                    Text(
                        text = msg.author,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = authorColor
                    )

                    if (msg.isOwner) {
                        Text(text = "👑 OWNER", fontSize = 8.sp, fontWeight = FontWeight.Black, color = EsportsGold)
                    } else if (msg.isModerator) {
                        Text(text = "🛡️ MOD", fontSize = 8.sp, fontWeight = FontWeight.Black, color = EsportsGreen)
                    } else if (msg.isSponsor) {
                        Text(text = "⭐ MEMBER", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFF00E5FF))
                    }

                    Text(
                        text = "• ${msg.timestampFormatted}",
                        fontSize = 9.sp,
                        color = EsportsTextMuted
                    )
                }

                Text(
                    text = msg.message,
                    fontSize = 11.sp,
                    color = EsportsTextPrimary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun LiveColorEnhancementCard(
    uiState: YouTubeLiveUiState,
    viewModel: YouTubeLiveViewModel
) {
    val adjust = uiState.videoAdjustmentConfig

    EsportsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EsportsSectionTitle(
                title = "Live Stream GPU Color Adjustments",
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
                        text = "LIVE SHADER • ZERO CPU COPY • ZERO UI LEAK",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = EsportsCyan,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(
                    text = if (adjust.isEnabled) "APPLIED TO STREAM" else "RAW PASSTHROUGH",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (adjust.isEnabled) EsportsGreen else EsportsTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Brightness
        LiveColorChannelBlock(
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

        // 2. Contrast
        LiveColorChannelBlock(
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

        // 3. Saturation
        LiveColorChannelBlock(
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
            accentColor = Color(0xFFFF007F)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Master Reset Button
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
                    text = "RESET LIVE COLOR ENHANCEMENTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = EsportsTextPrimary
                )
            }
        }
    }
}

@Composable
private fun LiveColorChannelBlock(
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
