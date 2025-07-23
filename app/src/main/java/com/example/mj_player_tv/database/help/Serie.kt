package com.example.mj_player_tv.database.help

import com.example.mj_player_tv.database.entity.SeasonsOB

data class Serie(
    val idByAccountData: String = "",
    val seriesId: String = "",
    val seriesCategory: String = "",
    val accountName: String = "",
    val accountId: Long? = null,
    val seriesName: String = "",
    val seriesCmd: String = "",
    var seriesTime: Int? = 0,
    val seriesYear: String = "",
    val rate: String = "",
    val rating_imdb: String = "",
    val screenshot_uri: String = "",
    val genres_str: String = "",
    val actors: String ="",
    val added: String = "",
    val age: String = "",
    val description: String = "",
    val director: String = "",
    var backdropPath: String = "",
    val tmdb_id: String = "",
    val o_name: String = "",
    var currentPosition: Long? = 0L,
    var isFavorite: Boolean = false,
    var isCompletelyWatched: Boolean = false,
    var isPartlyWatched: Boolean = false,
    var seriesPercentagePlayed: Double = 0.0,
    var lastWatchedSeason: Int = 0,
    var lastWatchedEpisode: Int = 0,
    var totalSeasons: Int = 0,
    var totalEpisodes: Int = 0,
    var newSeasons: Boolean = false,
    var newEpisodes: Boolean = false
)
