package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ApiKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys ORDER BY priority ASC, createdAt ASC")
    fun getAllKeysFlow(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys ORDER BY priority ASC, createdAt ASC")
    suspend fun getAllKeys(): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE isEnabled = 1 ORDER BY priority ASC, createdAt ASC")
    suspend fun getEnabledKeys(): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE id = :id LIMIT 1")
    suspend fun getKeyById(id: String): ApiKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(apiKey: ApiKeyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apiKeys: List<ApiKeyEntity>)

    @Update
    suspend fun update(apiKey: ApiKeyEntity)

    @Delete
    suspend fun delete(apiKey: ApiKeyEntity)

    @Query("UPDATE api_keys SET suspendedUntil = :until, suspensionReason = :reason, failedRequests = failedRequests + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun markSuspended(id: String, until: Long, reason: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE api_keys SET suspendedUntil = 0, suspensionReason = NULL WHERE id = :id")
    suspend fun clearSuspension(id: String)

    @Query("UPDATE api_keys SET totalRequests = totalRequests + 1, successfulRequests = successfulRequests + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun recordSuccess(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE api_keys SET priority = :newPriority WHERE id = :id")
    suspend fun updatePriority(id: String, newPriority: Int)

    @Query("SELECT COUNT(*) FROM api_keys")
    suspend fun getCount(): Int
}
