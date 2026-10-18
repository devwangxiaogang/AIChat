package com.example.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF1F4E46)
private val Foam = Color(0xFFD5EDE6)
private val Paper = Color(0xFFF6F4EF)
private val Card = Color(0xFFFFFCF8)
private val Clay = Color(0xFF8C4A2F)
private val ClayWash = Color(0xFFF3E4DC)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Foam,
    onPrimaryContainer = Color(0xFF0E2E28),
    secondary = Clay,
    onSecondary = Color.White,
    secondaryContainer = ClayWash,
    onSecondaryContainer = Color(0xFF3E2418),
    background = Paper,
    onBackground = Color(0xFF1C1B19),
    surface = Card,
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFE7E2D8),
    onSurfaceVariant = Color(0xFF45423C),
    outline = Color(0xFFC9C2B6),
    error = Clay,
    onError = Color.White,
    errorContainer = ClayWash,
    onErrorContainer = Color(0xFF3E2418),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCFC0),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF1F4E46),
    onPrimaryContainer = Foam,
    secondary = Color(0xFFE7B8A4),
    onSecondary = Color(0xFF3E2418),
    secondaryContainer = Color(0xFF4A3328),
    onSecondaryContainer = Color(0xFFF3E4DC),
    background = Color(0xFF141614),
    onBackground = Color(0xFFE7E4DC),
    surface = Color(0xFF1C201E),
    onSurface = Color(0xFFE7E4DC),
    surfaceVariant = Color(0xFF2A2E2B),
    onSurfaceVariant = Color(0xFFC9C4B8),
    outline = Color(0xFF3E4541),
    error = Color(0xFFE7B8A4),
    onError = Color(0xFF3E2418),
    errorContainer = Color(0xFF4A3328),
    onErrorContainer = Color(0xFFF3E4DC),
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
)

@Composable
fun AiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
