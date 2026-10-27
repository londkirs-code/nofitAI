package com.example.data.model

data class AiModelInfo(
    val id: String,
    val displayName: String = id,
    val isFree: Boolean = false,
    val description: String? = null,
    val contextWindow: Int? = null,
    val isRecommended: Boolean = false
)

data class FetchModelsResponse(
    val isSuccess: Boolean,
    val models: List<AiModelInfo>,
    val errorMessage: String? = null
)
