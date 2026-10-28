package com.example.data.remote

import com.example.data.model.ApiKeyEntity
import com.example.data.model.ProviderType
import okhttp3.Headers
import org.json.JSONObject
import java.util.regex.Pattern

enum class ApiErrorCategory(val badgeHe: String) {
    SERVER_OVERLOAD("עומס בשרת"),
    SERVER_ERROR("תקלת שרת"),
    MODEL_UNAVAILABLE("מודל לא זמין / לא קיים"),
    ACCOUNT_TIER_MISMATCH("לא מתאים לסוג החשבון"),
    MODEL_QUOTA_EXHAUSTED("נגמרו הטוקנים למודל הספציפי"),
    KEY_QUOTA_EXHAUSTED("נגמרו הטוקנים לכל המפתח"),
    INVALID_API_KEY("מפתח API שגוי"),
    NETWORK_ERROR("בעיית חיבור רשת"),
    GENERAL_ERROR("שגיאת API")
}

enum class BlockScope(val labelHe: String) {
    MODEL_ONLY("מודל ספציפי בלבד (שאר המודלים במפתח פעילים)"),
    ENTIRE_KEY("כל המפתח / ה-API"),
    NONE("ללא חסימה")
}

data class AnalyzedApiError(
    val category: ApiErrorCategory,
    val scope: BlockScope,
    val blockDurationSeconds: Long,
    val hebrewSummary: String,
    val rawDetail: String
)

object ApiErrorAnalyzer {

