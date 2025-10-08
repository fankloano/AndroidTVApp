package com.example.mj_player_tv.ui.tvguidetest

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.TvChannelOB_.channelId
import com.example.mj_player_tv.database.help.ShowTag
import com.example.mj_player_tv.databinding.RvItemTvguideEpgBinding
import org.joda.time.DateTime

class ProgramGuideEpgAdapter(
) :
    ListAdapter<EpgDataOB, ProgramGuideEpgAdapter.ProgramViewHolder>(DIFF_CALLBACK) {

    var channelId = ""

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

            val startTime = DateTime(epgDataOB.startTimestamp * 1000L)
            val endTime = DateTime(epgDataOB.stopTimestamp * 1000L)

            val hide = endTime.isBefore(ProgramGuideUtils.epgStartTime)

            if (!hide) {
                val itemStart =
                    if (startTime.isBefore(ProgramGuideUtils.epgStartTime)) ProgramGuideUtils.epgStartTime else startTime
                val itemEnd =
                    if (endTime.isAfter(ProgramGuideUtils.epgEndTime)) ProgramGuideUtils.epgEndTime else endTime
                // Breite = Dauer in Minuten * 5 Pixel
                binding.relLayoutFullepgitem.layoutParams.width =
                    ProgramGuideUtils.getCellWidth(itemStart, itemEnd)
            } else {
                binding.relLayoutFullepgitem.layoutParams.width = 0
            }
            binding.root.tag = ShowTag(channelId, epgDataOB.idByAccountData)
        }

        private val EpgDataOB.isLiveShow: Boolean
            get() = startTimestamp < System.currentTimeMillis() / 1000 && stopTimestamp > System.currentTimeMillis() / 1000

        // Fügen Sie diese beiden Methoden hinzu
        fun select() {
            binding.relLayoutFullepgitem.isSelected = true
            // Hier können Sie auch zusätzliche Animationen oder Zustandsänderungen auslösen
        }

        fun deselect() {
            binding.relLayoutFullepgitem.isSelected = false
            // Hier können Sie auch zusätzliche Animationen oder Zustandsänderungen auslösen
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgramViewHolder {
        val binding = RvItemTvguideEpgBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProgramViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProgramViewHolder, position: Int) {
        val program = getItem(position)
        holder.bind(program)
        holder.itemView.isFocusable = true
    }
}
