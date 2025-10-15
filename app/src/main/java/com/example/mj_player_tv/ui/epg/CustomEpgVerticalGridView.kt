package com.example.mj_player_tv.ui.epg

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.VerticalGridView
import kotlin.math.abs

class CustomEpgVerticalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VerticalGridView(context, attrs, defStyleAttr) {

    var synchronizer: EpgScrollSynchronizer? = null

    override fun focusSearch(focused: View?, direction: Int): View? {
        Log.d("CustomVGV", "focusSearch dir=$direction from=${focused?.tag}")

        if (direction != View.FOCUS_DOWN && direction != View.FOCUS_UP) {
            return super.focusSearch(focused, direction)
        }

        val currentVH = findContainingViewHolder(focused ?: return super.focusSearch(focused, direction))
                as? EpgRowAdapter.RowViewHolder ?: return super.focusSearch(focused, direction)

        val targetRow =
            if (direction == View.FOCUS_DOWN) currentVH.bindingAdapterPosition + 1
            else currentVH.bindingAdapterPosition - 1
        val targetVH = findViewHolderForAdapterPosition(targetRow)
                as? EpgRowAdapter.RowViewHolder ?: return super.focusSearch(focused, direction)

        val targetHgv = targetVH.horizontalGridView
        val currentCenterX = (focused.left + focused.right) / 2

        var bestView: View? = null
        var bestDistance = Int.MAX_VALUE

        for (i in 0 until targetHgv.childCount) {
            val child = targetHgv.getChildAt(i)
            val childCenterX = (child.left + child.right) / 2
            val distance = abs(childCenterX - currentCenterX)
            if (distance < bestDistance) {
                bestDistance = distance
                bestView = child
            }
        }

        bestView?.let { candidate ->
            targetHgv.isScrollEnabled = false
            synchronizer?.suppressSyncForNextFocusChange = true
            Log.d("HGV_Key", "→ targetHGV=${targetHgv.hashCode()} erhält Fokus auf ${candidate.tag}")
            return candidate
        }
        return super.focusSearch(focused, direction)
    }
}
