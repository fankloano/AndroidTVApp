package com.example.mj_player_tv.network.model.xtreamcodes.seriesdetails

data class XtreamSeriesDetails(
    val seasons: List<Season>,
    val info: Info,
    val episodes: Map<String, List<Episode>>
)