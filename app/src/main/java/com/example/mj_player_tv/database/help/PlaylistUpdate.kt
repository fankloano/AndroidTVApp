package com.example.mj_player_tv.database.help

import com.example.mj_player_tv.repository.PlaylistUpdateProcessState

data class PlaylistUpdate(
    val playlistName: String,
    val playlistId: Long,
    var playlistStatus: PlaylistUpdateProcessState
)
