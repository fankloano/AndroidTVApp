package com.example.mj_player_tv.ui.epg

import android.util.Log
import android.view.ViewTreeObserver
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.lang.ref.WeakReference

/**
 * Verwaltet die Synchronisation des vertikalen und horizontalen Scrollens
 * zwischen den verschiedenen EPG-Komponenten.
 */
class EpgScrollSynchronizer {

    // Liste aller HorizontalGridViews (Programmzeilen) und der Timeline// Die beiden vertikalen Views
    private var channelListView: RecyclerView? = null
    private var programGridView: VerticalGridView? = null

    // Flag zur Vermeidung von Endlosschleifen bei Scroll-Ereignissen
    private var isSyncScrolling = false

    private var isSyncScrollingHorizontal = false

    // Flag, um Sync bei Fokuswechsel zu unterdrücken
    var suppressSyncForNextFocusChange = false

    private var currentHorizontalScrollOffset = 0
    // --- Initialisierung ---


    fun setupVerticalSync(channelList: RecyclerView, programGrid: VerticalGridView) {
        this.channelListView = channelList
        this.programGridView = programGrid

        // Füge den Listener zu beiden vertikalen Views hinzu
        channelList.addOnScrollListener(verticalScrollListener)
        programGrid.addOnScrollListener(verticalScrollListener)
    }

    private val horizontalViews = mutableSetOf<WeakReference<RecyclerView>>()
    private var currentOffsetPx = 0

    fun registerHorizontalView(view: RecyclerView) {
        horizontalViews.add(WeakReference(view))
        view.addOnScrollListener(horizontalScrollListener)
    }


    fun unregisterHorizontalView(view: RecyclerView) {
        horizontalViews.removeAll { it.get() == view || it.get() == null }
    }

    private val horizontalScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (isSyncScrollingHorizontal || dx == 0) return

            if (suppressSyncForNextFocusChange) {
                suppressSyncForNextFocusChange = false
                return
            }
            currentHorizontalScrollOffset += dx
            isSyncScrollingHorizontal = true
            val masterView = recyclerView
            Log.d("SCROLL NORM RECYCLER", "MASTER: ${masterView.hashCode()}")
            for (ref in horizontalViews.toList()) {
                val syncView = ref.get()
                if (syncView == null) {
                    horizontalViews.remove(ref)
                    continue
                }
                if (syncView != masterView) {
                    syncView.scrollBy(dx, 0)
                }
            }

            isSyncScrollingHorizontal = false
        }
    }
    // --- Vertikale Synchronisation (Channel List <-> Program Grid) ---

    // Dies sollte funktionieren, wenn die Views 1:1 identisch sind
    private val verticalScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (isSyncScrolling || dy == 0) return
            // stoppe laufende Scrolls der alten HGVs
            horizontalViews.forEach { it.get()?.stopScroll() }

            isSyncScrolling = true

            // ProgramGridView als Master
            val masterView = programGridView
            val followerView = channelListView

            // Nur reagieren, wenn Master scrollt
            if (recyclerView == masterView) {
                // Follower relativ bewegen
                followerView?.scrollTo(0, dy)
            }

            isSyncScrolling = false
        }
    }


    // Fügen Sie diese Methode zu Ihrer EpgScrollSynchronizer Klasse hinzu:
    fun release() {
        channelListView?.removeOnScrollListener(verticalScrollListener)
        programGridView?.removeOnScrollListener(verticalScrollListener)

        horizontalViews.clear()

        // Setze die starken Referenzen zurück
        channelListView = null
        programGridView = null
    }

    // Speichert die Listener für jede HGV-Instanz
    private val layoutListeners =
        mutableMapOf<Int, ViewTreeObserver.OnGlobalLayoutListener>()

    fun initialHorizontalScroll(): Int {
        return currentHorizontalScrollOffset
    }
    fun cancelInitialScroll(view: RecyclerView) {
        val listener = layoutListeners.remove(view.hashCode())
        listener?.let {
            // Entfernen des Listeners, wenn er noch aktiv war
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnGlobalLayoutListener(it)
                Log.d("HGV_Key", "HGV ${view.hashCode()} Layout-Listener abgebrochen.")
            }
        }
    }
}