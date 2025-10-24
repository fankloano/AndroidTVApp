package com.example.mj_player_tv.ui.epg


import android.util.Log
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.google.type.DateTime
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
        this.endUtcMillis = endUtcMillis + TimeUnit.HOURS.toMillis(24)
        setTimeRange(startUtcMillis, endUtcMillis)
    }

    internal fun shiftTime(timeMillisToScroll: Long): Boolean {
        var newFromUtcMillis = this.fromUtcMillis + timeMillisToScroll
        var newToUtcMillis = this.toUtcMillis + timeMillisToScroll

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

        setTimeRange(newFromUtcMillis, newToUtcMillis)
        return !didOverOrUnderScroll
    }


    fun getTimeLineStart(): Long = startUtcMillis
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
        val startTime = startUtcMillis
        val endTime = endUtcMillis
        return channelsWithEpg.find { it.id == id }?.epgList?.filter {
            it.stopTimestamp * 1000 > startTime && it.startTimestamp < endTime
        } ?: emptyList()
    }

    /** Returns the program index of the program at `time` or -1 if not found.  */
    internal fun getProgramIndexAtTime(channelId: Long, time: Long): Int {
        val channel = channelsWithEpg.firstOrNull { it.id == channelId }
            return channel?.epgList?.indexOfFirst { epgData ->
            epgData.startTimestamp * 1000 <= time && time < epgData.stopTimestamp * 1000
        } ?: -1
    }

    internal fun getScheduleForChannelIdAndIndex(
        channelId: Long,
        index: Int
    ): EpgDataOB? {
        val channel = channelsWithEpg.firstOrNull { it.id == channelId }
        return channel?.epgList[index]
    }

    fun getTimeLineRowScrollOffset(): Int {
        return timeLine?.currentScrollOffset ?: 0
    }

    private fun setTimeRange(fromUtcMillis: Long, toUtcMillis: Long) {

        if (this.fromUtcMillis != fromUtcMillis || this.toUtcMillis != toUtcMillis) {
            val fromtime = org.joda.time.DateTime(fromUtcMillis).toString("HH:mm")
            val toTime = org.joda.time.DateTime(toUtcMillis).toString("HH:mm")
            Log.d("SCROLLE HORIZONTAL","settimerange VON $fromtime BIS $toTime")

            this.fromUtcMillis = fromUtcMillis
            this.toUtcMillis = toUtcMillis
            notifyTimeRangeUpdated()
        } else {
            println("HALLO")
        }
    }

    private fun notifyTimeRangeUpdated() {
        Log.d("SCROLLE HORIZONTAL","settimerange NEU: ${this.fromUtcMillis} BIS ${this.toUtcMillis}")

        for (listener in listeners) {
            listener.onTimeRangeUpdated()
        }
    }

    interface Listener {
        fun onTimeRangeUpdated()
        fun onSchedulesUpdated()
    }
}