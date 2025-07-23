package com.example.mj_player_tv.network.externalepg

data class Programme(
    var start: Long,
    var stop: Long,
    var channel: String,
    var title: String,
    var subTitle: String?,
    var description: String?,
    var categories: MutableList<String>?,
    var actors: MutableList<String>?,
    var directors: MutableList<String>?,
    var countries: MutableList<String>?,
    var date: String?,
    var episodeNumber: String?,
    var ratingValue: String?,
    var showIcon: String?
)
