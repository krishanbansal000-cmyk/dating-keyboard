package com.datingcopilot.keyboard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class ToneSelector(
    private val context: Context,
    private val onToneSelected: (String) -> Unit
) {

    private val tones = listOf("playful", "warm", "direct", "flirty", "witty")
    private val toneEmojis = mapOf(
        "playful" to "\uD83D\uDE04", "warm" to "\uD83D\uDE0A", "direct" to "\uD83C\uDFAF",
        "flirty" to "\uD83D\uDE09", "witty" to "\uD83E\uDDE0"
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
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
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
            chip.setTextColor(if (isSelected) 0xFF6366F1.toInt() else 0xFF94A3B8.toInt())
            chip.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun createChip(tone: String, selected: Boolean): TextView {
        return TextView(context).apply {
            text = "${toneEmojis[tone] ?: ""} $tone"
            textSize = 12f
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            background = getChipBackground(selected)
            setTextColor(if (selected) 0xFF6366F1.toInt() else 0xFF94A3B8.toInt())
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dpToPx(8), 0) }
        }
    }

    private fun getChipBackground(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(20).toFloat()
            if (selected) {
                setColor(0xFF1E2235.toInt())
                setStroke(dpToPx(1), 0xFF6366F1.toInt())
            } else {
                setColor(0xFF1A1D2E.toInt())
                setStroke(dpToPx(1), 0xFF2D3148.toInt())
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
