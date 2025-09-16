package com.example.mj_player_tv.utils.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.withClip

class TimeMarksRecyclerView @JvmOverloads constructor(
context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE // TimeMark Text
        textSize = 32f // anpassen
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.RED // aktuelle Zeitlinie
        strokeWidth = 3f
    }

    var times: List<String> = emptyList()
    private var nowX: Float = 0f
    private var lineHeight: Float = 0f
    private var showIndicator: Boolean = false
    private val leftMargin = 80 // Platz links für Logos
    var programHalfHourWidth: Float = 120f // Breite eines halben Stundenblocks

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // TimeMarks zeichnen
        var x = leftMargin.toFloat()
        times.forEach { time ->
            canvas.drawText(time, x, height / 2f + paint.textSize / 2f, paint)
            x += programHalfHourWidth
        }

        // aktuelle Zeitlinie
        if (showIndicator) {
            canvas.drawLine(nowX, 0f, nowX, lineHeight, indicatorPaint)
        }
    }

    fun setCurrentTimeIndicatorVisible(visible: Boolean) {
        if (showIndicator != visible) {
            showIndicator = visible
            invalidate()
        }
    }

    fun updateCurrentTimePosition(x: Float, h: Float) {
        nowX = x
        lineHeight = h
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean = false
}

