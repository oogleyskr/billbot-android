package com.oogley.billbot.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "billbot_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val GATEWAY_URL = stringPreferencesKey("gateway_url")
        private val AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        private val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val LAST_SESSION_KEY = stringPreferencesKey("last_session_key")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val BACKGROUND_SERVICE = booleanPreferencesKey("background_service")
        private val AUTH_MODE = stringPreferencesKey("auth_mode") // "tailscale" | "token" | "password"
        private val DEVICE_TOKEN = stringPreferencesKey("device_token")
    }

    val gatewayUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[GATEWAY_URL] ?: ""
    }

    val authToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AUTH_TOKEN] ?: ""
    }

    val deviceId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEVICE_ID] ?: UUID.randomUUID().toString()
    }

    val darkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_MODE] ?: true // Default to dark mode
    }

    val autoConnect: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_CONNECT] ?: true
    }

    val lastSessionKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LAST_SESSION_KEY] ?: "android://companion"
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[NOTIFICATIONS_ENABLED] ?: true
    }

    val backgroundService: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BACKGROUND_SERVICE] ?: false
    }

    val authMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AUTH_MODE] ?: "tailscale"
    }

    val deviceToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEVICE_TOKEN] ?: ""
    }

    suspend fun setGatewayUrl(url: String) {
        context.dataStore.edit { it[GATEWAY_URL] = url }
    }

    suspend fun setAuthToken(token: String) {
        context.dataStore.edit { it[AUTH_TOKEN] = token }
    }

    suspend fun setDeviceId(id: String) {
        context.dataStore.edit { it[DEVICE_ID] = id }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_CONNECT] = enabled }
    }

    suspend fun setLastSessionKey(key: String) {
        context.dataStore.edit { it[LAST_SESSION_KEY] = key }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setBackgroundService(enabled: Boolean) {
        context.dataStore.edit { it[BACKGROUND_SERVICE] = enabled }
    }

    suspend fun setAuthMode(mode: String) {
        context.dataStore.edit { it[AUTH_MODE] = mode }
    }

    suspend fun setDeviceToken(token: String) {
        context.dataStore.edit { it[DEVICE_TOKEN] = token }
    }
}
