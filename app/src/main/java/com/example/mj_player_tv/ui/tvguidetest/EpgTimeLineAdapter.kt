package com.example.mj_player_tv.ui.tvguidetest

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.databinding.RvItemTvguideTimelineBinding

class EpgTimeLineAdapter: ListAdapter<TimeLineData, EpgTimeLineAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: RvItemTvguideTimelineBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TimeLineData) {
            binding.root.layoutParams.width = item.width
            binding.tvTime.text = item.time
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TimeLineData>() {
        override fun areItemsTheSame(oldItem: TimeLineData, newItem: TimeLineData) =
            oldItem.timeId == newItem.timeId

        override fun areContentsTheSame(oldItem: TimeLineData, newItem: TimeLineData) =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemTvguideTimelineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
