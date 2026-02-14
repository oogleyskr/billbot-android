package com.oogley.billbot.data.repository

import com.oogley.billbot.data.db.MessageDao
import com.oogley.billbot.data.db.MessageEntity
import com.oogley.billbot.data.gateway.GatewayClient
import com.oogley.billbot.data.gateway.model.ChatAttachment
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
    private val gateway: GatewayClient,
    private val messageDao: MessageDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    val chatEvents: SharedFlow<ChatEvent> = gateway.chatEvents

    suspend fun sendMessage(text: String, sessionKey: String) {
        gateway.sendChat(text, sessionKey)
    }

    suspend fun sendMessageWithAttachments(text: String, sessionKey: String, attachments: List<ChatAttachment>) {
        gateway.sendChatWithAttachments(text, sessionKey, attachments)
    }

    /**
     * Get chat history -- tries server first, falls back to local cache.
     * Server response is cached locally so it survives process death.
     */
    suspend fun getHistory(sessionKey: String): List<ChatMessage> {
        // Try loading from server
        val serverMessages = try {
            val result = gateway.getChatHistory(sessionKey) ?: null
            if (result != null) {
                val arr = result.jsonObject["messages"]?.jsonArray ?: result.jsonArray
                arr.map { json.decodeFromJsonElement(ChatMessage.serializer(), it) }
            } else null
        } catch (_: Exception) {
            null
        }

        if (serverMessages != null && serverMessages.isNotEmpty()) {
            // Cache server history locally
            cacheMessages(sessionKey, serverMessages)
            return serverMessages
        }

        // Fall back to local cache (survives process death / gateway restarts)
        return loadFromCache(sessionKey)
    }

    /** Save a single message to local cache (called as user sends / assistant replies). */
    suspend fun cacheMessage(sessionKey: String, role: String, content: String, reasoning: String? = null) {
        messageDao.insert(
            MessageEntity(
                sessionKey = sessionKey,
                role = role,
                content = content,
                reasoning = reasoning,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /** Replace local cache with server history. */
    private suspend fun cacheMessages(sessionKey: String, messages: List<ChatMessage>) {
        messageDao.deleteBySession(sessionKey)
        messageDao.insertAll(messages.map { msg ->
            MessageEntity(
                sessionKey = sessionKey,
                role = msg.role,
                content = msg.content,
                reasoning = msg.reasoning,
                timestamp = msg.timestamp ?: System.currentTimeMillis()
            )
        })
    }

    private suspend fun loadFromCache(sessionKey: String): List<ChatMessage> {
        return messageDao.getMessages(sessionKey).map { entity ->
            ChatMessage(
                role = entity.role,
                content = entity.content,
                reasoning = entity.reasoning,
                timestamp = entity.timestamp
            )
        }
    }

    suspend fun abort(sessionKey: String) {
        gateway.abortChat(sessionKey)
    }

    suspend fun resetSession(sessionKey: String) {
        gateway.resetSession(sessionKey)
        // Clear local cache when user explicitly resets
        messageDao.deleteBySession(sessionKey)
    }
}
