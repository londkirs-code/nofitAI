package com.example.data.remote

import android.util.Base64
import com.example.data.model.AiModelInfo
import com.example.data.model.CustomApiProtocol
import com.example.data.model.FetchModelsResponse
import com.example.data.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class AiCallException(message: String) : Exception(message) {
    class SmartApiException(
        val analyzed: AnalyzedApiError
    ) : AiCallException(analyzed.hebrewSummary)

    class RateLimit(message: String, val retryAfterSeconds: Long) : AiCallException(message)
    class QuotaExceeded(message: String, val suspensionSeconds: Long) : AiCallException(message)
    class AuthenticationError(message: String) : AiCallException(message)
    class ServerUnavailable(message: String, val retryAfterSeconds: Long) : AiCallException(message)
    class GeneralError(message: String, val code: Int = -1) : AiCallException(message)
}

data class AiResponse(
    val text: String,
    val provider: ProviderType,
    val model: String,
    val tokensUsed: Int = 0,
    val generatedMediaBytes: ByteArray? = null,
    val generatedMediaMimeType: String? = null,
    val generatedMediaName: String? = null
)

data class AttachmentData(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray
)

data class ChatTurn(
    val role: String, // "user" or "assistant"
    val content: String
)

class AiApiClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(65, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun queryAi(
        provider: ProviderType,
        apiKey: String,
        model: String,
        prompt: String,
        history: List<ChatTurn> = emptyList(),
        systemInstruction: String = "You are NotifAI, a smart and helpful AI assistant. Always respond in fluent Hebrew (unless asked otherwise) with complete, detailed, and well-structured answers. Never cut off or truncate your response.",
        temperature: Float = 0.7f,
        maxTokens: Int = 8192,
        attachment: AttachmentData? = null,
        customBaseUrl: String? = null,
        customProtocol: String = "OPENAI"
    ): AiResponse = withContext(Dispatchers.IO) {
        // Check if OpenAI DALL-E or TTS endpoint is appropriate
        val lowerModel = model.lowercase()
        if ((provider == ProviderType.OPENAI || (provider == ProviderType.CUSTOM && customProtocol == "OPENAI")) &&
            (lowerModel.startsWith("dall-e") || lowerModel.startsWith("gpt-image"))
        ) {
            return@withContext callOpenAiImageGeneration(provider, apiKey, model, prompt, customBaseUrl)
        }
        if ((provider == ProviderType.OPENAI || (provider == ProviderType.CUSTOM && customProtocol == "OPENAI")) &&
            (lowerModel.startsWith("tts-"))
        ) {
            return@withContext callOpenAiAudioTts(provider, apiKey, model, prompt, customBaseUrl)
        }

        when (provider) {
            ProviderType.GEMINI -> callGemini(
                provider = provider,
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                history = history,
                systemInstruction = systemInstruction,
                temperature = temperature,
                maxTokens = maxTokens,
                attachment = attachment,
                customBaseUrl = customBaseUrl
            )
            ProviderType.CLAUDE -> callClaude(
                provider = provider,
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                history = history,
                systemInstruction = systemInstruction,
                temperature = temperature,
                maxTokens = maxTokens,
                attachment = attachment,
                customBaseUrl = customBaseUrl
            )
            ProviderType.CUSTOM -> {
                when (customProtocol.uppercase()) {
                    CustomApiProtocol.GEMINI.name -> callGemini(
                        provider = provider,
                        apiKey = apiKey,
                        model = model,
                        prompt = prompt,
                        history = history,
                        systemInstruction = systemInstruction,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        attachment = attachment,
                        customBaseUrl = customBaseUrl
                    )
                    CustomApiProtocol.CLAUDE.name -> callClaude(
                        provider = provider,
                        apiKey = apiKey,
                        model = model,
                        prompt = prompt,
                        history = history,
                        systemInstruction = systemInstruction,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        attachment = attachment,
                        customBaseUrl = customBaseUrl
                    )
                    else -> callOpenAiCompatible(
                        provider = provider,
                        apiKey = apiKey,
                        model = model,
                        prompt = prompt,
                        history = history,
                        systemInstruction = systemInstruction,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        attachment = attachment,
                        customBaseUrl = customBaseUrl
                    )
                }
            }
            ProviderType.GROQ,
            ProviderType.GROK,
            ProviderType.OPENAI,
            ProviderType.OPENROUTER,
            ProviderType.DEEPSEEK,
            ProviderType.MISTRAL,
            ProviderType.HUGGINGFACE -> callOpenAiCompatible(
                provider = provider,
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                history = history,
                systemInstruction = systemInstruction,
                temperature = temperature,
                maxTokens = maxTokens,
                attachment = attachment,
                customBaseUrl = customBaseUrl
            )
        }
    }

    suspend fun fetchAvailableModels(
        provider: ProviderType,
        apiKey: String
    ): List<String> = withContext(Dispatchers.IO) {
        val result = fetchAvailableModelsDetailed(provider, apiKey)
        if (result.models.isNotEmpty()) {
            result.models.map { it.id }
        } else {
            provider.suggestedModels
        }
    }

    /**
     * Live query to each provider's API with automatic best-first sorting (Task #5).
     */
    suspend fun fetchAvailableModelsDetailed(
        provider: ProviderType,
        apiKey: String,
        customBaseUrl: String? = null,
        customProtocol: String = "OPENAI"
    ): FetchModelsResponse = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            val defaultSorted = AiModelInfo.sortBestFirst(
                provider.suggestedModels.mapIndexed { idx, it ->
                    AiModelInfo(
                        id = it,
                        displayName = it,
                        isFree = provider == ProviderType.GROQ || it.contains("free", ignoreCase = true),
                        isRecommended = idx < 3
                    )
                }
            )
            return@withContext FetchModelsResponse(
                isSuccess = false,
                models = defaultSorted,
                errorMessage = "הזן מפתח API כדי לשלוף את רשימת המודלים הפעילים בזמן אמת"
            )
        }

        try {
            val rawResponse = when (provider) {
                ProviderType.GEMINI -> fetchGeminiModels(trimmedKey)
                ProviderType.GROQ -> fetchGroqModels(trimmedKey)
                ProviderType.OPENROUTER -> fetchOpenRouterModels(trimmedKey)
                ProviderType.DEEPSEEK -> fetchDeepSeekModels(trimmedKey)
                ProviderType.CLAUDE -> fetchClaudeModels(trimmedKey)
                ProviderType.OPENAI -> fetchOpenAiModels(trimmedKey)
                ProviderType.GROK -> fetchGrokModels(trimmedKey)
                ProviderType.MISTRAL -> fetchMistralModels(trimmedKey)
                ProviderType.HUGGINGFACE -> fetchHuggingFaceModels(trimmedKey)
                ProviderType.CUSTOM -> fetchCustomModels(trimmedKey, customBaseUrl, customProtocol)
            }
            rawResponse.copy(models = AiModelInfo.sortBestFirst(rawResponse.models))
        } catch (e: Exception) {
            val fallback = AiModelInfo.sortBestFirst(
                provider.suggestedModels.mapIndexed { idx, it ->
                    AiModelInfo(
                        id = it,
                        displayName = it,
                        isFree = provider == ProviderType.GROQ || it.contains("free", ignoreCase = true),
                        isRecommended = idx < 3
                    )
                }
            )
            FetchModelsResponse(
                isSuccess = false,
                models = fallback,
                errorMessage = "שגיאת רשת בשליפת מודלים: ${e.localizedMessage ?: "לא ניתן להתחבר לשרת"}. מוצגים מודלים מומלצים בראש הרשימה."
            )
        }
    }

    private fun fetchCustomModels(apiKey: String, customBaseUrl: String?, customProtocol: String): FetchModelsResponse {
        val base = customBaseUrl?.trim()?.trimEnd('/')
        if (base.isNullOrBlank()) {
            return FetchModelsResponse(
                isSuccess = true,
                models = AiModelInfo.sortBestFirst(
                    ProviderType.CUSTOM.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) }
                )
            )
        }
        val modelsUrl = when {
            base.endsWith("/chat/completions") -> base.removeSuffix("/chat/completions") + "/models"
            base.endsWith("/messages") -> base.removeSuffix("/messages") + "/models"
            base.endsWith("/models") -> base
            else -> "$base/models"
        }
        val reqBuilder = Request.Builder().url(modelsUrl).get()
        if (customProtocol.equals("CLAUDE", true)) {
            reqBuilder.addHeader("x-api-key", apiKey)
            reqBuilder.addHeader("anthropic-version", "2023-06-01")
        } else {
            reqBuilder.addHeader("Authorization", "Bearer $apiKey")
        }
        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.CUSTOM.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) },
                    errorMessage = "השרת המותאם החזיר קוד ${resp.code}. ניתן להזין שם מודל ידנית."
                )
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val arr = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
            val list = mutableListOf<AiModelInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", obj.optString("name", "")).removePrefix("models/")
                if (id.isNotBlank()) {
                    list.add(AiModelInfo(id = id, displayName = obj.optString("displayName", id), isRecommended = i < 4))
                }
            }
            return FetchModelsResponse(
                isSuccess = true,
                models = if (list.isNotEmpty()) list else ProviderType.CUSTOM.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) }
            )
        }
    }

    private fun fetchGeminiModels(apiKey: String): FetchModelsResponse {
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val analyzed = ApiErrorAnalyzer.analyzeHttpError(resp.code, resp.headers, resp.body?.string().orEmpty(), ProviderType.GEMINI, "gemini")
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.GEMINI.suggestedModels.map { AiModelInfo(it, it, isFree = true, isRecommended = true) },
                    errorMessage = analyzed.hebrewSummary
                )
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val arr = json.optJSONArray("models") ?: JSONArray()
            val list = mutableListOf<AiModelInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val rawName = obj.optString("name", "")
                val id = rawName.removePrefix("models/")
                val supportedMethods = obj.optJSONArray("supportedGenerationMethods")
                var supportsGenerate = false
                if (supportedMethods != null) {
                    for (j in 0 until supportedMethods.length()) {
                        if (supportedMethods.optString(j) == "generateContent" || supportedMethods.optString(j) == "predict") {
                            supportsGenerate = true
                            break
                        }
                    }
                }
                if (!supportsGenerate) continue
                if (!id.startsWith("gemini", ignoreCase = true) && !id.startsWith("imagen", ignoreCase = true)) continue
                if (id.contains("embedding", ignoreCase = true) || id.contains("aqa", ignoreCase = true)) continue

                val dispName = obj.optString("displayName", id)
                val desc = obj.optString("description", "")
                val isRec = id in listOf(
                    "gemini-2.5-flash",
                    "gemini-2.5-pro",
                    "gemini-2.0-flash",
                    "gemini-2.0-flash-exp-image-generation",
                    "gemini-1.5-pro",
                    "gemini-1.5-flash"
                )
                list.add(
                    AiModelInfo(
                        id = id,
                        displayName = dispName,
                        isFree = true,
                        description = desc.take(120),
                        isRecommended = isRec
                    )
                )
            }
            return FetchModelsResponse(
                isSuccess = true,
                models = if (list.isNotEmpty()) AiModelInfo.sortBestFirst(list) else ProviderType.GEMINI.suggestedModels.map { AiModelInfo(it, it, isFree = true, isRecommended = true) }
            )
        }
    }

    private fun fetchGroqModels(apiKey: String): FetchModelsResponse {
        val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val analyzed = ApiErrorAnalyzer.analyzeHttpError(resp.code, resp.headers, resp.body?.string().orEmpty(), ProviderType.GROQ, "groq")
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.GROQ.suggestedModels.map { AiModelInfo(it, it, isFree = true, isRecommended = true) },
                    errorMessage = analyzed.hebrewSummary
                )
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val arr = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AiModelInfo>()
            val excluded = listOf("whisper", "embedding", "guard", "safeguard", "moderation", "distil-whisper")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", "")
                val active = obj.optBoolean("active", true)
                if (!active || id.isBlank()) continue
                if (excluded.any { id.contains(it, ignoreCase = true) }) continue

                val ctx = obj.optInt("context_window", 0).takeIf { it > 0 }
                val isRec = id in listOf(
                    "llama-3.3-70b-versatile",
                    "deepseek-r1-distill-llama-70b",
                    "meta-llama/llama-4-scout-17b-16e-instruct",
                    "qwen-qwq-32b",
                    "llama-3.1-8b-instant"
                )
                list.add(
                    AiModelInfo(
                        id = id,
                        displayName = id,
                        isFree = true,
                        contextWindow = ctx,
                        isRecommended = isRec
                    )
                )
            }
            return FetchModelsResponse(
                isSuccess = true,
                models = if (list.isNotEmpty()) AiModelInfo.sortBestFirst(list) else ProviderType.GROQ.suggestedModels.map { AiModelInfo(it, it, isFree = true, isRecommended = true) }
            )
        }
    }

    private fun fetchOpenRouterModels(apiKey: String): FetchModelsResponse {
        val req = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://notifai.app")
            .addHeader("X-Title", "NotifAI")
            .addHeader("Accept", "application/json")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val analyzed = ApiErrorAnalyzer.analyzeHttpError(resp.code, resp.headers, resp.body?.string().orEmpty(), ProviderType.OPENROUTER, "openrouter")
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.OPENROUTER.suggestedModels.map { AiModelInfo(it, it, isFree = it.contains("free")) },
                    errorMessage = analyzed.hebrewSummary
                )
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val arr = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AiModelInfo>()
            val excluded = listOf("whisper", "embedding", "moderation")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", "")
                if (id.isBlank() || excluded.any { id.contains(it, ignoreCase = true) }) continue

                val name = obj.optString("name", id)
                val pricing = obj.optJSONObject("pricing")
                val pPrompt = pricing?.optString("prompt", "1") ?: "1"
                val pCompl = pricing?.optString("completion", "1") ?: "1"
                val pPromptNum = pPrompt.toDoubleOrNull() ?: 1.0
                val pComplNum = pCompl.toDoubleOrNull() ?: 1.0
                val isFree = id.contains(":free", ignoreCase = true) || (pPromptNum == 0.0 && pComplNum == 0.0)
                val ctx = obj.optInt("context_length", 0).takeIf { it > 0 }
                val desc = obj.optString("description", "")

                // Parse OpenRouter architecture modalities for accurate filtering
                val arch = obj.optJSONObject("architecture")
                val modalityStr = arch?.optString("modality", "")?.lowercase().orEmpty()
                val explicitCaps = if (modalityStr.isNotBlank()) {
                    val caps = mutableSetOf(com.example.data.model.AiTaskType.TEXT, com.example.data.model.AiTaskType.FILE_INPUT)
                    if (modalityStr.contains("image->") || modalityStr.contains("+image")) {
                        caps.add(com.example.data.model.AiTaskType.IMAGE_INPUT)
                    }
                    if (modalityStr.contains("->image")) {
                        caps.add(com.example.data.model.AiTaskType.IMAGE_GEN)
                    }
                    if (modalityStr.contains("audio")) {
                        caps.add(com.example.data.model.AiTaskType.AUDIO_GEN)
                    }
                    if (modalityStr.contains("video")) {
                        caps.add(com.example.data.model.AiTaskType.VIDEO_GEN)
                    }
                    caps
                } else null

                val disp = if (isFree && !name.contains("free", ignoreCase = true)) "$name (חינמי)" else name
                val isRec = id in ProviderType.OPENROUTER.suggestedModels ||
                        (isFree && (id.contains("deepseek") || id.contains("gemini") || id.contains("llama-3.3") || id.contains("qwen") || id.contains("llama-4")))

                list.add(
                    AiModelInfo(
                        id = id,
                        displayName = disp,
                        isFree = isFree,
                        contextWindow = ctx,
                        description = desc.take(120),
                        isRecommended = isRec,
                        explicitCapabilities = explicitCaps
                    )
                )
            }
            return FetchModelsResponse(
                isSuccess = true,
                models = if (list.isNotEmpty()) AiModelInfo.sortBestFirst(list) else ProviderType.OPENROUTER.suggestedModels.map { AiModelInfo(it, it, isFree = it.contains("free")) }
            )
        }
    }

    private fun fetchDeepSeekModels(apiKey: String): FetchModelsResponse {
        val req = Request.Builder()
            .url("https://api.deepseek.com/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val analyzed = ApiErrorAnalyzer.analyzeHttpError(resp.code, resp.headers, resp.body?.string().orEmpty(), ProviderType.DEEPSEEK, "deepseek")
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.DEEPSEEK.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) },
                    errorMessage = analyzed.hebrewSummary
                )
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val arr = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AiModelInfo>()
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("id", "")
                if (id.isNotBlank()) {
                    list.add(AiModelInfo(id = id, displayName = id, isRecommended = true))
                }
            }
            if (list.isEmpty()) {
                list.addAll(ProviderType.DEEPSEEK.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) })
            }
            return FetchModelsResponse(isSuccess = true, models = AiModelInfo.sortBestFirst(list))
        }
    }

    private fun fetchClaudeModels(apiKey: String): FetchModelsResponse {
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/models")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val analyzed = ApiErrorAnalyzer.analyzeHttpError(resp.code, resp.headers, resp.body?.string().orEmpty(), ProviderType.CLAUDE, "claude")
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.CLAUDE.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) },
                    errorMessage = analyzed.hebrewSummary
                )
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val arr = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AiModelInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", "")
                val disp = obj.optString("display_name", id)
                if (id.isNotBlank()) {
                    val isRec = id.contains("sonnet", ignoreCase = true) || id.contains("haiku", ignoreCase = true)
                    list.add(AiModelInfo(id = id, displayName = disp, isRecommended = isRec))
                }
            }
            return FetchModelsResponse(
                isSuccess = true,
                models = if (list.isNotEmpty()) AiModelInfo.sortBestFirst(list) else ProviderType.CLAUDE.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) }
            )
        }
    }

    private fun fetchOpenAiModels(apiKey: String): FetchModelsResponse {
        val req = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val analyzed = ApiErrorAnalyzer.analyzeHttpError(resp.code, resp.headers, resp.body?.string().orEmpty(), ProviderType.OPENAI, "gpt-4o")
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.OPENAI.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) },
                    errorMessage = analyzed.hebrewSummary
                )
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val arr = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AiModelInfo>()
            val excluded = listOf("whisper", "embedding", "babbage", "davinci", "moderation", "realtime", "instruct", "canary")
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("id", "")
                if (id.isBlank()) continue
                if (excluded.any { id.contains(it, ignoreCase = true) }) continue
                val isSupported = id.startsWith("gpt-") || id.startsWith("o1") || id.startsWith("o3") ||
                        id.startsWith("chatgpt-") || id.startsWith("dall-e") || id.startsWith("tts-")
                if (!isSupported) continue

                val isRec = id in listOf("gpt-4o", "gpt-4o-mini", "o3-mini", "o1-mini", "dall-e-3", "tts-1")
                list.add(AiModelInfo(id = id, displayName = id, isRecommended = isRec))
            }
            return FetchModelsResponse(
                isSuccess = true,
                models = if (list.isNotEmpty()) AiModelInfo.sortBestFirst(list) else ProviderType.OPENAI.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) }
            )
        }
    }

    private fun fetchGrokModels(apiKey: String): FetchModelsResponse {
        val req = Request.Builder()
            .url("https://api.x.ai/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val analyzed = ApiErrorAnalyzer.analyzeHttpError(resp.code, resp.headers, resp.body?.string().orEmpty(), ProviderType.GROK, "grok")
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.GROK.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) },
                    errorMessage = analyzed.hebrewSummary
                )
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val arr = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AiModelInfo>()
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("id", "")
                if (id.isNotBlank() && !id.contains("embedding", ignoreCase = true)) {
                    val isRec = id.startsWith("grok-3") || id.startsWith("grok-2")
                    list.add(AiModelInfo(id = id, displayName = id, isRecommended = isRec))
                }
            }
            return FetchModelsResponse(
                isSuccess = true,
                models = if (list.isNotEmpty()) AiModelInfo.sortBestFirst(list) else ProviderType.GROK.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) }
            )
        }
    }

    private fun fetchMistralModels(apiKey: String): FetchModelsResponse {
        val req = Request.Builder()
            .url("https://api.mistral.ai/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val analyzed = ApiErrorAnalyzer.analyzeHttpError(resp.code, resp.headers, resp.body?.string().orEmpty(), ProviderType.MISTRAL, "mistral")
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.MISTRAL.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) },
                    errorMessage = analyzed.hebrewSummary
                )
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val arr = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AiModelInfo>()
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("id", "")
                if (id.isNotBlank() && !id.contains("embed", ignoreCase = true)) {
                    val isRec = id in listOf("mistral-large-latest", "pixtral-large-latest", "codestral-latest", "mistral-small-latest")
                    list.add(AiModelInfo(id = id, displayName = id, isRecommended = isRec))
                }
            }
            return FetchModelsResponse(
                isSuccess = true,
                models = if (list.isNotEmpty()) AiModelInfo.sortBestFirst(list) else ProviderType.MISTRAL.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) }
            )
        }
    }

    private fun fetchHuggingFaceModels(apiKey: String): FetchModelsResponse {
        val req = Request.Builder()
            .url("https://huggingface.co/api/models?pipeline_tag=text-generation&sort=trending&limit=500")
            .apply {
                if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey")
            }
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val analyzed = ApiErrorAnalyzer.analyzeHttpError(resp.code, resp.headers, resp.body?.string().orEmpty(), ProviderType.HUGGINGFACE, "hf")
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.HUGGINGFACE.suggestedModels.map { AiModelInfo(it, it, isFree = true, isRecommended = true) },
                    errorMessage = analyzed.hebrewSummary
                )
            }
            val body = resp.body?.string().orEmpty()
            val arr = JSONArray(body)
            val list = mutableListOf<AiModelInfo>()
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("id", "")
                if (id.isNotBlank()) {
                    list.add(AiModelInfo(id = id, displayName = id, isFree = true, isRecommended = i < 5))
                }
            }
            return FetchModelsResponse(
                isSuccess = true,
                models = if (list.isNotEmpty()) AiModelInfo.sortBestFirst(list) else ProviderType.HUGGINGFACE.suggestedModels.map { AiModelInfo(it, it, isFree = true, isRecommended = true) }
            )
        }
    }

    private fun callOpenAiImageGeneration(
        provider: ProviderType,
        apiKey: String,
        model: String,
        prompt: String,
        customBaseUrl: String?
    ): AiResponse {
        val base = customBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: "https://api.openai.com/v1"
        val url = if (base.endsWith("/images/generations")) base else "${base.removeSuffix("/chat/completions")}/images/generations"
        val rootJson = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            put("n", 1)
            put("size", "1024x1024")
            put("response_format", "b64_json")
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(rootJson.toString().toRequestBody(jsonMediaType))
            .build()

        return executeAndParse(request, provider, model) { responseBody, _ ->
            val json = JSONObject(responseBody)
            val dataArr = json.optJSONArray("data")
            if (dataArr != null && dataArr.length() > 0) {
                val first = dataArr.getJSONObject(0)
                val b64 = first.optString("b64_json", "")
                val revisedPrompt = first.optString("revised_prompt", "התמונה נוצרה בהצלחה על ידי $model 🎨")
                if (b64.isNotBlank()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    return@executeAndParse AiResponse(
                        text = revisedPrompt,
                        provider = provider,
                        model = model,
                        tokensUsed = 0,
                        generatedMediaBytes = bytes,
                        generatedMediaMimeType = "image/png",
                        generatedMediaName = "dalle_generated.png"
                    )
                }
                val imgUrl = first.optString("url", "")
                if (imgUrl.isNotBlank()) {
                    val bytes = downloadUrlBytes(imgUrl)
                    return@executeAndParse AiResponse(
                        text = "$revisedPrompt\n$imgUrl",
                        provider = provider,
                        model = model,
                        tokensUsed = 0,
                        generatedMediaBytes = bytes,
                        generatedMediaMimeType = "image/png",
                        generatedMediaName = "dalle_generated.png"
                    )
                }
            }
            AiResponse("התמונה נוצרה על ידי $model", provider, model, 0)
        }
    }

    private fun callOpenAiAudioTts(
        provider: ProviderType,
        apiKey: String,
        model: String,
        prompt: String,
        customBaseUrl: String?
    ): AiResponse {
        val base = customBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: "https://api.openai.com/v1"
        val url = if (base.endsWith("/audio/speech")) base else "${base.removeSuffix("/chat/completions")}/audio/speech"
        val rootJson = JSONObject().apply {
            put("model", model)
            put("input", prompt)
            put("voice", "alloy")
            put("response_format", "mp3")
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(rootJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw AiCallException.ServerUnavailable("בעיית תקשורת לרשת: ${e.message}", 30L)
        }
        if (response.isSuccessful) {
            val bytes = response.body?.bytes()
            return AiResponse(
                text = "🔊 קובץ השמע הופק בהצלחה על ידי $model:\n\"${prompt.take(200)}\"",
                provider = provider,
                model = model,
                tokensUsed = 0,
                generatedMediaBytes = bytes,
                generatedMediaMimeType = "audio/mpeg",
                generatedMediaName = "tts_speech.mp3"
            )
        }
        val errBody = response.body?.string().orEmpty()
        val analyzed = ApiErrorAnalyzer.analyzeHttpError(response.code, response.headers, errBody, provider, model)
        throw AiCallException.SmartApiException(analyzed)
    }

    private fun downloadUrlBytes(url: String): ByteArray? {
        return try {
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun callGemini(
        provider: ProviderType,
        apiKey: String,
        model: String,
        prompt: String,
        history: List<ChatTurn>,
        systemInstruction: String,
        temperature: Float,
        maxTokens: Int,
        attachment: AttachmentData?,
        customBaseUrl: String? = null
    ): AiResponse {
        val base = customBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: "https://generativelanguage.googleapis.com/v1beta"
        val url = if (base.contains(":generateContent")) {
            if (base.contains("key=")) base else "$base?key=$apiKey"
        } else {
            "$base/models/$model:generateContent?key=$apiKey"
        }

        val rootJson = JSONObject()

        // System instruction
        val sysInstructionObj = JSONObject()
        val sysParts = JSONArray().put(JSONObject().put("text", systemInstruction))
        sysInstructionObj.put("parts", sysParts)
        rootJson.put("system_instruction", sysInstructionObj)

        // Contents array
        val contentsArray = JSONArray()

        var lastRole: String? = null
        for (turn in history) {
            if (turn.content.isBlank()) continue
            val geminiRole = if (turn.role == "assistant") "model" else "user"
            if (geminiRole == lastRole) continue

            val contentObj = JSONObject()
            contentObj.put("role", geminiRole)
            val parts = JSONArray().put(JSONObject().put("text", turn.content))
            contentObj.put("parts", parts)
            contentsArray.put(contentObj)
            lastRole = geminiRole
        }

        val userContent = JSONObject()
        userContent.put("role", "user")

        val partsArray = JSONArray()
        if (attachment != null && (attachment.mimeType.startsWith("image/") || attachment.mimeType.startsWith("audio/") || attachment.mimeType.startsWith("video/") || attachment.mimeType == "application/pdf")) {
            val inlineData = JSONObject()
            inlineData.put("mimeType", attachment.mimeType)
            inlineData.put("data", Base64.encodeToString(attachment.bytes, Base64.NO_WRAP))
            partsArray.put(JSONObject().put("inlineData", inlineData))
        } else if (attachment != null) {
            try {
                val textContent = String(attachment.bytes, Charsets.UTF_8)
                partsArray.put(JSONObject().put("text", "Attached file [${attachment.name}]:\n$textContent\n\n"))
            } catch (_: Exception) {}
        }

        partsArray.put(JSONObject().put("text", prompt))
        userContent.put("parts", partsArray)
        contentsArray.put(userContent)

        rootJson.put("contents", contentsArray)

        val genConfig = JSONObject()
        genConfig.put("temperature", temperature.toDouble())
        genConfig.put("maxOutputTokens", maxTokens)
        rootJson.put("generationConfig", genConfig)

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(rootJson.toString().toRequestBody(jsonMediaType))
            .build()

        return executeAndParse(request, provider, model) { responseBody, _ ->
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                val promptFeedback = json.optJSONObject("promptFeedback")
                throw AiCallException.GeneralError("Gemini חסם את התשובה: ${promptFeedback?.toString() ?: "רשימת תשובות ריקה"}")
            }
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val sb = StringBuilder()
            var inlineMediaBytes: ByteArray? = null
            var inlineMediaMime: String? = null

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                    sb.append(part.getString("text"))
                }
                if (part.has("inlineData")) {
                    val inlineObj = part.getJSONObject("inlineData")
                    val b64 = inlineObj.optString("data", "")
                    val mime = inlineObj.optString("mimeType", "image/png")
                    if (b64.isNotBlank()) {
                        inlineMediaBytes = Base64.decode(b64, Base64.DEFAULT)
                        inlineMediaMime = mime
                    }
                }
            }
            val usage = json.optJSONObject("usageMetadata")
            val tokens = usage?.optInt("totalTokenCount", 0) ?: 0

            val finalText = sb.toString().trim().ifBlank {
                if (inlineMediaBytes != null) "🎨 קובץ מדיה נוצר בהצלחה על ידי $model" else ""
            }

            AiResponse(
                text = finalText,
                provider = provider,
                model = model,
                tokensUsed = tokens,
                generatedMediaBytes = inlineMediaBytes,
                generatedMediaMimeType = inlineMediaMime,
                generatedMediaName = if (inlineMediaMime?.startsWith("audio") == true) "gemini_audio.wav" else "gemini_image.png"
            )
        }
    }

    private fun callClaude(
        provider: ProviderType,
        apiKey: String,
        model: String,
        prompt: String,
        history: List<ChatTurn>,
        systemInstruction: String,
        temperature: Float,
        maxTokens: Int,
        attachment: AttachmentData?,
        customBaseUrl: String? = null
    ): AiResponse {
        val url = customBaseUrl?.trim()?.takeIf { it.isNotBlank() } ?: "https://api.anthropic.com/v1/messages"

        val rootJson = JSONObject()
        rootJson.put("model", model)
        rootJson.put("max_tokens", maxTokens)
        rootJson.put("temperature", temperature.coerceIn(0f, 1f).toDouble())
        rootJson.put("system", systemInstruction)

        val messagesArray = JSONArray()

        var lastRole: String? = null
        for (turn in history) {
            if (turn.content.isBlank()) continue
            val claudeRole = if (turn.role == "assistant") "assistant" else "user"
            if (claudeRole == lastRole) continue

            val turnMsg = JSONObject()
            turnMsg.put("role", claudeRole)
            val contentArr = JSONArray().put(JSONObject().put("type", "text").put("text", turn.content))
            turnMsg.put("content", contentArr)
            messagesArray.put(turnMsg)
            lastRole = claudeRole
        }

        val userMsg = JSONObject()
        userMsg.put("role", "user")

        val contentArray = JSONArray()
        if (attachment != null && attachment.mimeType.startsWith("image/")) {
            val imageBlock = JSONObject()
            imageBlock.put("type", "image")
            val sourceObj = JSONObject()
            sourceObj.put("type", "base64")
            sourceObj.put("media_type", attachment.mimeType)
            sourceObj.put("data", Base64.encodeToString(attachment.bytes, Base64.NO_WRAP))
            imageBlock.put("source", sourceObj)
            contentArray.put(imageBlock)
        } else if (attachment != null) {
            try {
                val textContent = String(attachment.bytes, Charsets.UTF_8)
                val textBlock = JSONObject()
                textBlock.put("type", "text")
                textBlock.put("text", "Attached file [${attachment.name}]:\n$textContent\n\n")
                contentArray.put(textBlock)
            } catch (_: Exception) {}
        }

        val textBlock = JSONObject()
        textBlock.put("type", "text")
        textBlock.put("text", prompt)
        contentArray.put(textBlock)

        userMsg.put("content", contentArray)
        messagesArray.put(userMsg)
        rootJson.put("messages", messagesArray)

        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .addHeader("Accept", "application/json")
            .post(rootJson.toString().toRequestBody(jsonMediaType))
            .build()

        return executeAndParse(request, provider, model) { responseBody, _ ->
            val json = JSONObject(responseBody)
            val content = json.getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val item = content.getJSONObject(i)
                if (item.optString("type") == "text") {
                    sb.append(item.getString("text"))
                }
            }
            val usage = json.optJSONObject("usage")
            val inputTokens = usage?.optInt("input_tokens", 0) ?: 0
            val outputTokens = usage?.optInt("output_tokens", 0) ?: 0

            AiResponse(sb.toString().trim(), provider, model, inputTokens + outputTokens)
        }
    }

    private fun callOpenAiCompatible(
        provider: ProviderType,
        apiKey: String,
        model: String,
        prompt: String,
        history: List<ChatTurn>,
        systemInstruction: String,
        temperature: Float,
        maxTokens: Int,
        attachment: AttachmentData?,
        customBaseUrl: String? = null
    ): AiResponse {
        val endpoint = if (!customBaseUrl.isNullOrBlank()) {
            val trimmed = customBaseUrl.trim().trimEnd('/')
            if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
        } else {
            when (provider) {
                ProviderType.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
                ProviderType.GROK -> "https://api.x.ai/v1/chat/completions"
                ProviderType.OPENAI -> "https://api.openai.com/v1/chat/completions"
                ProviderType.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
                ProviderType.DEEPSEEK -> "https://api.deepseek.com/chat/completions"
                ProviderType.MISTRAL -> "https://api.mistral.ai/v1/chat/completions"
                ProviderType.HUGGINGFACE -> "https://router.huggingface.co/hf-inference/v1/chat/completions"
                ProviderType.CUSTOM -> "https://api.openai.com/v1/chat/completions"
                else -> "https://api.openai.com/v1/chat/completions"
            }
        }

        val rootJson = JSONObject()
        rootJson.put("model", model)

        val isOpenAiReasoningModel = provider == ProviderType.OPENAI &&
                (model.startsWith("o1", ignoreCase = true) || model.startsWith("o3", ignoreCase = true))

        val effectiveMaxTokens = if (provider == ProviderType.OPENROUTER) {
            maxOf(maxTokens, 8192)
        } else {
            maxOf(maxTokens, 4096)
        }

        if (isOpenAiReasoningModel) {
            rootJson.put("max_completion_tokens", effectiveMaxTokens)
        } else {
            rootJson.put("temperature", temperature.toDouble())
            rootJson.put("max_tokens", effectiveMaxTokens)
        }

        if (provider == ProviderType.OPENROUTER) {
            val reasoningObj = JSONObject().apply {
                put("exclude", true)
            }
            rootJson.put("reasoning", reasoningObj)
        }

        val messagesArray = JSONArray()
        val sysMsg = JSONObject()
        val sysRole = if (isOpenAiReasoningModel) "developer" else "system"
        sysMsg.put("role", sysRole)
        sysMsg.put("content", systemInstruction)
        messagesArray.put(sysMsg)

        for (turn in history) {
            if (turn.content.isBlank()) continue
            val role = if (turn.role == "assistant") "assistant" else "user"
            val msgObj = JSONObject()
            msgObj.put("role", role)
            msgObj.put("content", turn.content)
            messagesArray.put(msgObj)
        }

        val userMsg = JSONObject()
        userMsg.put("role", "user")

        if (attachment != null && attachment.mimeType.startsWith("image/")) {
            val contentArray = JSONArray()

            val textPart = JSONObject()
            textPart.put("type", "text")
            textPart.put("text", prompt)
            contentArray.put(textPart)

            val imgPart = JSONObject()
            imgPart.put("type", "image_url")
            val imgUrlObj = JSONObject()
            val base64Data = Base64.encodeToString(attachment.bytes, Base64.NO_WRAP)
            imgUrlObj.put("url", "data:${attachment.mimeType};base64,$base64Data")
            imgPart.put("image_url", imgUrlObj)
            contentArray.put(imgPart)

            userMsg.put("content", contentArray)
        } else if (attachment != null) {
            val textContent = try {
                String(attachment.bytes, Charsets.UTF_8)
            } catch (_: Exception) { "" }
            val fullPrompt = "Attached file [${attachment.name}]:\n$textContent\n\n$prompt"
            userMsg.put("content", fullPrompt)
        } else {
            userMsg.put("content", prompt)
        }

        messagesArray.put(userMsg)
        rootJson.put("messages", messagesArray)

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")

        if (provider == ProviderType.OPENROUTER) {
            reqBuilder.addHeader("HTTP-Referer", "https://notifai.app")
            reqBuilder.addHeader("X-Title", "NotifAI")
        }

        val request = reqBuilder
            .post(rootJson.toString().toRequestBody(jsonMediaType))
            .build()

        return executeAndParse(request, provider, model) { responseBody, _ ->
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            if (choices.length() == 0) {
                throw AiCallException.GeneralError("התקבלה תשובה ריקה מ-${provider.displayName}")
            }
            val firstChoice = choices.getJSONObject(0)
            val msgObj = firstChoice.getJSONObject("message")

            val rawContent = when (val contentField = msgObj.opt("content")) {
                is JSONArray -> {
                    val sb = StringBuilder()
                    for (i in 0 until contentField.length()) {
                        val elem = contentField.opt(i)
                        if (elem is JSONObject) {
                            val partText = elem.optString("text", "")
                            if (partText.isNotEmpty()) sb.append(partText)
                        } else if (elem is String) {
                            sb.append(elem)
                        }
                    }
                    sb.toString()
                }
                is String -> contentField
                else -> msgObj.optString("content", "")
            }

            val cleanedContent = if (rawContent.contains("<think>") && rawContent.contains("</think>")) {
                val stripped = rawContent.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
                stripped.ifBlank { rawContent.trim() }
            } else {
                rawContent.trim()
            }

            val finalContent = cleanedContent.ifBlank {
                msgObj.optString("reasoning", "").ifBlank {
                    msgObj.optString("reasoning_content", "")
                }.trim()
            }

            val usage = json.optJSONObject("usage")
            val totalTokens = usage?.optInt("total_tokens", 0) ?: 0

            AiResponse(finalContent, provider, model, totalTokens)
        }
    }

    private inline fun executeAndParse(
        request: Request,
        provider: ProviderType,
        model: String,
        parser: (String, okhttp3.Headers) -> AiResponse
    ): AiResponse {
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw AiCallException.ServerUnavailable(
                "🌐 בעיית חיבור לרשת מול ${provider.displayName} [מודל '$model']: ${e.localizedMessage ?: "בדוק חיבור אינטרנט"}",
                25L
            )
        }

        val responseBody = response.body?.string().orEmpty()
        val code = response.code

        if (response.isSuccessful) {
            return try {
                parser(responseBody, response.headers)
            } catch (e: AiCallException) {
                throw e
            } catch (e: Exception) {
                throw AiCallException.GeneralError("שגיאה בפענוח תשובה מ-${provider.displayName}: ${e.message}")
            }
        }

        // Smart Hebrew Error Analysis (Task #9)
        val analyzed = ApiErrorAnalyzer.analyzeHttpError(
            code = code,
            headers = response.headers,
            responseBody = responseBody,
            provider = provider,
            model = model
        )
        throw AiCallException.SmartApiException(analyzed)
    }
}
