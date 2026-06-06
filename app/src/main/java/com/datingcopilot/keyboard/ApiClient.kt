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

    companion object {
        const val DEFAULT_BACKEND_URL = "http://164.68.103.130:8000"
    }

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

    // ── Auth (optional, not required) ──
    private fun getBaseUrl(): String =
        prefs.getString("backend_url", DEFAULT_BACKEND_URL)?.trim()?.ifEmpty { DEFAULT_BACKEND_URL } ?: DEFAULT_BACKEND_URL

    private fun getAuthToken(): String? = prefs.getString("auth_token", null)
    fun setAuthToken(token: String) { prefs.edit().putString("auth_token", token).apply() }

    fun hasCredentials(): Boolean = false

    fun loginSync(email: String, password: String): Boolean = true

    // ── Fetch current match context from backend ──
    fun fetchCurrentMatchContext(): MatchContext? {
        return try {
            val request = Request.Builder()
                .url("${getBaseUrl()}/api/v1/chat/current-match")
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
    fun getSuggestions(
        conversationText: String,
        tone: String,
        intent: String = "keep_going",
        platform: String = "whatsapp"
    ): List<SuggestionOption>? {
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
                "conversation" to conversation,
                "tone" to tone,
                "intent" to intent,
                "platform" to platform,
                "hinglish" to if (prefs.getBoolean("hinglish_mode", false)) "true" else "false",
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

    // ── AI Suggestions with chat context (for keyboard) ──
    fun getSuggestionsWithContext(
        userText: String,
        chatContext: String,
        tone: String,
        intent: String = "keep_going",
        platform: String = "whatsapp"
    ): List<SuggestionOption>? {
        return try {
            val myProfile = loadProfile()
            val matchCtx = loadMatchContext()

            val conversation = if (userText.isNotBlank()) {
                listOf(mapOf("sender" to "them", "text" to userText))
            } else emptyList()

            val requestBody = mapOf(
                "match_id" to UUID.randomUUID().toString().substring(0, 8),
                "match_name" to matchCtx.name.ifEmpty { "Match" },
                "conversation" to conversation,
                "chat_context" to chatContext,
                "tone" to tone,
                "intent" to intent,
                "platform" to platform,
                "hinglish" to if (prefs.getBoolean("hinglish_mode", false)) "true" else "false",
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
    fun uploadScreenshot(
        uri: Uri,
        persona: String,
        context: Context,
        intent: String = "keep_going",
        platform: String = "whatsapp"
    ): AnalyzeResponse? {
        return try {
            // Copy URI content to temp file - detect MIME type
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val extension = when {
                mimeType.contains("png") -> ".png"
                mimeType.contains("webp") -> ".webp"
                else -> ".jpg"
            }
            
            val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}$extension")
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                android.util.Log.e("ApiClient", "Cannot open input stream for URI: $uri")
                return null
            }
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val mediaType = mimeType.toMediaTypeOrNull()
            val fileBody = tempFile.asRequestBody(mediaType)
            val imagePart = MultipartBody.Part.createFormData("image", tempFile.name, fileBody)

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(imagePart)
                .addFormDataPart("persona", persona)
                .addFormDataPart("intent", intent)
                .addFormDataPart("platform", platform)
                .addFormDataPart("user_gender", prefs.getString("user_gender", "male") ?: "male")
                .addFormDataPart("hinglish", if (prefs.getBoolean("hinglish_mode", false)) "true" else "false")
                .addFormDataPart("user_id", "user_001")
                .build()

            val request = Request.Builder()
                .url("${getBaseUrl()}/api/v1/analyze-screenshot")
                .post(requestBody)
                .build()

            android.util.Log.d("ApiClient", "Uploading screenshot, size: ${tempFile.length()} bytes")
            val response = client.newCall(request).execute()
            tempFile.delete()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "no body"
                android.util.Log.e("ApiClient", "Upload failed: HTTP ${response.code} - $errorBody")
                return null
            }
            
            val responseBody = response.body?.string() ?: return null
            android.util.Log.d("ApiClient", "Upload response: ${responseBody.take(200)}")

            val result = gson.fromJson(responseBody, Map::class.java)
            val error = result["error"] as? String
            if (error != null) {
                android.util.Log.e("ApiClient", "Backend returned error: $error")
                return null
            }

            // Parse conversation
            val conversationRaw = result["conversation"] as? List<Map<String, Any>> ?: emptyList()
            val conversation = conversationRaw.mapNotNull { msg ->
                val sender = msg["sender"] as? String ?: return@mapNotNull null
                val text = msg["text"] as? String ?: return@mapNotNull null
                ChatMessage(sender, text)
            }

            // Parse suggestions
            val suggestionsRaw = result["suggestions"] as? List<Map<String, Any>>
            android.util.Log.d("ApiClient", "suggestionsRaw type=${suggestionsRaw?.javaClass?.name}, size=${suggestionsRaw?.size}")
            val suggestions = suggestionsRaw?.mapNotNull { sug ->
                val text = sug["text"] as? String ?: return@mapNotNull null
                val confidence = (sug["confidence"] as? Number)?.toInt() ?: 90
                val sugPersona = sug["persona"] as? String ?: persona
                android.util.Log.d("ApiClient", "Parsed suggestion: text=${text.take(30)}, conf=$confidence")
                SuggestionOption(text, confidence, sugPersona)
            } ?: emptyList()
            android.util.Log.d("ApiClient", "Final suggestions count: ${suggestions.size}")

            AnalyzeResponse(
                conversation = conversation,
                suggestions = suggestions,
                detectedApp = result["detected_app"] as? String,
                persona = result["persona"] as? String
            )
        } catch (e: Exception) {
            android.util.Log.e("ApiClient", "Upload exception: ${e.message}", e)
            null
        }
    }

    // ── NEW: Get suggestions from pasted text ──
    fun getSuggestionsFromText(
        text: String,
        persona: String,
        intent: String = "keep_going",
        platform: String = "whatsapp"
    ): List<SuggestionOption>? {
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
                "conversation" to conversation,
                "tone" to persona,
                "intent" to intent,
                "platform" to platform,
                "user_gender" to (prefs.getString("user_gender", "male") ?: "male"),
                "hinglish" to if (prefs.getBoolean("hinglish_mode", false)) "true" else "false",
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

            android.util.Log.d("ApiClient", "Sending text request to ${getBaseUrl()}/api/v1/chat/draft with body: ${json.take(200)}")
            
            val response = client.newCall(reqBuilder.build()).execute()
            
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "no body"
                android.util.Log.e("ApiClient", "Text draft failed: HTTP ${response.code} - $errBody")
                return null
            }

            val responseBody = response.body?.string() ?: return null
            android.util.Log.d("ApiClient", "Text draft response: ${responseBody.take(200)}")
            
            val result = gson.fromJson(responseBody, Map::class.java)
            val optionsRaw = result["options"] as? List<Map<String, Any>> ?: return null

            optionsRaw.mapNotNull { opt ->
                val optText = opt["text"] as? String ?: return@mapNotNull null
                val optTone = opt["tone"] as? String ?: persona
                val confidence = (opt["confidence"] as? Number)?.toInt() ?: 90
                SuggestionOption(optText, confidence, optTone)
            }
        } catch (e: Exception) {
            android.util.Log.e("ApiClient", "Text draft exception: ${e.message}", e)
            null
        }
    }

    // ── Streaming suggestions ──
    fun getSuggestionsFromTextStreaming(
        text: String,
        persona: String,
        intent: String = "keep_going",
        platform: String = "whatsapp",
        onPartial: (String) -> Unit,
        onComplete: (List<SuggestionOption>?) -> Unit
    ) {
        try {
            val myProfile = loadProfile()
            val matchCtx = loadMatchContext()

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
                "conversation" to conversation,
                "tone" to persona,
                "intent" to intent,
                "platform" to platform,
                "user_gender" to (prefs.getString("user_gender", "male") ?: "male"),
                "hinglish" to if (prefs.getBoolean("hinglish_mode", false)) "true" else "false",
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
                .url("${getBaseUrl()}/api/v1/chat/draft/stream")
                .post(json.toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")

            val response = client.newCall(reqBuilder.build()).execute()
            
            if (!response.isSuccessful) {
                android.util.Log.e("ApiClient", "Stream failed: HTTP ${response.code}")
                onComplete(null)
                return
            }

            val body = response.body ?: run {
                onComplete(null)
                return
            }

            val source = body.source()
            val buffer = StringBuilder()
            
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                
                if (line.startsWith("data: ")) {
                    val data = line.substring(6)
                    
                    if (data == "[DONE]") {
                        break
                    }
                    
                    try {
                        val parsed = gson.fromJson(data, Map::class.java)
                        
                        // Check for partial text
                        val partial = parsed["partial"] as? String
                        if (partial != null) {
                            onPartial(partial)
                        }
                        
                        // Check for final options
                        val optionsRaw = parsed["options"] as? List<Map<String, Any>>
                        if (optionsRaw != null) {
                            val options = optionsRaw.mapNotNull { opt ->
                                val optText = opt["text"] as? String ?: return@mapNotNull null
                                val optTone = opt["tone"] as? String ?: persona
                                val confidence = (opt["confidence"] as? Number)?.toInt() ?: 90
                                SuggestionOption(optText, confidence, optTone)
                            }
                            onComplete(options)
                            return
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("ApiClient", "Failed to parse SSE: $data")
                    }
                }
            }
            
            // If we get here without final options, try to parse partial as final
            onComplete(null)
        } catch (e: Exception) {
            android.util.Log.e("ApiClient", "Stream exception: ${e.message}", e)
            onComplete(null)
        }
    }
}
