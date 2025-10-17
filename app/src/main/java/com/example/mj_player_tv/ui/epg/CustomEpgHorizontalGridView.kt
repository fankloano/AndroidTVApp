package com.example.mj_player_tv.ui.epg

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
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

    init {
        // Leanback Alignment abschalten → verhindert das automatische Nachrücken
        windowAlignment = BaseGridView.WINDOW_ALIGN_BOTH_EDGE
        windowAlignmentOffset = 0
        windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
        setItemAlignmentOffset(0)
        setItemAlignmentOffsetPercent(BaseGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED)
    }

    override fun onScrolled(dx: Int, dy: Int) {
        // Optional: Debug-Ausgabe
        Log.d("CustomEpgHGridView", "${this.hashCode()} = SCROLL = $dx")

        super.onScrolled(dx, dy)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        event ?: return super.dispatchKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    focusNextItem(View.FOCUS_RIGHT)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    focusNextItem(View.FOCUS_LEFT)
                    return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    override fun requestChildRectangleOnScreen(
        child: View,
        rect: Rect,
        immediate: Boolean
    ): Boolean {
        // Geben Sie für alle Fokus-Anfragen (immediate = false) false zurück,
        // um den automatischen Scroll zu verhindern.
        if (!immediate) {
            return false
        }
        // Für andere Fälle (z.B. manuelle Anfragen mit immediate=true)
        // könnten wir das Standardverhalten beibehalten oder ebenfalls unterdrücken.
        return super.requestChildRectangleOnScreen(child, rect, true)
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
            nextView?.let {
                if (direction == View.FOCUS_RIGHT) {
                    // 2. 🔥 Scroll-Distanz berechnen, um das Element zu zentrieren
                    val viewWidth = width // Breite der HorizontalGridView
                    val viewCenter =
                        scrollX + (viewWidth / 2) // Aktueller Mittelpunkt des sichtbaren Bereichs

                    val nextViewWidth = nextView.width
                    val nextViewLeft = nextView.left

// Mittelpunkt des Item-Views (relativ zum Content-Anfang der HGV)
                    val itemCenter = nextViewLeft + (nextViewWidth / 2)

// Der benötigte Scroll-Betrag (dx) ist die Distanz vom aktuellen Viewport-Zentrum
// zum gewünschten Item-Zentrum.
                    val dx = itemCenter - viewCenter
                    scrollBy(dx, 0)

                    post {
                        nextView.requestFocus()
                    }
                } else {
                    scrollToPosition(nextPos)
                    post {
                        nextView.requestFocus()
                    }
                }
            }
        } else {
            nextView?.requestFocus()
        }
    }
}
