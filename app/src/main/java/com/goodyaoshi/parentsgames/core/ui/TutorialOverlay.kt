package com.goodyaoshi.parentsgames.core.ui

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography

data class TutorialStep(
    val title: String,
    val body: String,
)

@Composable
fun TutorialOverlay(
    steps: List<TutorialStep>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    var index by remember { mutableIntStateOf(0) }
    val current = steps[index]
    val isLast = index == steps.lastIndex

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(theme.radii.xl))
                .background(theme.color.bgSurface)
                .padding(theme.spacing.lg),
        ) {
            // Indicadores de passo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                steps.forEachIndexed { i, _ ->
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (i == index) theme.color.primary
                                else theme.color.bgSurfaceMuted
                            ),
                    )
                }
            }
            Spacer(Modifier.height(theme.spacing.md))
            Text(current.title, color = theme.color.textPrimary, style = typo.titleLg)
            Spacer(Modifier.height(theme.spacing.xs))
            Text(current.body, color = theme.color.textSecondary, style = typo.bodyLg)
            Spacer(Modifier.height(theme.spacing.lg))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(theme.spacing.sm)) {
                TertiaryButton(
                    text = "跳过",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = if (isLast) "开始" else "下一步",
                    onClick = {
                        if (isLast) onDismiss() else index++
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 统一的教学弹窗容器：首次进入（TutorialFirstTime）和「玩法」按钮（HelpButton）都必须走这一个容器。
 * 之前两条路径一个画在页面里、一个包在 Dialog 里，弹窗高度始终对不齐；
 * 现在共用同一个 Dialog 包装，高度、位置、宽度在结构上保证完全一致。
 */
@Composable
fun TutorialDialog(
    steps: List<TutorialStep>,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            TutorialOverlay(
                steps = steps,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

object TutorialContent {
    val jigsaw = listOf(
        TutorialStep("欢迎来到拼图", "拼图开始时会散放在桌面上，您可以自己挑照片，决定要拼多少块。"),
        TutorialStep("拖动拼块", "按住一块拼图拖动，靠近正确位置时，它就会自动吸上去。"),
        TutorialStep("不用着急", "随时可以暂停，进度会自动保存。"),
    )
    val mahjong = listOf(
        TutorialStep("麻将连连看", "把两块相同的麻将配对消除，消完全部就赢了。"),
        TutorialStep("什么样的牌能选", "头顶上没有牌压着、旁边至少有一侧空着的麻将，才能点选。"),
        TutorialStep("卡住了怎么办", "点“提示”找找好办法，点“撤销”退回上一步，也可以“洗牌”重新摆一摆。"),
    )
    val match3 = listOf(
        TutorialStep("凑齐 3 个就消", "点一颗宝石，再点它旁边的一颗，两颗就交换位置。连成一排 3 个相同的，就能消除得分。"),
        TutorialStep("特殊宝石", "一次凑齐 4 个或 5 个，会生成特殊宝石，能清除整行、整列或一大片区域。"),
        TutorialStep("不限时慢慢玩", "这个游戏没有时间限制，也不会结束，按自己的节奏慢慢玩就好。"),
    )
    val colorSort = listOf(
        TutorialStep("把颜色分类", "目标很简单：让每根试管里只剩一种颜色。"),
        TutorialStep("怎么倒", "先点一根试管（它会抬起来），再点要倒过去的那根。只有对方是空管，或者最上面颜色相同时，才能倒。"),
        TutorialStep("帮手多多", "点错了可以无限次撤销，还能多要一根空试管来帮忙。"),
    )
    val solitaire = listOf(
        TutorialStep("跟着亮光玩", "发亮光的牌就是现在能动、能得分牌：点一下它，能去的地方也会亮起来，再点一下亮的地方，牌就飞过去了。"),
        TutorialStep("把牌收回家", "目标是按 A、2、3……从小到大的顺序，把四种花色分别收进右上角的四个家里。不用背规则，跟着亮光点就行。"),
        TutorialStep("翻新牌与演示", "左上角牌堆点一下就翻出新牌，翻完了再点一下整叠重新来。想先看怎么玩，点「看演示」让电脑玩给您看。"),
    )
    val minesweeper = listOf(
        TutorialStep("找出所有的雷", "点一下格子就能翻开。格子上的数字表示它周围藏着几颗雷。第一步永远不会踩雷。"),
        TutorialStep("插旗做记号", "确定哪格是雷就长按它插面小旗子做记号，也可以用“插旗模式”按钮来插旗。"),
        TutorialStep("卡住了点提示", "点「提示」会帮您亮出一个安全格子；想先看怎么玩，点「看演示」让电脑玩给您看。"),
    )
    val tetris = listOf(
        TutorialStep("堆叠方块", "方块会从顶上落下来，把一整行填满就能消掉得分。"),
        TutorialStep("怎么操作", "用下面的按钮：左右键移动，旋转键转向，下落键加速，▼ 键一落到底。"),
        TutorialStep("小技巧", "点一下屏幕 = 旋转，拖动 = 移动，双击 = 立刻落到底。"),
    )
    val frogger = listOf(
        TutorialStep("小心过马路", "用方向键移动小青蛙，千万当心路上的汽车！"),
        TutorialStep("过河", "过河要跳到浮木上站好，掉进水里会少一条命。"),
        TutorialStep("游戏目标", "送小青蛙到达顶部 5 次就算获胜，一共有 3 条命。"),
    )
    val xiangqi = listOf(
        TutorialStep("中国象棋", "红方先走，棋子按各自的规则在交叉点上走：车走直线、马走日、象走田、炮要隔子吃。"),
        TutorialStep("怎么走棋", "先点自己的棋子（会亮起来），再点要去的位置。点“提示”可以让程序帮您想一步。"),
        TutorialStep("怎么赢", "把对方的“将/帅”将死就赢了。您可以选择和人机下，也可以两人一起下。"),
    )
    val gomoku = listOf(
        TutorialStep("五子棋", "黑方先下，双方轮流在棋盘交叉点上放棋子。"),
        TutorialStep("怎么赢", "谁先把自己的五个棋子连成一条直线（横、竖、斜都算），谁就获胜。"),
        TutorialStep("怎么下棋", "直接点棋盘上想放的位置就行。点“提示”可以让程序帮您想一步，也可以两人轮流下。"),
    )
    val sudoku = listOf(
        TutorialStep("数独", "把空格填上数字，让每一行、每一列、每个粗线小宫格里都是 1 到 9，不能重复。"),
        TutorialStep("怎么填", "点一个空格，再点下面数字键盘上想填的数。填错了会标红提示您，随时可以改。"),
        TutorialStep("不着急", "可以先从数字多的行、列入手。卡住了点“提示”帮您填一格，点「看演示」让电脑玩给您看。"),
    )
    val klotski = listOf(
        TutorialStep("华容道", "棋盘上有大小不一的木块，最大的方块是曹操。目标：把曹操移到底部出口逃出去。"),
        TutorialStep("怎么移", "点一块木块，再点想让它去的位置，只要路上没有别的木块挡着就能滑过去。"),
        TutorialStep("卡住了", "点“悔棋”退回上一步，点“重来”换一种摆法从头开始。多试几次就有感觉了。"),
    )
    val memory = listOf(
        TutorialStep("翻牌配对", "牌都背面朝上摆在桌上，每次翻两张。"),
        TutorialStep("怎么玩", "两张一样就配对成功，会留在桌面上；不一样就会重新盖回去。把所有对子都找出来就赢了。"),
        TutorialStep("小诀窍", "记住每张牌的位置是关键，慢慢翻、用心记，不着急。"),
    )
}
