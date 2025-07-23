package com.example.mj_player_tv.network.model.stalker.epgforday

data class SimpleTableEpgJs(
    val cur_page: Int,
    val `data`: List<SimpleTableData>,
    val max_page_items: Int,
    val selected_item: Int,
    val total_items: Int
)