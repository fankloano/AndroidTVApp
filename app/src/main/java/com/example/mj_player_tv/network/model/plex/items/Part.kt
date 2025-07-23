package com.example.mj_player_tv.network.model.plex.items

data class Part(
    val audioProfile: String? = "",
    val container: String? = "",
    val duration: Int,
    val file: String? = "",
    val id: Int,
    val key: String? = "",
    val size: Long? = 0L,
    val videoProfile: String? = ""
)