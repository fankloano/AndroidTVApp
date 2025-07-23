package com.example.mj_player_tv.network.model.stalker.sortedtvchannels

data class Cmd(
    val ch_id: String,
    val changed: String,
    val enable_balancer_monitoring: String,
    val enable_monitoring: String,
    val flussonic_tmp_link: String,
    val id: String,
    val nginx_secure_link: String,
    val priority: String,
    val status: String,
    val url: String,
    val use_http_tmp_link: String,
    val use_load_balancing: String,
    val user_agent_filter: String,
    val wowza_tmp_link: String
)
