package com.hanapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 小马宝莉主色调：粉色
val PonyPink = Color(0xFFFFC0CB)
val PonyPinkLight = Color(0xFFFFB6C1)
val PonyPinkDark = Color(0xFFFF69B4)
val PonyPurple = Color(0xFFE6E6FA)
val PonyBlue = Color(0xFFB0E0E6)
val PonyYellow = Color(0xFFFFFACD)

private val LightColorScheme = lightColorScheme(
    primary = PonyPinkDark,
    onPrimary = Color.White,
    primaryContainer = PonyPink,
    secondary = PonyPurple,
    tertiary = PonyBlue,
    background = Color(0xFFFFF5F8), // 非常浅的粉色背景
    surface = Color.White,
    onSurface = Color(0xFF4A4A4A)
)

private val DarkColorScheme = darkColorScheme(
    primary = PonyPink,
    secondary = PonyPurple,
    tertiary = PonyBlue,
    background = Color(0xFF1A1A1A)
)

val PonyTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    )
)

@Composable
fun HanAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PonyTypography,
        content = content
    )
}
