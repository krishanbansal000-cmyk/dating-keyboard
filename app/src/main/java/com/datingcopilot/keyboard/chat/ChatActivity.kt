package com.datingcopilot.keyboard.chat

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.datingcopilot.keyboard.R
import com.datingcopilot.keyboard.image.ImagePickerBottomSheet
import com.datingcopilot.keyboard.SettingsSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var suggestionsRecyclerView: RecyclerView
    private lateinit var suggestionAdapter: SuggestionCardAdapter
    private lateinit var suggestionsLabel: TextView
    private lateinit var personaBar: LinearLayout
    private lateinit var loadingView: FrameLayout
    private lateinit var emptyStateView: LinearLayout
    
    private val messages = mutableListOf<ChatMessage>()
    private val personas = listOf("friendly", "romantic", "bold", "witty", "playful", "chill", "direct", "flirty")
    private var currentPersona = "playful"
    private var selectedPersonaChip: TextView? = null

    private val apiClient by lazy { com.datingcopilot.keyboard.ApiClient(this) }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImageSelected(it) }
    }

    private val browseDevice = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { handleImageSelected(it) }
        }
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

        buildUI()
        handleSendImage(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSendImage(intent)
    }

    private fun handleSendImage(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val imageUri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            imageUri?.let { handleImageSelected(it) }
        }
    }

    private fun buildUI() {
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

        val hinglishSwitch = Switch(this).apply {
            text = "Hinglish"
            textSize = 11f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            val sp = this@ChatActivity.getSharedPreferences("dating_copilot", Context.MODE_PRIVATE)
            isChecked = sp.getBoolean("hinglish_mode", false)
            setOnCheckedChangeListener { _, isChecked ->
                sp.edit().putBoolean("hinglish_mode", isChecked).apply()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
        }
        topBar.addView(hinglishSwitch)

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

            val emptyEmoji = TextView(this@ChatActivity).apply {
                text = "✨"
                textSize = 64f
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(0, 0, 0, (16 * resources.displayMetrics.density).toInt())
            }
            addView(emptyEmoji)

            val pill = TextView(this@ChatActivity).apply {
                text = "AI DATING COACH"
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.accent_violet, null))
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(
                    (16 * resources.displayMetrics.density).toInt(),
                    (6 * resources.displayMetrics.density).toInt(),
                    (16 * resources.displayMetrics.density).toInt(),
                    (6 * resources.displayMetrics.density).toInt()
                )
                val pillBg = android.graphics.drawable.GradientDrawable()
                pillBg.cornerRadius = 32 * resources.displayMetrics.density
                pillBg.setColor(resources.getColor(R.color.bg_surface, null))
                background = pillBg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER; bottomMargin = (12 * resources.displayMetrics.density).toInt() }
            }
            addView(pill)

            val emptyTitle = TextView(this@ChatActivity).apply {
                text = "Ready to up your rizz?"
                textSize = 22f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.text_primary, null))
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            }
            addView(emptyTitle)

            val emptySubtitle = TextView(this@ChatActivity).apply {
                text = "Upload a screenshot or paste a conversation to get AI-powered reply suggestions that match your vibe."
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(
                    (40 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt(),
                    (40 * resources.displayMetrics.density).toInt(),
                    0
                )
                maxLines = 3
            }
            addView(emptySubtitle)

            val uploadHint = TextView(this@ChatActivity).apply {
                text = "➕ Upload Screenshot"
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.white, null))
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(
                    (32 * resources.displayMetrics.density).toInt(),
                    (14 * resources.displayMetrics.density).toInt(),
                    (32 * resources.displayMetrics.density).toInt(),
                    (14 * resources.displayMetrics.density).toInt()
                )
                val btnBg = android.graphics.drawable.GradientDrawable()
                btnBg.cornerRadius = 32 * resources.displayMetrics.density
                btnBg.setColor(resources.getColor(R.color.accent_violet, null))
                background = btnBg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER; topMargin = (28 * resources.displayMetrics.density).toInt() }
                isClickable = true
                setOnClickListener { showImagePicker() }
            }
            addView(uploadHint)
        }
        chatContainer.addView(emptyStateView)

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

        // Loading overlay
        loadingView = FrameLayout(this).apply {
            setBackgroundColor(resources.getColor(R.color.bg_dark, null))
            alpha = 0.95f
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (120 * resources.displayMetrics.density).toInt()
            )

            val loadingContent = LinearLayout(this@ChatActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            val shimmer = ProgressBar(this@ChatActivity, null, android.R.attr.progressBarStyleSmall).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (48 * resources.displayMetrics.density).toInt(),
                    (48 * resources.displayMetrics.density).toInt()
                )
            }
            loadingContent.addView(shimmer)

            val loadingText = TextView(this@ChatActivity).apply {
                text = "Analyzing conversation..."
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_secondary, null))
                setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
            }
            loadingContent.addView(loadingText)

            addView(loadingContent)
        }
        mainLayout.addView(loadingView)

        // Suggestions carousel
        suggestionsLabel = TextView(this).apply {
            text = "AI Suggestions"
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
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(this@ChatActivity, LinearLayoutManager.HORIZONTAL, false)
            setPadding(
                (8 * resources.displayMetrics.density).toInt(),
                0,
                (8 * resources.displayMetrics.density).toInt(),
                0
            )
            clipToPadding = false
            visibility = View.GONE
        }
        suggestionAdapter = SuggestionCardAdapter { suggestion ->
            copyToClipboard(suggestion.text)
            Toast.makeText(this, "Copied! Paste in your dating app", Toast.LENGTH_SHORT).show()
        }
        suggestionsRecyclerView.adapter = suggestionAdapter
        mainLayout.addView(suggestionsRecyclerView)

        // Persona selector bar
        val personaScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
        }

        personaBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt()
            )
        }

        personas.forEach { persona ->
            val chip = TextView(this).apply {
                text = persona.replaceFirstChar { it.uppercase() }
                textSize = 13f
                setPadding(
                    (16 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt(),
                    (16 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt()
                )
                val bg = GradientDrawable()
                bg.cornerRadius = 32 * resources.displayMetrics.density
                
                if (persona == currentPersona) {
                    bg.setColor(resources.getColor(R.color.chip_selected, null))
                    setTextColor(resources.getColor(R.color.chip_selected_text, null))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    selectedPersonaChip = this
                } else {
                    bg.setColor(resources.getColor(R.color.chip_unselected_bg, null))
                    bg.setStroke(1, resources.getColor(R.color.chip_unselected_border, null))
                    setTextColor(resources.getColor(R.color.chip_unselected_text, null))
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (8 * resources.displayMetrics.density).toInt()
                }
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    // Deselect previous
                    selectedPersonaChip?.let { prev ->
                        val prevBg = GradientDrawable()
                        prevBg.cornerRadius = 32 * resources.displayMetrics.density
                        prevBg.setColor(resources.getColor(R.color.chip_unselected_bg, null))
                        prevBg.setStroke(1, resources.getColor(R.color.chip_unselected_border, null))
                        prev.background = prevBg
                        prev.setTextColor(resources.getColor(R.color.chip_unselected_text, null))
                        prev.setTypeface(null, android.graphics.Typeface.NORMAL)
                    }
                    // Select new
                    val newBg = GradientDrawable()
                    newBg.cornerRadius = 32 * resources.displayMetrics.density
                    newBg.setColor(resources.getColor(R.color.chip_selected, null))
                    background = newBg
                    setTextColor(resources.getColor(R.color.chip_selected_text, null))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    selectedPersonaChip = this
                    currentPersona = persona
                    
                    // Regenerate suggestions if we have messages
                    if (messages.isNotEmpty()) {
                        regenerateSuggestions()
                    }
                }
            }
            personaBar.addView(chip)
        }

        personaScroll.addView(personaBar)
        mainLayout.addView(personaScroll)

        root.addView(mainLayout)
        setContentView(root)
    }

    private fun showImagePicker() {
        val bottomSheet = ImagePickerBottomSheet()
        bottomSheet.listener = object : ImagePickerBottomSheet.ImagePickerListener {
            override fun onCameraSelected() {
                pickImage.launch("image/*")
            }

            override fun onGallerySelected() {
                pickImage.launch("image/*")
            }

            override fun onBrowseDeviceSelected() {
                val intent = Intent(this@ChatActivity, com.datingcopilot.keyboard.image.ImageBrowserActivity::class.java)
                browseDevice.launch(intent)
            }

            override fun onPasteTextSelected() {
                showPasteTextDialog()
            }
        }
        bottomSheet.show(supportFragmentManager, "picker")
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
            text = "Paste Conversation"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 0, 0, (16 * resources.displayMetrics.density).toInt())
        }
        layout.addView(title)

        val editText = android.widget.EditText(this).apply {
            hint = "Paste the conversation text here..."
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
            text = "Analyze"
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
                if (text.isNotEmpty()) {
                    dialog.dismiss()
                    analyzeText(text)
                }
            }
        }
        btnLayout.addView(submitBtn)

        layout.addView(btnLayout)
        dialog.setView(layout)
        dialog.show()
    }

    private fun handleImageSelected(uri: Uri) {
        showLoading(true)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiClient.uploadScreenshot(uri, currentPersona, this@ChatActivity)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    response?.let { handleAnalyzeResponse(it) }
                        ?: Toast.makeText(this@ChatActivity, "Failed to analyze screenshot", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@ChatActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun analyzeText(text: String) {
        showLoading(true)
        
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
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiClient.getSuggestionsFromText(text, currentPersona)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    response?.let { 
                        messages.clear()
                        messages.addAll(conversation)
                        updateChatUI()
                        showSuggestions(it)
                    } ?: Toast.makeText(this@ChatActivity, "Failed to get suggestions", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@ChatActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleAnalyzeResponse(response: AnalyzeResponse) {
        android.util.Log.d("ChatActivity", "handleAnalyzeResponse: conversation=${response.conversation?.size}, suggestions=${response.suggestions?.size}")
        response.conversation?.let { convo ->
            messages.clear()
            messages.addAll(convo)
            updateChatUI()
        }
        response.suggestions?.let { suggestions ->
            showSuggestions(suggestions)
        }
    }

    private fun updateChatUI() {
        if (messages.isEmpty()) return
        
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
        if (messages.isEmpty()) return
        showLoading(true)
        
        val convoText = messages.joinToString("\n") { 
            "${if (it.sender == "you") "You" else "Them"}: ${it.text}" 
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiClient.getSuggestionsFromText(convoText, currentPersona)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    response?.let { showSuggestions(it) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingView.visibility = if (show) View.VISIBLE else View.GONE
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
}
