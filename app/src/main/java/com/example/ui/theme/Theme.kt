package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CinemaDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = TextDark,
    primaryContainer = NeonCyanDark,
    onPrimaryContainer = TextWhite,
    secondary = ElectricViolet,
    onSecondary = TextWhite,
    secondaryContainer = ElectricVioletDark,
    onSecondaryContainer = TextWhite,
    tertiary = CinemaAmber,
    onTertiary = TextDark,
    background = CinemaBackground,
    onBackground = TextWhite,
    surface = CinemaSurface,
    onSurface = TextWhite,
    surfaceVariant = CinemaSurfaceElevated,
    onSurfaceVariant = TextMuted,
    outline = CinemaSurfaceBorder,
    error = CinemaRed,
    onError = TextWhite
)

@Composable
fun SequenceCutTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CinemaDarkColorScheme,
        typography = Typography,
        content = content
    )
}

// Alias for backwards compatibility with any template references
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SequenceCutTheme(content = content)
}
