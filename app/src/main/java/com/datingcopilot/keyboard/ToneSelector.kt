package com.datingcopilot.keyboard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class ToneSelector(
    private val context: Context,
    private val onToneSelected: (String) -> Unit
) {

    private val tones = listOf("friendly", "romantic", "bold", "witty", "playful", "chill", "direct", "flirty")
    private val toneEmojis = mapOf(
        "friendly" to "\uD83D\uDE0A", "romantic" to "\uD83D\uDC9C", "bold" to "\uD83D\uDCAA",
        "witty" to "\uD83E\uDDE0", "playful" to "\uD83D\uDE04", "chill" to "\uD83D\uDECC\uFE0F",
        "direct" to "\uD83C\uDFAF", "flirty" to "\uD83D\uDE09"
    )
    private val chipViews = mutableListOf<TextView>()
    private var selectedTone = "playful"

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private val scrollView = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private val inner = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(6))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    init {
        for (tone in tones) {
            val chip = createChip(tone, tone == selectedTone)
            chip.setOnClickListener {
                selectedTone = tone
                updateChips()
                onToneSelected(tone)
            }
            chipViews.add(chip)
            inner.addView(chip)
        }

        scrollView.addView(inner)
        container.addView(scrollView)
    }

    val rootView: View get() = container

    fun setSelectedTone(tone: String) {
        selectedTone = tone
        updateChips()
    }

    private fun updateChips() {
        for ((i, chip) in chipViews.withIndex()) {
            val isSelected = tones[i] == selectedTone
            chip.background = getChipBackground(isSelected)
            chip.setTextColor(
                if (isSelected) ContextCompat.getColor(context, R.color.chip_selected_text)
                else ContextCompat.getColor(context, R.color.chip_unselected_text)
            )
            chip.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun createChip(tone: String, selected: Boolean): TextView {
        val displayName = tone.replaceFirstChar { it.uppercase() }
        return TextView(context).apply {
            text = "${toneEmojis[tone] ?: ""} $displayName"
            textSize = 12f
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            background = getChipBackground(selected)
            setTextColor(
                if (selected) ContextCompat.getColor(context, R.color.chip_selected_text)
                else ContextCompat.getColor(context, R.color.chip_unselected_text)
            )
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dpToPx(6), 0) }
        }
    }

    private fun getChipBackground(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(20).toFloat()
            if (selected) {
                setColor(ContextCompat.getColor(context, R.color.chip_selected))
                setStroke(dpToPx(1), ContextCompat.getColor(context, R.color.accent_purple))
            } else {
                setColor(ContextCompat.getColor(context, R.color.bg_dark))
                setStroke(dpToPx(1), ContextCompat.getColor(context, R.color.chip_unselected_border))
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
