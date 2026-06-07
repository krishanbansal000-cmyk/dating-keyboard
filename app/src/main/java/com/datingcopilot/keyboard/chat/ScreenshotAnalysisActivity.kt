package com.datingcopilot.keyboard.chat

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.datingcopilot.keyboard.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ScreenshotAnalysisActivity : AppCompatActivity() {

    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageUri = intent.getParcelableExtra<Uri>("image_uri")
        val convoJson = intent.getStringExtra("conversation") ?: "[]"
        val suggsJson = intent.getStringExtra("suggestions") ?: "[]"

        val conversations: List<ChatMessage> = gson.fromJson(convoJson, object : TypeToken<List<ChatMessage>>() {}.type)
        val suggestions: List<SuggestionOption> = gson.fromJson(suggsJson, object : TypeToken<List<SuggestionOption>>() {}.type)

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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        topBar.addView(backBtn)

        val title = TextView(this).apply {
            text = "Analysis"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { gravity = Gravity.CENTER }
        }
        topBar.addView(title)
        root.addView(topBar)

        // Screenshot
        if (imageUri != null) {
            val imageView = ImageView(this).apply {
                setImageURI(imageUri)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (300 * resources.displayMetrics.density).toInt()
                ).apply {
                    bottomMargin = (20 * resources.displayMetrics.density).toInt()
                }
                alpha = 0f
            }
            root.addView(imageView)
            imageView.postDelayed({
                imageView.animate().alpha(1f).setDuration(500).start()
            }, 200)
        }

        // Conversation section
        if (conversations.isNotEmpty()) {
            val convoLabel = sectionLabel("Chat")
            root.addView(convoLabel)

            val convoContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (24 * resources.displayMetrics.density).toInt() }
            }

            conversations.forEachIndexed { i, msg ->
                val bubble = createBubble(msg)
                convoContainer.addView(bubble)
                // Staggered fade-in
                bubble.postDelayed({
                    bubble.animate().alpha(1f).setDuration(400).setStartDelay((i * 150).toLong()).start()
                }, 400)
            }
            root.addView(convoContainer)
        }

        // Suggestions section
        if (suggestions.isNotEmpty()) {
            val suggLabel = sectionLabel("Suggestions")
            root.addView(suggLabel)

            suggestions.forEachIndexed { i, sug ->
                val card = createSuggestionCard(sug)
                card.alpha = 0f
                root.addView(card)
                card.postDelayed({
                    card.animate().alpha(1f).translationY(0f).setDuration(500)
                        .setStartDelay((i * 200 + 600).toLong()).start()
                }, 400)
            }
        }

        scrollView.addView(root)
        setContentView(scrollView)
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
        }
    }

    private fun createBubble(msg: ChatMessage): View {
        val isYou = msg.sender == "you"
        val density = resources.displayMetrics.density
        val bubble = TextView(this).apply {
            text = msg.text
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(
                (14 * density).toInt(),
                (10 * density).toInt(),
                (14 * density).toInt(),
                (10 * density).toInt()
            )
            val bg = GradientDrawable()
            bg.cornerRadius = 16 * density
            bg.setColor(resources.getColor(if (isYou) R.color.bubble_you else R.color.bubble_them, null))
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                (280 * density).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isYou) Gravity.END else Gravity.START
                topMargin = (6 * density).toInt()
            }
            alpha = 0f
        }
        return bubble
    }

    private fun createSuggestionCard(sug: SuggestionOption): View {
        val density = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * density).toInt() }
            setPadding(
                (16 * density).toInt(),
                (14 * density).toInt(),
                (16 * density).toInt(),
                (14 * density).toInt()
            )
            val bg = GradientDrawable()
            bg.cornerRadius = 16 * density
            bg.setColor(resources.getColor(R.color.bg_card, null))
            bg.setStroke(1, resources.getColor(R.color.glass_border, null))
            background = bg
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val badge = TextView(this).apply {
            text = sug.persona.replaceFirstChar { it.uppercase() }
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.accent_violet, null))
            setPadding(
                (8 * density).toInt(), (3 * density).toInt(),
                (8 * density).toInt(), (3 * density).toInt()
            )
            val bg = GradientDrawable()
            bg.cornerRadius = 8 * density
            bg.setColor(resources.getColor(R.color.bg_surface, null))
            background = bg
        }
        topRow.addView(badge)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        topRow.addView(spacer)

        val confView = TextView(this).apply {
            text = "${sug.confidence}%"
            textSize = 11f
            setTextColor(resources.getColor(R.color.text_muted, null))
        }
        topRow.addView(confView)
        card.addView(topRow)

        val textView = TextView(this).apply {
            text = sug.text
            textSize = 15f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setLineSpacing(4f, 1.0f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * density).toInt() }
        }
        card.addView(textView)

        val copyBtn = TextView(this).apply {
            text = "📋 Copy"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.accent_violet, null))
            gravity = Gravity.CENTER
            setPadding(
                (12 * density).toInt(), (6 * density).toInt(),
                (12 * density).toInt(), (6 * density).toInt()
            )
            val bg = GradientDrawable()
            bg.cornerRadius = 14 * density
            bg.setColor(resources.getColor(R.color.bg_surface, null))
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                topMargin = (10 * density).toInt()
            }
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("RizzSe", sug.text))
                Toast.makeText(this@ScreenshotAnalysisActivity, "Copied!", Toast.LENGTH_SHORT).show()
            }
        }
        card.addView(copyBtn)

        return card
    }
}
