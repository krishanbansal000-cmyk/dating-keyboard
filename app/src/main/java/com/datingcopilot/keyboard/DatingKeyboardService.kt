package com.datingcopilot.keyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import kotlinx.coroutines.*

class DatingKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var suggestionBar: SuggestionBar
    private lateinit var apiClient: ApiClient
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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

        return root
    }

    private fun fetchSuggestions() {
        val text = currentInputConnection?.getTextBeforeCursor(500, 0)?.toString() ?: ""

        // Get chat context from accessibility service
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
