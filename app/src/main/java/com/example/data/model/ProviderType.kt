package com.example.data.model

enum class ProviderType(
    val displayName: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    val defaultBaseUrl: String,
    val keyHint: String,
    val documentationUrl: String
) {
    GROQ(
        displayName = "Groq (GroqCloud Playground)",
        defaultModel = "llama-3.3-70b-versatile",
        suggestedModels = listOf(
            "llama-3.3-70b-versatile",
            "deepseek-r1-distill-llama-70b",
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "qwen-qwq-32b",
            "llama-3.1-8b-instant",
            "gemma2-9b-it"
        ),
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        keyHint = "gsk_...",
        documentationUrl = "console.groq.com/playground"
    ),
    GEMINI(
        displayName = "Google Gemini",
        defaultModel = "gemini-2.5-flash",
        suggestedModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
        ),
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        keyHint = "AIzaSy...",
        documentationUrl = "aistudio.google.com/apikey"
    ),
    DEEPSEEK(
        displayName = "DeepSeek AI",
        defaultModel = "deepseek-chat",
        suggestedModels = listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        ),
        defaultBaseUrl = "https://api.deepseek.com",
        keyHint = "sk-...",
        documentationUrl = "platform.deepseek.com"
    ),
    OPENROUTER(
        displayName = "OpenRouter (חינמי ובתשלום)",
        defaultModel = "meta-llama/llama-3.3-70b-instruct:free",
        suggestedModels = listOf(
            "meta-llama/llama-3.3-70b-instruct:free",
            "deepseek/deepseek-r1:free",
            "deepseek/deepseek-chat:free",
            "google/gemini-2.0-flash-exp:free",
            "qwen/qwen-2.5-72b-instruct:free",
            "anthropic/claude-3.5-sonnet",
            "openai/gpt-4o"
        ),
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        keyHint = "sk-or-...",
        documentationUrl = "openrouter.ai/keys"
    ),
    CLAUDE(
        displayName = "Anthropic Claude",
        defaultModel = "claude-3-7-sonnet-latest",
        suggestedModels = listOf(
            "claude-3-7-sonnet-latest",
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022",
            "claude-3-opus-20240229"
        ),
        defaultBaseUrl = "https://api.anthropic.com",
        keyHint = "sk-ant-...",
        documentationUrl = "console.anthropic.com"
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultModel = "gpt-4o-mini",
        suggestedModels = listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "o3-mini",
            "o1-mini",
            "gpt-4.5-preview"
        ),
        defaultBaseUrl = "https://api.openai.com/v1",
        keyHint = "sk-proj-...",
        documentationUrl = "platform.openai.com/api-keys"
    ),
    GROK(
        displayName = "xAI Grok",
        defaultModel = "grok-3-latest",
        suggestedModels = listOf(
            "grok-3-latest",
            "grok-3-mini-latest",
            "grok-2-latest",
            "grok-2-vision-latest"
        ),
        defaultBaseUrl = "https://api.x.ai/v1",
        keyHint = "xai-...",
        documentationUrl = "console.x.ai"
    ),
    MISTRAL(
        displayName = "Mistral AI",
        defaultModel = "mistral-large-latest",
        suggestedModels = listOf(
            "mistral-large-latest",
            "codestral-latest",
            "mistral-small-latest",
            "open-mistral-nemo"
        ),
        defaultBaseUrl = "https://api.mistral.ai/v1",
        keyHint = "...",
        documentationUrl = "console.mistral.ai"
    ),
    HUGGINGFACE(
        displayName = "Hugging Face",
        defaultModel = "meta-llama/Llama-3.2-3B-Instruct",
        suggestedModels = listOf(
            "meta-llama/Llama-3.2-3B-Instruct",
            "Qwen/Qwen2.5-72B-Instruct",
            "mistralai/Mistral-7B-Instruct-v0.3"
        ),
        defaultBaseUrl = "https://router.huggingface.co/hf-inference/v1",
        keyHint = "hf_...",
        documentationUrl = "huggingface.co/settings/tokens"
    )
}
