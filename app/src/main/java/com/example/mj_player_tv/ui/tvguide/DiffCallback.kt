package com.example.mj_player_tv.ui.tvguide

import androidx.recyclerview.widget.DiffUtil
import com.example.mj_player_tv.database.help.TvChannelWithEpg

class DiffCallback : DiffUtil.ItemCallback<TvChannelWithEpg>() {
    override fun areItemsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg): Boolean {
        return oldItem.tvChannelPosition.catAndChannelAccount == newItem.tvChannelPosition.catAndChannelAccount
    }

    override fun areContentsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg): Boolean {
        return oldItem == newItem
    }
}