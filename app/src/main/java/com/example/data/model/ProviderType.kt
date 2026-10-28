package com.example.data.model

/**
 * Defines the task/modality category for routing requests and configuring keys/models:
 * - TEXT: טקסט רגיל (שיחה, קוד, שאלות ותשובות)
 * - IMAGE_INPUT: ניתוח תמונה / צילום מסך (Vision)
 * - IMAGE_GEN: בקשה לייצור תמונה (Image Generation)
 * - AUDIO_GEN: בקשה לייצור סאונד / דיבור / שיר (Audio & Speech Generation)
 * - VIDEO_GEN: בקשה לייצור וידאו / אנימציה (Video Generation)
 * - FILE_INPUT: בקשה המכילה קובץ / מסמך / שמע מוקלט
 */
enum class AiTaskType(
    val titleHe: String,
    val titleEn: String,
    val shortBadgeHe: String,
    val emoji: String
) {
    TEXT("טקסט רגיל", "Regular Text", "טקסט", "📝"),
    IMAGE_INPUT("ניתוח תמונה (Vision)", "Image Analysis", "ראייה/תמונה", "👁️"),
    IMAGE_GEN("ייצור תמונה", "Image Generation", "יצירת תמונה", "🎨"),
    AUDIO_GEN("ייצור סאונד / שמע", "Audio Generation", "יצירת סאונד", "🔊"),
    VIDEO_GEN("ייצור וידאו", "Video Generation", "יצירת וידאו", "🎬"),
    FILE_INPUT("ניתוח קובץ / מסמך", "File Analysis", "קובץ", "📎");

    companion object {
        val DEFAULT_TASKS_CSV = "TEXT,IMAGE_INPUT,FILE_INPUT"

        fun parseTasksCsv(csv: String?): Set<AiTaskType> {
            if (csv.isNullOrBlank()) return setOf(TEXT, IMAGE_INPUT, FILE_INPUT)
            val parsed = csv.split(",")
                .mapNotNull { token ->
                    entries.find { it.name.equals(token.trim(), ignoreCase = true) }
                }
                .toSet()
            return if (parsed.isEmpty()) setOf(TEXT) else parsed
        }

        fun toCsv(tasks: Collection<AiTaskType>): String {
            if (tasks.isEmpty()) return TEXT.name
            return tasks.joinToString(",") { it.name }
        }
    }
}

/**
 * Protocol format for Custom API Keys (Task #17)
 */
enum class CustomApiProtocol(
    val displayName: String,
    val defaultEndpointHint: String
) {
    OPENAI("OpenAI Compatible (/v1/chat/completions)", "https://api.example.com/v1/chat/completions"),
    GEMINI("Google Gemini Format (:generateContent)", "https://generativelanguage.googleapis.com/v1beta"),
    CLAUDE("Anthropic Claude Format (/v1/messages)", "https://api.anthropic.com/v1/messages")
}

