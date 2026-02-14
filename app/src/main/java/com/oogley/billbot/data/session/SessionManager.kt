package com.oogley.billbot.data.session

import com.oogley.billbot.data.db.SessionDao
import com.oogley.billbot.data.db.SessionEntity
import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.gateway.model.SessionInfo
import com.oogley.billbot.data.gateway.model.SessionListResponse
import com.oogley.billbot.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val gateway: GatewayClient,
    private val preferences: UserPreferences,
    private val sessionDao: SessionDao
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentSessionKey = MutableStateFlow("android://companion")
    val currentSessionKey: StateFlow<String> = _currentSessionKey.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = _sessions.asStateFlow()

    init {
        scope.launch {
            _currentSessionKey.value = preferences.lastSessionKey.first()
        }
    }

    suspend fun switchSession(key: String) {
        _currentSessionKey.value = key
        preferences.setLastSessionKey(key)
    }

    suspend fun refreshSessions() {
        try {
            val result = gateway.listSessions()
            if (result != null) {
                val response = json.decodeFromJsonElement(SessionListResponse.serializer(), result)
                _sessions.value = response.sessions

                // Cache to local DB
                sessionDao.upsertAll(response.sessions.map { it.toEntity() })
            }
        } catch (_: Exception) {
            // Fall back to cached sessions
            val cached = sessionDao.getAll()
            if (cached.isNotEmpty()) {
                _sessions.value = cached.map { it.toSessionInfo() }
            }
        }
    }

    suspend fun createSession(): String {
        val key = "android://${System.currentTimeMillis()}"
        switchSession(key)
        refreshSessions()
        return key
    }

    suspend fun deleteSession(key: String) {
        try {
            gateway.deleteSession(key)
            sessionDao.delete(key)
            _sessions.value = _sessions.value.filter { it.key != key }

            // If we deleted the current session, switch to companion
            if (_currentSessionKey.value == key) {
                switchSession("android://companion")
            }
        } catch (_: Exception) { }
    }

    suspend fun compactSession(key: String, maxLines: Int) {
        try {
            gateway.compactSession(key, maxLines)
            refreshSessions()
        } catch (_: Exception) { }
    }

    suspend fun renameSession(key: String, label: String) {
        try {
            gateway.patchSession(key, label = label)
            _sessions.value = _sessions.value.map { session ->
                if (session.key == key) session.copy(label = label) else session
            }
            sessionDao.getByKey(key)?.let { entity ->
                sessionDao.upsert(entity.copy(label = label))
            }
        } catch (_: Exception) { }
    }

    private fun SessionInfo.toEntity() = SessionEntity(
        key = key,
        label = label,
        agentId = agentId,
        messageCount = messageCount ?: 0,
        lastActiveAt = lastActiveAt ?: 0,
        createdAt = createdAt ?: 0,
        previewText = null
    )

    private fun SessionEntity.toSessionInfo() = SessionInfo(
        key = key,
        label = label,
        agentId = agentId,
        messageCount = messageCount,
        lastActiveAt = lastActiveAt,
        createdAt = createdAt
    )
}
