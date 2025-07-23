package com.example.mj_player_tv.database.help


data class Season(
    val seriesSeasonIdByAccountData: String,
    val seriesIdByAccount: String,
    val seasonsId: String,
    val seasonsNumber: String,
    val playlistId: Long,
    val seasonName: String,
    val seasonsCmd: String,
    val rating_imdb: String? = "",
    var screenshot_uri: String? = "",
    var backdropPath: String = "",
    val description: String? = "",
    var tmdb_id: String? = "",
    val episodes: List<Int>,
    var isSeasonFullyWatched: Boolean = false,
    var isSeasonPartlyWatched: Boolean = false,
    var seasonPercentagePlayed: Double = 0.0
)
