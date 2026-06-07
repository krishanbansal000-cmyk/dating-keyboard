package com.datingcopilot.keyboard.chat

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.datingcopilot.keyboard.ApiClient
import com.datingcopilot.keyboard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenshotAnalysisActivity : AppCompatActivity() {

    private val apiClient by lazy { ApiClient(this) }
    private lateinit var loadingState: View
    private lateinit var convoSection: LinearLayout
    private lateinit var suggSection: LinearLayout
    private val loadingAnimators = mutableListOf<ValueAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageUri = intent.data
        val persona = intent.getStringExtra("persona") ?: "playful"
        val intentType = intent.getStringExtra("intent") ?: "keep_going"
        val platform = intent.getStringExtra("platform") ?: "whatsapp"

        val scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.bg_dark, null))
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (48 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (40 * resources.displayMetrics.density).toInt()
            )
        }

        // ── Top bar ──
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * resources.displayMetrics.density).toInt() }
        }

        val backBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(resources.getColor(R.color.bg_surface, null))
                setStroke(dp(1), resources.getColor(R.color.glass_border_light, null))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        backBtn.addView(TextView(this).apply {
            text = "‹"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.white, null))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(18), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        backBtn.addView(TextView(this).apply {
            text = "Back"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_secondary, null))
        })
        topBar.addView(backBtn)

        val title = TextView(this).apply {
            text = "RizzSe"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { gravity = Gravity.CENTER }
        }
        topBar.addView(title)
        root.addView(topBar)

        // ── Screenshot ──
        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (300 * resources.displayMetrics.density).toInt()
            ).apply { bottomMargin = (16 * resources.displayMetrics.density).toInt() }
        }
        if (imageUri != null) {
            imageView.setImageURI(imageUri)
            imageView.alpha = 0f
            imageView.postDelayed({ imageView.animate().alpha(1f).setDuration(400).start() }, 100)
        }
        root.addView(imageView)

        // ── Animated analyzing panel ──
        val loadView = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (180 * resources.displayMetrics.density).toInt()
            )
            setPadding(
                dp(18),
                dp(20),
                dp(18),
                dp(20)
            )
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(resources.getColor(R.color.bg_card, null))
                setStroke(dp(1), resources.getColor(R.color.glass_border, null))
            }
        }
        loadingState = loadView

        val glow = View(this).apply {
            alpha = 0.32f
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    resources.getColor(R.color.accent_violet, null),
                    resources.getColor(R.color.accent_magenta, null),
                    resources.getColor(R.color.accent_pink, null)
                )
            ).apply { shape = GradientDrawable.OVAL }
            layoutParams = FrameLayout.LayoutParams(dp(92), dp(92), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(8)
            }
        }
        loadView.addView(glow)

        val orbit = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(96), dp(96), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(4)
            }
        }
        loadView.addView(orbit)

        val dotColors = intArrayOf(
            resources.getColor(R.color.accent_violet, null),
            resources.getColor(R.color.accent_magenta, null),
            resources.getColor(R.color.accent_pink, null)
        )
        repeat(3) { i ->
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(dotColors[i])
                }
                layoutParams = FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER)
            }
            orbit.addView(dot)
            val angle = Math.toRadians((i * 120).toDouble())
            dot.translationX = (Math.cos(angle) * dp(30)).toFloat()
            dot.translationY = (Math.sin(angle) * dp(30)).toFloat()
        }

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        }
        loadView.addView(copy)

        copy.addView(TextView(this).apply {
            text = "RizzSe is thinking"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
        })

        copy.addView(TextView(this).apply {
            text = "Reading the screenshot and crafting replies"
            textSize = 12f
            setTextColor(resources.getColor(R.color.text_muted, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        })

        val shimmerTrack = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(resources.getColor(R.color.shimmer, null))
            }
            layoutParams = LinearLayout.LayoutParams(dp(150), dp(5)).apply { topMargin = dp(14) }
        }
        val shimmerSweep = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    resources.getColor(R.color.accent_violet, null),
                    resources.getColor(R.color.accent_magenta, null)
                )
            ).apply { cornerRadius = dp(3).toFloat() }
            layoutParams = FrameLayout.LayoutParams(dp(54), dp(5))
        }
        shimmerTrack.addView(shimmerSweep)
        copy.addView(shimmerTrack)

        startGeminiStyleLoading(glow, orbit, shimmerSweep)
        root.addView(loadView)

        // ── Results containers ──
        convoSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        root.addView(convoSection)

        suggSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        root.addView(suggSection)

        scrollView.addView(root)
        setContentView(scrollView)

        if (imageUri != null) {
            runAnalysis(imageUri, persona, intentType, platform, root)
        }
    }

    private fun runAnalysis(uri: Uri, persona: String, intentType: String, platform: String, rootView: LinearLayout) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiClient.uploadScreenshot(uri, persona, this@ScreenshotAnalysisActivity, intentType, platform)
                withContext(Dispatchers.Main) {
                    if (response != null) showResults(response, rootView) else showError("Analysis failed")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showError("Error: ${e.message?.take(50)}") }
            }
        }
    }

    private fun showResults(response: AnalyzeResponse, rootView: LinearLayout) {
        val density = resources.displayMetrics.density
        stopLoadingAnimations()
        loadingState.visibility = View.GONE

        response.conversation?.forEachIndexed { i, msg ->
            val isYou = msg.sender == "you"
            val bubble = TextView(this).apply {
                text = msg.text
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_primary, null))
                setPadding(
                    (14 * density).toInt(), (10 * density).toInt(),
                    (14 * density).toInt(), (10 * density).toInt()
                )
                val bg = GradientDrawable()
                bg.cornerRadius = 16 * density
                bg.setColor(resources.getColor(if (isYou) R.color.bubble_you else R.color.bubble_them, null))
                background = bg
                layoutParams = LinearLayout.LayoutParams(
                    (280 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = if (isYou) Gravity.END else Gravity.START
                    topMargin = (6 * density).toInt()
                }
                translationY = 30f
                alpha = 0f
            }
            convoSection.addView(bubble)
            bubble.postDelayed({
                bubble.animate().translationY(0f).alpha(1f).setDuration(350)
                    .setStartDelay((i * 120).toLong()).start()
            }, 200)
            convoSection.visibility = View.VISIBLE
        }

        val delay = ((response.conversation?.size ?: 0) * 120L + 600L)
        rootView.postDelayed({ showSuggestions(response.suggestions.orEmpty()) }, delay)
    }

    private fun showSuggestions(suggestions: List<SuggestionOption>) {
        val density = resources.displayMetrics.density
        val label = TextView(this).apply {
            text = "Try these replies"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setPadding(0, (20 * density).toInt(), 0, (10 * density).toInt())
        }
        suggSection.addView(label)

        suggestions.forEachIndexed { i, sug ->
            val card = createCard(sug, density)
            card.alpha = 0f
            card.translationY = 20f
            suggSection.addView(card)
            card.postDelayed({
                card.animate().alpha(1f).translationY(0f).setDuration(350)
                    .setStartDelay((i * 150).toLong()).start()
            }, 100)
        }
        suggSection.visibility = View.VISIBLE
    }

    private fun createCard(sug: SuggestionOption, density: Float): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * density).toInt() }
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            val bg = GradientDrawable()
            bg.cornerRadius = 16 * density
            bg.setColor(resources.getColor(R.color.bg_card, null))
            bg.setStroke(1, resources.getColor(R.color.glass_border, null))
            background = bg
        }

        // Top row: badge + confidence + copy icon
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * density).toInt() }
        }

        topRow.addView(TextView(this).apply {
            text = sug.persona.replaceFirstChar { it.uppercase() }
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.accent_violet, null))
            setPadding((8 * density).toInt(), (2 * density).toInt(), (8 * density).toInt(), (2 * density).toInt())
            val bg = GradientDrawable()
            bg.cornerRadius = 6 * density
            bg.setColor(resources.getColor(R.color.bg_surface, null))
            background = bg
        })

        topRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })

        topRow.addView(TextView(this).apply {
            text = "${sug.confidence}%"
            textSize = 10f
            setTextColor(resources.getColor(R.color.text_muted, null))
            setPadding(0, 0, (8 * density).toInt(), 0)
        })

        // Copy icon only — small, inline on top-right
        topRow.addView(TextView(this).apply {
            text = "📋"
            textSize = 14f
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("RizzSe", sug.text))
                Toast.makeText(this@ScreenshotAnalysisActivity, "Copied!", Toast.LENGTH_SHORT).show()
            }
        })
        card.addView(topRow)

        // Suggestion text — more readable, tighter
        card.addView(TextView(this).apply {
            text = sug.text
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setLineSpacing(3f, 1.0f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        return card
    }

    private fun showError(msg: String) {
        stopLoadingAnimations()
        loadingState.visibility = View.GONE
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        stopLoadingAnimations()
        super.onDestroy()
    }

    private fun startGeminiStyleLoading(glow: View, orbit: View, shimmerSweep: View) {
        loadingAnimators += ObjectAnimator.ofFloat(glow, View.SCALE_X, 0.82f, 1.08f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        loadingAnimators += ObjectAnimator.ofFloat(glow, View.SCALE_Y, 0.82f, 1.08f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        loadingAnimators += ObjectAnimator.ofFloat(glow, View.ALPHA, 0.18f, 0.46f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        loadingAnimators += ObjectAnimator.ofFloat(orbit, View.ROTATION, 0f, 360f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        loadingAnimators += ObjectAnimator.ofFloat(shimmerSweep, View.TRANSLATION_X, 0f, dp(96).toFloat()).apply {
            duration = 950
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopLoadingAnimations() {
        loadingAnimators.forEach { it.cancel() }
        loadingAnimators.clear()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
