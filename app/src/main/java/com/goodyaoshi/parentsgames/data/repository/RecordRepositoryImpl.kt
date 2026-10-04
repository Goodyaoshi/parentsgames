package com.goodyaoshi.parentsgames.data.repository

import com.goodyaoshi.parentsgames.data.db.GameRecordDao
import com.goodyaoshi.parentsgames.data.db.GameRecordEntity
import com.goodyaoshi.parentsgames.domain.model.GameRecord
import com.goodyaoshi.parentsgames.domain.model.GameType
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordRepositoryImpl @Inject constructor(
    private val dao: GameRecordDao,
) : RecordRepository {

    override fun observeRecent(type: GameType, limit: Int): Flow<List<GameRecord>> =
        dao.observeRecent(type.key, limit).map { list -> list.map { it.toDomain() } }

    override fun observeAllRecent(limit: Int): Flow<List<GameRecord>> =
        dao.observeAllRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun save(record: GameRecord) {
        dao.insert(
            GameRecordEntity(
                gameType = record.gameType.key,
                score = record.score,
                durationMs = record.durationMs,
                difficulty = record.difficulty,
                finishedAt = record.finishedAt,
                payloadJson = null,
            )
        )
    }

    override suspend fun bestTime(type: GameType, difficulty: String?): Long? =
        dao.bestTime(type.key, difficulty)

    override suspend fun bestScore(type: GameType): Long? = dao.bestScore(type.key)

    override suspend fun countByGame(type: GameType): Int = dao.countByGame(type.key)

    private fun GameRecordEntity.toDomain() = GameRecord(
        id = id,
        gameType = GameType.fromKey(gameType) ?: GameType.JIGSAW,
        score = score,
        durationMs = durationMs,
        difficulty = difficulty,
        finishedAt = finishedAt,
    )
}
