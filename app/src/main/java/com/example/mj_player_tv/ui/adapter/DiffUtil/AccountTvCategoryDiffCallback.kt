package com.example.mj_player_tv.ui.adapter.DiffUtil

import androidx.recyclerview.widget.DiffUtil
import com.example.mj_player_tv.database.help.AccountTvCategory

class AccountTvCategoryDiffCallback : DiffUtil.ItemCallback<AccountTvCategory>() {
    override fun areItemsTheSame(oldItem: AccountTvCategory, newItem: AccountTvCategory): Boolean {
        return when {
            oldItem is AccountTvCategory.Account && newItem is AccountTvCategory.Account -> oldItem.id == newItem.id
            oldItem is AccountTvCategory.TvCategory && newItem is AccountTvCategory.TvCategory -> {
                oldItem.id == newItem.id &&
                oldItem.isFavoriteCategory == newItem.isFavoriteCategory
            }
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: AccountTvCategory, newItem: AccountTvCategory): Boolean {
        return oldItem == newItem
    }
}
