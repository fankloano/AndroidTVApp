package com.example.mj_player_tv.network.model.stalker.alltvchannels

import com.example.mj_player_tv.network.model.stalker.sortedtvchannels.Cmd

data class AllTvChannelsData(
    val archive: Int?,
    val cmd: String?,
    val cur_playing: String?,
    val enable_tv_archive: Int?,
    val id: String?,
    val logo: String?,
    val name: String?,
    val number: String?,
    val tv_archive_duration: Int?,
    val tv_genre_id: String?,
    val xmltv_id: String?
)
