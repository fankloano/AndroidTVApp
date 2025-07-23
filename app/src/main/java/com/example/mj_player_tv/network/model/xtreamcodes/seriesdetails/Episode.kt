package com.example.mj_player_tv.network.model.xtreamcodes.seriesdetails

data class Episode(
    val id: String = "",
    val episode_num: Int? = 0,
    val title: String? = "",
    val container_extension: String? = "",
    val info: EpisodeInfo,
    val season: Int? = 0,
    val direct_source: String? = null
)
