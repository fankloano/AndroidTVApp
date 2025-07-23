package com.example.mj_player_tv.network.model.stalker.seriesdetails

data class SeriesJs(
    val cur_page: Int,
    val data: List<SeriesData>,
    val max_page_items: Int,
    val selected_item: Int,
    val total_items: Int
)
