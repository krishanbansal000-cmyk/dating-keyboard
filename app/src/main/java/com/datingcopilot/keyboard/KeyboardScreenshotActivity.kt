package com.datingcopilot.keyboard

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class KeyboardScreenshotActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "KeyboardScreenshot"
    }

    private val projectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var chatContext: String = ""

    private val requestScreenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "MediaProjection resultCode=${result.resultCode}, hasData=${result.data != null}")
        val data: Intent? = result.data
        if (result.resultCode != RESULT_OK || data == null) {
            finish()
        } else {
            showRecordingOverlay()
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Starting ScreenshotCaptureService")
                val captureIntent = Intent(this, ScreenshotCaptureService::class.java).apply {
                    putExtra(ScreenshotCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenshotCaptureService.EXTRA_RESULT_DATA, data)
                    putExtra(ScreenshotCaptureService.EXTRA_CHAT_CONTEXT, chatContext)
                }
                startForegroundService(captureIntent)
                finish()
            }, 1200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.hide(WindowInsets.Type.ime())
        }
        chatContext = intent?.getStringExtra("chat_context") ?: ""

        val prefs = getSharedPreferences("dating_copilot", MODE_PRIVATE)
        chatContext = chatContext.ifEmpty {
            prefs.getString("rizzse_chat_context", "") ?: ""
        }

        if (savedInstanceState == null) {
            Handler(Looper.getMainLooper()).postDelayed({
                requestScreenCapture.launch(projectionManager.createScreenCaptureIntent())
            }, 350)
        }
    }

    private fun showRecordingOverlay() {
        val mosaic = AnimatedMosaicOverlay(this)

        val textColumn = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
            addView(TextView(this@KeyboardScreenshotActivity).apply {
                text = "RizzSe"
                textSize = 28f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xEEFFFFFF.toInt())
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            })
            addView(TextView(this@KeyboardScreenshotActivity).apply {
                text = "recording chat..."
                textSize = 14f
                setTextColor(0xAAFFFFFF.toInt())
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            })
        }

        setContentView(FrameLayout(this).apply {
            addView(mosaic, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(textColumn)
        })
    }
}
