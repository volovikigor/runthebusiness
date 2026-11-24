package com.example.runthebusiness.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Espresso = Color(0xFF30251F)
private val Honey = Color(0xFFB88B50)
private val Cream = Color(0xFFF5E9D5)
private val Ink = Color(0xFF30251E)
private val Sage = Color(0xFF53664C)

private val LightColorScheme = lightColorScheme(
    primary = Espresso,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEDCCE),
    onPrimaryContainer = Ink,
    secondary = Sage,
    onSecondary = Color.White,
    tertiary = Honey,
    background = Cream,
    onBackground = Ink,
    surface = Color(0xFFFFF9EE),
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0E7DB),
    onSurfaceVariant = Color(0xFF66584C),
    outline = Color(0xFF9B897A),
    outlineVariant = Color(0xFFD8C8B7),
    error = Color(0xFFA04C38),
    onError = Color.White,
    secondaryContainer = Color(0xFFDCE4D3),
    onSecondaryContainer = Color(0xFF263923)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE9BB88),
    onPrimary = Ink,
    secondary = Color(0xFFB8CDAA),
    tertiary = Honey,
    background = Color(0xFF211A16),
    onBackground = Color(0xFFF6EDE1),
    surface = Color(0xFF30251E),
    onSurface = Color(0xFFF6EDE1),
    surfaceVariant = Color(0xFF49382F),
    onSurfaceVariant = Color(0xFFDFCEBE),
    outlineVariant = Color(0xFF665343)
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
