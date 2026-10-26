package com.example.data.remote

import android.util.Base64
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
     * Queries the provider's live /models endpoint to list all Playground models available for the API key.
     */
    suspend fun fetchAvailableModels(
        provider: ProviderType,
        apiKey: String
    ): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext provider.suggestedModels

        try {
            when (provider) {
                ProviderType.GEMINI -> {
                    val req = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey.trim()}")
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext provider.suggestedModels
                        val body = resp.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val arr = json.optJSONArray("models") ?: return@withContext provider.suggestedModels
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val name = obj.optString("name", "").removePrefix("models/")
                            if (name.startsWith("gemini")) {
                                list.add(name)
                            }
                        }
                        if (list.isNotEmpty()) list else provider.suggestedModels
                    }
                }
                ProviderType.CLAUDE -> {
                    val req = Request.Builder()
                        .url("https://api.anthropic.com/v1/models")
                        .addHeader("x-api-key", apiKey.trim())
                        .addHeader("anthropic-version", "2023-06-01")
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext provider.suggestedModels
                        val body = resp.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val arr = json.optJSONArray("data") ?: return@withContext provider.suggestedModels
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val id = arr.getJSONObject(i).optString("id", "")
                            if (id.isNotBlank()) list.add(id)
                        }
                        if (list.isNotEmpty()) list else provider.suggestedModels
                    }
                }
                ProviderType.GROQ,
                ProviderType.GROK,
                ProviderType.OPENAI,
                ProviderType.OPENROUTER -> {
                    val modelsUrl = "${provider.defaultBaseUrl}/models"
                    val req = Request.Builder()
                        .url(modelsUrl)
                        .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext provider.suggestedModels
                        val body = resp.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val arr = json.optJSONArray("data") ?: return@withContext provider.suggestedModels
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val id = arr.getJSONObject(i).optString("id", "")
                            if (id.isNotBlank() && !id.contains("whisper", ignoreCase = true) && !id.contains("tts", ignoreCase = true) && !id.contains("embedding", ignoreCase = true)) {
                                list.add(id)
                            }
                        }
                        if (list.isNotEmpty()) list.take(40) else provider.suggestedModels
                    }
                }
                ProviderType.HUGGINGFACE -> provider.suggestedModels
            }
        } catch (_: Exception) {
            provider.suggestedModels
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
