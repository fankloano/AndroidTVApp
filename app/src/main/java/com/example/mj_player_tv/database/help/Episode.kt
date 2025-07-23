package com.example.mj_player_tv.database.help

data class Episode(
    val seriesSeasonEpisodeIdByAccountData: String,
    val seriesSeasonIdByAccount: String,
    val seriesIdByAccount: String,
    val episodeNumber: Int,
    val seasonName: String,
    val seasonNumber: String,
    val playlistId: Long,
    val seriesName: String,
    var episodeName: String = "",
    var episodeImage: String? = "",
    var episodeTime: String= "",
    var episodeDescription: String = "",
    val episodeCmd: String,
    var currentPosition: Long,
    var isEpisodeFullyWatched: Boolean,
    var isEpisodePartlyWatched: Boolean,
    var episodePercentagePlayed: Double,
    var xtreamExtension: String = ""
)

