package com.datingcopilot.keyboard.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.datingcopilot.keyboard.R
import com.datingcopilot.keyboard.SettingsSheet
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val history = AppHistoryStore.get(this)

        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF0A0211.toInt(), 0xFF21002F.toInt(), 0xFF12001E.toInt())
            )
        }

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(22))
        }

        content.addView(topBar())
        content.addView(TextView(this).apply {
            text = "Reply History"
            textSize = 27f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, dp(26), 0, dp(3))
        })
        content.addView(TextView(this).apply {
            text = "Relive your smooth moments and track your success."
            textSize = 16f
            setTextColor(0xFFEBD5FF.toInt())
            setPadding(0, 0, dp(32), dp(22))
        })
        content.addView(searchBox())

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(18), 0, dp(22))
        }
        listOf("All", "Funny", "Flirty", "Witty", "Smooth").forEachIndexed { index, label ->
            filterRow.addView(filterChip(label, index == 0))
        }
        content.addView(filterRow)

        if (history.isEmpty()) {
            content.addView(emptyHistoryCard())
        } else {
            history.forEachIndexed { index, entry ->
                content.addView(historyCard(entry, index))
            }
        }

        scrollView.addView(content)
        main.addView(scrollView)
        main.addView(bottomNav())
        root.addView(main)
        root.addView(floatingLightning())
        setContentView(root)
    }

    private fun topBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@HistoryActivity).apply {
                text = "RizzSe"
                textSize = 27f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFFFF38F8.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(materialIconBubble(GoogleMaterial.Icon.gmd_bolt, dp(38), 0xFF2A1235.toInt(), 0xFFFF38F8.toInt()))
        }
    }

    private fun searchBox(): TextView {
        return TextView(this).apply {
            text = "Search conversations..."
            textSize = 16f
            setTextColor(0xFF9E86B7.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, 0, 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(0xFF24122D.toInt())
                setStroke(dp(1), 0xFF4A255E.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        }
    }

    private fun filterChip(label: String, selected: Boolean): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (selected) Color.WHITE else 0xFFCBB4DA.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (selected) 0xFFE13EF8.toInt() else 0xFF2A1832.toInt())
                if (!selected) setStroke(dp(1), 0xFF3D2948.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36)
            ).apply { marginEnd = dp(8) }
        }
    }

    private fun historyCard(entry: AppHistoryEntry, index: Int): LinearLayout {
        val bestSuggestion = entry.suggestions.firstOrNull()?.text ?: entry.preview
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(0xFF291633.toInt())
                setStroke(dp(1), 0xFF432251.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) }
            isClickable = true
            isFocusable = true
            setOnClickListener { copy(bestSuggestion) }

            val meta = LinearLayout(this@HistoryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            meta.addView(materialIconBubble(GoogleMaterial.Icon.gmd_auto_awesome, dp(36), 0xFF5B1173.toInt(), 0xFFEBD5FF.toInt()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) }
            })
            meta.addView(TextView(this@HistoryActivity).apply {
                text = "Tone: ${entry.type}\n${relativeTime(entry.timestamp)}"
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFFCBB4DA.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            meta.addView(TextView(this@HistoryActivity).apply {
                text = "${entry.suggestions.size} replies"
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFFFFB4DD.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, dp(10), 0)
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(0xFF5D1B48.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28))
            })
            addView(meta)

            val body = LinearLayout(this@HistoryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(18), 0, 0)
            }
            val previewIcon = if (entry.type.contains("screenshot", ignoreCase = true)) GoogleMaterial.Icon.gmd_camera_alt else GoogleMaterial.Icon.gmd_auto_awesome
            body.addView(materialIconBubble(previewIcon, dp(92), 0xFF5B1173.toInt(), 0xFFFF38F8.toInt()).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF090312.toInt(), 0xFF5B1173.toInt(), 0xFFE41487.toInt())).apply {
                    cornerRadius = dp(8).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(dp(92), dp(112)).apply { marginEnd = dp(16) }
            })
            body.addView(TextView(this@HistoryActivity).apply {
                text = entry.suggestions.joinToString("\n\n") { "\"${it.text}\"" }
                textSize = 17f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.text_primary, null))
                maxLines = 8
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(body)

            addView(TextView(this@HistoryActivity).apply {
                text = entry.preview
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFFCBB4DA.toInt())
                setPadding(dp(130), dp(10), 0, 0)
            })
        }
    }

    private fun emptyHistoryCard(): TextView {
        return TextView(this).apply {
            text = "No smooth moments yet. Analyze a chat to start building your reply history."
            textSize = 15f
            setTextColor(0xFFEBD5FF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(28), dp(18), dp(28))
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(0xFF291633.toInt())
                setStroke(dp(1), 0xFF432251.toInt())
            }
        }
    }

    private fun bottomNav(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply { setColor(0xFF1B0E23.toInt()) }
            addView(navItem("Home", false) { finish() })
            addView(navItem("History", true) {})
            if (!isRizzseKeyboardEnabled()) {
                addView(navItem("Setup", false) { startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)) })
            }
            addView(navItem("Settings", false) { SettingsSheet().show(supportFragmentManager, "settings") })
        }
    }

    private fun isRizzseKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        return imm.enabledInputMethodList.any { info ->
            info.packageName == packageName || info.id == "$packageName/.nboard.NboardImeService"
        }
    }

    private fun navItem(label: String, selected: Boolean, action: () -> Unit): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = if (selected) GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0xFF5B1173.toInt())
            } else null
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) }
            isClickable = true
            setOnClickListener { action() }
        }
        val icon = when (label) {
            "Home" -> GoogleMaterial.Icon.gmd_home
            "History" -> GoogleMaterial.Icon.gmd_history
            "Setup" -> GoogleMaterial.Icon.gmd_keyboard
            "Settings" -> GoogleMaterial.Icon.gmd_settings
            else -> GoogleMaterial.Icon.gmd_circle
        }
        container.addView(ImageView(this).apply {
            setImageDrawable(
                IconicsDrawable(this@HistoryActivity, icon).apply {
                    colorInt = if (selected) Color.WHITE else 0xFFC4B5FD.toInt()
                    sizeDp = 22
                }
            )
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        })
        container.addView(TextView(this@HistoryActivity).apply {
            text = label
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(if (selected) Color.WHITE else 0xFFC4B5FD.toInt())
            setPadding(0, dp(2), 0, 0)
        })
        return container
    }

    private fun floatingLightning(): ImageView {
        return ImageView(this).apply {
            setImageDrawable(
                IconicsDrawable(this@HistoryActivity, GoogleMaterial.Icon.gmd_bolt).apply {
                    colorInt = Color.WHITE
                    sizeDp = 28
                }
            )
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFE13EF8.toInt())
                setStroke(dp(2), 0xFF35D5FF.toInt())
            }
            elevation = dp(8).toFloat()
            layoutParams = FrameLayout.LayoutParams(dp(62), dp(62), Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = dp(18)
                bottomMargin = dp(72)
            }
            setOnClickListener { finish() }
        }
    }

    private fun iconBubble(iconRes: Int, size: Int, backgroundColor: Int, tint: Int): ImageView {
        return ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(this@HistoryActivity, iconRes))
            setColorFilter(tint)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(size / 4, size / 4, size / 4, size / 4)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(backgroundColor)
            }
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    private fun materialIconBubble(icon: GoogleMaterial.Icon, size: Int, backgroundColor: Int, tint: Int): ImageView {
        return ImageView(this).apply {
            setImageDrawable(
                IconicsDrawable(this@HistoryActivity, icon).apply {
                    colorInt = tint
                    sizeDp = (size / 3)
                }
            )
            scaleType = ImageView.ScaleType.CENTER
            setPadding(size / 4, size / 4, size / 4, size / 4)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(backgroundColor)
            }
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    private fun copy(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("RizzSe", text))
        Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
    }

    private fun relativeTime(timestamp: Long): String {
        val minutes = ((System.currentTimeMillis() - timestamp) / 60000).coerceAtLeast(0)
        return when {
            minutes < 2 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60} hours ago"
            else -> "Yesterday"
        }
    }


    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
