package com.example.mj_player_tv.repository

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.utils.views.TimeMarksRecyclerView
import java.util.Collections

/**
 * Einfacher Manager, der mehrere RecyclerViews synchronisiert.
 * - register(rv) um RecyclerViews hinzuzufügen
 * - unregister(rv) falls nötig
 *
 * Verhindert Rückkopplungen via source tag.
 */
class TvGuideScrollSyncManager {

    private val listeners = Collections.synchronizedSet(mutableSetOf<RecyclerView>())
    @Volatile
    private var isSyncingFrom: RecyclerView? = null

    // Diese Variable speichert die kumulierte, globale Scroll-Position
    var totalScrollX = 0f

    private var redLineView: View? = null

    fun setRedLineView(view: View) {
        this.redLineView = view
    }

    /**
     * Registriere eine RecyclerView (Timeline oder ProgramRow)
     */
    fun register(rv: RecyclerView) {
        if (!listeners.add(rv)) return

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Verhindere Reentrancy
                if (isSyncingFrom != null && isSyncingFrom !== recyclerView) return

                isSyncingFrom = recyclerView
                try {
                    synchronized(listeners) {
                        // 1. Aktualisiere die globale Scroll-Position mit dem Delta-X
                        totalScrollX += dx

                        // 2. Setze die Position der roten Linie auf diese globale Position
                        redLineView?.translationX = totalScrollX

                        // 3. Scrolle alle anderen Listener um dasselbe Delta-X
                        for (other in listeners) {
                            if (other === recyclerView) continue

                            if (other is TimeMarksRecyclerView) {
                                other.scrollContentBy(dx)
                            } else {
                                other.scrollBy(dx, 0)
                            }
                        }
                    }
                } finally {
                    isSyncingFrom = null
                }
            }
        })
    }

    fun unregister(rv: RecyclerView) {
        listeners.remove(rv)
        rv.setTag(R.id.tag_sync_registered, null)
    }

    fun clear() {
        listeners.clear()
        isSyncingFrom = null
    }
}