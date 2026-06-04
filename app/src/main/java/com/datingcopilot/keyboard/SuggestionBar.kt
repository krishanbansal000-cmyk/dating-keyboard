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
    private val onSuggestionTap: (String) -> Unit
) {

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(2))
    }

    private val topRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private val loadingIndicator = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
        isIndeterminate = true
        visibility = View.GONE
        val params = LinearLayout.LayoutParams(dpToPx(20), dpToPx(20))
        params.gravity = Gravity.CENTER
        layoutParams = params
        indeterminateDrawable?.setTint(ContextCompat.getColor(context, R.color.accent_violet))
    }

    private val emptyLabel = TextView(context).apply {
        text = "\u2728 RizzSe"
        textSize = 13f
        setTextColor(ContextCompat.getColor(context, R.color.accent_violet))
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
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

    init {
        suggestionsContainer.addView(suggestionsInner)
        topRow.addView(loadingIndicator)
        topRow.addView(emptyLabel)
        container.addView(topRow)
        container.addView(suggestionsContainer)
        suggestionsContainer.visibility = View.GONE
    }

    val rootView: LinearLayout get() = container

    fun showLoading(loading: Boolean) {
        loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            suggestionsContainer.visibility = View.GONE
            emptyLabel.visibility = View.GONE
        }
    }

    fun showSuggestions(options: List<SuggestionOption>) {
        suggestionsInner.removeAllViews()
        suggestionsContainer.visibility = View.VISIBLE
        loadingIndicator.visibility = View.GONE
        emptyLabel.visibility = View.GONE

        for (opt in options) {
            val card = createSuggestionCard(opt)
            suggestionsInner.addView(card)
        }
    }

    fun showError() {
        suggestionsContainer.visibility = View.GONE
        loadingIndicator.visibility = View.GONE
        emptyLabel.text = "\u26A0\uFE0F Check backend connection"
        emptyLabel.setTextColor(ContextCompat.getColor(context, R.color.error))
        emptyLabel.visibility = View.VISIBLE
    }

    private fun createSuggestionCard(option: SuggestionOption): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(220), LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(8))
        }

        val bg = GradientDrawable().apply {
            cornerRadius = dpToPx(12).toFloat()
            setColor(ContextCompat.getColor(context, R.color.suggestion_card_bg))
            setStroke(dpToPx(1), ContextCompat.getColor(context, R.color.glass_border))
        }
        card.background = bg

        val textView = TextView(context).apply {
            text = option.text
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            maxLines = 3
            typeface = Typeface.DEFAULT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(textView)

        val toneLabel = TextView(context).apply {
            text = "${option.persona.uppercase()} \u00B7 ${option.confidence}%"
            textSize = 10f
            setTextColor(ContextCompat.getColor(context, R.color.accent_violet))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4) }
        }
        card.addView(toneLabel)

        card.setOnClickListener {
            onSuggestionTap(option.text)
            suggestionsContainer.visibility = View.GONE
            emptyLabel.text = "\u2713 Copied"
            emptyLabel.setTextColor(ContextCompat.getColor(context, R.color.success))
            emptyLabel.visibility = View.VISIBLE
        }

        val marginParams = card.layoutParams as LinearLayout.LayoutParams
        marginParams.setMargins(0, 0, dpToPx(8), 0)
        card.layoutParams = marginParams

        return card
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
