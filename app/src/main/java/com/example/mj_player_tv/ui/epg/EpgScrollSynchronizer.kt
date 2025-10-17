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
    val horizontalViewsToSync = mutableSetOf<WeakReference<RecyclerView>>()
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
        Log.d("HGV_Key", "HGV REGISTER HASHCODE: ${view.hashCode()}")

    }
    fun unregisterHorizontalView(view: RecyclerView) {
        // 1. Suche die WeakReference, die auf die übergebene View zeigt.
        val refToRemove = horizontalViewsToSync.find { it.get() == view }

        // 2. Entferne die gefundene WeakReference aus dem Set.
        if (refToRemove != null) {
            horizontalViewsToSync.remove(refToRemove)
            // Optional: Entferne auch den ScrollListener, wenn er nicht mehr gebraucht wird
            Log.d("HGV_Key", "HGV UNREGISTER HASHCODE: ${view.hashCode()}")
        }
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

    private var isSyncScrollingHorizontal = false
    private var syncScrollCounter = 0
    private var syncScrollId = 0 //

    private val horizontalScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        // 1. ZUERST: Rekursionsschutz und Ignorieren von Null-Bewegungen (dx=0)
            if (isSyncScrollingHorizontal || dx == 0) { // ... (Logik bleibt)
                return } // 🔥 NEU: Prüfe, ob die Synchronisation unterdrückt werden soll (Focuswechsel)
        if (suppressSyncForNextFocusChange) { // Scroll-Offset dennoch aktualisieren, damit die nächste Zeile korrekt initialisiert wird.
        currentHorizontalScrollOffset += dx
            suppressSyncForNextFocusChange = false
            Log.d("EpgSync", "🚫 Auto-Scroll unterdrückt wegen Fokuswechsel. Offset: ${currentHorizontalScrollOffset}")
            return
        } // --- Synchronisationslogik START ---
        isSyncScrollingHorizontal = true // Starte den Sync-Block
        val masterView = recyclerView // 🔥 Hole den aktuellen ABSOLUTEN Scroll-Offset des Masters. // Dies ist der stabile Zielwert für alle Slaves.
        currentHorizontalScrollOffset += dx
            val targetOffset = currentHorizontalScrollOffset
            Log.d("EpgSync_START", "📍 MASTER: HGV ${masterView.hashCode()} (ID ${syncScrollId}) scrollt zu Ziel=$targetOffset (dx=$dx)") // Synchronisiere alle anderen Views
        for (ref in horizontalViewsToSync.toList()) {
            val syncView = ref.get() // Garbage Collection Check
        if (syncView == null) {
            horizontalViewsToSync.remove(ref)
            continue } // Wichtig: Nur die Views scrollen, die NICHT der Master sind
        if (syncView !== masterView) { // 🔥 Stabile Synchronisation mit scrollTo (absolute Position)
        syncView.scrollBy(dx, 0) // Protokolliere die gescrollte View
        Log.d("HORIZONTAL SCROLL OTHERS", " → Synced HGV ${syncView.hashCode()} um $dx zu $targetOffset") }
        }
            isSyncScrollingHorizontal = false // Beende den Sync-Block } }
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

    // Speichert die Listener für jede HGV-Instanz
    private val layoutListeners =
        mutableMapOf<Int, ViewTreeObserver.OnGlobalLayoutListener>()
    // Im EpgScrollSynchronizer

    fun setInitialHorizontalOffset(view: RecyclerView) {
        val targetOffset = getCurrentHorizontalScrollOffset()
        if (targetOffset != 0) { // 1. Erstelle den Listener, der das Scrollen ausführt.
        val layoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() { // Den Listener sofort entfernen, damit er nur einmal ausführt.
            view.viewTreeObserver.removeOnGlobalLayoutListener(this) // Prüfen, ob die View noch an das Fenster angehängt ist (zusätzliche Sicherheit)
            if (!view.isAttachedToWindow) return
                val finalOffset = getCurrentHorizontalScrollOffset()
                isSyncScrollingHorizontal = true
                view.scrollBy(finalOffset, 0)
                isSyncScrollingHorizontal = false
                Log.d("HGV_INIT_SCROLL", "${view.hashCode()} = SCROLL TOTAL (Layout) = $finalOffset")
            }
        } // 2. Füge den Listener hinzu. Er wartet auf den nächsten Layout-Pass.
         view.viewTreeObserver.addOnGlobalLayoutListener(layoutListener) // Speichern des Listeners mit dem Hashcode der View als Schlüssel
         layoutListeners[view.hashCode()] = layoutListener } else { isSyncScrollingHorizontal = false
         }
    }


    // Füge diese Methode hinzu/ändere sie:
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