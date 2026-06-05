package com.datingcopilot.keyboard

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var backendUrlInput: EditText

    private lateinit var userNameInput: EditText
    private lateinit var userAgeInput: EditText
    private lateinit var userBioInput: EditText

    private lateinit var matchNameInput: EditText
    private lateinit var matchAgeInput: EditText
    private lateinit var matchBioInput: EditText

    private lateinit var apiClient: ApiClient
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = ApiClient(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF8FAFC.toInt())
        }

        val scrollView = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(24))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, dp(24))
        }
        header.addView(TextView(this).apply {
            text = "RizzSe"
            textSize = 28f
            setTextColor(0xFF0F172A.toInt())
            typeface = Typeface.DEFAULT_BOLD
        })
        header.addView(TextView(this).apply {
            text = "AI-powered keyboard for dating apps"
            textSize = 14f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, dp(4), 0, 0)
        })
        content.addView(header)

        // Profile Section
        content.addView(sectionHeader("YOUR PROFILE"))
        val profile = apiClient.loadProfile()

        content.addView(fieldLabel("Name"))
        userNameInput = styledEditText(profile.name, "Alex")
        content.addView(userNameInput)
        content.addView(spacer(12))

        content.addView(fieldLabel("Age"))
        userAgeInput = styledEditText(
            if (profile.age > 0) profile.age.toString() else "",
            "29",
            InputType.TYPE_CLASS_NUMBER
        )
        content.addView(userAgeInput)
        content.addView(spacer(12))

        content.addView(fieldLabel("Bio / About"))
        userBioInput = styledEditText(profile.bio, "Software engineer, love hiking and dogs", maxLines = 3)
        content.addView(userBioInput)
        content.addView(spacer(24))

        // Match Section
        content.addView(sectionHeader("CURRENT MATCH"))
        val matchCtx = apiClient.loadMatchContext()

        content.addView(fieldLabel("Name"))
        matchNameInput = styledEditText(matchCtx.name, "Sarah")
        content.addView(matchNameInput)
        content.addView(spacer(12))

        content.addView(fieldLabel("Age"))
        matchAgeInput = styledEditText(
            if (matchCtx.age > 0) matchCtx.age.toString() else "",
            "28",
            InputType.TYPE_CLASS_NUMBER
        )
        content.addView(matchAgeInput)
        content.addView(spacer(12))

        content.addView(fieldLabel("Bio / Profile"))
        matchBioInput = styledEditText(matchCtx.bio, "Environmental scientist who loves hiking", maxLines = 3)
        content.addView(matchBioInput)
        content.addView(spacer(12))

        content.addView(styledButton("Clear Match Context", 0xFFEF4444.toInt()) {
            apiClient.clearMatchContext()
            matchNameInput.setText("")
            matchAgeInput.setText("")
            matchBioInput.setText("")
            Toast.makeText(this@SettingsActivity, "Match context cleared", Toast.LENGTH_SHORT).show()
        })
        content.addView(spacer(24))

        // Server Section
        content.addView(sectionHeader("SERVER SETTINGS"))

        content.addView(fieldLabel("Backend URL"))
        backendUrlInput = styledEditText(
            getSharedPreferences("dating_copilot", MODE_PRIVATE)
                .getString("backend_url", "http://164.68.103.130:8000") ?: "",
            "http://164.68.103.130:8000"
        )
        content.addView(backendUrlInput)
        content.addView(spacer(20))

        content.addView(styledButton("Save All Settings", 0xFF6366F1.toInt()) {
            saveAllSettings()
        })
        content.addView(spacer(16))

        content.addView(TextView(this).apply {
            text = "\u2713 No login required"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF22C55E.toInt())
        })

        scrollView.addView(content)
        root.addView(scrollView)
        setContentView(root)
    }

    private fun sectionHeader(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(0xFF94A3B8.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(8))
            letterSpacing = 0.1f
        }
    }

    private fun fieldLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(0xFF475569.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, dp(4))
        }
    }

    private fun styledEditText(value: String, hint: String, inputType: Int = InputType.TYPE_CLASS_TEXT, maxLines: Int = 1): EditText {
        return EditText(this).apply {
            setText(value)
            this.hint = hint
            this.inputType = inputType
            this.maxLines = maxLines
            textSize = 15f
            setTextColor(0xFF0F172A.toInt())
            setHintTextColor(0xFF94A3B8.toInt())
            setBackgroundColor(0x00000000)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }

            val bg = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0xFFFFFFFF.toInt())
                setStroke(1, 0xFFE2E8F0.toInt())
            }
            background = bg
        }
    }

    private fun styledButton(text: String, bgColor: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val bg = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(bgColor)
            }
            background = bg
            setOnClickListener { onClick() }
        }
    }

    private fun spacer(h: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(h))
        }
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }

    private fun saveAllSettings() {
        val prefs = getSharedPreferences("dating_copilot", MODE_PRIVATE)
        prefs.edit().putString("backend_url", backendUrlInput.text.toString().trim()).apply()

        apiClient.saveProfile(UserProfile(
            name = userNameInput.text.toString().trim(),
            age = userAgeInput.text.toString().trim().toIntOrNull() ?: 0,
            bio = userBioInput.text.toString().trim()
        ))

        apiClient.saveMatchContext(MatchContext(
            name = matchNameInput.text.toString().trim(),
            age = matchAgeInput.text.toString().trim().toIntOrNull() ?: 0,
            bio = matchBioInput.text.toString().trim(),
            platform = "tinder"
        ))

        Toast.makeText(this, "All settings saved", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
