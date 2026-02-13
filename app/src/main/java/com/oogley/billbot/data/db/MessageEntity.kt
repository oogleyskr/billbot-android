package com.oogley.billbot.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of chat messages. Survives process death (phone lock)
 * so the user never loses their conversation.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionKey: String,
    val role: String,
    val content: String,
    val reasoning: String? = null,
    val timestamp: Long
)
