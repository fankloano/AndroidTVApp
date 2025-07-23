package com.example.mj_player_tv.network.model.stalker.movies

data class MovieJs(
    val cur_page: Int,
    val `data`: List<MovieData>,
    val max_page_items: Int,
    val selected_item: Int,
    val total_items: Int
)
