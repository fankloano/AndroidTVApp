package com.example.mj_player_tv.network.model.plex

data class Profile(
    val autoSelectAudio: Boolean,
    val autoSelectSubtitle: Int,
    val defaultAudioLanguage: String,
    val defaultSubtitleAccessibility: Int,
    val defaultSubtitleForced: Int,
    val defaultSubtitleLanguage: String,
    val mediaReviewsVisibility: Int,
    val watchedIndicator: Int
)