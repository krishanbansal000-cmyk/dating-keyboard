package com.datingcopilot.keyboard.chat

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
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
                (32 * resources.displayMetrics.density).toInt()
            )
        }

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * resources.displayMetrics.density).toInt() }
        }

        val backBtn = TextView(this).apply {
            text = "← Back"
            textSize = 16f
            setTextColor(resources.getColor(R.color.accent_violet, null))
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

        // Screenshot
        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (300 * resources.displayMetrics.density).toInt()
            ).apply { bottomMargin = (20 * resources.displayMetrics.density).toInt() }
        }
        if (imageUri != null) {
            imageView.setImageURI(imageUri)
            imageView.alpha = 0f
            imageView.postDelayed({ imageView.animate().alpha(1f).setDuration(400).start() }, 100)
        }
        root.addView(imageView)

        val loadView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (200 * resources.displayMetrics.density).toInt()
            )
        }
        loadingState = loadView

        val spinner = ProgressBar(this, null, android.R.attr.progressBarStyleLarge).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                (48 * resources.displayMetrics.density).toInt(),
                (48 * resources.displayMetrics.density).toInt()
            ).apply { bottomMargin = (16 * resources.displayMetrics.density).toInt() }
        }
        loadView.addView(spinner)

        val loadingText = TextView(this).apply {
            text = "Analyzing your conversation..."
            textSize = 15f
            setTextColor(resources.getColor(R.color.text_secondary, null))
        }
        loadView.addView(loadingText)
        root.addView(loadView)

        // Results containers (initially hidden)
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

        // Start analysis
        if (imageUri != null) {
            runAnalysis(imageUri, persona, intentType, platform, root)
        }
    }

    private fun runAnalysis(uri: Uri, persona: String, intentType: String, platform: String, root: LinearLayout) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiClient.uploadScreenshot(uri, persona, this@ScreenshotAnalysisActivity, intentType, platform)
                withContext(Dispatchers.Main) {
                    if (response != null) {
                        showResults(root, response)
                    } else {
                        showError(root, "Analysis failed")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError(root, "Error: ${e.message?.take(50)}")
                }
            }
        }
    }

    private fun showResults(root: LinearLayout, response: AnalyzeResponse) {
        val density = resources.displayMetrics.density

        // Hide loading
        loadingState.visibility = View.GONE

        // Conversation section
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
                translationY = 40f
                alpha = 0f
            }
            convoSection?.addView(bubble)
            bubble.postDelayed({
                bubble.animate().translationY(0f).alpha(1f).setDuration(400)
                    .setStartDelay((i * 150).toLong()).start()
            }, 300)
        }
        convoSection?.visibility = View.VISIBLE

        // Suggestions section (delayed)
        root.postDelayed({
            showSuggestions(root, response.suggestions.orEmpty())
        }, ((response.conversation?.size ?: 0) * 150L + 500L))
    }

    private fun showSuggestions(root: LinearLayout, suggestions: List<SuggestionOption>) {
        val density = resources.displayMetrics.density

        val label = TextView(this).apply {
            text = "Suggestions"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setPadding(0, (20 * density).toInt(), 0, (8 * density).toInt())
        }
        suggSection.addView(label)

        suggestions.forEachIndexed { i, sug ->
            val card = createCard(sug)
            card.alpha = 0f
            card.translationY = 30f
            suggSection.addView(card)
            card.postDelayed({
                card.animate().alpha(1f).translationY(0f).setDuration(400)
                    .setStartDelay((i * 200).toLong()).start()
            }, 100)
        }
        suggSection.visibility = View.VISIBLE
    }

    private fun createCard(sug: SuggestionOption): LinearLayout {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * density).toInt() }
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            val bg = GradientDrawable()
            bg.cornerRadius = 16 * density
            bg.setColor(resources.getColor(R.color.bg_card, null))
            bg.setStroke(1, resources.getColor(R.color.glass_border, null))
            background = bg

            addView(LinearLayout(this.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                addView(TextView(this.context).apply {
                    text = sug.persona.replaceFirstChar { it.uppercase() }
                    textSize = 10f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(resources.getColor(R.color.accent_violet, null))
                    setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
                    val bg2 = GradientDrawable()
                    bg2.cornerRadius = 8 * density
                    bg2.setColor(resources.getColor(R.color.bg_surface, null))
                    background = bg2
                })
                addView(View(this.context).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
                addView(TextView(this.context).apply {
                    text = "${sug.confidence}%"
                    textSize = 11f
                    setTextColor(resources.getColor(R.color.text_muted, null))
                })
            })
            addView(TextView(this.context).apply {
                text = sug.text
                textSize = 15f
                setTextColor(resources.getColor(R.color.text_primary, null))
                setLineSpacing(4f, 1.0f)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = (10 * density).toInt() }
            })
            addView(TextView(this.context).apply {
                text = "📋 Copy"
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.accent_violet, null))
                gravity = Gravity.CENTER
                setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
                val bg3 = GradientDrawable()
                bg3.cornerRadius = 14 * density
                bg3.setColor(resources.getColor(R.color.bg_surface, null))
                background = bg3
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { gravity = Gravity.END; topMargin = (10 * density).toInt() }
                setOnClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("RizzSe", sug.text))
                    Toast.makeText(this@ScreenshotAnalysisActivity, "Copied!", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun showError(root: LinearLayout, msg: String) {
        loadingState.visibility = View.GONE
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }
}
