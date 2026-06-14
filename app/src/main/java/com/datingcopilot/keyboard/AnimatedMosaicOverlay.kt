package com.datingcopilot.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class AnimatedMosaicOverlay(context: Context) : View(context) {

    private val particleCount = 120
    private val connectionDistance = 180f * resources.displayMetrics.density
    private val particles = Array(particleCount) { Particle() }
    private val path = Path()

    private val particlePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }
    private val bgPaint = Paint()
    private val scanPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private var progress = 0f
    private var scanY = 0f

    private val appColors = listOf(
        0xFFFF38F8.toInt(), // pink
        0xFF7C3AED.toInt(), // violet
        0xFFBE00FF.toInt(), // magenta
        0xFF3B82F6.toInt(), // blue
        0xFFEC4899.toInt(), // rose
    )

    init {
        // Initialize particles with random positions and velocities
        for (i in particles.indices) {
            particles[i].x = Random.nextFloat() * 1000f
            particles[i].y = Random.nextFloat() * 2000f
            particles[i].vx = (Random.nextFloat() - 0.5f) * 2f
            particles[i].vy = (Random.nextFloat() - 0.5f) * 2f
            particles[i].phase = Random.nextFloat() * PI.toFloat() * 2
            particles[i].size = 3f + Random.nextFloat() * 5f
            particles[i].color = appColors[i % appColors.size]
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 6000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                progress = it.animatedFraction
                scanY = (it.animatedValue as Float) * 2000f
                updateParticles()
                invalidate()
            }
        }
    }

    private fun updateParticles() {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val time = progress * PI.toFloat() * 2

        for (p in particles) {
            // Organic movement with sine waves
            p.x += p.vx + sin(time + p.phase) * 1.5f
            p.y += p.vy + cos(time * 0.7f + p.phase) * 1.5f

            // Wrap around edges
            if (p.x < -50f) p.x = w + 50f
            if (p.x > w + 50f) p.x = -50f
            if (p.y < -50f) p.y = h + 50f
            if (p.y > h + 50f) p.y = -50f

            // Pulse size
            p.currentSize = p.size * (0.7f + 0.3f * sin(time * 2f + p.phase))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator?.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Dark translucent background
        canvas.drawColor(0x550A0211.toInt())

        // Center radial glow
        val glowPaint = Paint().apply {
            shader = RadialGradient(
                w / 2, h / 2, Math.max(w, h) * 0.6f,
                intArrayOf(0x15000000, 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, glowPaint)

        // Draw connections between nearby particles
        for (i in particles.indices) {
            for (j in i + 1 until particles.size) {
                val dx = particles[i].x - particles[j].x
                val dy = particles[i].y - particles[j].y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)

                if (dist < connectionDistance) {
                    val alpha = ((1f - dist / connectionDistance) * 100).toInt().coerceIn(0, 100)
                    linePaint.color = (alpha shl 24) or (particles[i].color and 0x00FFFFFF)
                    canvas.drawLine(particles[i].x, particles[i].y, particles[j].x, particles[j].y, linePaint)
                }
            }
        }

        // Draw particles
        for (p in particles) {
            val alpha = (150 + 105 * sin(progress * PI.toFloat() * 4 + p.phase)).toInt().coerceIn(50, 255)
            particlePaint.color = (alpha shl 24) or (p.color and 0x00FFFFFF)
            canvas.drawCircle(p.x, p.y, p.currentSize * resources.displayMetrics.density, particlePaint)

            // Glow around larger particles
            if (p.currentSize > 5f) {
                val glow = Paint().apply {
                    color = (0x30 shl 24) or (p.color and 0x00FFFFFF)
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(p.x, p.y, p.currentSize * 2.5f * resources.displayMetrics.density, glow)
            }
        }

        // Scanning line effect
        val scanLineY = (progress * h * 1.2f - h * 0.1f) % (h * 1.2f)
        scanPaint.shader = RadialGradient(
            w / 2, scanLineY, w * 0.8f,
            intArrayOf(0x10FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, scanLineY - 2f, w, scanLineY + 2f, scanPaint)

        // Bottom fade
        val fadePaint = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, h * 0.7f, 0f, h,
                intArrayOf(0x00000000, 0x40000000),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, h * 0.7f, w, h, fadePaint)
    }

    private data class Particle(
        var x: Float = 0f,
        var y: Float = 0f,
        var vx: Float = 0f,
        var vy: Float = 0f,
        var phase: Float = 0f,
        var size: Float = 4f,
        var currentSize: Float = 4f,
        var color: Int = 0xFFFF38F8.toInt()
    )
}
