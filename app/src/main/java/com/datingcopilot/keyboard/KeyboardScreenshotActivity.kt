package com.datingcopilot.keyboard

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.datingcopilot.keyboard.R
import java.io.File
import java.io.FileOutputStream

class KeyboardScreenshotActivity : AppCompatActivity() {
    private val projectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captured = false

    private val requestScreenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) {
            finishAndShowKeyboard()
        } else {
            moveTaskToBack(true)
            Handler(Looper.getMainLooper()).postDelayed({
                startCapture(result.resultCode, result.data!!)
            }, 800)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildExplanationUI())
    }

    private fun buildExplanationUI(): View {
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((32 * density).toInt(), (48 * density).toInt(), (32 * density).toInt(), (32 * density).toInt())
            setBackgroundColor(resources.getColor(R.color.bg_dark, null))
        }

        // Icon
        root.addView(TextView(this).apply {
            text = "📸"
            textSize = 48f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (20 * density).toInt() }
        })

        // Title
        root.addView(TextView(this).apply {
            text = "Capture your chat"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * density).toInt() }
        })

        // Description
        root.addView(TextView(this).apply {
            text = "RizzSe will take a quick screenshot of your current screen to analyze the conversation and suggest replies."
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            gravity = Gravity.CENTER
            setLineSpacing(4f, 1.0f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (32 * density).toInt() }
        })

        // Note about system dialog
        root.addView(TextView(this).apply {
            text = "Note: Android will show a permission dialog. Tap \"Start now\" to proceed."
            textSize = 12f
            setTextColor(resources.getColor(R.color.text_muted, null))
            gravity = Gravity.CENTER
            setPadding(
                (16 * density).toInt(), (10 * density).toInt(),
                (16 * density).toInt(), (10 * density).toInt()
            )
            val bg = GradientDrawable()
            bg.cornerRadius = 12 * density
            bg.setColor(resources.getColor(R.color.bg_card, null))
            bg.setStroke(1, resources.getColor(R.color.glass_border, null))
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (32 * density).toInt() }
        })

        // Continue button
        root.addView(TextView(this).apply {
            text = "Continue →"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.white, null))
            gravity = Gravity.CENTER
            setPadding(0, (14 * density).toInt(), 0, (14 * density).toInt())
            val bg = GradientDrawable()
            bg.cornerRadius = 24 * density
            bg.setColor(resources.getColor(R.color.accent_violet, null))
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * density).toInt() }
            isClickable = true
            setOnClickListener {
                requestScreenCapture.launch(projectionManager.createScreenCaptureIntent())
            }
        })

        // Cancel
        root.addView(TextView(this).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_muted, null))
            gravity = Gravity.CENTER
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            isClickable = true
            setOnClickListener { finishAndShowKeyboard() }
        })

        return root
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val projection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection = projection
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { cleanupCapture() }
        }, Handler(Looper.getMainLooper()))

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ ir ->
            if (captured) return@setOnImageAvailableListener
            captured = true
            val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val rowPadding = plane.rowStride - plane.pixelStride * width
                val bitmap = Bitmap.createBitmap(width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                val file = File(cacheDir, "kb_ss_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bitmap.recycle()
                cropped.recycle()
                cleanupCapture()
                savePendingScreenshot(file)
            } finally { image.close() }
        }, Handler(Looper.getMainLooper()))

        Handler(Looper.getMainLooper()).postDelayed({
            if (!captured) {
                cleanupCapture()
                Toast.makeText(this, "Screenshot timed out. Try again", Toast.LENGTH_SHORT).show()
                finishAndShowKeyboard()
            }
        }, 3000)

        virtualDisplay = projection.createVirtualDisplay(
            "RizzSeKeyboardCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, Handler(Looper.getMainLooper())
        )
    }

    private fun cleanupCapture() {
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        mediaProjection?.stop(); mediaProjection = null
    }

    override fun onDestroy() { cleanupCapture(); super.onDestroy() }

    private fun savePendingScreenshot(file: File) {
        val prefs = getSharedPreferences("dating_copilot", Context.MODE_PRIVATE)
        prefs.getString("pending_keyboard_screenshot_path", null)?.let { File(it).delete() }
        prefs.edit().putString("pending_keyboard_screenshot_path", file.absolutePath).apply()
        Toast.makeText(this, "Screenshot captured! Open keyboard to continue", Toast.LENGTH_LONG).show()
        finishAndShowKeyboard()
    }

    private fun finishAndShowKeyboard() {
        finish()
        Handler(Looper.getMainLooper()).postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            @Suppress("DEPRECATION")
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
        }, 250)
    }
}
