package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EsportsColorScheme = darkColorScheme(
  primary = EsportsCyan,
  onPrimary = Color(0xFF002026),
  primaryContainer = Color(0xFF00363F),
  onPrimaryContainer = Color(0xFF80F8FF),

  secondary = EsportsGold,
  onSecondary = Color(0xFF2E1C00),
  secondaryContainer = Color(0xFF4A3100),
  onSecondaryContainer = Color(0xFFFFDEA3),

  tertiary = EsportsPurple,
  onTertiary = Color(0xFF28114D),
  tertiaryContainer = Color(0xFF402271),
  onTertiaryContainer = Color(0xFFEADBFF),

  background = EsportsBackground,
  onBackground = EsportsTextPrimary,

  surface = EsportsSurface,
  onSurface = EsportsTextPrimary,
  surfaceVariant = EsportsSurfaceVariant,
  onSurfaceVariant = EsportsTextSecondary,

  outline = EsportsSurfaceBorder,
  outlineVariant = Color(0xFF1E273A),

  error = EsportsRed,
  onError = Color.White
)

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = EsportsColorScheme,
    typography = Typography,
    content = content
  )
}

