package com.goodyaoshi.parentsgames.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Confete sutil para a tela de vitória.
 * Partículas caem suavemente da parte superior com leve rotação e cores da paleta.
 */
@Composable
fun Confetti(particleCount: Int = 30, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val particles = remember(particleCount) {
        val rng = Random(System.currentTimeMillis())
        List(particleCount) {
            ConfettiParticle(
                xRel = rng.nextFloat(),
                size = 6f + rng.nextFloat() * 8f,
                rotateBase = rng.nextFloat() * 360f,
                rotateSpeed = (rng.nextFloat() - 0.5f) * 360f,
                fallDelay = rng.nextFloat() * 0.6f,
                fallDuration = 1.6f + rng.nextFloat() * 1.4f,
                color = listOf(
                    theme.color.primary,
                    theme.color.accentGold,
                    theme.color.accentTerracotta,
                    theme.color.accentPlum,
                ).random(rng),
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "confetti")
    val tick by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 2400, easing = LinearEasing)),
        label = "confetti-tick",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        for (p in particles) {
            val phase = ((tick + p.fallDelay) % 1f)
            val y = phase * size.height
            val x = p.xRel * size.width
            val rot = p.rotateBase + tick * p.rotateSpeed
            rotate(degrees = rot, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(x - p.size / 2f, y - p.size / 2f),
                    size = Size(p.size, p.size * 0.55f),
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val xRel: Float,
    val size: Float,
    val rotateBase: Float,
    val rotateSpeed: Float,
    val fallDelay: Float,
    val fallDuration: Float,
    val color: Color,
)
