package com.yuguang.lumo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 设计令牌：靛紫主色 + 暖白 / 深空蓝两套配色，统一全应用视觉。 */
object AiColors {
    val Indigo = Color(0xFF5B6CFF)
    val IndigoLight = Color(0xFF7C87FF)
    val IndigoSoft = Color(0xFFEDEFFF)
    val Coral = Color(0xFFFF7A59)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFFB86B)
    val LightBackground = Color(0xFFF6F7FB)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFEEF0F7)
    val LightInk = Color(0xFF1C2030)
    val LightSub = Color(0xFF5B6273)
    val LightFaint = Color(0xFF9AA3B2)
    val LightOutline = Color(0xFFDDE1EC)
    val DarkBackground = Color(0xFF0B1220)
    val DarkSurface = Color(0xFF141D33)
    val DarkSurfaceVariant = Color(0xFF1C2742)
    val DarkInk = Color(0xFFE9EDFB)
    val DarkSub = Color(0xFF9AA6C8)
    val DarkOutline = Color(0xFF2A3556)
}

private val LightColors = lightColorScheme(
    primary = AiColors.Indigo,
    onPrimary = Color.White,
    primaryContainer = AiColors.IndigoSoft,
    onPrimaryContainer = Color(0xFF1B2260),
    secondary = AiColors.Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9E2),
    onSecondaryContainer = Color(0xFF4A1F14),
    background = AiColors.LightBackground,
    onBackground = AiColors.LightInk,
    surface = AiColors.LightSurface,
    onSurface = AiColors.LightInk,
    surfaceVariant = AiColors.LightSurfaceVariant,
    onSurfaceVariant = AiColors.LightSub,
    outline = AiColors.LightOutline,
    outlineVariant = Color(0xFFEDF0F8),
    surfaceDim = Color(0xFFE9ECF4),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFCFE),
    surfaceContainer = Color(0xFFF2F4FA),
    surfaceContainerHigh = Color(0xFFEDF0F8),
    surfaceContainerHighest = Color(0xFFE7EBF5),
    inverseSurface = Color(0xFF1C2030),
    inverseOnSurface = Color(0xFFF6F7FB),
    inversePrimary = AiColors.IndigoLight,
    error = Color(0xFFE5484D),
    onError = Color.White,
    errorContainer = Color(0xFFFFE8E8),
    onErrorContainer = Color(0xFF5A1414),
)

private val DarkColors = darkColorScheme(
    primary = AiColors.IndigoLight,
    onPrimary = Color(0xFF0B1230),
    primaryContainer = Color(0xFF2A3270),
    onPrimaryContainer = Color(0xFFE3E7FF),
    secondary = Color(0xFFFF9C82),
    onSecondary = Color(0xFF3A160C),
    secondaryContainer = Color(0xFF4A2418),
    onSecondaryContainer = Color(0xFFFFE3D9),
    background = AiColors.DarkBackground,
    onBackground = AiColors.DarkInk,
    surface = AiColors.DarkSurface,
    onSurface = AiColors.DarkInk,
    surfaceVariant = AiColors.DarkSurfaceVariant,
    onSurfaceVariant = AiColors.DarkSub,
    outline = AiColors.DarkOutline,
    outlineVariant = Color(0xFF223050),
    surfaceDim = Color(0xFF0B1220),
    surfaceBright = Color(0xFF2A3A62),
    surfaceContainerLowest = Color(0xFF070C18),
    surfaceContainerLow = Color(0xFF101A2E),
    surfaceContainer = Color(0xFF16233F),
    surfaceContainerHigh = Color(0xFF1B2A4A),
    surfaceContainerHighest = Color(0xFF223155),
    inverseSurface = Color(0xFFE9EDFB),
    inverseOnSurface = Color(0xFF141D33),
    inversePrimary = AiColors.Indigo,
    error = Color(0xFFFF8B95),
    onError = Color(0xFF3A0A0F),
    errorContainer = Color(0xFF4A1418),
    onErrorContainer = Color(0xFFFFD9DD),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
