package com.example.mj_player_tv.ui.epg

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.databinding.VgItemEpgTimelineBinding

// TimeAdapter.kt

class EpgTimeLineAdapter : ListAdapter<TimeLineData, EpgTimeLineAdapter.TimeViewHolder>(TimeDiffCallback()) {

    // Konstante Breite des Zeitblocks (Wichtig für Scroll-Sync!)
    companion object {
        const val TIME_BLOCK_WIDTH_DP = 150 // z.B. 150dp pro 30 Minuten
    }

    class TimeViewHolder(private val binding: VgItemEpgTimelineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Stellen Sie sicher, dass die Kachel NICHT fokussierbar ist
            binding.root.isFocusable = false
            // Dynamische Breitenanpassung (wie im vorherigen Beispiel)
            val params = binding.root.layoutParams
            params.width = (TIME_BLOCK_WIDTH_DP * binding.root.context.resources.displayMetrics.density).toInt()
            binding.root.layoutParams = params
        }

        fun bind(item: TimeLineData) {
            binding.tvTime.text = item.time // Angenommen tvTime ist die ID im Layout
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeViewHolder {
        val binding = VgItemEpgTimelineBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TimeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TimeDiffCallback : DiffUtil.ItemCallback<TimeLineData>() {
        override fun areItemsTheSame(oldItem: TimeLineData, newItem: TimeLineData): Boolean =
            oldItem.timeId == newItem.timeId

        override fun areContentsTheSame(oldItem: TimeLineData, newItem: TimeLineData): Boolean =
            oldItem == newItem
    }
}