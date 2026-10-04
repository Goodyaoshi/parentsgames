package com.goodyaoshi.parentsgames.feature.stats

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodyaoshi.parentsgames.R
import com.goodyaoshi.parentsgames.core.theme.LocalAppTheme
import com.goodyaoshi.parentsgames.core.theme.LocalAppTypography
import com.goodyaoshi.parentsgames.core.ui.SimpleTopBar
import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class StatsUiState(
    val records: List<GameRecord> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val recordRepo: RecordRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            recordRepo.observeAllRecent(50).collect { list ->
                _state.update { it.copy(records = list) }
            }
        }
    }
}

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val theme = LocalAppTheme.current
    Column(modifier = Modifier.fillMaxSize().background(theme.color.bgCanvas).systemBarsPadding()) {
        SimpleTopBar(title = "战绩", onBackClick = onBack)
        if (state.records.isEmpty()) {
            EmptyStats()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = theme.spacing.md,
                    end = theme.spacing.md,
                    bottom = theme.spacing.lg,
                ),
                verticalArrangement = Arrangement.spacedBy(theme.spacing.sm),
            ) {
                item {
                    SummaryCard(
                        totalCount = state.records.size,
                        totalDurationMs = state.records.sumOf { it.durationMs },
                        gameCount = state.records.map { it.gameType }.distinct().size,
                    )
                }
                item {
                    Text(
                        "最近的对局",
                        color = theme.color.textPrimary,
                        style = LocalAppTypography.current.titleMd,
                        modifier = Modifier.padding(start = theme.spacing.xxs, top = theme.spacing.sm),
                    )
                }
                items(state.records, key = { it.id }) { record ->
                    RecordRow(record)
                }
            }
        }
    }
}

@Composable
private fun EmptyStats() {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Column(
        modifier = Modifier.fillMaxSize().padding(theme.spacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "还没有对局记录。",
            color = theme.color.textPrimary,
            style = typo.titleMd,
        )
        Spacer(Modifier.height(theme.spacing.xs))
        Text(
            "开始游戏后，你的战绩会显示在这里，还能看到自己的进步。",
            color = theme.color.textSecondary,
            style = typo.bodyMd,
        )
    }
}

/** 顶部汇总卡：总时长（大号数字）+ 总对局数 + 玩过的游戏数，品牌红竖条点缀。 */
@Composable
private fun SummaryCard(
    totalCount: Int,
    totalDurationMs: Long,
    gameCount: Int,
) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.radii.lg))
            .background(theme.color.bgSurface)
            .padding(theme.spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(theme.color.primary),
            )
            Spacer(Modifier.width(theme.spacing.xs))
            Text("战绩总览", color = theme.color.textPrimary, style = typo.titleMd)
        }
        Spacer(Modifier.height(theme.spacing.lg))
        Text("总游戏时长", color = theme.color.textSecondary, style = typo.labelMd)
        Spacer(Modifier.height(theme.spacing.xxs))
        Text(formatDurationHuman(totalDurationMs), color = theme.color.textPrimary, style = typo.numericLg)
        Spacer(Modifier.height(theme.spacing.md))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("总对局", color = theme.color.textSecondary, style = typo.labelMd)
                Spacer(Modifier.height(theme.spacing.xxs))
                Text("$totalCount 局", color = theme.color.textPrimary, style = typo.numericMd)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("玩过的游戏", color = theme.color.textSecondary, style = typo.labelMd)
                Spacer(Modifier.height(theme.spacing.xxs))
                Text("$gameCount 款", color = theme.color.textPrimary, style = typo.numericMd)
            }
        }
    }
}

/** 单条对局：游戏插画 + 游戏名 + 难度/得分/用时 + 相对时间（如"昨天"）。 */
@Composable
private fun RecordRow(record: GameRecord) {
    val theme = LocalAppTheme.current
    val typo = LocalAppTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.radii.lg))
            .background(theme.color.bgSurface)
            .padding(theme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(theme.radii.md))
                .background(theme.color.bgSurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = record.gameType.illustRes()),
                contentDescription = record.gameType.label,
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.width(theme.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    record.gameType.label,
                    color = theme.color.textPrimary,
                    style = typo.titleSm,
                    modifier = Modifier.weight(1f),
                )
                Text(relativeTimeLabel(record.finishedAt), color = theme.color.textSecondary, style = typo.labelMd)
            }
            Spacer(Modifier.height(theme.spacing.xxs))
            Text(recordDetail(record), color = theme.color.textSecondary, style = typo.bodyMd)
        }
    }
}

/** 与首页一致的插画资源映射。 */
private fun GameType.illustRes(): Int = when (this) {
    GameType.JIGSAW -> R.drawable.illust_jigsaw
    GameType.MAHJONG -> R.drawable.illust_mahjong
    GameType.MATCH3 -> R.drawable.illust_match3
    GameType.COLOR_SORT -> R.drawable.illust_colorsort
    GameType.SOLITAIRE -> R.drawable.illust_solitaire
    GameType.MINESWEEPER -> R.drawable.illust_minesweeper
    GameType.TETRIS -> R.drawable.illust_tetris
    GameType.FROGGER -> R.drawable.illust_frogger
    GameType.XIANGQI -> R.drawable.illust_xiangqi
    GameType.GOMOKU -> R.drawable.illust_gomoku
    GameType.SUDOKU -> R.drawable.illust_sudoku
    GameType.KLOTSKI -> R.drawable.illust_klotski
    GameType.MEMORY -> R.drawable.illust_memory
}

private fun recordDetail(record: GameRecord): String {
    val parts = buildList {
        record.difficulty?.takeIf { it.isNotBlank() }?.let { add(difficultyLabel(it)) }
        record.score?.let { add("得分 %,d".format(it)) }
        add("用时 " + formatDurationHuman(record.durationMs))
    }
    return parts.joinToString(" · ")
}

/** 各游戏存的难度格式不一，统一转成好懂的中文。 */
private fun difficultyLabel(raw: String): String = when {
    raw.equals("EASY", ignoreCase = true) -> "简单"
    raw.equals("MEDIUM", ignoreCase = true) -> "中等"
    raw.equals("HARD", ignoreCase = true) -> "困难"
    raw.equals("CLASSIC", ignoreCase = true) || raw.equals("KLONDIKE", ignoreCase = true) -> "经典"
    raw.startsWith("STAGE_") -> "第 ${raw.removePrefix("STAGE_")} 关"
    raw.length > 1 && raw.endsWith("P") && raw.dropLast(1).all(Char::isDigit) -> "${raw.dropLast(1)} 片拼图"
    else -> raw
}

/** 相对时间：今天 / 昨天 / N 天前，更早显示日期。 */
private fun relativeTimeLabel(finishedAtMs: Long): String {
    val finishedDate = Instant.ofEpochMilli(finishedAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(finishedDate, LocalDate.now())
    return when {
        days <= 0L -> "今天"
        days == 1L -> "昨天"
        days < 30L -> "$days 天前"
        else -> "${finishedDate.monthValue} 月 ${finishedDate.dayOfMonth} 日"
    }
}

/** 人类可读时长：如"2 小时 15 分""3 分 20 秒""45 秒"。 */
private fun formatDurationHuman(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> if (m > 0) "$h 小时 $m 分" else "$h 小时"
        m > 0 -> if (s > 0) "$m 分 $s 秒" else "$m 分"
        else -> "$s 秒"
    }
}
