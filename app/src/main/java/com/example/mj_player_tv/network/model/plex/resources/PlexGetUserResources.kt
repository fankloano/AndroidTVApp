package com.example.mj_player_tv.network.model.plex.resources

class PlexGetUserResources(
    val accessToken: String,
    val clientIdentifier: String,
    val connections: List<Connection>,
    val name: String,
    val provides: String,
    val synced: Boolean
)