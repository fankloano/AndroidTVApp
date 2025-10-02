package com.example.mj_player_tv.ui.tvguidetest

import com.volkov.epgrecycler.dpToPx
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Hours
import org.joda.time.Minutes
import org.joda.time.Seconds

object ProgramGuideUtils {

    private const val MINUTE_TO_PIXEL = 5

    val minuteToPixel: Int
        get() = MINUTE_TO_PIXEL.dpToPx


    fun getCellWidth(from: DateTime, to: DateTime): Int =
        Minutes.minutesBetween(from, to).minutes * minuteToPixel
}