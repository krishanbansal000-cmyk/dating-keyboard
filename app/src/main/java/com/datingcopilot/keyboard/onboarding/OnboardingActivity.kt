package com.datingcopilot.keyboard.onboarding

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.datingcopilot.keyboard.R
import com.datingcopilot.keyboard.chat.ChatActivity

class OnboardingActivity : AppCompatActivity() {

    private var currentPage = 0
    private val totalPages = 4
    private val answers = mutableMapOf<Int, Int>()

    private val questions = listOf(
        QuestionData(
            emoji = "💬",
            title = "How do you usually start a conversation?",
            options = listOf("With a compliment", "With a question", "With a tease", "Direct and bold")
        ),
        QuestionData(
            emoji = "🔥",
            title = "What's your dating vibe?",
            options = listOf("Warm & Friendly", "Romantic & Sweet", "Bold & Confident", "Funny & Witty")
        ),
        QuestionData(
            emoji = "😏",
            title = "How flirty do you want to be?",
            options = listOf("Keep it chill", "Playful banter", "Flirty vibes", "Full rizz mode")
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = FrameLayout(this).apply {
            setBackgroundColor(resources.getColor(R.color.bg_dark, null))
        }

        // Background gradient decoration
        val gradientBg = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    resources.getColor(R.color.bg_surface, null),
                    resources.getColor(R.color.bg_dark, null)
                )
            )
            background = gradient
        }
        root.addView(gradientBg)

        // Page indicator dots
        val indicatorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
                topMargin = (80 * resources.displayMetrics.density).toInt()
            }
        }

        val dotViews = mutableListOf<View>()
        for (i in 0 until totalPages) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (10 * resources.displayMetrics.density).toInt(),
                    (10 * resources.displayMetrics.density).toInt()
                ).apply {
                    marginStart = (6 * resources.displayMetrics.density).toInt()
                    marginEnd = (6 * resources.displayMetrics.density).toInt()
                }
                val shape = android.graphics.drawable.GradientDrawable()
                shape.shape = android.graphics.drawable.GradientDrawable.OVAL
                shape.setColor(resources.getColor(R.color.text_muted, null))
                background = shape
            }
            dotViews.add(dot)
            indicatorLayout.addView(dot)
        }
        root.addView(indicatorLayout)

        // Content container
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
                leftMargin = (24 * resources.displayMetrics.density).toInt()
                rightMargin = (24 * resources.displayMetrics.density).toInt()
            }
        }

        // Emoji
        val emojiView = TextView(this).apply {
            textSize = 56f
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setTextColor(resources.getColor(R.color.text_primary, null))
        }
        contentLayout.addView(emojiView)

        // Title
        val titleView = TextView(this).apply {
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, (32 * resources.displayMetrics.density).toInt())
        }
        contentLayout.addView(titleView)

        // Options container
        val optionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        contentLayout.addView(optionsLayout)

        root.addView(contentLayout)

        // Bottom CTA button
        val ctaButton = TextView(this).apply {
            text = "Next"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.white, null))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(
                (24 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt()
            )
            val bg = GradientDrawable()
            bg.cornerRadius = 48 * resources.displayMetrics.density
            bg.setColor(resources.getColor(R.color.accent_violet, null))
            background = bg
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM
                leftMargin = (24 * resources.displayMetrics.density).toInt()
                rightMargin = (24 * resources.displayMetrics.density).toInt()
                bottomMargin = (48 * resources.displayMetrics.density).toInt()
            }
            isClickable = true
            isFocusable = true
        }
        root.addView(ctaButton)

        setContentView(root)

        // Welcome page (page 0)
        fun showWelcomePage() {
            emojiView.text = "💜"
            titleView.text = "RizzSe\nAI Dating Coach"
            optionsLayout.removeAllViews()
            
            val subtitle = TextView(this).apply {
                text = "Get the best rizz for anywhere you chat. Tailored to your style."
                textSize = 16f
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(0, 0, 0, (16 * resources.displayMetrics.density).toInt())
            }
            optionsLayout.addView(subtitle)
            
            val features = listOf("📸 Upload screenshots", "🤖 AI-powered replies", "🎭 Switch personas instantly")
            features.forEach { feat ->
                val tv = TextView(this).apply {
                    text = feat
                    textSize = 15f
                    setTextColor(resources.getColor(R.color.text_secondary, null))
                    setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
                }
                optionsLayout.addView(tv)
            }
            ctaButton.text = "Get Started"
        }

        // Question page
        fun showQuestionPage(page: Int) {
            val q = questions[page - 1]
            emojiView.text = q.emoji
            titleView.text = q.title
            optionsLayout.removeAllViews()

            q.options.forEachIndexed { index, option ->
                val optionBtn = TextView(this).apply {
                    text = option
                    textSize = 16f
                    setTextColor(resources.getColor(R.color.text_primary, null))
                    setPadding(
                        (20 * resources.displayMetrics.density).toInt(),
                        (16 * resources.displayMetrics.density).toInt(),
                        (20 * resources.displayMetrics.density).toInt(),
                        (16 * resources.displayMetrics.density).toInt()
                    )
                    val bg = GradientDrawable()
                    bg.cornerRadius = 16 * resources.displayMetrics.density
                    bg.setColor(resources.getColor(R.color.bg_surface, null))
                    bg.setStroke(1, resources.getColor(R.color.glass_border, null))
                    background = bg
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (12 * resources.displayMetrics.density).toInt()
                    }
                    isClickable = true
                    isFocusable = true
                }
                
                optionBtn.setOnClickListener {
                    answers[page] = index
                    // Highlight selection
                    val selBg = GradientDrawable()
                    selBg.cornerRadius = 16 * resources.displayMetrics.density
                    selBg.setColor(resources.getColor(R.color.chip_selected, null))
                    selBg.setStroke(1, resources.getColor(R.color.accent_purple, null))
                    optionBtn.background = selBg
                    optionBtn.setTextColor(resources.getColor(R.color.white, null))
                }
                
                optionsLayout.addView(optionBtn)
            }
            ctaButton.text = if (page < totalPages - 1) "Next" else "Finish"
        }

        // Result page
        fun showResultPage() {
            emojiView.text = "✨"
            titleView.text = "Your Persona"
            optionsLayout.removeAllViews()

            val persona = calculatePersona()
            val personaName = persona.replaceFirstChar { it.uppercase() }
            
            val personaText = TextView(this).apply {
                text = personaName
                textSize = 32f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.accent_violet, null))
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
            }
            optionsLayout.addView(personaText)
            
            val descText = TextView(this).apply {
                text = getPersonaDescription(persona)
                textSize = 16f
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
            }
            optionsLayout.addView(descText)
            
            ctaButton.text = "Start Chatting"
        }

        fun updatePage() {
            // Update dots
            dotViews.forEachIndexed { index, dot ->
                val shape = android.graphics.drawable.GradientDrawable()
                shape.shape = android.graphics.drawable.GradientDrawable.OVAL
                if (index == currentPage) {
                    shape.setColor(resources.getColor(R.color.accent_violet, null))
                    dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).apply {
                        width = (24 * resources.displayMetrics.density).toInt()
                    }
                } else {
                    shape.setColor(resources.getColor(R.color.text_muted, null))
                    dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).apply {
                        width = (10 * resources.displayMetrics.density).toInt()
                    }
                }
                dot.background = shape
            }

            // Animate content change
            contentLayout.alpha = 0f
            contentLayout.translationY = 30f
            
            when (currentPage) {
                0 -> showWelcomePage()
                in 1..3 -> showQuestionPage(currentPage)
                else -> showResultPage()
            }
            
            contentLayout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        ctaButton.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                updatePage()
            } else {
                // Save persona and go to chat
                val prefs = getSharedPreferences("dating_copilot", MODE_PRIVATE)
                prefs.edit().putString("persona", calculatePersona()).apply()
                prefs.edit().putBoolean("onboarding_complete", true).apply()
                startActivity(Intent(this, ChatActivity::class.java))
                finish()
            }
        }

        updatePage()
    }

    private fun calculatePersona(): String {
        // Simple scoring based on answers
        var friendly = 0
        var romantic = 0
        var bold = 0
        var witty = 0
        
        answers.forEach { (_, answer) ->
            when (answer) {
                0 -> friendly += 2
                1 -> romantic += 1
                2 -> bold += 2
                3 -> witty += 2
            }
        }
        
        val scores = listOf(
            "friendly" to friendly,
            "romantic" to romantic,
            "bold" to bold,
            "witty" to witty
        )
        
        return scores.maxByOrNull { it.second }?.first ?: "playful"
    }

    private fun getPersonaDescription(persona: String): String {
        return when (persona) {
            "friendly" -> "You keep things warm, approachable, and genuine. People feel comfortable around you."
            "romantic" -> "You have a sweet, charming vibe. You build deep emotional connections effortlessly."
            "bold" -> "You're confident and direct. You take the lead and aren't afraid to make a move."
            "witty" -> "You're sharp, funny, and quick with a comeback. Humor is your superpower."
            else -> "You adapt to every situation. A true dating chameleon."
        }
    }
}

data class QuestionData(
    val emoji: String,
    val title: String,
    val options: List<String>
)
