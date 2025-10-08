package com.example.mj_player_tv.ui.tvguidetest

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.databinding.RvItemTvguideEpgBinding
import org.joda.time.DateTime

class EpgDataAdapter(
    private val epgRecyclerView: EpgRecyclerview
) : ListAdapter<EpgDataOB, EpgDataAdapter.ShowViewHolder>(DiffCallback()) {


    var channelId = ""
    inner class ShowViewHolder(val binding: RvItemTvguideEpgBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShowViewHolder {
        val binding = RvItemTvguideEpgBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShowViewHolder, position: Int) {
        val show = getItem(position)
        holder.binding.tvProgram.text = show.name
        val startTimeDate = DateTime(show.startTimestamp * 1000)
        val endTimeDate = DateTime(show.stopTimestamp * 1000)
        val startTime = if (startTimeDate.isBefore(EpgUtil.epgStartTime)) EpgUtil.epgStartTime.millis else show.startTimestamp
        val endTime = if (endTimeDate.isAfter(EpgUtil.epgEndTime)) EpgUtil.epgEndTime.millis else show.stopTimestamp
        val widthPx = EpgUtil.calculateEpgWidth(startTime,endTime, 5f)
        holder.binding.root.layoutParams.width = widthPx
        Log.d("NEW EPG GUIDE", "EPG: ${show.name} = $widthPx")
        holder.binding.root.tag = ShowTag(channelId, show.idByAccountData, show.startTimestamp, show.stopTimestamp)
        // Fokus auf die Show setzen
        holder.binding.root.setOnClickListener {
            epgRecyclerView.focusShowAfterLayout(holder.binding.root)
        }

        // Tag für Fokuslogik
        holder.binding.root.tag = ShowTag(
            channelId,
            show.idByAccountData,
            show.startTimestamp,
            show.stopTimestamp
        )
    }

    class DiffCallback : DiffUtil.ItemCallback<EpgDataOB>() {
        override fun areItemsTheSame(oldItem: EpgDataOB, newItem: EpgDataOB): Boolean {
            return oldItem.idByAccountData == newItem.idByAccountData
        }

        override fun areContentsTheSame(oldItem: EpgDataOB, newItem: EpgDataOB): Boolean {
            return oldItem == newItem
        }
    }
}
