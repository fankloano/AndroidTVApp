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
import com.example.mj_player_tv.databinding.RvItemTvguideEpgBinding
import org.joda.time.DateTime

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

            val startTime = DateTime(epgDataOB.startTimestamp * 1000L)
            val endTime = DateTime(epgDataOB.stopTimestamp * 1000L)
            // Breite = Dauer in Minuten * 5 Pixel

            binding.relLayoutFullepgitem.layoutParams.width = ProgramGuideUtils.getCellWidth(startTime, endTime)
            Log.d("PROGRAM GUIDE TEST EPG", "${epgDataOB.name} = START: $startTime END: $endTime WIDTH === ${binding.relLayoutFullepgitem.layoutParams.width}")
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
