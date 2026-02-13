package com.oogley.billbot.data.repository

import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.gateway.model.ChatEvent
import com.oogley.billbot.data.gateway.model.ChatMessage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val gateway: GatewayClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    val chatEvents: SharedFlow<ChatEvent> = gateway.chatEvents

    suspend fun sendMessage(text: String, sessionKey: String = "android://companion") {
        gateway.sendChat(text, sessionKey)
    }

    suspend fun getHistory(sessionKey: String = "android://companion"): List<ChatMessage> {
        val result = gateway.getChatHistory(sessionKey) ?: return emptyList()
        return try {
            val messages = result.jsonObject["messages"]?.jsonArray ?: result.jsonArray
            messages.map { json.decodeFromJsonElement(ChatMessage.serializer(), it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun abort() {
        gateway.abortChat()
    }

    suspend fun resetSession(sessionKey: String = "android://companion") {
        gateway.resetSession(sessionKey)
    }
}
