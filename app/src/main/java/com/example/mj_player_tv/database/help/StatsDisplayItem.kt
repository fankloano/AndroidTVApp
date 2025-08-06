package com.example.mj_player_tv.database.help

import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.database.entity.TvChannelOB

sealed class StatsDisplayItem {
    data class MovieItem(val movie: MovieOB) : StatsDisplayItem()
    data class SeriesItem(val series: SeriesOB) : StatsDisplayItem()
    data class TvChannelItem(val tvchannel: TvChannelOB) : StatsDisplayItem()
}
