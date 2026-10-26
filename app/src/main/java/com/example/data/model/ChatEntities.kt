package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_conversations")
data class ChatConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSnippet: String = "",
    val messageCount: Int = 0
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val role: String, // "user", "assistant", "error"
    val content: String,
    val provider: String? = null,
    val model: String? = null,
    val tokens: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentUri: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentName: String? = null
)
