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
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.RvItemFullepgBinding
import com.example.mj_player_tv.databinding.RvItemGlobalsearchEpglistBinding
import com.example.mj_player_tv.ui.FullEpgFragment
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
class GlobalSearchEpgListAdapter(
    private val onEpgFocused: (EpgDataOB) -> Unit,
    private val helpViewModel: HelpViewModel) : ListAdapter<EpgDataOB, GlobalSearchEpgListAdapter.ViewHolder>(
    FULLEPG_COMPERATOR) {

    var selectedChannel: ChannelPositions? = null

    inner class ViewHolder(val binding: RvItemGlobalsearchEpglistBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(epgData: EpgDataOB) {

            val timeOffSet = helpViewModel.currentFocusedChannel?.epgTimeOffSet ?: helpViewModel.currentFocusedChannPosition?.tvcategory?.target?.epgTimeOffSet ?: helpViewModel.currentFocusedChannel?.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0
            binding.tvProgram.text = epgData.name
            if (epgData.showIcon.isNotEmpty()) {
                binding.viewFullepg.visibility = View.VISIBLE
                binding.ivTvshowPoster.visibility = View.VISIBLE
                binding.ivTvshowPoster.load(epgData.showIcon)
            } else {
                binding.viewFullepg.visibility = View.GONE
                binding.ivTvshowPoster.visibility = View.GONE
            }
            val startTime = formatUnixTimestampToTime(epgData.startTimestamp!!, timeOffSet)
            val endTime = formatUnixTimestampToTime(epgData.stopTimestamp!!, timeOffSet)
            binding.tvStartTime.text = startTime
            binding.tvEndTime.text = " - ${endTime}"
            if (epgData.sub_title.isEmpty()) {
                binding.tvSubTitleProgram.visibility = View.GONE
            } else {
                binding.tvSubTitleProgram.visibility = View.VISIBLE
                binding.tvSubTitleProgram.text = epgData.sub_title
            }
            binding.tvDate.text = epgData.datum

            val currentTime = System.currentTimeMillis() / 1000
            val timeOffSetSeconds = calculateTimeOffsetInSeconds(timeOffSet)
            val currentTimePlusTimeOffSet = currentTime.plus(timeOffSetSeconds)

            if (epgData.startTimestamp!! < currentTimePlusTimeOffSet && epgData.stopTimestamp!! > currentTimePlusTimeOffSet) {
                binding.ivPlayTv.visibility = View.VISIBLE
            } else if (epgData.startTimestamp!! > currentTimePlusTimeOffSet) {
                binding.ivLater.visibility = View.VISIBLE
            } else if (epgData.stopTimestamp!! < currentTimePlusTimeOffSet && selectedChannel?.tvchannel?.target?.enable_tv_archive == 1) {
                binding.ivCatchup.visibility = View.VISIBLE
            }

        }

        fun calculateTimeOffsetInSeconds(timeOffset: Int): Long {
            return (timeOffset * 3600).toLong()
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemGlobalsearchEpglistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val epgData = getItem(position)
        holder.bind(epgData)

        holder.binding.relLayoutFullepgitem.isSelected = epgData.idByAccountData == helpViewModel.currentFullEpgProgramId

        holder.binding.ivPlayTv.isSelected = if (helpViewModel.isFullScreenFullEpg) {
            (epgData.idByAccountData == helpViewModel.currentFullEpgProgramId && helpViewModel.currentPlayingChannelPosition == helpViewModel.fullScreenFocusedChannelPosition)

        } else {
            (epgData.idByAccountData == helpViewModel.currentFullEpgProgramId && helpViewModel.currentPlayingChannelPosition == helpViewModel.currentFocusedChannPosition)
        }

        if (epgData.idByAccountData == helpViewModel.currentFullEpgProgramId) {
            holder.binding.ivPlayTv.visibility = View.VISIBLE
        } else {
            holder.binding.ivPlayTv.visibility = View.INVISIBLE
        }

        holder.binding.relLayoutFullepgitem.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.ivTvshowPoster.isSelected = hasFocus
            holder.binding.viewFullepg.isSelected = hasFocus
            holder.binding.tvProgram.isSelected = hasFocus
            holder.binding.tvSubTitleProgram.isSelected = hasFocus
            if (hasFocus) {
                holder.binding.overlayFull.visibility = View.GONE
                onEpgFocused.invoke(epgData)
            } else {
                holder.binding.overlayFull.visibility = View.VISIBLE
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

    class OnClickListener(val clickListener: (epg: EpgDataOB) -> Unit) {
        fun onClick(epg: EpgDataOB) = clickListener(epg)
    }

    fun formatUnixTimestampToTime(unixTimestamp: Long, timeOffset: Int): String {
        try {
            // Konvertiere den Unix-Zeitstempel in ein Date-Objekt
            val date = Date(unixTimestamp * 1000)

            // Erstelle ein SimpleDateFormat-Objekt für das gewünschte Zeitformat
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            // Berechne den Zeitversatz in Stunden (positiv oder negativ)
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.add(Calendar.HOUR_OF_DAY, timeOffset)

            // Gib das formatierte Datum und die Uhrzeit zurück
            return timeFormat.format(calendar.time)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }
}
