package com.datingcopilot.keyboard

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
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
            val captureIntent = Intent(this, ScreenshotCaptureService::class.java).apply {
                putExtra(ScreenshotCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenshotCaptureService.EXTRA_RESULT_DATA, data)
                putExtra(ScreenshotCaptureService.EXTRA_CHAT_CONTEXT, chatContext)
            }
            Log.d(TAG, "Starting ScreenshotCaptureService")
            startForegroundService(captureIntent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Aggressively hide keyboard before anything else
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.hide(WindowInsets.Type.ime())
        }
        forceHideKeyboard()
        // Hide keyboard again after layout
        Handler(Looper.getMainLooper()).postDelayed({ forceHideKeyboard() }, 200)

        chatContext = intent?.getStringExtra("chat_context") ?: ""

        val prefs = getSharedPreferences("dating_copilot", MODE_PRIVATE)
        chatContext = chatContext.ifEmpty {
            prefs.getString("rizzse_chat_context", "") ?: ""
        }

        if (savedInstanceState == null) {
            // Wait for keyboard to dismiss before showing MediaProjection dialog
            Handler(Looper.getMainLooper()).postDelayed({
                forceHideKeyboard()
                val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    projectionManager.createScreenCaptureIntent(
                        MediaProjectionConfig.createConfigForDefaultDisplay()
                    )
                } else {
                    projectionManager.createScreenCaptureIntent()
                }
                requestScreenCapture.launch(captureIntent)
            }, 500)
        }
    }

    private fun forceHideKeyboard() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            window?.let { win ->
                imm.hideSoftInputFromWindow(win.decorView.windowToken, 0)
            }
            // Also hide via current focus
            currentFocus?.let { view ->
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        } catch (_: Exception) {}
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
                text = "scroll the chat, then tap Stop in the notification"
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
