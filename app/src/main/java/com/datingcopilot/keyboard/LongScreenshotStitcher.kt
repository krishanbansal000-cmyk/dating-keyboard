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
        private const val MIN_OVERLAP_PX = 40
        private const val MAX_OVERLAP_RATIO = 0.88f
        private const val MIN_OVERLAP_RATIO = 0.04f
        private const val MATCH_THRESHOLD = 1800.0
        private const val COARSE_STEP = 16
        private const val FINE_RANGE = 24
        private const val FINE_STEP = 2
    }

    data class StitchSegment(
        val frameIndex: Int,
        val srcRect: Rect,
        val pixelShift: Int
    )

    private val matchColumnFractions = floatArrayOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)

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
        val contentHeight = height

        val colXs = matchColumnFractions.map { (it * width).toInt().coerceIn(0, width - 1) }
        val profiles = frames.map { frame ->
            colXs.map { x ->
                IntArray(contentHeight) { row -> frame.getPixel(x, row) }
            }
        }
        val t1 = System.nanoTime()

        val segments = mutableListOf(StitchSegment(0, Rect(0, 0, width, height), 0))
        val recentShifts = mutableListOf<Int>()

        for (i in 1 until frames.size) {
            val prevCols = profiles[i - 1]
            val nextCols = profiles[i]
            val maxShift = (contentHeight * MAX_OVERLAP_RATIO).toInt().coerceAtMost(contentHeight - 1)
            val minShift = max(MIN_OVERLAP_PX, (contentHeight * MIN_OVERLAP_RATIO).toInt())

            var bestShift = minShift
            var bestScore = Double.MAX_VALUE

            for (shift in minShift..maxShift step COARSE_STEP) {
                val overlap = contentHeight - shift
                if (overlap < MIN_OVERLAP_PX) break
                val score = scoreShift(prevCols, nextCols, shift, contentHeight)
                if (score < bestScore) {
                    bestScore = score
                    bestShift = shift
                }
            }

            val threshold = MATCH_THRESHOLD * (contentHeight / 300.0).coerceAtLeast(1.0)

            if (bestScore >= threshold * 0.4) {
                val fineStart = max(minShift, bestShift - FINE_RANGE)
                val fineEnd = min(maxShift, bestShift + FINE_RANGE)
                for (shift in fineStart..fineEnd step FINE_STEP) {
                    if (shift == bestShift) continue
                    val overlap = contentHeight - shift
                    if (overlap < MIN_OVERLAP_PX) continue
                    val score = scoreShift(prevCols, nextCols, shift, contentHeight)
                    if (score < bestScore) {
                        bestScore = score
                        bestShift = shift
                    }
                }
            }

            val foundShift = if (bestScore < threshold) bestShift else 0
            val finalShift: Int

            if (foundShift > 0) {
                finalShift = foundShift
                recentShifts.add(foundShift)
                if (recentShifts.size > 3) recentShifts.removeAt(0)
            } else {
                finalShift = if (recentShifts.isNotEmpty()) {
                    recentShifts.sorted()[recentShifts.size / 2]
                } else {
                    (contentHeight * 0.5f).toInt()
                }
            }

            val srcTop = (contentHeight - finalShift).coerceIn(0, contentHeight - 1)
            segments.add(StitchSegment(i, Rect(0, srcTop, width, height), finalShift))
        }

        val totalHeight = segments.sumOf { it.srcRect.height() }
        if (totalHeight <= 0) return frames.last().copy(Bitmap.Config.ARGB_8888, false)

        val result = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        var y = 0
        for (s in segments) {
            canvas.drawBitmap(frames[s.frameIndex], s.srcRect, Rect(0, y, width, y + s.srcRect.height()), null)
            y += s.srcRect.height()
        }
        val t2 = System.nanoTime()
        Log.d(TAG, "stitchFast: ${frames.size} f -> ${totalHeight}px | profile:${(t1-t0)/1e6}ms match:${(t2-t1)/1e6}ms total:${(t2-t0)/1e6}ms")
        return result
    }

    private fun scoreShift(
        prevCols: List<IntArray>,
        nextCols: List<IntArray>,
        shift: Int,
        contentHeight: Int
    ): Double {
        val overlap = contentHeight - shift
        if (overlap < MIN_OVERLAP_PX) return Double.MAX_VALUE
        var totalScore = 0.0
        var compared = 0
        val stepRows = max(1, overlap / 60)

        for (row in 0 until overlap step stepRows) {
            val prevRow = shift + row
            val nextRow = row
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
