package com.example.mj_player_tv.network.model.xtreamcodes.seriesdetails

data class Info(
    val name: String? = "",
    val cover: String? = "",
    val plot: String? = "",
    val cast: String? = "",
    val director: String? = "",
    val genre: String? = "",
    val releaseDate: String? = "",
    val last_modified: String? = "",
    val rating: String? = "",
    val backdrop_path: List<String>? = emptyList(),
    val youtube_trailer: String? = "",
    val episode_run_time: String? = "",
    val category_id: String? = ""
)