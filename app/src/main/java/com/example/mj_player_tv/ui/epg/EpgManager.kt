package com.example.mj_player_tv.ui.epg


import android.util.Log
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import java.util.concurrent.TimeUnit

class EpgManager {
    // Der Start der aktuell sichtbaren Zeitspanne in Millisekunden UTC

    val listeners = mutableListOf<Listener>()

    private var channelsWithEpg = mutableListOf<TvChannelWithEpg>()

    private var timeLine: EpgTimeLineRow? = null

    private var startUtcMillis: Long = 0
    private var endUtcMillis: Long = 0
    private var fromUtcMillis: Long = 0
    private var toUtcMillis: Long = 0


    internal fun updateInitialTimeRange(startUtcMillis: Long, endUtcMillis: Long) {
        this.startUtcMillis = startUtcMillis
        if (endUtcMillis > this.endUtcMillis) {
            this.endUtcMillis = endUtcMillis + 12 * 60 * 60 * 1000L
        }
        setTimeRange(startUtcMillis, endUtcMillis)
        Log.d("EPGMANAGER ZEITEN", "BEIM FRAGMENT ÖFFNEN -> START: $startUtcMillis END: $endUtcMillis")
    }

    internal fun shiftTime(timeMillisToScroll: Long): Boolean {
        var newFromUtcMillis = this.fromUtcMillis + timeMillisToScroll
        var newToUtcMillis = this.toUtcMillis + timeMillisToScroll

        Log.d("EPGMANAGER ZEITEN", "SHIFT TIME -> START: $newFromUtcMillis END: $newToUtcMillis")

        var didOverOrUnderScroll = false
        if (newFromUtcMillis < startUtcMillis) {
            val difference = newToUtcMillis - newFromUtcMillis
            newFromUtcMillis = startUtcMillis
            newToUtcMillis = startUtcMillis + difference
            didOverOrUnderScroll = true
        }
        if (newToUtcMillis > endUtcMillis) {
            val difference = newToUtcMillis - newFromUtcMillis
            newToUtcMillis = endUtcMillis
            newFromUtcMillis = endUtcMillis - difference
            didOverOrUnderScroll = true
        }
        Log.d("EPGMANAGER ZEITEN", "SHIFT TIME 2 -> START: $newFromUtcMillis END: $newToUtcMillis")

        setTimeRange(newFromUtcMillis, newToUtcMillis)
        return !didOverOrUnderScroll
    }


    fun getVisibleTimeStart(): Long = fromUtcMillis
    fun getVisibleTimeEnd(): Long = toUtcMillis

    internal fun getShiftedTime(): Long {
        return fromUtcMillis - startUtcMillis
    }

    private fun notifyTimeChanged() {
        listeners.forEach { it.onTimeRangeUpdated() }
    }

    fun setData(tvchannelsAndEpg: List<TvChannelWithEpg>, timeLineRow: EpgTimeLineRow) {
        channelsWithEpg = tvchannelsAndEpg.toMutableList()
        timeLine = timeLineRow
    }

    fun getProgramsForChannel(id: Long) : List<EpgDataOB> {
        val startTime = getVisibleTimeStart()
        val endTime = getVisibleTimeEnd()
        return channelsWithEpg.find { it.id == id }?.epgList?.filter {
            it.stopTimestamp * 1000 > startTime && it.startTimestamp < endTime
        } ?: emptyList()
    }

    fun getTimeLineRowScrollOffset(): Int {
        return timeLine?.currentScrollOffset ?: 0
    }

    private fun setTimeRange(fromUtcMillis: Long, toUtcMillis: Long) {
        Log.d("EPGMANAGER ZEITEN", "SET TIME RANGE ALT -> START: ${this.fromUtcMillis} END: ${this.toUtcMillis}")

        Log.d("EPGMANAGER ZEITEN", "SET TIME RANGE NEU -> START: $fromUtcMillis END: $toUtcMillis")

        if (this.fromUtcMillis != fromUtcMillis || this.toUtcMillis != toUtcMillis) {
            this.fromUtcMillis = fromUtcMillis
            this.toUtcMillis = toUtcMillis
            notifyTimeRangeUpdated()
        } else {
            println("HALLO")
        }
    }

    private fun notifyTimeRangeUpdated() {
        for (listener in listeners) {
            listener.onTimeRangeUpdated()
        }
    }

    interface Listener {
        fun onTimeRangeUpdated()
        fun onSchedulesUpdated()
    }
}