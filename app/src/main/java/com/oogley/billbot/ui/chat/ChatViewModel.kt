package com.oogley.billbot.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oogley.billbot.data.gateway.ConnectionState
import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.gateway.model.ChatAttachment
import com.oogley.billbot.data.gateway.model.ChatEvent
import com.oogley.billbot.data.gateway.model.SessionInfo
import com.oogley.billbot.data.repository.ChatRepository
import com.oogley.billbot.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user" | "assistant"
    val content: String,
    val reasoning: String? = null,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<ChatAttachment> = emptyList()
)

data class PendingAttachment(
    val uri: Uri,
    val mimeType: String,
    val fileName: String?,
    val thumbnailBase64: String? = null,
    val attachment: ChatAttachment? = null // filled after compression
)

data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null,
    val currentSessionKey: String = "android://companion",
    val currentSessionLabel: String? = null,
    val sessions: List<SessionInfo> = emptyList(),
    val isDrawerOpen: Boolean = false,
    val pendingAttachments: List<PendingAttachment> = emptyList()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepo: ChatRepository,
    private val gateway: GatewayClient,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentAssistantId: String? = null
    private val reasoningBuffer = StringBuilder()
    private val contentBuffer = StringBuilder()

    private val sessionKey: String
        get() = sessionManager.currentSessionKey.value

    init {
        // Listen for chat events
        viewModelScope.launch {
            chatRepo.chatEvents.collect { event ->
                handleChatEvent(event)
            }
        }

        // Track current session key
        viewModelScope.launch {
            sessionManager.currentSessionKey.collect { key ->
                val label = sessionManager.sessions.value.find { it.key == key }?.label
                _uiState.update { it.copy(currentSessionKey = key, currentSessionLabel = label) }
                loadHistory()
            }
        }

        // Track session list
        viewModelScope.launch {
            sessionManager.sessions.collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
            }
        }

        // Load cached messages immediately
        viewModelScope.launch {
            loadHistory()
        }

        // Refresh from server on every (re)connect
        viewModelScope.launch {
            gateway.connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) {
                    loadHistory()
                    loadSessions()
                }
            }
        }
    }

    private suspend fun loadHistory() {
        try {
            val history = chatRepo.getHistory(sessionKey)
            val messages = history.map { msg ->
                UiMessage(
                    role = msg.role,
                    content = msg.content,
                    reasoning = msg.reasoning,
                    timestamp = msg.timestamp ?: 0
                )
            }.filter { it.role == "user" || it.role == "assistant" }

            currentAssistantId = null
            reasoningBuffer.clear()
            contentBuffer.clear()
            _uiState.update { it.copy(messages = messages, isGenerating = false) }
        } catch (_: Exception) {
            // History loading is non-critical
        }
    }

    fun loadSessions() {
        viewModelScope.launch {
            sessionManager.refreshSessions()
        }
    }

    fun switchSession(key: String) {
        viewModelScope.launch {
            sessionManager.switchSession(key)
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            sessionManager.createSession()
        }
    }

    fun deleteSession(key: String) {
        viewModelScope.launch {
            sessionManager.deleteSession(key)
        }
    }

    fun compactSession(key: String, maxLines: Int) {
        viewModelScope.launch {
            sessionManager.compactSession(key, maxLines)
        }
    }

    fun renameSession(key: String, label: String) {
        viewModelScope.launch {
            sessionManager.renameSession(key, label)
        }
    }

    private fun ensureAssistantBubble() {
        if (currentAssistantId != null) return
        currentAssistantId = java.util.UUID.randomUUID().toString()
        reasoningBuffer.clear()
        contentBuffer.clear()
        val msg = UiMessage(
            id = currentAssistantId!!,
            role = "assistant",
            content = "",
            isStreaming = true
        )
        _uiState.update { state ->
            state.copy(
                messages = state.messages + msg,
                isGenerating = true
            )
        }
    }

    private fun handleChatEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.Started -> {
                ensureAssistantBubble()
            }

            is ChatEvent.Delta -> {
                ensureAssistantBubble()
                contentBuffer.clear()
                contentBuffer.append(event.text)
                updateCurrentAssistant(
                    content = contentBuffer.toString(),
                    reasoning = reasoningBuffer.toString().takeIf { it.isNotEmpty() }
                )
            }

            is ChatEvent.ReasoningDelta -> {
                ensureAssistantBubble()
                reasoningBuffer.append(event.text)
                updateCurrentAssistant(
                    content = contentBuffer.toString(),
                    reasoning = reasoningBuffer.toString()
                )
            }

            is ChatEvent.ToolCall -> {
                ensureAssistantBubble()
                contentBuffer.append("\n[Tool: ${event.name}]\n")
                updateCurrentAssistant(content = contentBuffer.toString())
            }

            is ChatEvent.ToolResult -> {
                contentBuffer.append("[Result: ${event.result.take(200)}]\n")
                updateCurrentAssistant(content = contentBuffer.toString())
            }

            is ChatEvent.Completed -> {
                val id = currentAssistantId ?: return
                val finalContent = contentBuffer.toString()
                val finalReasoning = reasoningBuffer.toString().takeIf { it.isNotEmpty() }
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == id) msg.copy(isStreaming = false) else msg
                        },
                        isGenerating = false
                    )
                }
                currentAssistantId = null

                // Persist completed assistant message to local cache
                if (finalContent.isNotBlank()) {
                    viewModelScope.launch {
                        try {
                            chatRepo.cacheMessage(sessionKey, "assistant", finalContent, finalReasoning)
                        } catch (_: Exception) { }
                    }
                }
            }

            is ChatEvent.Error -> {
                _uiState.update { it.copy(
                    isGenerating = false,
                    error = event.message
                )}
                currentAssistantId = null
            }
        }
    }

    private fun updateCurrentAssistant(content: String, reasoning: String? = null) {
        val id = currentAssistantId ?: return
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == id) msg.copy(
                        content = content,
                        reasoning = reasoning ?: msg.reasoning
                    ) else msg
                }
            )
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val pending = _uiState.value.pendingAttachments
        val attachments = pending.mapNotNull { it.attachment }

        val userMsg = UiMessage(
            role = "user",
            content = text,
            attachments = attachments
        )
        _uiState.update { it.copy(
            messages = it.messages + userMsg,
            isGenerating = true,
            error = null,
            pendingAttachments = emptyList()
        )}

        // Persist user message to local cache immediately
        viewModelScope.launch {
            try {
                chatRepo.cacheMessage(sessionKey, "user", text)
            } catch (_: Exception) { }
        }

        viewModelScope.launch {
            try {
                if (attachments.isNotEmpty()) {
                    chatRepo.sendMessageWithAttachments(text, sessionKey, attachments)
                } else {
                    chatRepo.sendMessage(text, sessionKey)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun addAttachment(pending: PendingAttachment) {
        _uiState.update { it.copy(pendingAttachments = it.pendingAttachments + pending) }
    }

    fun removeAttachment(index: Int) {
        _uiState.update { state ->
            state.copy(pendingAttachments = state.pendingAttachments.filterIndexed { i, _ -> i != index })
        }
    }

    fun abort() {
        viewModelScope.launch {
            val id = currentAssistantId
            currentAssistantId = null
            _uiState.update { state ->
                state.copy(
                    messages = if (id != null) state.messages.map { msg ->
                        if (msg.id == id) msg.copy(isStreaming = false) else msg
                    } else state.messages,
                    isGenerating = false
                )
            }
            try {
                chatRepo.abort(sessionKey)
            } catch (_: Exception) {
                // Best effort
            }
        }
    }

    fun resetChat() {
        viewModelScope.launch {
            try {
                chatRepo.resetSession(sessionKey)
                _uiState.update { it.copy(messages = emptyList()) }
                currentAssistantId = null
                reasoningBuffer.clear()
                contentBuffer.clear()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to reset: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
