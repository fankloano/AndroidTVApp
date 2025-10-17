package com.example.mj_player_tv.ui.epg

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SyncableHorizontalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var synchronizer: EpgScrollSynchronizer? = null

    init {
        // horizontaler LinearLayoutManager
        layoutManager = CustomEpgLinearLayoutManager(context, HORIZONTAL, false)

        // optional: bessere Performance
        setHasFixedSize(true)
        isFocusable = true
        isFocusableInTouchMode = true

        // KeyEvent-DPad-Fokus-Steuerung
        setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> smoothScrollBy(-width / 3, 0)
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    Log.d("SCROLL NORM RECYCLER", "GESCROLLT: ${this.hashCode()} ${width / 3}")

                    smoothScrollBy(width / 3, 0)
                }
            }
            false
        }
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
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
}
