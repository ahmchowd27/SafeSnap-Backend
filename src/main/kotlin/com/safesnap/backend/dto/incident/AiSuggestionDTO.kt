package com.safesnap.backend.dto.incident

data class AiSuggestionDTO(
    val summary: String,
    val keywords: List<String>
)