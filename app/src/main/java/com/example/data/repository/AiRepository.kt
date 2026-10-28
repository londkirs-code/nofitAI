package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.ApiKeyDao
import com.example.data.local.ChatDao
import com.example.data.model.AiTaskType
import com.example.data.model.ApiKeyEntity
import com.example.data.model.ChatConversationEntity
import com.example.data.model.ChatMessageEntity
import com.example.data.model.FetchModelsResponse
import com.example.data.model.ProviderType
import com.example.data.remote.AiApiClient
import com.example.data.remote.AiCallException
import com.example.data.remote.AiResponse
import com.example.data.remote.AttachmentData
import com.example.data.remote.BlockScope
import com.example.data.remote.ChatTurn
import com.example.data.security.CryptoEngine
import com.example.util.MediaStorageManager
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class QueryResult(
    val responseText: String,
    val provider: ProviderType,
    val model: String,
    val keyName: String,
    val fallbackOccurred: Boolean,
    val fallbackReason: String? = null,
    val conversationId: Long = 0,
    val tokensUsed: Int = 0,
    val generatedAttachmentUri: String? = null,
    val generatedAttachmentMimeType: String? = null,
    val generatedAttachmentName: String? = null
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
     * Infers the effective task type from the user's prompt, attachment, and requested output mode (Tasks #2, #6, #16).
     */
    fun resolveRequiredTaskType(
        prompt: String,
        attachment: AttachmentData?,
        requestedOutputType: AiTaskType = AiTaskType.TEXT
    ): AiTaskType {
        if (requestedOutputType != AiTaskType.TEXT) {
            return requestedOutputType
        }
        if (attachment != null) {
            return when {
                attachment.mimeType.startsWith("image/") -> AiTaskType.IMAGE_INPUT
                else -> AiTaskType.FILE_INPUT
            }
        }
        val lower = prompt.lowercase()
        val isImageGenPrompt = listOf(
            "צור תמונה", "צייר", "ייצר תמונה", "תמונה של", "generate image", "draw ", "create an image"
        ).any { lower.contains(it) }
        if (isImageGenPrompt) return AiTaskType.IMAGE_GEN

        val isAudioGenPrompt = listOf(
            "צור סאונד", "צור שיר", "הקרא בקול", "צור שמע", "generate audio", "text to speech"
        ).any { lower.contains(it) }
        if (isAudioGenPrompt) return AiTaskType.AUDIO_GEN

        val isVideoGenPrompt = listOf(
            "צור וידאו", "ייצר וידאו", "צור סרטון", "generate video"
        ).any { lower.contains(it) }
        if (isVideoGenPrompt) return AiTaskType.VIDEO_GEN

        return AiTaskType.TEXT
    }

    /**
     * Executes AI query with task-based routing, multi-model fallback within keys, multi-key priority fallback,
     * smart Hebrew error classification, and rich media output generation.
     */
    suspend fun askAi(
        prompt: String,
        attachmentUri: Uri? = null,
        directAttachment: AttachmentData? = null,
        conversationId: Long? = null,
        requestedOutputType: AiTaskType? = null
    ): QueryResult {
        val allEnabledKeys = apiKeyDao.getEnabledKeys()
        if (allEnabledKeys.isEmpty()) {
            throw IllegalStateException("לא הוגדרו או לא הופעלו מפתחות API באפליקציה. אנא הפעל או הוסף מפתח.")
        }

        val conv = if (conversationId != null) {
            chatDao.getConversationById(conversationId) ?: getOrCreateActiveConversation()
        } else {
            getOrCreateActiveConversation()
        }

        val attachment = directAttachment ?: attachmentUri?.let { readAttachmentFromUri(it) }

        val persistentMediaUri = if (attachment != null) {
            MediaStorageManager.persistMediaForChat(
                context = context,
                sourceUri = attachmentUri,
                directBytes = directAttachment?.bytes,
                mimeType = attachment.mimeType,
                originalName = attachment.name
            )
        } else {
            null
        }

        val effectiveOutputType = requestedOutputType ?: getPreferredOutputType(context)
        val requiredTask = resolveRequiredTaskType(prompt, attachment, effectiveOutputType)

        val priorMessages = chatDao.getMessagesForConversation(conv.id)
            .filter { it.role == "user" || it.role == "assistant" }
            .takeLast(16)
        val historyTurns = priorMessages.map { ChatTurn(it.role, it.content) }

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

        val userMessage = ChatMessageEntity(
            conversationId = conv.id,
            role = "user",
            content = prompt,
            timestamp = System.currentTimeMillis(),
            attachmentUri = persistentMediaUri ?: attachmentUri?.toString(),
            attachmentMimeType = attachment?.mimeType,
            attachmentName = attachment?.name
        )
        chatDao.addMessageAndUpdateConversation(conv.id, userMessage, prompt)

        // Sort keys so:
        // 1. Any key explicitly dedicated to requiredTask comes first,
        // 2. Keys supporting requiredTask come next (in priority order),
        // 3. Remaining enabled keys come last as automatic fallback (Tasks #2 & #13).
        val dedicatedKeyId = getDedicatedKeyIdForTask(context, requiredTask)
        val dedicatedKey = allEnabledKeys.find { it.id == dedicatedKeyId }
        val matchingKeys = allEnabledKeys
            .filter { it.id != dedicatedKeyId && requiredTask in it.parsedSupportedTasks }
            .sortedBy { it.priority }
        val otherFallbackKeys = allEnabledKeys
            .filter { it.id != dedicatedKeyId && requiredTask !in it.parsedSupportedTasks }
            .sortedBy { it.priority }
        val orderedKeysToTry = listOfNotNull(dedicatedKey) + matchingKeys + otherFallbackKeys

        var fallbackOccurred = false
        var fallbackReason: String? = null
        val errorsEncountered = mutableListOf<String>()

        for (keyEntity in orderedKeysToTry) {
            if (keyEntity.isSuspended) {
                fallbackOccurred = true
                val remainingStr = ApiKeyEntity.formatDurationHe(keyEntity.remainingSuspensionSeconds)
                val skipMsg = "המפתח '${keyEntity.name}' חסום זמנית לעוד $remainingStr (${keyEntity.suspensionReason ?: "מכסה"})"
                fallbackReason = skipMsg
                errorsEncountered.add(skipMsg)
                continue
            }

            val rawKey = CryptoEngine.decrypt(keyEntity.encryptedKey)
            if (rawKey.isBlank()) {
                continue
            }

            // Try primary model + any enabled fallback models within this key in priority order (Task #3)
            val candidateModels = keyEntity.orderedCandidateModels
            var currentKeySnapshot = keyEntity

            for ((modelIndex, candidateModel) in candidateModels.withIndex()) {
                val modelRemSec = currentKeySnapshot.getModelSuspensionRemainingSeconds(candidateModel)
                if (modelRemSec > 0L) {
                    fallbackOccurred = true
                    val remStr = ApiKeyEntity.formatDurationHe(modelRemSec)
                    val modelSkipMsg = "המודל '$candidateModel' במפתח '${keyEntity.name}' חסום לעוד $remStr"
                    fallbackReason = modelSkipMsg
                    errorsEncountered.add(modelSkipMsg)
                    continue
                }

                if (modelIndex > 0) {
                    fallbackOccurred = true
                }

                try {
                    val baseSysPrompt = keyEntity.systemPrompt?.takeIf { it.isNotBlank() }
                        ?: com.example.util.AppStrings.defaultAiLanguageInstruction()

                    val outputModeInstruction = when (requiredTask) {
                        AiTaskType.IMAGE_GEN -> "\nהמשתמש ביקש ייצור תמונה. תאר בצורה עשירה, מפורטת וציורית את התמונה המבוקשת."
                        AiTaskType.AUDIO_GEN -> "\nהמשתמש ביקש ייצור סאונד/שמע. כתוב את הטקסט המלא להקראה קולית או את מילות השיר בצורה קולחת וברורה."
                        AiTaskType.VIDEO_GEN -> "\nהמשתמש ביקש ייצור וידאו/סרטון. פרט תסריט ויזואלי מלא וסצנות מדויקות."
                        else -> ""
                    }

                    val effectiveSystemInstruction = buildString {
                        append(baseSysPrompt)
                        append(outputModeInstruction)
                        if (crossConversationContext.isNotBlank()) {
                            append("\n\nהנה מידע והקשר משיחות קודמות שקיימת עם המשתמש בעבר:\n")
                            append(crossConversationContext)
                        }
                    }

                    val effectiveMaxTokens = if (keyEntity.provider == ProviderType.OPENROUTER) {
                        maxOf(keyEntity.maxTokens, 8192)
                    } else {
                        maxOf(keyEntity.maxTokens, 4096)
                    }

                    val response: AiResponse = apiClient.queryAi(
                        provider = keyEntity.provider,
                        apiKey = rawKey,
                        model = candidateModel,
                        prompt = prompt,
                        history = historyTurns,
                        systemInstruction = effectiveSystemInstruction,
                        temperature = keyEntity.temperature,
                        maxTokens = effectiveMaxTokens,
                        attachment = attachment,
                        customBaseUrl = keyEntity.customBaseUrl,
                        customProtocol = keyEntity.customProtocol
                    )

                    apiKeyDao.recordSuccess(keyEntity.id)

                    // Handle generated media (from native API bytes, synthesized for requested output mode, or URL in response)
                    var genUri: String? = null
                    var genMime: String? = null
                    var genName: String? = null

                    if (response.generatedMediaBytes != null && response.generatedMediaBytes.isNotEmpty()) {
                        genMime = response.generatedMediaMimeType ?: "image/png"
                        genName = response.generatedMediaName ?: "notifai_output"
                        genUri = MediaStorageManager.persistMediaForChat(
                            context = context,
                            directBytes = response.generatedMediaBytes,
                            mimeType = genMime,
                            originalName = genName,
                            forceSave = true
                        )
                    } else when (requiredTask) {
                        AiTaskType.IMAGE_GEN -> {
                            genMime = "image/png"
                            genName = "notifai_image_${System.currentTimeMillis()}.png"
                            genUri = MediaStorageManager.generateVisualPosterPng(
                                context = context,
                                prompt = prompt,
                                aiDescription = response.text
                            )
                        }
                        AiTaskType.AUDIO_GEN -> {
                            genMime = "audio/wav"
                            genName = "notifai_audio_${System.currentTimeMillis()}.wav"
                            genUri = MediaStorageManager.synthesizeAudioFile(
                                context = context,
                                textToSpeak = response.text
                            )
                        }
                        AiTaskType.VIDEO_GEN -> {
                            val mp4Path = MediaStorageManager.generateMp4VideoFile(
                                context = context,
                                prompt = prompt,
                                aiDescription = response.text
                            )
                            if (mp4Path != null) {
                                genMime = "video/mp4"
                                genName = "notifai_video_${System.currentTimeMillis()}.mp4"
                                genUri = mp4Path
                            } else {
                                genMime = "image/png"
                                genName = "notifai_video_storyboard_${System.currentTimeMillis()}.png"
                                genUri = MediaStorageManager.generateVisualPosterPng(
                                    context = context,
                                    prompt = "🎬 Storyboard: $prompt",
                                    aiDescription = response.text
                                )
                            }
                        }
                        else -> {
                            if (effectiveOutputType == AiTaskType.FILE_INPUT) {
                                genMime = "text/plain"
                                genName = "notifai_document_${System.currentTimeMillis()}.txt"
                                genUri = MediaStorageManager.persistMediaForChat(
                                    context = context,
                                    directBytes = response.text.toByteArray(Charsets.UTF_8),
                                    mimeType = genMime,
                                    originalName = genName,
                                    forceSave = true
                                )
                            }
                        }
                    }

                    // If the chat response contains a direct link to an image, video, song, or file, download it automatically (Tasks #7 & #8)
                    if (genUri == null) {
                        val extractedMedia = MediaStorageManager.extractAndDownloadFirstMediaUrl(context, response.text)
                        if (extractedMedia != null) {
                            genUri = extractedMedia.first
                            genMime = extractedMedia.second
                            genName = extractedMedia.third
                        }
                    }

                    val assistantMessage = ChatMessageEntity(
                        conversationId = conv.id,
                        role = "assistant",
                        content = response.text,
                        provider = response.provider.displayName,
                        model = response.model,
                        tokens = response.tokensUsed,
                        timestamp = System.currentTimeMillis(),
                        attachmentUri = genUri,
                        attachmentMimeType = genMime,
                        attachmentName = genName
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
                        tokensUsed = response.tokensUsed,
                        generatedAttachmentUri = genUri,
                        generatedAttachmentMimeType = genMime,
                        generatedAttachmentName = genName
                    )

                } catch (e: AiCallException.SmartApiException) {
                    val analyzed = e.analyzed
                    val until = System.currentTimeMillis() + (analyzed.blockDurationSeconds * 1000L)
                    fallbackOccurred = true
                    fallbackReason = "דילוג אוטומטי מ-'$candidateModel' (${keyEntity.name}): ${analyzed.category.badgeHe}"
                    errorsEncountered.add("${keyEntity.name} ($candidateModel): ${analyzed.hebrewSummary}")

                    if (analyzed.scope == BlockScope.ENTIRE_KEY) {
                        apiKeyDao.markSuspended(keyEntity.id, until, analyzed.hebrewSummary)
                        break // Entire key is blocked; move immediately to the next key!
                    } else {
                        // Only this specific model is blocked; mark model suspended and try next fallback model in this key!
                        val updatedCsv = currentKeySnapshot.withModelSuspended(candidateModel, until)
                        currentKeySnapshot = currentKeySnapshot.copy(modelSuspensionsCsv = updatedCsv)
                        apiKeyDao.markModelSuspended(keyEntity.id, updatedCsv, analyzed.hebrewSummary)
                    }
                } catch (e: AiCallException.QuotaExceeded) {
                    val until = System.currentTimeMillis() + (e.suspensionSeconds * 1000)
                    val reason = "נגמרה המכסה במפתח [כל ה-API]: ${e.message}"
                    apiKeyDao.markSuspended(keyEntity.id, until, reason)
                    fallbackOccurred = true
                    fallbackReason = "המפתח '${keyEntity.name}' ניצל את המכסה, עובר אוטומטית למודל/מפתח הבא"
                    errorsEncountered.add("${keyEntity.name}: $reason")
                    break
                } catch (e: AiCallException.RateLimit) {
                    val until = System.currentTimeMillis() + (e.retryAfterSeconds * 1000)
                    val updatedCsv = currentKeySnapshot.withModelSuspended(candidateModel, until)
                    currentKeySnapshot = currentKeySnapshot.copy(modelSuspensionsCsv = updatedCsv)
                    val reason = "הגבלת קצב [מודל '$candidateModel']: חסום ל-${ApiKeyEntity.formatDurationHe(e.retryAfterSeconds)}"
                    apiKeyDao.markModelSuspended(keyEntity.id, updatedCsv, reason)
                    fallbackOccurred = true
                    fallbackReason = "המודל '$candidateModel' חרג מקצב בקשות, עובר למודל הבא"
                    errorsEncountered.add("${keyEntity.name}: $reason")
                } catch (e: AiCallException.ServerUnavailable) {
                    val until = System.currentTimeMillis() + (e.retryAfterSeconds * 1000)
                    val reason = "שרת לא זמין (${e.message}) — מושהה ל-${ApiKeyEntity.formatDurationHe(e.retryAfterSeconds)}"
                    apiKeyDao.markSuspended(keyEntity.id, until, reason)
                    fallbackOccurred = true
                    fallbackReason = "שרת '${keyEntity.name}' אינו זמין כרגע, עובר למודל הבא"
                    errorsEncountered.add("${keyEntity.name}: $reason")
                    break
                } catch (e: Exception) {
                    errorsEncountered.add("${keyEntity.name} ($candidateModel): ${e.message}")
                    fallbackOccurred = true
                    fallbackReason = "שגיאה ב-$candidateModel (${keyEntity.name}), מנסה את המודל הבא"
                }
            }
        }

        val errSummary = if (errorsEncountered.isNotEmpty()) {
            errorsEncountered.joinToString("\n• ")
        } else {
            "כל המפתחות והמודלים מושהים זמנית עקב מגבלת מכסה. אנא המתן להתחדשות או הפעל מפתח נוסף."
        }
        val errText = "⚠️ לא התקבל מענה (בוצע ניסיון מעבר אוטומטי בין כל המודלים הפעילים):\n• $errSummary"

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

    suspend fun saveApiKey(
        id: String? = null,
        provider: ProviderType,
        name: String,
        plainKey: String,
        model: String,
        priority: Int = 1,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096,
        systemPrompt: String? = null,
        supportedTasks: String = AiTaskType.DEFAULT_TASKS_CSV,
        fallbackModels: String = "",
        customBaseUrl: String? = null,
        customProtocol: String = "OPENAI"
    ) {
        val existing = if (id != null) apiKeyDao.getKeyById(id) else null
        val encrypted = if (plainKey.isBlank() && existing != null) {
            existing.encryptedKey
        } else {
            CryptoEngine.encrypt(plainKey)
        }
        val key = ApiKeyEntity(
            id = id ?: UUID.randomUUID().toString(),
            provider = provider,
            name = name.ifBlank { "${provider.displayName} #${priority}" },
            encryptedKey = encrypted,
            selectedModel = model.ifBlank { provider.defaultModel },
            priority = priority,
            isEnabled = existing?.isEnabled ?: true,
            temperature = temperature,
            maxTokens = maxTokens,
            systemPrompt = systemPrompt?.takeIf { it.isNotBlank() },
            customBaseUrl = customBaseUrl?.trim()?.takeIf { it.isNotBlank() },
            supportedTasks = supportedTasks.ifBlank { AiTaskType.DEFAULT_TASKS_CSV },
            fallbackModels = fallbackModels.trim(),
            customProtocol = customProtocol.ifBlank { "OPENAI" }
        )
        apiKeyDao.insertOrUpdate(key)
    }

    /**
     * Duplicates an existing API key (preserving its encrypted key without exposing it)
     * so the user can assign a different model, task type, or priority in 1 click (Task #3).
     */
    suspend fun duplicateKey(keyId: String): ApiKeyEntity? {
        val original = apiKeyDao.getKeyById(keyId) ?: return null
        val allKeys = apiKeyDao.getAllKeys()
        val nextPriority = (allKeys.maxOfOrNull { it.priority } ?: 0) + 1
        val altModel = original.provider.suggestedModels.firstOrNull { it != original.selectedModel }
            ?: original.selectedModel
        val duplicated = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (עותק)",
            selectedModel = altModel,
            priority = nextPriority,
            isEnabled = true,
            suspendedUntil = 0L,
            suspensionReason = null,
            modelSuspensionsCsv = "",
            createdAt = System.currentTimeMillis()
        )
        apiKeyDao.insertOrUpdate(duplicated)
        return duplicated
    }

    suspend fun fetchModelsForKeyId(keyId: String): FetchModelsResponse {
        val key = apiKeyDao.getKeyById(keyId) ?: return FetchModelsResponse(false, emptyList(), "מפתח לא נמצא")
        val plainKey = CryptoEngine.decrypt(key.encryptedKey)
        return apiClient.fetchAvailableModelsDetailed(
            provider = key.provider,
            apiKey = plainKey,
            customBaseUrl = key.customBaseUrl,
            customProtocol = key.customProtocol
        )
    }

    suspend fun fetchAvailableModels(provider: ProviderType, apiKey: String): List<String> {
        return apiClient.fetchAvailableModels(provider, apiKey)
    }

    suspend fun fetchAvailableModelsDetailed(
        provider: ProviderType,
        apiKey: String,
        customBaseUrl: String? = null,
        customProtocol: String = "OPENAI"
    ): FetchModelsResponse {
        return apiClient.fetchAvailableModelsDetailed(provider, apiKey, customBaseUrl, customProtocol)
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

    suspend fun setKeyAsPrimary(keyId: String) {
        val target = apiKeyDao.getKeyById(keyId) ?: return
        val enabledKeys = apiKeyDao.getEnabledKeys().filter { it.id != keyId }.sortedBy { it.priority }
        apiKeyDao.update(target.copy(isEnabled = true, priority = 1))
        enabledKeys.forEachIndexed { index, keyEntity ->
            apiKeyDao.updatePriority(keyEntity.id, index + 2)
        }
    }

    suspend fun getLatestAssistantResponse(): String {
        val latestConv = chatDao.getLatestConversation() ?: return ""
        val msgs = chatDao.getMessagesForConversation(latestConv.id)
        return msgs.lastOrNull { it.role == "assistant" }?.content.orEmpty()
    }

    suspend fun getLatestAssistantMessage(): ChatMessageEntity? {
        val latestConv = chatDao.getLatestConversation() ?: return null
        val msgs = chatDao.getMessagesForConversation(latestConv.id)
        return msgs.lastOrNull { it.role == "assistant" }
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
                    file.name.endsWith(".m4a", true) -> "audio/mp4"
                    file.name.endsWith(".aac", true) -> "audio/aac"
                    file.name.endsWith(".mp3", true) -> "audio/mpeg"
                    file.name.endsWith(".wav", true) -> "audio/wav"
                    file.name.endsWith(".ogg", true) -> "audio/ogg"
                    file.name.endsWith(".mp4", true) -> "video/mp4"
                    file.name.endsWith(".pdf", true) -> "application/pdf"
                    file.name.endsWith(".txt", true) -> "text/plain"
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

    companion object {
        private const val PREF_REQUESTED_OUTPUT_TYPE = "preferred_output_type"
        private const val PREF_DEDICATED_KEY_PREFIX = "dedicated_key_for_task_"

        fun getPreferredOutputType(context: Context): AiTaskType {
            val prefs = context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
            val saved = prefs.getString(PREF_REQUESTED_OUTPUT_TYPE, AiTaskType.TEXT.name)
            return AiTaskType.entries.find { it.name == saved } ?: AiTaskType.TEXT
        }

        fun setPreferredOutputType(context: Context, taskType: AiTaskType) {
            context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_REQUESTED_OUTPUT_TYPE, taskType.name)
                .apply()
        }

        fun getDedicatedKeyIdForTask(context: Context, taskType: AiTaskType): String? {
            val prefs = context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
            return prefs.getString(PREF_DEDICATED_KEY_PREFIX + taskType.name, null)?.takeIf { it.isNotBlank() }
        }

        fun setDedicatedKeyIdForTask(context: Context, taskType: AiTaskType, keyId: String?) {
            context.getSharedPreferences("notifai_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_DEDICATED_KEY_PREFIX + taskType.name, keyId ?: "")
                .apply()
        }
    }
}
