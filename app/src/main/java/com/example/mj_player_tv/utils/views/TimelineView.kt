package com.example.mj_player_tv.utils.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

// Der Name "TimelineView" ist selbst gewählt. Sie können ihn beliebig nennen.
class TimelineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint-Objekte zum Zeichnen von Text und Linien
    private val textPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt() // Weiß
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val linePaint = Paint().apply {
        color = 0x80FFFFFF.toInt() // Halb-transparentes Weiß
        strokeWidth = 2f
    }

    // Dieser Wert muss mit dem in Ihrem ProgramsAdapter übereinstimmen!
    private val pixelsPerMinute = 2.5f

    /**
     * Die wichtigste Methode: Hier wird die Ansicht gezeichnet.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val viewHeight = height.toFloat()
        // Holen Sie die aktuelle Scroll-Position, damit die Zeitleiste mitscrollt
        val startX = -scrollX

        // Schleife, um Zeitmarken für einen 24-Stunden-Zeitraum zu zeichnen
        for (hour in 0..23) {
            // Zeichne eine Linie und Text für die volle Stunde
            val hourX = (hour * 60 * pixelsPerMinute) + startX
            if (hourX > -100 && hourX < width + 100) { // Nur zeichnen, was sichtbar ist
                canvas.drawLine(hourX, viewHeight / 2, hourX, viewHeight, linePaint)
                canvas.drawText("$hour:00", hourX, viewHeight / 2 - 5, textPaint)
            }

            // Zeichne eine kleinere Linie für die halbe Stunde
            val halfHourX = ((hour * 60 + 30) * pixelsPerMinute) + startX
            if (halfHourX > -100 && halfHourX < width + 100) {
                canvas.drawLine(halfHourX, viewHeight * 0.75f, halfHourX, viewHeight, linePaint)
            }
        }
    }
}