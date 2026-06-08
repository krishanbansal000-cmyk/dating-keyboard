package com.datingcopilot.keyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.datingcopilot.keyboard.R

class RizzseKeyboardView(context: Context, private val callback: KeyboardActionCallback) : View(context) {

    private enum class KeyAction {
        LETTER, BACKSPACE, SHIFT, SYMBOL_TOGGLE, ENTER, SPACE, RIZZSE_ACTION, SUGGESTION
    }

    private data class KeyRect(
        val rect: Rect, val primary: String, val display: String,
        val isSpecial: Boolean, val action: KeyAction
    )

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density
    private fun dp(value: Int): Int = (value * density).toInt()

    private var suggestions: List<String> = emptyList()
    private var isShifted = false
    private var isSymbol = false
    private var enterKeyLabel: String? = null

    private var suggestionStripHeight = dp(50f).toInt()
    private var actionBarHeight = dp(40f).toInt()
    private var keyRowHeight = dp(48f)
    private val keyMargin = dp(4f).toInt()
    private val sideMargin = dp(4f).toInt()
    private val cornerRadius = dp(8f)

    private val keys = mutableListOf<KeyRect>()
    private var pressedKeyIndex = -1

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.bg_dark)
        style = Paint.Style.FILL
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val suggestionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    private val actionButtonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val rectF = RectF()
    private val tempRect = Rect()

    private val alphaLayout = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m"),
        listOf("?123", ",", " ", " ", " ", " ", " ", ".", "\u23CE")
    )

    private val symbolLayout = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "$", "%", "&", "*", "-", "+", "(", ")"),
        listOf("'", "\"", ":", ";", "!", "?", "/", "\\"),
        listOf("ABC", ",", " ", " ", " ", " ", " ", ".", "\u23CE")
    )

    fun setSuggestions(newSuggestions: List<String>) {
        suggestions = newSuggestions
        buildHitTargets()
        invalidate()
    }

    fun setShiftState(shifted: Boolean) {
        isShifted = shifted
        buildHitTargets()
        invalidate()
    }

    fun setSymbolState(symbol: Boolean) {
        isSymbol = symbol
        buildHitTargets()
        invalidate()
    }

    fun setEnterKeyLabel(label: String?) {
        enterKeyLabel = label
        buildHitTargets()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        val stripH = suggestionStripHeight
        val actionH = actionBarHeight
        val minKeyboardHeight = dp(48f).toInt() * 4
        val remaining = (availableHeight - stripH - actionH).coerceAtLeast(minKeyboardHeight)
        keyRowHeight = (remaining / 4).toFloat().coerceAtLeast(dp(48f))
        val height = stripH + actionH + (keyRowHeight * 4).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            buildHitTargets()
        }
    }

    private fun buildHitTargets() {
        keys.clear()
        buildSuggestionTargets()
        buildActionBarTargets()
        buildKeyboardTargets()
    }

    private fun buildSuggestionTargets() {
        val pillHeight = dp(36f).toInt()
        val pillTop = (suggestionStripHeight - pillHeight) / 2

        if (suggestions.isEmpty()) {
            val pillW = width - sideMargin * 2
            val hitRect = Rect(sideMargin, pillTop, sideMargin + pillW, pillTop + pillHeight)
            keys.add(KeyRect(hitRect, "RizzSe", "RizzSe", true, KeyAction.SUGGESTION))
            return
        }

        var pillX = sideMargin
        for (suggestion in suggestions.take(3)) {
            suggestionTextPaint.textSize = 13f * density
            suggestionTextPaint.getTextBounds(suggestion, 0, suggestion.length, tempRect)
            val textW = tempRect.width().toFloat()
            val pillPadding = dp(16f).toInt()
            val pillW = (textW + pillPadding * 2).coerceAtMost((width - pillX - sideMargin).toFloat()).toInt()

            if (pillX + pillW > width - sideMargin) break

            val hitRect = Rect(pillX, pillTop, pillX + pillW, pillTop + pillHeight)
            keys.add(KeyRect(hitRect, suggestion, suggestion, true, KeyAction.SUGGESTION))

            pillX += pillW + dp(8f).toInt()
        }
    }

    private fun buildActionBarTargets() {
        val barTop = suggestionStripHeight
        val btnCount = 3

        val btnWidth = (width - (btnCount + 1) * dp(6f).toInt()) / btnCount
        val btnHeight = dp(28f).toInt()
        val btnY = barTop + (actionBarHeight - btnHeight) / 2

        val actionDefs = listOf(
            "\uD83D\uDCF7 Shot" to "screenshot",
            "\uD83C\uDFAC Record" to "record",
            "\u2728 Rizz" to "magic"
        )

        for ((i, pair) in actionDefs.withIndex()) {
            val (label, action) = pair
            val btnX = dp(6f).toInt() + i * (btnWidth + dp(6f).toInt())
            val hitRect = Rect(btnX, btnY, btnX + btnWidth, btnY + btnHeight)
            keys.add(KeyRect(hitRect, action, label, true, KeyAction.RIZZSE_ACTION))
        }
    }

    private fun buildKeyboardTargets() {
        val layout = if (isSymbol) symbolLayout else alphaLayout
        val keyboardTop = suggestionStripHeight + actionBarHeight
        val rowH = keyRowHeight.toInt()

        for (rowIndex in layout.indices) {
            val row = layout[rowIndex]
            val viewWidth = width.toFloat()
            val totalMargins = (row.size + 1) * keyMargin
            val availableWidth = viewWidth - totalMargins

            val specialWidths = mutableMapOf<Int, Float>()
            val isSpaceRow = row.contains(" ")

            if (isSpaceRow) {
                for (i in row.indices) {
                    when (row[i]) {
                        " " -> specialWidths[i] = availableWidth * 0.35f
                        "\u23CE" -> specialWidths[i] = availableWidth * 0.15f
                        "?123", "ABC" -> specialWidths[i] = availableWidth * 0.12f
                    }
                }
            } else {
                if (rowIndex == 2 && layout.size > 3) {
                    specialWidths[row.size - 1] = availableWidth * 0.15f
                }
            }

            val specialTotal = specialWidths.values.sum()
            val regularCount = row.size - specialWidths.size
            val regularWidth = if (regularCount > 0) (availableWidth - specialTotal) / regularCount else 0f

            var x = sideMargin.toFloat()
            for (i in row.indices) {
                val w = specialWidths[i] ?: regularWidth
                val y = keyboardTop + rowIndex * rowH
                val ch = row[i]
                val display = getCharForDisplay(ch)
                val primary = getPrimaryChar(ch)
                val isSpecial = ch == "\u23CE" || ch == "?123" || ch == "ABC" || ch == "," || ch == "." || ch == " "
                val action = when {
                    ch == "\u23CE" -> KeyAction.ENTER
                    ch == " " -> KeyAction.SPACE
                    ch == "?123" || ch == "ABC" -> KeyAction.SYMBOL_TOGGLE
                    else -> KeyAction.LETTER
                }
                val keyRect = Rect(
                    (x + keyMargin).toInt(), (y + keyMargin).toInt(),
                    (x + keyMargin + w).toInt(), (y + rowH - keyMargin).toInt()
                )
                keys.add(KeyRect(keyRect, primary, display, isSpecial, action))
                x += w + keyMargin
            }

            if (!isSpaceRow && rowIndex == 2 && layout.size > 3) {
                val bw = specialWidths[row.size - 1] ?: 0f
                val y = keyboardTop + rowIndex * rowH
                val keyRect = Rect(
                    (x + keyMargin).toInt(), (y + keyMargin).toInt(),
                    (x + keyMargin + bw).toInt(), (y + rowH - keyMargin).toInt()
                )
                keys.add(KeyRect(keyRect, "\u232B", "\u232B", true, KeyAction.BACKSPACE))
            }
        }
    }

    private fun getCharForDisplay(ch: String): String {
        if (ch == "\u23CE") return enterKeyLabel ?: "\u23CE"
        if (ch == "?123" || ch == "ABC") return ch
        if (ch == "," || ch == "." || ch == " ") return ch
        if (isSymbol) return ch
        return if (isShifted) ch.uppercase() else ch.lowercase()
    }

    private fun getPrimaryChar(ch: String): String {
        if (ch == "\u23CE") return "\n"
        if (ch == "?123" || ch == "ABC") return ch
        if (ch == ",") return ","
        if (ch == ".") return "."
        if (ch == " ") return " "
        if (isSymbol) return ch
        return if (isShifted) ch.uppercase() else ch.lowercase()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawSuggestionStrip(canvas)
        drawActionBar(canvas)
        drawKeyboard(canvas)
    }

    private fun drawSuggestionStrip(canvas: Canvas) {
        val pillHeight = dp(36f)
        val pillTop = (suggestionStripHeight - pillHeight) / 2f

        if (suggestions.isEmpty()) {
            suggestionTextPaint.color = ContextCompat.getColor(context, R.color.accent_violet)
            suggestionTextPaint.textSize = 16f * density
            suggestionTextPaint.typeface = Typeface.DEFAULT_BOLD
            val label = "RizzSe"
            suggestionTextPaint.getTextBounds(label, 0, label.length, tempRect)
            canvas.drawText(
                label,
                width / 2f,
                pillTop + pillHeight / 2f - tempRect.exactCenterY(),
                suggestionTextPaint
            )
            return
        }

        suggestionTextPaint.textSize = 13f * density
        suggestionTextPaint.typeface = Typeface.DEFAULT

        for ((i, key) in keys.withIndex()) {
            if (key.action != KeyAction.SUGGESTION) continue
            rectF.set(key.rect)
            if (i != pressedKeyIndex) {
                fillPaint.color = ContextCompat.getColor(context, R.color.bg_surface)
                canvas.drawRoundRect(rectF, pillHeight / 2f, pillHeight / 2f, fillPaint)
                strokePaint.color = ContextCompat.getColor(context, R.color.glass_border)
                canvas.drawRoundRect(rectF, pillHeight / 2f, pillHeight / 2f, strokePaint)
            }
            suggestionTextPaint.color = ContextCompat.getColor(context, R.color.accent_pink)
            suggestionTextPaint.getTextBounds(key.display, 0, key.display.length, tempRect)
            canvas.drawText(
                key.display,
                key.rect.exactCenterX().toFloat(),
                key.rect.exactCenterY().toFloat() - tempRect.exactCenterY(),
                suggestionTextPaint
            )
        }
    }

    private fun drawActionBar(canvas: Canvas) {
        val btnHeight = dp(28f)
        val btnRadius = btnHeight / 2f

        actionButtonTextPaint.textSize = 12f * density
        actionButtonTextPaint.color = Color.WHITE

        for ((i, key) in keys.withIndex()) {
            if (key.action != KeyAction.RIZZSE_ACTION) continue
            rectF.set(key.rect)
            if (i == pressedKeyIndex) {
                fillPaint.color = ContextCompat.getColor(context, R.color.bg_surface)
            } else {
                fillPaint.color = ContextCompat.getColor(context, R.color.accent_magenta)
            }
            canvas.drawRoundRect(rectF, btnRadius, btnRadius, fillPaint)
            actionButtonTextPaint.getTextBounds(key.display, 0, key.display.length, tempRect)
            canvas.drawText(
                key.display,
                rectF.centerX(),
                rectF.centerY() - tempRect.exactCenterY(),
                actionButtonTextPaint
            )
        }

        strokePaint.color = ContextCompat.getColor(context, R.color.glass_border)
        val barBottom = (suggestionStripHeight + actionBarHeight).toFloat()
        canvas.drawLine(0f, barBottom, width.toFloat(), barBottom, strokePaint)
    }

    private fun drawKeyboard(canvas: Canvas) {
        textPaint.typeface = Typeface.DEFAULT_BOLD

        for ((i, key) in keys.withIndex()) {
            if (key.action == KeyAction.SUGGESTION || key.action == KeyAction.RIZZSE_ACTION) continue
            rectF.set(key.rect)

            if (i == pressedKeyIndex) {
                fillPaint.color = ContextCompat.getColor(context, R.color.accent_magenta)
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint)
            } else if (key.action == KeyAction.BACKSPACE || key.action == KeyAction.SYMBOL_TOGGLE || key.action == KeyAction.ENTER) {
                fillPaint.color = ContextCompat.getColor(context, R.color.bg_surface)
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint)
                strokePaint.color = ContextCompat.getColor(context, R.color.glass_border)
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, strokePaint)
            } else if (key.action == KeyAction.SPACE) {
                fillPaint.color = ContextCompat.getColor(context, R.color.bg_card)
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint)
                strokePaint.color = ContextCompat.getColor(context, R.color.glass_border)
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, strokePaint)
                val hintRect = RectF(
                    rectF.left + dp(6f), rectF.bottom - dp(3f),
                    rectF.right - dp(6f), rectF.bottom
                )
                fillPaint.color = ContextCompat.getColor(context, R.color.accent_violet)
                canvas.drawRoundRect(hintRect, dp(1.5f), dp(1.5f), fillPaint)
            } else if (key.action == KeyAction.LETTER) {
                fillPaint.color = ContextCompat.getColor(context, R.color.bg_card)
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, fillPaint)
                strokePaint.color = ContextCompat.getColor(context, R.color.glass_border)
                canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, strokePaint)
            }

            val isSpecialText = key.action == KeyAction.BACKSPACE || key.action == KeyAction.SYMBOL_TOGGLE || key.action == KeyAction.ENTER
            if (isSpecialText) {
                textPaint.color = ContextCompat.getColor(context, R.color.text_secondary)
                textPaint.textSize = 14f * density
            } else if (key.action == KeyAction.SPACE) {
                continue
            } else {
                textPaint.color = ContextCompat.getColor(context, R.color.text_primary)
                textPaint.textSize = 18f * density
            }

            if (key.display.isNotEmpty()) {
                textPaint.getTextBounds(key.display, 0, key.display.length, tempRect)
                canvas.drawText(
                    key.display,
                    rectF.centerX(),
                    rectF.centerY() - tempRect.exactCenterY(),
                    textPaint
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedKeyIndex = findKeyIndex(x, y)
                if (pressedKeyIndex >= 0) {
                    performHaptic()
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val newIndex = findKeyIndex(x, y)
                if (newIndex != pressedKeyIndex) {
                    pressedKeyIndex = newIndex
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (pressedKeyIndex >= 0 && pressedKeyIndex < keys.size) {
                    val key = keys[pressedKeyIndex]
                    fireKeyAction(key)
                }
                pressedKeyIndex = -1
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedKeyIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKeyIndex(x: Int, y: Int): Int {
        for (i in keys.indices) {
            if (keys[i].rect.contains(x, y)) return i
        }
        return -1
    }

    private fun fireKeyAction(key: KeyRect) {
        when (key.action) {
            KeyAction.LETTER -> callback.onKeyPress(key.primary, isText = true)
            KeyAction.BACKSPACE -> callback.onBackspace()
            KeyAction.SHIFT -> callback.onShift()
            KeyAction.SYMBOL_TOGGLE -> callback.onSymbolToggle()
            KeyAction.ENTER -> callback.onEnter()
            KeyAction.SPACE -> callback.onSpace()
            KeyAction.RIZZSE_ACTION -> callback.onRizzseAction(key.primary)
            KeyAction.SUGGESTION -> callback.onSuggestionSelected(key.primary)
        }
    }

    private fun performHaptic() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
        }
    }
}
