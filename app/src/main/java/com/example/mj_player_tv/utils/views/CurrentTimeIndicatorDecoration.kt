package com.example.mj_player_tv.utils.views

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.recyclerview.widget.RecyclerView

class CurrentTimeIndicatorDecoration : RecyclerView.ItemDecoration() {

    private val linePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private var lineX: Float = -1f // Die absolute X-Position der Linie, basierend auf der Uhrzeit
    private var offsetX: Float = 0f // Der aktuelle horizontale Scroll-Offset

    /**
     * Diese Methode wird vom Fragment/ViewModel aufgerufen, um die Position der Linie zu aktualisieren.
     * @param x Die absolute Pixel-Position der aktuellen Zeit seit Timeline-Beginn.
     */
    fun setCurrentTimePosition(x: Float) {
        if (lineX != x) {
            lineX = x
        }
    }

    /**
     * Diese Methode wird bei jedem Scroll-Vorgang aufgerufen, um den Offset zu aktualisieren.
     * @param offset Der gesamte bisherige Scroll-Wert.
     */
    fun setScrollOffset(offset: Float) {
        offsetX = offset
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

        // Wenn die Position noch nicht gesetzt wurde, nichts zeichnen.
        if (lineX < 0) return

        // Berechne die tatsächliche Zeichenposition auf dem Bildschirm
        val drawX = lineX - offsetX

        // Zeichne die Linie nur, wenn sie im sichtbaren Bereich des RecyclerView liegt
        if (drawX >= 0 && drawX <= parent.width) {
            c.drawLine(
                drawX,
                0f, // Startet ganz oben
                drawX,
                parent.height.toFloat(), // Geht bis ganz nach unten
                linePaint
            )
        }
    }
}