package com.example.mj_player_tv.network.model.xtreamcodes.allseries

data class XtreamAllSeries(
    val backdrop_path: List<String>? = emptyList(),
    val cast: String? = "",
    val category_id: String? = "",
    val cover: String? = "",
    val director: String? = "",
    val episode_run_time: String? = "",
    val genre: String? = "",
    val name: String? = "",
    val lastModified: String? = null,
    val num: Int? = 0,
    val plot: String? = "",
    val rating: String? = "",
    val releaseDate: String? ="",
    val series_id: Int? = 0,
    val youtube_trailer: String? = ""
)