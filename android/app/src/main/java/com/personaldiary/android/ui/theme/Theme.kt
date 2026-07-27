package com.personaldiary.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.personaldiary.android.R

// Quiet ink — matches desktop styles.py
val InkBg = Color(0xFFF2F4F1)
val InkSurface = Color(0xFFFBFCFB)
val InkSidebar = Color(0xFFE8EBE7)
val InkBorder = Color(0xFFD2D8D2)
val InkText = Color(0xFF1B221E)
val InkMuted = Color(0xFF6A746C)
val InkAccent = Color(0xFF3F6B58)
val InkAccentSoft = Color(0xFFDCE8E1)
val InkHover = Color(0xFFDDE3DD)

val InkBgDark = Color(0xFF121614)
val InkSurfaceDark = Color(0xFF1A1F1C)
val InkTextDark = Color(0xFFE6EBE7)
val InkMutedDark = Color(0xFF8E9891)
val InkAccentDark = Color(0xFF7FAE97)
val InkAccentSoftDark = Color(0xFF24332C)
val InkBorderDark = Color(0xFF2A322D)

val WenKaiFamily = FontFamily(Font(R.font.lxgw_wenkai, weight = FontWeight.Normal))

private val LightColors = lightColorScheme(
    primary = InkAccent,
    onPrimary = Color.White,
    secondary = InkAccent,
    background = InkBg,
    onBackground = InkText,
    surface = InkSurface,
    onSurface = InkText,
    surfaceVariant = InkSidebar,
    onSurfaceVariant = InkMuted,
    outline = InkBorder,
    primaryContainer = InkAccentSoft,
    onPrimaryContainer = InkAccent,
)

private val DarkColors = darkColorScheme(
    primary = InkAccentDark,
    onPrimary = InkBgDark,
    secondary = InkAccentDark,
    background = InkBgDark,
    onBackground = InkTextDark,
    surface = InkSurfaceDark,
    onSurface = InkTextDark,
    surfaceVariant = Color(0xFF0F1311),
    onSurfaceVariant = InkMutedDark,
    outline = InkBorderDark,
    primaryContainer = InkAccentSoftDark,
    onPrimaryContainer = InkAccentDark,
)

@Composable
fun DiaryTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(
                fontFamily = WenKaiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = colors.onBackground,
            ),
            headlineMedium = TextStyle(
                fontFamily = WenKaiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            ),
            titleLarge = TextStyle(
                fontFamily = WenKaiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            ),
            titleMedium = TextStyle(
                fontFamily = WenKaiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            ),
            bodyLarge = TextStyle(
                fontFamily = WenKaiFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 26.sp,
            ),
            bodyMedium = TextStyle(
                fontFamily = WenKaiFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            ),
            labelLarge = TextStyle(
                fontFamily = WenKaiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ),
            labelMedium = TextStyle(
                fontFamily = WenKaiFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
            ),
        ),
        content = content,
    )
}
