package com.example.mj_player_tv.ui.tvguide

import com.volkov.EPGConfig
import com.volkov.epgrecycler.dpToPx
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Hours
import org.joda.time.Seconds

object EPGUtils {

    const val SHOW_TIME_PATTERN = "HH:mm"
    private const val PIXELS_PER_MINUTE = 5
    private const val PIXELS_PER_SECOND = PIXELS_PER_MINUTE / 60f
    private const val TIME_LABEL_WIDTH = 40

    var dayShift = 0
    var startHour = 0
    var endHour = 0
    var timeZone = DateTimeZone.getDefault()

    var currentEpgTime: DateTime = startTime

    val minuteToPixel: Int
        get() = PIXELS_PER_MINUTE.dpToPx

    val secondToPixel: Float
        get() = PIXELS_PER_SECOND.dpToPx.toFloat()

    val timeLabelWidth: Int
        get() = TIME_LABEL_WIDTH.dpToPx

    val maxHour: Int
        get() = Hours.hoursBetween(startTime, endTime).hours

    // In deiner EPGUtils-Klasse, getCellWidth
    fun getCellWidth(start: DateTime, end: DateTime): Int {
        val durationInMinutes = (end.millis - start.millis) / 1000 / 60
        return (durationInMinutes * 5f).toInt()
    }

    // Diese Methode wird überflüssig, da getCellWidth jetzt in Sekunden arbeitet
    // fun getCellWidthSeconds(from: DateTime, to: DateTime): Int =
    //     Seconds.secondsBetween(from, to).seconds / 60 * minuteToPixel

    fun getDayLength(): Int {
        return Seconds.secondsBetween(startTime, endTime).seconds
    }

    val startTime: DateTime
        get() = DateTime()
            .plusDays(dayShift)
            .withHourOfDay(startHour)
            .withMinuteOfHour(0)
            .withSecondOfMinute(0)
            .withMillisOfSecond(0)
            .withZoneRetainFields(timeZone)

    val endTime: DateTime
        get() = startTime.plusDays(1).withHourOfDay(endHour)

    val startEpgTime: DateTime
        get() {
            // Holen Sie sich die aktuelle Zeit unter Berücksichtigung von dayShift und timeZone.
            val now = DateTime()
                .plusDays(dayShift)
                .withZoneRetainFields(timeZone)

            // Berechne die letzte volle Stunde basierend auf der aktuellen Zeit.
            val lastFullHour = now
                .withMinuteOfHour(0)
                .withSecondOfMinute(0)
                .withMillisOfSecond(0)

            // Ziehe 15 Minuten ab, um die neue startEpgTime zu erhalten.
            return lastFullHour.minusMinutes(15)
        }
}