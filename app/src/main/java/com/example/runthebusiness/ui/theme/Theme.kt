package com.example.runthebusiness.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Espresso = Color(0xFF523327)
private val Honey = Color(0xFFC18A49)
private val Cream = Color(0xFFFFF8ED)
private val Ink = Color(0xFF30251E)
private val Sage = Color(0xFF65775B)

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
    surface = Color(0xFFFFFCF6),
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0E7DB),
    onSurfaceVariant = Color(0xFF66584C),
    outline = Color(0xFF9B897A)
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
    onSurfaceVariant = Color(0xFFDFCEBE)
)

@Composable
fun RunthebusinessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
