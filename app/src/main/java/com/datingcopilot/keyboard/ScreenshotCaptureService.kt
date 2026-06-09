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
    private var frameCount: Long = 0
    private var captureFinished = false
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
                    frameSamples.add(sampleFrame(cropped))
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
            if (captureFinished) {
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

            val stitched = stitchFrames(capturedFrames)
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
        if (frames.isEmpty()) return null
        if (frames.size == 1) return frames[0].copy(Bitmap.Config.ARGB_8888, false)

        val width = frames[0].width
        val height = frames[0].height
        val topChrome = (height * 0.14f).toInt()
        val bottomChrome = (height * 0.16f).toInt()
        val contentTop = topChrome.coerceIn(0, height / 2)
        val contentBottom = (height - bottomChrome).coerceIn(contentTop + 1, height)
        val contentHeight = contentBottom - contentTop
        val features = frames.map { readLuminanceFeatures(it, contentTop, contentBottom) }
        val appendSegments = mutableListOf(Rect(0, 0, width, height))

        for (i in 1 until frames.size) {
            val shift = findScrollShift(features[i - 1], features[i], contentHeight)
            if (shift <= 0) {
                Log.d(TAG, "Skipping frame $i while stitching; no reliable scroll shift")
                appendSegments.add(Rect(0, 0, width, height))
                continue
            }

            val srcTop = (contentBottom - shift).coerceIn(contentTop, contentBottom - 1)
            val srcBottom = contentBottom.coerceAtLeast(srcTop + 1)
            Log.d(TAG, "Stitch frame $i shift=$shift append=${srcBottom - srcTop}px")
            appendSegments.add(Rect(0, srcTop, width, srcBottom))
        }

        val totalHeight = appendSegments.sumOf { it.height() }

        if (totalHeight <= 0) return frames[0]

        val result = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        var y = 0
        for (i in frames.indices) {
            val frame = frames[i]
            val src = appendSegments[i]
            val dst = Rect(0, y, width, y + src.height())
            canvas.drawBitmap(frame, src, dst, null)
            y += src.height()
        }
        return result
    }

    private data class LuminanceFeatures(
        val rows: Array<IntArray>,
        val weights: DoubleArray
    )

    private fun readLuminanceFeatures(bitmap: Bitmap, top: Int, bottom: Int): LuminanceFeatures {
        val sampleStep = 4
        val sampleWidth = (bitmap.width + sampleStep - 1) / sampleStep
        val rows = Array(bottom - top) { rowIndex ->
            val y = top + rowIndex
            IntArray(sampleWidth) { sampleIndex ->
                val x = (sampleIndex * sampleStep).coerceAtMost(bitmap.width - 1)
                val p = bitmap.getPixel(x, y)
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                (r * 299 + g * 587 + b * 114) / 1000
            }
        }
        return LuminanceFeatures(rows, rowStddevs(rows))
    }

    private fun findScrollShift(prev: LuminanceFeatures, next: LuminanceFeatures, contentHeight: Int): Int {
        val maxShift = (contentHeight * 0.88f).toInt().coerceAtMost(contentHeight - 1)
        val minShift = 24.coerceAtMost(maxShift)
        if (maxShift < 1) return 0

        var bestWeightedShift = -1
        var bestWeightedScore = Double.POSITIVE_INFINITY
        var bestPlainShift = minShift
        var bestPlainScore = Double.POSITIVE_INFINITY

        for (shift in minShift..maxShift step 4) {
            val overlap = contentHeight - shift
            if (overlap < 80) break

            var weightSum = 0.0
            var weightedCost = 0.0
            var plainCost = 0.0
            var comparedRows = 0
            val rowStep = (overlap / 220).coerceAtLeast(1)

            for (row in 0 until overlap step rowStep) {
                val prevRowIndex = shift + row
                val nextRowIndex = row
                val weight = max(prev.weights[prevRowIndex], next.weights[nextRowIndex])
                val sad = rowSad(prev.rows[prevRowIndex], next.rows[nextRowIndex])
                plainCost += sad
                comparedRows++
                if (weight > 0.0) {
                    weightedCost += weight * sad
                    weightSum += weight
                }
            }

            if (comparedRows == 0) continue
            val plainScore = plainCost / comparedRows
            if (plainScore < bestPlainScore) {
                bestPlainScore = plainScore
                bestPlainShift = shift
            }
            if (weightSum > 0.0) {
                val weightedScore = weightedCost / weightSum
                if (weightedScore < bestWeightedScore) {
                    bestWeightedScore = weightedScore
                    bestWeightedShift = shift
                }
            }
        }

        val shift = if (bestWeightedShift >= 0) bestWeightedShift else bestPlainShift
        val score = if (bestWeightedShift >= 0) bestWeightedScore else bestPlainScore
        return if (score < 9000.0) shift else 0
    }

    private fun rowStddevs(rows: Array<IntArray>): DoubleArray {
        return DoubleArray(rows.size) { y ->
            val row = rows[y]
            if (row.isEmpty()) return@DoubleArray 0.0
            var sum = 0L
            for (v in row) sum += v
            val mean = sum.toDouble() / row.size
            var varianceSum = 0.0
            for (v in row) {
                val d = v - mean
                varianceSum += d * d
            }
            sqrt(varianceSum / row.size)
        }
    }

    private fun rowSad(a: IntArray, b: IntArray): Long {
        val width = min(a.size, b.size)
        var sum = 0L
        for (i in 0 until width) {
            val d = a[i] - b[i]
            sum += if (d < 0) -d.toLong() else d.toLong()
        }
        return sum
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
