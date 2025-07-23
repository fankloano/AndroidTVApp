package com.example.mj_player_tv.network.model.plex.items

data class MediaContainer(
    val Metadata: List<Metadata>?,
    val allowSync: Boolean,
    val librarySectionID: Int,
    val librarySectionTitle: String,
    val offset: Int,
    val size: Int,
    val title1: String,
    val totalSize: Int,
    val viewGroup: String
)