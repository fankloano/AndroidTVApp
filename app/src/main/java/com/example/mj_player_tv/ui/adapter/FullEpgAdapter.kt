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
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.databinding.RvItemFullepgBinding
import com.example.mj_player_tv.ui.FullEpgFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
class FullEpgAdapter(private val onClickListener: FullEpgAdapter.OnClickListener,  private val fragment: FullEpgFragment, private val helpViewModel: HelpViewModel) : ListAdapter<EpgDataOB, FullEpgAdapter.ViewHolder>(
    FULLEPG_COMPERATOR) {

    private var selectedEpgData: String = ""

    var focusedChannelId = ""

    inner class ViewHolder(val binding: RvItemFullepgBinding) : RecyclerView.ViewHolder(binding.root) {
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
            if (epgData.isRemembered) {
                binding.ivReminder.visibility = View.VISIBLE
            } else {
                binding.ivReminder.visibility = View.INVISIBLE
            }

            binding.relLayoutFullepgitem.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    fragment.setFocusToTab()
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    fragment.setFocusToTab()
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    fragment.setFocusToDescription(epgData)
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
        }

        fun calculateTimeOffsetInSeconds(timeOffset: Int): Long {
            return (timeOffset * 3600).toLong()
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemFullepgBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val epgData = getItem(position)
        holder.bind(epgData)

        holder.binding.relLayoutFullepgitem.isSelected = epgData.idByAccountData == helpViewModel.currentSelectedEpgForSelectedChannel?.idByAccountData

        holder.binding.ivPlayTv.isSelected = if (helpViewModel.isFullScreenFullEpg) {
            (epgData.idByAccountData == helpViewModel.currentFullEpgProgramId && helpViewModel.currentPlayingChannelPosition == helpViewModel.fullScreenFocusedChannelPosition)

        } else {
            (epgData.idByAccountData == helpViewModel.currentFullEpgProgramId && helpViewModel.currentPlayingChannelPosition == helpViewModel.currentFocusedChannPosition)
        }

        if (epgData.idByAccountData == helpViewModel.currentFullEpgProgramId) {
            holder.binding.ivPlayTv.visibility = View.VISIBLE
        } else {
            holder.binding.ivPlayTv.visibility = View.GONE
        }

        holder.binding.relLayoutFullepgitem.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.ivTvshowPoster.isSelected = hasFocus
            holder.binding.viewFullepg.isSelected = hasFocus
            holder.binding.tvProgram.isSelected = hasFocus
            holder.binding.tvSubTitleProgram.isSelected = hasFocus
            if (hasFocus) {
                fragment.showDetailEpg(epgData)
            }
        }

        holder.binding.relLayoutFullepgitem.setOnClickListener {
            onClickListener.onClick(epgData, it)
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

    fun setCurrentChannelId(channelId: String) {
        focusedChannelId = channelId
    }
}
