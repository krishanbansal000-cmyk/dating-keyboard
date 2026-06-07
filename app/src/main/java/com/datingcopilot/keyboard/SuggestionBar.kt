package com.datingcopilot.keyboard

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.core.content.ContextCompat
import com.datingcopilot.keyboard.chat.SuggestionOption

class SuggestionBar(
    private val context: Context,
    private val onSuggestionTap: (String) -> Unit,
    private val onGenerateTap: (() -> Unit)? = null,
    private val onScreenshotTap: (() -> Unit)? = null
) {

    private val density = context.resources.displayMetrics.density
    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(dp(6), dp(4), dp(6), dp(2))
    }

    // ── Button row ──
    private val generateBtn = pillButton("✨ Generate", R.color.accent_violet, R.color.bg_card)
    private val screenshotBtn = pillButton("📸 Shot", R.color.text_primary, R.color.bg_card, borderColor = R.color.glass_border)

    // ── Animated loading dots ──
    private val loadingContainer = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        visibility = View.GONE
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(28)
        )
    }
    private val loadingDots = Array(3) {
        TextView(context).apply {
            text = "●"
            textSize = 10f
            setTextColor(ContextCompat.getColor(context, R.color.accent_violet))
            setPadding(dp(2), 0, dp(2), 0)
            alpha = 0.3f
        }
    }

    // ── Suggestions row ──
    private val suggestionsContainer = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    private val suggestionsInner = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    init {
        generateBtn.setOnClickListener { onGenerateTap?.invoke() }
        screenshotBtn.setOnClickListener { onScreenshotTap?.invoke() }
        loadingDots.forEach { loadingContainer.addView(it) }
        suggestionsContainer.addView(suggestionsInner)
        row.addView(generateBtn)
        row.addView(screenshotBtn)
        row.addView(loadingContainer)
        container.addView(row)
        container.addView(suggestionsContainer)
        suggestionsContainer.visibility = View.GONE
    }

    val rootView: LinearLayout get() = container

    // ── Public API ──

    fun showLoading(loading: Boolean) {
        if (loading) {
            hideAllButtons()
            suggestionsContainer.visibility = View.GONE
            loadingContainer.visibility = View.VISIBLE
            animateDots()
        } else {
            loadingContainer.visibility = View.GONE
        }
    }

    fun showScreenshotLoading(uri: Uri) {
        hideAllButtons()
        loadingContainer.visibility = View.GONE
        suggestionsContainer.visibility = View.VISIBLE
        suggestionsInner.removeAllViews()
        suggestionsInner.addView(screenshotPreview(uri))
        val status = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(40))
        }
        status.addView(ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(6) }
        })
        status.addView(TextView(context).apply {
            text = "Reading..."
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        })
        suggestionsInner.addView(status)
    }

    fun showSuggestions(options: List<SuggestionOption>) {
        hideAllButtons()
        loadingContainer.visibility = View.GONE
        suggestionsContainer.visibility = View.VISIBLE
        suggestionsInner.removeAllViews()
        // Show suggestions as compact horizontal pills
        options.forEach { opt ->
            val card = compactCard(opt)
            card.alpha = 0f
            card.postDelayed({ card.animate().alpha(1f).setDuration(300).start() }, 100)
            suggestionsInner.addView(card)
        }
    }

    fun showScreenshotToneOptions(onToneSelected: (String, String) -> Unit) {
        hideAllButtons()
        loadingContainer.visibility = View.GONE
        suggestionsContainer.visibility = View.VISIBLE
        suggestionsInner.removeAllViews()

        suggestionsInner.addView(TextView(context).apply {
            text = "Tone:"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(6) }
        })
        listOf(
            "🔥 Rizz" to "playful",
            "😂 Funny" to "witty",
            "😎 Chill" to "chill",
            "💯 Ask out" to "direct"
        ).forEach { (label, persona) ->
            val intent = when (persona) {
                "playful" -> "flirt"
                "witty" -> "recover_dry"
                "chill" -> "keep_going"
                "direct" -> "ask_date"
                else -> "keep_going"
            }
            suggestionsInner.addView(toneChip(label.split(" ")[1]) { onToneSelected(persona, intent) })
        }
    }

    fun showError() {
        suggestionsContainer.visibility = View.GONE
        loadingContainer.visibility = View.GONE
        generateBtn.text = "⚠️ Retry"
        generateBtn.visibility = View.VISIBLE
        screenshotBtn.visibility = View.VISIBLE
    }

    fun reset() {
        suggestionsContainer.visibility = View.GONE
        loadingContainer.visibility = View.GONE
        generateBtn.text = "✨ Generate"
        generateBtn.visibility = View.VISIBLE
        screenshotBtn.visibility = View.VISIBLE
    }

    // ── Animations ──

    private val animators = mutableListOf<ObjectAnimator>()
    private fun animateDots() {
        animators.forEach { it.cancel() }
        animators.clear()
        loadingDots.forEachIndexed { i, dot ->
            val anim = ObjectAnimator.ofFloat(dot, "alpha", 0.2f, 1f).apply {
                duration = 500
                startDelay = (i * 200).toLong()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            animators.add(anim)
        }
    }

    // ── UI Builders ──

    private fun hideAllButtons() {
        generateBtn.visibility = View.GONE
        screenshotBtn.visibility = View.GONE
    }

    private fun pillButton(text: String, textColor: Int, bgColor: Int, borderColor: Int? = null): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, textColor))
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(12), dp(5), dp(12), dp(5))
            val bg = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(ContextCompat.getColor(context, bgColor))
                if (borderColor != null) setStroke(dp(1), ContextCompat.getColor(context, borderColor))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun compactCard(option: SuggestionOption): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)
            )
            setPadding(dp(10), 0, dp(10), 0)
            val bg = GradientDrawable().apply {
                cornerRadius = dp(17).toFloat()
                setColor(ContextCompat.getColor(context, R.color.bg_card))
                setStroke(dp(1), ContextCompat.getColor(context, R.color.glass_border))
            }
            background = bg
        }
        card.addView(TextView(context).apply {
            text = option.text
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        card.addView(TextView(context).apply {
            text = "📋"
            textSize = 10f
            setPadding(dp(4), 0, 0, 0)
            setOnClickListener {
                onSuggestionTap(option.text)
            }
        })
        card.setOnClickListener { onSuggestionTap(option.text) }
        (card.layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, dp(6), 0)
        return card
    }

    private fun toneChip(label: String, onClick: () -> Unit): View {
        return TextView(context).apply {
            text = label
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.white))
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(12), 0, dp(12), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(17).toFloat()
                setColor(ContextCompat.getColor(context, R.color.accent_violet))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply { marginEnd = dp(6) }
            setOnClickListener { onClick() }
        }
    }

    private fun screenshotPreview(uri: Uri): View {
        val frame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(34)).apply { marginEnd = dp(6) }
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(ContextCompat.getColor(context, R.color.bg_card))
                setStroke(dp(1), ContextCompat.getColor(context, R.color.accent_violet))
            }
            clipToOutline = true
        }
        frame.addView(ImageView(context).apply {
            setImageURI(uri)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.8f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        })
        return frame
    }

    private fun dp(v: Int) = (v * density).toInt()
}
