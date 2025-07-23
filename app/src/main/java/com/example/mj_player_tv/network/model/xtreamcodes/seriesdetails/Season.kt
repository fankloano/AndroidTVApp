package com.example.mj_player_tv.network.model.xtreamcodes.seriesdetails

data class Season(
    val episode_count: Int = 1,
    val id: Int,
    val name: String = "",
    val season_number: Int = 0,
    val vote_average: Double = 0.0,
    val cover: String = "",
    val cover_big: String = "",
    val tmdb_id: String = "",
    val plot: String = ""
)