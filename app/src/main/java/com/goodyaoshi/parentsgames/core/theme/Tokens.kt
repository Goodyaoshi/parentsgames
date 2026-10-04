package com.goodyaoshi.parentsgames.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens do design system — fonte única da verdade para cores, espaçamento,
 * raios e elevação. Tipografia vem em [AppTypography], motion em [AppMotion].
 *
 * Use sempre via [LocalAppTheme.current.color.x] em vez de hex hardcoded.
 */
@Immutable
data class AppColors(
    val bgCanvas: Color,
    val bgSurface: Color,
    val bgSurfaceMuted: Color,
    val bgTubeWell: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val primary: Color,
    val primaryPressed: Color,
    val accentTerracotta: Color,
    val accentPlum: Color,
    val accentGold: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val textInverse: Color,
    val feedbackSuccess: Color,
    val feedbackWarning: Color,
    val feedbackError: Color,
)

val LightColors = AppColors(
    bgCanvas = Color(0xFFFAF6F0),
    bgSurface = Color(0xFFFFFFFF),
    bgSurfaceMuted = Color(0xFFF2EBE0),
    bgTubeWell = Color(0xFFE9E1D3),
    borderSubtle = Color(0xFFE6DAC6),
    borderStrong = Color(0xFFC7B89F),
    primary = Color(0xFFC03A2B),
    primaryPressed = Color(0xFF992A1F),
    accentTerracotta = Color(0xFFC97B5C),
    accentPlum = Color(0xFF7B5E8C),
    accentGold = Color(0xFFD4A24A),
    textPrimary = Color(0xFF2C2A26),
    textSecondary = Color(0xFF6B665E),
    textDisabled = Color(0xFFA39E94),
    textInverse = Color(0xFFFAF6F0),
    feedbackSuccess = Color(0xFF5E8C5B),
    feedbackWarning = Color(0xFFC9924A),
    feedbackError = Color(0xFFB8523A),
)

val DarkColors = AppColors(
    bgCanvas = Color(0xFF1C1A17),
    bgSurface = Color(0xFF26231F),
    bgSurfaceMuted = Color(0xFF2A2722),
    bgTubeWell = Color(0xFF1F1C19),
    borderSubtle = Color(0xFF3A3631),
    borderStrong = Color(0xFF4D4842),
    primary = Color(0xFFE06A55),
    primaryPressed = Color(0xFFB84A38),
    accentTerracotta = Color(0xFFD89578),
    accentPlum = Color(0xFF9B7BAA),
    accentGold = Color(0xFFE5B970),
    textPrimary = Color(0xFFF2EBE0),
    textSecondary = Color(0xFFB5AEA1),
    textDisabled = Color(0xFF6B665E),
    textInverse = Color(0xFF2C2A26),
    feedbackSuccess = Color(0xFF8AB489),
    feedbackWarning = Color(0xFFE0AC65),
    feedbackError = Color(0xFFD17357),
)

@Immutable
data class AppSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val xxxl: Dp = 64.dp,
)

@Immutable
data class AppRadii(
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 28.dp,
)

@Immutable
data class AppElevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 2.dp,
    val level2: Dp = 6.dp,
)

@Immutable
data class AppMotion(
    val microMs: Int = 120,
    val standardMs: Int = 240,
    val slowMs: Int = 400,
    val celebrationMs: Int = 600,
)

@Immutable
data class AppTheme(
    val color: AppColors,
    val spacing: AppSpacing = AppSpacing(),
    val radii: AppRadii = AppRadii(),
    val elevation: AppElevation = AppElevation(),
    val motion: AppMotion = AppMotion(),
    val isDark: Boolean = false,
    val reduceMotion: Boolean = false,
)

val LocalAppTheme = compositionLocalOf<AppTheme> {
    error("AppTheme not provided")
}
