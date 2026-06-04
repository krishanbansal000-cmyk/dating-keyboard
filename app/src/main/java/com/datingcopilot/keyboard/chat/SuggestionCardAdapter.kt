package com.datingcopilot.keyboard.chat

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.datingcopilot.keyboard.R

class SuggestionCardAdapter(
    private val onCopyClicked: (SuggestionOption) -> Unit
) : RecyclerView.Adapter<SuggestionCardAdapter.CardViewHolder>() {

    private val suggestions = mutableListOf<SuggestionOption>()

    fun updateSuggestions(newSuggestions: List<SuggestionOption>) {
        suggestions.clear()
        suggestions.addAll(newSuggestions)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val container = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.MarginLayoutParams(
                (260 * resources.displayMetrics.density).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (8 * resources.displayMetrics.density).toInt()
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
        }
        return CardViewHolder(container, onCopyClicked)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(suggestions[position])
    }

    override fun getItemCount(): Int = suggestions.size

    class CardViewHolder(
        private val container: LinearLayout,
        private val onCopyClicked: (SuggestionOption) -> Unit
    ) : RecyclerView.ViewHolder(container) {

        fun bind(suggestion: SuggestionOption) {
            val context = container.context
            val density = context.resources.displayMetrics.density
            
            container.removeAllViews()
            
            // Card background
            container.setPadding(
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt()
            )
            
            val cardBg = GradientDrawable()
            cardBg.cornerRadius = 16 * density
            cardBg.setColor(context.resources.getColor(R.color.bg_card, null))
            cardBg.setStroke(1, context.resources.getColor(R.color.glass_border, null))
            container.background = cardBg
            container.isClickable = true
            container.isFocusable = true
            
            // Suggestion text
            val textView = TextView(context).apply {
                text = suggestion.text
                textSize = 14f
                setTextColor(context.resources.getColor(R.color.text_primary, null))
                setPadding(0, 0, 0, (12 * density).toInt())
                maxLines = 4
            }
            container.addView(textView)
            
            // Bottom row: confidence + copy button
            val bottomRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            
            // Confidence badge
            val confidenceView = TextView(context).apply {
                text = "${suggestion.confidence}%"
                textSize = 11f
                setTextColor(context.resources.getColor(R.color.accent_violet, null))
                setPadding(
                    (8 * density).toInt(),
                    (4 * density).toInt(),
                    (8 * density).toInt(),
                    (4 * density).toInt()
                )
                val bg = GradientDrawable()
                bg.cornerRadius = 12 * density
                bg.setColor(context.resources.getColor(R.color.bg_surface, null))
                background = bg
            }
            bottomRow.addView(confidenceView)
            
            // Spacer
            bottomRow.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
            
            // Copy button
            val copyBtn = TextView(context).apply {
                text = "📋 Copy"
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(context.resources.getColor(R.color.white, null))
                setPadding(
                    (12 * density).toInt(),
                    (6 * density).toInt(),
                    (12 * density).toInt(),
                    (6 * density).toInt()
                )
                val bg = GradientDrawable()
                bg.cornerRadius = 12 * density
                bg.setColor(context.resources.getColor(R.color.accent_violet, null))
                background = bg
                isClickable = true
                isFocusable = true
                setOnClickListener { onCopyClicked(suggestion) }
            }
            bottomRow.addView(copyBtn)
            
            container.addView(bottomRow)
        }
    }
}
