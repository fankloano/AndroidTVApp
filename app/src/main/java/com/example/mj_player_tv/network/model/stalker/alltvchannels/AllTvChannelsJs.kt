package com.example.mj_player_tv.network.model.stalker.alltvchannels

data class AllTvChannelsJs(
    val cur_page: Int,
    val `data`: List<AllTvChannelsData>,
    val max_page_items: Int,
    val selected_item: Int,
    val total_items: Int
)
