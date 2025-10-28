package com.example.mj_player_tv.ui.epg.util

import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.ui.epg.CustomEpgHorizontalGridView
import com.example.mj_player_tv.ui.epg.CustomEpgVerticalGridView
import com.example.mj_player_tv.ui.epg.EpgProgramItemView
import java.util.concurrent.TimeUnit
import androidx.core.view.isEmpty
import androidx.recyclerview.widget.LinearLayoutManager

object EpgUtil {
    @JvmStatic
    var WIDTH_PER_HOUR = 300 // Dies ist die zentrale Stelle

    @JvmStatic
    fun convertMillisToPixel(millis: Long): Int {
        return (millis * 300 / TimeUnit.HOURS.toMillis(1)).toInt()
    }

    @JvmStatic
    fun convertMillisToPixel(startMillis: Long, endMillis: Long): Int {

        return convertMillisToPixel(endMillis) - convertMillisToPixel(startMillis)
    }

    /** Gets the time in millis that corresponds to the given pixels in the program guide.  */
    fun convertPixelToMillis(pixel: Int): Long {
        return pixel * TimeUnit.HOURS.toMillis(1) / WIDTH_PER_HOUR
    }

    /**
     * Return the view should be focused in the given program row according to the focus range.
     *
     * @param keepCurrentProgramFocused If `true`, focuses on the current program if possible,
     * else falls back the general logic.
     */


    private fun findFocusables(v: View, outFocusable: ArrayList<View>) {
        if (v.isFocusable) {
            outFocusable.add(v)
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findFocusables(v.getChildAt(i), outFocusable)
            }
        }
    }

    /**
     * Findet in der angegebenen Zeile (HorizontalGridView) das Programm-View,
     * das zeitlich am besten zum aktuell fokussierten Programm passt.
     *
     * @param currentProgram Das aktuell fokussierte EPG-Objekt
     * @param nextRow Die Zeile, in der gesucht werden soll
     * @return Das passende EpgProgramItemView oder null, falls keines passt
     */
    fun findVisibleMatchingProgramView(
        currentProgram: EpgDataOB,
        nextRow: CustomEpgHorizontalGridView
    ): EpgProgramItemView? {

        if (nextRow.isEmpty()) return null

        val layoutManager = nextRow.layoutManager as? LinearLayoutManager ?: return null

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        val visibleChildren = (firstVisible..lastVisible)
            .mapNotNull { index ->
                nextRow.layoutManager?.findViewByPosition(index) as? EpgProgramItemView
            }

        if (visibleChildren.isEmpty()) return null

        // Berechne Zielzeit: Mitte des aktuell fokussierten Programms
        val targetTime = (currentProgram.startTimestamp + currentProgram.stopTimestamp) / 2
        var bestMatchView: EpgProgramItemView? = null
        var smallestTimeDiff = Long.MAX_VALUE

        val nextChannel = nextRow.thischannel?.tvChannelPosition?.tvchannel?.target?.showingName
        Log.d(
            "FOCUS_MATCH_DEBUG",
            "---- CHANNEL: $nextChannel | Target: ${currentProgram.name} @ $targetTime ----"
        )

        // ⬇️ Nur sichtbare Programme vergleichen
        for (child in visibleChildren) {
            val program = child.programData ?: continue
            val programMid = (program.startTimestamp + program.stopTimestamp) / 2
            val diff = kotlin.math.abs(programMid - targetTime)

            Log.d(
                "FOCUS_MATCH_DEBUG",
                "Candidate: ${program.name} | Start=${program.startTimestamp}, Stop=${program.stopTimestamp}, Mid=$programMid | Diff=$diff"
            )

            if (diff < smallestTimeDiff) {
                smallestTimeDiff = diff
                bestMatchView = child
                Log.d("FOCUS_MATCH_DEBUG", "→ NEW BEST MATCH: ${program.name} (Diff=$diff)")
            }
        }

        Log.d(
            "FOCUS_MATCH_DEBUG",
            "==> FINAL MATCH for $nextChannel: ${bestMatchView?.programData?.name} (Diff=$smallestTimeDiff)\n"
        )

        return bestMatchView
    }

}