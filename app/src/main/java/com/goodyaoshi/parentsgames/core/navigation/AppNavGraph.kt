package com.goodyaoshi.parentsgames.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.feature.games.colorsort.ColorSortScreen
import com.goodyaoshi.parentsgames.feature.games.frogger.FroggerScreen
import com.goodyaoshi.parentsgames.feature.games.gomoku.GomokuScreen
import com.goodyaoshi.parentsgames.feature.games.jigsaw.JigsawGameScreen
import com.goodyaoshi.parentsgames.feature.games.jigsaw.JigsawSetupScreen
import com.goodyaoshi.parentsgames.feature.games.klotski.KlotskiScreen
import com.goodyaoshi.parentsgames.feature.games.mahjong.MahjongScreen
import com.goodyaoshi.parentsgames.feature.games.match3.Match3Screen
import com.goodyaoshi.parentsgames.feature.games.memory.MemoryScreen
import com.goodyaoshi.parentsgames.feature.games.minesweeper.MinesweeperScreen
import com.goodyaoshi.parentsgames.feature.games.solitaire.SolitaireScreen
import com.goodyaoshi.parentsgames.feature.games.sudoku.SudokuScreen
import com.goodyaoshi.parentsgames.feature.games.tetris.TetrisScreen
import com.goodyaoshi.parentsgames.feature.games.xiangqi.XiangqiScreen
import com.goodyaoshi.parentsgames.feature.home.HomeScreen
import com.goodyaoshi.parentsgames.feature.photolibrary.PhotoLibraryScreen
import com.goodyaoshi.parentsgames.feature.splash.AnimatedSplashScreen
import com.goodyaoshi.parentsgames.feature.settings.SettingsScreen
import com.goodyaoshi.parentsgames.feature.stats.StatsScreen

@Composable
fun AppNavGraph() {
    val nav = rememberNavController()
    val theme = LocalAppTheme.current

    // 游戏进行中保持屏幕常亮：下棋思考久了不操作，屏幕也不会自动锁
    val view = LocalView.current
    val currentEntry by nav.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: ""
    val keepScreenOn = listOf(
        "MahjongGameRoute", "Match3GameRoute", "ColorSortGameRoute", "SolitaireRoute",
        "MinesweeperRoute", "TetrisRoute", "FroggerRoute", "XiangqiRoute", "GomokuRoute",
        "SudokuRoute", "KlotskiRoute", "MemoryRoute", "JigsawGameRoute", "JigsawSetupRoute",
    ).any { currentRoute.contains(it) }
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    NavHost(
        navController = nav,
        startDestination = SplashRoute,
        modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas),
    ) {
        composable<SplashRoute> {
            AnimatedSplashScreen(onFinished = {
                nav.navigate(HomeRoute) {
                    popUpTo(SplashRoute) { inclusive = true }
                }
            })
        }
        composable<HomeRoute> {
            HomeScreen(
                onGameJigsaw = { nav.navigate(JigsawSetupRoute) },
                onGameMahjong = { nav.navigate(MahjongGameRoute) },
                onGameMatch3 = { nav.navigate(Match3GameRoute) },
                onGameColorSort = { nav.navigate(ColorSortGameRoute()) },
                onGameSolitaire = { nav.navigate(SolitaireRoute) },
                onGameMinesweeper = { nav.navigate(MinesweeperRoute) },
                onGameTetris = { nav.navigate(TetrisRoute) },
                onGameFrogger = { nav.navigate(FroggerRoute) },
                onGameXiangqi = { nav.navigate(XiangqiRoute) },
                onGameGomoku = { nav.navigate(GomokuRoute) },
                onGameSudoku = { nav.navigate(SudokuRoute) },
                onGameKlotski = { nav.navigate(KlotskiRoute) },
                onGameMemory = { nav.navigate(MemoryRoute) },
                onStatsClick = { nav.navigate(StatsRoute) },
                onSettingsClick = { nav.navigate(SettingsRoute) },
            )
        }
        composable<StatsRoute> { StatsScreen(onBack = { nav.popBackStack() }) }
        composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable<PhotoLibraryRoute> {
            PhotoLibraryScreen(
                onBack = { nav.popBackStack() },
                onPhotoSelected = { id -> nav.navigate(JigsawGameRoute(id, 12)) },
            )
        }
        composable<JigsawSetupRoute> {
            JigsawSetupScreen(
                onBack = { nav.popBackStack() },
                onLaunch = { id, pc -> nav.navigate(JigsawGameRoute(id, pc)) },
            )
        }
        composable<JigsawGameRoute> { entry ->
            val r: JigsawGameRoute = entry.toRoute()
            JigsawGameScreen(
                photoId = r.photoId,
                pieceCount = r.pieceCount,
                onBack = { nav.popBackStack() },
                onHome = { nav.popBackStack(HomeRoute, inclusive = false) },
            )
        }
        composable<MahjongGameRoute> {
            MahjongScreen(onBack = { nav.popBackStack() }, onHome = { nav.popBackStack(HomeRoute, inclusive = false) })
        }
        composable<Match3GameRoute> {
            Match3Screen(onBack = { nav.popBackStack() }, onHome = { nav.popBackStack(HomeRoute, inclusive = false) })
        }
        composable<ColorSortGameRoute> { entry ->
            val r: ColorSortGameRoute = entry.toRoute()
            ColorSortScreen(
                stage = r.stage,
                onBack = { nav.popBackStack() },
                onHome = { nav.popBackStack(HomeRoute, inclusive = false) },
                onNextStage = { next ->
                    nav.navigate(ColorSortGameRoute(stage = next)) {
                        popUpTo(HomeRoute) { inclusive = false }
                    }
                },
            )
        }
        composable<SolitaireRoute> {
            SolitaireScreen(onBack = { nav.popBackStack() }, onHome = { nav.popBackStack(HomeRoute, inclusive = false) })
        }
        composable<MinesweeperRoute> {
            MinesweeperScreen(onBack = { nav.popBackStack() }, onHome = { nav.popBackStack(HomeRoute, inclusive = false) })
        }
        composable<TetrisRoute> {
            TetrisScreen(onBack = { nav.popBackStack() }, onHome = { nav.popBackStack(HomeRoute, inclusive = false) })
        }
        composable<FroggerRoute> {
            FroggerScreen(onBack = { nav.popBackStack() }, onHome = { nav.popBackStack(HomeRoute, inclusive = false) })
        }
        composable<XiangqiRoute> {
            XiangqiScreen(onBack = { nav.popBackStack() }, onHome = { nav.popBackStack(HomeRoute, inclusive = false) })
        }
        composable<GomokuRoute> {
            GomokuScreen(onBack = { nav.popBackStack() }, onHome = { nav.popBackStack(HomeRoute, inclusive = false) })
        }
        composable<SudokuRoute> {
            SudokuScreen(onBack = { nav.popBackStack() }, onHome = { nav.popBackStack(HomeRoute, inclusive = false) })
        }
        composable<KlotskiRoute> {
            KlotskiScreen(onBack = { nav.popBackStack() }, onHome = { nav.popBackStack(HomeRoute, inclusive = false) })
        }
        composable<MemoryRoute> {
            MemoryScreen(onBack = { nav.popBackStack() }, onHome = { nav.popBackStack(HomeRoute, inclusive = false) })
        }
    }
}
