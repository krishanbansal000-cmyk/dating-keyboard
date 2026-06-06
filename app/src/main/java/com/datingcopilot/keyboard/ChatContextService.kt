package com.datingcopilot.keyboard

import android.content.Context

object ChatContextService {
    fun getChatContext(context: Context): String {
        return context.getSharedPreferences("dating_copilot", Context.MODE_PRIVATE)
            .getString("chat_context", "") ?: ""
    }

    fun getChatPlatform(context: Context): String {
        return context.getSharedPreferences("dating_copilot", Context.MODE_PRIVATE)
            .getString("detected_platform", "whatsapp") ?: "whatsapp"
    }
}
