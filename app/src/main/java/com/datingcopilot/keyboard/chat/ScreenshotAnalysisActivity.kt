package com.datingcopilot.keyboard.chat

import android.animation.AnimatorSet
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
import android.widget.ProgressBar
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

        val backBtn = TextView(this).apply {
            text = "←"
            textSize = 20f
            setTextColor(resources.getColor(R.color.white, null))
            gravity = Gravity.CENTER
            setPadding(
                (10 * resources.displayMetrics.density).toInt(),
                (6 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (6 * resources.displayMetrics.density).toInt()
            )
            val bg = GradientDrawable()
            bg.cornerRadius = 12 * resources.displayMetrics.density
            bg.setColor(resources.getColor(R.color.bg_surface, null))
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { finish() }
        }
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

        // ── Animated analyzing overlay ──
        val loadView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (180 * resources.displayMetrics.density).toInt()
            )
            setPadding(
                (32 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (32 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt()
            )
            val bg = GradientDrawable()
            bg.cornerRadius = 20 * resources.displayMetrics.density
            bg.setColor(resources.getColor(R.color.bg_card, null))
            bg.setStroke(1, resources.getColor(R.color.glass_border, null))
            background = bg
        }
        loadingState = loadView

        val loaderDots = TextView(this).apply {
            text = ". . ."
            textSize = 32f
            setTextColor(resources.getColor(R.color.accent_violet, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
        }
        // Pulsing animation for dots
        val pulseAnim = ObjectAnimator.ofFloat(loaderDots, "alpha", 0.3f, 1f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        pulseAnim.start()
        loadView.addView(loaderDots)

        val analyzingText = TextView(this).apply {
            text = "Reading your chat..."
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
        }
        loadView.addView(analyzingText)

        val subText = TextView(this).apply {
            text = "Generating rizz replies"
            textSize = 12f
            setTextColor(resources.getColor(R.color.text_muted, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * resources.displayMetrics.density).toInt() }
        }
        loadView.addView(subText)
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
        loadingState.visibility = View.GONE
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }
}
