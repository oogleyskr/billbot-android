package com.oogley.billbot.di

import android.content.Context
import androidx.room.Room
import com.oogley.billbot.data.db.AppDatabase
import com.oogley.billbot.data.db.MessageDao
import com.oogley.billbot.data.db.SessionDao
import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.preferences.UserPreferences
import com.oogley.billbot.data.repository.ChatRepository
import com.oogley.billbot.data.repository.DashboardRepository
import com.oogley.billbot.data.repository.LogsRepository
import com.oogley.billbot.data.repository.SettingsRepository
import com.oogley.billbot.data.repository.TokensRepository
import com.oogley.billbot.data.session.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "billbot.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideMessageDao(db: AppDatabase): MessageDao {
        return db.messageDao()
    }

    @Provides
    @Singleton
    fun provideSessionDao(db: AppDatabase): SessionDao {
        return db.sessionDao()
    }

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context)
    }

    @Provides
    @Singleton
    fun provideSessionManager(gateway: GatewayClient, preferences: UserPreferences, sessionDao: SessionDao): SessionManager {
        return SessionManager(gateway, preferences, sessionDao)
    }

    @Provides
    @Singleton
    fun provideChatRepository(gateway: GatewayClient, messageDao: MessageDao): ChatRepository {
        return ChatRepository(gateway, messageDao)
    }

    @Provides
    @Singleton
    fun provideDashboardRepository(gateway: GatewayClient): DashboardRepository {
        return DashboardRepository(gateway)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(gateway: GatewayClient): SettingsRepository {
        return SettingsRepository(gateway)
    }

    @Provides
    @Singleton
    fun provideTokensRepository(gateway: GatewayClient): TokensRepository {
        return TokensRepository(gateway)
    }

    @Provides
    @Singleton
    fun provideLogsRepository(gateway: GatewayClient): LogsRepository {
        return LogsRepository(gateway)
    }
}
