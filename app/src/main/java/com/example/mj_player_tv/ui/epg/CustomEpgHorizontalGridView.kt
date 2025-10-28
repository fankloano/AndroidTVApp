package com.example.mj_player_tv.ui.epg

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.ui.epg.util.EpgUtil
import org.joda.time.DateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class CustomEpgHorizontalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CustomEpgTimeLineGridView(context, attrs, defStyleAttr) {

    var thischannel: TvChannelWithEpg? = null

    // NEUE KONSTANTEN FÜR DEN PUFFER
    val RIGHT_SCROLL_BUFFER_MILLIS = TimeUnit.MINUTES.toMillis(30)
    val LEFT_SCROLL_BUFFER_MILLIS = TimeUnit.MINUTES.toMillis(5)
    private lateinit var epgManager: EpgManager
    fun setChannel(channelToSet: TvChannelWithEpg) {
        thischannel = channelToSet
    }

    fun setEpgManager(thisEpgManager: EpgManager) {
        epgManager = thisEpgManager
    }

    // Call this API after RTL is resolved. (i.e. View is measured.)
    private fun isDirectionStart(direction: Int): Boolean {
        return if (layoutDirection == LAYOUT_DIRECTION_LTR)
            direction == FOCUS_LEFT
        else
            direction == FOCUS_RIGHT
    }

    // Call this API after RTL is resolved. (i.e. View is measured.)
    private fun isDirectionEnd(direction: Int): Boolean {
        return if (layoutDirection == LAYOUT_DIRECTION_LTR)
            direction == FOCUS_RIGHT
        else
            direction == FOCUS_LEFT
    }
// ...

    override fun focusSearch(focused: View, direction: Int): View? {
        // 1. Hole das Ziel-View
        val target = super.focusSearch(focused, direction)
        if (target !is EpgProgramItemView) return target

        val targetProgram = target.programData ?: return target

        post {
            // 2. Mache alles Weitere im post { ... } Block
            //    Das stellt sicher, dass wir den Zustand ABFRAGEN, NACHDEM
            //    das System seinen eigenen Fokus-Scroll beendet hat.
            // 3. Hole den JETZT AKTUELLEN Zustand
            val fromMillis = epgManager.getVisibleTimeStart()
            val toMillis = epgManager.getVisibleTimeEnd()
            val startTimeline = epgManager.getTimeLineStart()
            val endTimeline = epgManager.getTimeLineEnd()

            val programStart = targetProgram.startTimestamp * 1000
            val programEnd = targetProgram.stopTimestamp * 1000
            val programWidth = programEnd - programStart
            val visibleWidth = toMillis - fromMillis

            // --- DEINE GEWÜNSCHTEN LOGS (START) ---
            // Helper, um Zeitstempel lesbar zu machen
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            Log.d("EPG_SCROLL_DEBUG", "--- NEUER FOKUS-CHECK (im Post) ---")
            Log.d(
                "EPG_SCROLL_DEBUG",
                "Sichtbar:   ${timeFormat.format(Date(fromMillis))} bis ${
                    timeFormat.format(
                        Date(toMillis)
                    )
                }"
            )
            Log.d(
                "EPG_SCROLL_DEBUG",
                "Ziel-Sendung: ${timeFormat.format(Date(programStart))} bis ${
                    timeFormat.format(
                        Date(programEnd)
                    )
                }"
            )
            Log.d(
                "EPG_SCROLL_DEBUG",
                "Timeline:     ${timeFormat.format(Date(startTimeline))} bis ${
                    timeFormat.format(Date(endTimeline))
                }"
            )

