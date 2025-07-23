package com.example.mj_player_tv.network.model.xtreamcodes.epgbychannel

data class EpgListings(
    val channel_id: String,
    val description: String,
    val end: String,
    val epg_id: String,
    val has_archive: Int,
    val id: String,
    val lang: String,
    val now_playing: Int,
    val start: String,
    val start_timestamp: String,
    val stop_timestamp: String,
    val title: String
)