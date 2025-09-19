package com.example.mj_player_tv.ui.tvguide

import androidx.recyclerview.widget.DiffUtil
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TvChannelWithEpg

class EpgDataOBDiffCallback : DiffUtil.ItemCallback<EpgDataOB>() {
    override fun areItemsTheSame(oldItem: EpgDataOB, newItem: EpgDataOB): Boolean {
        return oldItem.idByAccountData == newItem.idByAccountData
    }

    override fun areContentsTheSame(oldItem: EpgDataOB, newItem: EpgDataOB): Boolean {
        return oldItem == newItem
    }
}