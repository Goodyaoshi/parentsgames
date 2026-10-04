package com.goodyaoshi.parentsgames.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography

/**
 * Bolha de pontos que aparece, sobe e desaparece — feedback positivo após
 * matches, encaixes ou ações pontuáveis.
 */
@Composable
fun FloatingScore(
    text: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    var animKey by remember { mutableStateOf(0) }
    LaunchedEffect(visible) {
        if (visible) animKey++
    }
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    val transition = updateTransition(targetState = animKey, label = "floating-score")
    val translateY by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 900, easing = LinearEasing) },
        label = "y",
    ) { 0f }   // ignored; we want a per-trigger animation

    // Simpler approach with single-shot
    val yShift by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) -48f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "yshift",
    )
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(durationMillis = 800),
        label = "alpha",
    )

    Text(
        text,
        color = theme.color.accentGold,
        style = typo.numericMd,
        modifier = modifier
            .graphicsLayer {
                translationY = yShift
                this.alpha = alpha
            }
            .background(theme.color.bgSurface.copy(alpha = 0.9f), RoundedCornerShape(theme.radii.sm))
            .padding(horizontal = theme.spacing.xs, vertical = 2.dp),
    )
}
