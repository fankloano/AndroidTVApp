package com.example.mj_player_tv.network.model.plex.library

data class MediaContainer(
    val Directory: List<Directory>,
    val allowSync: Boolean,
    val size: Int,
    val title1: String
)