package com.example.mj_player_tv.network.model.plex

data class Service(
    val endpoint: String,
    val identifier: String,
    val secret: String,
    val status: String,
    val token: String
)