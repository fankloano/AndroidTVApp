package com.example.mj_player_tv.database.help

import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.database.entity.SeriesOB

sealed class WatchlistItem {
    data class Movies(
        val account: Accounts,
        val movies: List<MovieOB>
    ) : WatchlistItem()

    data class Series(
        val account: Accounts,
        val series: List<SeriesOB>
    ) : WatchlistItem()

    data class Programs(
        val account: Accounts,
        val programs: List<Programme>
    ) : WatchlistItem()
}
