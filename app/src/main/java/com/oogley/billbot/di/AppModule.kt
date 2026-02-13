package com.oogley.billbot.di

import android.content.Context
import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.preferences.UserPreferences
import com.oogley.billbot.data.repository.ChatRepository
import com.oogley.billbot.data.repository.DashboardRepository
import com.oogley.billbot.data.repository.SettingsRepository
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
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context)
    }

    @Provides
    @Singleton
    fun provideChatRepository(gateway: GatewayClient): ChatRepository {
        return ChatRepository(gateway)
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
}
