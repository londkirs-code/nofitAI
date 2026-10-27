package com.example.data.model

data class AiModelInfo(
    val id: String,
    val displayName: String = id,
    val isFree: Boolean = false,
    val description: String? = null,
    val contextWindow: Int? = null,
    val isRecommended: Boolean = false
) {
    val roleBadge: String
        get() = when {
            id.contains("reasoner", ignoreCase = true) || id.contains("r1", ignoreCase = true) || id.contains("o1", ignoreCase = true) || id.contains("o3", ignoreCase = true) || id.contains("qwq", ignoreCase = true) -> "🧠 חשיבה והיגיון"
            id.contains("code", ignoreCase = true) || id.contains("coder", ignoreCase = true) -> "💻 קידוד ותכנות"
            id.contains("vision", ignoreCase = true) -> "👁️ מולטימדיה וראייה"
            id.contains("flash", ignoreCase = true) || id.contains("instant", ignoreCase = true) || id.contains("haiku", ignoreCase = true) || id.contains("mini", ignoreCase = true) || id.contains("8b", ignoreCase = true) -> "⚡ מהיר וחסכוני"
            id.contains("pro", ignoreCase = true) || id.contains("sonnet", ignoreCase = true) || id.contains("opus", ignoreCase = true) || id.contains("large", ignoreCase = true) || id.contains("70b", ignoreCase = true) || id.contains("gpt-4o", ignoreCase = true) -> "🌟 מודל דגל מתקדם"
            else -> "💬 שיחה וסיוע כללי"
        }

    val pricingBadge: String
        get() = if (isFree || id.contains(":free", ignoreCase = true)) "חינמי" else "בתשלום"
}

data class FetchModelsResponse(
    val isSuccess: Boolean,
    val models: List<AiModelInfo>,
    val errorMessage: String? = null
)
