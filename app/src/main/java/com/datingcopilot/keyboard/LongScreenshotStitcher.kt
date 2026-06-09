package com.datingcopilot.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class LongScreenshotStitcher {

    companion object {
        private const val TAG = "LongScreenshotStitcher"

        // Matching parameters
        private const val COLUMN_SAMPLE_STEP = 8
        private const val ROW_SAMPLE_STEP = 2
        private const val MIN_OVERLAP_PX = 40
        private const val MAX_OVERLAP_RATIO = 0.88f
        private const val MIN_OVERLAP_RATIO = 0.04f

        // Matching threshold — lower = stricter match
        private const val MATCH_THRESHOLD = 1800.0

        // Seam blend width
        private const val BLEND_WIDTH = 20

        // Chrome detection — sample rows from edges to find content boundaries
        private const val CHROME_SCAN_HEIGHT = 60
        private const val CHROME_VARIANCE_THRESHOLD = 80.0
    }

    data class StitchSegment(
        val frameIndex: Int,
        val srcRect: Rect,         // source region in the frame
        val pixelShift: Int        // how many pixels of overlap detected
    )

    fun stitch(frames: List<Bitmap>): Bitmap? {
        if (frames.isEmpty()) return null
        if (frames.size == 1) return frames[0].copy(Bitmap.Config.ARGB_8888, false)

        val width = frames[0].width
        val height = frames[0].height

        // Detect content boundaries (skip chrome dynamically)
        val contentTop = findContentTop(frames[0], width, height)
        val contentBottom = findContentBottom(frames[0], width, height)
        val contentHeight = contentBottom - contentTop
        if (contentHeight <= 0) return frames[0]

        // Compute pixel profiles for each frame (full RGB, multi-column samples)
        val profiles = frames.map { buildProfile(it, width, contentTop, contentBottom) }

        // Build segments with smart fallback
        val segments = mutableListOf(StitchSegment(0, Rect(0, 0, width, height), 0))
        val recentShifts = mutableListOf<Int>()
        val maxRecentShifts = 3

        for (i in 1 until frames.size) {
            val shift = findOptimalShift(profiles[i - 1], profiles[i], contentHeight)
            val bestShift: Int

            if (shift > 0) {
                bestShift = shift
                recentShifts.add(shift)
                if (recentShifts.size > maxRecentShifts) recentShifts.removeAt(0)
                Log.d(TAG, "Frame $i: shift=$bestShift content=${contentBottom - bestShift}px")
            } else {
                // Fallback: use median of recent successful shifts
                bestShift = if (recentShifts.isNotEmpty()) {
                    recentShifts.sorted()[recentShifts.size / 2]
                } else {
                    (contentHeight * 0.5f).toInt()
                }
                Log.d(TAG, "Frame $i: no reliable match, fallback shift=$bestShift")
            }

            val srcTop = (contentBottom - bestShift).coerceIn(contentTop, contentBottom - 1)
            val srcBottom = contentBottom.coerceAtLeast(srcTop + 1)
            segments.add(StitchSegment(i, Rect(0, srcTop, width, srcBottom), bestShift))
        }

        // Calculate total height
        val totalHeight = segments.sumOf { it.srcRect.height() }
        if (totalHeight <= 0) return frames[0]

        // Stitch with seam blending
        val result = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        var y = 0

        for (s in segments) {
            val frame = frames[s.frameIndex]
            val src = s.srcRect
            val dst = Rect(0, y, width, y + src.height())
            canvas.drawBitmap(frame, src, dst, null)
            y += src.height()
        }

        // Apply seam blending at stitch boundaries
        y = 0
        // Use a simple progress tracking byte array for each row
        for (i in 1 until segments.size) {
            y += segments[i - 1].srcRect.height()
            val overlapStart = max(0, y - BLEND_WIDTH)
            val overlapEnd = min(y + BLEND_WIDTH, totalHeight)
            blendSeam(canvas, frames, segments, i, width, y, overlapStart, overlapEnd)
        }

        return result
    }

    private fun blendSeam(
        canvas: Canvas,
        frames: List<Bitmap>,
        segments: List<StitchSegment>,
        segIndex: Int,
        width: Int,
        seamY: Int,
        blendStart: Int,
        blendEnd: Int
    ) {
        val prevFrame = frames[segIndex - 1]
        val curFrame = frames[segIndex]
        val prevSeg = segments[segIndex - 1]
        val curSeg = segments[segIndex]
        val frameHeight = frames[0].height

        // We accumulate pixel data per row for blended rows and draw them as a line
        val pixels = IntArray(width)
        for (blendY in blendStart until blendEnd) {
            val t = (blendY - blendStart).toFloat() / (blendEnd - blendStart).coerceAtLeast(1)
            val alphaBlend = (t * 255).toInt().coerceIn(0, 255)
            val oneMinusAlpha = 255 - alphaBlend

            val prevSegStart = segments.take(segIndex - 1).sumOf { it.srcRect.height() }
            val prevRowInFrame = (blendY - prevSegStart) + prevSeg.srcRect.top
            val curRowInFrame = (blendY - seamY) + curSeg.srcRect.top + curSeg.pixelShift

            if (prevRowInFrame !in 0 until frameHeight || curRowInFrame !in 0 until frameHeight) continue

            for (x in 0 until width) {
                val a = prevFrame.getPixel(x, prevRowInFrame)
                val b = curFrame.getPixel(x, curRowInFrame)
                val r = ((((a shr 16) and 0xFF) * oneMinusAlpha + ((b shr 16) and 0xFF) * alphaBlend) / 255).coerceIn(0, 255)
                val g = ((((a shr 8) and 0xFF) * oneMinusAlpha + ((b shr 8) and 0xFF) * alphaBlend) / 255).coerceIn(0, 255)
                val blue = (((a and 0xFF) * oneMinusAlpha + (b and 0xFF) * alphaBlend) / 255).coerceIn(0, 255)
                pixels[x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or blue
            }
            val rowBmp = Bitmap.createBitmap(pixels, width, 1, Bitmap.Config.ARGB_8888)
            canvas.drawBitmap(rowBmp, 0f, blendY.toFloat(), null)
            rowBmp.recycle()
        }
    }

    private data class ProfileColumn(val pixels: IntArray)

    private data class FrameProfile(
        val columns: List<ProfileColumn>,
        val startY: Int,
        val endY: Int
    )

    private fun buildProfile(bitmap: Bitmap, width: Int, top: Int, bottom: Int): FrameProfile {
        val cols = mutableListOf<ProfileColumn>()
        for (x in 0 until width step COLUMN_SAMPLE_STEP) {
            val clampedX = min(x, width - 1)
            val pixelRow = IntArray(bottom - top) { rowIdx ->
                bitmap.getPixel(clampedX, top + rowIdx)
            }
            cols.add(ProfileColumn(pixelRow))
        }
        return FrameProfile(cols, top, bottom)
    }

    private fun findOptimalShift(prev: FrameProfile, next: FrameProfile, contentHeight: Int): Int {
        val maxShift = (contentHeight * MAX_OVERLAP_RATIO).toInt().coerceAtMost(contentHeight - 1)
        val minShift = max(MIN_OVERLAP_PX, (contentHeight * MIN_OVERLAP_RATIO).toInt())
        if (maxShift < minShift || contentHeight <= 0) return 0

        var bestShift = 0
        var bestScore = Double.MAX_VALUE

        for (shift in minShift..maxShift step ROW_SAMPLE_STEP) {
            val overlap = contentHeight - shift
            if (overlap < MIN_OVERLAP_PX) break

            var totalScore = 0.0
            var compared = 0
            val rowStep = max(1, overlap / 200)

            for (row in 0 until overlap step rowStep) {
                val prevRowIdx = shift + row
                val nextRowIdx = row
                if (prevRowIdx >= contentHeight || nextRowIdx >= contentHeight) continue

                var columnScore = 0.0
                for (col in prev.columns.indices) {
                    val prevPx = prev.columns[col].pixels[prevRowIdx]
                    val nextPx = next.columns[col].pixels[nextRowIdx]
                    columnScore += pixelDiff(prevPx, nextPx)
                }
                totalScore += columnScore / prev.columns.size.coerceAtLeast(1)
                compared++
            }

            if (compared > 0) {
                val avgScore = totalScore / compared
                if (avgScore < bestScore) {
                    bestScore = avgScore
                    bestShift = shift
                }
            }
        }

        return if (bestScore < MATCH_THRESHOLD * (contentHeight / 300.0).coerceAtLeast(1.0)) bestShift else 0
    }

    private fun pixelDiff(a: Int, b: Int): Double {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }

    private fun rowVariance(bitmap: Bitmap, x: Int, y: Int, sampleWidth: Int): Double {
        val startX = max(0, x - sampleWidth / 2)
        val endX = min(bitmap.width - 1, x + sampleWidth / 2)
        if (endX <= startX) return 0.0

        var sumR = 0L; var sumG = 0L; var sumB = 0L
        val pixels = mutableListOf<Int>()
        for (sx in startX..endX) {
            val px = bitmap.getPixel(sx, y)
            pixels.add(px)
            sumR += (px shr 16) and 0xFF
            sumG += (px shr 8) and 0xFF
            sumB += px and 0xFF
        }
        val n = pixels.size
        val meanR = sumR.toDouble() / n
        val meanG = sumG.toDouble() / n
        val meanB = sumB.toDouble() / n

        var varSum = 0.0
        for (px in pixels) {
            val dr = (px shr 16 and 0xFF) - meanR
            val dg = (px shr 8 and 0xFF) - meanG
            val db = (px and 0xFF) - meanB
            varSum += dr * dr + dg * dg + db * db
        }
        return sqrt(varSum / n)
    }

    private fun findContentTop(bitmap: Bitmap, width: Int, height: Int): Int {
        val scanWidth = min(width, CHROME_SCAN_HEIGHT * 3)
        for (y in 0 until min(height / 3, CHROME_SCAN_HEIGHT * 6)) {
            val v = rowVariance(bitmap, width / 2, y, scanWidth)
            if (v > CHROME_VARIANCE_THRESHOLD) {
                return max(0, y - 10)
            }
        }
        return 0
    }

    private fun findContentBottom(bitmap: Bitmap, width: Int, height: Int): Int {
        val scanWidth = min(width, CHROME_SCAN_HEIGHT * 3)
        for (y in (height - 1) downTo max(0, height - CHROME_SCAN_HEIGHT * 6)) {
            val v = rowVariance(bitmap, width / 2, y, scanWidth)
            if (v > CHROME_VARIANCE_THRESHOLD) {
                return min(height, y + 10)
            }
        }
        return height
    }
}
