package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ZeroChatDarkColorScheme = darkColorScheme(
    primary = NeonCyberGreen,
    onPrimary = Color(0xFF002110),
    primaryContainer = DeepGreenContainer,
    onPrimaryContainer = NeonCyberGreen,
    secondary = CyberCyan,
    onSecondary = Color(0xFF001F28),
    secondaryContainer = DeepCyanContainer,
    onSecondaryContainer = CyberCyan,
    tertiary = AmberTimerWarning,
    onTertiary = Color(0xFF261900),
    background = ObsidianBlack,
    onBackground = TextPrimaryIce,
    surface = CarbonSurface,
    onSurface = TextPrimaryIce,
    surfaceVariant = ElevatedCyberSurface,
    onSurfaceVariant = TextSecondaryMuted,
    error = BurnCoralRed,
    onError = Color.White,
    errorContainer = DeepBurnContainer,
    onErrorContainer = BurnCoralRed,
    outline = GlassBorderGreen
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ZeroChatDarkColorScheme,
        typography = Typography,
        content = content
    )
}
