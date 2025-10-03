package com.example.mj_player_tv.ui.tvguidetest

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.databinding.RvItemTvguideTimelineBinding

class ProgramGuideTimeLineAdapter : ListAdapter<TimeLineData, ProgramGuideTimeLineViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TimeLineData>() {
            override fun areItemsTheSame(oldItem: TimeLineData, newItem: TimeLineData) =
                oldItem.timeId == newItem.timeId

            override fun areContentsTheSame(oldItem: TimeLineData, newItem: TimeLineData) =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgramGuideTimeLineViewHolder {
        val binding = RvItemTvguideTimelineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProgramGuideTimeLineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProgramGuideTimeLineViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
