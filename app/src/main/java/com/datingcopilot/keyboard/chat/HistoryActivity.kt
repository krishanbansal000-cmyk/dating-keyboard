package com.datingcopilot.keyboard.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.datingcopilot.keyboard.R

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        val history = AppHistoryStore.get(this)
        if (history.isEmpty()) {
            setContentView(TextView(this).apply {
                text = "No history yet"
                textSize = 16f
                setTextColor(resources.getColor(R.color.text_muted, null))
                gravity = Gravity.CENTER
            })
            return
        }

        val scrollView = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.bg_dark, null))
            setPadding(dp(16), dp(48), dp(16), dp(40))
        }

        root.addView(TextView(this).apply {
            text = "History"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        })

        history.forEachIndexed { index, entry ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
                setPadding(dp(16), dp(14), dp(16), dp(14))
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(resources.getColor(R.color.bg_card, null))
                    setStroke(dp(1), resources.getColor(R.color.glass_border, null))
                }
                background = bg
            }

            card.addView(TextView(this).apply {
                text = entry.type
                textSize = 10f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.accent_violet, null))
                setPadding(dp(8), dp(2), dp(8), dp(2))
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(4).toFloat()
                    setColor(resources.getColor(R.color.bg_surface, null))
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            })

            val preview = entry.preview
            card.addView(TextView(this).apply {
                text = preview
                textSize = 13f
                setTextColor(resources.getColor(R.color.text_primary, null))
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            })

            entry.suggestions.forEach { sug ->
                val sugRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(4) }
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    val bg = GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        setColor(resources.getColor(R.color.bg_surface, null))
                    }
                    background = bg
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("RizzSe", sug.text))
                        Toast.makeText(this@HistoryActivity, "Copied!", Toast.LENGTH_SHORT).show()
                    }
                }
                sugRow.addView(TextView(this).apply {
                    text = sug.text
                    textSize = 12f
                    setTextColor(resources.getColor(R.color.text_secondary, null))
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                sugRow.addView(TextView(this).apply {
                    text = "📋"
                    textSize = 12f
                    setPadding(dp(4), 0, 0, 0)
                })
                card.addView(sugRow)
            }

            root.addView(card)
        }

        scrollView.addView(root)
        setContentView(scrollView)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
