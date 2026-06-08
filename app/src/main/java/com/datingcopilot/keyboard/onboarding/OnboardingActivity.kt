package com.datingcopilot.keyboard.onboarding

import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.datingcopilot.keyboard.R
import com.datingcopilot.keyboard.chat.ChatActivity

class OnboardingActivity : AppCompatActivity() {

    private var currentPage = 0
    private val totalPages = 7
    private val answers = mutableMapOf<Int, Int>()
    private var waitingForKeyboardEnable = false

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
        ),
        QuestionData(
            emoji = "👤",
            title = "What's your gender?",
            options = listOf("Male", "Female", "Non-binary / Other")
        )
    )

    private val keyboardComponent by lazy { ComponentName("helium314.keyboard.debug", "helium314.keyboard.latin.LatinIME") }

    private lateinit var root: FrameLayout
    private lateinit var contentLayout: LinearLayout
    private lateinit var optionsLayout: LinearLayout
    private lateinit var emojiView: TextView
    private lateinit var titleView: TextView
    private lateinit var ctaButton: TextView
    private val dotViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        updatePage()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForKeyboardEnable && currentPage == 5) {
            updatePage()
        }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply {
            setBackgroundColor(resources.getColor(R.color.bg_dark, null))
        }

        val gradientBg = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    resources.getColor(R.color.bg_surface, null),
                    resources.getColor(R.color.bg_dark, null)
                )
            )
        }
        root.addView(gradientBg)

        val indicatorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                topMargin = (80 * resources.displayMetrics.density).toInt()
            }
        }
        for (i in 0 until totalPages) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (10 * resources.displayMetrics.density).toInt(),
                    (10 * resources.displayMetrics.density).toInt()
                ).apply {
                    marginStart = (6 * resources.displayMetrics.density).toInt()
                    marginEnd = (6 * resources.displayMetrics.density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(resources.getColor(R.color.text_muted, null))
                }
            }
            dotViews.add(dot)
            indicatorLayout.addView(dot)
        }
        root.addView(indicatorLayout)

        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                leftMargin = (24 * resources.displayMetrics.density).toInt()
                rightMargin = (24 * resources.displayMetrics.density).toInt()
            }
        }

        emojiView = TextView(this).apply {
            textSize = 56f
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setTextColor(resources.getColor(R.color.text_primary, null))
        }
        contentLayout.addView(emojiView)

        titleView = TextView(this).apply {
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, (32 * resources.displayMetrics.density).toInt())
        }
        contentLayout.addView(titleView)

        optionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        contentLayout.addView(optionsLayout)

        root.addView(contentLayout)

        ctaButton = TextView(this).apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resources.getColor(R.color.white, null))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(
                (24 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt()
            )
            background = GradientDrawable().apply {
                cornerRadius = 48 * resources.displayMetrics.density
                setColor(resources.getColor(R.color.accent_violet, null))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                leftMargin = (24 * resources.displayMetrics.density).toInt()
                rightMargin = (24 * resources.displayMetrics.density).toInt()
                bottomMargin = (48 * resources.displayMetrics.density).toInt()
            }
            isClickable = true
            isFocusable = true
        }
        ctaButton.setOnClickListener { handlePrimaryAction() }
        root.addView(ctaButton)

        setContentView(root)
    }

    private fun handlePrimaryAction() {
        when {
            currentPage in 0..4 -> {
                currentPage++
                updatePage()
            }
            currentPage == 5 -> {
                if (isKeyboardEnabled()) {
                    currentPage++
                    updatePage()
                } else {
                    waitingForKeyboardEnable = true
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }
            }
            else -> finishOnboarding()
        }
    }

    private fun updatePage() {
        dotViews.forEachIndexed { index, dot ->
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (index == currentPage) resources.getColor(R.color.accent_violet, null) else resources.getColor(R.color.text_muted, null))
            }
            dot.background = shape
            dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).apply {
                width = if (index == currentPage) (24 * resources.displayMetrics.density).toInt() else (10 * resources.displayMetrics.density).toInt()
            }
        }

        contentLayout.alpha = 0f
        contentLayout.translationY = 30f

        when (currentPage) {
            0 -> showWelcomePage()
            in 1..4 -> showQuestionPage(currentPage)
            5 -> showKeyboardSetupPage()
            else -> showResultPage()
        }

        contentLayout.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun showWelcomePage() {
        emojiView.text = "💜"
        titleView.text = "RizzSe\nAI Dating Coach"
        optionsLayout.removeAllViews()

        optionsLayout.addView(TextView(this).apply {
            text = "Get the best rizz for anywhere you chat. Tailored to your style."
            textSize = 16f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, 0, 0, (16 * resources.displayMetrics.density).toInt())
        })

        listOf(
            "📸 Upload screenshots",
            "🤖 AI-powered replies",
            "🎭 Switch personas instantly",
            "⌨️ Enable the keyboard in system settings"
        ).forEach { feat ->
            optionsLayout.addView(TextView(this).apply {
                text = feat
                textSize = 15f
                setTextColor(resources.getColor(R.color.text_secondary, null))
                setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
            })
        }

        ctaButton.text = "Get Started"
    }

    private fun showQuestionPage(page: Int) {
        val q = questions[page - 1]
        emojiView.text = q.emoji
        titleView.text = q.title
        optionsLayout.removeAllViews()

        q.options.forEachIndexed { index, option ->
            val optionBtn = createChoiceButton(option)
            optionBtn.setOnClickListener {
                answers[page] = index
                optionBtn.background = GradientDrawable().apply {
                    cornerRadius = 16 * resources.displayMetrics.density
                    setColor(resources.getColor(R.color.chip_selected, null))
                    setStroke(1, resources.getColor(R.color.accent_violet, null))
                }
                optionBtn.setTextColor(resources.getColor(R.color.white, null))
            }
            optionsLayout.addView(optionBtn)
        }

        ctaButton.text = if (page < 4) "Next" else "Next"
    }

    private fun showKeyboardSetupPage() {
        emojiView.text = "⌨️"
        titleView.text = "Enable the Keyboard"
        optionsLayout.removeAllViews()

        val enabled = isKeyboardEnabled()

        optionsLayout.addView(TextView(this).apply {
            text = "Android requires you to manually enable RizzSe before it can appear as a keyboard."
            textSize = 16f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, 0, 0, (16 * resources.displayMetrics.density).toInt())
        })

        optionsLayout.addView(TextView(this).apply {
            text = if (enabled) {
                "RizzSe is already enabled. You can continue or open the keyboard picker."
            } else {
                "Tap below to open Allowed keyboards / Input method settings and turn it on."
            }
            textSize = 15f
            setTextColor(resources.getColor(R.color.text_primary, null))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, 0, 0, (20 * resources.displayMetrics.density).toInt())
        })

        if (enabled) {
            val pickerBtn = createChoiceButton("Show Keyboard Picker")
            pickerBtn.setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
            optionsLayout.addView(pickerBtn)
        }

        ctaButton.text = if (enabled) "Continue" else "Open Keyboard Settings"
    }

    private fun showResultPage() {
        emojiView.text = "✨"
        titleView.text = "Your Persona"
        optionsLayout.removeAllViews()

        val persona = calculatePersona()
        val personaName = persona.replaceFirstChar { it.uppercase() }

        optionsLayout.addView(TextView(this).apply {
            text = personaName
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resources.getColor(R.color.accent_violet, null))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
        })

        optionsLayout.addView(TextView(this).apply {
            text = getPersonaDescription(persona)
            textSize = 16f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        })

        ctaButton.text = "Start Chatting"
    }

    private fun createChoiceButton(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(
                (20 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (20 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt()
            )
            background = GradientDrawable().apply {
                cornerRadius = 16 * resources.displayMetrics.density
                setColor(resources.getColor(R.color.bg_surface, null))
                setStroke(1, resources.getColor(R.color.glass_border, null))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * resources.displayMetrics.density).toInt()
            }
            isClickable = true
            isFocusable = true
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any {
            it.packageName == packageName
                    || it.id == keyboardComponent.flattenToString()
                    || it.packageName == "helium314.keyboard.debug"
        }
    }

    private fun finishOnboarding() {
        val prefs = getSharedPreferences("dating_copilot", MODE_PRIVATE)
        prefs.edit().putString("persona", calculatePersona()).apply()
        prefs.edit().putBoolean("onboarding_complete", true).apply()
        val genderOptions = listOf("male", "female", "non_binary")
        val genderAnswer = answers[4]
        val userGender = if (genderAnswer != null && genderAnswer < genderOptions.size) genderOptions[genderAnswer] else "male"
        prefs.edit().putString("user_gender", userGender).apply()
        startActivity(Intent(this, ChatActivity::class.java))
        finish()
    }

    private fun calculatePersona(): String {
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