    fun analyzeHttpError(
        code: Int,
        headers: Headers?,
        responseBody: String,
        provider: ProviderType,
        model: String
    ): AnalyzedApiError {
        val rawMsg = extractErrorMessage(responseBody).orEmpty()
        val combined = "$rawMsg $responseBody".lowercase()
        val retryAfterSec = parseRetryAfterSeconds(headers, responseBody)

        // 1. Check Invalid Key (401 or explicit invalid key)
        if (code == 401 || combined.contains("invalid_api_key") || combined.contains("api_key_invalid") ||
            combined.contains("incorrect api key") || combined.contains("unauthorized")
        ) {
            val summary = "🔑 מפתח לא תקין [כל ה-API במפתח]: המפתח עבור ${provider.displayName} שגוי או בוטל."
            return AnalyzedApiError(
                category = ApiErrorCategory.INVALID_API_KEY,
                scope = BlockScope.ENTIRE_KEY,
                blockDurationSeconds = 3600L,
                hebrewSummary = summary,
                rawDetail = rawMsg
            )
        }

        // 2. Check Model Not Found / Decommissioned / Unavailable (404 or explicit message)
        if (code == 404 || combined.contains("model_not_found") || combined.contains("does not exist") ||
            combined.contains("decommissioned") || combined.contains("not found for api version") ||
            combined.contains("no endpoints found") || combined.contains("model is not supported")
        ) {
            val summary = "🚫 מודל לא קיים או לא זמין [מודל '$model' בלבד]: המודל אינו קיים ב-${provider.displayName} או הוצא משימוש. שאר המודלים במפתח זמינים."
            return AnalyzedApiError(
                category = ApiErrorCategory.MODEL_UNAVAILABLE,
                scope = BlockScope.MODEL_ONLY,
                blockDurationSeconds = 86400L, // Block just this broken model ID for 24h
                hebrewSummary = summary,
                rawDetail = rawMsg
            )
        }

        // 3. Check Account Tier / Billing / Permission Mismatch (402, 403, or tier/credits errors)
        val isBillingEmpty = code == 402 || combined.contains("insufficient_balance") ||
                combined.contains("credit balance is too low") || combined.contains("insufficient credits") ||
                combined.contains("billing_hard_limit")
        if (isBillingEmpty) {
            val duration = if (retryAfterSec > 0) retryAfterSec else 7200L
            val timeStr = ApiKeyEntity.formatDurationHe(duration)
            val summary = "💳 נגמרה יתרת הטוקנים בחשבון [כל ה-API במפתח]: נגמרו הקרדיטים/הטוקנים לכל המפתח ב-${provider.displayName} (חסום ל-$timeStr)."
            return AnalyzedApiError(
                category = ApiErrorCategory.KEY_QUOTA_EXHAUSTED,
                scope = BlockScope.ENTIRE_KEY,
                blockDurationSeconds = duration,
                hebrewSummary = summary,
                rawDetail = rawMsg
            )
        }

        val isAccountTierIssue = code == 403 || combined.contains("free tier") ||
                combined.contains("not available on your tier") || combined.contains("requires a paid") ||
                combined.contains("access to model") || combined.contains("permission_denied") ||
                combined.contains("must be verified")
        if (isAccountTierIssue) {
            val summary = "🔒 מודל לא מתאים לסוג החשבון [מודל '$model' בלבד]: המודל דורש חשבון משודרג או הרשאה מיוחדת ב-${provider.displayName}. שאר המודלים במפתח פעילים."
            return AnalyzedApiError(
                category = ApiErrorCategory.ACCOUNT_TIER_MISMATCH,
                scope = BlockScope.MODEL_ONLY,
                blockDurationSeconds = 3600L,
                hebrewSummary = summary,
                rawDetail = rawMsg
            )
        }

        // 4. Server Overload (529, 503, overloaded) vs Server Error (500, 502, 504)
        if (code == 529 || code == 503 || combined.contains("overloaded") || combined.contains("capacity")) {
            val duration = if (retryAfterSec > 0) retryAfterSec else 45L
            val timeStr = ApiKeyEntity.formatDurationHe(duration)
            // Often overload is on the specific model (especially on OpenRouter / Gemini / Claude)
            val isModelSpecificOverload = combined.contains("model") || provider == ProviderType.OPENROUTER || provider == ProviderType.GEMINI
            val scope = if (isModelSpecificOverload) BlockScope.MODEL_ONLY else BlockScope.ENTIRE_KEY
            val scopeTag = if (scope == BlockScope.MODEL_ONLY) "[מודל '$model' בלבד]" else "[כל ה-API במפתח]"
            val summary = "⏳ עומס בשרת $scopeTag: שרת ${provider.displayName} עמוס כעת. חסום זמנית ל-$timeStr."
            return AnalyzedApiError(
                category = ApiErrorCategory.SERVER_OVERLOAD,
                scope = scope,
                blockDurationSeconds = duration,
                hebrewSummary = summary,
                rawDetail = rawMsg
            )
        }

        if (code in 500..599) {
            val duration = if (retryAfterSec > 0) retryAfterSec else 45L
            val timeStr = ApiKeyEntity.formatDurationHe(duration)
            val summary = "🛠️ תקלת שרת (${code}) [כל ה-API במפתח]: שרת ${provider.displayName} החזיר שגיאה פנימית. מושהה ל-$timeStr."
            return AnalyzedApiError(
                category = ApiErrorCategory.SERVER_ERROR,
                scope = BlockScope.ENTIRE_KEY,
                blockDurationSeconds = duration,
                hebrewSummary = summary,
                rawDetail = rawMsg
            )
        }

        // 5. Rate Limit / Quota Exhausted (429 or RESOURCE_EXHAUSTED)
        // Distinguish between SPECIFIC MODEL quota/rate-limit vs ENTIRE API KEY quota!
        if (code == 429 || combined.contains("rate_limit") || combined.contains("resource_exhausted") ||
            combined.contains("quota") || combined.contains("too many requests")
        ) {
            val mentionsModelSpecifically = combined.contains(model.lowercase()) ||
                    combined.contains("per-model") ||
                    combined.contains("for model") ||
                    combined.contains("limit for this model") ||
                    combined.contains("requests per minute (rpm) for") ||
                    combined.contains("tokens per minute (tpm) for") ||
                    provider == ProviderType.GROQ ||
                    provider == ProviderType.OPENROUTER ||
                    provider == ProviderType.GEMINI

            val mentionsAccountWideQuota = combined.contains("exceeded your current quota, please check your plan and billing") ||
                    combined.contains("account_quota") ||
                    combined.contains("monthly limit") ||
                    combined.contains("organization quota") ||
                    combined.contains("daily key limit")

            if (mentionsAccountWideQuota && !mentionsModelSpecifically) {
                val duration = if (retryAfterSec > 0) retryAfterSec else 1800L
                val timeStr = ApiKeyEntity.formatDurationHe(duration)
                val summary = "🛑 נגמרו הטוקנים למפתח [כל ה-API במפתח]: מכסת המפתח ב-${provider.displayName} נוצלה במלואה. חסום ל-$timeStr."
                return AnalyzedApiError(
                    category = ApiErrorCategory.KEY_QUOTA_EXHAUSTED,
                    scope = BlockScope.ENTIRE_KEY,
                    blockDurationSeconds = duration,
                    hebrewSummary = summary,
                    rawDetail = rawMsg
                )
            } else {
                val duration = if (retryAfterSec > 0) retryAfterSec else 60L
                val timeStr = ApiKeyEntity.formatDurationHe(duration)
                val summary = "⚡ נגמרו הטוקנים למודל הספציפי [מודל '$model' בלבד]: המכסה הזמנית של המודל '$model' ב-${provider.displayName} נוצלה (חסום ל-$timeStr). שאר המודלים במפתח זמינים!"
                return AnalyzedApiError(
                    category = ApiErrorCategory.MODEL_QUOTA_EXHAUSTED,
                    scope = BlockScope.MODEL_ONLY,
                    blockDurationSeconds = duration,
                    hebrewSummary = summary,
                    rawDetail = rawMsg
                )
            }
        }

        // 6. Fallback General Error
        val shortDetail = rawMsg.take(120).ifBlank { "קוד שגיאה $code" }
        val summary = "⚠️ שגיאת בקשה ב-${provider.displayName} [מודל '$model']: $shortDetail"
        return AnalyzedApiError(
            category = ApiErrorCategory.GENERAL_ERROR,
            scope = BlockScope.MODEL_ONLY,
            blockDurationSeconds = 15L,
            hebrewSummary = summary,
            rawDetail = rawMsg
        )
    }

