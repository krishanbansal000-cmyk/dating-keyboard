package com.datingcopilot.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.datingcopilot.keyboard.chat.AnalyzeResponse
import com.datingcopilot.keyboard.chat.ChatMessage
import com.datingcopilot.keyboard.chat.SuggestionOption
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class UserProfile(
    val name: String = "",
    val age: Int = 0,
    val bio: String = ""
)

data class MatchContext(
    val name: String = "",
    val age: Int = 0,
    val bio: String = "",
    val platform: String = "tinder"
)

class ApiClient(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("dating_copilot", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    // ── User Profile ──
    fun saveProfile(profile: UserProfile) {
        prefs.edit()
            .putString("profile_name", profile.name)
            .putInt("profile_age", profile.age)
            .putString("profile_bio", profile.bio)
            .apply()
    }

    fun loadProfile(): UserProfile = UserProfile(
        name = prefs.getString("profile_name", "") ?: "",
        age = prefs.getInt("profile_age", 0),
        bio = prefs.getString("profile_bio", "") ?: ""
    )

    // ── Match Context ──
    fun saveMatchContext(ctx: MatchContext) {
        prefs.edit()
            .putString("match_name", ctx.name)
            .putInt("match_age", ctx.age)
            .putString("match_bio", ctx.bio)
            .putString("match_platform", ctx.platform)
            .apply()
    }

    fun loadMatchContext(): MatchContext = MatchContext(
        name = prefs.getString("match_name", "") ?: "",
        age = prefs.getInt("match_age", 0),
        bio = prefs.getString("match_bio", "") ?: "",
        platform = prefs.getString("match_platform", "tinder") ?: "tinder"
    )

    fun clearMatchContext() {
        prefs.edit()
            .remove("match_name")
            .remove("match_age")
            .remove("match_bio")
            .apply()
    }

    // ── Auth ──
    private fun getBaseUrl(): String =
        prefs.getString("backend_url", "http://10.0.2.2:8000") ?: "http://10.0.2.2:8000"

    private fun getAuthToken(): String? = prefs.getString("auth_token", null)
    fun setAuthToken(token: String) { prefs.edit().putString("auth_token", token).apply() }

    fun hasCredentials(): Boolean = getAuthToken() != null

    fun loginSync(email: String, password: String): Boolean {
        return try {
            val json = gson.toJson(mapOf("email" to email, "password" to password))
            val request = Request.Builder()
                .url("${getBaseUrl()}/api/v1/auth/login")
                .post(json.toRequestBody(JSON))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return false
                val result = gson.fromJson(body, Map::class.java)
                val token = result["access_token"] as? String
                if (token != null) {
                    setAuthToken(token)
                    prefs.edit().putString("email", email).apply()
                    return true
                }
            }
            false
        } catch (_: Exception) { false }
    }

    // ── Fetch current match context from backend ──
    fun fetchCurrentMatchContext(): MatchContext? {
        return try {
            val request = Request.Builder()
                .url("${getBaseUrl()}/api/v1/chat/current-match")
                .addHeader("Authorization", "Bearer ${getAuthToken()}")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val result = gson.fromJson(body, Map::class.java)
            val matchName = result["match_name"] as? String ?: ""
            val messages = result["messages"] as? List<Map<String, Any>> ?: emptyList()

            val latestText = messages.lastOrNull()?.let { it["text"] as? String } ?: ""

            if (matchName.isNotBlank()) {
                val ctx = MatchContext(
                    name = matchName,
                    bio = latestText,
                    platform = result["platform"] as? String ?: "tinder"
                )
                saveMatchContext(ctx)
                return ctx
            }
            null
        } catch (_: Exception) { null }
    }

    // ── AI Suggestions (legacy keyboard endpoint) ──
    fun getSuggestions(conversationText: String, tone: String): List<SuggestionOption>? {
        return try {
            val matchId = UUID.randomUUID().toString().substring(0, 8)
            val myProfile = loadProfile()
            val matchCtx = loadMatchContext()

            val conversation = if (conversationText.isNotBlank()) {
                listOf(mapOf("sender" to "them", "text" to conversationText))
            } else emptyList()

            val requestBody = mapOf(
                "match_id" to matchId,
                "match_name" to matchCtx.name.ifEmpty { "Match" },
                "platform" to matchCtx.platform,
                "conversation" to conversation,
                "tone" to tone,
                "my_profile" to mapOf(
                    "name" to myProfile.name,
                    "age" to myProfile.age,
                    "bio" to myProfile.bio
                ),
                "their_profile" to mapOf(
                    "name" to matchCtx.name,
                    "age" to matchCtx.age,
                    "bio" to matchCtx.bio
                )
            )

            val json = gson.toJson(requestBody)
            val reqBuilder = Request.Builder()
                .url("${getBaseUrl()}/api/v1/chat/draft")
                .post(json.toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")
            getAuthToken()?.let { reqBuilder.addHeader("Authorization", "Bearer $it") }

            val response = client.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) return null

            val responseBody = response.body?.string() ?: return null
            val result = gson.fromJson(responseBody, Map::class.java)
            val optionsRaw = result["options"] as? List<Map<String, Any>> ?: return null

            optionsRaw.mapNotNull { opt ->
                val text = opt["text"] as? String ?: return@mapNotNull null
                val optTone = opt["tone"] as? String ?: tone
                val confidence = (opt["confidence"] as? Number)?.toInt() ?: 90
                SuggestionOption(text, confidence, optTone)
            }
        } catch (_: Exception) { null }
    }

    // ── NEW: Upload screenshot for analysis ──
    fun uploadScreenshot(uri: Uri, persona: String, context: Context): AnalyzeResponse? {
        return try {
            // Copy URI content to temp file
            val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val fileBody = tempFile.asRequestBody(mediaType)
            val imagePart = MultipartBody.Part.createFormData("image", tempFile.name, fileBody)

            val personaBody = persona.toRequestBody("text/plain".toMediaTypeOrNull())
            val userIdBody = "user_001".toRequestBody("text/plain".toMediaTypeOrNull())

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(imagePart)
                .addFormDataPart("persona", persona)
                .addFormDataPart("user_id", "user_001")
                .build()

            val request = Request.Builder()
                .url("${getBaseUrl()}/api/v1/analyze-screenshot")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            tempFile.delete()

            if (!response.isSuccessful) return null
            val responseBody = response.body?.string() ?: return null

            val result = gson.fromJson(responseBody, Map::class.java)
            val error = result["error"] as? String
            if (error != null) return null

            // Parse conversation
            val conversationRaw = result["conversation"] as? List<Map<String, Any>> ?: emptyList()
            val conversation = conversationRaw.mapNotNull { msg ->
                val sender = msg["sender"] as? String ?: return@mapNotNull null
                val text = msg["text"] as? String ?: return@mapNotNull null
                ChatMessage(sender, text)
            }

            // Parse suggestions
            val suggestionsRaw = result["suggestions"] as? List<Map<String, Any>> ?: emptyList()
            val suggestions = suggestionsRaw.mapNotNull { sug ->
                val text = sug["text"] as? String ?: return@mapNotNull null
                val confidence = (sug["confidence"] as? Number)?.toInt() ?: 90
                val sugPersona = sug["persona"] as? String ?: persona
                SuggestionOption(text, confidence, sugPersona)
            }

            AnalyzeResponse(
                conversation = conversation,
                suggestions = suggestions,
                detectedApp = result["detected_app"] as? String,
                persona = result["persona"] as? String
            )
        } catch (_: Exception) { null }
    }

    // ── NEW: Get suggestions from pasted text ──
    fun getSuggestionsFromText(text: String, persona: String): List<SuggestionOption>? {
        return try {
            val myProfile = loadProfile()
            val matchCtx = loadMatchContext()

            // Parse simple conversation format
            val lines = text.lines().filter { it.isNotBlank() }
            val conversation = lines.map { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("You:", ignoreCase = true) ||
                    trimmed.startsWith("Me:", ignoreCase = true)) {
                    mapOf("sender" to "you", "text" to trimmed.substringAfter(":").trim())
                } else if (trimmed.startsWith("Them:", ignoreCase = true)) {
                    mapOf("sender" to "them", "text" to trimmed.substringAfter(":").trim())
                } else {
                    mapOf("sender" to "them", "text" to trimmed)
                }
            }

            val requestBody = mapOf(
                "match_id" to UUID.randomUUID().toString().substring(0, 8),
                "match_name" to matchCtx.name.ifEmpty { "Match" },
                "platform" to matchCtx.platform,
                "conversation" to conversation,
                "tone" to persona,
                "my_profile" to mapOf(
                    "name" to myProfile.name,
                    "age" to myProfile.age,
                    "bio" to myProfile.bio
                ),
                "their_profile" to mapOf(
                    "name" to matchCtx.name,
                    "age" to matchCtx.age,
                    "bio" to matchCtx.bio
                )
            )

            val json = gson.toJson(requestBody)
            val reqBuilder = Request.Builder()
                .url("${getBaseUrl()}/api/v1/chat/draft")
                .post(json.toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")
            getAuthToken()?.let { reqBuilder.addHeader("Authorization", "Bearer $it") }

            val response = client.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) return null

            val responseBody = response.body?.string() ?: return null
            val result = gson.fromJson(responseBody, Map::class.java)
            val optionsRaw = result["options"] as? List<Map<String, Any>> ?: return null

            optionsRaw.mapNotNull { opt ->
                val optText = opt["text"] as? String ?: return@mapNotNull null
                val optTone = opt["tone"] as? String ?: persona
                val confidence = (opt["confidence"] as? Number)?.toInt() ?: 90
                SuggestionOption(optText, confidence, optTone)
            }
        } catch (_: Exception) { null }
    }
}
