package com.example.mj_player_tv.database.help

import com.example.mj_player_tv.repository.EpgUpdateProcessState
import com.example.mj_player_tv.repository.PlaylistUpdateProcessState

data class EpgUpdate(
    val epgName: String,
    var epgStatus: EpgUpdateProcessState
)
