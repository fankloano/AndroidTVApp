package com.example.mj_player_tv.ui.epg

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewParent
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.EpgDataOB
import kotlin.math.abs

class CustomEpgVerticalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VerticalGridView(context, attrs, defStyleAttr) {

    override fun focusSearch(focused: View?, direction: Int): View? {
        if (direction != View.FOCUS_DOWN && direction != View.FOCUS_UP) {
            return super.focusSearch(focused, direction)
        }

        val currentVH =
            findContainingViewHolder(focused ?: return super.focusSearch(focused, direction))
                    as? EpgRowAdapter.RowViewHolder ?: return super.focusSearch(focused, direction)
        val currentPosition = getChildAdapterPosition(currentVH.itemView)
        if (currentPosition == RecyclerView.NO_POSITION) return super.focusSearch(
            focused,
            direction
        )

        val targetRow =
            if (direction == View.FOCUS_DOWN) currentPosition + 1 else currentPosition - 1
        val targetVH = findViewHolderForAdapterPosition(targetRow) as? EpgRowAdapter.RowViewHolder
            ?: return super.focusSearch(focused, direction)

        val targetHgv = targetVH.horizontalGridView
        val currentCenterX = (focused.left + focused.right) / 2

        var bestView: View? = null
        var bestDistance = Int.MAX_VALUE

        // Suche das Programm im Ziel-Row, das der gleichen X-Position am nächsten ist
        for (i in 0 until targetHgv.childCount) {
            val child = targetHgv.getChildAt(i)
            val childCenterX = (child.left + child.right) / 2
            val distance = abs(childCenterX - currentCenterX)
            if (distance < bestDistance) {
                bestDistance = distance
                bestView = child
            }
        }

        if (bestView != null) {
            val synchronizer = EpgScrollSynchronizer()
            synchronizer.setInitialHorizontalOffset(targetHgv)
            Log.d("FOCUS_DEBUG", "Fokus geht auf: ${bestView.tag}")
            return bestView
        }

        // Kein Treffer? Dann normales Verhalten
        return super.focusSearch(focused, direction)
    }
}
