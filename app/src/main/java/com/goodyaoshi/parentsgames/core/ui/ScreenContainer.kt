package com.goodyaoshi.parentsgames.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme

/**
 * Container padrão para telas raiz que respeita system bars (status + nav).
 *
 * Uso: em vez de `Column(modifier = Modifier.fillMaxSize().background(...))`,
 * use `ScreenContainer { ... }`.
 */
@Composable
fun ScreenContainer(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val theme = LocalAppTheme.current
    val bg = backgroundColor ?: theme.color.bgCanvas
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .systemBarsPadding(),
    ) {
        content()
    }
}
