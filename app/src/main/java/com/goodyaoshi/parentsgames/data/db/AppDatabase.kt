package com.goodyaoshi.parentsgames.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        GameRecordEntity::class,
        GameStateEntity::class,
        UserPhotoEntity::class,
        AppSettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameRecordDao(): GameRecordDao
    abstract fun gameStateDao(): GameStateDao
    abstract fun userPhotoDao(): UserPhotoDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        const val NAME = "fatima_games.db"
    }
}
