package com.personaldiary.android.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.personaldiary.android.R

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val notoSansSc = GoogleFont("Noto Sans SC")

/** Body / UI / brand — Noto Sans SC only. */
val AppFontFamily: FontFamily = FontFamily(
    Font(googleFont = notoSansSc, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = notoSansSc, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = notoSansSc, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = notoSansSc, fontProvider = googleFontProvider, weight = FontWeight.Bold),
    Font(googleFont = notoSansSc, fontProvider = googleFontProvider, weight = FontWeight.ExtraBold),
)

/** Display titles use the same sans family (weight/size carry hierarchy). */
val DisplayFontFamily: FontFamily = AppFontFamily
