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
    val encryptedKey: String, // Stored encrypted via CryptoEngine (never displayed in plaintext)
    val selectedModel: String,
    val priority: Int = 1,    // Lower number = higher priority (1, 2, 3...)
    val isEnabled: Boolean = true,
    val suspendedUntil: Long = 0L, // Timestamp in ms when whole-key suspension expires
    val suspensionReason: String? = null,
    // Playground / Generation Parameters
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val systemPrompt: String? = null,
    val customBaseUrl: String? = null,
    // New fields for v3:
    // Comma-separated AiTaskType names (TEXT, IMAGE_INPUT, IMAGE_GEN, AUDIO_GEN, VIDEO_GEN, FILE_INPUT)
    val supportedTasks: String = AiTaskType.DEFAULT_TASKS_CSV,
    // Comma-separated fallback models enabled within this key in priority order (default empty = off)
    val fallbackModels: String = "",
    // Protocol for ProviderType.CUSTOM ("OPENAI", "GEMINI", "CLAUDE")
    val customProtocol: String = "OPENAI",
    // Per-model suspension tracking ("modelId=untilEpochMs;modelId2=untilEpochMs") to distinguish single-model vs full-key blocks
    val modelSuspensionsCsv: String = "",
    // Usage statistics
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val lastUsedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isSuspended: Boolean
        get() = suspendedUntil > System.currentTimeMillis()

    val remainingSuspensionSeconds: Long
        get() = ((suspendedUntil - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)

    val parsedSupportedTasks: Set<AiTaskType>
        get() = AiTaskType.parseTasksCsv(supportedTasks)

    val parsedModelSuspensions: Map<String, Long>
        get() {
            if (modelSuspensionsCsv.isBlank()) return emptyMap()
            val map = mutableMapOf<String, Long>()
            modelSuspensionsCsv.split(";").forEach { entry ->
                val parts = entry.split("=")
                if (parts.size >= 2) {
                    val m = parts[0].trim()
                    val u = parts[1].trim().toLongOrNull() ?: 0L
                    if (m.isNotBlank() && u > 0L) {
                        map[m] = u
                    }
                }
            }
            return map
        }

    /**
     * Returns the ordered list of models to try for this key:
     * [selectedModel] followed by any enabled [fallbackModels] in priority order.
     */
    val orderedCandidateModels: List<String>
        get() {
            val extras = fallbackModels
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.equals(selectedModel, ignoreCase = true) }
            return (listOf(selectedModel) + extras).distinct()
        }

    /**
     * Checks if a specific model within this key is currently blocked/suspended.
     */
    fun getModelSuspensionRemainingSeconds(modelId: String, nowMs: Long = System.currentTimeMillis()): Long {
        if (modelSuspensionsCsv.isBlank()) return 0L
        val entries = modelSuspensionsCsv.split(";")
        for (entry in entries) {
            val parts = entry.split("=")
            if (parts.size >= 2 && parts[0].trim().equals(modelId.trim(), ignoreCase = true)) {
                val until = parts[1].trim().toLongOrNull() ?: 0L
                if (until > nowMs) {
                    return ((until - nowMs) / 1000L).coerceAtLeast(1L)
                }
            }
        }
        return 0L
    }

    fun withModelSuspended(modelId: String, untilMs: Long): String {
        val nowMs = System.currentTimeMillis()
        val map = mutableMapOf<String, Long>()
        if (modelSuspensionsCsv.isNotBlank()) {
            modelSuspensionsCsv.split(";").forEach { entry ->
                val parts = entry.split("=")
                if (parts.size >= 2) {
                    val m = parts[0].trim()
                    val u = parts[1].trim().toLongOrNull() ?: 0L
                    if (m.isNotBlank() && u > nowMs) {
                        map[m] = u
                    }
                }
            }
        }
        map[modelId.trim()] = untilMs
        return map.entries.joinToString(";") { "${it.key}=${it.value}" }
    }

    companion object {
        fun formatDurationHe(seconds: Long): String {
            val lang = com.example.util.AppStrings.currentLang()
            if (seconds <= 0) {
                return when (lang) {
                    com.example.util.AppStrings.Lang.HE, com.example.util.AppStrings.Lang.AUTO -> "מיידי"
                    com.example.util.AppStrings.Lang.AR -> "فوري"
                    com.example.util.AppStrings.Lang.RU -> "мгновенно"
                    com.example.util.AppStrings.Lang.ES -> "inmediato"
                    com.example.util.AppStrings.Lang.FR -> "immédiat"
                    com.example.util.AppStrings.Lang.DE -> "sofort"
                    com.example.util.AppStrings.Lang.PT -> "imediato"
                    com.example.util.AppStrings.Lang.ZH -> "立即"
                    com.example.util.AppStrings.Lang.JA -> "即時"
                    com.example.util.AppStrings.Lang.TR -> "hemen"
                    com.example.util.AppStrings.Lang.IT -> "immediato"
                    else -> "immediately"
                }
            }
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            return when (lang) {
                com.example.util.AppStrings.Lang.HE, com.example.util.AppStrings.Lang.AUTO -> when {
                    hours > 0 && minutes > 0 -> "$hours שעות ו-$minutes דקות"
                    hours > 0 -> "$hours שעות"
                    minutes > 0 && secs > 0 -> "$minutes דקות ו-$secs שניות"
                    minutes > 0 -> "$minutes דקות"
                    else -> "$secs שניות"
                }
                com.example.util.AppStrings.Lang.AR -> when {
                    hours > 0 && minutes > 0 -> "$hours ساعات و $minutes دقائق"
                    hours > 0 -> "$hours ساعات"
                    minutes > 0 && secs > 0 -> "$minutes دقائق و $secs ثوانٍ"
                    minutes > 0 -> "$minutes دقائق"
                    else -> "$secs ثوانٍ"
                }
                com.example.util.AppStrings.Lang.RU -> when {
                    hours > 0 && minutes > 0 -> "$hours ч. $minutes мин."
                    hours > 0 -> "$hours ч."
                    minutes > 0 && secs > 0 -> "$minutes мин. $secs сек."
                    minutes > 0 -> "$minutes мин."
                    else -> "$secs сек."
                }
                com.example.util.AppStrings.Lang.ES -> when {
                    hours > 0 && minutes > 0 -> "$hours h y $minutes min"
                    hours > 0 -> "$hours horas"
                    minutes > 0 && secs > 0 -> "$minutes min y $secs seg"
                    minutes > 0 -> "$minutes minutos"
                    else -> "$secs segundos"
                }
                com.example.util.AppStrings.Lang.FR -> when {
                    hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                    hours > 0 -> "$hours heures"
                    minutes > 0 && secs > 0 -> "${minutes}m ${secs}s"
                    minutes > 0 -> "$minutes minutes"
                    else -> "$secs secondes"
                }
                com.example.util.AppStrings.Lang.DE -> when {
                    hours > 0 && minutes > 0 -> "$hours Std. $minutes Min."
                    hours > 0 -> "$hours Stunden"
                    minutes > 0 && secs > 0 -> "$minutes Min. $secs Sek."
                    minutes > 0 -> "$minutes Minuten"
                    else -> "$secs Sekunden"
                }
                com.example.util.AppStrings.Lang.PT -> when {
                    hours > 0 && minutes > 0 -> "${hours}h e ${minutes}m"
                    hours > 0 -> "$hours horas"
                    minutes > 0 && secs > 0 -> "$minutes min e $secs seg"
                    minutes > 0 -> "$minutes minutos"
                    else -> "$secs segundos"
                }
                com.example.util.AppStrings.Lang.ZH -> when {
                    hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
                    hours > 0 -> "${hours}小时"
                    minutes > 0 && secs > 0 -> "${minutes}分${secs}秒"
                    minutes > 0 -> "${minutes}分钟"
                    else -> "${secs}秒"
                }
                com.example.util.AppStrings.Lang.JA -> when {
                    hours > 0 && minutes > 0 -> "${hours}時間${minutes}分"
                    hours > 0 -> "${hours}時間"
                    minutes > 0 && secs > 0 -> "${minutes}分${secs}秒"
                    minutes > 0 -> "${minutes}分"
                    else -> "${secs}秒"
                }
                com.example.util.AppStrings.Lang.TR -> when {
                    hours > 0 && minutes > 0 -> "$hours saat $minutes dk"
                    hours > 0 -> "$hours saat"
                    minutes > 0 && secs > 0 -> "$minutes dk $secs sn"
                    minutes > 0 -> "$minutes dakika"
                    else -> "$secs saniye"
                }
                com.example.util.AppStrings.Lang.IT -> when {
                    hours > 0 && minutes > 0 -> "$hours ore e $minutes min"
                    hours > 0 -> "$hours ore"
                    minutes > 0 && secs > 0 -> "$minutes min e $secs sec"
                    minutes > 0 -> "$minutes minuti"
                    else -> "$secs secondi"
                }
                else -> when {
                    hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                    hours > 0 -> "${hours}h"
                    minutes > 0 && secs > 0 -> "${minutes}m ${secs}s"
                    minutes > 0 -> "${minutes}m"
                    else -> "${secs}s"
                }
            }
        }
    }
}
