package com.example.mj_player_tv.repository

import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import java.util.Collections

/**
 * Einfacher Manager, der mehrere RecyclerViews synchronisiert.
 * - register(rv) um RecyclerViews hinzuzufügen
 * - unregister(rv) falls nötig
 *
 * Verhindert Rückkopplungen via source tag.
 */
class TvGuideScrollSyncManager {

    private val rvs = Collections.synchronizedSet(mutableSetOf<RecyclerView>())

    @Volatile
    private var isSyncingFrom: RecyclerView? = null

    fun register(rv: RecyclerView) {
        if (!rvs.add(rv)) return

        // markieren für Identification
        rv.setTag(R.id.tag_sync_registered, true)

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var lastScrollX = 0

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (isSyncingFrom != null && isSyncingFrom !== recyclerView) return

                // beginne Sync
                isSyncingFrom = recyclerView
                try {
                    // Für alle anderen Rvs die gleiche scrollPosition setzen
                    synchronized(rvs) {
                        for (other in rvs) {
                            if (other === recyclerView) continue
                            other.scrollBy(dx, 0)
                        }
                    }

                } finally {
                    // kleine Verzögerung/Reset könnte nötig sein, aber setzen wir sofort zurück
                    isSyncingFrom = null
                }
            }
        })
    }

    fun unregister(rv: RecyclerView) {
        rvs.remove(rv)
        rv.setTag(R.id.tag_sync_registered, null)
        // Listener entfernen ist optional — hier ignoriert
    }

    fun clear() {
        rvs.clear()
        isSyncingFrom = null
    }
}
