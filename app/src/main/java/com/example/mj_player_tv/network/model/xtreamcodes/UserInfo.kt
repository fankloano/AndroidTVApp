package com.example.mj_player_tv.network.model.xtreamcodes

data class UserInfo(
    val active_cons: Int = 0,
    val allowed_output_formats: List<String> = emptyList(),
    val auth: Int,
    val created_at: String = "",
    val exp_date: String? = "",
    val is_trial: String = "",
    val max_connections: String = "",
    val message: String = "",
    val password: String = "",
    val status: String = "",
    val username: String = ""
)