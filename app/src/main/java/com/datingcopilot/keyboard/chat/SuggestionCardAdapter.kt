package com.datingcopilot.keyboard.chat

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, (10 * resources.displayMetrics.density).toInt())
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

            val accentColor = when (suggestion.persona.lowercase()) {
                "safe" -> R.color.accent_pink
                "smooth" -> R.color.accent_violet
                "bold" -> R.color.accent_magenta
                else -> R.color.accent_violet
            }
            val accentInt = context.resources.getColor(accentColor, null)

            val leftStrip = View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 4 * density
                    setColor(accentInt)
                }
                layoutParams = LinearLayout.LayoutParams((4 * density).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
            }
            container.addView(leftStrip)

            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(
                    (16 * density).toInt(),
                    (14 * density).toInt(),
                    (16 * density).toInt(),
                    (14 * density).toInt()
                )
                val cardBg = GradientDrawable()
                cardBg.cornerRadius = 18 * density
                cardBg.setColor(context.resources.getColor(R.color.bg_card, null))
                cardBg.setStroke(1, context.resources.getColor(R.color.glass_border, null))
                background = cardBg
            }

            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * density).toInt() }
            }

            topRow.addView(TextView(context).apply {
                text = suggestion.persona.replaceFirstChar { it.uppercase() }
                textSize = 10f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(accentInt)
                setPadding(
                    (8 * density).toInt(),
                    (3 * density).toInt(),
                    (8 * density).toInt(),
                    (3 * density).toInt()
                )
                val bg = GradientDrawable()
                bg.cornerRadius = 8 * density
                bg.setColor(context.resources.getColor(R.color.bg_surface, null))
                background = bg
            })

            topRow.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })

            topRow.addView(TextView(context).apply {
                text = "${suggestion.confidence}%"
                textSize = 10f
                setTextColor(context.resources.getColor(R.color.text_muted, null))
                setPadding(0, 0, (8 * density).toInt(), 0)
            })

            topRow.addView(TextView(context).apply {
                text = "\uD83D\uDCCB"
                textSize = 16f
                setPadding((6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt())
                setOnClickListener { onCopyClicked(suggestion) }
            })

            card.addView(topRow)

            card.addView(TextView(context).apply {
                text = suggestion.text
                textSize = 15f
                setTextColor(context.resources.getColor(R.color.text_primary, null))
                setLineSpacing(4f, 1.0f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            container.addView(card)
        }
    }
}
