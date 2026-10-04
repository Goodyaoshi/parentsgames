package com.goodyaoshi.parentsgames.core.di

import com.goodyaoshi.parentsgames.data.repository.GameStateRepositoryImpl
import com.goodyaoshi.parentsgames.data.repository.RecordRepositoryImpl
import com.goodyaoshi.parentsgames.domain.repository.GameStateRepository
import com.goodyaoshi.parentsgames.domain.repository.RecordRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindRecordRepo(impl: RecordRepositoryImpl): RecordRepository

    @Binds @Singleton
    abstract fun bindGameStateRepo(impl: GameStateRepositoryImpl): GameStateRepository
}
