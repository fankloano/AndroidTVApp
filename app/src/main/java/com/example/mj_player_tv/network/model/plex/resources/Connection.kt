package com.example.mj_player_tv.network.model.plex.resources

data class Connection(
    val IPv6: Boolean,
    val address: String,
    val local: Boolean,
    val port: Int,
    val protocol: String,
    val relay: Boolean,
    val uri: String
)