package com.example.mj_player_tv.ui.adapter.DiffUtil

import androidx.recyclerview.widget.DiffUtil
import com.example.mj_player_tv.database.help.AccountSeriesCategory
import com.example.mj_player_tv.database.help.AccountTvCategory

class AccountSeriesCategoryDiffCallback : DiffUtil.ItemCallback<AccountSeriesCategory>() {
    override fun areItemsTheSame(oldItem: AccountSeriesCategory, newItem: AccountSeriesCategory): Boolean {
        return when {
            oldItem is AccountSeriesCategory.Account && newItem is AccountSeriesCategory.Account -> oldItem.id == newItem.id
            oldItem is AccountSeriesCategory.SeriesCategory && newItem is AccountSeriesCategory.SeriesCategory -> oldItem.id == newItem.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: AccountSeriesCategory, newItem: AccountSeriesCategory): Boolean {
        return oldItem == newItem
    }
}
