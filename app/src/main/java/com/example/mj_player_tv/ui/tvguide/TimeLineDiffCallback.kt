package com.example.mj_player_tv.ui.tvguide

import androidx.recyclerview.widget.DiffUtil
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.database.help.TvChannelWithEpg

class TimeLineDiffCallback : DiffUtil.ItemCallback<TimeLineData>() {
    override fun areItemsTheSame(oldItem: TimeLineData, newItem: TimeLineData): Boolean {
        return oldItem.timeId == newItem.timeId
    }

    override fun areContentsTheSame(oldItem: TimeLineData, newItem: TimeLineData): Boolean {
        return oldItem == newItem
    }
}