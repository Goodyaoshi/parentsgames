package com.goodyaoshi.parentsgames.feature.games.solitaire.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.feature.games.solitaire.domain.Card

private val Back1 = Color(0xFF4F6A53)
private val Back2 = Color(0xFF2C3E33)
private val FaceBg = Color(0xFFFFFEF7)
private val RedInk = Color(0xFFB8351F)
private val BlackInk = Color(0xFF1F1B17)
private val HintGold = Color(0xFFD4A24A)

/**
 * 纸牌渲染。
 * @param selected 选中态：金色粗描边 + 轻微放大，让老人一眼看出"这张已拿起"。
 * @param hintTarget 合法去处提示：淡金描边 + 淡金底色。
 * @param glow 亮光提示：这张牌当前有合法去处，柔和金色呼吸微光（reduceMotion 时为静态亮边）。
 * @param dimmed 拖拽中：源牌组压暗。
 */
@Composable
fun PlayingCard(
    card: Card?,
    width: Dp = 62.dp,
    height: Dp = 88.dp,
    selected: Boolean = false,
    placeholder: Boolean = false,
    hintTarget: Boolean = false,
    glow: Boolean = false,
    dimmed: Boolean = false,
) {
    val cornerShape = RoundedCornerShape(6.dp)
    val layerAlpha = if (dimmed) 0.35f else 1f
    // 亮光呼吸：1.6 秒缓慢明暗；系统开了"减弱动态效果"就给静态亮边；
    // 不发光的牌不启动动画，避免整副牌都在空跑动画帧
    val reduceMotion = LocalAppTheme.current.reduceMotion
    val glowAlpha: Float
    if (!glow) {
        glowAlpha = 0f
    } else if (reduceMotion) {
        glowAlpha = 0.55f
    } else {
        glowAlpha = rememberInfiniteTransition(label = "cardGlow").animateFloat(
            initialValue = 0.25f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "cardGlowAlpha",
        ).value
    }
    if (card == null) {
        // Placeholder (slot vazio)
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = layerAlpha }
                .size(width = width, height = height)
                .clip(cornerShape)
                .background(if (hintTarget) HintGold.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f))
                .border(
                    if (hintTarget) 2.dp else 1.dp,
                    if (hintTarget) HintGold.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.25f),
                    cornerShape,
                ),
        )
        return
    }
    if (!card.faceUp) {
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = layerAlpha }
                .size(width = width, height = height)
                .clip(cornerShape)
                .background(Back1)
                .border(1.dp, Color.Black.copy(alpha = 0.4f), cornerShape),
        ) {
            // Padrão de diamantes simples
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Back2)
                    .border(1.dp, Color(0xFFD4A24A).copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("♣", color = Color(0xFFD4A24A).copy(alpha = 0.7f), fontWeight = FontWeight.W700)
            }
        }
        return
    }
    val ink = if (card.suit.isRed) RedInk else BlackInk
    val borderColor = when {
        selected -> HintGold
        hintTarget -> HintGold.copy(alpha = 0.55f)
        else -> Color.Black.copy(alpha = 0.4f)
    }
    val borderW = when {
        selected -> 3.dp
        hintTarget -> 2.dp
        else -> 1.dp
    }
    Box(modifier = Modifier.size(width = width, height = height)) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    alpha = layerAlpha
                    if (selected) {
                        scaleX = 1.06f
                        scaleY = 1.06f
                    }
                }
                .fillMaxSize()
                .clip(cornerShape)
                .background(FaceBg)
                .border(borderW, borderColor, cornerShape)
                .padding(4.dp),
        ) {
            // Topo esquerda: rank + suit pequenos
            Row {
                Text(
                    card.rank.symbol,
                    color = ink,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W700),
                )
                Text(
                    card.suit.symbol,
                    color = ink,
                    style = TextStyle(fontSize = 14.sp),
                )
            }
            Spacer(Modifier.height(2.dp))
            // Centro: naipe grande
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    card.suit.symbol,
                    color = ink,
                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.W600),
                )
            }
        }
        if (glowAlpha > 0f) {
            // 柔和金色微光：淡金罩 + 呼吸描边，告诉老人"这张牌能动"
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(cornerShape)
                    .background(HintGold.copy(alpha = glowAlpha * 0.18f)),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(cornerShape)
                    .border(2.dp, HintGold.copy(alpha = 0.30f + 0.45f * glowAlpha), cornerShape),
            )
        }
    }
}
