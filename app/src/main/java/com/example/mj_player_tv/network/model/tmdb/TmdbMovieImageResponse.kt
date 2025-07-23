package com.example.mj_player_tv.network.model.tmdb

data class TmdbMovieImageResponse(
    val backdrops: List<Backdrop>,
    val id: Int,
    val posters: List<Poster>
)