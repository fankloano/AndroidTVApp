package com.example.mj_player_tv.ui.tvguidetest

import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TvChannelWithEpg

class ProgramGuideManager {

    private val channels = mutableListOf<ChannelPositions>()
    private val channelSchedules = mutableMapOf<String, List<EpgDataOB>>()

    // Anzahl der Kanäle
    val channelCount: Int
        get() = channels.size

    /** Kanal anhand Index */

    /** Alle Programme eines Kanals */
    fun getSchedulesForChannel(chEpgId: String?): List<EpgDataOB> {
        return channelSchedules[chEpgId] ?: emptyList()
    }

    /** Daten setzen / aktualisieren */
    fun setData(
        newChannels: List<ChannelPositions>,
        newSchedules: Map<String, List<EpgDataOB>>
    ) {
        channels.clear()
        channels.addAll(newChannels)

        channelSchedules.clear()
        channelSchedules.putAll(newSchedules)
    }

    /** Optional: Hole das aktuelle Programm eines Kanals (z.B. für Fokus auf "jetzt") */
    fun getCurrentProgram(channelId: String, currentTimeSeconds: Long = System.currentTimeMillis()): EpgDataOB? {
        val schedules = getSchedulesForChannel(channelId)
        return schedules.firstOrNull { it.startTimestamp <= currentTimeSeconds && currentTimeSeconds < it.stopTimestamp }
    }
}
