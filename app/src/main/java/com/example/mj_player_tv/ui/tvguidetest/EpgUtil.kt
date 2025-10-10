package com.example.mj_player_tv.ui.tvguidetest

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import org.joda.time.DateTime

object EpgUtil {

    /**
     * Prüft, ob `child` ein Nachkomme von `parent` ist
     */
    fun isDescendant(parent: ViewGroup, child: View?): Boolean {
        var current = child
        while (current != null && current != parent) {
            val parentView = current.parent
            current = parentView as? View
        }
        return current == parent
    }

    /**
     * Konvertiert eine Zeit in Millisekunden in Pixel auf der Timeline
     */
    fun calculateEpgWidth(startTimestamp: Long, stopTimestamp: Long, pixelsPerMinute: Float = 5f): Int {
        val durationSeconds = stopTimestamp - startTimestamp
        val durationMinutes = durationSeconds / 60f
        return (durationMinutes * pixelsPerMinute).toInt()
    }

    /**
     * Findet das nächste fokussierbare Programm innerhalb eines Bereichs
     * @param parentView: Container mit den Show-Items
     * @param focusRange: sichtbarer Bereich in Pixeln (left..right)
     * @param keepCurrentFocused: true -> Fokus soll auf aktueller Show bleiben, falls möglich
     */
    fun findNextFocusable(
        parentView: ViewGroup,
        focusRange: Rect,
        keepCurrentFocused: Boolean
    ): View? {
        var candidate: View? = null
        var minDistance = Int.MAX_VALUE

        for (i in 0 until parentView.childCount) {
            val child = parentView.getChildAt(i)
            if (!child.isShown || !child.isFocusable) continue

            val rect = Rect()
            child.getGlobalVisibleRect(rect)
            val distance = when {
                rect.right < focusRange.left -> focusRange.left - rect.right
                rect.left > focusRange.right -> rect.left - focusRange.right
                else -> 0 // innerhalb des Fokusbereichs
            }

            if (distance < minDistance) {
                minDistance = distance
                candidate = child
            }

            if (keepCurrentFocused && child.isFocused) {
                return child
            }
        }
        return candidate
    }

    /**
     * Hilfsmethode: erstellt einen eindeutigen Tag für ein ShowView
     */
    fun getShowTag(channelId: String, showId: String): String {
        return "show_${channelId}_$showId"
    }

    /**
     * Prüft, ob die Show die aktuelle Sendung ist
     */
    fun isCurrentProgram(showView: View): Boolean {
        val tag = showView.tag as? String ?: return false
        val currentTime = System.currentTimeMillis() / 1000
        val showTime = (showView.getTagTimestamp()) // eigene Extension, z.B. Long
        return currentTime in showTime..(showTime + showView.getTagDuration())
    }

    /**
     * Extension-Funktionen für Tag-Zeit
     */
    fun View.getTagTimestamp(): Long {
        val tagData = this.tag as? ShowTag ?: return 0
        return tagData.startTimestamp
    }

    fun View.getTagDuration(): Long {
        val tagData = this.tag as? ShowTag ?: return 0
        return tagData.stopTimestamp - tagData.startTimestamp
    }

    fun findCurrentShow(channel: TvChannelWithEpg): EpgDataOB? {
        val now = System.currentTimeMillis() / 1000
        return channel.epgList.find { show ->
            now in show.startTimestamp..show.stopTimestamp
        }
    }

    fun createTimelineData(): List<TimeLineData> {
        val timeline = mutableListOf<TimeLineData>()

        val now = DateTime.now()

        // Letzte volle Stunde minus 15 Minuten
        val start = now
            .withMinuteOfHour(0)
            .withSecondOfMinute(0)
            .withMillisOfSecond(0)

        // Ende = letzte volle Stunde + 12 Stunden
        val end = start.plusHours(12)
        epgStartTime = start
        epgEndTime = end

        var current = start
        val pixelPerMinute = 5
        val halfHourWidth = 30 * pixelPerMinute // 150 px pro Halbstunde

        while (current <= end) {
            val timeId = current
            val displayText = current.toString("HH:mm")

            timeline.add(
                TimeLineData(
                    timeId = timeId,
                    time = displayText,
                    width = halfHourWidth,
                    textSizeSp = 14f
                )
            )
            current = current.plusMinutes(30)
        }

        return timeline
    }


    var epgStartTime = DateTime()
    var epgEndTime = DateTime()
}



/**
 * Datenklasse für ShowTag, damit wir Tag und Timestamps an Views binden können
 */
data class ShowTag(
    val channelId: String,
    val showId: String,
    val startTimestamp: Long = 0,
    val stopTimestamp: Long = 0
)
