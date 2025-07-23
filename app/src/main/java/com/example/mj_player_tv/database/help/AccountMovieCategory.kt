package com.example.mj_player_tv.database.help

sealed class AccountMovieCategory {
    data class Account(val id: Long, val name: String, val categories: List<MovieCategory>) : AccountMovieCategory()
    data class MovieCategory(val id: Long,
                          val name: String,
                          val parentId: Long,
                          val movieCategoryId: String,
                          var isFavoriteCategory: Boolean) : AccountMovieCategory()
}
