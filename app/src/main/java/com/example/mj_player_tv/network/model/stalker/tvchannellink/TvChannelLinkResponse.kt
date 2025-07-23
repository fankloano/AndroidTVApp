package com.example.mj_player_tv.network.model.stalker.tvchannellink

data class TvChannelLinkResponse(
    val error: String,
    val js: TvUrlJs,
    val link_id: Int,
    val load: Int,
    val streamer_id: Int
)
