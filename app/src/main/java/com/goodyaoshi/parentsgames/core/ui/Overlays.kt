package com.goodyaoshi.parentsgames.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography

@Composable
fun WinOverlay(
    timeLabel: String,
    secondaryLabel: String,
    secondaryValue: String,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "win-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        label = "win-alpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f * alpha))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Confetti()

        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                .clip(RoundedCornerShape(theme.radii.xl))
                .background(theme.color.bgSurface)
                .padding(vertical = theme.spacing.xl, horizontal = theme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Medalha dourada
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(theme.color.accentGold),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.EmojiEvents,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(theme.spacing.md))
            Text("干得好！", color = theme.color.textPrimary, style = typo.displayLg)
            Spacer(Modifier.height(theme.spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatCard(label = "用时", value = timeLabel, modifier = Modifier.weight(1f))
                Spacer(Modifier.size(theme.spacing.sm))
                StatCard(label = secondaryLabel, value = secondaryValue, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(theme.spacing.lg))
            PrimaryButton(text = "再玩一局", onClick = onPlayAgain, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(theme.spacing.sm))
            TertiaryButton(text = "返回首页", onClick = onHome, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun PauseOverlay(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(theme.radii.xl))
                .background(theme.color.bgSurface)
                .padding(theme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("已暂停", color = theme.color.textPrimary, style = typo.displayMd)
            Spacer(Modifier.height(theme.spacing.lg))
            PrimaryButton(text = "继续", onClick = onResume, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(theme.spacing.sm))
            SecondaryButton(text = "重新开始", onClick = onRestart, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(theme.spacing.sm))
            TertiaryButton(text = "退出", onClick = onExit, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ConfirmExitDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(theme.radii.xl))
                .background(theme.color.bgSurface)
                .clickable(enabled = false) {}
                .padding(theme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = theme.color.textPrimary, style = typo.titleLg)
            Spacer(Modifier.height(theme.spacing.sm))
            Text(message, color = theme.color.textSecondary, style = typo.bodyMd)
            Spacer(Modifier.height(theme.spacing.lg))
            PrimaryButton(text = "继续游戏", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(theme.spacing.sm))
            TertiaryButton(text = "仍然退出", onClick = onConfirm, modifier = Modifier.fillMaxWidth())
        }
    }
}
