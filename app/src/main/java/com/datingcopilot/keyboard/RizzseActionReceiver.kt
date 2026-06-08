package com.datingcopilot.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class RizzseActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "RizzseActionReceiver"
        const val ACTION_RIZZSE = "com.datingcopilot.keyboard.RIZZSE_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("action") ?: return
        val chatContext = intent.getStringExtra("chat_context") ?: ""
        Log.d(TAG, "Received RizzSe action: $action, context: ${chatContext.take(80)}")

        val prefs = context.getSharedPreferences("dating_copilot", Context.MODE_MULTI_PROCESS)
        prefs.edit()
            .putString("rizzse_pending_action", action)
            .putString("rizzse_chat_context", chatContext)
            .apply()

        val launchIntent = if (action == "screenshot") {
            Intent(context, com.datingcopilot.keyboard.chat.ChatActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(com.datingcopilot.keyboard.chat.ChatActivity.EXTRA_OPEN_IMAGE_PICKER, true)
                putExtra("rizzse_chat_context", chatContext)
            }
        } else {
            Intent(context, com.datingcopilot.keyboard.KeyboardScreenshotActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("chat_context", chatContext)
            }
        }
        context.startActivity(launchIntent)
    }
}
