package com.datingcopilot.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class LongScreenshotStitcher {

    companion object {
        private const val TAG = "LongScreenshotStitcher"
        private const val MIN_OVERLAP_PX = 60
        private const val MAX_OVERLAP_RATIO = 0.85f
        private const val COARSE_STEP = 12
        private const val FINE_RANGE = 18
        private const val FINE_STEP = 1
        private const val DUPLICATE_THRESHOLD = 1200.0
        private const val MAX_OUTPUT_HEIGHT_PX = 4200
    }

    private val matchColumnFractions = floatArrayOf(0.12f, 0.28f, 0.50f, 0.72f, 0.88f)

    /**
     * Run a synthetic benchmark to measure stitching speed.
     * Returns a string with timing results.
     */
    fun benchmark(context: android.content.Context): String {
        val w = 1080
        val h = 1920
        val overlap = 600
        val frames = List(8) { idx ->
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                canvas.drawColor(android.graphics.Color.BLACK)
                val paint = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY }
                for (y in 0 until h step 40) {
                    paint.color = if ((y / 40 + idx * 100) % 2 == 0) android.graphics.Color.DKGRAY else android.graphics.Color.GRAY
                    canvas.drawRect(0f, y.toFloat(), w.toFloat(), (y + 20).toFloat(), paint)
                }
                val chatY = h - overlap + (idx * overlap / 8)
                paint.color = android.graphics.Color.WHITE
                canvas.drawRect(100f, chatY.toFloat(), 800f, (chatY + 30).toFloat(), paint)
            }
        }
        val t0 = System.currentTimeMillis()
        val result = stitchFast(frames)
        val t1 = System.currentTimeMillis()
        val r = result?.let { "result=${it.width}x${it.height}" } ?: "null"
        frames.forEach { it.recycle() }
        result?.recycle()
        return "Benchmark: 8 frames ${w}x${h} -> $r | stitch=${t1 - t0}ms"
    }

    fun stitchFast(frames: List<Bitmap>): Bitmap? {
        val t0 = System.nanoTime()
        if (frames.isEmpty()) return null
        if (frames.size == 1) {
            val r = frames[0].copy(Bitmap.Config.ARGB_8888, false)
            Log.d(TAG, "stitchFast: 1 frame, took 0ms")
            return r
        }

        val width = frames[0].width
        val height = frames[0].height

        // 1. Deduplicate near-identical frames so they don't corrupt alignment.
        val uniqueFrames = deduplicateFrames(frames)
        if (uniqueFrames.size == 1) {
            return uniqueFrames[0].copy(Bitmap.Config.ARGB_8888, false)
        }

        // 2. Build column profiles for fast comparison.
        val colXs = matchColumnFractions.map { (it * width).toInt().coerceIn(0, width - 1) }
        val profiles = uniqueFrames.map { frame ->
            colXs.map { x -> IntArray(height) { row -> frame.getPixel(x, row) } }
        }
        val t1 = System.nanoTime()

        // 3. Compute relative vertical shift for each consecutive pair.
        //    Shift > 0 means the next frame shows content *below* the previous frame (scrolled down).
        //    Shift < 0 means the next frame shows content *above* (scrolled up).
        val shifts = mutableListOf<Int>()
        val perFrameTimes = mutableListOf<String>()
        val recentShifts = mutableListOf<Int>()

        for (i in 1 until uniqueFrames.size) {
            val ft0 = System.nanoTime()
            val prevCols = profiles[i - 1]
            val nextCols = profiles[i]

            val maxShift = (height * MAX_OVERLAP_RATIO).toInt().coerceAtMost(height - 1)
            val minShift = -maxShift

            var bestShift = 0
            var bestScore = Double.MAX_VALUE

            // Coarse search over both directions.
            for (shift in minShift..maxShift step COARSE_STEP) {
                if (kotlin.math.abs(shift) < MIN_OVERLAP_PX) continue
                val score = scoreBidirectionalShift(prevCols, nextCols, shift, height)
                if (score < bestScore) {
                    bestScore = score
                    bestShift = shift
                }
            }

            // Fine refinement around the best coarse shift.
            val fineStart = max(minShift, bestShift - FINE_RANGE)
            val fineEnd = min(maxShift, bestShift + FINE_RANGE)
            for (shift in fineStart..fineEnd step FINE_STEP) {
                if (kotlin.math.abs(shift) < MIN_OVERLAP_PX) continue
                if (shift == bestShift) continue
                val score = scoreBidirectionalShift(prevCols, nextCols, shift, height)
                if (score < bestScore) {
                    bestScore = score
                    bestShift = shift
                }
            }

            // Reject absurd matches and fall back to recent median shift.
            val finalShift = if (bestScore < DUPLICATE_THRESHOLD * 3) {
                recentShifts.add(bestShift)
                if (recentShifts.size > 4) recentShifts.removeAt(0)
                bestShift
            } else {
                if (recentShifts.isNotEmpty()) recentShifts.sorted()[recentShifts.size / 2] else 0
            }

            shifts.add(finalShift)
            perFrameTimes.add("f$i:${(System.nanoTime()-ft0)/1e6}ms shift=$finalShift score=${"%.0f".format(bestScore)}")
        }

        // 4. Build a clean panorama using only non-overlapping strips.
        //    This avoids ghosting/double content when frames overlap.
        //    Shift > 0 -> user scrolled down -> new strip is bottom of next frame.
        //    Shift < 0 -> user scrolled up   -> new strip is top of next frame.
        val stripSources = mutableListOf<Triple<Bitmap, Rect, Int>>() // bitmap, srcRect, destY
        var currentTop = 0
        var currentBottom = height

        // First frame: draw full.
        stripSources.add(Triple(uniqueFrames[0], Rect(0, 0, width, height), 0))

        for (i in 1 until uniqueFrames.size) {
            val shift = shifts[i - 1]
            if (shift > 0) {
                // Scrolled down: append the bottom `shift` rows of frame i.
                val src = Rect(0, height - shift, width, height)
                stripSources.add(Triple(uniqueFrames[i], src, currentBottom))
                currentBottom += shift
            } else if (shift < 0) {
                // Scrolled up: prepend the top `|shift|` rows of frame i.
                val absShift = -shift
                currentTop -= absShift
                val src = Rect(0, 0, width, absShift)
                stripSources.add(Triple(uniqueFrames[i], src, currentTop))
            }
            // shift == 0 is a duplicate frame; skip drawing to avoid overdraw.
        }

        val panoramaHeight = currentBottom - currentTop
        if (panoramaHeight <= 0) return uniqueFrames.last().copy(Bitmap.Config.ARGB_8888, false)

        val panorama = Bitmap.createBitmap(width, panoramaHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(panorama)
        for ((bitmap, src, destY) in stripSources) {
            val y = destY - currentTop
            canvas.drawBitmap(bitmap, src, Rect(0, y, width, y + src.height()), null)
        }
        val t2 = System.nanoTime()

        // 5. Crop to the most recent conversation region.
        //    Determine overall scroll direction from cumulative shift.
        val cumulativeShift = shifts.sum()
        val lastFrameTopInPanorama = 0 - currentTop  // first frame top in panorama
        val lastFrameBottomInPanorama = lastFrameTopInPanorama + height

        // The "recent" edge of the panorama:
        //   - If overall scroll is down, recent content is at the bottom.
        //   - If overall scroll is up, recent content is at the top.
        //   - If no net scroll, center on the single viewport.
        val recentEdge = when {
            cumulativeShift > 0 -> currentBottom // bottom of panorama (newest content)
            cumulativeShift < 0 -> -currentTop   // top of panorama (newest content after scrolling up)
            else -> (lastFrameBottomInPanorama + lastFrameTopInPanorama) / 2
        }

        val cropTop: Int
        val cropBottom: Int
        if (cumulativeShift >= 0) {
            // Keep the bottom MAX_OUTPUT_HEIGHT_PX so the latest replies are visible.
            cropBottom = panoramaHeight
            cropTop = max(0, cropBottom - MAX_OUTPUT_HEIGHT_PX)
        } else {
            // Keep the top MAX_OUTPUT_HEIGHT_PX.
            cropTop = 0
            cropBottom = min(panoramaHeight, MAX_OUTPUT_HEIGHT_PX)
        }
        val cropHeight = cropBottom - cropTop

        val result = Bitmap.createBitmap(panorama, 0, cropTop, width, cropHeight)
        panorama.recycle()

        Log.d(TAG, "stitchFast: ${frames.size} f -> ${uniqueFrames.size} unique -> ${result.width}x${result.height} | profile:${(t1-t0)/1e6}ms match:${(t2-t1)/1e6}ms total:${(t2-t0)/1e6}ms shifts=$shifts cumulative=$cumulativeShift")
        for (ft in perFrameTimes) Log.d(TAG, "  $ft")
        return result
    }

    private fun deduplicateFrames(frames: List<Bitmap>): List<Bitmap> {
        if (frames.size <= 1) return frames
        val result = mutableListOf<Bitmap>()
        result.add(frames[0])
        for (i in 1 until frames.size) {
            val prev = result.last()
            val next = frames[i]
            if (!areFramesDuplicate(prev, next)) {
                result.add(next)
            }
        }
        return result
    }

    private fun areFramesDuplicate(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        val w = a.width
        val h = a.height
        val samplePoints = 16
        var totalDiff = 0.0
        var samples = 0
        for (i in 0 until samplePoints) {
            val x = (w * ((i * 17 + 3) % 97) / 97).coerceIn(0, w - 1)
            val y = (h * ((i * 31 + 7) % 97) / 97).coerceIn(0, h - 1)
            val pa = a.getPixel(x, y)
            val pb = b.getPixel(x, y)
            val dr = ((pa shr 16) and 0xFF) - ((pb shr 16) and 0xFF)
            val dg = ((pa shr 8) and 0xFF) - ((pb shr 8) and 0xFF)
            val db = (pa and 0xFF) - (pb and 0xFF)
            totalDiff += sqrt((dr * dr + dg * dg + db * db).toDouble())
            samples++
        }
        return if (samples > 0) (totalDiff / samples) < 30.0 else false
    }

    private fun scoreBidirectionalShift(
        prevCols: List<IntArray>,
        nextCols: List<IntArray>,
        shift: Int,
        contentHeight: Int
    ): Double {
        // shift > 0: next frame is below prev frame.
        //    overlap: prev bottom [shift .. height-1] with next top [0 .. height-shift-1]
        // shift < 0: next frame is above prev frame.
        //    overlap: prev top [0 .. height+shift-1] with next bottom [-shift .. height-1]
        val overlap = contentHeight - kotlin.math.abs(shift)
        if (overlap < MIN_OVERLAP_PX) return Double.MAX_VALUE

        var totalScore = 0.0
        var compared = 0
        val stepRows = max(1, overlap / 80)

        for (row in 0 until overlap step stepRows) {
            val prevRow = if (shift >= 0) shift + row else row
            val nextRow = if (shift >= 0) row else -shift + row
            if (prevRow >= contentHeight || nextRow >= contentHeight) continue
            var colScore = 0.0
            for (c in prevCols.indices) {
                val a = prevCols[c][prevRow]
                val b = nextCols[c][nextRow]
                val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
                val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
                val db = (a and 0xFF) - (b and 0xFF)
                colScore += sqrt((dr * dr + dg * dg + db * db).toDouble())
            }
            totalScore += colScore / prevCols.size
            compared++
        }
        return if (compared > 0) totalScore / compared else Double.MAX_VALUE
    }
}
