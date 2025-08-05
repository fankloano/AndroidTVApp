package com.example.mj_player_tv.database.help

import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.database.entity.SeriesOB

sealed class WatchlistDisplayItem {
    data class MovieItem(val movie: MovieOB) : WatchlistDisplayItem()
    data class SeriesItem(val series: SeriesOB) : WatchlistDisplayItem()
    data class ProgramItem(val programs: Programme) : WatchlistDisplayItem()
}
