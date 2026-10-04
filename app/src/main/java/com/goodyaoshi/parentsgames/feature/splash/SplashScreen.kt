package com.goodyaoshi.parentsgames.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// 品牌红：与桌面启动图标同一色系（红白黑）
private val SplashBrandRed = Color(0xFFC03A2B)

@Composable
fun AnimatedSplashScreen(onFinished: () -> Unit) {
    val theme = LocalAppTheme.current

    val logoScale = remember { Animatable(0.4f) }
    val logoAlpha = remember { Animatable(0f) }
    val tagAlpha = remember { Animatable(0f) }
    val particlesProgress = remember { Animatable(0f) }
    val finalFade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Particles aparecem desde o início
        particlesProgress.animateTo(1f, animationSpec = tween(durationMillis = 1800, easing = LinearEasing))
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        // Logo cresce com bounce
        logoAlpha.animateTo(1f, animationSpec = tween(durationMillis = 400))
        logoScale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(700)
        tagAlpha.animateTo(1f, animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2200)
        finalFade.animateTo(0f, animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing))
        onFinished()
    }

    val particles = remember {
        val rng = Random(42)
        List(28) {
            FloatingPiece(
                xRel = rng.nextFloat(),
                yStart = 1.2f + rng.nextFloat() * 0.5f,
                yEnd = -0.4f - rng.nextFloat() * 0.3f,
                size = 16f + rng.nextFloat() * 28f,
                rotateBase = rng.nextFloat() * 360f,
                rotateSpeed = (rng.nextFloat() - 0.5f) * 540f,
                color = listOf(
                    SplashBrandRed,
                    theme.color.accentTerracotta,
                    theme.color.accentGold,
                    Color(0xFF2C2A26),
                ).random(rng),
                shapeKind = rng.nextInt(3),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.color.bgCanvas)
            .graphicsLayer { alpha = finalFade.value },
    ) {
        // Camada de partículas: peças de puzzle/gemas/folhas voando
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (p in particles) {
                val t = particlesProgress.value
                val y = (p.yStart + (p.yEnd - p.yStart) * t) * size.height
                val x = p.xRel * size.width + sin((t + p.rotateBase / 360f) * PI.toFloat() * 2f) * size.width * 0.05f
                val rot = p.rotateBase + p.rotateSpeed * t
                drawSplashShape(
                    kind = p.shapeKind,
                    cx = x,
                    cy = y,
                    size = p.size,
                    rotationDeg = rot,
                    color = p.color.copy(alpha = (1f - kotlin.math.abs(t - 0.5f) * 1.6f).coerceIn(0.2f, 0.85f)),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Símbolo
            Box(
                modifier = Modifier
                    .height(112.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f
                    drawSplashBrand(cx = cx, cy = cy, scale = h, primary = SplashBrandRed)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "爸妈游戏厅",
                color = theme.color.textPrimary,
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.W600,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.graphicsLayer { alpha = tagAlpha.value },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "专为爸妈打造 · 慢慢玩，天天开心",
                color = theme.color.textSecondary,
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W500,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.graphicsLayer { alpha = tagAlpha.value },
            )
        }
    }
}

private data class FloatingPiece(
    val xRel: Float,
    val yStart: Float,
    val yEnd: Float,
    val size: Float,
    val rotateBase: Float,
    val rotateSpeed: Float,
    val color: Color,
    val shapeKind: Int,
)

private fun DrawScope.drawSplashShape(
    kind: Int,
    cx: Float,
    cy: Float,
    size: Float,
    rotationDeg: Float,
    color: Color,
) {
    rotate(degrees = rotationDeg, pivot = Offset(cx, cy)) {
        when (kind) {
            0 -> {
                // Quadrado arredondado (puzzle piece)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cx - size / 2f, cy - size / 2f),
                    size = Size(size, size),
                    cornerRadius = CornerRadius(size * 0.2f, size * 0.2f),
                )
            }
            1 -> {
                // Círculo (gema)
                drawCircle(color, radius = size / 2f, center = Offset(cx, cy))
            }
            2 -> {
                // Losango (gem alt)
                val path = Path().apply {
                    moveTo(cx, cy - size / 2f)
                    lineTo(cx + size / 2f, cy)
                    lineTo(cx, cy + size / 2f)
                    lineTo(cx - size / 2f, cy)
                    close()
                }
                drawPath(path, color)
            }
        }
    }
}

/**
 * 闪屏品牌标志：红色圆角方块上放一张白色麻将牌（红中），右下角一颗五子棋黑子。
 * 与桌面启动图标同一设计语言。
 */
private fun DrawScope.drawSplashBrand(
    cx: Float,
    cy: Float,
    scale: Float,
    primary: Color,
) {
    // 红色圆角方块（应用标底）
    val s = scale * 0.9f
    drawRoundRect(
        color = primary,
        topLeft = Offset(cx - s / 2f, cy - s / 2f),
        size = Size(s, s),
        cornerRadius = CornerRadius(s * 0.24f, s * 0.24f),
    )
    // 白色麻将牌
    val t = s * 0.60f
    val tx = cx - t / 2f - s * 0.045f
    val ty = cy - t / 2f - s * 0.045f
    drawRoundRect(
        color = Color(0xFFFFFEF9),
        topLeft = Offset(tx, ty),
        size = Size(t, t),
        cornerRadius = CornerRadius(t * 0.16f, t * 0.16f),
    )
    // 红中：竖笔
    val barW = t * 0.16f
    val barH = t * 0.70f
    drawRect(
        color = Color(0xFFC03A2B),
        topLeft = Offset(tx + t / 2f - barW / 2f, ty + t / 2f - barH / 2f),
        size = Size(barW, barH),
    )
    // 红中：中框
    val boxW = t * 0.52f
    val boxH = t * 0.34f
    drawRect(
        color = Color(0xFFC03A2B),
        topLeft = Offset(tx + t / 2f - boxW / 2f, ty + t / 2f - boxH / 2f),
        size = Size(boxW, boxH),
        style = Stroke(width = t * 0.09f),
    )
    // 五子棋黑子（压在牌的右下角）
    val sr = s * 0.13f
    val sx = cx + s * 0.22f
    val sy = cy + s * 0.22f
    drawCircle(Color(0xFF2C2A26), radius = sr, center = Offset(sx, sy))
    drawCircle(
        Color(0xB3FFFFFF),
        radius = sr * 0.30f,
        center = Offset(sx - sr * 0.32f, sy - sr * 0.32f),
    )
}
