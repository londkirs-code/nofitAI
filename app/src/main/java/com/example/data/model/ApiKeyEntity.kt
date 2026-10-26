package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "api_keys")
data class ApiKeyEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val provider: ProviderType,
    val name: String,
    val encryptedKey: String,
    val selectedModel: String,
    val customBaseUrl: String? = null,
    val priority: Int = 1,
    val isEnabled: Boolean = true,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val systemPrompt: String? = null,
    val suspendedUntil: Long = 0L,
    val suspensionReason: String? = null,
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val lastUsedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isSuspended: Boolean
        get() = suspendedUntil > System.currentTimeMillis()

    val remainingSuspensionSeconds: Long
        get() = if (isSuspended) {
            (suspendedUntil - System.currentTimeMillis()) / 1000
        } else {
            0L
        }
}
