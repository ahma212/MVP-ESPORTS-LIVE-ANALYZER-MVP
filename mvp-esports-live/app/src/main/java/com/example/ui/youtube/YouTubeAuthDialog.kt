package com.example.ui.youtube

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.YouTubeLiveUiState
import com.example.ui.theme.EsportsCardSurface
import com.example.ui.theme.EsportsCyan
import com.example.ui.theme.EsportsGreen
import com.example.ui.theme.EsportsRed
import com.example.ui.theme.EsportsSurfaceBorder
import com.example.ui.theme.EsportsSurfaceVariant
import com.example.ui.theme.EsportsTextMuted
import com.example.ui.theme.EsportsTextPrimary
import com.example.ui.theme.EsportsTextSecondary
import com.example.ui.theme.YouTubeBrandRed
import com.example.viewmodel.YouTubeLiveViewModel

enum class AuthMethodTab(val label: String) {
    GOOGLE_SIGN_IN("Google Identity (Credential Manager)"),
    OAUTH_ACCESS_TOKEN("OAuth 2.0 Access Token")
}

@Composable
fun YouTubeAuthDialog(
    viewModel: YouTubeLiveViewModel,
    uiState: YouTubeLiveUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(AuthMethodTab.GOOGLE_SIGN_IN) }

    var accountEmailInput by remember { mutableStateOf("") }
    var accessTokenInput by remember { mutableStateOf("") }
    var clientIdInput by remember { mutableStateOf(uiState.customOAuthClientId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = YouTubeBrandRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CONNECT YOUTUBE CHANNEL",
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Method Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(EsportsSurfaceVariant)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AuthMethodTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) EsportsCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { selectedTab = tab }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (tab == AuthMethodTab.GOOGLE_SIGN_IN) "Google Sign-In" else "OAuth Token",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) EsportsCyan else EsportsTextMuted
                            )
                        }
                    }
                }

                when (selectedTab) {
                    AuthMethodTab.GOOGLE_SIGN_IN -> {
                        Text(
                            text = "Authenticate securely using Android Credential Manager with Google Play Services.",
                            fontSize = 11.sp,
                            color = EsportsTextSecondary
                        )

                        OutlinedTextField(
                            value = clientIdInput,
                            onValueChange = {
                                clientIdInput = it
                                viewModel.saveCustomOAuthClientId(it)
                            },
                            label = { Text("Google Web Client ID (Optional)") },
                            placeholder = { Text("xxxx.apps.googleusercontent.com") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EsportsCyan,
                                unfocusedBorderColor = EsportsSurfaceBorder,
                                focusedTextColor = EsportsTextPrimary,
                                unfocusedTextColor = EsportsTextPrimary
                            ),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                viewModel.connectWithCredentialManager(context)
                            },
                            enabled = !uiState.isConnectingAccount,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = YouTubeBrandRed,
                                contentColor = Color.White
                            )
                        ) {
                            if (uiState.isConnectingAccount) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connecting to Google Identity...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SIGN IN WITH GOOGLE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    AuthMethodTab.OAUTH_ACCESS_TOKEN -> {
                        Text(
                            text = "Authorize via Google OAuth 2.0 access token with 'https://www.googleapis.com/auth/youtube' scope.",
                            fontSize = 11.sp,
                            color = EsportsTextSecondary
                        )

                        OutlinedTextField(
                            value = accountEmailInput,
                            onValueChange = { accountEmailInput = it },
                            label = { Text("Google Account Email") },
                            placeholder = { Text("streamer@gmail.com") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EsportsCyan,
                                unfocusedBorderColor = EsportsSurfaceBorder,
                                focusedTextColor = EsportsTextPrimary,
                                unfocusedTextColor = EsportsTextPrimary
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = accessTokenInput,
                            onValueChange = { accessTokenInput = it },
                            label = { Text("OAuth 2.0 Access Token (Bearer)") },
                            placeholder = { Text("ya29.a0A...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EsportsCyan,
                                unfocusedBorderColor = EsportsSurfaceBorder,
                                focusedTextColor = EsportsTextPrimary,
                                unfocusedTextColor = EsportsTextPrimary
                            ),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                viewModel.connectWithDirectOAuthToken(
                                    accountEmail = accountEmailInput,
                                    accessToken = accessTokenInput
                                )
                            },
                            enabled = !uiState.isConnectingAccount,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = YouTubeBrandRed,
                                contentColor = Color.White
                            )
                        ) {
                            if (uiState.isConnectingAccount) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Validating YouTube Channel...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CONNECT YOUTUBE API", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Security & Output Isolation Notice
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF070B12))
                        .border(1.dp, EsportsSurfaceBorder, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = EsportsCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Zero Secret Exposure & Encrypted Storage",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EsportsCyan
                            )
                        }
                        Text(
                            text = "Client secrets are never embedded into the application. Session tokens are encrypted in Android Private storage. Authentication and control dialogs are strictly isolated from the live video composition.",
                            fontSize = 9.sp,
                            color = EsportsTextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
        containerColor = EsportsCardSurface,
        shape = RoundedCornerShape(12.dp)
    )
}
