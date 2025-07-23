package com.example.mj_player_tv.database.help

sealed class AccountTvCategory {
    data class Account(val id: Long, val name: String, val categories: List<TvCategory>) : AccountTvCategory()
    data class TvCategory(val id: Long,
                          val name: String,
                          val parentId: Long,
                          val tvCategoryId: String,
                          var isFavoriteCategory: Boolean,
                          var isAllChannelsCategory: Boolean) : AccountTvCategory()
}
