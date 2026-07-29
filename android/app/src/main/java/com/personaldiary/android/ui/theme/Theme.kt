package com.personaldiary.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 青笺 — Slip / cyan paper
val SlipField = Color(0xFFF5F7F8)
val SlipFieldDeep = Color(0xFFEEF2F4)
val SlipCard = Color(0xFFFFFFFF)
val SlipInk = Color(0xFF111827)
val SlipMute = Color(0xFF6B7280)
val SlipTeal = Color(0xFF3A5F5A)
val SlipTealSoft = Color(0xFFD9E5E2)
val SlipLine = Color(0xFFE5E7EB)

/** Call-site aliases during transition from 匣光. */
val SparkCopper = SlipTeal
val SparkFieldTop = SlipField
val SparkFieldBrush = Brush.verticalGradient(listOf(SlipField, SlipFieldDeep))
val InkAccent = SlipTeal

val SlipFieldDark = Color(0xFF12161A)
val SlipFieldDeepDark = Color(0xFF0E1114)
val SlipCardDark = Color(0xFF1A1F24)
val SlipInkDark = Color(0xFFE8EAED)
val SlipMuteDark = Color(0xFF9AA0A8)
val SlipTealDark = Color(0xFF7FA39C)
val SlipTealSoftDark = Color(0xFF243836)
val SlipLineDark = Color(0xFF2A3138)

val SparkFieldBrushDark = Brush.verticalGradient(listOf(SlipFieldDark, SlipFieldDeepDark))

private val LightColors = lightColorScheme(
    primary = SlipTeal,
    onPrimary = Color.White,
    secondary = SlipTeal,
    background = SlipField,
    onBackground = SlipInk,
    surface = SlipCard,
    onSurface = SlipInk,
    surfaceVariant = SlipFieldDeep,
    onSurfaceVariant = SlipMute,
    outline = SlipLine,
    primaryContainer = SlipTealSoft,
    onPrimaryContainer = SlipTeal,
)

private val DarkColors = darkColorScheme(
    primary = SlipTealDark,
    onPrimary = SlipFieldDark,
    secondary = SlipTealDark,
    background = SlipFieldDark,
    onBackground = SlipInkDark,
    surface = SlipCardDark,
    onSurface = SlipInkDark,
    surfaceVariant = SlipFieldDeepDark,
    onSurfaceVariant = SlipMuteDark,
    outline = SlipLineDark,
    primaryContainer = SlipTealSoftDark,
    onPrimaryContainer = SlipTealDark,
)

@Composable
fun DiaryTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 36.sp,
                color = colors.primary,
            ),
            headlineMedium = TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            ),
            titleLarge = TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
            ),
            titleMedium = TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            ),
            bodyLarge = TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 26.sp,
            ),
            bodyMedium = TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            ),
            labelLarge = TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            ),
            labelMedium = TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
            ),
            labelSmall = TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
            ),
        ),
        content = content,
    )
}

@Composable
fun SlipFieldBackground(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (darkTheme) SparkFieldBrushDark else SparkFieldBrush),
    ) {
        content()
    }
}
