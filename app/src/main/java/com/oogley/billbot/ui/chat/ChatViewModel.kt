package com.oogley.billbot.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oogley.billbot.data.gateway.ConnectionState
import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.gateway.model.ChatEvent
import com.oogley.billbot.data.repository.ChatRepository
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
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepo: ChatRepository,
    private val gateway: GatewayClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentAssistantId: String? = null
    private val reasoningBuffer = StringBuilder()
    private val contentBuffer = StringBuilder()

    init {
        // Listen for chat events
        viewModelScope.launch {
            chatRepo.chatEvents.collect { event ->
                handleChatEvent(event)
            }
        }

        // Load history when connected
        viewModelScope.launch {
            gateway.connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) {
                    loadHistory()
                }
            }
        }
    }

    private suspend fun loadHistory() {
        try {
            val history = chatRepo.getHistory()
            val messages = history.map { msg ->
                UiMessage(
                    role = msg.role,
                    content = msg.content,
                    reasoning = msg.reasoning,
                    timestamp = msg.timestamp ?: 0
                )
            }.filter { it.role == "user" || it.role == "assistant" }

            _uiState.update { it.copy(messages = messages) }
        } catch (e: Exception) {
            // History loading is non-critical
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
                // Create bubble if not already active (dedup)
                ensureAssistantBubble()
            }

            is ChatEvent.Delta -> {
                ensureAssistantBubble()
                // Deltas are MONOLITHIC — full text so far, not incremental
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
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == id) msg.copy(isStreaming = false) else msg
                        },
                        isGenerating = false
                    )
                }
                currentAssistantId = null
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

        val userMsg = UiMessage(role = "user", content = text)
        _uiState.update { it.copy(messages = it.messages + userMsg, error = null) }

        viewModelScope.launch {
            try {
                chatRepo.sendMessage(text)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun abort() {
        viewModelScope.launch {
            try {
                chatRepo.abort()
                _uiState.update { it.copy(isGenerating = false) }
            } catch (e: Exception) {
                // Best effort
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
