package com.example.mj_player_tv.network.model.stalker.shortepg

data class ShortEpgJ(
    val actor: String? = "",
    val category: String? = "",
    val ch_id: String? = "",
    val descr: String? = "",
    val director: String? = "",
    val duration: Int? = 0,
    val id: Int,
    val mark_archive: Int? = 0,
    val mark_memo: Int? = 0,
    val name: String? = "",
    val real_id: String? = "",
    val start_timestamp: Int? = 0,
    val stop_timestamp: Int? = 0,
    val t_time: String? = "",
    val t_time_to: String? = "",
    val time: String? = "",
    val time_to: String? = ""
)
