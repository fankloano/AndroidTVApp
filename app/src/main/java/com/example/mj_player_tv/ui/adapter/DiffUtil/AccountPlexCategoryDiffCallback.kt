package com.example.mj_player_tv.ui.adapter.DiffUtil

import androidx.recyclerview.widget.DiffUtil
import com.example.mj_player_tv.database.help.AccountPlexCategory
import com.example.mj_player_tv.database.help.AccountTvCategory

class AccountPlexCategoryDiffCallback : DiffUtil.ItemCallback<AccountPlexCategory>() {
    override fun areItemsTheSame(oldItem: AccountPlexCategory, newItem: AccountPlexCategory): Boolean {
        return when {
            oldItem is AccountPlexCategory.Header && newItem is AccountPlexCategory.Header -> oldItem.name == newItem.name
            oldItem is AccountPlexCategory.PlexCategory && newItem is AccountPlexCategory.PlexCategory -> oldItem.id == newItem.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: AccountPlexCategory, newItem: AccountPlexCategory): Boolean {
        return oldItem == newItem
    }
}
