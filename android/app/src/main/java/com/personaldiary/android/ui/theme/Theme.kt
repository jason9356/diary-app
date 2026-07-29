package com.personaldiary.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Named visual themes — each has light + dark variants. */
enum class ThemePalette(val id: String, val label: String, val hint: String) {
    Slip("slip", "青笺", "冷灰纸 · 青苔"),
    Moss("moss", "苔墨", "雾绿纸 · 苔绿"),
    Spark("spark", "匣光", "暖纸 · 铜辉"),
    Paper("paper", "素昼", "奶油纸 · 霁蓝"),
    ;

    companion object {
        fun fromId(id: String): ThemePalette =
            values().firstOrNull { it.id == id } ?: Slip
    }
}

data class AppColors(
    val field: Color,
    val fieldDeep: Color,
    val card: Color,
    val ink: Color,
    val mute: Color,
    val accent: Color,
    val accentSoft: Color,
    val line: Color,
) {
    fun brush(): Brush = Brush.verticalGradient(colors = listOf(field, fieldDeep))
}

val LocalAppColors = staticCompositionLocalOf { PaletteCatalog.slip(dark = false) }

object PaletteCatalog {
    fun resolve(paletteId: String, dark: Boolean): AppColors =
        when (ThemePalette.fromId(paletteId)) {
            ThemePalette.Slip -> slip(dark)
            ThemePalette.Moss -> moss(dark)
            ThemePalette.Spark -> spark(dark)
            ThemePalette.Paper -> paper(dark)
        }

    fun slip(dark: Boolean) = if (!dark) {
        AppColors(
            field = Color(0xFFF5F7F8),
            fieldDeep = Color(0xFFEEF2F4),
            card = Color(0xFFFFFFFF),
            ink = Color(0xFF111827),
            mute = Color(0xFF6B7280),
            accent = Color(0xFF3A5F5A),
            accentSoft = Color(0xFFD9E5E2),
            line = Color(0xFFE5E7EB),
        )
    } else {
        AppColors(
            field = Color(0xFF12161A),
            fieldDeep = Color(0xFF0E1114),
            card = Color(0xFF1A1F24),
            ink = Color(0xFFE8EAED),
            mute = Color(0xFF9AA0A8),
            accent = Color(0xFF7FA39C),
            accentSoft = Color(0xFF243836),
            line = Color(0xFF2A3138),
        )
    }

    fun moss(dark: Boolean) = if (!dark) {
        AppColors(
            field = Color(0xFFF2F4F1),
            fieldDeep = Color(0xFFE8EBE7),
            card = Color(0xFFFBFCFB),
            ink = Color(0xFF1B221E),
            mute = Color(0xFF6A746C),
            accent = Color(0xFF3F6B58),
            accentSoft = Color(0xFFDCE8E1),
            line = Color(0xFFD2D8D2),
        )
    } else {
        AppColors(
            field = Color(0xFF121614),
            fieldDeep = Color(0xFF0F1311),
            card = Color(0xFF1A1F1C),
            ink = Color(0xFFE6EBE7),
            mute = Color(0xFF8E9891),
            accent = Color(0xFF7FAE97),
            accentSoft = Color(0xFF24332C),
            line = Color(0xFF2A322D),
        )
    }

    fun spark(dark: Boolean) = if (!dark) {
        AppColors(
            field = Color(0xFFF7F3EE),
            fieldDeep = Color(0xFFF0E9E1),
            card = Color(0xFFFFFBF7),
            ink = Color(0xFF2A2118),
            mute = Color(0xFF7A6E62),
            accent = Color(0xFFA86B3C),
            accentSoft = Color(0xFFEEDCCB),
            line = Color(0xFFE4D8CB),
        )
    } else {
        AppColors(
            field = Color(0xFF16120F),
            fieldDeep = Color(0xFF110E0C),
            card = Color(0xFF1F1A16),
            ink = Color(0xFFEDE4DA),
            mute = Color(0xFFA89888),
            accent = Color(0xFFD4A574),
            accentSoft = Color(0xFF3A2C22),
            line = Color(0xFF332920),
        )
    }

    fun paper(dark: Boolean) = if (!dark) {
        AppColors(
            field = Color(0xFFF3F1EC),
            fieldDeep = Color(0xFFEAE6DE),
            card = Color(0xFFFCFBF8),
            ink = Color(0xFF1C1F24),
            mute = Color(0xFF6E737C),
            accent = Color(0xFF2F6FED),
            accentSoft = Color(0xFFDCE6FB),
            line = Color(0xFFDDD8CF),
        )
    } else {
        AppColors(
            field = Color(0xFF161718),
            fieldDeep = Color(0xFF111213),
            card = Color(0xFF1C1E21),
            ink = Color(0xFFE8EAED),
            mute = Color(0xFF9AA0A8),
            accent = Color(0xFF6B9BFF),
            accentSoft = Color(0xFF24304A),
            line = Color(0xFF2A2E33),
        )
    }
}

/** Legacy aliases — prefer MaterialTheme.colorScheme / LocalAppColors. */
@Deprecated("Use LocalAppColors.current.field", ReplaceWith("LocalAppColors.current.field"))
val SlipField get() = PaletteCatalog.slip(false).field
@Deprecated("Use MaterialTheme.colorScheme.primary")
val SlipTeal get() = PaletteCatalog.slip(false).accent
@Deprecated("Use LocalAppColors.current.brush()")
val SparkFieldBrush get() = PaletteCatalog.slip(false).brush()
@Deprecated("Use MaterialTheme.colorScheme.primary")
val SparkCopper get() = PaletteCatalog.slip(false).accent
@Deprecated("Use MaterialTheme.colorScheme.primary")
val InkAccent get() = PaletteCatalog.slip(false).accent

@Composable
fun DiaryTheme(
    paletteId: String = ThemePalette.Slip.id,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = PaletteCatalog.resolve(paletteId, darkTheme)
    val onAccent = if (darkTheme) colors.field else Color.White
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = onAccent,
            secondary = colors.accent,
            onSecondary = onAccent,
            background = colors.field,
            onBackground = colors.ink,
            surface = colors.card,
            onSurface = colors.ink,
            surfaceVariant = colors.fieldDeep,
            onSurfaceVariant = colors.mute,
            outline = colors.line,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.accent,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = onAccent,
            secondary = colors.accent,
            onSecondary = onAccent,
            background = colors.field,
            onBackground = colors.ink,
            surface = colors.card,
            onSurface = colors.ink,
            surfaceVariant = colors.fieldDeep,
            onSurfaceVariant = colors.mute,
            outline = colors.line,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.accent,
        )
    }
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalContentColor provides colors.ink,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography.copy(
                displayLarge = TextStyle(
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 36.sp,
                    color = colors.accent,
                ),
                headlineMedium = TextStyle(
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = colors.ink,
                ),
                titleLarge = TextStyle(
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = colors.ink,
                ),
                titleMedium = TextStyle(
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = colors.ink,
                ),
                bodyLarge = TextStyle(
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                    color = colors.ink,
                ),
                bodyMedium = TextStyle(
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = colors.ink,
                ),
                labelLarge = TextStyle(
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = colors.ink,
                ),
                labelMedium = TextStyle(
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = colors.mute,
                ),
                labelSmall = TextStyle(
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    color = colors.mute,
                ),
            ),
            content = {
                CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
                    content()
                }
            },
        )
    }
}

@Composable
fun SlipFieldBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.brush()),
    ) {
        content()
    }
}
