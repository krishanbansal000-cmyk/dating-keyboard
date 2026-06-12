package com.datingcopilot.keyboard.chat

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.ScrollCaptureCallback
import android.view.ScrollCaptureSession
import android.view.Surface
import android.view.inputmethod.InputMethodManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.datingcopilot.keyboard.R
import com.datingcopilot.keyboard.SettingsSheet
import com.datingcopilot.keyboard.ChatContextService
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_IMAGE_PICKER = "com.datingcopilot.keyboard.OPEN_IMAGE_PICKER"
        const val EXTRA_RETURN_TO_KEYBOARD = "com.datingcopilot.keyboard.RETURN_TO_KEYBOARD"
        const val PREF_PENDING_KEYBOARD_SUGGESTIONS = "pending_keyboard_suggestions"
        const val PREF_PENDING_KEYBOARD_CONTEXT = "chat_context"
    }

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var suggestionsRecyclerView: RecyclerView
    private lateinit var suggestionAdapter: SuggestionCardAdapter
    private lateinit var suggestionsLabel: TextView
    private lateinit var toneBar: LinearLayout
    private lateinit var messageInput: android.widget.EditText
    private lateinit var loadingView: FrameLayout
    private lateinit var loadingTextView: TextView
    private lateinit var loadingPreviewImage: ImageView
    private lateinit var screenshotPreview: ImageView
    private lateinit var inputRow: LinearLayout
    private lateinit var emptyStateView: LinearLayout

    private val messages = mutableListOf<ChatMessage>()
    private var currentPersona = "playful"
    private var currentIntent = "flirt"
    private var currentPlatform = "whatsapp"
    private var selectedToneChip: TextView? = null
    private var lastInputText = ""
    private var loadingPulseAnimator: ObjectAnimator? = null
    private var lastPendingKeyboardSuggestionsJson: String? = null

    private val apiClient by lazy { com.datingcopilot.keyboard.ApiClient(this) }
    private val personaManager by lazy { PersonaManager(this) }
    private val gson by lazy { Gson() }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImageSelected(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if onboarding needed
        val prefs = getSharedPreferences("dating_copilot", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("onboarding_complete", false)) {
            startActivity(Intent(this, com.datingcopilot.keyboard.onboarding.OnboardingActivity::class.java))
            finish()
            return
        }
        currentPersona = prefs.getString("persona", "playful") ?: "playful"
        currentIntent = prefs.getString("intent", "flirt") ?: "flirt"
        currentPlatform = ChatContextService.getChatPlatform(this)

        val recommended = personaManager.getRecommendedPersona()
        if (recommended != null && recommended != currentPersona && prefs.getBoolean("auto_persona", false)) {
            currentPersona = recommended
            savePreference("persona", recommended)
        }

        buildUI()
        handleSendImage(intent)
        handleOpenImagePicker(intent)
        handleRizzseAction(intent)
        showPendingKeyboardSuggestions()
        try {
            window?.registerScrollCaptureCallback(RizzseScrollCaptureCallback())
        } catch (e: Throwable) {
            Log.w("ChatActivity", "ScrollCaptureCallback register failed: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSendImage(intent)
        handleOpenImagePicker(intent)
        handleRizzseAction(intent)
        showPendingKeyboardSuggestions()
    }

    override fun onResume() {
        super.onResume()
        showPendingKeyboardSuggestions()
        // Show keyboard if capture is active (IME may have been killed by projection dialog)
        val prefs = getSharedPreferences("dating_copilot", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("capture_active", false) && ::messageInput.isInitialized) {
            messageInput.postDelayed({
                messageInput.requestFocus()
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(messageInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        }
    }

    private fun handleRizzseAction(intent: Intent?) {
        val action = intent?.getStringExtra("rizzse_action") ?: return
        intent.removeExtra("rizzse_action")
        val chatContext = intent.getStringExtra("rizzse_chat_context") ?: ""
        intent.removeExtra("rizzse_chat_context")
        if (action == "generate") {
            Handler(Looper.getMainLooper()).postDelayed({ generateFromKeyboard(chatContext) }, 300)
        }
    }

    private fun generateFromKeyboard(chatContext: String) {
        val text = messageInput.text.toString().trim()
        val ctx = if (chatContext.isNotBlank()) chatContext else ChatContextService.getChatContext(this)

        if (text.isBlank() && ctx.isBlank()) {
            android.widget.Toast.makeText(this, "Type something or open a chat to get suggestions", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            showLoading(true)
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                apiClient.getSuggestionsWithContext(text, ctx, currentPersona, currentIntent, currentPlatform)
            }
            showLoading(false)
            if (result != null) {
                AppHistoryStore.add(this@ChatActivity, "Keyboard", text.ifBlank { ctx }, result)
                showSuggestions(result)
            } else {
                android.widget.Toast.makeText(this@ChatActivity, "Generate failed. Try again.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSendImage(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val imageUri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            imageUri?.let { handleImageSelected(it) }
        }
    }

    private fun handleOpenImagePicker(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_IMAGE_PICKER, false) == true) {
            intent.removeExtra(EXTRA_OPEN_IMAGE_PICKER)
            intent.removeExtra(EXTRA_RETURN_TO_KEYBOARD)
            Handler(Looper.getMainLooper()).post { showImagePicker() }
        }
    }

    private fun buildUI() {
        buildDashboardUI()
    }

    private fun buildLegacyUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(resources.getColor(R.color.bg_dark, null))
        }

        // Gradient background
        val gradientBg = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    resources.getColor(R.color.bg_surface, null),
                    resources.getColor(R.color.bg_dark, null)
                )
            )
            background = gradient
        }
        root.addView(gradientBg)

        // Main content container
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleText = TextView(this).apply {
            text = "RizzSe"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(titleText)

        val historyBtn = TextView(this).apply {
            text = "📋"
            textSize = 18f
            setPadding(0, 0, (8 * resources.displayMetrics.density).toInt(), 0)
            setOnClickListener {
                startActivity(Intent(this@ChatActivity, HistoryActivity::class.java))
            }
        }
        topBar.addView(historyBtn)

        val profileBtn = TextView(this).apply {
            text = "⚙️"
            textSize = 20f
            setPadding((8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt(), 0)
            setOnClickListener {
                SettingsSheet().show(supportFragmentManager, "settings")
            }
        }
        topBar.addView(profileBtn)

        val uploadBtn = TextView(this).apply {
            text = "➕"
            textSize = 24f
            setPadding((8 * resources.displayMetrics.density).toInt(), 0, 0, 0)
            setOnClickListener { showImagePicker() }
        }
        topBar.addView(uploadBtn)

        mainLayout.addView(topBar)

        // Divider
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            )
            setBackgroundColor(resources.getColor(R.color.divider, null))
        }
        mainLayout.addView(divider)

        // Chat area
        val chatContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        // Empty state
        emptyStateView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            val emptyTitle = TextView(this@ChatActivity).apply {
                text = "Make the next text hit"
                textSize = 24f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.text_primary, null))
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            }
            addView(emptyTitle)

            val emptySubtitle = TextView(this@ChatActivity).apply {
                text = "Paste or type below. Leave it empty for opener ideas."
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(
                    (32 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt(),
                    (32 * resources.displayMetrics.density).toInt(),
                    0
                )
                maxLines = 3
            }
            addView(emptySubtitle)

            val uploadHint = TextView(this@ChatActivity).apply {
                text = "Upload Screenshot"
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.accent_violet, null))
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(dp(24), dp(12), dp(24), dp(12))
                val outlineBg = GradientDrawable()
                outlineBg.cornerRadius = dp(32).toFloat()
                outlineBg.setColor(resources.getColor(R.color.bg_surface, null))
                outlineBg.setStroke(1, resources.getColor(R.color.accent_violet, null))
                background = outlineBg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER; topMargin = dp(24) }
                isClickable = true
                setOnClickListener { showImagePicker() }
            }
            addView(uploadHint)

        }
        chatContainer.addView(emptyStateView)

        // Screenshot preview
        screenshotPreview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (200 * resources.displayMetrics.density).toInt()
            ).apply { gravity = Gravity.TOP }
            visibility = View.GONE
        }
        chatContainer.addView(screenshotPreview)

        // Chat RecyclerView
        chatRecyclerView = RecyclerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(this@ChatActivity)
            visibility = View.GONE
        }
        chatAdapter = ChatAdapter()
        chatRecyclerView.adapter = chatAdapter
        chatContainer.addView(chatRecyclerView)

        mainLayout.addView(chatContainer)

        // Loading overlay with skeleton cards
        loadingView = FrameLayout(this).apply {
            setBackgroundColor(resources.getColor(R.color.bg_dark, null))
            alpha = 0.95f
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (180 * resources.displayMetrics.density).toInt()
            )

            val loadingContent = LinearLayout(this@ChatActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setPadding(
                    (16 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt(),
                    (16 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt()
                )
            }

            loadingPreviewImage = ImageView(this@ChatActivity).apply {
                visibility = View.GONE
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(
                    (92 * resources.displayMetrics.density).toInt(),
                    (70 * resources.displayMetrics.density).toInt()
                ).apply { bottomMargin = (8 * resources.displayMetrics.density).toInt() }
                val bg = GradientDrawable()
                bg.cornerRadius = 14 * resources.displayMetrics.density
                bg.setColor(resources.getColor(R.color.bg_surface, null))
                bg.setStroke(1, resources.getColor(R.color.accent_violet, null))
                background = bg
                clipToOutline = true
            }
            loadingContent.addView(loadingPreviewImage)

            loadingTextView = TextView(this@ChatActivity).apply {
                text = "Generating replies..."
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.accent_violet, null))
                setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
            }
            loadingContent.addView(loadingTextView)

            val loadingProgressBar = ProgressBar(this@ChatActivity).apply {
                isIndeterminate = true
                layoutParams = LinearLayout.LayoutParams(
                    (28 * resources.displayMetrics.density).toInt(),
                    (28 * resources.displayMetrics.density).toInt()
                ).apply { bottomMargin = (10 * resources.displayMetrics.density).toInt() }
            }
            loadingContent.addView(loadingProgressBar)

            // Skeleton cards row
            val skeletonRow = LinearLayout(this@ChatActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            for (i in 0..2) {
                val skeletonCard = View(this@ChatActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (100 * resources.displayMetrics.density).toInt(),
                        (140 * resources.displayMetrics.density).toInt()
                    ).apply {
                        marginStart = if (i > 0) (8 * resources.displayMetrics.density).toInt() else 0
                    }
                    val bg = GradientDrawable()
                    bg.cornerRadius = 18 * resources.displayMetrics.density
                    bg.setColor(resources.getColor(R.color.bg_surface, null))
                    background = bg
                }
                skeletonRow.addView(skeletonCard)
            }
            loadingContent.addView(skeletonRow)

            addView(loadingContent)
        }
        mainLayout.addView(loadingView)

        // Suggestions carousel
        suggestionsLabel = TextView(this).apply {
            text = "Pick a reply"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )
            visibility = View.GONE
        }
        mainLayout.addView(suggestionsLabel)

        suggestionsRecyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = LinearLayoutManager(this@ChatActivity, LinearLayoutManager.VERTICAL, false)
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (4 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (4 * resources.displayMetrics.density).toInt()
            )
            clipToPadding = false
            visibility = View.GONE
        }
        suggestionAdapter = SuggestionCardAdapter { suggestion ->
            copyToClipboard(suggestion.text)
            personaManager.recordCopy(suggestion.persona)
            Toast.makeText(this, "Reply copied. Paste it in your chat", Toast.LENGTH_SHORT).show()
        }
        suggestionsRecyclerView.adapter = suggestionAdapter
        mainLayout.addView(suggestionsRecyclerView)

        mainLayout.addView(sectionLabel("Tone"))
        val toneScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
        }

        toneBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                0,
                (16 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt()
            )
        }

        toneScroll.addView(toneBar)
        mainLayout.addView(toneScroll)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                0,
                (16 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt()
            )
        }
        inputRow.tag = "input_row"

        messageInput = android.widget.EditText(this).apply {
            hint = "Paste chat or leave empty"
            textSize = 14f
            maxLines = 3
            setTextColor(resources.getColor(R.color.text_primary, null))
            setHintTextColor(resources.getColor(R.color.text_muted, null))
            setPadding(
                (14 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (14 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt()
            )
            val bg = GradientDrawable()
            bg.cornerRadius = 18 * resources.displayMetrics.density
            bg.setColor(resources.getColor(R.color.bg_input, null))
            bg.setStroke(1, resources.getColor(R.color.glass_border, null))
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginEnd = (10 * resources.displayMetrics.density).toInt() }
        }
        inputRow.addView(messageInput)

        val generateBtn = TextView(this).apply {
            text = "Go"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.white, null))
            gravity = Gravity.CENTER
            setPadding(
                (18 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (18 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt()
            )
            val bg = GradientDrawable()
            bg.cornerRadius = 18 * resources.displayMetrics.density
            bg.setColor(resources.getColor(R.color.accent_violet, null))
            background = bg
            isClickable = true
            setOnClickListener {
                analyzeText(messageInput.text.toString().trim())
            }
        }
        inputRow.addView(generateBtn)
        mainLayout.addView(inputRow)

        root.addView(mainLayout)
        setContentView(root)

        messageInput.postDelayed({
            messageInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(messageInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 500)
    }

    private fun buildDashboardUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(resources.getColor(R.color.bg_dark, null))
        }

        val bg = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF0A0211.toInt(), 0xFF21002F.toInt(), 0xFF12001E.toInt())
            )
        }
        root.addView(bg)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        emptyStateView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), statusBarHeight() + dp(10), dp(14), dp(18))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        topBar.addView(TextView(this).apply {
            text = "RizzSe"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFFFF38F8.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topBar.addView(ImageView(this).apply {
            setImageDrawable(
                IconicsDrawable(this@ChatActivity, GoogleMaterial.Icon.gmd_settings).apply {
                    colorInt = 0xFFE9D5FF.toInt()
                    sizeDp = 18
                }
            )
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF231331.toInt())
                setStroke(dp(1), 0xFFE9D5FF.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener { SettingsSheet().show(supportFragmentManager, "settings") }
        })
        emptyStateView.addView(topBar)

        emptyStateView.addView(personaCard())

        emptyStateView.addView(analyzeCard())

        scroll.addView(emptyStateView)
        mainLayout.addView(scroll)

        chatRecyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = LinearLayoutManager(this@ChatActivity)
            visibility = View.GONE
        }
        chatAdapter = ChatAdapter()
        chatRecyclerView.adapter = chatAdapter
        mainLayout.addView(chatRecyclerView)

        suggestionsLabel = TextView(this).apply {
            text = "Pick a reply"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setPadding(dp(16), dp(12), dp(16), dp(8))
            visibility = View.GONE
        }
        mainLayout.addView(suggestionsLabel)

        suggestionsRecyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = LinearLayoutManager(this@ChatActivity, LinearLayoutManager.VERTICAL, false)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            clipToPadding = false
            visibility = View.GONE
        }
        suggestionAdapter = SuggestionCardAdapter { suggestion ->
            copyToClipboard(suggestion.text)
            Toast.makeText(this, "Reply copied. Paste it in your chat", Toast.LENGTH_SHORT).show()
        }
        suggestionsRecyclerView.adapter = suggestionAdapter
        mainLayout.addView(suggestionsRecyclerView)

        inputRow = LinearLayout(this).apply {
            tag = "input_row"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(0xFF160720.toInt())
                setStroke(dp(1), 0xFF4C1D65.toInt())
            }
        }
        messageInput = android.widget.EditText(this).apply {
            hint = "Type or tap for RizzSe keyboard"
            textSize = 14f
            setSingleLine(true)
            setTextColor(resources.getColor(R.color.text_primary, null))
            setHintTextColor(resources.getColor(R.color.text_muted, null))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(resources.getColor(R.color.bg_input, null))
                setStroke(dp(1), resources.getColor(R.color.glass_border, null))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }
        inputRow.addView(messageInput)
        inputRow.addView(TextView(this).apply {
            text = "Go"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.white, null))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(resources.getColor(R.color.accent_violet, null))
            }
            setOnClickListener { analyzeText(messageInput.text.toString().trim()) }
        })
        mainLayout.addView(inputRow)

        mainLayout.addView(bottomNav())
        root.addView(mainLayout)

        screenshotPreview = ImageView(this).apply { visibility = View.GONE }
        root.addView(screenshotPreview)

        loadingView = FrameLayout(this).apply {
            setBackgroundColor(0xEE0A0211.toInt())
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            val loadingContent = LinearLayout(this@ChatActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            loadingPreviewImage = ImageView(this@ChatActivity).apply { visibility = View.GONE }
            loadingContent.addView(loadingPreviewImage, LinearLayout.LayoutParams(dp(92), dp(70)))
            loadingTextView = TextView(this@ChatActivity).apply {
                text = "Generating replies..."
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFFFF38F8.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(14))
            }
            loadingContent.addView(loadingTextView)
            loadingContent.addView(ProgressBar(this@ChatActivity).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(32), dp(32)))
            addView(loadingContent)
        }
        root.addView(loadingView)

        setContentView(root)

        messageInput.postDelayed({
            messageInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(messageInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 400)
    }

    private fun personaCard(): View {
        val personaDisplay = currentPersona.replaceFirstChar { it.uppercase() }
        val recommendedPersona = personaManager.getRecommendedPersona()

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0xFF3A1648.toInt())
                setStroke(dp(1), 0xFF5D2872.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(ImageView(this@ChatActivity).apply {
                setImageDrawable(
                    IconicsDrawable(this@ChatActivity, GoogleMaterial.Icon.gmd_mood).apply {
                        colorInt = 0xFFC084FC.toInt()
                        sizeDp = 24
                    }
                )
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) }
            })
            addView(LinearLayout(this@ChatActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@ChatActivity).apply {
                    text = personaDisplay
                    textSize = 17f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(0xFFE9D5FF.toInt())
                })
                if (recommendedPersona != null && recommendedPersona != currentPersona) {
                    addView(TextView(this@ChatActivity).apply {
                        text = "⭐ Try ${recommendedPersona.replaceFirstChar { it.uppercase() }}"
                        textSize = 11f
                        setTextColor(0xFFFFD700.toInt())
                    })
                }
            })
            if (recommendedPersona != null && recommendedPersona != currentPersona) {
                addView(TextView(this@ChatActivity).apply {
                    text = "Switch"
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(0xFFFF38F8.toInt())
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(0xFF5B1173.toInt())
                    }
                    setOnClickListener {
                        savePreference("persona", recommendedPersona)
                        if (recommendedPersona == "playful") savePreference("intent", "flirt")
                        else if (recommendedPersona == "witty") savePreference("intent", "recover_dry")
                        else if (recommendedPersona == "chill") savePreference("intent", "keep_going")
                        else if (recommendedPersona == "direct") savePreference("intent", "ask_date")
                        recreate()
                    }
                })
            }
        }
    }

    private fun vibeRow(left: View, right: View): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left)
            addView(right)
        }
    }

    private fun showPendingKeyboardSuggestions() {
        val prefs = getSharedPreferences("dating_copilot", Context.MODE_MULTI_PROCESS)
        val json = prefs.getString(PREF_PENDING_KEYBOARD_SUGGESTIONS, null) ?: return
        if (json == lastPendingKeyboardSuggestionsJson) return
        lastPendingKeyboardSuggestionsJson = json
        try {
            val type = object : TypeToken<List<SuggestionOption>>() {}.type
            val suggestions: List<SuggestionOption> = gson.fromJson(json, type) ?: emptyList()
            if (suggestions.isNotEmpty()) {
                showSuggestions(suggestions)
            }
        } catch (e: Exception) {
            Log.w("ChatActivity", "Failed to load pending suggestions: ${e.message}")
        }
    }

    private fun vibeTile(label: String, persona: String, intent: String, selected: Boolean): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (selected) 0xFFE9D5FF.toInt() else resources.getColor(R.color.text_secondary, null))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0xFF2C1B35.toInt())
                setStroke(dp(1), if (selected) 0xFFB112D6.toInt() else 0xFF3A2746.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(78), 1f).apply {
                setMargins(dp(4), dp(5), dp(4), dp(5))
            }
            isClickable = true
            setOnClickListener {
                if (this@ChatActivity.currentPersona != persona) {
                    this@ChatActivity.personaManager.recordSelection(persona)
                }
                currentPersona = persona
                currentIntent = intent
                savePreference("persona", persona)
                savePreference("intent", intent)
                Toast.makeText(this@ChatActivity, "$label vibe selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun analyzeCard(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFFBE00FF.toInt(), 0xFFE41487.toInt())).apply {
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(100)
            ).apply { topMargin = dp(18) }
            isClickable = true
            setOnClickListener { showImagePicker() }
            
            val iconContainer = FrameLayout(this@ChatActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(60), dp(60)).apply { marginEnd = dp(16) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x40FFFFFF.toInt())
                }
            }
            iconContainer.addView(ImageView(this@ChatActivity).apply {
                setImageDrawable(
                    IconicsDrawable(this@ChatActivity, GoogleMaterial.Icon.gmd_camera_alt).apply {
                        colorInt = Color.WHITE
                        sizeDp = 30
                    }
                )
                scaleType = ImageView.ScaleType.CENTER
                layoutParams = FrameLayout.LayoutParams(dp(60), dp(60))
            })
            addView(iconContainer)
            
            val textColumn = LinearLayout(this@ChatActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textColumn.addView(TextView(this@ChatActivity).apply {
                text = "Analyze Chat"
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            textColumn.addView(TextView(this@ChatActivity).apply {
                text = "Upload screenshot for instant rizz"
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFFFFD6F2.toInt())
                setPadding(0, dp(2), 0, 0)
            })
            addView(textColumn)
        }
    }

    private fun bottomNav(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(8))
            background = GradientDrawable().apply { setColor(0xFF1B0E23.toInt()) }
            addView(navItem("Home", true) {})
            addView(navItem("History", false) { startActivity(Intent(this@ChatActivity, HistoryActivity::class.java)) })
            addView(navItem("Setup", false) { startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)) })
            addView(navItem("Settings", false) { SettingsSheet().show(supportFragmentManager, "settings") })
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
                IconicsDrawable(this@ChatActivity, icon).apply {
                    colorInt = if (selected) Color.WHITE else 0xFFC4B5FD.toInt()
                    sizeDp = 22
                }
            )
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        })
        container.addView(TextView(this@ChatActivity).apply {
            text = label
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(if (selected) Color.WHITE else 0xFFC4B5FD.toInt())
            setPadding(0, dp(2), 0, 0)
        })
        return container
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text.uppercase()
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_muted, null))
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (6 * resources.displayMetrics.density).toInt()
            )
        }
    }

    private fun choiceChip(label: String, selected: Boolean, onClick: (View) -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 13f
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * resources.displayMetrics.density).toInt() }
            isClickable = true
            isFocusable = true
            applyChipStyle(this, selected)
            setOnClickListener(onClick)
        }
    }

    private fun toneChip(label: String, persona: String, intent: String, selected: Boolean = false): TextView {
        val stats = personaManager.getAllPersonaStats()
        val personaStats = stats.find { it.persona == persona }
        val isRecommended = personaManager.getRecommendedPersona() == persona
        val displayLabel = if (isRecommended && personaStats?.copyCount ?: 0 > 0) "⭐ $label" else label

        val chip = choiceChip(displayLabel, selected) { view ->
            if (currentPersona != persona) {
                personaManager.recordSelection(persona)
            }
            currentPersona = persona
            currentIntent = intent
            savePreference("persona", persona)
            savePreference("intent", intent)
            selectedToneChip = updateSelectedChip(selectedToneChip, view as TextView)
            regenerateSuggestions()
        }
        if (selected) selectedToneChip = chip
        return chip
    }

    private fun updateSelectedChip(previous: TextView?, selected: TextView): TextView {
        previous?.let { applyChipStyle(it, false) }
        applyChipStyle(selected, true)
        return selected
    }

    private fun applyChipStyle(chip: TextView, selected: Boolean) {
        val bg = GradientDrawable()
        bg.cornerRadius = 32 * resources.displayMetrics.density
        if (selected) {
            bg.setColor(resources.getColor(R.color.chip_selected, null))
            chip.setTextColor(resources.getColor(R.color.chip_selected_text, null))
            chip.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            bg.setColor(resources.getColor(R.color.chip_unselected_bg, null))
            bg.setStroke(1, resources.getColor(R.color.chip_unselected_border, null))
            chip.setTextColor(resources.getColor(R.color.chip_unselected_text, null))
            chip.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        chip.background = bg
    }

    private fun savePreference(key: String, value: String) {
        getSharedPreferences("dating_copilot", Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    private fun showImagePicker() {
        // Directly open system photo picker — shows recent images
        pickImage.launch("image/*")
    }

    private fun showPasteTextDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_DatingCopilotKeyboard).create()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt()
            )
            setBackgroundColor(resources.getColor(R.color.bg_card, null))
        }

        val title = TextView(this).apply {
            text = "Paste chat or start fresh"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
        }
        layout.addView(title)

        val helper = TextView(this).apply {
            text = "Leave it empty for first-message ideas. Add the latest messages for sharper replies."
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setPadding(0, 0, 0, (16 * resources.displayMetrics.density).toInt())
        }
        layout.addView(helper)

        val editText = android.widget.EditText(this).apply {
            hint = "Paste the conversation text here, or leave empty for first-message ideas..."
            setTextColor(resources.getColor(R.color.text_primary, null))
            setHintTextColor(resources.getColor(R.color.text_muted, null))
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt()
            )
            val bg = GradientDrawable()
            bg.cornerRadius = 12 * resources.displayMetrics.density
            bg.setColor(resources.getColor(R.color.bg_input, null))
            bg.setStroke(1, resources.getColor(R.color.glass_border, null))
            background = bg
            minLines = 4
            gravity = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (120 * resources.displayMetrics.density).toInt()
            )
        }
        layout.addView(editText)

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        val cancelBtn = TextView(this).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setPadding((16 * resources.displayMetrics.density).toInt(), (8 * resources.displayMetrics.density).toInt(), (16 * resources.displayMetrics.density).toInt(), (8 * resources.displayMetrics.density).toInt())
            setOnClickListener { dialog.dismiss() }
        }
        btnLayout.addView(cancelBtn)

        val submitBtn = TextView(this).apply {
            text = "Generate 3 replies"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.white, null))
            setPadding((20 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt(), (20 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt())
            val bg = GradientDrawable()
            bg.cornerRadius = 24 * resources.displayMetrics.density
            bg.setColor(resources.getColor(R.color.accent_violet, null))
            background = bg
            setOnClickListener {
                val text = editText.text.toString().trim()
                dialog.dismiss()
                analyzeText(text)
            }
        }
        btnLayout.addView(submitBtn)

        layout.addView(btnLayout)
        dialog.setView(layout)
        dialog.show()
    }

    private fun handleImageSelected(uri: Uri) {
        val intent = Intent(this@ChatActivity, ScreenshotAnalysisActivity::class.java).apply {
            data = uri
            putExtra("persona", currentPersona)
            putExtra("intent", currentIntent)
            putExtra("platform", currentPlatform)
        }
        startActivity(intent)
    }

    private fun analyzeText(text: String) {
        showLoading(true)
        lastInputText = text
        
        // Convert text to messages and send to backend
        val lines = text.lines().filter { it.isNotBlank() }
        val conversation = lines.map { line ->
            if (line.startsWith("You:", ignoreCase = true) || line.startsWith("Me:", ignoreCase = true)) {
                ChatMessage("you", line.substringAfter(":").trim())
            } else {
                ChatMessage("them", line.trim())
            }
        }

        messages.clear()
        messages.addAll(conversation)
        
        lifecycleScope.launch {
            try {
                val requestIntent = if (text.isBlank()) "first_message" else currentIntent
                
                withContext(Dispatchers.IO) {
                    apiClient.getSuggestionsFromTextStreaming(
                        text,
                        currentPersona,
                        requestIntent,
                        currentPlatform,
                        onPartial = { partialText ->
                            // Update loading text with partial response
                            runOnUiThread {
                                updateLoadingText(partialText)
                            }
                        },
                        onComplete = { options ->
                            runOnUiThread {
                                showLoading(false)
                                if (options != null && options.isNotEmpty()) {
                                    AppHistoryStore.add(this@ChatActivity, "Text", text.ifBlank { "First-message ideas" }, options)
                                    messages.clear()
                                    messages.addAll(conversation)
                                    updateChatUI()
                                    showSuggestions(options)
                                } else {
                                    Toast.makeText(this@ChatActivity, "Failed to get suggestions", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@ChatActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun updateLoadingText(partialText: String) {
        // Extract the last line being typed for display
        val lines = partialText.split("\n").filter { it.isNotBlank() }
        val displayText = if (lines.isNotEmpty()) {
            val lastLine = lines.last().removePrefix(">>> ").take(40)
            "Generating: $lastLine..."
        } else {
            "Generating replies..."
        }
        
        loadingTextView.text = displayText
    }

    private fun handleAnalyzeResponse(response: AnalyzeResponse) {
        android.util.Log.d("ChatActivity", "handleAnalyzeResponse: conversation=${response.conversation?.size}, suggestions=${response.suggestions?.size}")
        response.conversation?.let { convo ->
            messages.clear()
            messages.addAll(convo)
            updateChatUI()
        }
        response.suggestions?.let { suggestions ->
            AppHistoryStore.add(
                this,
                "Screenshot",
                response.conversation?.lastOrNull()?.text ?: "Screenshot analysis",
                suggestions
            )
            showSuggestions(suggestions)
        }
    }

    private fun updateChatUI() {
        if (messages.isEmpty()) return
        
        findViewById<View>(android.R.id.content)?.findViewWithTag<View>("input_row")?.visibility = View.GONE
        emptyStateView.visibility = View.GONE
        chatRecyclerView.visibility = View.VISIBLE
        chatAdapter.updateMessages(messages)
        
        // Scroll to bottom
        Handler(Looper.getMainLooper()).postDelayed({
            chatRecyclerView.scrollToPosition(messages.size - 1)
        }, 100)
    }

    private fun showSuggestions(suggestions: List<SuggestionOption>) {
        android.util.Log.d("ChatActivity", "showSuggestions: count=${suggestions.size}")
        suggestionsLabel.visibility = View.VISIBLE
        suggestionsRecyclerView.visibility = View.VISIBLE
        suggestionAdapter.updateSuggestions(suggestions)
    }

    private fun regenerateSuggestions() {
        if (messages.isEmpty() && lastInputText.isBlank()) return
        
        showLoading(true)
        
        val convoText = if (messages.isNotEmpty()) {
            messages.joinToString("\n") { 
                "${if (it.sender == "you") "You" else "Them"}: ${it.text}" 
            }
        } else {
            lastInputText
        }
        
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiClient.getSuggestionsFromText(convoText, currentPersona, currentIntent, currentPlatform)
                }
                showLoading(false)
                if (response != null) {
                    AppHistoryStore.add(this@ChatActivity, "Text", convoText, response)
                    showSuggestions(response)
                } else {
                    android.util.Log.e("ChatActivity", "regenerateSuggestions: API returned null")
                    Toast.makeText(this@ChatActivity, "Could not regenerate. Try again.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                showLoading(false)
                android.util.Log.e("ChatActivity", "regenerateSuggestions exception: ${e.message}", e)
                Toast.makeText(this@ChatActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(show: Boolean, screenshotUri: Uri? = null) {
        loadingView.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            loadingTextView.text = if (screenshotUri != null) "Reading screenshot..." else "Generating replies..."
            loadingPreviewImage.visibility = if (screenshotUri != null) View.VISIBLE else View.GONE
            if (screenshotUri != null) loadingPreviewImage.setImageURI(screenshotUri)
            startLoadingPulse()
        } else {
            stopLoadingPulse()
        }
    }

    private fun startLoadingPulse() {
        loadingPulseAnimator?.cancel()
        loadingView.alpha = 0.95f
        loadingPulseAnimator = ObjectAnimator.ofFloat(loadingView, View.ALPHA, 0.88f, 1f).apply {
            duration = 850
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopLoadingPulse() {
        loadingPulseAnimator?.cancel()
        loadingPulseAnimator = null
        loadingView.alpha = 0.95f
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("RizzSe", text))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : View> findViewByIdWithTag(tag: String): T? {
        val root = window.decorView.findViewById<View>(android.R.id.content)
        return findViewWithTag(root, tag) as? T
    }

    private fun findViewWithTag(root: View, tag: String): View? {
        if (root.tag == tag) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findViewWithTag(root.getChildAt(i), tag)
                if (found != null) return found
            }
        }
        return null
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private inner class RizzseScrollCaptureCallback : ScrollCaptureCallback {
        override fun onScrollCaptureSearch(
            signal: CancellationSignal,
            onReady: java.util.function.Consumer<Rect>
        ) {
            val root = window?.decorView ?: return onReady.accept(Rect())
            val visible = Rect(0, 0, root.width, root.height)
            onReady.accept(visible)
        }

        override fun onScrollCaptureStart(
            session: ScrollCaptureSession,
            signal: CancellationSignal,
            onReady: Runnable
        ) {
            val root = window?.decorView
            if (root == null) {
                onReady.run()
                return
            }
            root.post {
                onReady.run()
            }
        }

        override fun onScrollCaptureImageRequest(
            session: ScrollCaptureSession,
            signal: CancellationSignal,
            captureArea: Rect,
            onComplete: java.util.function.Consumer<Rect>
        ) {
            val root = window?.decorView
            val surface = session.surface
            if (root == null) {
                onComplete.accept(Rect())
                return
            }
            try {
                val pixelCount = captureArea.width() * captureArea.height()
                if (pixelCount <= 0) {
                    onComplete.accept(Rect())
                    return
                }
                val buffer = java.nio.ByteBuffer.allocateDirect(pixelCount * 4)
                buffer.order(java.nio.ByteOrder.nativeOrder())
                val pixelSrc = IntArray(pixelCount)
                val out = IntArray(pixelCount)
                root.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(
                    captureArea.width(), captureArea.height(), android.graphics.Bitmap.Config.ARGB_8888
                )))
                val bmp = android.graphics.Bitmap.createBitmap(
                    captureArea.width(), captureArea.height(), android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bmp)
                canvas.translate(-captureArea.left.toFloat(), -captureArea.top.toFloat())
                canvas.clipRect(captureArea)
                root.draw(canvas)
                bmp.copyPixelsToBuffer(buffer)
                buffer.rewind()
                bmp.recycle()
                onComplete.accept(captureArea)
            } catch (e: Throwable) {
                Log.w("ChatActivity", "ScrollCapture image request failed: ${e.message}")
                onComplete.accept(Rect())
            }
        }

        override fun onScrollCaptureEnd(onReady: Runnable) {
            onReady.run()
        }
    }
}
