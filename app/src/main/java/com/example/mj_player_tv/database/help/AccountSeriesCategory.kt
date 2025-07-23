package com.example.mj_player_tv.database.help

sealed class AccountSeriesCategory {
    data class Account(val id: Long, val name: String, val categories: List<SeriesCategory>) : AccountSeriesCategory()
    data class SeriesCategory(val id: Long,
                          val name: String,
                          val parentId: Long,
                          val seriesCategoryId: String,
                          var isFavoriteCategory: Boolean) : AccountSeriesCategory()
}
