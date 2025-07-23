package com.example.mj_player_tv.network.model.stalker.allepg

data class AllEpgProgram(
    val id: String,
    val ch_id: String,
    val time: String,
    val time_to: String,
    val duration: Int,
    val name: String,
    val descr: String,
    val real_id: String,
    val category: String,
    val director: String,
    val actor: String,
    val start_timestamp: Long,
    val stop_timestamp: Long,
    val t_time: String,
    val t_time_to: String,
    val display_duration: Int,
    val larr: Int,
    val rarr: Int,
    val mark_rec: Int,
    val mark_memo: Int,
    val mark_archive: Int,
    val on_date: String
)