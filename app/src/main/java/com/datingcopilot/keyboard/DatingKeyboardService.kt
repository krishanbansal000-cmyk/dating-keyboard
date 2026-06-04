package com.datingcopilot.keyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import kotlinx.coroutines.*

class DatingKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var suggestionBar: SuggestionBar
    private lateinit var toneSelector: ToneSelector
    private lateinit var apiClient: ApiClient
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentTone = "playful"

    override fun onCreate() {
        super.onCreate()
        apiClient = ApiClient(this)
        scope.launch {
            withContext(Dispatchers.IO) {
                apiClient.fetchCurrentMatchContext()
            }
        }
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1A1D2E.toInt())
        }

        suggestionBar = SuggestionBar(this) { text ->
            currentInputConnection?.commitText(text, 1)
        }

        toneSelector = ToneSelector(this) { tone ->
            currentTone = tone
            fetchSuggestions()
        }

        keyboardView = KeyboardView(this, null).apply {
            keyboard = Keyboard(this@DatingKeyboardService, R.xml.qwerty)
            setOnKeyboardActionListener(this@DatingKeyboardService)
            isPreviewEnabled = false
            setBackgroundColor(0xFF1A1D2E.toInt())
        }

        root.addView(suggestionBar.rootView)
        root.addView(toneSelector.rootView)
        root.addView(keyboardView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        toneSelector.setSelectedTone(currentTone)
        val text = currentInputConnection?.getTextBeforeCursor(500, 0)?.toString()
        if (!text.isNullOrBlank()) {
            fetchSuggestions()
        }
    }

    private fun fetchSuggestions() {
        val text = currentInputConnection?.getTextBeforeCursor(500, 0)?.toString() ?: return
        if (text.isBlank()) return

        val latestMessage = text.lines().lastOrNull { it.isNotBlank() } ?: text

        scope.launch {
            suggestionBar.showLoading(true)
            val result = withContext(Dispatchers.IO) {
                apiClient.getSuggestions(latestMessage, currentTone)
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
        fetchSuggestions()
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
}
