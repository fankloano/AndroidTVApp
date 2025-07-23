package com.example.mj_player_tv.network.externalepg

data class TvData(
    var channels: List<Channel>? = null,
    var programmes: List<Programme>? = null
)
