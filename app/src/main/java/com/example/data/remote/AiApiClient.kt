package com.example.data.remote

import android.util.Base64
import com.example.data.model.AiModelInfo
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
import java.util.regex.Pattern

sealed class AiCallException(message: String) : Exception(message) {
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
    val tokensUsed: Int = 0
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
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun queryAi(
        provider: ProviderType,
        apiKey: String,
        model: String,
        prompt: String,
        history: List<ChatTurn> = emptyList(),
        systemInstruction: String = "You are NotifAI, a helpful and concise AI assistant. You answer in Hebrew or the language asked. When answering for notifications, keep key points clear and easy to read.",
        temperature: Float = 0.7f,
        maxTokens: Int = 4096,
        attachment: AttachmentData? = null
    ): AiResponse = withContext(Dispatchers.IO) {
        when (provider) {
            ProviderType.GEMINI -> callGemini(
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                history = history,
                systemInstruction = systemInstruction,
                temperature = temperature,
                maxTokens = maxTokens,
                attachment = attachment
            )
            ProviderType.CLAUDE -> callClaude(
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                history = history,
                systemInstruction = systemInstruction,
                temperature = temperature,
                maxTokens = maxTokens,
                attachment = attachment
            )
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
                attachment = attachment
            )
        }
    }

    /**
     * Queries the provider's live /models endpoint and returns a list of model IDs.
     */
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
     * Live query to each provider's API with active/free/chat validation and rich status metadata.
     */
    suspend fun fetchAvailableModelsDetailed(
        provider: ProviderType,
        apiKey: String
    ): FetchModelsResponse = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext FetchModelsResponse(
                isSuccess = false,
                models = provider.suggestedModels.map {
                    AiModelInfo(
                        id = it,
                        displayName = it,
                        isFree = provider == ProviderType.GROQ || it.contains("free", ignoreCase = true),
                        isRecommended = true
                    )
                },
                errorMessage = "הזן מפתח API כדי לשלוף את רשימת המודלים הפעילים בזמן אמת"
            )
        }

        try {
            when (provider) {
                ProviderType.GEMINI -> fetchGeminiModels(trimmedKey)
                ProviderType.GROQ -> fetchGroqModels(trimmedKey)
                ProviderType.OPENROUTER -> fetchOpenRouterModels(trimmedKey)
                ProviderType.DEEPSEEK -> fetchDeepSeekModels(trimmedKey)
                ProviderType.CLAUDE -> fetchClaudeModels(trimmedKey)
                ProviderType.OPENAI -> fetchOpenAiModels(trimmedKey)
                ProviderType.GROK -> fetchGrokModels(trimmedKey)
                ProviderType.MISTRAL -> fetchMistralModels(trimmedKey)
                ProviderType.HUGGINGFACE -> fetchHuggingFaceModels(trimmedKey)
            }
        } catch (e: Exception) {
            val fallback = provider.suggestedModels.map {
                AiModelInfo(
                    id = it,
                    displayName = it,
                    isFree = provider == ProviderType.GROQ || it.contains("free", ignoreCase = true),
                    isRecommended = true
                )
            }
            FetchModelsResponse(
                isSuccess = false,
                models = fallback,
                errorMessage = "שגיאת רשת בשליפת מודלים: ${e.localizedMessage ?: "לא ניתן להתחבר לשרת"}. מוצגים מודלים מומלצים כגיבוי."
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
                val msg = when (resp.code) {
                    400 -> "מפתח Gemini אינו תקין (Error 400)"
                    403 -> "המפתח חסום או שאין הרשאה לפרויקט ב-Gemini (Error 403)"
                    429 -> "חרגת ממכסת הבקשות של Gemini (Error 429)"
                    else -> "שגיאה ב-Gemini (${resp.code})"
                }
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.GEMINI.suggestedModels.map { AiModelInfo(it, it, isFree = true, isRecommended = true) },
                    errorMessage = msg
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
                        if (supportedMethods.optString(j) == "generateContent") {
                            supportsGenerate = true
                            break
                        }
                    }
                }
                if (!supportsGenerate) continue
                if (!id.startsWith("gemini", ignoreCase = true)) continue
                if (id.contains("vision", ignoreCase = true) || id.contains("embedding", ignoreCase = true) || id.contains("aqa", ignoreCase = true)) continue

                val dispName = obj.optString("displayName", id)
                val desc = obj.optString("description", "")
                val isRec = id in listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro")
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
            list.sortWith(compareByDescending<AiModelInfo> { it.isRecommended }.thenByDescending { it.id })
            return FetchModelsResponse(isSuccess = true, models = if (list.isNotEmpty()) list else ProviderType.GEMINI.suggestedModels.map { AiModelInfo(it, it, isFree = true, isRecommended = true) })
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
                val msg = if (resp.code == 401) "מפתח Groq אינו תקין (401 Unauthorized)" else "שגיאת שרת Groq (${resp.code})"
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.GROQ.suggestedModels.map { AiModelInfo(it, it, isFree = true, isRecommended = true) },
                    errorMessage = msg
                )
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val arr = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AiModelInfo>()
            val excluded = listOf("whisper", "tts", "embedding", "guard", "safeguard", "moderation", "distil-whisper")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", "")
                val active = obj.optBoolean("active", true)
                if (!active || id.isBlank()) continue
                if (excluded.any { id.contains(it, ignoreCase = true) }) continue

                val ctx = obj.optInt("context_window", 0).takeIf { it > 0 }
                val isRec = id in listOf("llama-3.3-70b-versatile", "deepseek-r1-distill-llama-70b", "meta-llama/llama-4-scout-17b-16e-instruct", "llama-3.1-8b-instant", "qwen-qwq-32b")
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
            list.sortWith(compareByDescending<AiModelInfo> { it.isRecommended }.thenBy { it.id })
            return FetchModelsResponse(isSuccess = true, models = if (list.isNotEmpty()) list else ProviderType.GROQ.suggestedModels.map { AiModelInfo(it, it, isFree = true, isRecommended = true) })
        }
    }

    private fun fetchOpenRouterModels(apiKey: String): FetchModelsResponse {
        val req = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.OPENROUTER.suggestedModels.map { AiModelInfo(it, it, isFree = it.contains("free")) },
                    errorMessage = "שגיאה ב-OpenRouter (${resp.code})"
                )
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val arr = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AiModelInfo>()
            val excluded = listOf("whisper", "tts", "embedding", "moderation")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", "")
                if (id.isBlank() || excluded.any { id.contains(it, ignoreCase = true) }) continue

                val name = obj.optString("name", id)
                val pricing = obj.optJSONObject("pricing")
                val pPrompt = pricing?.optString("prompt", "0") ?: "0"
                val pCompl = pricing?.optString("completion", "0") ?: "0"
                val isFree = id.endsWith(":free") || (pPrompt == "0" && pCompl == "0")
                val ctx = obj.optInt("context_length", 0).takeIf { it > 0 }
                val desc = obj.optString("description", "")

                val disp = if (isFree && !name.contains("free", ignoreCase = true)) "$name (חינמי)" else name
                val isRec = isFree || id in listOf("anthropic/claude-3.5-sonnet", "openai/gpt-4o", "deepseek/deepseek-chat")

                list.add(
                    AiModelInfo(
                        id = id,
                        displayName = disp,
                        isFree = isFree,
                        contextWindow = ctx,
                        description = desc.take(120),
                        isRecommended = isRec
                    )
                )
            }
            // Sort: Free models first, then recommended, then alphabetical
            list.sortWith(compareByDescending<AiModelInfo> { it.isFree }.thenByDescending { it.isRecommended }.thenBy { it.id })
            return FetchModelsResponse(isSuccess = true, models = if (list.isNotEmpty()) list.take(80) else ProviderType.OPENROUTER.suggestedModels.map { AiModelInfo(it, it, isFree = it.contains("free")) })
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
                val msg = if (resp.code == 401) "מפתח DeepSeek אינו תקין (401)" else "שגיאה ב-DeepSeek (${resp.code})"
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.DEEPSEEK.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) },
                    errorMessage = msg
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
            return FetchModelsResponse(isSuccess = true, models = list)
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
                val msg = if (resp.code == 401) "מפתח Anthropic Claude שגוי או לא מורשה (401)" else "שגיאה ב-Claude (${resp.code})"
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.CLAUDE.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) },
                    errorMessage = msg
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
            list.sortWith(compareByDescending<AiModelInfo> { it.isRecommended }.thenBy { it.id })
            return FetchModelsResponse(isSuccess = true, models = if (list.isNotEmpty()) list else ProviderType.CLAUDE.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) })
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
                val msg = if (resp.code == 401) "מפתח OpenAI שגוי או שאין הרשאה (401)" else "שגיאה ב-OpenAI (${resp.code})"
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.OPENAI.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) },
                    errorMessage = msg
                )
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val arr = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AiModelInfo>()
            val excluded = listOf("whisper", "tts", "embedding", "dall-e", "babbage", "davinci", "moderation", "realtime", "audio", "instruct", "canary")
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("id", "")
                if (id.isBlank()) continue
                if (excluded.any { id.contains(it, ignoreCase = true) }) continue
                val isChat = id.startsWith("gpt-") || id.startsWith("o1") || id.startsWith("o3") || id.startsWith("chatgpt-")
                if (!isChat) continue

                val isRec = id in listOf("gpt-4o-mini", "gpt-4o", "o3-mini", "o1-mini")
                list.add(AiModelInfo(id = id, displayName = id, isRecommended = isRec))
            }
            list.sortWith(compareByDescending<AiModelInfo> { it.isRecommended }.thenBy { it.id })
            return FetchModelsResponse(isSuccess = true, models = if (list.isNotEmpty()) list else ProviderType.OPENAI.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) })
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
                val msg = if (resp.code == 401) "מפתח xAI Grok שגוי (401)" else "שגיאה ב-Grok (${resp.code})"
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.GROK.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) },
                    errorMessage = msg
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
            list.sortWith(compareByDescending<AiModelInfo> { it.isRecommended }.thenBy { it.id })
            return FetchModelsResponse(isSuccess = true, models = if (list.isNotEmpty()) list else ProviderType.GROK.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) })
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
                val msg = if (resp.code == 401) "מפתח Mistral אינו תקין (401)" else "שגיאה ב-Mistral (${resp.code})"
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.MISTRAL.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) },
                    errorMessage = msg
                )
            }
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val arr = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AiModelInfo>()
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("id", "")
                if (id.isNotBlank() && !id.contains("embed", ignoreCase = true)) {
                    val isRec = id in listOf("mistral-large-latest", "codestral-latest", "mistral-small-latest")
                    list.add(AiModelInfo(id = id, displayName = id, isRecommended = isRec))
                }
            }
            list.sortWith(compareByDescending<AiModelInfo> { it.isRecommended }.thenBy { it.id })
            return FetchModelsResponse(isSuccess = true, models = if (list.isNotEmpty()) list else ProviderType.MISTRAL.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) })
        }
    }

    private fun fetchHuggingFaceModels(apiKey: String): FetchModelsResponse {
        val req = Request.Builder()
            .url("https://huggingface.co/api/models?pipeline_tag=text-generation&sort=trending&limit=30")
            .apply {
                if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey")
            }
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                return FetchModelsResponse(
                    isSuccess = false,
                    models = ProviderType.HUGGINGFACE.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) },
                    errorMessage = "שגיאה ב-Hugging Face (${resp.code})"
                )
            }
            val body = resp.body?.string().orEmpty()
            val arr = JSONArray(body)
            val list = mutableListOf<AiModelInfo>()
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("id", "")
                if (id.isNotBlank()) {
                    list.add(AiModelInfo(id = id, displayName = id, isRecommended = i < 5))
                }
            }
            return FetchModelsResponse(isSuccess = true, models = if (list.isNotEmpty()) list else ProviderType.HUGGINGFACE.suggestedModels.map { AiModelInfo(it, it, isRecommended = true) })
        }
    }

    private fun callGemini(
        apiKey: String,
        model: String,
        prompt: String,
        history: List<ChatTurn>,
        systemInstruction: String,
        temperature: Float,
        maxTokens: Int,
        attachment: AttachmentData?
    ): AiResponse {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val rootJson = JSONObject()

        // System instruction
        val sysInstructionObj = JSONObject()
        val sysParts = JSONArray().put(JSONObject().put("text", systemInstruction))
        sysInstructionObj.put("parts", sysParts)
        rootJson.put("system_instruction", sysInstructionObj)

        // Contents array
        val contentsArray = JSONArray()

        // Add history turns with alternating roles
        var lastRole: String? = null
        for (turn in history) {
            if (turn.content.isBlank()) continue
            val geminiRole = if (turn.role == "assistant") "model" else "user"
            // Avoid duplicate consecutive roles for Gemini API requirements
            if (geminiRole == lastRole) continue

            val contentObj = JSONObject()
            contentObj.put("role", geminiRole)
            val parts = JSONArray().put(JSONObject().put("text", turn.content))
            contentObj.put("parts", parts)
            contentsArray.put(contentObj)
            lastRole = geminiRole
        }

        // Current turn
        val userContent = JSONObject()
        userContent.put("role", "user")

        val partsArray = JSONArray()
        if (attachment != null && attachment.mimeType.startsWith("image/")) {
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

        // Generation config (Playground parameters)
        val genConfig = JSONObject()
        genConfig.put("temperature", temperature.toDouble())
        genConfig.put("maxOutputTokens", maxTokens)
        rootJson.put("generationConfig", genConfig)

        val request = Request.Builder()
            .url(url)
            .post(rootJson.toString().toRequestBody(jsonMediaType))
            .build()

        return executeAndParse(request, ProviderType.GEMINI, model) { responseBody, _ ->
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                val promptFeedback = json.optJSONObject("promptFeedback")
                throw AiCallException.GeneralError("Gemini blocked response: ${promptFeedback?.toString() ?: "Empty candidate list"}")
            }
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                    sb.append(part.getString("text"))
                }
            }
            val usage = json.optJSONObject("usageMetadata")
            val tokens = usage?.optInt("totalTokenCount", 0) ?: 0

            AiResponse(sb.toString().trim(), ProviderType.GEMINI, model, tokens)
        }
    }

    private fun callClaude(
        apiKey: String,
        model: String,
        prompt: String,
        history: List<ChatTurn>,
        systemInstruction: String,
        temperature: Float,
        maxTokens: Int,
        attachment: AttachmentData?
    ): AiResponse {
        val url = "https://api.anthropic.com/v1/messages"

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
            .post(rootJson.toString().toRequestBody(jsonMediaType))
            .build()

        return executeAndParse(request, ProviderType.CLAUDE, model) { responseBody, _ ->
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

            AiResponse(sb.toString().trim(), ProviderType.CLAUDE, model, inputTokens + outputTokens)
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
        attachment: AttachmentData?
    ): AiResponse {
        val endpoint = when (provider) {
            ProviderType.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
            ProviderType.GROK -> "https://api.x.ai/v1/chat/completions"
            ProviderType.OPENAI -> "https://api.openai.com/v1/chat/completions"
            ProviderType.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
            ProviderType.DEEPSEEK -> "https://api.deepseek.com/chat/completions"
            ProviderType.MISTRAL -> "https://api.mistral.ai/v1/chat/completions"
            ProviderType.HUGGINGFACE -> "https://router.huggingface.co/hf-inference/v1/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }

        val rootJson = JSONObject()
        rootJson.put("model", model)
        rootJson.put("temperature", temperature.toDouble())
        rootJson.put("max_tokens", maxTokens)

        val messagesArray = JSONArray()
        // System message
        val sysMsg = JSONObject()
        sysMsg.put("role", "system")
        sysMsg.put("content", systemInstruction)
        messagesArray.put(sysMsg)

        // Historical messages
        for (turn in history) {
            if (turn.content.isBlank()) continue
            val role = if (turn.role == "assistant") "assistant" else "user"
            val msgObj = JSONObject()
            msgObj.put("role", role)
            msgObj.put("content", turn.content)
            messagesArray.put(msgObj)
        }

        // Current User message
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

        if (provider == ProviderType.OPENROUTER) {
            reqBuilder.addHeader("HTTP-Referer", "https://notifai.android")
            reqBuilder.addHeader("X-Title", "NotifAI Android")
        }

        val request = reqBuilder
            .post(rootJson.toString().toRequestBody(jsonMediaType))
            .build()

        return executeAndParse(request, provider, model) { responseBody, _ ->
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            if (choices.length() == 0) {
                throw AiCallException.GeneralError("Empty response choices from ${provider.displayName}")
            }
            val firstChoice = choices.getJSONObject(0)
            val msgObj = firstChoice.getJSONObject("message")
            val content = msgObj.optString("content", "")

            val usage = json.optJSONObject("usage")
            val totalTokens = usage?.optInt("total_tokens", 0) ?: 0

            AiResponse(content.trim(), provider, model, totalTokens)
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
            throw AiCallException.ServerUnavailable("בעיית תקשורת לרשת: ${e.message}", 30L)
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

        // Handle error responses & quota limits
        val retryAfterHeader = response.header("Retry-After") ?: response.header("x-ratelimit-reset-requests")
        val resetSeconds = parseRetryAfter(retryAfterHeader, responseBody)

        val isQuotaExhausted = responseBody.contains("quota", ignoreCase = true) ||
                responseBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                responseBody.contains("insufficient_quota", ignoreCase = true) ||
                responseBody.contains("credit_limit", ignoreCase = true) ||
                responseBody.contains("exceeded your current quota", ignoreCase = true)

        val isRateLimit = code == 429 ||
                responseBody.contains("rate_limit", ignoreCase = true) ||
                responseBody.contains("Too Many Requests", ignoreCase = true)

        if (isQuotaExhausted) {
            val cooldown = if (resetSeconds > 0) resetSeconds else 3600L
            val message = extractErrorMessage(responseBody) ?: "נגמרה מכסת השימוש בספק ${provider.displayName}"
            throw AiCallException.QuotaExceeded(message, cooldown)
        }

        if (isRateLimit) {
            val cooldown = if (resetSeconds > 0) resetSeconds else 60L
            val message = extractErrorMessage(responseBody) ?: "חריגה מקצב הבקשות (Rate Limit) ב-${provider.displayName}"
            throw AiCallException.RateLimit(message, cooldown)
        }

        if (code == 401 || code == 403) {
            val message = extractErrorMessage(responseBody) ?: "מפתח ה-API אינו תקין או חסר הרשאות"
            throw AiCallException.AuthenticationError("${provider.displayName}: $message")
        }

        if (code in 500..599) {
            throw AiCallException.ServerUnavailable("שרת ${provider.displayName} אינו זמין כרגע (קוד $code)", 45L)
        }

        val message = extractErrorMessage(responseBody) ?: "שגיאה $code משרת ${provider.displayName}"
        throw AiCallException.GeneralError(message, code)
    }

    private fun parseRetryAfter(header: String?, body: String): Long {
        if (!header.isNullOrBlank()) {
            try {
                val clean = header.trim().removeSuffix("s")
                return clean.toDouble().toLong()
            } catch (_: Exception) {}
        }
        val pattern = Pattern.compile("in\\s+(\\d+(?:\\.\\d+)?)\\s*(seconds?|minutes?|hours?|s|m|h)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(body)
        if (matcher.find()) {
            val value = matcher.group(1)?.toDoubleOrNull()?.toLong() ?: return 0L
            val unit = matcher.group(2)?.lowercase().orEmpty()
            return when {
                unit.startsWith("m") -> value * 60
                unit.startsWith("h") -> value * 3600
                else -> maxOf(1L, value)
            }
        }
        return 0L
    }

    private fun extractErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val json = JSONObject(body)
            if (json.has("error")) {
                val errorObj = json.get("error")
                if (errorObj is JSONObject) {
                    errorObj.optString("message", null) ?: errorObj.optString("code", null)
                } else {
                    errorObj.toString()
                }
            } else {
                json.optString("message", null)
            }
        } catch (_: Exception) {
            null
        }
    }
}
