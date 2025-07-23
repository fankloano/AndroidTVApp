package com.example.mj_player_tv.network.model.xtreamcodes

data class ServerInfo(
    val https_port: String = "",
    val port: String = "",
    val revision: Int = 0,
    val rtmp_port: String = "",
    val server_protocol: String = "",
    val time_now: String = "",
    val timestamp_now: Int = 0,
    val timezone: String = "",
    val url: String = "",
    val version: String = "",
    val xui: Boolean = false
)