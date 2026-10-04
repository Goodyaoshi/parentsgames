package com.goodyaoshi.parentsgames.core.di

import android.content.Context
import androidx.room.Room
import com.goodyaoshi.parentsgames.data.db.AppDatabase
import com.goodyaoshi.parentsgames.data.db.AppSettingDao
import com.goodyaoshi.parentsgames.data.db.GameRecordDao
import com.goodyaoshi.parentsgames.data.db.GameStateDao
import com.goodyaoshi.parentsgames.data.db.UserPhotoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideRecordDao(db: AppDatabase): GameRecordDao = db.gameRecordDao()
    @Provides fun provideStateDao(db: AppDatabase): GameStateDao = db.gameStateDao()
    @Provides fun providePhotoDao(db: AppDatabase): UserPhotoDao = db.userPhotoDao()
    @Provides fun provideSettingDao(db: AppDatabase): AppSettingDao = db.appSettingDao()
}
