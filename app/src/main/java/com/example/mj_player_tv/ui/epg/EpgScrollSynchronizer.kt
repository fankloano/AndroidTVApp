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

    // Liste aller HorizontalGridViews (Programmzeilen) und der Timeline
    private val horizontalViewsToSync = mutableSetOf<WeakReference<RecyclerView>>()
    // Die beiden vertikalen Views
    private var channelListView: RecyclerView? = null
    private var programGridView: VerticalGridView? = null

    // Flag zur Vermeidung von Endlosschleifen bei Scroll-Ereignissen
    private var isSyncScrolling = false

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

    fun registerHorizontalView(view: RecyclerView) {
        horizontalViewsToSync.add(WeakReference(view))
        view.addOnScrollListener(horizontalScrollListener)
    }

    // --- Vertikale Synchronisation (Channel List <-> Program Grid) ---

    // Dies sollte funktionieren, wenn die Views 1:1 identisch sind
    private val verticalScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (isSyncScrolling || dy == 0) return
            // stoppe laufende Scrolls der alten HGVs
            horizontalViewsToSync.forEach { it.get()?.stopScroll() }

            isSyncScrolling = true

            // ProgramGridView als Master
            val masterView = programGridView
            val followerView = channelListView

            // Nur reagieren, wenn Master scrollt
            if (recyclerView == masterView) {
                // Follower relativ bewegen
                followerView?.scrollBy(0, dy)
            }

            isSyncScrolling = false
        }
    }

    // In EpgScrollSynchronizer Klasse
    private var isSyncScrollingHorizontal = false
    private var syncScrollCounter = 0
    private var syncScrollId = 0
// private var suppressSyncForNextFocusChange = false // (Falls in Ihrer Klasse vorhanden)


    private val horizontalScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            // 1. ZUERST: Rekursionsschutz und Ignorieren von Null-Bewegungen (dx=0)
            if (isSyncScrollingHorizontal || dx == 0) {
                if (isSyncScrollingHorizontal && dx != 0) {
                    // Protokollierung der Rekursion, falls sie doch auftritt
                    Log.d("EpgSync_RECURSION", "🚫 ABGEFANGEN: HGV ${recyclerView.hashCode()} (ID ${syncScrollId}) während Sync-Block ausgeführt. Zähler: ${syncScrollCounter}")
                    syncScrollCounter++
                }
                return
            }

            // --- Optionale Fokus-Unterdrückung (falls benötigt) ---
            // if (suppressSyncForNextFocusChange) {
            //     suppressSyncForNextFocusChange = false
            //     Log.d("EpgSync", "🚫 Auto-Scroll unterdrückt.")
            //     return
            // }

            // --- Synchronisationslogik START ---

            isSyncScrollingHorizontal = true // Starte den Sync-Block
            syncScrollId++         // Starte eine neue Sync-ID
            syncScrollCounter = 1  // Setze den Zähler zurück

            val masterView = recyclerView

            // 🔥 Hole den aktuellen ABSOLUTEN Scroll-Offset des Masters.
            // Dies ist der stabile Zielwert für alle Slaves.
            currentHorizontalScrollOffset += dx
            val targetOffset = currentHorizontalScrollOffset

            Log.d("EpgSync_START", "📍 MASTER: HGV ${masterView.hashCode()} (ID ${syncScrollId}) scrollt zu Ziel=$targetOffset (dx=$dx)")

            // Synchronisiere alle anderen Views
            for (ref in horizontalViewsToSync.toList()) {
                val syncView = ref.get()

                // Garbage Collection Check
                if (syncView == null) {
                    horizontalViewsToSync.remove(ref)
                    continue
                }

                // Wichtig: Nur die Views scrollen, die NICHT der Master sind
                if (syncView !== masterView) {

                    // 🔥 Stabile Synchronisation mit scrollTo (absolute Position)
                    syncView.scrollBy(dx, 0)

                    // Protokolliere die gescrollte View
                    Log.d("EpgSync_SYNCED", "   → Synced HGV ${syncView.hashCode()} zu $targetOffset (ID ${syncScrollId})")
                }
            }

            isSyncScrollingHorizontal = false // Beende den Sync-Block
        }
    }

    fun getCurrentHorizontalScrollOffset(): Int {
        return currentHorizontalScrollOffset
    }

    // Fügen Sie diese Methode zu Ihrer EpgScrollSynchronizer Klasse hinzu:
    fun release() {
        channelListView?.removeOnScrollListener(verticalScrollListener)
        programGridView?.removeOnScrollListener(verticalScrollListener)

        horizontalViewsToSync.clear()

        // Setze die starken Referenzen zurück
        channelListView = null
        programGridView = null
    }

    //Wendet den aktuellen horizontalen Synchronisations-Offset auf eine neue View an.
    // Sollte im RecyclerView.onBindViewHolder der EPG-Row aufgerufen werden.
//Wendet den aktuellen horizontalen Synchronisations-Offset auf eine neue View an.
    fun setInitialHorizontalOffset(view: RecyclerView) {
        val targetOffset = getCurrentHorizontalScrollOffset()
        if (targetOffset == 0) return

        view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // Jetzt ist das Layout wirklich bereit
                isSyncScrollingHorizontal = true
                view.scrollBy(targetOffset, 0)
                isSyncScrollingHorizontal = false

                Log.d("EpgSync_INIT", "✅ Initial scroll applied to ${view.hashCode()} → $targetOffset")
            }
        })
    }
}