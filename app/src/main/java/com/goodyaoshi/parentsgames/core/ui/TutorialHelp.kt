package com.goodyaoshi.parentsgames.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography

/**
 * 「玩法」按钮：点击随时重新打开教程弹窗。
 * 老人不需要一次记住玩法，每个游戏界面都放一个，想看就看。
 *
 * 用法：放在游戏底部按钮行里，与提示/悔棋等按钮并排：
 *   HelpButton(steps = TutorialContent.sudoku)
 */
@Composable
fun HelpButton(steps: List<TutorialStep>) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    var show by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(theme.radii.md))
            .clickable { show = true }
            .background(
                if (show) theme.color.primary.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent,
            )
            .padding(horizontal = theme.spacing.md, vertical = theme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.HelpOutline,
            contentDescription = "玩法说明",
            tint = theme.color.primaryPressed,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text("玩法", color = theme.color.textSecondary, style = typo.labelMd)
    }

    if (show) {
        // 与首次教程共用同一个 TutorialDialog 容器，保证弹窗高度/位置完全一致
        TutorialDialog(
            steps = steps,
            onDismiss = { show = false },
        )
    }
}
