package com.oogley.billbot.data.repository

import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.gateway.model.LogTailResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogsRepository @Inject constructor(
    private val gateway: GatewayClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var cursor: String? = null
    private var tailingJob: Job? = null

    private companion object {
        const val MAX_BUFFER_LINES = 1000
        const val POLL_INTERVAL_MS = 2000L
    }

    suspend fun tailLogs(logCursor: String? = null, limit: Int = 200): LogTailResponse? {
        return try {
            val result = gateway.tailLogs(logCursor, limit)
            if (result != null) {
                json.decodeFromJsonElement(LogTailResponse.serializer(), result)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun startTailing() {
        if (tailingJob?.isActive == true) return
        _isLoading.value = true
        cursor = null
        _lines.value = emptyList()

        tailingJob = scope.launch {
            while (isActive) {
                try {
                    val response = tailLogs(cursor)
                    if (response != null) {
                        if (response.reset) {
                            _lines.value = response.lines
                        } else if (response.lines.isNotEmpty()) {
                            val current = _lines.value
                            val newLines = current + response.lines
                            _lines.value = if (newLines.size > MAX_BUFFER_LINES) {
                                newLines.takeLast(MAX_BUFFER_LINES)
                            } else newLines
                        }
                        response.cursor?.let { cursor = it }
                    }
                    _isLoading.value = false
                } catch (_: Exception) {
                    _isLoading.value = false
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopTailing() {
        tailingJob?.cancel()
        tailingJob = null
    }
}
