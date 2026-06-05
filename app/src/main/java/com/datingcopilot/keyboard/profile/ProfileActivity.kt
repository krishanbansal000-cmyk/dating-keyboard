package com.datingcopilot.keyboard.profile

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.datingcopilot.keyboard.ApiClient
import com.datingcopilot.keyboard.R
import com.datingcopilot.keyboard.UserProfile

class ProfileActivity : AppCompatActivity() {

    private val apiClient by lazy { ApiClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (48 * resources.displayMetrics.density).toInt()
            )
        }

        // Title
        val title = TextView(this).apply {
            text = "Your Profile"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 0, 0, (24 * resources.displayMetrics.density).toInt())
        }
        root.addView(title)

        // Load current profile
        val profile = apiClient.loadProfile()

        // Name
        val nameLabel = sectionLabel("Name")
        root.addView(nameLabel)
        val nameInput = styledInput(profile.name, "Alex")
        root.addView(nameInput)

        // Age
        val ageLabel = sectionLabel("Age")
        root.addView(ageLabel)
        val ageInput = styledInput(if (profile.age > 0) profile.age.toString() else "", "29")
        root.addView(ageInput)

        // Bio
        val bioLabel = sectionLabel("Bio")
        root.addView(bioLabel)
        val bioInput = styledInput(profile.bio, "Adventure seeker, coffee lover")
        bioInput.minLines = 3
        root.addView(bioInput)

        // Backend URL
        val urlLabel = sectionLabel("Backend URL")
        root.addView(urlLabel)
        val urlInput = styledInput(
            getSharedPreferences("dating_copilot", MODE_PRIVATE)
                .getString("backend_url", "http://164.68.103.130:8000") ?: "http://164.68.103.130:8000",
            "http://164.68.103.130:8000"
        )
        root.addView(urlInput)

        // Spacer
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (24 * resources.displayMetrics.density).toInt()
            )
        })

        // Save button
        val saveBtn = TextView(this).apply {
            text = "Save Profile"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.white, null))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(
                (24 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt()
            )
            val bg = GradientDrawable()
            bg.cornerRadius = 48 * resources.displayMetrics.density
            bg.setColor(resources.getColor(R.color.accent_violet, null))
            background = bg
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val age = try { (ageInput as android.widget.EditText).text.toString().toInt() } catch (_: Exception) { 0 }
                apiClient.saveProfile(UserProfile(
                    name = (nameInput as android.widget.EditText).text.toString(),
                    age = age,
                    bio = (bioInput as android.widget.EditText).text.toString()
                ))
                getSharedPreferences("dating_copilot", MODE_PRIVATE).edit()
                    .putString("backend_url", (urlInput as android.widget.EditText).text.toString())
                    .apply()
                Toast.makeText(this@ProfileActivity, "Profile saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        root.addView(saveBtn)

        // Back to chat
        val backBtn = TextView(this).apply {
            text = "← Back to Chat"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
            isClickable = true
            setOnClickListener { finish() }
        }
        root.addView(backBtn)

        scrollView.addView(root)
        setContentView(scrollView)
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text.uppercase()
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_muted, null))
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
        }
    }

    private fun styledButton(text: String, bgColorId: Int): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.white, null))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(
                (24 * resources.displayMetrics.density).toInt(),
                (14 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (14 * resources.displayMetrics.density).toInt()
            )
            val bg = GradientDrawable()
            bg.cornerRadius = 24 * resources.displayMetrics.density
            bg.setColor(resources.getColor(bgColorId, null))
            background = bg
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * resources.displayMetrics.density).toInt()
            }
        }
    }

    private fun styledInput(value: String, hint: String): android.widget.EditText {
        return android.widget.EditText(this).apply {
            this.setText(value)
            this.hint = hint
            textSize = 16f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setHintTextColor(resources.getColor(R.color.text_muted, null))
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (14 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (14 * resources.displayMetrics.density).toInt()
            )
            val bg = GradientDrawable()
            bg.cornerRadius = 12 * resources.displayMetrics.density
            bg.setColor(resources.getColor(R.color.bg_input, null))
            bg.setStroke(1, resources.getColor(R.color.glass_border, null))
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * resources.displayMetrics.density).toInt()
            }
        }
    }
}
