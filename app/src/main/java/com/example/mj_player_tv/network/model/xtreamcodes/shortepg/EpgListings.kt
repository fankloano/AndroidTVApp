package com.example.mj_player_tv.network.model.xtreamcodes.shortepg

data class EpgListings(
    val channel_id: String,
    val description: String,
    val end: String,
    val epg_id: String,
    val id: String,
    val lang: String,
    val start: String,
    val start_timestamp: String,
    val stop_timestamp: String,
    val stream_id: String,
    val title: String
)