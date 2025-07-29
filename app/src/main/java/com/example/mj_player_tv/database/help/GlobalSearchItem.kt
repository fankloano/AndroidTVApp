package com.example.mj_player_tv.database.help

import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.SeriesOB

sealed class GlobalSearchItem {
    data class TvChannels(
        val account: Accounts,
        val channels: List<ChannelPositions>
    ) : GlobalSearchItem()

    data class Movies(
        val account: Accounts,
        val movies: List<MovieOB>
    ) : GlobalSearchItem()

    data class Series(
        val account: Accounts,
        val series: List<SeriesOB>
    ) : GlobalSearchItem()

    data class Programs(
        val account: Accounts,
        val programs: List<Pair<ChannelPositions, List<EpgDataOB>>>
    ) : GlobalSearchItem()
}
