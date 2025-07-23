package com.example.mj_player_tv.network.model.xtreamcodes.seriesdetails

data class EpisodeInfo(
    val tmdb_id: Int? = null,
    val releasedate: String? = "",
    val plot: String? = "",
    val duration_secs: Int? = 0,
    val movie_image: String? = "",
    val video: VideoDetails,
    val audio: AudioDetails,
    val bitrate: Int? = null,
    val rating: Double? = null,
    val season: String? = ""
)