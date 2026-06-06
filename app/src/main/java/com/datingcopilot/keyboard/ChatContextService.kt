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
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 500
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val chatApps = listOf("bumble", "hinge", "tinder", "instagram", "whatsapp", "facebook.orca")
        if (chatApps.none { pkg.contains(it, ignoreCase = true) }) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val root = rootInActiveWindow ?: return
            val text = extractText(root)
            root.recycle()
            if (text.isNotBlank()) {
                prefs.edit()
                    .putString("chat_context", text.takeLast(3000))
                    .putString("detected_platform", detectPlatform(pkg))
                    .apply()
            }
        }
    }

    private fun detectPlatform(packageName: String): String {
        return when {
            packageName.contains("whatsapp", ignoreCase = true) -> "whatsapp"
            packageName.contains("instagram", ignoreCase = true) -> "instagram"
            packageName.contains("hinge", ignoreCase = true) -> "hinge"
            packageName.contains("bumble", ignoreCase = true) -> "bumble"
            packageName.contains("tinder", ignoreCase = true) -> "tinder"
            packageName.contains("facebook.orca", ignoreCase = true) -> "instagram"
            else -> "whatsapp"
        }
    }

    private fun extractText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        if (node.text != null) {
            val t = node.text.toString().trim()
            // Skip noise: timestamps, read receipts, UI labels
            if (t.isNotEmpty() && !isNoise(t)) {
                sb.append(t).append('\n')
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(extractText(child))
        }
        return sb.toString()
    }

    private fun isNoise(text: String): Boolean {
        if (text.length < 3) return true
        if (text.startsWith("Today") || text.startsWith("Yesterday")) return true
        if (text.matches(Regex("^\\d{1,2}:\\d{2}.*"))) return true
        if (text.matches(Regex("^[A-Z][a-z]{2,8},?\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec).*"))) return true
        if (text in setOf("Online", "Typing...", "typing...", "Seen", "Delivered", "Read", "Sent")) return true
        if (text.matches(Regex("^✓✓?.*"))) return true
        if (text.matches(Regex("^Read \\d"))) return true
        return false
    }

    override fun onInterrupt() {}

    companion object {
        fun getChatContext(context: Context): String {
            return context.getSharedPreferences("dating_copilot", Context.MODE_PRIVATE)
                .getString("chat_context", "") ?: ""
        }

        fun getChatPlatform(context: Context): String {
            return context.getSharedPreferences("dating_copilot", Context.MODE_PRIVATE)
                .getString("detected_platform", "whatsapp") ?: "whatsapp"
        }
    }
}
