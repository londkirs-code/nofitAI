package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.ChatConversationEntity
import com.example.data.model.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
    fun getAllConversationsFlow(): Flow<List<ChatConversationEntity>>

    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
    suspend fun getAllConversationsList(): List<ChatConversationEntity>

    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestConversation(): ChatConversationEntity?

    @Query("SELECT * FROM chat_conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: Long): ChatConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ChatConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: ChatConversationEntity)

    @Delete
    suspend fun deleteConversation(conversation: ChatConversationEntity)

    @Query("DELETE FROM chat_conversations WHERE id = :id")
    suspend fun deleteConversationById(id: Long)

    @Query("DELETE FROM chat_conversations")
    suspend fun clearAllConversations()

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversationFlow(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversation(conversationId: Long): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE role != 'error' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesAcrossConversations(limit: Int = 20): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Transaction
    suspend fun addMessageAndUpdateConversation(
        conversationId: Long,
        message: ChatMessageEntity,
        newSnippet: String
    ): Long {
        val messageId = insertMessage(message)
        val conv = getConversationById(conversationId)
        if (conv != null) {
            updateConversation(
                conv.copy(
                    updatedAt = System.currentTimeMillis(),
                    lastSnippet = newSnippet.take(120),
                    messageCount = conv.messageCount + 1
                )
            )
        }
        return messageId
    }
}
