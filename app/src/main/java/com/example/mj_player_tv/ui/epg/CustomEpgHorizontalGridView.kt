package com.example.mj_player_tv.ui.epg

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.GridLayoutManager
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CustomEpgHorizontalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalGridView(context, attrs, defStyleAttr) {

    val synchronizer: EpgScrollSynchronizer
        get() {
            return EpgScrollSynchronizer()
        }

    init {
        // Leanback Einstellungen
        windowAlignment = WINDOW_ALIGN_BOTH_EDGE
        itemAlignmentOffsetPercent = ITEM_ALIGN_OFFSET_PERCENT_DISABLED
    }

    var focusedRecycler: Int = -1

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        event ?: return super.dispatchKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    focusedRecycler = this.hashCode()
                    focusNextItem(View.FOCUS_RIGHT)
                    return true // wir übernehmen die Navigation
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    focusNextItem(View.FOCUS_LEFT)
                    return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    fun focusNextItem(direction: Int) {
        val focused = focusedChild ?: return
        val currentPos = getChildAdapterPosition(focused)
        if (currentPos == RecyclerView.NO_POSITION) return

        val nextPos = currentPos + if (direction == View.FOCUS_RIGHT) 1 else -1
        if (nextPos !in 0 until (adapter?.itemCount ?: 0)) return

        // Prüfen, ob das Item aktuell sichtbar ist
        val nextView = findViewHolderForAdapterPosition(nextPos)?.itemView
        val needScroll = if (nextView != null) {
            val itemStart = nextView.left
            val itemEnd = nextView.right
            val viewStart = scrollX
            val viewEnd = scrollX + width

            // Scroll nötig, wenn Item teilweise oder gar nicht sichtbar
            itemStart < viewStart || itemEnd > viewEnd
        } else {
            true // Item noch nicht gebunden → scrollen
        }

        if (needScroll) {
            // Scroll zum Item per Adapter-Position
            scrollToPosition(nextPos)

            // Nach dem Layout: Fokus setzen und andere HGVs synchron scrollen
            post {
                val updatedNextView = findViewHolderForAdapterPosition(nextPos)?.itemView
                updatedNextView?.requestFocus()
            }
        } else {
            // Vollständig sichtbar → nur Fokus setzen
            nextView?.requestFocus()
        }
    }

    private fun scrollToViewIfNeeded(view: View?) {
        view ?: return
        val itemStart = view.left
        val itemEnd = view.right
        val viewStart = scrollX
        val viewEnd = scrollX + width

        val dx = when {
            itemStart < viewStart -> itemStart - viewStart
            itemEnd > viewEnd -> itemEnd - viewEnd
            else -> 0
        }
        if (dx != 0) smoothScrollBy(dx, 0)
    }

    override fun onScrolled(dx: Int, dy: Int) {
        // Optional: Debug-Ausgabe
        Log.d("CustomEpgHGridView", "${this.hashCode()} = SCROLL = $dx")

        super.onScrolled(dx, dy)
    }
}
