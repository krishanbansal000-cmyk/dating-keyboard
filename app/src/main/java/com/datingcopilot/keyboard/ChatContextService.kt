package com.datingcopilot.keyboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ChatContextService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("dating_copilot", Context.MODE_PRIVATE)
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 300
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName == null) return
        val pkg = event.packageName.toString()

        // Only process known chat/dating apps
        val chatApps = listOf(
            "com.bumble.app", "com.bumble.auth",
            "com.hinge.app", "com.hinge.auth",
            "com.tinder", "com.tinder.auth",
            "com.instagram.android",
            "com.whatsapp", "com.facebook.orca"
        )
        if (chatApps.none { pkg.startsWith(it) }) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                captureScreenText(event)
            }
        }
    }

    private fun captureScreenText(event: AccessibilityEvent) {
        val root = rootInActiveWindow ?: return
        val text = extractText(root)
        root.recycle()

        if (text.isNotBlank()) {
            // Keep last 2000 chars of context
            val existing = prefs.getString("chat_context", "") ?: ""
            val combined = if (existing.length > 5000) {
                existing.takeLast(3000) + "\n" + text
            } else {
                existing + "\n" + text
            }
            prefs.edit().putString("chat_context", combined.takeLast(5000)).apply()
        }
    }

    private fun extractText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        if (node.text != null) {
            sb.append(node.text)
            sb.append("\n")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(extractText(child))
            child.recycle()
        }
        return sb.toString()
    }

    override fun onInterrupt() {}

    companion object {
        fun getChatContext(context: Context): String {
            return context.getSharedPreferences("dating_copilot", Context.MODE_PRIVATE)
                .getString("chat_context", "") ?: ""
        }
    }
}
