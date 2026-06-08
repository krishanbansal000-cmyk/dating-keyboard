package com.datingcopilot.keyboard

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.TextView
import android.widget.FrameLayout
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class KeyboardScreenshotActivity : AppCompatActivity() {
    private val projectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var chatContext: String = ""

    private val requestScreenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) {
            finish()
        } else {
            val intent = Intent(this, ScreenshotCaptureService::class.java).apply {
                putExtra(ScreenshotCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenshotCaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenshotCaptureService.EXTRA_CHAT_CONTEXT, chatContext)
            }
            startForegroundService(intent)
            showCountdownOverlay()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatContext = intent?.getStringExtra("chat_context") ?: ""

        val prefs = getSharedPreferences("dating_copilot", MODE_PRIVATE)
        chatContext = chatContext.ifEmpty {
            prefs.getString("rizzse_chat_context", "") ?: ""
        }

        if (savedInstanceState == null) {
            requestScreenCapture.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    private fun showCountdownOverlay() {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0x99000000.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val pill = TextView(this).apply {
            text = "⬤ RizzSe · Starting capture..."
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(24), dp(12), dp(24), dp(12))
            gravity = Gravity.CENTER
            val bg = GradientDrawable()
            bg.cornerRadius = dp(28).toFloat()
            bg.setColor(0xFF7C3AED.toInt())
            background = bg
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        overlay.addView(pill)
        setContentView(overlay)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish() }, 1500)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}