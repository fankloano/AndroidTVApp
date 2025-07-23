package com.example.mj_player_tv.database.help

sealed class AccountPlexCategory {
    data class Header(val name: String) : AccountPlexCategory()
    data class PlexCategory(val id: Long,
                            val name: String,
                            val parentId: Long,
                            val plexCategoryId: String,
                            var isMovie: Boolean,
                            var isAudio: Boolean,
                            var isFavoriteCategory: Boolean) : AccountPlexCategory()
}
