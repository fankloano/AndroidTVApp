package com.example.mj_player_tv.network.model.stalker.epgforday

data class SimpleTableData(
    val actor: String,
    val category: String,
    val ch_id: String,
    val descr: String,
    val director: String,
    val duration: Int,
    val id: String,
    val mark_archive: Int,
    val mark_memo: Int,
    val mark_rec: Int,
    val name: String,
    val `open`: Int,
    val real_id: String,
    val start_timestamp: Int,
    val stop_timestamp: Int,
    val t_time: String,
    val t_time_to: String,
    val time: String,
    val time_to: String
)