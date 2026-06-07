package com.datingcopilot.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.Toast
import com.datingcopilot.keyboard.chat.SuggestionOption
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.File

class DatingKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var suggestionBar: SuggestionBar
    private lateinit var apiClient: ApiClient
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val gson = Gson()
    private var currentTone = "playful"
    private var currentIntent = "keep_going"
    private var currentPlatform = "whatsapp"

    override fun onCreate() {
        super.onCreate()
        apiClient = ApiClient(this)
        val prefs = getSharedPreferences("dating_copilot", MODE_PRIVATE)
        currentTone = prefs.getString("persona", "playful") ?: "playful"
        currentIntent = prefs.getString("intent", "keep_going") ?: "keep_going"
        currentPlatform = ChatContextService.getChatPlatform(this)
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.bg_dark, null))
        }

        suggestionBar = SuggestionBar(this,
            onSuggestionTap = { text ->
                val ic = currentInputConnection
                val existing = ic?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
                ic?.deleteSurroundingText(existing.length, 0)
                ic?.commitText(text, 1)
            },
            onGenerateTap = {
                fetchSuggestions()
            },
            onScreenshotTap = {
                openScreenshotPicker()
            }
        )

        // Custom keyboard container with rounded top corners
        val keyboardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dpToPx(4), 0, 0)
            val bg = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    dpToPx(12).toFloat(), dpToPx(12).toFloat(),
                    dpToPx(12).toFloat(), dpToPx(12).toFloat(),
                    0f, 0f,
                    0f, 0f
                )
                setColor(resources.getColor(R.color.bg_card, null))
            }
            background = bg
        }

        keyboardView = KeyboardView(this, null).apply {
            keyboard = Keyboard(this@DatingKeyboardService, R.xml.qwerty)
            setOnKeyboardActionListener(this@DatingKeyboardService)
            isPreviewEnabled = false
            setBackgroundColor(resources.getColor(R.color.bg_card, null))
            setPadding(0, dpToPx(6), 0, dpToPx(6))
        }

        keyboardContainer.addView(keyboardView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        root.addView(suggestionBar.rootView)
        root.addView(keyboardContainer)
        loadPendingKeyboardState()

        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::suggestionBar.isInitialized) {
            loadPendingKeyboardState()
        }
    }

    private fun fetchSuggestions() {
        val text = currentInputConnection?.getTextBeforeCursor(500, 0)?.toString() ?: ""

        // Use any saved context from app flows; no screen-reading service is packaged.
        val chatCtx = ChatContextService.getChatContext(this)
        
        // Do NOT merge chat context with user text into one blob.
        // Instead, send chat context separately so backend can handle it properly.
        if (text.isBlank() && chatCtx.isBlank()) return

        scope.launch {
            suggestionBar.showLoading(true)
            val result = withContext(Dispatchers.IO) {
                apiClient.getSuggestionsWithContext(text, chatCtx, currentTone, currentIntent, currentPlatform)
            }
            suggestionBar.showLoading(false)
            if (result != null) {
                suggestionBar.showSuggestions(result)
            } else {
                suggestionBar.showError()
            }
        }
    }

    private fun openScreenshotPicker() {
        requestHideSelf(0)
        val intent = Intent(this, KeyboardScreenshotActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun loadPendingKeyboardState() {
        if (loadPendingScreenshotSuggestions()) return
        loadPendingScreenshotToneOptions()
    }

    private fun loadPendingScreenshotSuggestions(): Boolean {
        val prefs = getSharedPreferences("dating_copilot", MODE_PRIVATE)
        val json = prefs.getString("pending_keyboard_suggestions", null) ?: return false
        val suggestions = runCatching {
            gson.fromJson(json, Array<SuggestionOption>::class.java).toList()
        }.getOrNull().orEmpty()

        if (suggestions.isNotEmpty()) {
            suggestionBar.showSuggestions(suggestions)
            prefs.edit().remove("pending_keyboard_suggestions").apply()
            return true
        }
        return false
    }

    private fun loadPendingScreenshotToneOptions() {
        val prefs = getSharedPreferences("dating_copilot", MODE_PRIVATE)
        val path = prefs.getString("pending_keyboard_screenshot_path", null) ?: return
        if (!File(path).exists()) {
            prefs.edit().remove("pending_keyboard_screenshot_path").apply()
            return
        }
        suggestionBar.showScreenshotToneOptions { persona, intent ->
            generateFromPendingScreenshot(path, persona, intent)
        }
    }

    private fun generateFromPendingScreenshot(path: String, persona: String, intent: String) {
        val screenshot = File(path)
        if (!screenshot.exists()) {
            android.util.Log.e("KeyboardService", "screenshot file not found: $path")
            getSharedPreferences("dating_copilot", MODE_PRIVATE)
                .edit()
                .remove("pending_keyboard_screenshot_path")
                .apply()
            suggestionBar.showError()
            return
        }

        scope.launch {
            suggestionBar.showScreenshotLoading(Uri.fromFile(screenshot))
            android.util.Log.d("KeyboardService", "uploading screenshot for analysis")
            try {
                val result = withContext(Dispatchers.IO) {
                    apiClient.uploadScreenshot(Uri.fromFile(screenshot), persona, this@DatingKeyboardService, intent, currentPlatform)
                }
                suggestionBar.showLoading(false)
                val suggestions = result?.suggestions.orEmpty()
                if (suggestions.isNotEmpty()) {
                    screenshot.delete()
                    getSharedPreferences("dating_copilot", MODE_PRIVATE)
                        .edit()
                        .remove("pending_keyboard_screenshot_path")
                        .apply()
                    suggestionBar.showSuggestions(suggestions)
                    android.util.Log.d("KeyboardService", "screenshot analysis OK, ${suggestions.size} suggestions")
                } else {
                    val errMsg = if (result == null) "API returned null" else "empty suggestions"
                    android.util.Log.e("KeyboardService", "screenshot analysis failed: $errMsg")
                    Toast.makeText(this@DatingKeyboardService, "Analyze failed", Toast.LENGTH_SHORT).show()
                    suggestionBar.showError()
                }
            } catch (e: Exception) {
                suggestionBar.showLoading(false)
                android.util.Log.e("KeyboardService", "screenshot exception: ${e.message}", e)
                Toast.makeText(this@DatingKeyboardService, "Error: ${e.message?.take(50)}", Toast.LENGTH_LONG).show()
                suggestionBar.showError()
            }
        }
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_DONE -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            10 -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            32 -> ic.commitText(" ", 1)
            44 -> ic.commitText(",", 1)
            46 -> ic.commitText(".", 1)
            -101 -> ic.commitText("❤️", 1)
            -102 -> ic.commitText("😍", 1)
            -103 -> ic.commitText("😘", 1)
            -104 -> ic.commitText("🥰", 1)
            -105 -> ic.commitText("😉", 1)
            -106 -> ic.commitText("💕", 1)
            -107 -> ic.commitText("🔥", 1)
            -1 -> {}
            else -> ic.commitText(primaryCode.toChar().toString(), 1)
        }
    }

    override fun onPress(code: Int) {}
    override fun onRelease(code: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
