// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import helium314.keyboard.latin.utils.Log

class RizzseSuggestionsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val suggestions = intent.getStringExtra(EXTRA_PENDING_SUGGESTIONS) ?: return
        val contextText = intent.getStringExtra(EXTRA_PENDING_CONTEXT) ?: ""
        Log.d(TAG, "Received RizzSe suggestions: ${suggestions.take(60)}")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_SUGGESTIONS, suggestions)
            .putString(KEY_PENDING_CONTEXT, contextText)
            .commit()
    }

    companion object {
        const val ACTION_RIZZSE_SUGGESTIONS = "com.datingcopilot.keyboard.RIZZSE_SUGGESTIONS"
        const val EXTRA_PENDING_SUGGESTIONS = "pending_keyboard_suggestions"
        const val EXTRA_PENDING_CONTEXT = "pending_keyboard_context"
        private const val TAG = "RizzseSuggestionsRx"
        private const val PREFS_NAME = "dating_copilot"
        private const val KEY_PENDING_SUGGESTIONS = "pending_keyboard_suggestions"
        private const val KEY_PENDING_CONTEXT = "pending_keyboard_context"
    }
}
