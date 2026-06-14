package com.datingcopilot.keyboard

import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
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
        val data: Intent? = result.data
        if (result.resultCode != RESULT_OK || data == null) {
            finish()
        } else {
            val captureIntent = Intent(this, ScreenshotCaptureService::class.java).apply {
                putExtra(ScreenshotCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenshotCaptureService.EXTRA_RESULT_DATA, data)
                putExtra(ScreenshotCaptureService.EXTRA_CHAT_CONTEXT, chatContext)
            }
            getSharedPreferences("dating_copilot", MODE_PRIVATE)
                .edit().putBoolean("capture_active", true).remove("capture_error").apply()
            startForegroundService(captureIntent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chatContext = intent?.getStringExtra("chat_context") ?: ""
        val prefs = getSharedPreferences("dating_copilot", MODE_PRIVATE)
        chatContext = chatContext.ifEmpty {
            prefs.getString("rizzse_chat_context", "") ?: ""
        }

        launchScreenCapturePicker()
    }

    private fun launchScreenCapturePicker() {
        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projectionManager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForDefaultDisplay()
            )
        } else {
            projectionManager.createScreenCaptureIntent()
        }
        requestScreenCapture.launch(captureIntent)
    }
}
