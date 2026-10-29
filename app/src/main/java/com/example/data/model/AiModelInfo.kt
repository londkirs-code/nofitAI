package com.example.data.model

data class AiModelInfo(
    val id: String,
    val displayName: String = id,
    val isFree: Boolean = false,
    val contextWindow: Int? = null,
    val description: String? = null,
    val isRecommended: Boolean = false,
    val explicitCapabilities: Set<AiTaskType>? = null
) {
    /**
     * Automatically infers the capabilities/modalities of the model (Task #6)
     * unless explicitly overridden by the user.
     */
    val capabilities: Set<AiTaskType>
        get() {
            if (!explicitCapabilities.isNullOrEmpty()) return explicitCapabilities
            val lower = id.lowercase()
            val set = mutableSetOf<AiTaskType>()
            when {
                lower.contains("dall-e") || lower.contains("flux") || lower.contains("stable-diffusion") ||
                        lower.contains("imagen") || lower.contains("sdxl") || lower.contains("midjourney") ||
                        lower.contains("image-generation") || lower == "turbo" || lower == "any-dark" ||
                        lower.contains("pollinations") -> {
                    set.add(AiTaskType.IMAGE_GEN)
                    if (lower.contains("gemini")) {
                        set.add(AiTaskType.TEXT)
                        set.add(AiTaskType.IMAGE_INPUT)
                    }
                }
                lower.contains("tts") || lower.contains("audio") || lower.contains("speech") ||
                        lower.contains("eleven") || lower.contains("music") || lower.contains("suno") -> {
                    set.add(AiTaskType.AUDIO_GEN)
                    if (lower.contains("gpt-4o")) {
                        set.add(AiTaskType.TEXT)
                    }
                }
                lower.contains("sora") || lower.contains("veo") || lower.contains("runway") ||
                        lower.contains("kling") || lower.contains("luma") || lower.contains("video") -> {
                    set.add(AiTaskType.VIDEO_GEN)
                }
                else -> {
                    set.add(AiTaskType.TEXT)
                    set.add(AiTaskType.FILE_INPUT)
                    if (lower.contains("vision") || lower.contains("4o") || lower.contains("gemini") ||
                        lower.contains("claude-3") || lower.contains("pixtral") || lower.contains("scout") ||
                        lower.contains("maverick") || lower.contains("grok-2-vision") || lower.contains("vl")
                    ) {
                        set.add(AiTaskType.IMAGE_INPUT)
                    }
                }
            }
            return set
        }

    /**
     * Automatic quality score so the best models for each provider are sorted to the top (Task #5).
     */
    val qualityScore: Int
        get() {
            val lower = id.lowercase()
            var score = 50
            if (isRecommended) score += 100

            // Top-tier flagship & high-value models
            when {
                lower == "flux" || lower == "flux-realism" || lower == "flux-pro" -> score += 98
                lower.startsWith("flux-") || lower == "turbo" || lower == "any-dark" -> score += 92
                lower == "gemini-2.5-pro" || lower == "gemini-2.5-flash" -> score += 95
                lower == "llama-3.3-70b-versatile" -> score += 95
                lower.contains("claude-3-7-sonnet") || lower.contains("claude-3.7-sonnet") -> score += 95
                lower == "gpt-4o" || lower == "openai/gpt-4o" -> score += 94
                lower == "gpt-4o-mini" || lower == "openai/gpt-4o-mini" -> score += 92
                lower.contains("deepseek-chat-v3") || lower == "deepseek-chat" -> score += 93
                lower.contains("deepseek-r1") || lower == "deepseek-reasoner" -> score += 91
                lower.contains("grok-3") -> score += 93
                lower.contains("mistral-large") -> score += 90
                lower.contains("llama-4") -> score += 92
                lower.contains("qwq-32b") || lower.contains("qwen2.5-72b") -> score += 88
                lower.contains("gemini-2.0-flash") -> score += 89
                lower.contains("claude-3-5-sonnet") || lower.contains("claude-3.5-sonnet") -> score += 88
                lower.contains("o3-mini") || lower.contains("o1") -> score += 87
                lower.contains("70b") -> score += 40
                lower.contains("32b") -> score += 30
                lower.contains("8b") || lower.contains("flash") || lower.contains("mini") -> score += 25
            }

            if (isFree) score += 15
            if (contextWindow != null && contextWindow >= 128000) score += 10
            return score
        }

    /**
     * Infers the primary role/purpose of the model in Hebrew for clear UI categorization (Task #6).
     */
    val roleBadge: String
        get() {
            val lower = id.lowercase()
            return when {
                lower.contains("dall-e") || lower.contains("flux") || lower.contains("imagen") ||
                        lower.contains("image-generation") || lower.contains("sdxl") ||
                        lower == "turbo" || lower == "any-dark" -> "🎨 יצירת תמונות"
                lower.contains("tts") || lower.contains("speech") || lower.contains("audio") -> "🔊 יצירת סאונד/שמע"
                lower.contains("sora") || lower.contains("veo") || lower.contains("video") -> "🎬 יצירת וידאו"
                lower.contains("r1") || lower.contains("reason") || lower.contains("qwq") ||
                        lower.startsWith("o1") || lower.startsWith("o3") || lower.contains("think") -> "🧠 חשיבה ולוגיקה"
                lower.contains("code") || lower.contains("codestral") || lower.contains("qwen2.5-coder") -> "💻 קוד ופיתוח"
                lower.contains("vision") || lower.contains("pixtral") || lower.contains("vl") ||
                        lower.contains("4o") || lower.contains("gemini") || lower.contains("claude-3") ||
                        lower.contains("scout") -> "👁️ טקסט + ראייה (Vision)"
                lower.contains("instant") || lower.contains("mini") || lower.contains("haiku") ||
                        lower.contains("8b") || lower.contains("9b") -> "⚡ טקסט מהיר וחסכוני"
                lower.contains("70b") || lower.contains("large") || lower.contains("pro") ||
                        lower.contains("sonnet") || lower.contains("opus") || lower.contains("grok-3") -> "🚀 מודל דגל (איכות מקסימלית)"
                else -> "💬 יצירת טקסט ושיחה"
            }
        }

    val effectivelyFree: Boolean
        get() = isFree || id.contains(":free", ignoreCase = true) || displayName.contains("חינמי") || displayName.contains("free", ignoreCase = true)

    fun matchesFilter(filter: ModelFilterType): Boolean {
        val caps = capabilities
        return when (filter) {
            ModelFilterType.ALL -> true
            ModelFilterType.FREE -> effectivelyFree
            ModelFilterType.TEXT -> AiTaskType.TEXT in caps
            ModelFilterType.VISION -> AiTaskType.IMAGE_INPUT in caps
            ModelFilterType.IMAGE_GEN -> AiTaskType.IMAGE_GEN in caps
            ModelFilterType.AUDIO -> AiTaskType.AUDIO_GEN in caps
            ModelFilterType.VIDEO -> AiTaskType.VIDEO_GEN in caps
            ModelFilterType.FILES -> AiTaskType.FILE_INPUT in caps
        }
    }

    companion object {
        /**
         * Sorts models so ALL free models appear first (ordered by quality),
         * followed by paid/regular models (ordered by quality).
         */
        fun sortBestFirst(models: List<AiModelInfo>): List<AiModelInfo> {
            return models.sortedWith(
                compareByDescending<AiModelInfo> { it.effectivelyFree }
                    .thenByDescending { it.qualityScore }
                    .thenByDescending { it.isRecommended }
                    .thenBy { it.id }
            )
        }
    }
}

enum class ModelFilterType(
    val labelHe: String,
    val labelEn: String,
    val emoji: String
) {
    ALL("הכל", "All", "✨"),
    FREE("חינמי", "Free", "🟢"),
    TEXT("טקסט", "Text", "📝"),
    VISION("ראייה/תמונה", "Vision", "👁️"),
    IMAGE_GEN("ייצור תמונה", "Image Gen", "🎨"),
    AUDIO("שמע/סאונד", "Audio", "🔊"),
    VIDEO("וידאו", "Video", "🎬"),
    FILES("קבצים", "Files", "📎")
}

data class FetchModelsResponse(
    val isSuccess: Boolean,
    val models: List<AiModelInfo>,
    val errorMessage: String? = null
)

