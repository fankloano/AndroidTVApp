package com.example.mj_player_tv.network.model.tmdb.imdb_id

data class TMDB_imdb_id(
    val movie_results: List<MovieResult>,
    val person_results: List<Any>,
    val tv_episode_results: List<Any>,
    val tv_results: List<Any>,
    val tv_season_results: List<Any>
)