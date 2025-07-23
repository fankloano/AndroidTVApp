package com.example.mj_player_tv.database.help

import androidx.media3.common.Tracks

data class TrackInfo(
    val trackName: String,
    val trackId: Int,
    var isSelected: Boolean = false,
    val isSupported: Boolean = false,
    val group: Tracks.Group? = null,
    val isDefault: Boolean = false
)
