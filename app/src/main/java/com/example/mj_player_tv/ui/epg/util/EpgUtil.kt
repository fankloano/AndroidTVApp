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

object EpgUtil {
    private var WIDTH_PER_HOUR = 300


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
    fun findMatchingProgramView(
        currentProgram: EpgDataOB,
        nextRow: CustomEpgHorizontalGridView
    ): EpgProgramItemView? {

        if (nextRow.isEmpty()) return null

        val targetTime = (currentProgram.startTimestamp + currentProgram.stopTimestamp) / 2
        var bestMatchView: EpgProgramItemView? = null
        var smallestTimeDiff = Long.MAX_VALUE

        for (i in 0 until nextRow.childCount) {
            val child = nextRow.getChildAt(i)
            if (child is EpgProgramItemView) {
                val program = child.programData ?: continue
                val programMid = (program.startTimestamp + program.stopTimestamp) / 2
                val diff = kotlin.math.abs(programMid - targetTime)

                if (diff < smallestTimeDiff) {
                    smallestTimeDiff = diff
                    bestMatchView = child
                }
            }
        }

        return bestMatchView
    }
}