package com.goodyaoshi.parentsgames.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GameRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: GameRecordEntity): Long

    @Query("SELECT * FROM game_record WHERE gameType = :gameType ORDER BY finishedAt DESC LIMIT :limit")
    fun observeRecent(gameType: String, limit: Int = 20): Flow<List<GameRecordEntity>>

    @Query("SELECT * FROM game_record ORDER BY finishedAt DESC LIMIT :limit")
    fun observeAllRecent(limit: Int = 50): Flow<List<GameRecordEntity>>

    @Query("SELECT MIN(durationMs) FROM game_record WHERE gameType = :gameType AND difficulty = :difficulty")
    suspend fun bestTime(gameType: String, difficulty: String?): Long?

    @Query("SELECT MAX(score) FROM game_record WHERE gameType = :gameType")
    suspend fun bestScore(gameType: String): Long?

    @Query("SELECT COUNT(*) FROM game_record WHERE gameType = :gameType")
    suspend fun countByGame(gameType: String): Int
}

@Dao
interface GameStateDao {
    @Upsert
    suspend fun upsert(state: GameStateEntity)

    @Query("SELECT * FROM game_state WHERE gameType = :gameType LIMIT 1")
    suspend fun getByGame(gameType: String): GameStateEntity?

    @Query("SELECT * FROM game_state WHERE gameType = :gameType LIMIT 1")
    fun observeByGame(gameType: String): Flow<GameStateEntity?>

    @Query("DELETE FROM game_state WHERE gameType = :gameType")
    suspend fun clearByGame(gameType: String)

    @Query("SELECT * FROM game_state ORDER BY updatedAt DESC LIMIT 1")
    fun observeMostRecent(): Flow<GameStateEntity?>
}

@Dao
interface UserPhotoDao {
    @Insert
    suspend fun insert(photo: UserPhotoEntity): Long

    @Query("SELECT * FROM user_photo ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<UserPhotoEntity>>

    @Query("SELECT * FROM user_photo WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): UserPhotoEntity?

    @Delete
    suspend fun delete(photo: UserPhotoEntity)
}

@Dao
interface AppSettingDao {
    @Upsert
    suspend fun upsert(setting: AppSettingEntity)

    @Query("SELECT value FROM app_setting WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?
}
