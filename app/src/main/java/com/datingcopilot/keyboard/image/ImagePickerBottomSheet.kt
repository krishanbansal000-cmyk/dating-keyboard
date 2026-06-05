package com.datingcopilot.keyboard.image

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.datingcopilot.keyboard.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ImagePickerBottomSheet : BottomSheetDialogFragment() {

    interface ImagePickerListener {
        fun onCameraSelected()
        fun onGallerySelected()
        fun onBrowseDeviceSelected()
        fun onPasteTextSelected()
    }

    var listener: ImagePickerListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        val view = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt()
            )
            setBackgroundColor(resources.getColor(R.color.bg_card, null))
        }

        val title = TextView(requireContext()).apply {
            text = "Upload Conversation"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_primary, null))
            setPadding(0, 0, 0, (24 * resources.displayMetrics.density).toInt())
        }
        view.addView(title)

        val options = listOf(
            Triple("📷", "Take Photo", View.OnClickListener {
                listener?.onCameraSelected()
                dismiss()
            }),
            Triple("🖼️", "Choose from Gallery", View.OnClickListener {
                listener?.onGallerySelected()
                dismiss()
            }),
            Triple("📁", "Browse Device Images", View.OnClickListener {
                listener?.onBrowseDeviceSelected()
                dismiss()
            }),
            Triple("📋", "Paste Text", View.OnClickListener {
                listener?.onPasteTextSelected()
                dismiss()
            })
        )

        options.forEach { (emoji, label, clickListener) ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(
                    (16 * resources.displayMetrics.density).toInt(),
                    (16 * resources.displayMetrics.density).toInt(),
                    (16 * resources.displayMetrics.density).toInt(),
                    (16 * resources.displayMetrics.density).toInt()
                )
                val bg = android.graphics.drawable.GradientDrawable()
                bg.cornerRadius = 12 * resources.displayMetrics.density
                bg.setColor(resources.getColor(R.color.bg_surface, null))
                background = bg
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (12 * resources.displayMetrics.density).toInt()
                }
                isClickable = true
                isFocusable = true
                setOnClickListener(clickListener)
            }

            val emojiView = TextView(requireContext()).apply {
                text = emoji
                textSize = 24f
                setPadding(0, 0, (16 * resources.displayMetrics.density).toInt(), 0)
            }
            row.addView(emojiView)

            val labelView = TextView(requireContext()).apply {
                text = label
                textSize = 16f
                setTextColor(resources.getColor(R.color.text_primary, null))
            }
            row.addView(labelView)

            view.addView(row)
        }

        dialog.setContentView(view)
        return dialog
    }
}
