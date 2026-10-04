package com.goodyaoshi.parentsgames.domain.repository

import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import kotlinx.coroutines.flow.Flow

interface RecordRepository {
    fun observeRecent(type: GameType, limit: Int = 20): Flow<List<GameRecord>>
    fun observeAllRecent(limit: Int = 50): Flow<List<GameRecord>>
    suspend fun save(record: GameRecord)
    suspend fun bestTime(type: GameType, difficulty: String? = null): Long?
    suspend fun bestScore(type: GameType): Long?
    suspend fun countByGame(type: GameType): Int
}

interface GameStateRepository {
    suspend fun saveSnapshot(type: GameType, snapshotJson: String)
    suspend fun loadSnapshot(type: GameType): String?
    suspend fun clearSnapshot(type: GameType)
    fun observeMostRecentSnapshot(): Flow<Pair<GameType, Long>?>
}
