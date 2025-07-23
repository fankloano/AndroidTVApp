package com.example.mj_player_tv.network.model.stalker.movieurl

data class MovieUrlJs(
    val cmd: String,
    val error: String,
    val id: String,
    val load: String,
    val subtitles: List<Any>
)
