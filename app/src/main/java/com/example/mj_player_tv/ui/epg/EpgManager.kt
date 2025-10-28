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
        this.endUtcMillis = startUtcMillis + TimeUnit.HOURS.toMillis(24)
        val start = org.joda.time.DateTime(this.startUtcMillis).toString("HH:mm")
        val end = org.joda.time.DateTime(this.endUtcMillis).toString("HH:mm")
        Log.d("EPG_SCROLL_DEBUG", "START TIMELINEZEITEN: $start BIS $end")
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

    fun getTimeLineEnd(): Long = endUtcMillis
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
        channelsWithEpg.filter { it.tvChannelPosition.tvchannel.target.showingName.contains("ARD-ALPHA") }.forEach { channel ->
            val channelname = channel.tvChannelPosition.tvchannel.target.showingName
            channel.epgList.forEachIndexed { index, oB ->
                val start = org.joda.time.DateTime(oB.startTimestamp * 1000).toString("HH:mm")
                val end = org.joda.time.DateTime(oB.stopTimestamp * 1000).toString("HH:mm")
                Log.d("LOG THE EPGDATA", "$channelname = $index -> ${oB.name} START: $start END: $end")
            }
        }
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

    fun getProgramAtTime(channelId: Long, time: Long): Pair<EpgDataOB?, Int> {
        val channel = channelsWithEpg.firstOrNull { it.id == channelId }
        val epgData =  channel?.epgList?.firstOrNull { epgDataOB ->
            epgDataOB.startTimestamp * 1000 <= time && time < epgDataOB.stopTimestamp * 1000
        }
        val index = channel?.epgList?.indexOf(epgData) ?: 0
        return Pair(epgData, index)
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

    fun getFirstProgramForChannel(channelId: Long): EpgDataOB? {
        val channel = channelsWithEpg.firstOrNull { it.id == channelId }
        return channel?.epgList[0]
    }

    private fun setTimeRange(fromUtcMillis: Long, toUtcMillis: Long) {

        if (this.fromUtcMillis != fromUtcMillis || this.toUtcMillis != toUtcMillis) {

            this.fromUtcMillis = fromUtcMillis
            this.toUtcMillis = toUtcMillis
            notifyTimeRangeUpdated()
        } else {
            println("HALLO")
        }
    }

    private fun notifyTimeRangeUpdated() {
        val fromtime = org.joda.time.DateTime(fromUtcMillis).toString("HH:mm")
        val toTime = org.joda.time.DateTime(toUtcMillis).toString("HH:mm")
        Log.d("EPG_SCROLL_DEBUG","SET NEW TIME RANGE: VON $fromtime BIS $toTime")

        for (listener in listeners) {
            listener.onTimeRangeUpdated()
        }
    }

    interface Listener {
        fun onTimeRangeUpdated()
        fun onSchedulesUpdated()
    }
}