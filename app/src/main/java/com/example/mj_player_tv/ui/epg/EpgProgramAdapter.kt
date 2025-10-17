package com.example.mj_player_tv.ui.epg

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.VgItemEpgProgramBinding
import com.example.mj_player_tv.ui.tvguide.viewholders.TvGuideEmptyShowViewholder
import com.example.mj_player_tv.ui.tvguide.viewholders.TvGuideEpgViewholder
import com.example.mj_player_tv.ui.tvguidetest.EpgUtil
import com.volkov.epgrecycler.EPGUtils
import com.volkov.epgrecycler.getView
import org.joda.time.DateTime
import kotlin.math.max
import kotlin.math.min

// ProgramBlockAdapter.kt

class EpgProgramAdapter(private val onClick: (EpgDataOB) -> Unit, private var channelName: String) :
    ListAdapter<EpgDataOB, EpgProgramAdapter.ProgramViewHolder>(ProgramDiffCallback()) {

    fun updateChannelName(newChannelName: String) {
        this.channelName = newChannelName
    }
    inner class ProgramViewHolder(private val binding: VgItemEpgProgramBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Die Root View ist bereits im XML als fokussierbar markiert!

        fun bind(item: EpgDataOB) {
            // 1. Dynamische Breitenberechnung

            val startTimeDate = DateTime(item.startTimestamp * 1000)
            val endTimeDate = DateTime(item.stopTimestamp * 1000)
            val startTime = if (startTimeDate.isBefore(EpgUtils.timeLineStartTime)) EpgUtils.timeLineStartTime else startTimeDate
            val endTime = if (endTimeDate.isAfter(EpgUtils.timeLineEndTime)) EpgUtils.timeLineEndTime else endTimeDate
            val hide = if (endTimeDate.isBefore(EpgUtils.timeLineStartTime)) true else false

            val widthPx = if (hide) 0 else EpgUtils.getEpgWidth(startTime, endTime)
            binding.relLayoutFullepgitem.layoutParams.width = widthPx

            // 2. Datenbindung
            binding.tvProgram.text = item.name
            binding.tvSubTitleProgram.text = item.sub_title

            // 3. Status
            binding.progressBar.visibility = if (item.isLiveShow) View.VISIBLE else View.INVISIBLE
            binding.ivReminder.visibility = if (item.isRemembered) View.VISIBLE else View.INVISIBLE
            val minuten = widthPx / 5

            binding.relLayoutFullepgitem.setOnFocusChangeListener { _, hasFocus ->
                Log.d("FOKUSSIERTES ITEM", "$channelName = ${item.name} ON INDEX: $bindingAdapterPosition")
            }
        }

        private val EpgDataOB.isLiveShow: Boolean
            get() = startTimestamp < System.currentTimeMillis() / 1000 && stopTimestamp > System.currentTimeMillis() / 1000

        fun updateUiState(item: EpgDataOB) {
            val nowSeconds = System.currentTimeMillis() / 1000
            val isLive = item.isLiveShow
            val isPast = item.stopTimestamp <= nowSeconds
            // Zeige die ProgressBar nur für Live-Sendungen an
            binding.progressBar.isVisible = isLive
        }

        // Diese Methode wird von deinem Runnable im Fragment aufgerufen, um den Fortschritt zu aktualisieren
        fun updateProgress(item: EpgDataOB) {
            val nowSeconds = System.currentTimeMillis() / 1000

            if (item.isLiveShow) {
                val progressStartTime = max(item.startTimestamp, com.example.mj_player_tv.ui.tvguide.EPGUtils.startEpgTime.millis / 1000)
                val progressEndTime = min(item.stopTimestamp, EPGUtils.endTime.millis / 1000)

                val progress = nowSeconds - progressStartTime
                val progressMax = progressEndTime - progressStartTime

                if (progressMax > 0) {
                    binding.progressBar.updateLayoutParams {
                        width = LinearLayout.LayoutParams.MATCH_PARENT
                    }
                    binding.progressBar.max = progressMax.toInt()
                    binding.progressBar.progress = progress.toInt()
                    binding.progressBar.isVisible = true
                } else {
                    binding.progressBar.isVisible = false
                }
            } else {
                // Keine ProgressBar für vergangene oder zukünftige Sendungen
                binding.progressBar.isVisible = false
            }
        }
    }


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ProgramViewHolder {
        val binding = VgItemEpgProgramBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val viewHolder = ProgramViewHolder(binding)
        return viewHolder
    }

    override fun onBindViewHolder(
        holder: ProgramViewHolder,
        position: Int,
    ) {
        val show = getItem(position)
        holder.bind(show)
        holder.itemView.tag = "${show.name} + ${show.sub_title} I: ${holder.bindingAdapterPosition}"
        holder.itemView.setTag(R.id.program_data_tag, show)

    }

    class ProgramDiffCallback : DiffUtil.ItemCallback<EpgDataOB>() {
        override fun areItemsTheSame(oldItem: EpgDataOB, newItem: EpgDataOB): Boolean =
            oldItem.idByAccountData == newItem.idByAccountData

        override fun areContentsTheSame(oldItem: EpgDataOB, newItem: EpgDataOB): Boolean =
            oldItem == newItem
    }
}