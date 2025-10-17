package com.example.mj_player_tv.ui.epg

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// CustomEpgLinearLayoutManager.kt

class CustomEpgLinearLayoutManager(
    context: Context,
    orientation: Int,
    reverseLayout: Boolean
) : LinearLayoutManager(context, orientation, reverseLayout) {

    /**
     * 🔥 Überschreibt das Standardverhalten, das die RecyclerView scrollt,
     * um das fokussierte Kind-View (child) in den sichtbaren Bereich zu bringen.
     * Indem wir immer 'false' zurückgeben, unterdrücken wir diesen autonomen Scroll.
     */
    override fun requestChildRectangleOnScreen(
        parent: RecyclerView,
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
        return super.requestChildRectangleOnScreen(parent, child, rect, immediate)
    }


}