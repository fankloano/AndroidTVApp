package com.example.mj_player_tv.network.model.plex.library

data class Directory(
    val Location: List<Location>,
    val agent: String,
    val allowSync: Boolean,
    val art: String,
    val composite: String,
    val content: Boolean,
    val contentChangedAt: Long,
    val createdAt: Int,
    val directory: Boolean,
    val filters: Boolean,
    val hidden: Int,
    val key: String,
    val language: String,
    val refreshing: Boolean,
    val scannedAt: Int,
    val scanner: String,
    val thumb: String,
    val title: String,
    val type: String,
    val updatedAt: Int,
    val uuid: String
)