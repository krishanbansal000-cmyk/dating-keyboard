package com.datingcopilot.keyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ScreenshotCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var chatContext: String = ""
    private val handler = Handler(Looper.getMainLooper())

    private val capturedFrames = mutableListOf<Bitmap>()
    private val frameHashes = mutableListOf<Long>()
    private val frameSamples = mutableListOf<IntArray>()
    private var captureStartTime: Long = 0
    private var lastUniqueFrameTime: Long = 0
    private var lastProcessedFrameTime: Long = 0
    private var frameCount: Long = 0
    private var captureFinished = false
    private val maxFrames = 8
    private val captureDurationMs = 8000L
    private val minCaptureDurationMs = 1800L
    private val stableStopMs = 1400L
    private val frameIntervalMs = 300L
    private val pixelCompareCount = 100
    private val differThreshold = 0.08

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
        Log.d(TAG, "onStartCommand")
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

        // Minimal notification required by Android 14+ for MediaProjection
        startForeground(NOTIFICATION_ID, Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("RizzSe")
            .setContentText("Recording")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build())

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

            if (elapsed - lastProcessedFrameTime < frameIntervalMs) {
                image.close()
                return@setOnImageAvailableListener
            }
            lastProcessedFrameTime = elapsed

            frameCount++

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
                    frameSamples.add(sampleFrame(cropped))
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
                image.close()
            }
        }, handler)

        handler.postDelayed({
            if (captureFinished) {
                return@postDelayed
            }
            if (capturedFrames.isNotEmpty()) {
                finishCapture(width, height)
                return@postDelayed
            }
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

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "RizzSeCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler
        )
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
        if (changed.toFloat() / current.size < differThreshold) {
            return true
        }
        return false
    }

    private fun finishCapture(width: Int, height: Int) {
        if (captureFinished) {
            return
        }
        captureFinished = true
        cleanupCapture()
        handler.post {
            if (capturedFrames.isEmpty()) {
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

    private fun stitchFrames(frames: List<Bitmap>): Bitmap? {
        val stitcher = LongScreenshotStitcher()
        return stitcher.stitch(frames)
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
