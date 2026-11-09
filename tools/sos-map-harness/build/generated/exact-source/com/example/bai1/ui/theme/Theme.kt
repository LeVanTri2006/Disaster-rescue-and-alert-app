package com.example.bai1.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = SurfaceWhite,
    primaryContainer = PrimaryLightBlue,
    onPrimaryContainer = PrimaryDarkBlue,
    secondary = WarningOrange,
    onSecondary = SurfaceWhite,
    secondaryContainer = WarningOrangeLight,
    onSecondaryContainer = WarningOrangeDark,
    tertiary = SuccessGreen,
    onTertiary = SurfaceWhite,
    tertiaryContainer = SuccessGreenLight,
    onTertiaryContainer = SuccessGreenDark,
    error = EmergencyRed,
    onError = SurfaceWhite,
    errorContainer = EmergencyRedLight,
    onErrorContainer = EmergencyRedDark,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundLight,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor,
    outlineVariant = DividerColor
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = PrimaryDarkBlue,
    onPrimaryContainer = PrimaryLightBlue,
    secondary = WarningOrange,
    onSecondary = SurfaceWhite,
    secondaryContainer = Color(0xFF5C3300),
    onSecondaryContainer = WarningOrangeLight,
    tertiary = Color(0xFF81C784),
    onTertiary = Color(0xFF1B5E20),
    error = EmergencyRed,
    onError = SurfaceWhite,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF475569),
    outlineVariant = DividerDark
)

@Composable
fun DisasterAlertTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

// Backward compatibility with existing calls
@Composable
fun Bai1Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    DisasterAlertTheme(darkTheme = darkTheme, content = content)
}