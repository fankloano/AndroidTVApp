package com.example.mj_player_tv.ui.adapter

import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.databinding.RvItemFullepgBinding
import com.example.mj_player_tv.databinding.RvItemTvguideEpgBinding
import com.example.mj_player_tv.databinding.RvItemTvguideTimelineBinding
import com.example.mj_player_tv.ui.FullEpgFragment
import com.example.mj_player_tv.ui.TvGuideFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
class TvGuideTimelineAdapter(
    private val pxPerMinute: Float
) : ListAdapter<Long, TvGuideTimelineAdapter.TimeVH>(DIFF_CALLBACK) {

    inner class TimeVH(val binding: RvItemTvguideTimelineBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(timestampSec: Long) {
            val cal = Calendar.getInstance().apply { timeInMillis = timestampSec * 1000 }
            val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())

            val day = cal.get(Calendar.DAY_OF_YEAR)
            val label = if (day != today) {
                if (day == today + 1) "Tomorrow ${formatter.format(Date(timestampSec * 1000))}"
                else formatter.format(Date(timestampSec * 1000))
            } else {
                formatter.format(Date(timestampSec * 1000))
            }

            binding.tvTimeMark.text = label
            val params = binding.root.layoutParams ?: RecyclerView.LayoutParams(
                (30 * pxPerMinute).toInt(),
                RecyclerView.LayoutParams.MATCH_PARENT
            )
            params.width = (30 * pxPerMinute).toInt() // immer 30 Minuten breit
            binding.root.layoutParams = params

            binding.root.setOnFocusChangeListener { _, hasFocus ->
                binding.tvTimeMark.isSelected = hasFocus
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeVH {
        val binding = RvItemTvguideTimelineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TimeVH(binding)
    }

    override fun onBindViewHolder(holder: TimeVH, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Long>() {
            override fun areItemsTheSame(oldItem: Long, newItem: Long) = oldItem == newItem
            override fun areContentsTheSame(oldItem: Long, newItem: Long) = oldItem == newItem
        }
    }
}
