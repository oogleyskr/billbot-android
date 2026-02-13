package com.oogley.billbot.data.gateway.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Message sent to chat.send
@Serializable
data class ChatSendParams(
    val message: String,
    val sessionKey: String? = null,
    val agentId: String? = null
)

// Chat message in history
@Serializable
data class ChatMessage(
    val role: String, // "user" | "assistant" | "system"
    val content: String,
    val reasoning: String? = null,
    val timestamp: Long? = null,
    val toolCalls: List<JsonElement>? = null,
    val toolResults: List<JsonElement>? = null
)

// Streaming chat events
sealed class ChatEvent {
    data class Delta(val text: String) : ChatEvent()
    data class ReasoningDelta(val text: String) : ChatEvent()
    data class ToolCall(val name: String, val args: String) : ChatEvent()
    data class ToolResult(val name: String, val result: String) : ChatEvent()
    data object Started : ChatEvent()
    data object Completed : ChatEvent()
    data class Error(val message: String) : ChatEvent()
}

// Chat history response
@Serializable
data class ChatHistoryResponse(
    val messages: List<ChatMessage> = emptyList(),
    val sessionKey: String? = null
)

// Session info
@Serializable
data class SessionInfo(
    val key: String,
    val agentId: String? = null,
    val label: String? = null,
    val messageCount: Int? = null,
    val lastActiveAt: Long? = null,
    val createdAt: Long? = null
)

@Serializable
data class SessionListResponse(
    val sessions: List<SessionInfo> = emptyList()
)
