package helium314.keyboard.latin.suggestions

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.customview.widget.ViewDragHelper
import helium314.keyboard.latin.R
import kotlin.math.roundToInt

class RizzseSwipeRailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.suggestionWordStyle,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val dragHelper: ViewDragHelper
    private val thumb: TextView
    private var onConfirmed: (() -> Unit)? = null
    private var confirmed = false

    init {
        contentDescription = "RizzSe swipe to record context"
        isClickable = true
        isFocusable = true
        clipToPadding = false

        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(0xFF2A1532.toInt())
            setStroke(dp(1), 0xFF4A255E.toInt())
        }

        addView(TextView(context, null, R.attr.suggestionWordStyle).apply {
            text = "SWIPE TO RECORD"
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.18f
            setTextColor(0xFFD9B8EA.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(42), 0, dp(48), 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        })

        thumb = TextView(context, null, R.attr.suggestionWordStyle).apply {
            text = "♦"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFF4BC8.toInt())
            }
            elevation = dp(2).toFloat()
            layoutParams = LayoutParams(dp(32), dp(32)).apply {
                marginStart = dp(5)
                topMargin = dp(5)
                bottomMargin = dp(5)
            }
        }
        addView(thumb)

        dragHelper = ViewDragHelper.create(this, 1f, object : ViewDragHelper.Callback() {
            override fun tryCaptureView(child: View, pointerId: Int): Boolean = child === thumb

            override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
                return left.coerceIn(startLeft, endLeft)
            }

            override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int = child.top

            override fun getViewHorizontalDragRange(child: View): Int = endLeft - startLeft

            override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
                val shouldConfirm = releasedChild.left >= confirmLeft || xvel > 1200f
                val finalLeft = if (shouldConfirm) endLeft else startLeft
                confirmed = shouldConfirm
                dragHelper.settleCapturedViewAt(finalLeft, releasedChild.top)
                invalidate()
            }
        })
    }

    fun setOnConfirmedListener(listener: () -> Unit) {
        onConfirmed = listener
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return dragHelper.shouldInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        dragHelper.processTouchEvent(event)
        return true
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) {
            postInvalidateOnAnimation()
        } else if (confirmed) {
            confirmed = false
            onConfirmed?.invoke()
            postDelayed({ resetThumb() }, 300)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed({ nudgeThumb() }, 500)
    }

    private fun nudgeThumb() {
        if (!isAttachedToWindow || thumb.left != startLeft) return
        thumb.animate()
            .translationX((endLeft - startLeft) * 0.16f)
            .setDuration(600)
            .withEndAction { thumb.animate().translationX(0f).setDuration(600).start() }
            .start()
    }

    private fun resetThumb() {
        thumb.translationX = 0f
        if (thumb.left != startLeft) {
            dragHelper.smoothSlideViewTo(thumb, startLeft, thumb.top)
            postInvalidateOnAnimation()
        }
    }

    private val startLeft: Int
        get() = paddingLeft + (thumb.layoutParams as MarginLayoutParams).leftMargin

    private val endLeft: Int
        get() = (width - paddingRight - thumb.width - (thumb.layoutParams as MarginLayoutParams).rightMargin).coerceAtLeast(startLeft)

    private val confirmLeft: Int
        get() = startLeft + ((endLeft - startLeft) * 0.72f).roundToInt()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