    fun parseRetryAfterSeconds(headers: Headers?, body: String): Long {
        if (headers != null) {
            val retryHeader = headers["Retry-After"]
                ?: headers["x-ratelimit-reset-requests"]
                ?: headers["x-ratelimit-reset-tokens"]
            if (!retryHeader.isNullOrBlank()) {
                val parsed = parseDurationString(retryHeader)
                if (parsed > 0) return parsed
            }
        }

        // Check body for patterns like "Please try again in 2m30s", "retry in 42.5s", "retryDelay": "35s"
        val minSecPattern = Pattern.compile("(\\d+)m\\s*(\\d+(?:\\.\\d+)?)s", Pattern.CASE_INSENSITIVE)
        val minSecMatcher = minSecPattern.matcher(body)
        if (minSecMatcher.find()) {
            val mins = minSecMatcher.group(1)?.toLongOrNull() ?: 0L
            val secs = minSecMatcher.group(2)?.toDoubleOrNull()?.toLong() ?: 0L
            return mins * 60 + secs
        }

        val retryDelayJsonPattern = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+(?:\\.\\d+)?)s\"", Pattern.CASE_INSENSITIVE)
        val retryDelayMatcher = retryDelayJsonPattern.matcher(body)
        if (retryDelayMatcher.find()) {
            val secs = retryDelayMatcher.group(1)?.toDoubleOrNull()?.toLong() ?: 0L
            if (secs > 0) return secs
        }

        val pattern = Pattern.compile("(?:in|after)\\s+(\\d+(?:\\.\\d+)?)\\s*(milliseconds?|ms|seconds?|secs?|minutes?|mins?|hours?|hrs?|s|m|h)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(body)
        if (matcher.find()) {
            val value = matcher.group(1)?.toDoubleOrNull() ?: return 0L
            val unit = matcher.group(2)?.lowercase().orEmpty()
            return when {
                unit.startsWith("ms") || unit.startsWith("millisecond") -> maxOf(1L, (value / 1000.0).toLong())
                unit.startsWith("m") -> (value * 60).toLong()
                unit.startsWith("h") -> (value * 3600).toLong()
                else -> maxOf(1L, value.toLong())
            }
        }
        return 0L
    }

    private fun parseDurationString(raw: String): Long {
        val clean = raw.trim().lowercase()
        clean.toDoubleOrNull()?.let { return maxOf(1L, it.toLong()) }
        val minSec = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+(?:\\.\\d+)?)s)?").matcher(clean)
        if (minSec.matches()) {
            val h = minSec.group(1)?.toLongOrNull() ?: 0L
            val m = minSec.group(2)?.toLongOrNull() ?: 0L
            val s = minSec.group(3)?.toDoubleOrNull()?.toLong() ?: 0L
            val total = h * 3600 + m * 60 + s
            if (total > 0) return total
        }
        return 0L
    }

    fun extractErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val json = JSONObject(body)
            if (json.has("error")) {
                val errorObj = json.get("error")
                if (errorObj is JSONObject) {
                    val mainMsg = errorObj.optString("message", "")
                    val metadata = errorObj.optJSONObject("metadata")
                    val rawMeta = metadata?.optString("raw", "").orEmpty()
                    if (rawMeta.isNotBlank() && !mainMsg.contains(rawMeta)) {
                        "$mainMsg ($rawMeta)"
                    } else {
                        mainMsg.ifBlank { errorObj.optString("code", "") }.ifBlank { null }
                    }
                } else {
                    errorObj.toString()
                }
            } else {
                json.optString("message", "").ifBlank { null }
            }
        } catch (_: Exception) {
            body.take(200)
        }
    }
}
