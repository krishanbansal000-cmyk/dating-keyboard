package com.datingcopilot.keyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.ImageFormat
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
        const val ACTION_STOP_CAPTURE = "com.datingcopilot.keyboard.ACTION_STOP_CAPTURE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        if (intent?.action == ACTION_STOP_CAPTURE) {
            requestStop()
            return START_NOT_STICKY
        }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Scroll the chat, then tap Stop"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Scroll the chat, then tap Stop"))
        }

        handler.postDelayed({
            startMultiFrameCapture(resultCode, data)
        }, 200)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMultiFrameCapture(resultCode: Int, data: Intent) {
        captureStartTime = System.currentTimeMillis()
        lastUniqueFrameTime = captureStartTime
        lastProcessedFrameTime = captureStartTime - frameIntervalMs
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

        Log.d(TAG, "Capture metrics raw=${rawWidth}x$rawHeight output=${width}x$height density=$density")

        val reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
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
        Log.d(TAG, "Virtual display created=${virtualDisplay != null}, size=${width}x$height")

        reader.setOnImageAvailableListener({
            val next = try { it.acquireNextImage() } catch (e: Exception) { null }
            if (next != null) {
                val toDrop = synchronized(pendingImageLock) {
                    val old = pendingImage
                    pendingImage = next
                    old
                }
                if (toDrop != null) {
                    try { toDrop.close() } catch (_: Exception) {}
                }
            }
        }, handler)

        handler.post(processingLoopRunnable)

        handler.postDelayed({
            if (captureFinished) return@postDelayed
            if (capturedFrames.isEmpty()) {
                Log.w(TAG, "No frames captured in window")
            }
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

            if (image.planes.size < 3) {
                Log.w(TAG, "Image planes < 3, format=${image.format}")
                return
            }
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            val yRowStride = yPlane.rowStride
            val uRowStride = uPlane.rowStride
            val vRowStride = vPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            val uPixelStride = uPlane.pixelStride
            val vPixelStride = vPlane.pixelStride

            val argb = IntArray(width * height)
            for (row in 0 until height) {
                for (col in 0 until width) {
                    val y = (yBuffer.get(row * yRowStride + col * yPixelStride).toInt() and 0xFF)
                    val uOffset = (row / 2) * uRowStride + (col / 2) * uPixelStride
                    val vOffset = (row / 2) * vRowStride + (col / 2) * vPixelStride
                    val u = (uBuffer.get(uOffset).toInt() and 0xFF) - 128
                    val v = (vBuffer.get(vOffset).toInt() and 0xFF) - 128
                    val r = (y + 1.370705f * v).toInt().coerceIn(0, 255)
                    val g = (y - 0.3376335f * u - 0.698001f * v).toInt().coerceIn(0, 255)
                    val b = (y + 1.732446f * u).toInt().coerceIn(0, 255)
                    argb[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            val bitmap = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)

            if (isDuplicateFrame(bitmap)) {
                bitmap.recycle()
            } else {
                val hash = computeFrameHash(bitmap)
                capturedFrames.add(bitmap)
                frameHashes.add(hash)
                frameSamples.add(sampleFrame(bitmap))
                lastUniqueFrameTime = System.currentTimeMillis()
                Log.d(TAG, "Captured frame ${capturedFrames.size}, elapsed=${elapsed}ms")
            }

            val hasSettled = capturedFrames.isNotEmpty() &&
                capturedFrames.size >= 2 &&
                elapsed >= minCaptureDurationMs &&
                System.currentTimeMillis() - lastUniqueFrameTime >= stableStopMs
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
            val img = synchronized(pendingImageLock) {
                val cur = pendingImage
                pendingImage = null
                cur
            }
            if (img != null && captureWidth > 0) {
                processImage(img, captureWidth, captureHeight)
            }
            if (captureFinished) return
            handler.postDelayed(this, frameIntervalMs)
        }
    }

    private fun requestStop() {
        if (captureFinished) {
            stopSelf()
            return
        }
        if (captureWidth > 0 && captureHeight > 0 && capturedFrames.isNotEmpty()) {
            finishCapture(captureWidth, captureHeight)
        } else {
            stopSelf()
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, ScreenshotCaptureService::class.java).apply {
            action = ACTION_STOP_CAPTURE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2001,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("RizzSe long shot")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun computeFrameHash(bitmap: Bitmap): Long {
        var hash = 0L
        val w = bitmap.width
        val h = bitmap.height
        for (i in 0 until pixelCompareCount) {
            val x = (i * 7 + 3) % w
            val y = (i * 13 + 7) % h
            hash = hash * 31 + bitmap.getPixel(x, y)
        }
        return hash
    }

    private fun sampleFrame(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val top = (h * 0.14f).toInt()
        val bottom = (h * 0.84f).toInt().coerceAtLeast(top + 1)
        return IntArray(pixelCompareCount) { i ->
            val x = (i * 37 + 17) % w
            val y = top + ((i * 53 + 29) % (bottom - top))
            bitmap.getPixel(x, y)
        }
    }

    private fun isDuplicateFrame(bitmap: Bitmap): Boolean {
        if (frameHashes.isEmpty()) return false
        val hash = computeFrameHash(bitmap)
        for (existing in frameHashes) {
            if (existing == hash) return true
        }
        val current = sampleFrame(bitmap)
        val previous = frameSamples.lastOrNull() ?: return false
        var changed = 0
        for (i in current.indices) {
            val a = current[i]
            val b = previous[i]
            val dr = abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF))
            val dg = abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF))
            val db = abs((a and 0xFF) - (b and 0xFF))
            if ((dr + dg + db) / 3 > 22) changed++
        }
        return changed.toFloat() / current.size < differThreshold
    }

    private fun finishCapture(width: Int, height: Int) {
        if (captureFinished) return
        captureFinished = true
        cleanupCapture()
        handler.post {
            if (capturedFrames.isEmpty()) {
                Log.w(TAG, "Finishing capture with no frames")
                Toast.makeText(this, "Failed to capture screenshots", Toast.LENGTH_SHORT).show()
                stopSelf()
                return@post
            }

            val stitcher = LongScreenshotStitcher()
            val stitched = stitcher.stitch(capturedFrames)
            if (stitched != null) {
                Log.d(TAG, "Stitched image ${stitched.width}x${stitched.height} from ${capturedFrames.size} frames")
            }
            capturedFrames.forEach { if (!it.isRecycled) it.recycle() }
            capturedFrames.clear()

            if (stitched == null) {
                Toast.makeText(this, "Failed to stitch screenshots", Toast.LENGTH_SHORT).show()
                stopSelf()
                return@post
            }

            val file = File(cacheDir, "kb_long_ss_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { stitched.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            stitched.recycle()

            val prefs = getSharedPreferences("dating_copilot", Context.MODE_MULTI_PROCESS)
            prefs.edit()
                .putString("pending_screenshot_paths", file.absolutePath)
                .putString("pending_chat_context", chatContext)
                .apply()

            val ctx = chatContext.ifEmpty {
                prefs.getString("rizzse_chat_context", "") ?: ""
            }

            val intent = Intent(this, com.datingcopilot.keyboard.chat.ScreenshotAnalysisActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                data = Uri.fromFile(file)
                putExtra("image_paths", arrayOf(file.absolutePath))
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
