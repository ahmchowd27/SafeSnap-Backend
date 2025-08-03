package com.safesnap.backend.dto.rca

data class RcaAiSuggestionDTO(
    val suggestedFiveWhys: String,
    val suggestedCorrectiveAction: String, 
    val suggestedPreventiveAction: String,
    val reviewedByManager: String // Manager who approved showing these suggestions
)
