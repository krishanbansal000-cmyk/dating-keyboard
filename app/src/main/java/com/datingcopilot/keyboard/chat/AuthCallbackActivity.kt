package com.datingcopilot.keyboard.chat

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AuthCallbackActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = intent?.data?.getQueryParameter("token")
        if (!token.isNullOrBlank()) {
            getSharedPreferences("dating_copilot", MODE_PRIVATE)
                .edit()
                .putString("github_token", token)
                .apply()
        }

        val intent = Intent(this, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("auth_success", true)
        }
        startActivity(intent)
        finish()
    }
}
