package com.goodyaoshi.parentsgames.domain.model

enum class GameType(val key: String, val label: String) {
    JIGSAW("JIGSAW", "拼图"),
    MAHJONG("MAHJONG", "麻将连连看"),
    MATCH3("MATCH3", "宝石消除"),
    COLOR_SORT("COLOR_SORT", "颜色分类"),
    SOLITAIRE("SOLITAIRE", "纸牌"),
    MINESWEEPER("MINESWEEPER", "扫雷"),
    TETRIS("TETRIS", "俄罗斯方块"),
    FROGGER("FROGGER", "青蛙过马路"),
    XIANGQI("XIANGQI", "中国象棋"),
    GOMOKU("GOMOKU", "五子棋"),
    SUDOKU("SUDOKU", "数独"),
    KLOTSKI("KLOTSKI", "华容道"),
    MEMORY("MEMORY", "翻牌配对");

    companion object {
        fun fromKey(key: String): GameType? = entries.find { it.key == key }
    }
}
