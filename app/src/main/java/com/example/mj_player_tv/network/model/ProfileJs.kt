package com.example.mj_player_tv.network.model

data class ProfileJs(
    val default_locale: String,
    val default_timezone: String,
    val ip: String,
    val parent_password: String,
    val pass: String,
    val password: String,
    val stb_lang: String,
    val stb_type: String,
    val version: String,
    val watchdog_timeout: Int,
)
