package com.goodyaoshi.parentsgames.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodyaoshi.parentsgames.R
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography
import com.goodyaoshi.parentsgames.core.ui.ContinueBanner
import com.goodyaoshi.parentsgames.core.ui.GameCard
import com.goodyaoshi.parentsgames.core.ui.HomeTopBar
import com.goodyaoshi.parentsgames.domain.model.GameType

@Composable
fun HomeScreen(
    onGameJigsaw: () -> Unit,
    onGameMahjong: () -> Unit,
    onGameMatch3: () -> Unit,
    onGameColorSort: () -> Unit,
    onGameSolitaire: () -> Unit,
    onGameMinesweeper: () -> Unit,
    onGameTetris: () -> Unit,
    onGameFrogger: () -> Unit,
    onGameXiangqi: () -> Unit,
    onGameGomoku: () -> Unit,
    onGameSudoku: () -> Unit,
    onGameKlotski: () -> Unit,
    onGameMemory: () -> Unit,
    onStatsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        greeting = state.weekdayLabel,
        continueGame = state.continueGame,
        onGameJigsaw = onGameJigsaw,
        onGameMahjong = onGameMahjong,
        onGameMatch3 = onGameMatch3,
        onGameColorSort = onGameColorSort,
        onGameSolitaire = onGameSolitaire,
        onGameMinesweeper = onGameMinesweeper,
        onGameTetris = onGameTetris,
        onGameFrogger = onGameFrogger,
        onGameXiangqi = onGameXiangqi,
        onGameGomoku = onGameGomoku,
        onGameSudoku = onGameSudoku,
        onGameKlotski = onGameKlotski,
        onGameMemory = onGameMemory,
        onStatsClick = onStatsClick,
        onSettingsClick = onSettingsClick,
        onContinueClick = {
            when (state.continueGame?.gameType) {
                GameType.JIGSAW -> onGameJigsaw()
                GameType.MAHJONG -> onGameMahjong()
                GameType.MATCH3 -> onGameMatch3()
                GameType.COLOR_SORT -> onGameColorSort()
                GameType.SOLITAIRE -> onGameSolitaire()
                GameType.MINESWEEPER -> onGameMinesweeper()
                GameType.TETRIS -> onGameTetris()
                GameType.FROGGER -> onGameFrogger()
                GameType.XIANGQI -> onGameXiangqi()
                GameType.GOMOKU -> onGameGomoku()
                GameType.SUDOKU -> onGameSudoku()
                GameType.KLOTSKI -> onGameKlotski()
                GameType.MEMORY -> onGameMemory()
                null -> {}
            }
        },
    )
}

@Composable
private fun HomeContent(
    greeting: String,
    continueGame: ContinueGameInfo?,
    onGameJigsaw: () -> Unit,
    onGameMahjong: () -> Unit,
    onGameMatch3: () -> Unit,
    onGameColorSort: () -> Unit,
    onGameSolitaire: () -> Unit,
    onGameMinesweeper: () -> Unit,
    onGameTetris: () -> Unit,
    onGameFrogger: () -> Unit,
    onGameXiangqi: () -> Unit,
    onGameGomoku: () -> Unit,
    onGameSudoku: () -> Unit,
    onGameKlotski: () -> Unit,
    onGameMemory: () -> Unit,
    onStatsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onContinueClick: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
        HomeTopBar(
            greeting = greeting,
            onRecordsClick = onStatsClick,
            onSettingsClick = onSettingsClick,
        )

        Column(modifier = Modifier.padding(horizontal = theme.spacing.lg)) {
            continueGame?.let {
                ContinueBanner(
                    title = "继续",
                    subtitle = it.title,
                    onClick = onContinueClick,
                    icon = Icons.Outlined.PlayArrow,
                )
                Spacer(Modifier.height(theme.spacing.md))
            }

            Text(
                "选一个游戏开始吧",
                color = theme.color.textSecondary,
                style = typo.labelMd,
                modifier = Modifier.padding(start = theme.spacing.xxs, bottom = theme.spacing.sm),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = theme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(theme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.sm),
            ) {
                item {
                    GameCard(
                        title = "中国象棋",
                        subtitle = "和人机对弈",
                        onClick = onGameXiangqi,
                        illustration = { Illustration(R.drawable.illust_xiangqi) },
                    )
                }
                item {
                    GameCard(
                        title = "五子棋",
                        subtitle = "五子连线",
                        onClick = onGameGomoku,
                        illustration = { Illustration(R.drawable.illust_gomoku) },
                    )
                }
                item {
                    GameCard(
                        title = "华容道",
                        subtitle = "送曹操出去",
                        onClick = onGameKlotski,
                        illustration = { Illustration(R.drawable.illust_klotski) },
                    )
                }
                item {
                    GameCard(
                        title = "数独",
                        subtitle = "填数字动动脑",
                        onClick = onGameSudoku,
                        illustration = { Illustration(R.drawable.illust_sudoku) },
                    )
                }
                item {
                    GameCard(
                        title = "翻牌配对",
                        subtitle = "翻牌找相同",
                        onClick = onGameMemory,
                        illustration = { Illustration(R.drawable.illust_memory) },
                    )
                }
                item {
                    GameCard(
                        title = "拼图",
                        subtitle = "你的照片",
                        onClick = onGameJigsaw,
                        illustration = { Illustration(R.drawable.illust_jigsaw) },
                    )
                }
                item {
                    GameCard(
                        title = "麻将连连看",
                        subtitle = "找出相同的牌",
                        onClick = onGameMahjong,
                        illustration = { Illustration(R.drawable.illust_mahjong) },
                    )
                }
                item {
                    GameCard(
                        title = "宝石消除",
                        subtitle = "3个或更多相同",
                        onClick = onGameMatch3,
                        illustration = { Illustration(R.drawable.illust_match3) },
                    )
                }
                item {
                    GameCard(
                        title = "颜色分类",
                        subtitle = "彩色试管",
                        onClick = onGameColorSort,
                        illustration = { Illustration(R.drawable.illust_colorsort) },
                    )
                }
                item {
                    GameCard(
                        title = "纸牌",
                        subtitle = "经典克朗代克",
                        onClick = onGameSolitaire,
                        illustration = { Illustration(R.drawable.illust_solitaire) },
                    )
                }
                item {
                    GameCard(
                        title = "扫雷",
                        subtitle = "找出炸弹",
                        onClick = onGameMinesweeper,
                        illustration = { Illustration(R.drawable.illust_minesweeper) },
                    )
                }
                item {
                    GameCard(
                        title = "俄罗斯方块",
                        subtitle = "堆叠方块",
                        onClick = onGameTetris,
                        illustration = { Illustration(R.drawable.illust_tetris) },
                    )
                }
                item {
                    GameCard(
                        title = "青蛙过马路",
                        subtitle = "穿过马路",
                        onClick = onGameFrogger,
                        illustration = { Illustration(R.drawable.illust_frogger) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Illustration(@androidx.annotation.DrawableRes resId: Int) {
    Image(
        painter = painterResource(id = resId),
        contentDescription = null,
        modifier = Modifier.size(96.dp),
        contentScale = ContentScale.Fit,
    )
}
