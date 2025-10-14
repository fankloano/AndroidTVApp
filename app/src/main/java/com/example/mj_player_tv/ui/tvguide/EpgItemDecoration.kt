package com.example.mj_player_tv.ui.tvguide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class EpgItemDecoration(
    color: Int,
    private val thickness: Float = 2f // px
) : RecyclerView.ItemDecoration() {

    private val paint = Paint().apply {
        this.color = color
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val childCount = parent.childCount
        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)

            // Linke Linie (außer beim 1. Item)
            if (i > 0) {
                val right = child.left.toFloat()
                val left = right - thickness / 2f
                c.drawRect(left, child.top.toFloat(), right, child.bottom.toFloat(), paint)
            }

            // Rechte Linie (außer beim letzten Item)
            if (i < childCount - 1) {
                val left = child.right.toFloat() - thickness
                val right = child.right.toFloat()
                c.drawRect(left, child.top.toFloat(), right, child.bottom.toFloat(), paint)
            }
        }
    }
}
