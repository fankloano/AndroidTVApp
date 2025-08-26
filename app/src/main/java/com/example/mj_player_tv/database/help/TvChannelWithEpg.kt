package com.example.mj_player_tv.database.help

import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB

data class TvChannelWithEpg (
    val id: Long,
    val tvChannelPosition: ChannelPositions,
    val epgList: List<EpgDataOB> = emptyList()
)