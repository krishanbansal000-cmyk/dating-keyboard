package com.datingcopilot.keyboard.chat

import android.content.Context
import com.google.gson.Gson

data class AppHistoryEntry(
    val timestamp: Long,
    val type: String,
    val preview: String,
    val suggestions: List<SuggestionOption>
)

object AppHistoryStore {
    private const val PREFS = "dating_copilot"
    private const val KEY = "app_generation_history"
    private const val MAX_ITEMS = 12
    private val gson = Gson()

    fun add(context: Context, type: String, preview: String, suggestions: List<SuggestionOption>) {
        if (suggestions.isEmpty()) return
        val cleanPreview = preview.lines().firstOrNull { it.isNotBlank() }?.take(120) ?: type
        val updated = listOf(
            AppHistoryEntry(
                timestamp = System.currentTimeMillis(),
                type = type,
                preview = cleanPreview,
                suggestions = suggestions.take(3)
            )
        ) + get(context)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, gson.toJson(updated.take(MAX_ITEMS)))
            .apply()
    }

    fun get(context: Context): List<AppHistoryEntry> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            gson.fromJson(json, Array<AppHistoryEntry>::class.java).toList()
        }.getOrDefault(emptyList())
    }
}
