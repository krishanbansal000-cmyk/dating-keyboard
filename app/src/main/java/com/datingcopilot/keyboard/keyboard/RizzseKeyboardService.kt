package com.datingcopilot.keyboard.keyboard

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RizzseKeyboardService : InputMethodService(), KeyboardActionCallback {

    data class SuggestionOption(val text: String, val confidence: Int = 90, val persona: String = "playful")

    companion object {
        private const val PREFS_NAME = "dating_copilot"
        private const val KEY_SUGGESTIONS = "pending_keyboard_suggestions"
        private const val KEY_CHAT_CONTEXT = "chat_context"
        private const val SUGGESTION_POLL_INTERVAL = 2000L
        private const val BACKSPACE_INITIAL_DELAY = 400L
        private const val BACKSPACE_REPEAT_INTERVAL = 100L
        private const val ACTION_RIZZSE = "com.datingcopilot.keyboard.RIZZSE_ACTION"
        private const val SHIFT_OFF = 0
        private const val SHIFT_ON = 1
        private const val SHIFT_LOCKED = 2
    }

    private lateinit var keyboardView: RizzseKeyboardView
    private lateinit var prefs: android.content.SharedPreferences
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var shiftState = SHIFT_OFF
    private var isSymbolState = false
    private var lastSuggestionsJson: String? = null
    private var currentEditorInfo: EditorInfo? = null
    private var isBackspaceHeld = false
    private var backspaceRunnable: Runnable? = null

    private val suggestionPollRunnable = object : Runnable {
        override fun run() {
            checkForSuggestions()
            mainHandler.postDelayed(this, SUGGESTION_POLL_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS)
    }

    override fun onCreateInputView(): View {
        keyboardView = RizzseKeyboardView(this, this)
        return keyboardView
    }

    override fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        currentEditorInfo = editorInfo
        shiftState = SHIFT_OFF
        isSymbolState = false
        keyboardView.setShiftState(false)
        keyboardView.setSymbolState(false)

        val enterLabel = getEnterKeyLabel(editorInfo)
        keyboardView.setEnterKeyLabel(enterLabel)

        startPolling()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        currentInputConnection?.finishComposingText()
        stopPolling()
        stopBackspaceRepeat()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        stopPolling()
        stopBackspaceRepeat()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onKeyPress(key: String, isText: Boolean) {
        val ic = currentInputConnection ?: return
        if (isText) {
            ic.commitText(key, 1)
        }
    }

    override fun onBackspace() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    override fun onShift() {
        shiftState = when (shiftState) {
            SHIFT_OFF -> SHIFT_ON
            SHIFT_ON -> SHIFT_LOCKED
            SHIFT_LOCKED -> SHIFT_OFF
            else -> SHIFT_OFF
        }
        keyboardView.setShiftState(shiftState == SHIFT_ON || shiftState == SHIFT_LOCKED)
    }

    override fun onSymbolToggle() {
        isSymbolState = !isSymbolState
        keyboardView.setSymbolState(isSymbolState)
    }

    override fun onEnter() {
        val info = currentEditorInfo ?: run {
            currentInputConnection?.commitText("\n", 1)
            return
        }
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        if (action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.commitText("\n", 1)
        } else {
            currentInputConnection?.performEditorAction(action)
        }
    }

    override fun onSpace() {
        currentInputConnection?.commitText(" ", 1)
    }

    override fun onSuggestionSelected(text: String) {
        currentInputConnection?.finishComposingText()
        currentInputConnection?.commitText(text, 1)
    }

    override fun onRizzseAction(action: String) {
        when (action) {
            "screenshot", "record" -> {
                val chatContext = prefs.getString(KEY_CHAT_CONTEXT, "") ?: ""
                val intent = Intent(ACTION_RIZZSE).apply {
                    putExtra("action", action)
                    putExtra("chat_context", chatContext)
                }
                sendBroadcast(intent)
            }
            "magic" -> {
                val json = prefs.getString(KEY_SUGGESTIONS, null) ?: return
                try {
                    val type = object : TypeToken<List<SuggestionOption>>() {}.type
                    val suggestions: List<SuggestionOption> = gson.fromJson(json, type)
                    if (suggestions.isNotEmpty()) {
                        val text = suggestions.first().text
                        currentInputConnection?.finishComposingText()
                        currentInputConnection?.commitText(text, 1)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun startBackspaceRepeat() {
        if (isBackspaceHeld) return
        isBackspaceHeld = true
        backspaceRunnable = Runnable {
            onBackspace()
            if (isBackspaceHeld) {
                mainHandler.postDelayed(backspaceRunnable!!, BACKSPACE_REPEAT_INTERVAL)
            }
        }
        mainHandler.postDelayed(backspaceRunnable!!, BACKSPACE_INITIAL_DELAY)
    }

    fun stopBackspaceRepeat() {
        isBackspaceHeld = false
        backspaceRunnable?.let { mainHandler.removeCallbacks(it) }
        backspaceRunnable = null
    }

    private fun startPolling() {
        mainHandler.removeCallbacks(suggestionPollRunnable)
        mainHandler.post(suggestionPollRunnable)
    }

    private fun stopPolling() {
        mainHandler.removeCallbacks(suggestionPollRunnable)
    }

    private fun checkForSuggestions() {
        val json = prefs.getString(KEY_SUGGESTIONS, null)
        if (json == null) {
            if (lastSuggestionsJson != null) {
                lastSuggestionsJson = null
                keyboardView.setSuggestions(emptyList())
            }
            return
        }
        if (json == lastSuggestionsJson) return
        lastSuggestionsJson = json
        try {
            val type = object : TypeToken<List<SuggestionOption>>() {}.type
            val suggestions: List<SuggestionOption> = gson.fromJson(json, type)
            val texts = suggestions.map { it.text }
            keyboardView.setSuggestions(texts)
        } catch (_: Exception) {
        }
    }

    private fun getEnterKeyLabel(editorInfo: EditorInfo): String? {
        val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        return when (action) {
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_GO -> "Go"
            else -> null
        }
    }
}

interface KeyboardActionCallback {
    fun onKeyPress(key: String, isText: Boolean)
    fun onBackspace()
    fun onShift()
    fun onSymbolToggle()
    fun onEnter()
    fun onSpace()
    fun onSuggestionSelected(text: String)
    fun onRizzseAction(action: String)
}
