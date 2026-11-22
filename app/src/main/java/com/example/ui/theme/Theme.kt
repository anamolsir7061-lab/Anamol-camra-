package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.model.ThemeMode

private val LensMindDarkScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = CyanMuted,
    onPrimaryContainer = TextPrimaryDark,
    secondary = EmeraldMint,
    onSecondary = ObsidianVoid,
    tertiary = AmberPro,
    onTertiary = ObsidianVoid,
    error = CoralAlert,
    onError = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = ObsidianElevated,
    outline = OutlineDark
)

private val LensMindLightScheme = lightColorScheme(
    primary = CyanMuted,
    onPrimary = StudioLightSurface,
    primaryContainer = ColorTokens.LightPrimaryContainer,
    onPrimaryContainer = TextPrimaryLight,
    secondary = EmeraldMint,
    onSecondary = StudioLightSurface,
    tertiary = AmberPro,
    onTertiary = StudioLightSurface,
    error = CoralAlert,
    onError = StudioLightSurface,
    background = StudioLightBg,
    onBackground = TextPrimaryLight,
    surface = StudioLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = StudioLightCard,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight
)

private object ColorTokens {
    val LightPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFCFFAFE)
}

private val LensMindShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AnamolCameraTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (useDark) LensMindDarkScheme else LensMindLightScheme,
        typography = LensMindTypography,
        shapes = LensMindShapes,
        content = content
    )
}

@Composable
fun LensMindTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) = AnamolCameraTheme(themeMode = themeMode, content = content)
