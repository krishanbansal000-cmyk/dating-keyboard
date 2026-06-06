package com.datingcopilot.keyboard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.datingcopilot.keyboard.chat.SuggestionOption

class SuggestionBar(
    private val context: Context,
    private val onSuggestionTap: (String) -> Unit,
    private val onGenerateTap: (() -> Unit)? = null,
    private val onScreenshotTap: (() -> Unit)? = null
) {

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(2))
    }

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

    // Generate button - shown when no suggestions are visible
    private val generateBtn = TextView(context).apply {
        text = "\u2728 Generate"
        textSize = 12f
        setTextColor(ContextCompat.getColor(context, R.color.accent_violet))
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))
        val bg = GradientDrawable().apply {
            cornerRadius = dpToPx(16).toFloat()
            setColor(ContextCompat.getColor(context, R.color.bg_card))
            setStroke(dpToPx(1), ContextCompat.getColor(context, R.color.accent_violet))
        }
        background = bg
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setOnClickListener { onGenerateTap?.invoke() }
    }

    private val screenshotBtn = TextView(context).apply {
        text = "📸 Screenshot"
        textSize = 12f
        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setPadding(dpToPx(14), dpToPx(6), dpToPx(14), dpToPx(6))
        val bg = GradientDrawable().apply {
            cornerRadius = dpToPx(16).toFloat()
            setColor(ContextCompat.getColor(context, R.color.bg_card))
            setStroke(dpToPx(1), ContextCompat.getColor(context, R.color.glass_border))
        }
        background = bg
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dpToPx(6) }
        setOnClickListener { onScreenshotTap?.invoke() }
    }

    // Loading dots shown while generating
    private val loadingDots = TextView(context).apply {
        text = ". . ."
        textSize = 14f
        setTextColor(ContextCompat.getColor(context, R.color.text_muted))
        visibility = View.GONE
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // Top row: generate btn | loading | [suggestions scroll]
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    init {
        suggestionsContainer.addView(suggestionsInner)
        row.addView(generateBtn)
        row.addView(screenshotBtn)
        row.addView(loadingDots)
        container.addView(row)
        container.addView(suggestionsContainer)
        suggestionsContainer.visibility = View.GONE
    }

    val rootView: LinearLayout get() = container

    fun showLoading(loading: Boolean) {
        if (loading) {
            generateBtn.visibility = View.GONE
            screenshotBtn.visibility = View.GONE
            suggestionsContainer.visibility = View.GONE
            loadingDots.visibility = View.VISIBLE
        } else {
            loadingDots.visibility = View.GONE
        }
    }

    fun showSuggestions(options: List<SuggestionOption>) {
        suggestionsInner.removeAllViews()
        loadingDots.visibility = View.GONE
        generateBtn.visibility = View.GONE
        screenshotBtn.visibility = View.GONE
        suggestionsContainer.visibility = View.VISIBLE

        for (opt in options) {
            val card = createSuggestionCard(opt)
            suggestionsInner.addView(card)
        }
    }

    fun showError() {
        suggestionsContainer.visibility = View.GONE
        loadingDots.visibility = View.GONE
        generateBtn.text = "\u26A0\uFE0F Retry"
        generateBtn.visibility = View.VISIBLE
        screenshotBtn.visibility = View.VISIBLE
    }

    fun reset() {
        suggestionsContainer.visibility = View.GONE
        loadingDots.visibility = View.GONE
        generateBtn.text = "\u2728 Generate"
        generateBtn.visibility = View.VISIBLE
        screenshotBtn.visibility = View.VISIBLE
    }

    private fun createSuggestionCard(option: SuggestionOption): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(36)
            )
            setPadding(dpToPx(12), 0, dpToPx(12), 0)
        }

        val bg = GradientDrawable().apply {
            cornerRadius = dpToPx(18).toFloat()
            setColor(ContextCompat.getColor(context, R.color.bg_card))
            setStroke(dpToPx(1), ContextCompat.getColor(context, R.color.glass_border))
        }
        card.background = bg

        val textView = TextView(context).apply {
            text = option.text
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        card.addView(textView)

        val confLabel = TextView(context).apply {
            text = "${option.confidence}%"
            textSize = 10f
            setTextColor(ContextCompat.getColor(context, R.color.accent_violet))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(6), 0, 0, 0)
        }
        card.addView(confLabel)

        card.setOnClickListener {
            onSuggestionTap(option.text)
            reset()
        }

        val marginParams = card.layoutParams as LinearLayout.LayoutParams
        marginParams.setMargins(0, 0, dpToPx(6), 0)
        card.layoutParams = marginParams

        return card
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
