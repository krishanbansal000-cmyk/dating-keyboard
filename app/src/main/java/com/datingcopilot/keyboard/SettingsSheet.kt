package com.datingcopilot.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Switch
import android.view.inputmethod.InputMethodManager
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SettingsSheet : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val prefs = context.getSharedPreferences("dating_copilot", 0)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (32 * resources.displayMetrics.density).toInt()
            )
            setBackgroundColor(resources.getColor(R.color.bg_card, null))
        }

        // Title with icon
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (20 * resources.displayMetrics.density).toInt())
        }
        titleRow.addView(ImageView(context).apply {
            setImageDrawable(
                IconicsDrawable(context, GoogleMaterial.Icon.gmd_settings).apply {
                    colorInt = resources.getColor(R.color.accent_pink, null)
                    sizeDp = 24
                }
            )
            layoutParams = LinearLayout.LayoutParams((24 * resources.displayMetrics.density).toInt(), (24 * resources.displayMetrics.density).toInt()).apply {
                marginEnd = (10 * resources.displayMetrics.density).toInt()
            }
        })
        titleRow.addView(TextView(context).apply {
            text = "Settings"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
        })
        root.addView(titleRow)

        // Keyboard actions
        root.addView(sectionLabel(context, "Keyboard", GoogleMaterial.Icon.gmd_keyboard))
        root.addView(actionButton(context, "Choose Keyboard", GoogleMaterial.Icon.gmd_check_circle) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })
        root.addView(actionButton(context, "Open Keyboard Settings", GoogleMaterial.Icon.gmd_settings_applications) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        // Backend URL
        root.addView(sectionLabel(context, "Backend URL", GoogleMaterial.Icon.gmd_cloud))
        val urlInput = EditText(context).apply {
            setText(prefs.getString("backend_url", ApiClient.DEFAULT_BACKEND_URL))
            setTextColor(resources.getColor(R.color.text_primary, null))
            setHintTextColor(resources.getColor(R.color.text_muted, null))
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt()
            )
            val bg = android.graphics.drawable.GradientDrawable()
            bg.cornerRadius = 8 * resources.displayMetrics.density
            bg.setColor(resources.getColor(R.color.bg_input, null))
            bg.setStroke(1, resources.getColor(R.color.glass_border, null))
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * resources.displayMetrics.density).toInt() }
        }
        root.addView(urlInput)

        // Gender
        root.addView(sectionLabel(context, "Your Gender", GoogleMaterial.Icon.gmd_person))
        val genderInput = EditText(context).apply {
            setText(prefs.getString("user_gender", "male"))
            setTextColor(resources.getColor(R.color.text_primary, null))
            setHintTextColor(resources.getColor(R.color.text_muted, null))
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt()
            )
            val bg = android.graphics.drawable.GradientDrawable()
            bg.cornerRadius = 8 * resources.displayMetrics.density
            bg.setColor(resources.getColor(R.color.bg_input, null))
            bg.setStroke(1, resources.getColor(R.color.glass_border, null))
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * resources.displayMetrics.density).toInt() }
        }
        root.addView(genderInput)

        // Auto-persona
        root.addView(sectionLabel(context, "Persona", GoogleMaterial.Icon.gmd_auto_awesome))
        val autoPersonaRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * resources.displayMetrics.density).toInt() }
        }
        autoPersonaRow.addView(TextView(context).apply {
            text = "Auto-optimize persona"
            textSize = 15f
            setTextColor(resources.getColor(R.color.text_primary, null))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val autoPersonaSwitch = Switch(context).apply {
            isChecked = prefs.getBoolean("auto_persona", false)
        }
        autoPersonaRow.addView(autoPersonaSwitch)
        root.addView(autoPersonaRow)

        // Save button
        val saveBtn = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            setPadding(
                (24 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt()
            )
            val bg = android.graphics.drawable.GradientDrawable()
            bg.cornerRadius = 24 * resources.displayMetrics.density
            bg.setColor(resources.getColor(R.color.accent_violet, null))
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.END }
            setOnClickListener {
                val backendUrl = urlInput.text.toString().trim().ifEmpty { ApiClient.DEFAULT_BACKEND_URL }
                val gender = genderInput.text.toString().trim().lowercase().ifEmpty { "male" }
                prefs.edit()
                    .putString("backend_url", backendUrl)
                    .putString("user_gender", gender)
                    .putBoolean("auto_persona", autoPersonaSwitch.isChecked)
                    .apply()
                dismiss()
            }
        }
        saveBtn.addView(ImageView(context).apply {
            setImageDrawable(
                IconicsDrawable(context, GoogleMaterial.Icon.gmd_check).apply {
                    colorInt = resources.getColor(R.color.white, null)
                    sizeDp = 18
                }
            )
            layoutParams = LinearLayout.LayoutParams((18 * resources.displayMetrics.density).toInt(), (18 * resources.displayMetrics.density).toInt()).apply {
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
        })
        saveBtn.addView(TextView(context).apply {
            text = "Save"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.white, null))
        })
        root.addView(saveBtn)

        return root
    }

    private fun sectionLabel(context: android.content.Context, text: String, icon: GoogleMaterial.Icon): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (6 * resources.displayMetrics.density).toInt())
        }
        row.addView(ImageView(context).apply {
            setImageDrawable(
                IconicsDrawable(context, icon).apply {
                    colorInt = resources.getColor(R.color.accent_pink, null)
                    sizeDp = 16
                }
            )
            layoutParams = LinearLayout.LayoutParams((16 * resources.displayMetrics.density).toInt(), (16 * resources.displayMetrics.density).toInt()).apply {
                marginEnd = (6 * resources.displayMetrics.density).toInt()
            }
        })
        row.addView(TextView(context).apply {
            this.text = text
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_secondary, null))
        })
        return row
    }

    private fun actionButton(context: android.content.Context, text: String, icon: GoogleMaterial.Icon, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (18 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (18 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt()
            )
            val bg = android.graphics.drawable.GradientDrawable()
            bg.cornerRadius = 24 * resources.displayMetrics.density
            bg.setColor(resources.getColor(R.color.bg_input, null))
            bg.setStroke(1, resources.getColor(R.color.glass_border, null))
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * resources.displayMetrics.density).toInt() }
            isClickable = true
            setOnClickListener { onClick() }
        }
        row.addView(ImageView(context).apply {
            setImageDrawable(
                IconicsDrawable(context, icon).apply {
                    colorInt = resources.getColor(R.color.accent_pink, null)
                    sizeDp = 20
                }
            )
            layoutParams = LinearLayout.LayoutParams((20 * resources.displayMetrics.density).toInt(), (20 * resources.displayMetrics.density).toInt()).apply {
                marginEnd = (12 * resources.displayMetrics.density).toInt()
            }
        })
        row.addView(TextView(context).apply {
            this.text = text
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
        })
        return row
    }
}
