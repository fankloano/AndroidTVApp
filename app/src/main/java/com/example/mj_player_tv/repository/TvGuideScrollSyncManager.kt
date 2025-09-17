package com.example.mj_player_tv.repository

import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.utils.views.CurrentTimeIndicatorDecoration
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

    private var timeIndicatorDecoration: CurrentTimeIndicatorDecoration? = null
    private var totalScrollX = 0f // <-- hier speichern wir den kumulierten Scroll

    fun setTimeIndicatorDecoration(decoration: CurrentTimeIndicatorDecoration) {
        this.timeIndicatorDecoration = decoration
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
                        for (other in listeners) {
                            if (other === recyclerView) continue

                            // Spezialfall TimeMarksRecyclerView
                            if (other is TimeMarksRecyclerView) {
                                other.scrollContentBy(dx)
                            } else {
                                other.scrollBy(dx, 0)
                            }
                        }

                        // NEU: totalScrollX aktualisieren und Linie verschieben
                        totalScrollX += dx
                        timeIndicatorDecoration?.setScrollOffset(totalScrollX)

                        // invalidate auf dem vertikalen Channel RecyclerView, damit die Linie gezeichnet wird
                        // Wir nehmen einfach das erste "normale" RecyclerView außer Timeline
                        listeners.firstOrNull { it !is TimeMarksRecyclerView }?.invalidate()
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
