package com.example.mj_player_tv.utils.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.withClip
import androidx.recyclerview.widget.LinearLayoutManager

class TimeMarksRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textSize = 14f
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.RED
        strokeWidth = 3f
    }

    var times: List<String> = emptyList()
    private var nowX: Float = 0f
    private var lineHeight: Float = 0f
    private var showIndicator: Boolean = false
    private val leftMargin = 80
    var programHalfHourWidth: Float = 150f

    init {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        isHorizontalScrollBarEnabled = false
        isNestedScrollingEnabled = false
    }

    fun scrollContentBy(dx: Int) {
        // Halte Offset intern
        offsetX += dx
        invalidate()
    }

    private var offsetX = 0f
    var halfHourWidth = 30 * 5f

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        times.forEachIndexed { index, time ->
            val centerX = index * halfHourWidth - offsetX
// RICHTIG
            val textX = centerX - paint.measureText(time) / 2
            // Text zeichnen
            canvas.drawText(
                time,
                textX,
                height / 2f + paint.textSize / 2f,
                paint
            )

            // Debug-Hilfslinie für jede TimeMark
            canvas.drawLine(
                centerX,
                0f,
                centerX,
                height.toFloat(),
                Paint().apply {
                    color = Color.GRAY
                    strokeWidth = 1f
                }
            )
        }

        if (showIndicator) {
            canvas.drawLine(nowX - offsetX, 0f, nowX - offsetX, lineHeight, indicatorPaint)
        }
    }


    fun setCurrentTimeIndicatorVisible(visible: Boolean) {
        showIndicator = visible
        invalidate()
    }

    fun updateCurrentTimePosition(x: Float, h: Float) {
        nowX = x
        lineHeight = h
        Log.d("TimeMarks", "NOW: ${nowX} & lineHeight=$lineHeight")
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean = false

}
