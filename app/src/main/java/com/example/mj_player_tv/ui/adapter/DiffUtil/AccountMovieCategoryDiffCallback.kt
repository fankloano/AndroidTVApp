package com.example.mj_player_tv.ui.adapter.DiffUtil

import androidx.recyclerview.widget.DiffUtil
import com.example.mj_player_tv.database.help.AccountMovieCategory
import com.example.mj_player_tv.database.help.AccountTvCategory

class AccountMovieCategoryDiffCallback : DiffUtil.ItemCallback<AccountMovieCategory>() {
    override fun areItemsTheSame(oldItem: AccountMovieCategory, newItem: AccountMovieCategory): Boolean {
        return when {
            oldItem is AccountMovieCategory.Account && newItem is AccountMovieCategory.Account -> oldItem.id == newItem.id
            oldItem is AccountMovieCategory.MovieCategory && newItem is AccountMovieCategory.MovieCategory -> oldItem.id == newItem.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: AccountMovieCategory, newItem: AccountMovieCategory): Boolean {
        return oldItem == newItem
    }
}
