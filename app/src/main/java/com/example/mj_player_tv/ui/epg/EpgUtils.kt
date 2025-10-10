package com.example.mj_player_tv.ui.epg

import com.volkov.epgrecycler.dpToPx
import org.joda.time.DateTime
import org.joda.time.Minutes
import java.util.Date

object EpgUtils {

    private const val MINUTE_TO_PIXEL = 5

    val minuteToPixel: Int
        get() = MINUTE_TO_PIXEL.dpToPx

    fun getEpgWidth(from: DateTime, to: DateTime): Int =
        Minutes.minutesBetween(from, to).minutes * minuteToPixel

    var timeLineStartTime = DateTime()
    var timeLineEndTime = DateTime()
}