package com.example.mj_player_tv.ui.tvguidetest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.databinding.RvItemTvguideEpgBinding

class ProgramGuideEpgAdapter :
    ListAdapter<EpgDataOB, ProgramGuideEpgAdapter.ProgramViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<EpgDataOB>() {
            override fun areItemsTheSame(
                oldItem: EpgDataOB,
                newItem: EpgDataOB
            ) = oldItem.idByAccountData == newItem.idByAccountData

            override fun areContentsTheSame(
                oldItem: EpgDataOB,
                newItem: EpgDataOB
            ) = oldItem == newItem
        }
    }

    inner class ProgramViewHolder(val binding: RvItemTvguideEpgBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(epgDataOB: EpgDataOB) {
            binding.apply {
                tvProgram.text = epgDataOB.name
                tvSubTitleProgram.visibility = if (epgDataOB.sub_title.isNotEmpty()) {
                    tvSubTitleProgram.text = epgDataOB.sub_title
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
            // Breite = Dauer in Minuten * 5 Pixel
            val durationMinutes = ((epgDataOB.stopTimestamp - epgDataOB.startTimestamp) / 60).toInt()
            val widthPx = durationMinutes * 5
            binding.relLayoutFullepgitem.layoutParams.width = widthPx
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgramViewHolder {
        val binding = RvItemTvguideEpgBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProgramViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProgramViewHolder, position: Int) {
        val program = getItem(position)
        holder.itemView.isFocusable = true
    }
}
