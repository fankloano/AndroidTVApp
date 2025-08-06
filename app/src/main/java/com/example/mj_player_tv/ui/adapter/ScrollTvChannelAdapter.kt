package com.example.mj_player_tv.ui.adapter

import android.os.Handler
import android.os.Looper
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
import com.example.mj_player_tv.database.entity.EpgDataOB_
import com.example.mj_player_tv.databinding.RvItemFullepgBinding
import com.example.mj_player_tv.databinding.RvItemScrollTvchannelBinding
import com.example.mj_player_tv.ui.FullEpgFragment
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import io.objectbox.Box
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
class ScrollTvChannelAdapter(
    private val onClickListener: ScrollTvChannelAdapter.OnClickListener,
    private val fragment: TvChannelsFragment,
    private val helpViewModel: HelpViewModel,
    private val epgDataBox: Box<EpgDataOB>
) : ListAdapter<ChannelPositions, ScrollTvChannelAdapter.ViewHolder>(
    SCROLLTVCHANNEL_COMPERATOR) {

    private val focusHandler = Handler(Looper.getMainLooper())
    private var focusRunnable: Runnable? = null

    inner class ViewHolder(val binding: RvItemScrollTvchannelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tvchannelpos: ChannelPositions) {
            val tvChannel = tvchannelpos.tvchannel.target
            val epgChId = tvChannel.linkedEpgChannel?.target?.chEpgId
            val timeOffSet = helpViewModel.currentFocusedChannel?.epgTimeOffSet ?: helpViewModel.currentFocusedChannPosition?.tvcategory?.target?.epgTimeOffSet ?: helpViewModel.currentFocusedChannel?.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0
            val timeOffSetSeconds = calculateTimeOffsetInSeconds(timeOffSet)
            val currentTimeMillis =
                (System.currentTimeMillis() / 1000).plus(calculateTimeOffsetInSeconds(timeOffSet))
            if (epgChId != null) {
                val currentProgram = epgDataBox.query(
                    EpgDataOB_.epgChId.equal(epgChId)
                        .and(EpgDataOB_.stopTimestamp.greater(currentTimeMillis)
                            .and(EpgDataOB_.startTimestamp.less(currentTimeMillis)))
                ).build().findFirst()
                if (currentProgram != null) {
                    val currentStartTime = formatUnixTimestampToTime(currentProgram.startTimestamp!!, timeOffSet)
                    val currentEndTime = formatUnixTimestampToTime(currentProgram.stopTimestamp!!, timeOffSet)
                    binding.tvProgram.text = currentProgram.name
                    binding.tvStartTime.text = currentStartTime
                    binding.tvEndTime.text = " - ${currentEndTime}"

                    val duration =
                        ((currentProgram.stopTimestamp!! + timeOffSetSeconds).minus(
                            currentProgram.startTimestamp!! + timeOffSetSeconds
                        ))
                    binding.progressBar.max = 100
                    val progress =
                        ((currentTimeMillis - (currentProgram.startTimestamp!! + timeOffSetSeconds)) * 100 / duration).toInt()
                    binding.progressBar.progress = progress
                }
            }

            binding.relLayoutScrollTvchannel.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {

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
        val binding = RvItemScrollTvchannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tvChannPos = getItem(position)
        holder.bind(tvChannPos)

        holder.binding.relLayoutScrollTvchannel.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.progressBar.isSelected = hasFocus

            // Fokus erhalten → Timer starten
            if (hasFocus) {
                focusRunnable?.let { focusHandler.removeCallbacks(it) } // falls vorher noch aktiv
                focusRunnable = Runnable {
                    onClickListener.onClick(tvChannPos)
                }
                focusHandler.postDelayed(focusRunnable!!, 2000) // 2 Sekunden
            } else {
                // Fokus verloren → Timer abbrechen
                focusRunnable?.let { focusHandler.removeCallbacks(it) }
            }
        }

        holder.binding.relLayoutScrollTvchannel.setOnClickListener {
            focusRunnable?.let { focusHandler.removeCallbacks(it) } // falls noch aktiv
            onClickListener.onClick(tvChannPos)
        }
    }

    companion object {
        private val SCROLLTVCHANNEL_COMPERATOR = object : DiffUtil.ItemCallback<ChannelPositions>() {
            override fun areItemsTheSame(oldItem: ChannelPositions, newItem: ChannelPositions) =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: ChannelPositions, newItem: ChannelPositions) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (tvchannelpos: ChannelPositions) -> Unit) {
        fun onClick(tvchannelpos: ChannelPositions) = clickListener(tvchannelpos)
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
