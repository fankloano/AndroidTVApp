package com.example.mj_player_tv.ui.epg

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class CustomEpgVerticalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VerticalGridView(context, attrs, defStyleAttr) {

    var synchronizer: EpgScrollSynchronizer? = null
    init {
        smoothScrollSpeedFactor = 2f
        setOnKeyInterceptListener { event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val focused = focusedChild ?: return@setOnKeyInterceptListener false
                val pos = getChildAdapterPosition(focused)
                val total = adapter?.itemCount ?: return@setOnKeyInterceptListener false

                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> return@setOnKeyInterceptListener pos == 0
                    KeyEvent.KEYCODE_DPAD_DOWN -> return@setOnKeyInterceptListener pos == total - 1
                }
            }
            false
        }
    }

    override fun focusSearch(focused: View?, direction: Int): View? {

        if (scrollState != SCROLL_STATE_IDLE) {
            // Wenn der VGV aktiv scrollt (ziehen oder abbremsen),
            // ignorieren wir manuelle Fokus-Suchen (vom D-Pad).
            // Wir lassen die Basisklasse die Fokus-Suche durchführen, die in der Regel
            // den Fokus an die nächstgelegene sichtbare Kachel verschiebt, sobald der Scrollvorgang stoppt.
            Log.d("CustomVGV", "FocusSearch suppressed: VGV is scrolling (State: $scrollState)")
            return focused?.parent as? View ?: focused // Fokus bleibt auf Row-View
        }

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
            // 👇 Sichtbarkeitsprüfung einbauen:
            val rect = Rect()
            val visible = child.isShown && child.getGlobalVisibleRect(rect)
            if (!visible) {
                Log.d("VGV FIND FOCUS CHILDS", "${targetHgv.hashCode()} Skip invisible candidate: ${child.tag}")
                continue
            }
            val childCenterX = (child.left + child.right) / 2
            val distance = abs(childCenterX - currentCenterX)
            if (distance < bestDistance) {
                bestDistance = distance
                bestView = child
            }
        }

        bestView?.let { candidate ->

            Log.d("VGV FIND FOCUS", "${targetHgv.hashCode()} ✅ Focus candidate accepted: ${candidate.tag}")

            val childVH = targetHgv.findContainingViewHolder(candidate) ?: return@let
            val targetPosition = childVH.bindingAdapterPosition // Position des besten sichtbaren Views

            // 1. Asynchrones Scrollen, um sicherzustellen, dass das "ideale" Item zentriert wird.
            // Der Fokus wird auf das "beste sichtbare" Item (candidate) gesetzt,
            // und danach scrollt die Row weiter, damit der Benutzer das "ideale" Item sieht.

            // Verwenden Sie eine der Scroll-Methoden der HorizontalGridView, um das View
            // mit dem besten X-Alignment zu zentrieren:
            targetHgv.setSelectedPositionSmooth(targetPosition) // Oder eine ähnliche Methode

            // 2. Setzen Sie dann den Fokus auf das gefundene (sichtbare) View
            targetHgv.isScrollEnabled = false
            targetHgv.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            targetHgv.isFocusable = true
            targetHgv.isFocusableInTouchMode = true
            synchronizer?.suppressSyncForNextFocusChange = true

            // 3. WICHTIG: Setzen Sie den Fokus direkt auf das gefundene View, NICHT auf die Row!
            return candidate // Das gefundene Kind zurückgeben, um den Fokus zu setzen.
        }
        Log.d("VGV FIND FOCUS", "${targetHgv.hashCode()} ⚠️ Kein sichtbarer Kandidat gefunden, nutze Default-Fokus.")
        return super.focusSearch(focused, direction)
    }
}
