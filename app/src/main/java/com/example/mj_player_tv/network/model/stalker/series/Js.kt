package com.example.mj_player_tv.network.model.stalker.series

data class Js(
    val cur_page: Int,
    val `data`: List<SeriesData>,
    val max_page_items: Int,
    val selected_item: Int,
    val total_items: Int
)