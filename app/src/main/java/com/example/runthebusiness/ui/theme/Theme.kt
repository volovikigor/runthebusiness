package com.example.runthebusiness.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF17243A)
private val Accent = Color(0xFF234AD1)

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE5FF),
    onPrimaryContainer = Color(0xFF132E84),
    secondary = Color(0xFF005B59),
    onSecondary = Color.White,
    tertiary = Accent,
    background = Color(0xFFF4F6FA),
    onBackground = Ink,
    surface = Color(0xFFF4F6FA),
    surfaceContainerLowest = Color(0xFFF4F6FA),
    surfaceContainerLow = Color(0xFFE8EDF6),
    surfaceContainer = Color(0xFFE2E9F3),
    surfaceContainerHigh = Color(0xFFDDE5F0),
    surfaceContainerHighest = Color(0xFFD3DDED),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE2E9F3),
    onSurfaceVariant = Color(0xFF44536C),
    outline = Color(0xFF647389),
    outlineVariant = Color(0xFFC6D1E1),
    error = Color(0xFFA52C32),
    onError = Color.White,
    secondaryContainer = Color(0xFFDAF1EE),
    onSecondaryContainer = Color(0xFF003D3C)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB7C7FF),
    onPrimary = Color(0xFF11296E),
    secondary = Color(0xFFA2DDD6),
    tertiary = Color(0xFFB7C7FF),
    background = Color(0xFF101827),
    onBackground = Color(0xFFE7EEF9),
    surface = Color(0xFF182335),
    surfaceContainerLow = Color(0xFF1E2B41),
    surfaceContainer = Color(0xFF253349),
    onSurface = Color(0xFFE7EEF9),
    surfaceVariant = Color(0xFF293850),
    onSurfaceVariant = Color(0xFFBCCAE0),
    outlineVariant = Color(0xFF3A4D69)
)

@Composable
fun RunthebusinessTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(14.dp), extraLarge = RoundedCornerShape(18.dp)),
        content = content
    )
}
