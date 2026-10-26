package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.ApiKeyDao
import com.example.data.local.ChatDao
import com.example.data.model.ApiKeyEntity
import com.example.data.model.ChatConversationEntity
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ProviderType
import com.example.data.remote.AiApiClient
import com.example.data.remote.AiCallException
import com.example.data.remote.AiResponse
import com.example.data.remote.AttachmentData
import com.example.data.remote.ChatTurn
import com.example.data.security.CryptoEngine
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class QueryResult(
    val responseText: String,
    val provider: ProviderType,
    val model: String,
    val keyName: String,
    val fallbackOccurred: Boolean,
    val fallbackReason: String? = null,
    val conversationId: Long = 0,
    val tokensUsed: Int = 0
)

class AiRepository(
    private val apiKeyDao: ApiKeyDao,
    private val chatDao: ChatDao,
    private val context: Context,
    private val apiClient: AiApiClient = AiApiClient()
) {
    val allKeysFlow: Flow<List<ApiKeyEntity>> = apiKeyDao.getAllKeysFlow()
    val allConversationsFlow: Flow<List<ChatConversationEntity>> = chatDao.getAllConversationsFlow()

    fun getMessagesForConversationFlow(conversationId: Long): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForConversationFlow(conversationId)

    /**
     * Gets or creates active conversation.
     * Automatically starts a new conversation if last interaction was more than 3 hours ago.
     */
    suspend fun getOrCreateActiveConversation(): ChatConversationEntity {
        val latest = chatDao.getLatestConversation()
        // If less than 3 hours passed, continue existing conversation
        if (latest != null && (System.currentTimeMillis() - latest.updatedAt) < 1000 * 60 * 60 * 3) {
            return latest
        }
        val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        val newConv = ChatConversationEntity(
            title = "שיחה • ${dateFormat.format(Date())}",
            lastSnippet = "שיחה חדשה"
        )
        val id = chatDao.insertConversation(newConv)
        return newConv.copy(id = id)
    }

    suspend fun createNewConversation(title: String? = null): ChatConversationEntity {
        val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        val newConv = ChatConversationEntity(
            title = title ?: "שיחה חדשה • ${dateFormat.format(Date())}",
            lastSnippet = ""
        )
        val id = chatDao.insertConversation(newConv)
        return newConv.copy(id = id)
    }

    /**
     * Executes AI query with conversational continuity and automatic cross-conversation recall.
     */
    suspend fun askAi(
        prompt: String,
        attachmentUri: Uri? = null,
        directAttachment: AttachmentData? = null,
        conversationId: Long? = null
    ): QueryResult {
        val allEnabledKeys = apiKeyDao.getEnabledKeys()
        if (allEnabledKeys.isEmpty()) {
            throw IllegalStateException("לא הוגדרו מפתחות API באפליקציה. אנא הכנס מפתח במסך הניהול.")
        }

        // Active conversation
        val conv = if (conversationId != null) {
            chatDao.getConversationById(conversationId) ?: getOrCreateActiveConversation()
        } else {
            getOrCreateActiveConversation()
        }

        // Read attachment if present
        val attachment = directAttachment ?: attachmentUri?.let { readAttachmentFromUri(it) }

        // Fetch prior turns of this conversation for conversation continuity
        val priorMessages = chatDao.getMessagesForConversation(conv.id)
            .filter { it.role == "user" || it.role == "assistant" }
            .takeLast(16)
        val historyTurns = priorMessages.map { ChatTurn(it.role, it.content) }

        // Check if user is asking to recall past conversations
        val recallKeywords = listOf(
            "זוכר", "שיחות קודמות", "דיברנו", "מה אמרתי", "זוכרת",
            "בפעם הקודמת", "מה שאלתי", "remember", "previous chat",
            "שיחה קודמת", "בשיחות קודמות", "מה דיברנו", "שוחחנו"
        )
        val isRecallTriggered = recallKeywords.any { prompt.contains(it, ignoreCase = true) }
        val crossConversationContext = if (isRecallTriggered) {
            buildCrossConversationSummary(conv.id)
        } else {
            ""
        }

        // Save current user message to database
        val userMessage = ChatMessageEntity(
            conversationId = conv.id,
            role = "user",
            content = prompt,
            timestamp = System.currentTimeMillis(),
            attachmentUri = attachmentUri?.toString(),
            attachmentMimeType = attachment?.mimeType,
            attachmentName = attachment?.name
        )
        chatDao.addMessageAndUpdateConversation(conv.id, userMessage, prompt)

        // Find eligible keys (skip those still suspended / cooling down)
        val currentTime = System.currentTimeMillis()
        val sortedKeys = allEnabledKeys.sortedBy { it.priority }

        var fallbackOccurred = false
        var fallbackReason: String? = null
        val errorsEncountered = mutableListOf<String>()

        for (keyEntity in sortedKeys) {
            if (keyEntity.isSuspended) {
                fallbackOccurred = true
                val remainingSec = keyEntity.remainingSuspensionSeconds
                fallbackReason = "המפתח '${keyEntity.name}' מושהה לעוד ${remainingSec} שניות"
                continue
            }

            val rawKey = CryptoEngine.decrypt(keyEntity.encryptedKey)
            if (rawKey.isBlank()) {
                continue
            }

            try {
                val baseSysPrompt = keyEntity.systemPrompt?.takeIf { it.isNotBlank() }
                    ?: "You are NotifAI, a helpful and concise AI assistant. You answer in Hebrew or the language asked. When answering for notifications, keep key points clear and easy to read."

                val effectiveSystemInstruction = if (crossConversationContext.isNotBlank()) {
                    "$baseSysPrompt\n\nהנה מידע והקשר משיחות קודמות שקיימת עם המשתמש בעבר (השתמש במידע זה כדי לענות על שאלותיו על העבר):\n$crossConversationContext"
                } else {
                    baseSysPrompt
                }

                val response: AiResponse = apiClient.queryAi(
                    provider = keyEntity.provider,
                    apiKey = rawKey,
                    model = keyEntity.selectedModel,
                    prompt = prompt,
                    history = historyTurns,
                    systemInstruction = effectiveSystemInstruction,
                    temperature = keyEntity.temperature,
                    maxTokens = keyEntity.maxTokens,
                    attachment = attachment
                )

                // Record success on key
                apiKeyDao.recordSuccess(keyEntity.id)

                // Save assistant message to database
                val assistantMessage = ChatMessageEntity(
                    conversationId = conv.id,
                    role = "assistant",
                    content = response.text,
                    provider = response.provider.displayName,
                    model = response.model,
                    tokens = response.tokensUsed,
                    timestamp = System.currentTimeMillis()
                )
                chatDao.addMessageAndUpdateConversation(conv.id, assistantMessage, response.text)

                return QueryResult(
                    responseText = response.text,
                    provider = response.provider,
                    model = response.model,
                    keyName = keyEntity.name,
                    fallbackOccurred = fallbackOccurred,
                    fallbackReason = fallbackReason,
                    conversationId = conv.id,
                    tokensUsed = response.tokensUsed
                )

            } catch (e: AiCallException.QuotaExceeded) {
                val until = System.currentTimeMillis() + (e.suspensionSeconds * 1000)
                val reason = "מכסה נוצלה. ממתין להתחדשות: ${e.message}"
                apiKeyDao.markSuspended(keyEntity.id, until, reason)
                fallbackOccurred = true
                fallbackReason = "המפתח '${keyEntity.name}' ניצל את המכסה, מנסה את הבא בסדר העדיפויות"
                errorsEncountered.add("${keyEntity.name}: ${e.message}")
            } catch (e: AiCallException.RateLimit) {
                val until = System.currentTimeMillis() + (e.retryAfterSeconds * 1000)
                val reason = "הגבלת קצב בקשות (Rate Limit): ${e.message}"
                apiKeyDao.markSuspended(keyEntity.id, until, reason)
                fallbackOccurred = true
                fallbackReason = "המפתח '${keyEntity.name}' חרג מקצב בקשות, עובר למפתח הבא"
                errorsEncountered.add("${keyEntity.name}: ${e.message}")
            } catch (e: AiCallException.ServerUnavailable) {
                val until = System.currentTimeMillis() + (e.retryAfterSeconds * 1000)
                apiKeyDao.markSuspended(keyEntity.id, until, "שרת לא זמין: ${e.message}")
                fallbackOccurred = true
                fallbackReason = "שרת '${keyEntity.name}' אינו זמין כרגע, מנסה ספק חלופי"
                errorsEncountered.add("${keyEntity.name}: ${e.message}")
            } catch (e: Exception) {
                errorsEncountered.add("${keyEntity.name}: ${e.message}")
                fallbackOccurred = true
                fallbackReason = "שגיאה ב-${keyEntity.name}: ${e.message}"
            }
        }

        // All keys failed or suspended
        val errSummary = if (errorsEncountered.isNotEmpty()) {
            errorsEncountered.joinToString("\n• ")
        } else {
            "כל המפתחות מושהים זמנית עקב מגבלת מכסה. אנא המתן להתחדשות המכסה או הוסף מפתח נוסף."
        }
        val errText = "⚠️ לא התקבל מענה מאף מודל:\n• $errSummary"

        // Save error record
        val errorMsg = ChatMessageEntity(
            conversationId = conv.id,
            role = "error",
            content = errText,
            timestamp = System.currentTimeMillis()
        )
        chatDao.addMessageAndUpdateConversation(conv.id, errorMsg, errText)

        throw IllegalStateException(errText)
    }

    private suspend fun buildCrossConversationSummary(currentConvId: Long): String {
        return try {
            val pastConversations = chatDao.getAllConversationsList().filter { it.id != currentConvId }.take(5)
            if (pastConversations.isEmpty()) return ""

            val sb = StringBuilder()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

            for (conv in pastConversations) {
                sb.append("--- שיחה מתאריך ${dateFormat.format(Date(conv.updatedAt))} (נושא: ${conv.title}) ---\n")
                val messages = chatDao.getMessagesForConversation(conv.id).filter { it.role != "error" }.takeLast(4)
                for (m in messages) {
                    val sender = if (m.role == "user") "משתמש" else "AI"
                    sb.append("$sender: ${m.content.take(150)}\n")
                }
                sb.append("\n")
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    // Key management
    suspend fun saveApiKey(
        id: String? = null,
        provider: ProviderType,
        name: String,
        plainKey: String,
        model: String,
        priority: Int = 1,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096,
        systemPrompt: String? = null
    ) {
        val encrypted = CryptoEngine.encrypt(plainKey)
        val key = ApiKeyEntity(
            id = id ?: java.util.UUID.randomUUID().toString(),
            provider = provider,
            name = name.ifBlank { "${provider.displayName} #${priority}" },
            encryptedKey = encrypted,
            selectedModel = model.ifBlank { provider.defaultModel },
            priority = priority,
            isEnabled = true,
            temperature = temperature,
            maxTokens = maxTokens,
            systemPrompt = systemPrompt?.takeIf { it.isNotBlank() }
        )
        apiKeyDao.insertOrUpdate(key)
    }

    suspend fun fetchAvailableModels(provider: ProviderType, apiKey: String): List<String> {
        return apiClient.fetchAvailableModels(provider, apiKey)
    }

    suspend fun getDecryptedKey(id: String): String {
        val key = apiKeyDao.getKeyById(id) ?: return ""
        return CryptoEngine.decrypt(key.encryptedKey)
    }

    suspend fun updateKeyEntity(entity: ApiKeyEntity) {
        apiKeyDao.update(entity)
    }

    suspend fun deleteKey(entity: ApiKeyEntity) {
        apiKeyDao.delete(entity)
    }

    suspend fun clearKeySuspension(id: String) {
        apiKeyDao.clearSuspension(id)
    }

    suspend fun swapPriorities(key1: ApiKeyEntity, key2: ApiKeyEntity) {
        val p1 = key1.priority
        val p2 = key2.priority
        apiKeyDao.updatePriority(key1.id, p2)
        apiKeyDao.updatePriority(key2.id, p1)
    }

    suspend fun deleteConversation(id: Long) {
        chatDao.deleteConversationById(id)
    }

    suspend fun clearAllHistory() {
        chatDao.clearAllConversations()
    }

    private fun readAttachmentFromUri(uri: Uri): AttachmentData? {
        return try {
            if (uri.scheme == "file" || (uri.scheme == null && uri.path != null)) {
                val file = java.io.File(uri.path ?: "")
                if (!file.exists()) return null
                val bytes = file.readBytes()
                val mime = when {
                    file.name.endsWith(".jpg", true) || file.name.endsWith(".jpeg", true) -> "image/jpeg"
                    file.name.endsWith(".png", true) -> "image/png"
                    file.name.endsWith(".webp", true) -> "image/webp"
                    else -> "application/octet-stream"
                }
                return AttachmentData(
                    name = file.name,
                    mimeType = mime,
                    bytes = bytes
                )
            }

            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            var fileName = "attachment"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                }
            }

            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bytes = inputStream?.use { it.readBytes() } ?: return null

            AttachmentData(
                name = fileName,
                mimeType = mimeType,
                bytes = bytes
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
