package com.datingcopilot.keyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class ScreenshotCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var chatContext: String = ""
    private val handler = Handler(Looper.getMainLooper())

    private val capturedFrames = mutableListOf<Bitmap>()
    private val frameHashes = mutableListOf<Long>()
    private var captureStartTime: Long = 0
    private var frameCount: Long = 0
    private val maxFrames = 5
    private val captureDurationMs = 5000L
    private val frameIntervalMs = 300L
    private val pixelCompareCount = 100
    private val differThreshold = 0.15

    companion object {
        private const val TAG = "ScreenshotCapture"
        private const val CHANNEL_ID = "screenshot_capture"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_CHAT_CONTEXT = "chat_context"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        chatContext = intent?.getStringExtra(EXTRA_CHAT_CONTEXT) ?: ""

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("RizzSe")
            .setContentText("Recording chat...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        handler.postDelayed({
            startMultiFrameCapture(resultCode, data)
        }, 200)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMultiFrameCapture(resultCode: Int, data: Intent) {
        captureStartTime = System.currentTimeMillis()
        frameCount = 0
        capturedFrames.clear()
        frameHashes.clear()

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection = projection
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { cleanupCapture() }
        }, handler)

        val metrics = android.util.DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        display.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ ir ->
            val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            val elapsed = System.currentTimeMillis() - captureStartTime
            if (elapsed > captureDurationMs + 1000) {
                image.close()
                return@setOnImageAvailableListener
            }

            frameCount++
            if (frameCount % 2 != 0L) {
                image.close()
                return@setOnImageAvailableListener
            }

            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val rowPadding = plane.rowStride - plane.pixelStride * width
                val bitmapW = width + rowPadding / plane.pixelStride
                val bitmap = Bitmap.createBitmap(bitmapW, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                bitmap.recycle()

                if (isDuplicateFrame(cropped)) {
                    cropped.recycle()
                } else {
                    val hash = computeFrameHash(cropped)
                    capturedFrames.add(cropped)
                    frameHashes.add(hash)
                    Log.d(TAG, "Captured frame ${capturedFrames.size}, elapsed=${elapsed}ms")
                }

                if (capturedFrames.size >= maxFrames || elapsed >= captureDurationMs) {
                    imageReader?.setOnImageAvailableListener(null, null)
                    finishCapture(width, height)
                }
            } finally {
                image.close()
            }
        }, handler)

        handler.postDelayed({
            if (capturedFrames.isEmpty()) {
                Log.w(TAG, "No frames captured, taking single frame")
                val singleImage = imageReader?.acquireLatestImage()
                if (singleImage != null) {
                    try {
                        val plane = singleImage.planes[0]
                        val buffer = plane.buffer
                        val rowPadding = plane.rowStride - plane.pixelStride * width
                        val bitmap = Bitmap.createBitmap(width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
                        bitmap.copyPixelsFromBuffer(buffer)
                        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        bitmap.recycle()
                        capturedFrames.add(cropped)
                    } finally {
                        singleImage.close()
                    }
                }
                finishCapture(width, height)
            }
        }, captureDurationMs + 500)

        virtualDisplay = projection.createVirtualDisplay(
            "RizzSeCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler
        )
    }

    private fun computeFrameHash(bitmap: Bitmap): Long {
        var hash = 0L
        val w = bitmap.width
        val h = bitmap.height
        val step = maxOf(1, (w * h) / pixelCompareCount)
        val pixels = IntArray(1)
        for (i in 0 until pixelCompareCount) {
            val x = (i * 7 + 3) % w
            val y = (i * 13 + 7) % h
            hash = hash * 31 + bitmap.getPixel(x, y)
        }
        return hash
    }

    private fun isDuplicateFrame(bitmap: Bitmap): Boolean {
        if (frameHashes.isEmpty()) return false
        val hash = computeFrameHash(bitmap)
        for (existing in frameHashes) {
            if (existing == hash) return true
        }
        if (frameHashes.isNotEmpty()) {
            var diffCount = 0
            val w = bitmap.width
            val h = bitmap.height
            for (i in 0 until pixelCompareCount) {
                val x = (i * 7 + 3) % w
                val y = (i * 13 + 7) % h
                val pixel = bitmap.getPixel(x, y)
                val hashVal = frameHashes.last().toInt()
                if (abs((pixel shr 16 and 0xFF) - (hashVal shr 8 and 0xFF)) > 30) {
                    diffCount++
                }
            }
            if (diffCount.toFloat() / pixelCompareCount < differThreshold) {
                return true
            }
        }
        return false
    }

    private fun finishCapture(width: Int, height: Int) {
        cleanupCapture()
        handler.post {
            if (capturedFrames.isEmpty()) {
                Toast.makeText(this, "Failed to capture screenshots", Toast.LENGTH_SHORT).show()
                stopSelf()
                return@post
            }

            val imageUris = mutableListOf<String>()
            for ((i, bitmap) in capturedFrames.withIndex()) {
                val file = File(cacheDir, "kb_ss_${System.currentTimeMillis()}_$i.jpg")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                imageUris.add(file.absolutePath)
                bitmap.recycle()
            }
            capturedFrames.clear()

            val prefs = getSharedPreferences("dating_copilot", Context.MODE_MULTI_PROCESS)
            prefs.edit()
                .putString("pending_screenshot_paths", imageUris.joinToString("|"))
                .putString("pending_chat_context", chatContext)
                .apply()

            val ctx = chatContext.ifEmpty {
                prefs.getString("rizzse_chat_context", "") ?: ""
            }

            val intent = Intent(this, com.datingcopilot.keyboard.chat.ScreenshotAnalysisActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("image_paths", imageUris.toTypedArray())
                putExtra("chat_context", ctx)
                putExtra("persona", prefs.getString("persona", "playful") ?: "playful")
                putExtra("intent", prefs.getString("intent", "keep_going") ?: "keep_going")
                putExtra("platform", com.datingcopilot.keyboard.ChatContextService.getChatPlatform(this@ScreenshotCaptureService))
            }
            startActivity(intent)
            stopSelf()
        }
    }

    private fun cleanupCapture() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        cleanupCapture()
        capturedFrames.forEach { if (!it.isRecycled) it.recycle() }
        capturedFrames.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Screenshot Capture", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}