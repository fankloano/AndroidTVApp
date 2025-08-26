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
import com.example.mj_player_tv.ui.FullEpgFragment
import com.example.mj_player_tv.ui.TvGuideFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
class TvGuideEpgAdapter(
    private val fragment: TvGuideFragment,
    private val channelPosition: ChannelPositions,
    private val helpViewModel: HelpViewModel) : ListAdapter<EpgDataOB, TvGuideEpgAdapter.ViewHolder>(
    FULLEPG_COMPERATOR) {

    private var selectedEpgData: String = ""

    var focusedChannelId = ""

    inner class ViewHolder(val binding: RvItemTvguideEpgBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(epgData: EpgDataOB) {
            val tvchannel = channelPosition.tvchannel.target
            val timeOffSetSec = calculateTimeOffsetInSeconds(
                tvchannel.epgTimeOffSet
                    ?: channelPosition.tvcategory.target.epgTimeOffSet
                    ?: tvchannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                    ?: 0
            )
            // Hilfsfunktion für verschobene Zeiten
            fun EpgDataOB.shiftedStart() = (this.startTimestamp ?: 0) + timeOffSetSec
            fun EpgDataOB.shiftedStop()  = (this.stopTimestamp ?: 0) + timeOffSetSec
            binding.tvProgram.text = epgData.name
            val startTime = formatUnixTimestampToTime(epgData.shiftedStart())
            val endTime = formatUnixTimestampToTime(epgData.shiftedStop())
            binding.tvStartTime.text = startTime
            binding.tvEndTime.text = " - ${endTime}"
            if (epgData.sub_title.isEmpty()) {
                binding.tvSubTitleProgram.visibility = View.GONE
            } else {
                binding.tvSubTitleProgram.visibility = View.VISIBLE
                binding.tvSubTitleProgram.text = epgData.sub_title
            }
            if (epgData.isRemembered) {
                binding.ivReminder.visibility = View.VISIBLE
            } else {
                binding.ivReminder.visibility = View.INVISIBLE
            }

            val durationMinutes = ((epgData.stopTimestamp ?: 0) - (epgData.startTimestamp ?: 0)) / 60
            val params = binding.root.layoutParams
            params.width = (durationMinutes * 5f).toInt()
            binding.root.layoutParams = params
        }

        fun calculateTimeOffsetInSeconds(timeOffset: Int): Long {
            return (timeOffset * 3600).toLong()
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemTvguideEpgBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val epgData = getItem(position)
        holder.bind(epgData)

        holder.binding.relLayoutFullepgitem.isSelected = epgData.idByAccountData == helpViewModel.currentSelectedEpgForSelectedChannel?.idByAccountData


        holder.binding.relLayoutFullepgitem.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvProgram.isSelected = hasFocus
            holder.binding.tvSubTitleProgram.isSelected = hasFocus
            if (hasFocus) {
                //fragment.showDetailEpg(epgData)
            }
        }
    }

    companion object {
        private val FULLEPG_COMPERATOR = object : DiffUtil.ItemCallback<EpgDataOB>() {
            override fun areItemsTheSame(oldItem: EpgDataOB, newItem: EpgDataOB) =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: EpgDataOB, newItem: EpgDataOB) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (epg: EpgDataOB, view: View) -> Unit) {
        fun onClick(epg: EpgDataOB, view: View) = clickListener(epg, view)
    }

    fun formatUnixTimestampToTime(unixTimestamp: Long): String {
        return try {
            val date = Date(unixTimestamp * 1000) // Timestamp in Sekunden → ms
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            timeFormat.format(date)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun setCurrentChannelId(channelId: String) {
        focusedChannelId = channelId
    }
}
