package com.datingcopilot.keyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread
import kotlin.math.abs

class ScreenshotCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var chatContext: String = ""
    private var captureWidth: Int = 0
    private var captureHeight: Int = 0
    private val handler = Handler(Looper.getMainLooper())
    private val pendingImageLock = Any()
    private var pendingImage: Image? = null

    private val capturedFrames = mutableListOf<Bitmap>()
    private val frameHashes = mutableListOf<Long>()
    private val frameSamples = mutableListOf<IntArray>()
    private var captureStartTime: Long = 0
    private var lastUniqueFrameTime: Long = 0
    private var lastProcessedFrameTime: Long = 0
    private var frameCount: Long = 0
    private var captureFinished = false

    private val maxFrames = 8
    private val captureDurationMs = 15000L
    private val minCaptureDurationMs = 1000L
    private val stableStopMs = 1400L
    private val frameIntervalMs = 300L
    private val pixelCompareCount = 100
    private val differThreshold = 0.08f

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("screenshot_capture", "Capture", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false); enableVibration(false); setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        val prefs = getSharedPreferences("dating_copilot", MODE_PRIVATE)
        prefs.edit()
            .putBoolean("capture_active", true)
            .putLong("capture_start_time", System.currentTimeMillis())
            .apply()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (data == null) {
            Log.w(TAG, "Missing MediaProjection data")
            stopSelf()
            return START_NOT_STICKY
        }
        chatContext = intent?.getStringExtra(EXTRA_CHAT_CONTEXT) ?: ""

        // Foreground service notification is required by Android 14+ for MediaProjection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1001,
                Notification.Builder(this, "screenshot_capture")
                    .setContentTitle("")
                    .setContentText("")
                    .setSmallIcon(android.R.drawable.presence_video_online)
                    .setOngoing(true)
                    .build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        }

        // Keep trying to show keyboard with STOP button - IME may restart after projection dialog
        val showImeIntent = Intent("com.datingcopilot.keyboard.SHOW_IME").apply { setPackage(packageName) }
        sendBroadcast(showImeIntent)
        handler.postDelayed({ sendBroadcast(showImeIntent) }, 1000)
        handler.postDelayed({ sendBroadcast(showImeIntent) }, 3000)

        handler.postDelayed({
            startMultiFrameCapture(resultCode, data)
        }, 200)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildMinimalNotification(): Notification {
        val channel = NotificationManager.IMPORTANCE_MIN
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("")
            .setContentText("")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()
    }

    private fun startMultiFrameCapture(resultCode: Int, data: Intent) {
        captureStartTime = System.currentTimeMillis()
        lastUniqueFrameTime = captureStartTime
        lastProcessedFrameTime = -frameIntervalMs
        captureWidth = 0
        captureHeight = 0
        frameCount = 0
        capturedFrames.clear()
        frameHashes.clear()
        frameSamples.clear()
        captureFinished = false

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { cleanupCapture() }
        }, handler)

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val rawWidth = bounds.width().coerceAtLeast(1)
        val rawHeight = bounds.height().coerceAtLeast(1)
        val scale = (1080f / rawWidth).coerceAtMost(1f)
        val width = (rawWidth * scale).toInt().coerceAtLeast(1)
        val height = (rawHeight * scale).toInt().coerceAtLeast(1)
        val density = resources.configuration.densityDpi
        captureWidth = width
        captureHeight = height

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "RizzSeCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            object : VirtualDisplay.Callback() {
                override fun onStopped() { Log.w(TAG, "Virtual display stopped") }
            },
            handler
        )

        reader.setOnImageAvailableListener({
            val next = try { it.acquireNextImage() } catch (_: Exception) { null }
            if (next != null) {
                val toDrop = synchronized(pendingImageLock) {
                    val old = pendingImage
                    pendingImage = next
                    old
                }
                if (toDrop != null) try { toDrop.close() } catch (_: Exception) {}
            }
        }, handler)

        handler.post(processingLoopRunnable)

        handler.postDelayed({
            if (captureFinished) return@postDelayed
            if (capturedFrames.isEmpty()) Log.w(TAG, "No frames captured")
            finishCapture(width, height)
        }, captureDurationMs + 500)
    }

    private fun processImage(image: Image, width: Int, height: Int) {
        try {
            val elapsed = System.currentTimeMillis() - captureStartTime
            if (elapsed > captureDurationMs + 1000) return
            if (elapsed - lastProcessedFrameTime < frameIntervalMs) return
            lastProcessedFrameTime = elapsed
            frameCount++

            if (image.planes.isEmpty()) return
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            if (croppedBitmap !== bitmap) bitmap.recycle()

            if (isDuplicateFrame(croppedBitmap)) {
                croppedBitmap.recycle()
            } else {
                val hash = computeFrameHash(croppedBitmap)
                capturedFrames.add(croppedBitmap)
                frameHashes.add(hash)
                frameSamples.add(sampleFrame(croppedBitmap))
                lastUniqueFrameTime = System.currentTimeMillis()
                Log.d(TAG, "Captured frame ${capturedFrames.size}")
            }

            val hasSettled = capturedFrames.isNotEmpty() && capturedFrames.size >= 2 &&
                elapsed >= minCaptureDurationMs && System.currentTimeMillis() - lastUniqueFrameTime >= stableStopMs
            if (capturedFrames.size >= maxFrames || elapsed >= captureDurationMs || hasSettled) {
                imageReader?.setOnImageAvailableListener(null, null)
                finishCapture(width, height)
            }
        } finally {
            try { image.close() } catch (_: Exception) {}
        }
    }

    private val processingLoopRunnable = object : Runnable {
        override fun run() {
            if (captureFinished) return
            // Check if user tapped STOP - stop early
            if (!getSharedPreferences("dating_copilot", MODE_PRIVATE).getBoolean("capture_active", true)) {
                requestStop()
                return
            }
            val img = synchronized(pendingImageLock) {
                val cur = pendingImage; pendingImage = null; cur
            }
            if (img != null && captureWidth > 0) processImage(img, captureWidth, captureHeight)
            if (captureFinished) return
            handler.postDelayed(this, frameIntervalMs)
        }
    }

    private fun requestStop() {
        if (captureFinished) { stopSelf(); return }
        if (captureWidth > 0 && captureHeight > 0 && capturedFrames.isNotEmpty()) {
            finishCapture(captureWidth, captureHeight)
        } else stopSelf()
    }

    private fun computeFrameHash(bitmap: Bitmap): Long {
        var hash = 0L
        val w = bitmap.width; val h = bitmap.height
        for (i in 0 until pixelCompareCount) {
            hash = hash * 31 + bitmap.getPixel((i * 7 + 3) % w, (i * 13 + 7) % h)
        }
        return hash
    }

    private fun sampleFrame(bitmap: Bitmap): IntArray {
        val w = bitmap.width; val h = bitmap.height
        val top = (h * 0.14f).toInt(); val bottom = (h * 0.84f).toInt().coerceAtLeast(top + 1)
        return IntArray(pixelCompareCount) { i ->
            bitmap.getPixel((i * 37 + 17) % w, top + ((i * 53 + 29) % (bottom - top)))
        }
    }

    private fun isDuplicateFrame(bitmap: Bitmap): Boolean {
        if (frameHashes.isEmpty()) return false
        val hash = computeFrameHash(bitmap)
        for (existing in frameHashes) { if (existing == hash) return true }
        val current = sampleFrame(bitmap)
        val previous = frameSamples.lastOrNull() ?: return false
        var changed = 0
        for (i in current.indices) {
            val dr = abs((current[i] shr 16 and 0xFF) - (previous[i] shr 16 and 0xFF))
            val dg = abs((current[i] shr 8 and 0xFF) - (previous[i] shr 8 and 0xFF))
            val db = abs((current[i] and 0xFF) - (previous[i] and 0xFF))
            if ((dr + dg + db) / 3 > 22) changed++
        }
        return changed.toFloat() / current.size < differThreshold
    }

    private fun finishCapture(width: Int, height: Int) {
        if (captureFinished) return
        captureFinished = true
        cleanupCapture()

        val frames = ArrayList(capturedFrames)
        capturedFrames.clear()

        val prefs = getSharedPreferences("dating_copilot", MODE_PRIVATE)
        prefs.edit()
            .putBoolean("capture_active", false)
            .putBoolean("show_analyzing", true)
            .apply()
        sendBroadcast(Intent("com.datingcopilot.keyboard.CAPTURE_STOPPED").apply { setPackage(packageName) })

        // Save first frame immediately for preview
        val firstFrame = frames.firstOrNull()
        val quickFile = if (firstFrame != null && !firstFrame.isRecycled) {
            File(cacheDir, "kb_quick_${System.currentTimeMillis()}.jpg")
        } else null
        if (quickFile != null && firstFrame != null) {
            try { FileOutputStream(quickFile).use { firstFrame.compress(Bitmap.CompressFormat.JPEG, 70, it) } } catch (_: Exception) {}
        }

        val ctx = chatContext.ifEmpty { prefs.getString("rizzse_chat_context", "") ?: "" }

        // Don't open analysis activity - show results in keyboard suggestion strip
        
        // Stitch and analyze in background
        thread(name = "RizzSeStitch") {
            try {
                val stitcher = LongScreenshotStitcher()
                val stitched = stitcher.stitchFast(frames)
                frames.forEach { if (!it.isRecycled) it.recycle() }
                if (stitched == null) { stopSelf(); return@thread }
                
                val file = File(cacheDir, "kb_long_ss_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { stitched.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                stitched.recycle()
                Log.d(TAG, "Stitched: ${file.absolutePath}")
                
                prefs.edit()
                    .putString("pending_screenshot_paths", file.absolutePath)
                    .putString("pending_chat_context", chatContext)
                    .apply()

                val persona = prefs.getString("persona", "playful") ?: "playful"
                val intentType = prefs.getString("intent", "keep_going") ?: "keep_going"
                val platform = ChatContextService.getChatPlatform(this@ScreenshotCaptureService)
                val resolvedContext = chatContext.ifEmpty {
                    prefs.getString("rizzse_chat_context", "") ?: ""
                }

                val response = ApiClient(this@ScreenshotCaptureService).uploadScreenshot(
                    android.net.Uri.fromFile(file),
                    persona,
                    this@ScreenshotCaptureService,
                    intentType,
                    platform
                )
                val suggestions = response?.suggestions.orEmpty()
                if (suggestions.isNotEmpty()) {
                    val suggestionsJson = com.google.gson.Gson().toJson(suggestions)
                    prefs.edit()
                        .putString("pending_keyboard_suggestions", suggestionsJson)
                        .putString("pending_keyboard_context", resolvedContext)
                        .apply()
                    Log.d(TAG, "Suggestions saved to keyboard: ${suggestions.size}")
                    // Notify keyboard to show suggestions
                    sendBroadcast(Intent("com.datingcopilot.keyboard.SHOW_IME").apply { setPackage(packageName) })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed: ${e.message}", e)
            }
            stopSelf()
        }
    }

    private fun cleanupCapture() {
        try { virtualDisplay?.release() } catch (_: Exception) {}; virtualDisplay = null
        try { imageReader?.close() } catch (_: Exception) {}; imageReader = null
        try { mediaProjection?.stop() } catch (_: Exception) {}; mediaProjection = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        cleanupCapture()
        capturedFrames.forEach { if (!it.isRecycled) it.recycle() }
        capturedFrames.clear()
        getSharedPreferences("dating_copilot", MODE_PRIVATE).edit().putBoolean("capture_active", false).apply()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
