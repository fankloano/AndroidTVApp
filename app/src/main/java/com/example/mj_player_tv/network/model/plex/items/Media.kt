package com.example.mj_player_tv.network.model.plex.items

data class Media(
    val Part: List<Part>,
    val aspectRatio: Double? = 0.0,
    val audioChannels: Int? = 0,
    val audioCodec: String? = "",
    val audioProfile: String? = "",
    val bitrate: Int? = 0,
    val container: String? = "",
    val duration: Int? = 0,
    val height: Int? = 0,
    val id: Int,
    val optimizedForStreaming: Int,
    val videoCodec: String? = "",
    val videoFrameRate: String? = "",
    val videoProfile: String? = "",
    val videoResolution: String? = "",
    val width: Int? = 0
)