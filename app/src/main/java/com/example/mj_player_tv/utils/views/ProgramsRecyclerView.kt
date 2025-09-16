package com.example.mj_player_tv.utils.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ProgramsRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFCCCCCC.toInt() // Beispiel-Farbe für Divider
    }
    private val dividerHeight = 2 // Höhe des Dividers in px
    private var drawDivider = false

    init {
        // Horizontal LayoutManager für Programme
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        // Optional: itemAnimator deaktivieren, damit keine blinkenden Updates
        itemAnimator = null
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drawDivider) {
            canvas.drawRect(
                paddingStart.toFloat(),
                height - dividerHeight.toFloat(),
                width.toFloat(),
                height.toFloat(),
                dividerPaint
            )
        }
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean = false // Scroll nur über Key/Sync

    fun setDrawDivider(enabled: Boolean) {
        if (drawDivider != enabled) {
            drawDivider = enabled
            invalidate()
        }
    }

    /**
     * Verschiebt alle ProgramTextViews um deltaX (z.B. synchron mit Timeline)
     */
    fun scrollProgramsBy(deltaX: Int) {
        scrollBy(deltaX, 0)
        adjustChildPadding()
    }

    /**
     * Passe Padding jedes ProgramTextView an, damit Inhalte nicht abgeschnitten werden
     */
    private fun adjustChildPadding() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is ProgramTextView) {
                val maxPadding = Math.max(0, -child.left + paddingStart)
                val remainingWidth = child.width - maxPadding
                val minWidth = child.minWidth
                val paddingStart = if (remainingWidth < minWidth) child.width - minWidth else maxPadding
                child.isAdjustingPadding = true
                child.setPaddingRelative(
                    paddingStart,
                    child.paddingTop,
                    child.paddingEnd,
                    child.paddingBottom
                )
                child.isAdjustingPadding = false
            }
        }
    }
}