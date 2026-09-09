package com.example.ui.youtube

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ui.theme.EsportsRed
import com.example.ui.theme.EsportsSurfaceBorder
import com.example.ui.theme.EsportsTextMuted
import com.example.ui.theme.EsportsTextPrimary
import com.example.ui.theme.EsportsTextSecondary
import com.example.ui.theme.YouTubeBrandRed
import com.example.viewmodel.YouTubeLiveViewModel

@Composable
fun YouTubeAuthDialog(
    viewModel: YouTubeLiveViewModel,
    uiState: YouTubeLiveUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {
            if (!uiState.isConnectingAccount) {
                onDismiss()
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = YouTubeBrandRed,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "CONNECT YOUTUBE CHANNEL",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Black
                        ),
                        color = EsportsTextPrimary
                    )
                }

                IconButton(
                    onClick = {
                        if (!uiState.isConnectingAccount) {
                            onDismiss()
                        }
                    },
                    modifier = Modifier.size(24.dp),
                    enabled = !uiState.isConnectingAccount
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = EsportsTextMuted
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                Text(
                    text = "Connect your real YouTube channel securely with your Google account.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = EsportsTextSecondary
                )

                Button(
                    onClick = {
                        viewModel.connectWithCredentialManager(context)
                    },
                    enabled = !uiState.isConnectingAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YouTubeBrandRed,
                        contentColor = Color.White,
                        disabledContainerColor = EsportsRed.copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    if (uiState.isConnectingAccount) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "CONNECTING...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "SIGN IN WITH GOOGLE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF070B12))
                        .border(
                            width = 1.dp,
                            color = EsportsSurfaceBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = EsportsCyan,
                                modifier = Modifier.size(15.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = "SECURE GOOGLE AUTHENTICATION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EsportsCyan
                            )
                        }

                        Text(
                            text = "Use Google Sign-In to connect your real YouTube channel. No manual OAuth access token or client secret is entered in the app.",
                            fontSize = 9.sp,
                            color = EsportsTextSecondary
                        )

                        Text(
                            text = "The authentication controls and this dialog stay outside the final recording and YouTube video output.",
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