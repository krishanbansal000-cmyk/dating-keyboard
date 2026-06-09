package com.datingcopilot.keyboard.chat

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import org.json.JSONArray

data class PersonaStats(
    val persona: String,
    val selectionCount: Int,
    val copyCount: Int,
    val effectivenessScore: Float,
    val lastUsedTimestamp: Long
)

class PersonaManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("persona_telemetry", Context.MODE_PRIVATE)
    private val knownPersonas = listOf("playful", "witty", "chill", "direct", "friendly", "romantic", "bold")

    fun recordSelection(persona: String) {
        val key = keyFor(persona)
        val data = loadPersonaData(persona)
        data.put("selection_count", data.optInt("selection_count", 0) + 1)
        data.put("last_selected", System.currentTimeMillis())
        savePersonaData(persona, data)
    }

    fun recordCopy(persona: String) {
        val data = loadPersonaData(persona)
        data.put("copy_count", data.optInt("copy_count", 0) + 1)
        data.put("last_copied", System.currentTimeMillis())
        savePersonaData(persona, data)
    }

    fun getRecommendedPersona(): String? {
        val stats = getAllPersonaStats()
            .filter { it.selectionCount > 0 || it.copyCount > 0 }
        if (stats.isEmpty()) return null

        return stats.maxByOrNull {
            it.effectivenessScore * 0.7f + (it.copyCount.toFloat()) * 0.3f
        }?.persona
    }

    fun getTopPersonas(limit: Int = 3): List<PersonaStats> {
        return getAllPersonaStats()
            .sortedByDescending { it.effectivenessScore * 0.7f + it.copyCount * 0.3f }
            .take(limit)
    }

    fun getAllPersonaStats(): List<PersonaStats> {
        return knownPersonas.map { persona ->
            val data = loadPersonaData(persona)
            val selectionCount = data.optInt("selection_count", 0)
            val copyCount = data.optInt("copy_count", 0)
            val effectiveness = if (selectionCount > 0) copyCount.toFloat() / selectionCount else 0f
            val lastUsed = maxOf(
                data.optLong("last_selected", 0L),
                data.optLong("last_copied", 0L)
            )
            PersonaStats(persona, selectionCount, copyCount, effectiveness, lastUsed)
        }
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private fun keyFor(persona: String): String {
        return "persona_${persona.lowercase().replace(" ", "_")}"
    }

    private fun loadPersonaData(persona: String): JSONObject {
        val raw = prefs.getString(keyFor(persona), "{}") ?: "{}"
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun savePersonaData(persona: String, data: JSONObject) {
        prefs.edit().putString(keyFor(persona), data.toString()).apply()
    }
}
