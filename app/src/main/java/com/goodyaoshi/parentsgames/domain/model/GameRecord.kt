package com.goodyaoshi.parentsgames.domain.model

data class GameRecord(
    val id: Long,
    val gameType: GameType,
    val score: Long?,
    val durationMs: Long,
    val difficulty: String?,
    val finishedAt: Long,
)
