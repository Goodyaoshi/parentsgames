package com.goodyaoshi.parentsgames.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun FatimaGamesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val theme = AppTheme(
        color = colors,
        isDark = darkTheme,
        reduceMotion = reduceMotion,
    )

    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.textInverse,
            background = colors.bgCanvas,
            onBackground = colors.textPrimary,
            surface = colors.bgSurface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.bgSurfaceMuted,
            outline = colors.borderStrong,
            error = colors.feedbackError,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.textInverse,
            background = colors.bgCanvas,
            onBackground = colors.textPrimary,
            surface = colors.bgSurface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.bgSurfaceMuted,
            outline = colors.borderStrong,
            error = colors.feedbackError,
        )
    }

    CompositionLocalProvider(
        LocalAppTheme provides theme,
        LocalAppTypography provides DefaultAppTypography,
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = materialTypographyFrom(DefaultAppTypography),
            content = content,
        )
    }
}
