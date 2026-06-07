package com.datingcopilot.keyboard

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class KeyboardScreenshotActivity : AppCompatActivity() {
    private val projectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    private var launchedCapturePrompt = false
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captured = false

    private val requestScreenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) {
            finish()
        } else {
            moveTaskToBack(true)
            Handler(Looper.getMainLooper()).postDelayed({
                startCapture(result.resultCode, result.data!!)
            }, 800)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !launchedCapturePrompt) {
            launchedCapturePrompt = true
            requestScreenCapture.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val projection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection = projection
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                cleanupCapture()
            }
        }, Handler(Looper.getMainLooper()))

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ imageReader ->
            if (captured) return@setOnImageAvailableListener
            captured = true

            val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                val file = File(cacheDir, "keyboard_capture_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    cropped.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
                cropped.recycle()
                cleanupCapture()
                savePendingScreenshot(file)
            } finally {
                image.close()
            }
        }, Handler(Looper.getMainLooper()))

        virtualDisplay = projection.createVirtualDisplay(
            "RizzSeKeyboardCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            Handler(Looper.getMainLooper())
        )
    }

    private fun cleanupCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        val projection = mediaProjection
        mediaProjection = null
        projection?.stop()
    }

    override fun onDestroy() {
        cleanupCapture()
        super.onDestroy()
    }

    private fun savePendingScreenshot(file: File) {
        val prefs = getSharedPreferences("dating_copilot", Context.MODE_PRIVATE)
        prefs.getString("pending_keyboard_screenshot_path", null)?.let { oldPath ->
            File(oldPath).delete()
        }
        prefs.edit()
            .putString("pending_keyboard_screenshot_path", file.absolutePath)
            .apply()
        Toast.makeText(this, "Screenshot captured. Pick tone in RizzSe keyboard", Toast.LENGTH_LONG).show()
        finish()
    }
}
