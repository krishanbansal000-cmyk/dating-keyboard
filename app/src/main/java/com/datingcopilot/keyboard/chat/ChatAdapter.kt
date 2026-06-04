package com.datingcopilot.keyboard.chat

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.datingcopilot.keyboard.R

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    fun updateMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val container = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return MessageViewHolder(container)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(private val container: LinearLayout) : RecyclerView.ViewHolder(container) {

        fun bind(message: ChatMessage) {
            container.removeAllViews()
            
            val context = container.context
            val density = context.resources.displayMetrics.density
            val isYou = message.sender == "you"
            
            // Wrapper to control alignment
            val wrapper = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (8 * density).toInt()
                    bottomMargin = (8 * density).toInt()
                }
                gravity = if (isYou) Gravity.END else Gravity.START
            }
            
            // Avatar/initial
            if (!isYou) {
                val avatar = TextView(context).apply {
                    text = "💜"
                    textSize = 20f
                    setPadding(
                        (8 * density).toInt(),
                        (4 * density).toInt(),
                        (8 * density).toInt(),
                        0
                    )
                }
                wrapper.addView(avatar)
            }
            
            // Message bubble
            val bubble = TextView(context).apply {
                text = message.text
                textSize = 15f
                setTextColor(
                    if (isYou) {
                        context.resources.getColor(R.color.white, null)
                    } else {
                        context.resources.getColor(R.color.text_primary, null)
                    }
                )
                setPadding(
                    (16 * density).toInt(),
                    (12 * density).toInt(),
                    (16 * density).toInt(),
                    (12 * density).toInt()
                )
                
                val bg = GradientDrawable()
                bg.cornerRadius = 20 * density
                if (isYou) {
                    bg.setColor(context.resources.getColor(R.color.bubble_you, null))
                } else {
                    bg.setColor(context.resources.getColor(R.color.bubble_them, null))
                    bg.setStroke(1, context.resources.getColor(R.color.glass_border, null))
                }
                background = bg
                
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    maxWidth = (280 * density).toInt()
                    if (isYou) {
                        marginEnd = (12 * density).toInt()
                    } else {
                        marginStart = (4 * density).toInt()
                    }
                }
            }
            wrapper.addView(bubble)
            
            container.addView(wrapper)
        }
    }
}
