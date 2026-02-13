package com.oogley.billbot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionKey = :sessionKey ORDER BY timestamp ASC")
    suspend fun getMessages(sessionKey: String): List<MessageEntity>

    @Insert
    suspend fun insertAll(messages: List<MessageEntity>)

    @Insert
    suspend fun insert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE sessionKey = :sessionKey")
    suspend fun deleteBySession(sessionKey: String)
}
