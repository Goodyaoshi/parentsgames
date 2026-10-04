package com.goodyaoshi.parentsgames.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_record")
data class GameRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameType: String,            // "JIGSAW", "MAHJONG", "MATCH3", "COLOR_SORT"
    val score: Long?,
    val durationMs: Long,
    val difficulty: String?,
    val finishedAt: Long,
    val payloadJson: String?,
)

@Entity(tableName = "game_state")
data class GameStateEntity(
    @PrimaryKey val gameType: String,
    val snapshotJson: String,
    val updatedAt: Long,
)

@Entity(tableName = "user_photo")
data class UserPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val thumbnailPath: String,
    val label: String?,
    val createdAt: Long,
)

@Entity(tableName = "app_setting")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
