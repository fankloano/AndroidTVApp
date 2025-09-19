package com.example.mj_player_tv.ui.tvguide.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.ui.tvguide.TimeLineDiffCallback
import com.example.mj_player_tv.ui.tvguide.viewholders.TimeLineViewHolder
import com.example.mj_player_tv.utils.Common.getView

class TimeLineAdapter : ListAdapter<TimeLineData, TimeLineViewHolder>(TimeLineDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeLineViewHolder {
        return TimeLineViewHolder(getView(parent, R.layout.rv_item_tvguide_timeline))
    }

    override fun onBindViewHolder(holder: TimeLineViewHolder, position: Int) {
        holder.bind(getItem(position) as TimeLineData)
    }
}