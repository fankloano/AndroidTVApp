package com.example.mj_player_tv.network.model.stalker.sortedtvchannels


data class TvChannelsJs(
    val cur_page: Int,
    val `data`: List<TvChannelsData>,
    val max_page_items: Int,
    val selected_item: Int,
    val total_items: Int
)
