package com.example.mj_player_tv.ui.epg

import android.util.Log
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

    // --- Horizontale Synchronisation (Timeline <-> Alle Program Rows) ---


    // Felder zur Protokollierung der Rekursion
    private var syncScrollCounter = 0
    private var syncScrollId = 0
    // In EpgScrollSynchronizer

    // In EpgScrollSynchronizer

    // NEUE HILFSMETHODE
    fun syncAllViewsToMaster(masterView: RecyclerView, scrollOffSet: Int) {

        // Beachte: Wir müssen hier die gleichen Checks wie im Listener durchführen!
        if (isSyncScrolling) {
            Log.d("EpgSync_FORCE", "🚫 Sync blockiert, bereits aktiv.")
            return
        }

        isSyncScrolling = true
        syncScrollId++

        val targetOffset = scrollOffSet

        // 🔥 WICHTIG: Prüfen Sie den Ziel-Offset, um Redundanzen zu vermeiden
        if (targetOffset == 0) {
            isSyncScrolling = false
            return
        }

        Log.d("EpgSync_FORCE", "📍 MASTER (FORCE): HGV ${masterView.hashCode()} (ID ${syncScrollId}) scrollt zu Ziel=$targetOffset")

        for (ref in horizontalViewsToSync.toList()) {
            val syncView = ref.get()
            if (syncView == null) {
                horizontalViewsToSync.remove(ref)
                continue
            }

            if (syncView !== masterView) {
                // Setze die absolute Position
                syncView.scrollTo(targetOffset, 0)
                Log.d("EpgSync_FORCE", "   → Synced Program Row ${syncView.hashCode()}")
            }
        }

        isSyncScrolling = false
    }
    fun getCurrentHorizontalScrollOffset(): Int {
        val firstView = horizontalViewsToSync.firstOrNull()?.get()
        return firstView?.computeHorizontalScrollOffset() ?: 0
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
        Log.d("EPGSynch NEU GEBUNDEN", "${view.hashCode()} = SCROLL: $targetOffset")
        if (targetOffset != 0) {
            // 1. Setze das Flag, um das Auslösen eines Events durch das Scrollen zu verhindern
            isSyncScrolling = true

            // 2. Wende den Offset auf die neue View an (absolutes Scrollen ist hier richtig)
            view.scrollTo(targetOffset, 0)

            // 3. Setze das Flag zurück, damit weitere manuelle Scrolls Events auslösen
            isSyncScrolling = false
        }
    }
}