enum class ProviderType(
    val displayName: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    val keyHint: String,
    val documentationUrl: String,
    val apiKeysWebUrl: String
) {
    GROQ(
        displayName = "Groq (חינמי ומהיר במיוחד)",
        defaultModel = "llama-3.3-70b-versatile",
        suggestedModels = listOf(
            "llama-3.3-70b-versatile",
            "deepseek-r1-distill-llama-70b",
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "qwen-qwq-32b",
            "llama-3.1-8b-instant",
            "gemma2-9b-it"
        ),
        keyHint = "gsk_...",
        documentationUrl = "console.groq.com/keys",
        apiKeysWebUrl = "https://console.groq.com/keys"
    ),
    GEMINI(
        displayName = "Google Gemini",
        defaultModel = "gemini-2.5-flash",
        suggestedModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash",
            "gemini-2.0-flash-exp-image-generation",
            "gemini-1.5-pro",
            "gemini-1.5-flash"
        ),
        keyHint = "AIzaSy...",
        documentationUrl = "aistudio.google.com/apikey",
        apiKeysWebUrl = "https://aistudio.google.com/apikey"
    ),
    OPENROUTER(
        displayName = "OpenRouter (מודלים חינמיים ומתקדמים)",
        defaultModel = "deepseek/deepseek-chat-v3-0324:free",
        suggestedModels = listOf(
            "deepseek/deepseek-chat-v3-0324:free",
            "deepseek/deepseek-r1:free",
            "google/gemini-2.0-flash-exp:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "qwen/qwq-32b:free",
            "anthropic/claude-3.7-sonnet",
            "openai/gpt-4o"
        ),
        keyHint = "sk-or-v1-...",
        documentationUrl = "openrouter.ai/keys",
        apiKeysWebUrl = "https://openrouter.ai/keys"
    ),
    OPENAI(
        displayName = "OpenAI (ChatGPT / DALL-E / TTS)",
        defaultModel = "gpt-4o-mini",
        suggestedModels = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "o3-mini",
            "o1",
            "gpt-4-turbo",
            "dall-e-3",
            "tts-1"
        ),
        keyHint = "sk-proj-...",
        documentationUrl = "platform.openai.com/api-keys",
        apiKeysWebUrl = "https://platform.openai.com/api-keys"
    ),
    CLAUDE(
        displayName = "Anthropic Claude",
        defaultModel = "claude-3-7-sonnet-latest",
        suggestedModels = listOf(
            "claude-3-7-sonnet-latest",
            "claude-3-5-sonnet-latest",
            "claude-3-5-haiku-latest",
            "claude-3-opus-latest"
        ),
        keyHint = "sk-ant-...",
        documentationUrl = "console.anthropic.com/settings/keys",
        apiKeysWebUrl = "https://console.anthropic.com/settings/keys"
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultModel = "deepseek-chat",
        suggestedModels = listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        ),
        keyHint = "sk-...",
        documentationUrl = "platform.deepseek.com/api_keys",
        apiKeysWebUrl = "https://platform.deepseek.com/api_keys"
    ),
    GROK(
        displayName = "xAI (Grok)",
        defaultModel = "grok-2-latest",
        suggestedModels = listOf(
            "grok-3-latest",
            "grok-2-latest",
            "grok-2-vision-latest",
            "grok-beta"
        ),
        keyHint = "xai-...",
        documentationUrl = "console.x.ai",
        apiKeysWebUrl = "https://console.x.ai"
    ),
    MISTRAL(
        displayName = "Mistral AI",
        defaultModel = "mistral-large-latest",
        suggestedModels = listOf(
            "mistral-large-latest",
            "pixtral-large-latest",
            "codestral-latest",
            "mistral-small-latest",
            "open-mistral-nemo"
        ),
        keyHint = "מפתח Mistral...",
        documentationUrl = "console.mistral.ai/api-keys",
        apiKeysWebUrl = "https://console.mistral.ai/api-keys"
    ),
    HUGGINGFACE(
        displayName = "Hugging Face",
        defaultModel = "Qwen/Qwen2.5-72B-Instruct",
        suggestedModels = listOf(
            "Qwen/Qwen2.5-72B-Instruct",
            "meta-llama/Llama-3.3-70B-Instruct",
            "deepseek-ai/DeepSeek-R1-Distill-Qwen-32B",
            "black-forest-labs/FLUX.1-schnell",
            "mistralai/Mistral-7B-Instruct-v0.3"
        ),
        keyHint = "hf_...",
        documentationUrl = "huggingface.co/settings/tokens",
        apiKeysWebUrl = "https://huggingface.co/settings/tokens"
    ),
    CUSTOM(
        displayName = "מפתח מותאם אישית (Custom API)",
        defaultModel = "gpt-4o-mini",
        suggestedModels = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "llama-3.3-70b-versatile",
            "claude-3-7-sonnet-latest",
            "gemini-2.5-flash",
            "deepseek-chat"
        ),
        keyHint = "מפתח API מותאם אישית...",
        documentationUrl = "הגדרת שרת API מותאם אישית",
        apiKeysWebUrl = "https://openrouter.ai/keys"
    );

    companion object {
        /**
         * Automatically detects the AI provider from the raw API key string and optional context hint.
         * Users can always manually override the selected provider after auto-detection.
         */
        fun detectProviderFromKey(rawKey: String, contextHint: String = ""): ProviderType? {
            val k = rawKey.trim()
            if (k.length < 8) return null
            val hintLower = contextHint.lowercase()

            return when {
                k.startsWith("AIza") -> GEMINI
                k.startsWith("sk-or-") -> OPENROUTER
                k.startsWith("gsk_") -> GROQ
                k.startsWith("sk-ant-") -> CLAUDE
                k.startsWith("xai-") -> GROK
                k.startsWith("hf_") -> HUGGINGFACE
                k.startsWith("sk-proj-") || k.startsWith("sk-svcacct-") || k.startsWith("sk-None-") -> OPENAI
                k.startsWith("sk-") -> {
                    val afterPrefix = k.removePrefix("sk-")
                    val isHexOnly = afterPrefix.length in 30..36 && afterPrefix.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                    when {
                        hintLower.contains("openrouter") -> OPENROUTER
                        hintLower.contains("deepseek") || isHexOnly -> DEEPSEEK
                        hintLower.contains("claude") || hintLower.contains("anthropic") -> CLAUDE
                        hintLower.contains("custom") -> CUSTOM
                        else -> OPENAI
                    }
                }
                else -> {
                    when {
                        hintLower.contains("gemini") || hintLower.contains("google") -> GEMINI
                        hintLower.contains("openrouter") -> OPENROUTER
                        hintLower.contains("groq") -> GROQ
                        hintLower.contains("claude") || hintLower.contains("anthropic") -> CLAUDE
                        hintLower.contains("deepseek") -> DEEPSEEK
                        hintLower.contains("grok") || hintLower.contains("xai") -> GROK
                        hintLower.contains("mistral") -> MISTRAL
                        hintLower.contains("huggingface") || hintLower.contains("hf") -> HUGGINGFACE
                        hintLower.contains("openai") || hintLower.contains("gpt") -> OPENAI
                        k.length == 32 && k.all { it.isLetterOrDigit() } -> MISTRAL
                        else -> null
                    }
                }
            }
        }
    }
}

