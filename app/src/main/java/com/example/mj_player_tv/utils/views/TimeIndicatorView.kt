package com.example.mj_player_tv.utils.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class TimeIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    var timeX: Float = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawLine(timeX, 0f, timeX, height.toFloat(), paint)
    }
}
