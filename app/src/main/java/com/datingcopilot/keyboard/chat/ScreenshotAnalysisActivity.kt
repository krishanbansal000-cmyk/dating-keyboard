package com.datingcopilot.keyboard.chat

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import com.datingcopilot.keyboard.ApiClient
import com.datingcopilot.keyboard.R
import com.mikepenz.iconics.dsl.iconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenshotAnalysisActivity : AppCompatActivity() {

    private val apiClient by lazy { ApiClient(this) }
    private lateinit var loadingState: View
    private lateinit var insightsSection: LinearLayout
    private lateinit var convoSection: LinearLayout
    private lateinit var suggSection: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imagePaths = intent.getStringArrayExtra("image_paths") ?: arrayOf()
        val imageUri = intent.data
        val chatContext = intent.getStringExtra("chat_context") ?: ""
        val persona = intent.getStringExtra("persona") ?: "playful"
        val intentType = intent.getStringExtra("intent") ?: "keep_going"
        val platform = intent.getStringExtra("platform") ?: "whatsapp"

        val scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.bg_dark, null))
            setPadding(dp(18), statusBarHeight() + dp(12), dp(18), dp(32))
        }

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        val backBtn = TextView(this).apply {
            text = "‹"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.white, null))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(resources.getColor(R.color.bg_surface, null))
            }
            isClickable = true
            setOnClickListener { finish() }
        }
        topBar.addView(backBtn)

        topBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), 0)
        })

        val title = TextView(this).apply {
            text = "RizzSe"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFFFF38F8.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { gravity = Gravity.CENTER }
        }
        topBar.addView(title)
        root.addView(topBar)

        // Screenshot preview
        if (imagePaths.isNotEmpty()) {
            val previewCard = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(360)
                ).apply { bottomMargin = dp(16) }
                val bg = GradientDrawable()
                bg.cornerRadius = dp(20).toFloat()
                bg.setColor(resources.getColor(R.color.bg_surface, null))
                bg.setStroke(dp(1), resources.getColor(R.color.glass_border, null))
                background = bg
                clipChildren = true
            }
            
            val imgView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setImageURI(Uri.fromFile(java.io.File(imagePaths[0])))
            }
            previewCard.addView(imgView)
            
            // Gradient overlay at bottom
            previewCard.addView(View(this).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0x00000000, 0x80000000.toInt())
                )
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dp(80),
                    Gravity.BOTTOM
                )
            })
            
            // Label overlay
            previewCard.addView(TextView(this).apply {
                text = if (imagePaths.size == 1) "Long screenshot captured" else "${imagePaths.size} screenshots captured"
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(dp(16), dp(0), dp(16), dp(16))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START
                )
            })
            
            // Tap to expand indicator
            previewCard.addView(TextView(this).apply {
                text = "Tap to view full image"
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFFFF38F8.toInt())
                setPadding(dp(14), dp(4), dp(14), dp(4))
                setBackgroundColor(0x66000000.toInt())
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply { topMargin = dp(12); rightMargin = dp(12) }
            })
            
            // Make entire card clickable - show full-screen preview in-app.
            previewCard.isClickable = true
            previewCard.isFocusable = true
            previewCard.setOnClickListener {
                showFullScreenPreview(Uri.fromFile(java.io.File(imagePaths[0])))
            }
            
            root.addView(previewCard)
        } else if (imageUri != null) {
            val previewCard = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(360)
                ).apply { bottomMargin = dp(16) }
                val bg = GradientDrawable()
                bg.cornerRadius = dp(20).toFloat()
                bg.setColor(resources.getColor(R.color.bg_surface, null))
                background = bg
                clipChildren = true
            }
            val imgView = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setImageURI(imageUri)
            }
            previewCard.addView(imgView)
            previewCard.isClickable = true
            previewCard.isFocusable = true
            previewCard.setOnClickListener { showFullScreenPreview(imageUri) }
            root.addView(previewCard)
        }

        // Loading panel
        val loadView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(24), dp(32), dp(24), dp(32))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(resources.getColor(R.color.bg_card, null))
                setStroke(dp(1), resources.getColor(R.color.glass_border, null))
            }
        }
        loadingState = loadView

        // Animated ring - use arc drawable for visible rotation
        val ringContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(16) }
        }
        loadView.addView(ringContainer)

        val ring = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleLarge).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.accent_violet, null))
            layoutParams = FrameLayout.LayoutParams(dp(64), dp(64))
        }
        ringContainer.addView(ring)

        loadView.addView(TextView(this).apply {
            text = "RizzSe is thinking"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
        })

        val imageCount = imagePaths.size
        loadView.addView(TextView(this).apply {
            text = if (imageCount > 1) "Reading $imageCount screenshots..." else "Reading the screenshot and crafting replies"
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_muted, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        })

        root.addView(loadView)

        convoSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        root.addView(convoSection)

        insightsSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        root.addView(insightsSection)

        suggSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        root.addView(suggSection)

        scrollView.addView(root)
        setContentView(scrollView)

        if (imagePaths.isNotEmpty()) {
            runMultiImageAnalysis(imagePaths.toList(), chatContext, persona, intentType, platform)
        } else if (imageUri != null) {
            runAnalysis(imageUri, persona, intentType, platform)
        }
    }

    private fun runMultiImageAnalysis(imagePaths: List<String>, chatContext: String, persona: String, intentType: String, platform: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uris = imagePaths.map { Uri.fromFile(java.io.File(it)) }
                val response = if (uris.size == 1) {
                    apiClient.uploadScreenshot(uris.first(), persona, this@ScreenshotAnalysisActivity, intentType, platform)
                } else {
                    apiClient.uploadScreenshots(uris, chatContext, this@ScreenshotAnalysisActivity, persona, intentType, platform)
                }
                withContext(Dispatchers.Main) {
                    if (response != null && response.suggestions?.isNotEmpty() == true) {
                        val suggestions = response.suggestions.orEmpty()
                        showResults(response)
                        saveToKeyboardSuggestions(suggestions)
                    } else {
                        showError("Analysis failed")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showError("Error: ${e.message?.take(80)}") }
            } finally {
                // Keep captured long screenshots available for the on-screen preview.
            }
        }
    }

    private fun runAnalysis(uri: Uri, persona: String, intentType: String, platform: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiClient.uploadScreenshot(uri, persona, this@ScreenshotAnalysisActivity, intentType, platform)
                withContext(Dispatchers.Main) {
                    if (response != null) showResults(response) else showError("Analysis failed")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showError("Error: ${e.message?.take(50)}") }
            }
        }
    }

    private fun saveToKeyboardSuggestions(suggestions: List<SuggestionOption>) {
        val json = com.google.gson.Gson().toJson(suggestions)
        getSharedPreferences("dating_copilot", Context.MODE_MULTI_PROCESS)
            .edit()
            .putString("pending_keyboard_suggestions", json)
            .commit()

        val handoff = Intent("com.datingcopilot.keyboard.RIZZSE_SUGGESTIONS").apply {
            setPackage(packageName)
            putExtra("pending_keyboard_suggestions", json)
            putExtra("pending_keyboard_context", getSharedPreferences("dating_copilot", Context.MODE_PRIVATE).getString("pending_chat_context", "") ?: "")
        }
        sendBroadcast(handoff)
    }

    private fun showResults(response: AnalyzeResponse) {
        val density = resources.displayMetrics.density
        loadingState.visibility = View.GONE
        convoSection.removeAllViews()
        insightsSection.removeAllViews()
        suggSection.removeAllViews()
        AppHistoryStore.add(
            this,
            "Screenshot",
            response.conversation?.lastOrNull()?.text ?: "Screenshot analysis",
            response.suggestions.orEmpty()
        )

        response.insights?.let { showInsights(it) }

        response.conversation?.forEachIndexed { i, msg ->
            val isYou = msg.sender == "you"
            val bubble = TextView(this).apply {
                text = msg.text
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_primary, null))
                setPadding(dp(14), dp(10), dp(14), dp(10))
                val bg = GradientDrawable()
                bg.cornerRadius = 16 * density
                bg.setColor(resources.getColor(if (isYou) R.color.bubble_you else R.color.bubble_them, null))
                background = bg
                layoutParams = LinearLayout.LayoutParams(
                    (280 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = if (isYou) Gravity.END else Gravity.START
                    topMargin = (8 * density).toInt()
                }
                translationY = 30f
                alpha = 0f
            }
            convoSection.addView(bubble)
            bubble.postDelayed({
                bubble.animate().translationY(0f).alpha(1f).setDuration(350)
                    .setStartDelay((i * 120).toLong()).start()
            }, 200)
            convoSection.visibility = View.VISIBLE
        }

        val delay = ((response.conversation?.size ?: 0) * 120L + 500L)
        convoSection.postDelayed({ showSuggestions(response.suggestions.orEmpty()) }, delay)
    }

    private fun showInsights(insights: ConversationInsights) {
        val header = TextView(this).apply {
            text = "Her energy"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setPadding(0, dp(18), 0, dp(10))
        }
        insightsSection.addView(header)

        insightsSection.addView(makeInsightCard("Energy", insights.herEnergy.ifBlank { "No clear read yet" }, "score ${insights.conversationScore}/100"))
        if (insights.comments.isNotEmpty()) {
            insights.comments.forEach { comment ->
                insightsSection.addView(makeInsightCard("Comment", comment, ""))
            }
        }
        if (insights.greenFlags.isNotEmpty()) {
            insightsSection.addView(makeChipCard("Green flags", insights.greenFlags, R.color.accent_pink))
        }
        if (insights.redFlags.isNotEmpty()) {
            insightsSection.addView(makeChipCard("Watch outs", insights.redFlags, R.color.error))
        }
        if (insights.nextMove.isNotBlank()) {
            insightsSection.addView(makeInsightCard("Next move", insights.nextMove, ""))
        }
        insightsSection.visibility = View.VISIBLE
    }

    private fun makeInsightCard(title: String, body: String, meta: String): LinearLayout {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * density).toInt() }
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(resources.getColor(R.color.bg_card, null))
                setStroke(1, resources.getColor(R.color.glass_border, null))
            }
            addView(TextView(this@ScreenshotAnalysisActivity).apply {
                text = title
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.accent_pink, null))
            })
            addView(TextView(this@ScreenshotAnalysisActivity).apply {
                text = body
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_primary, null))
                setLineSpacing(3f, 1.0f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (6 * density).toInt() }
            })
            if (meta.isNotBlank()) {
                addView(TextView(this@ScreenshotAnalysisActivity).apply {
                    text = meta
                    textSize = 10f
                    setTextColor(resources.getColor(R.color.text_muted, null))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (6 * density).toInt() }
                })
            }
        }
    }

    private fun makeChipCard(title: String, items: List<String>, accent: Int): LinearLayout {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * density).toInt() }
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(resources.getColor(R.color.bg_card, null))
                setStroke(1, resources.getColor(R.color.glass_border, null))
            }
            addView(TextView(this@ScreenshotAnalysisActivity).apply {
                text = title
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(accent, null))
            })
            addView(LinearLayout(this@ScreenshotAnalysisActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (8 * density).toInt(), 0, 0)
                items.take(3).forEachIndexed { index, item ->
                    addView(TextView(this@ScreenshotAnalysisActivity).apply {
                        text = item
                        textSize = 12f
                        setTextColor(resources.getColor(R.color.text_primary, null))
                        setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
                        background = GradientDrawable().apply {
                            cornerRadius = 999f
                            setColor(resources.getColor(R.color.bg_surface, null))
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            if (index > 0) leftMargin = (8 * density).toInt()
                        }
                    })
                }
            })
        }
    }

    private fun showSuggestions(suggestions: List<SuggestionOption>) {
        loadingState.visibility = View.GONE
        val density = resources.displayMetrics.density
        val label = TextView(this).apply {
            text = "Try these replies"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setPadding(0, dp(22), 0, dp(12))
        }
        suggSection.addView(label)

        suggestions.forEachIndexed { i, sug ->
            val card = createCard(sug, density)
            card.alpha = 0f
            card.translationY = 20f
            suggSection.addView(card)
            card.postDelayed({
                card.animate().alpha(1f).translationY(0f).setDuration(350)
                    .setStartDelay((i * 150).toLong()).start()
            }, 100)
        }
        suggSection.visibility = View.VISIBLE
    }

    private fun createCard(sug: SuggestionOption, density: Float): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * density).toInt() }
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            val bg = GradientDrawable()
            bg.cornerRadius = 16 * density
            bg.setColor(resources.getColor(R.color.bg_card, null))
            bg.setStroke(1, resources.getColor(R.color.glass_border, null))
            background = bg
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * density).toInt() }
        }

        topRow.addView(TextView(this).apply {
            text = sug.persona.replaceFirstChar { it.uppercase() }
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.accent_violet, null))
            setPadding((8 * density).toInt(), (2 * density).toInt(), (8 * density).toInt(), (2 * density).toInt())
            val bg = GradientDrawable()
            bg.cornerRadius = 6 * density
            bg.setColor(resources.getColor(R.color.bg_surface, null))
            background = bg
        })

        topRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })

        topRow.addView(TextView(this).apply {
            text = "${sug.confidence}%"
            textSize = 10f
            setTextColor(resources.getColor(R.color.text_muted, null))
            setPadding(0, 0, (8 * density).toInt(), 0)
        })

        topRow.addView(ImageView(this).apply {
            setImageDrawable(
                this@ScreenshotAnalysisActivity.iconicsDrawable(GoogleMaterial.Icon.gmd_content_copy) {
                    color = colorInt(ContextCompat.getColor(this@ScreenshotAnalysisActivity, R.color.text_muted))
                    size = sizeDp(18)
                }
            )
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("RizzSe", sug.text))
                Toast.makeText(this@ScreenshotAnalysisActivity, "Copied!", Toast.LENGTH_SHORT).show()
            }
        })
        card.addView(topRow)

        card.addView(TextView(this).apply {
            text = sug.text
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setLineSpacing(3f, 1.0f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        return card
    }

    private fun showError(msg: String) {
        loadingState.visibility = View.GONE
        suggSection.removeAllViews()
        suggSection.visibility = View.VISIBLE
        suggSection.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(resources.getColor(R.color.bg_card, null))
                setStroke(dp(1), resources.getColor(R.color.accent_pink, null))
            }
            addView(TextView(this@ScreenshotAnalysisActivity).apply {
                text = "Analysis did not return suggestions"
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.text_primary, null))
            })
            addView(TextView(this@ScreenshotAnalysisActivity).apply {
                text = msg
                textSize = 13f
                setTextColor(resources.getColor(R.color.text_muted, null))
                setPadding(0, dp(8), 0, 0)
            })
        })
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun showFullScreenPreview(uri: Uri) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val layout = FrameLayout(this).apply {
            setBackgroundColor(0xFF050009.toInt())
            isClickable = true
            setOnClickListener { dialog.dismiss() }
        }

        layout.addView(ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setImageURI(uri)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(dp(8), dp(48), dp(8), dp(72)) }
            setOnClickListener { dialog.dismiss() }
        })

        layout.addView(TextView(this).apply {
            text = "Tap anywhere to close"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFFEBD5FF.toInt())
            setBackgroundColor(0x66000000.toInt())
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = dp(28) }
            setOnClickListener { dialog.dismiss() }
        })

        dialog.setContentView(layout)
        dialog.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }
}
