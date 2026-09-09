package com.example.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Accompanist-based permission request flow for initial app launch.
 * Requests CAMERA and RECORD_AUDIO for esports facecam overlay and live commentary audio.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun InitialLaunchPermissionFlow(
    forceShow: Boolean = false,
    onFlowDismissed: () -> Unit = {}
) {
    val context = LocalContext.current
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    )

    var hasDismissedByUser by rememberSaveable { mutableStateOf(false) }
    var hasRequestedOnce by rememberSaveable { mutableStateOf(false) }

    val allGranted = permissionsState.allPermissionsGranted
    val shouldShow = (forceShow || (!allGranted && !hasDismissedByUser))

    if (shouldShow && !allGranted) {
        Dialog(
            onDismissRequest = {
                hasDismissedByUser = true
                onFlowDismissed()
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            PermissionDialogContent(
                permissionsState = permissionsState,
                hasRequestedOnce = hasRequestedOnce,
                onRequestPermissions = {
                    hasRequestedOnce = true
                    permissionsState.launchMultiplePermissionRequest()
                },
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                onDismiss = {
                    hasDismissedByUser = true
                    onFlowDismissed()
                }
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionDialogContent(
    permissionsState: MultiplePermissionsState,
    hasRequestedOnce: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val micPermission = permissionsState.permissions.find { it.permission == Manifest.permission.RECORD_AUDIO }
    val cameraPermission = permissionsState.permissions.find { it.permission == Manifest.permission.CAMERA }

    val isMicGranted = micPermission?.status?.isGranted == true
    val isCameraGranted = cameraPermission?.status?.isGranted == true

    // Check if any permission is permanently denied (denied & shouldShowRationale == false after being requested)
    val isPermanentlyDenied = hasRequestedOnce && (
        (!isMicGranted && micPermission?.status?.shouldShowRationale == false) ||
        (!isCameraGranted && cameraPermission?.status?.shouldShowRationale == false)
    )

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(EsportsSurface)
                .border(1.5.dp, EsportsCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Shield Icon & Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(EsportsCyan.copy(alpha = 0.2f), EsportsGold.copy(alpha = 0.2f))
                            )
                        )
                        .border(1.5.dp, EsportsCyan, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Esports Security Shield",
                        tint = EsportsCyan,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(EsportsSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = EsportsTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title and Subtitle
            Text(
                text = "BROADCAST HARDWARE SETUP",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = EsportsTextPrimary,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "MVP ESPORTS LIVE requires microphone and camera access to enable real-time commentary audio and player facecam bubble overlays directly into your gameplay stream.",
                fontSize = 12.sp,
                color = EsportsTextSecondary,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Permission Cards
            PermissionItemCard(
                icon = if (isMicGranted) Icons.Default.Mic else Icons.Default.MicOff,
                title = "Microphone & In-Game Chat",
                purpose = "High-definition voice commentary, party chat mixing, and hardware AAC-LC live encoding.",
                isGranted = isMicGranted,
                isDenied = !isMicGranted && micPermission?.status?.shouldShowRationale == false && hasRequestedOnce
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionItemCard(
                icon = if (isCameraGranted) Icons.Default.Videocam else Icons.Default.VideocamOff,
                title = "Camera & Facecam Overlay",
                purpose = "Real-time player facecam bubble overlay directly composed onto your gameplay broadcast.",
                isGranted = isCameraGranted,
                isDenied = !isCameraGranted && cameraPermission?.status?.shouldShowRationale == false && hasRequestedOnce
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Rationale / Notice Banner
            if (isPermanentlyDenied) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(EsportsGold.copy(alpha = 0.12f))
                        .border(1.dp, EsportsGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = EsportsGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "One or more permissions are blocked. Please tap 'Open App Settings' to grant permissions in Android Settings.",
                            fontSize = 11.sp,
                            color = EsportsGold,
                            lineHeight = 15.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Primary Action Button
            if (isPermanentlyDenied) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EsportsGold,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OPEN APP SETTINGS",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            } else {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EsportsCyan,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GRANT BROADCAST PERMISSIONS",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Action: Continue / Later
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = EsportsTextMuted
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(EsportsSurfaceBorder, EsportsSurfaceBorder))
                )
            ) {
                Text(
                    text = "CONTINUE TO DESK (LATER)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    purpose: String,
    isGranted: Boolean,
    isDenied: Boolean
) {
    val borderColor = when {
        isGranted -> EsportsGreen
        isDenied -> EsportsRed
        else -> EsportsSurfaceBorder
    }

    val badgeBg = when {
        isGranted -> EsportsGreen.copy(alpha = 0.15f)
        isDenied -> EsportsRed.copy(alpha = 0.15f)
        else -> EsportsCyan.copy(alpha = 0.12f)
    }

    val badgeColor = when {
        isGranted -> EsportsGreen
        isDenied -> EsportsRed
        else -> EsportsCyan
    }

    val badgeText = when {
        isGranted -> "ACTIVE / GRANTED"
        isDenied -> "DENIED IN SETTINGS"
        else -> "REQUIRED"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(EsportsSurfaceVariant)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) EsportsGreen.copy(alpha = 0.2f) else EsportsCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isGranted) EsportsGreen else EsportsCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = EsportsTextPrimary
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = badgeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = purpose,
                    fontSize = 11.sp,
                    color = EsportsTextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * Compact permission status badge for top bar or header display.
 * Allows user to see at a glance if hardware is ready, and tap to trigger the flow.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionStatusChip(
    onChipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    )

    val allGranted = permissionsState.allPermissionsGranted

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (allGranted) EsportsGreen.copy(alpha = 0.15f) else EsportsGold.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = if (allGranted) EsportsGreen.copy(alpha = 0.5f) else EsportsGold.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onChipClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (allGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (allGranted) EsportsGreen else EsportsGold,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = if (allGranted) "MIC & CAM READY" else "PERMISSIONS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = if (allGranted) EsportsGreen else EsportsGold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
