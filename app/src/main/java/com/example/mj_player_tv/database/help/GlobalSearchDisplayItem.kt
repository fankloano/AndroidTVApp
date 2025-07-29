package com.example.mj_player_tv.database.help

import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.SeriesOB

sealed class GlobalSearchDisplayItem {
    data class ChannelItem(val channel: ChannelPositions) : GlobalSearchDisplayItem()
    data class MovieItem(val movie: MovieOB) : GlobalSearchDisplayItem()
    data class SeriesItem(val series: SeriesOB) : GlobalSearchDisplayItem()
    data class ProgramItem(val programs: List<Pair<ChannelPositions, List<EpgDataOB>>>) : GlobalSearchDisplayItem()
}
