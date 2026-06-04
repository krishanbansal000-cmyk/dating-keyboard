package com.datingcopilot.keyboard.chat

data class ChatMessage(
    val sender: String,     // "them" or "you"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuggestion: Boolean = false
)

data class SuggestionOption(
    val text: String,
    val confidence: Int = 90,
    val persona: String = "playful"
)

data class AnalyzeResponse(
    val conversation: List<ChatMessage>? = null,
    val suggestions: List<SuggestionOption>? = null,
    val detectedApp: String? = null,
    val persona: String? = null
)
