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

        if (action == "open_app") {
            val lastScreenshotPath = intent.getStringExtra("last_screenshot_path") ?: ""
            val lastChatContext = intent.getStringExtra("last_chat_context") ?: ""
            val lastPersona = intent.getStringExtra("last_persona") ?: "playful"
            val lastIntent = intent.getStringExtra("last_intent") ?: "keep_going"
            
            if (lastScreenshotPath.isNotEmpty() && java.io.File(lastScreenshotPath).exists()) {
                // Open previous analysis
                val analysisIntent = Intent(context, com.datingcopilot.keyboard.chat.ScreenshotAnalysisActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("image_paths", arrayOf(lastScreenshotPath))
                    putExtra("chat_context", lastChatContext)
                    putExtra("persona", lastPersona)
                    putExtra("intent", lastIntent)
                    putExtra("platform", "whatsapp")
                }
                context.startActivity(analysisIntent)
            } else {
                val appIntent = Intent(context, com.datingcopilot.keyboard.chat.ChatActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(appIntent)
            }
            return
        }

        val launchIntent = Intent(context, com.datingcopilot.keyboard.KeyboardScreenshotActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("chat_context", chatContext)
        }
        context.startActivity(launchIntent)
    }
}
