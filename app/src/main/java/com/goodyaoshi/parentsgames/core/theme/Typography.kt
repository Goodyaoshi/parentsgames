package com.goodyaoshi.parentsgames.core.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tipografia do app. Usamos as famílias do sistema (sans-serif e serif)
 * como fallback. Para fontes customizadas (Fraunces, Inter), adicione os
 * arquivos .ttf em res/font/ e troque as referências aqui.
 */

private val DisplayFamily = FontFamily.Serif        // futuro: Fraunces
private val BodyFamily = FontFamily.SansSerif       // futuro: Inter

@Immutable
data class AppTypography(
    val displayLg: TextStyle,
    val displayMd: TextStyle,
    val titleLg: TextStyle,
    val titleMd: TextStyle,
    val titleSm: TextStyle,
    val bodyLg: TextStyle,
    val bodyMd: TextStyle,
    val labelLg: TextStyle,
    val labelMd: TextStyle,
    val numericLg: TextStyle,
    val numericMd: TextStyle,
)

val DefaultAppTypography = AppTypography(
    displayLg = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.W600, fontSize = 42.sp, lineHeight = 52.sp),
    displayMd = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.W500, fontSize = 32.sp, lineHeight = 42.sp),
    titleLg = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.W600, fontSize = 25.sp, lineHeight = 32.sp),
    titleMd = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.W600, fontSize = 23.sp, lineHeight = 30.sp),
    titleSm = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.W600, fontSize = 21.sp, lineHeight = 28.sp),
    bodyLg = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.W400, fontSize = 20.sp, lineHeight = 30.sp),
    bodyMd = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.W400, fontSize = 18.sp, lineHeight = 28.sp),
    labelLg = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.W500, fontSize = 18.sp, lineHeight = 24.sp),
    labelMd = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.W500, fontSize = 16.sp, lineHeight = 22.sp),
    numericLg = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.W600, fontSize = 32.sp, lineHeight = 38.sp),
    numericMd = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.W600, fontSize = 23.sp, lineHeight = 28.sp),
)

val LocalAppTypography = compositionLocalOf<AppTypography> { DefaultAppTypography }

/** Bridge para Material 3 — alguns componentes Material consomem [Typography]. */
internal fun materialTypographyFrom(t: AppTypography) = Typography(
    headlineLarge = t.displayLg,
    headlineMedium = t.displayMd,
    titleLarge = t.titleLg,
    titleMedium = t.titleMd,
    titleSmall = t.titleSm,
    bodyLarge = t.bodyLg,
    bodyMedium = t.bodyMd,
    labelLarge = t.labelLg,
    labelMedium = t.labelMd,
)