// 4. Berechne das Delta (Mit Puffer, wenn möglich)
            val delta: Long = if (programWidth <= visibleWidth) {
                // A) LOGIK FÜR SENDUNGEN, DIE IN DEN SICHTBAREN BEREICH PASSEN
                when {
                    // SCROLL NACH LINKS
                    programStart < fromMillis ->
                        (programStart - fromMillis) - LEFT_SCROLL_BUFFER_MILLIS

                    // SCROLL NACH RECHTS
                    programEnd > toMillis ->
                        // Hier wird das GERINGSTFÜGIG zu große Delta berechnet, das Clamping folgt in Schritt 5
                        (programEnd - toMillis) + RIGHT_SCROLL_BUFFER_MILLIS

                    else -> 0L
                }
            } else {
                // B) LOGIK FÜR SENDUNGEN, DIE BREITER ALS DER SCHIRM SIND (Kein Puffer)
                when {
                    // SCROLL NACH RECHTS (Ende ins Bild bringen)
                    direction == FOCUS_FORWARD || isDirectionEnd(direction) ->
                        programEnd - toMillis

                    // SCROLL NACH LINKS (Anfang ins Bild bringen)
                    direction == FOCUS_BACKWARD || isDirectionStart(direction) ->
                        programStart - fromMillis

                    else -> 0L
                }
            }
            // --- NEUER DEBUG LOG (VOR DEM CLAMPING) ---
            val unlimitiertesDeltaMs = delta
            val unlimitiertesDeltaMin = TimeUnit.MILLISECONDS.toMinutes(Math.abs(delta))

            Log.d(
                "EPG_SCROLL_DEBUG",
                "BERECHNUNG: Unlimitiertes Delta = $unlimitiertesDeltaMs ms ($unlimitiertesDeltaMin Minuten)"
            )
            // --- LOGS (ENDE) ---


            // 5. Clamping (Der entscheidende Schritt zur Einhaltung der Timeline-Grenze)
            val maxLeftDelta = startTimeline - fromMillis
            val maxRightDelta = endTimeline - toMillis // <= DIES IST DIE HARTE GRENZE!
            val limitedDelta = delta.coerceIn(maxLeftDelta, maxRightDelta) // <= HIER WIRD AUF DIE GRENZE REDUZIERT

            Log.d("EPG_SCROLL_DEBUG", "CLAMPING: maxLeft=$maxLeftDelta, maxRight=$maxRightDelta")
            Log.d("EPG_SCROLL_DEBUG", "CLAMPING: Delta wird limitiert auf $limitedDelta ms")

            // 6. Ausführen
            if (limitedDelta != 0L) {
                Log.d("EPG_SCROLL_DEBUG", "AKTION: Scrolle um $limitedDelta ms...")
                scrollByTime(limitedDelta)
            } else {
                Log.d(
                    "EPG_SCROLL_DEBUG",
                    "AKTION: 🚫 Kein Scroll (Delta war 0 oder Timeline-Limit erreicht)"
                )
            }
            Log.d("EPG_SCROLL_DEBUG", "--- FOKUS-CHECK ENDE ---")
        }
        // 7. Wichtig: Gib das Ziel sofort zurück
        return target
    }

    private fun scrollByTime(timeToScroll: Long) {
            epgManager.shiftTime(timeToScroll)
    }

    /** Resets the scroll with the initial offset `currentScrollOffset`.  */
    fun resetScroll2(scrollOffset: Int) {
        // --- 1. INPUT LOG ---
        Log.d("EPG_RESET_DEBUG", "--- NEUES resetScroll (Input Pixel: $scrollOffset) ---")

        val channel = thischannel

        // Berechne die Startzeit der Ansicht basierend auf dem übergebenen Pixel-Offset
        val startTime =
            EpgUtil.convertPixelToMillis(scrollOffset) + epgManager.getTimeLineStart()

        // Hilfslogs für die Zeit
        val visibleStart = DateTime(epgManager.getVisibleTimeStart()).toString("HH:mm")
        val currentScrollTime = DateTime(startTime).toString("HH:mm")

        Log.d("EPG_RESET_DEBUG", "Zeit: Manager Visible Start: $visibleStart")
        Log.d("EPG_RESET_DEBUG", "Zeit: Ziel Scroll-Start-Zeit: $currentScrollTime")


        val position = if (channel == null) {
            -1
        } else {
            epgManager.getProgramIndexAtTime(channel.id, startTime)
        }

        if (position < 0) {
            Log.d("EPG_RESET_DEBUG", "AKTION: Kein Programm für Zeit $currentScrollTime gefunden. Scrolle zu Position 0.")
            layoutManager?.scrollToPosition(0)
        } else if (channel?.id != null) {
            val slug = channel.id
            val entry = epgManager.getScheduleForChannelIdAndIndex(slug, position)

            // --- 2. ZIEL PROGRAMM LOG ---
            val entryStart = entry?.let { DateTime(it.startTimestamp * 1000).toString("HH:mm") } ?: "N/A"
            val entryName = entry?.name ?: "N/A"
            Log.d("EPG_RESET_DEBUG", "Ziel: Programm Position $position gefunden: $entryName (Start $entryStart) CHANNEL: ${thischannel?.tvChannelPosition?.tvchannel?.target?.showingName}")

            if (entry != null) {
                if (entry.startTimestamp * 1000 == epgManager.getTimeLineStart()) {
                    Log.d("EPG_RESET_DEBUG", "AKTION: Startzeit = Timeline Start. Scrolle zu Position 0.")
                    layoutManager?.scrollToPosition(0)
                } else {
                    // Pixel von Timeline-Start bis zum Start des gefundenen Programms
                    val offsetUntilProgramStart = EpgUtil.convertMillisToPixel(
                        epgManager.getTimeLineStart(), // Absolute 0-Pixel-Position
                        entry.startTimestamp * 1000 // Startzeit des Programms
                    )

                    // Berechnung des finalen Offsets
                    val offset = (offsetUntilProgramStart - scrollOffset)

                    // --- 3. FINALER SCROLL LOG ---
                    Log.d("EPG_RESET_DEBUG", "Scroll-Berechnung: Pixel bis Programmstart: $offsetUntilProgramStart Px")
                    Log.d("EPG_RESET_DEBUG", "Scroll-AKTION: Verschiebe Programm $entryName um $offset Px vom linken Rand.")

                    (layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                        position,
                        offset
                    )
                }
            } else {
                Log.d("EPG_RESET_DEBUG", "AKTION: Entry ist null. Scrolle zu Position 0.")
                layoutManager?.scrollToPosition(0)
            }
        }
        Log.d("EPG_RESET_DEBUG", "--- resetScroll ENDE ---")
    }

    fun resetScroll() {
        val channelId = thischannel?.id ?: return
        val visibleStart = epgManager.getVisibleTimeStart()

        val (program, index) = epgManager.getProgramAtTime(channelId, visibleStart)
        if (program == null) {
            Log.w("EPG_RESET_DEBUG", "Kein Programm bei ${DateTime(visibleStart).toString("HH:mm")} gefunden.")
            scrollToPosition(0)
            return
        }

        val offsetPx = EpgUtil.convertMillisToPixel(
            visibleStart,
            program.startTimestamp * 1000
        )

        Log.d("EPG_RESET_DEBUG", """
        resetScroll:
        Channel=$channelId -> ${thischannel?.tvChannelPosition?.tvchannel?.target?.showingName}
        VisibleStart=${DateTime(visibleStart).toString("HH:mm")}
        Program=${program.name} (${DateTime(program.startTimestamp * 1000).toString("HH:mm")} - ${DateTime(program.stopTimestamp * 1000).toString("HH:mm")})
        Index=$index
        Offset=$offsetPx px
    """.trimIndent())

        val layout = layoutManager as LinearLayoutManager
        if (program.startTimestamp * 1000 == visibleStart) {
            layout.scrollToPosition(index)
        } else {
            layout.scrollToPositionWithOffset(index, offsetPx)
        }
    }



    fun View.getVisibleWidthInParent(parent: RecyclerView): Int {
        val parentRect = Rect()
        parent.getHitRect(parentRect)
        val visibleRect = Rect()
        this.getLocalVisibleRect(visibleRect)
        return visibleRect.width().coerceAtMost(this.width)
    }

    fun View.isFullyVisible(parent: RecyclerView): Boolean =
        getVisibleWidthInParent(parent) == this.width

    fun View.isPartiallyVisible(parent: RecyclerView): Boolean =
        getVisibleWidthInParent(parent) in 1 until this.width

}
