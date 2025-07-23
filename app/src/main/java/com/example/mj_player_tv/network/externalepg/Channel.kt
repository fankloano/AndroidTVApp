package com.example.mj_player_tv.network.externalepg

data class Channel(
    var id: String,
    var displayName: MutableList<String>,
    var icon: MutableList<String>?,
    var url: String?,
    var name: String?
)
