package com.oogley.billbot.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val key: String,
    val label: String? = null,
    val agentId: String? = null,
    val messageCount: Int = 0,
    val lastActiveAt: Long = 0,
    val createdAt: Long = 0,
    val previewText: String? = null
)
