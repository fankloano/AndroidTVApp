package com.example.mj_player_tv.ui.adapter

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgDataOB_
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.RvItemFullepgBinding
import com.example.mj_player_tv.databinding.RvItemScrollTvchannelBinding
import com.example.mj_player_tv.ui.FullEpgFragment
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import io.objectbox.Box
import kotlinx.coroutines.delay
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
) : ListAdapter<TvChannelWithEpg, ScrollTvChannelAdapter.ViewHolder>(
    SCROLLTVCHANNEL_COMPERATOR) {

    private val focusHandler = Handler(Looper.getMainLooper())
    private var focusRunnable: Runnable? = null

    inner class ViewHolder(val binding: RvItemScrollTvchannelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tvChannelWithEpg: TvChannelWithEpg) {
            val tvchannelpos = tvChannelWithEpg.tvChannelPosition
            val tvChannel = tvchannelpos.tvchannel.target
            val linkedEpgChannel = tvChannel.linkedEpgChannel?.target
            val epgChId = linkedEpgChannel?.chEpgId
            val image = tvChannel.logo
            val epgLogo = linkedEpgChannel?.icon?.firstOrNull()

            binding.tvChannelname.text = tvChannel.showingName

            if (tvChannel.account.target!!.useEpgLogos) {
                if (!epgLogo.isNullOrEmpty() && (linkedEpgChannel.isExternalEpg || tvChannel.alwaysUsesExternalEpg)) {
                    binding.ivChannelLogo.visibility = View.VISIBLE
                    binding.ivChannelLogo.load(epgLogo)
                } else {
                    if (image.isNotEmpty()) {
                        binding.ivChannelLogo.visibility = View.VISIBLE
                        binding.ivChannelLogo.load(image)
                    } else {
                        binding.ivChannelLogo.visibility = View.INVISIBLE
                    }
                }
            } else {
                if (image.isNotEmpty()) {
                    binding.ivChannelLogo.visibility = View.VISIBLE
                    binding.ivChannelLogo.load(image)
                } else {
                    binding.ivChannelLogo.visibility = View.INVISIBLE
                }
            }
            val currentTimeSec = System.currentTimeMillis() / 1000

            val timeOffSetSec = calculateTimeOffsetInSeconds(
                tvChannel.epgTimeOffSet
                    ?: tvchannelpos.tvcategory.target.epgTimeOffSet
                    ?: tvChannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                    ?: 0
            )

            // Hilfsfunktion für verschobene Zeiten
            fun EpgDataOB.shiftedStart() = (this.startTimestamp ?: 0) + timeOffSetSec
            fun EpgDataOB.shiftedStop()  = (this.stopTimestamp ?: 0) + timeOffSetSec

            binding.progressBar.max = 100
            if (epgChId != null) {
                val currentProgram = epgDataBox.query(
                    EpgDataOB_.epgChId.equal(epgChId)
                        .and(EpgDataOB_.stopTimestamp.greater(currentTimeSec - timeOffSetSec)
                            .and(EpgDataOB_.startTimestamp.less(currentTimeSec - timeOffSetSec)))
                ).build().findFirst()
                if (currentProgram != null) {
                    val startTime = formatUnixTimestampToTime(currentProgram.shiftedStart())
                    val endTime   = formatUnixTimestampToTime(currentProgram.shiftedStop())
                    binding.tvStartTime.text = startTime
                    binding.tvEndTime.text = " - $endTime"
                    binding.tvProgram.text = currentProgram.name

                    val duration = currentProgram.shiftedStop() - currentProgram.shiftedStart()
                    val progress = ((currentTimeSec - currentProgram.shiftedStart()) * 100 / duration).toInt()
                    binding.progressBar.isInvisible = false
                    binding.progressBar.max = 100
                    binding.progressBar.progress = progress.coerceIn(0, 100)
                } else {
                    binding.tvProgram.text = itemView.context.getString(R.string.no_information)
                    binding.tvStartTime.text = formatUnixTimestampToTime(currentTimeSec)
                    binding.tvEndTime.text = " - " + formatUnixTimestampToTime(currentTimeSec + 1800)
                    binding. progressBar.progress = 0
                }
            } else {
                binding.tvProgram.text = itemView.context.getString(R.string.no_information)
                binding.tvStartTime.text = formatUnixTimestampToTime(currentTimeSec)
                binding.tvEndTime.text = " - " + formatUnixTimestampToTime(currentTimeSec + 1800)
                binding. progressBar.progress = 0
            }

            binding.relLayoutScrollTvchannel.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    fragment.closeScrollTvChannelRV()
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
        val tvChannelWithEpg = getItem(position)
        holder.bind(tvChannelWithEpg)

        holder.binding.relLayoutScrollTvchannel.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.progressBar.isSelected = hasFocus
            holder.binding.tvProgram.isSelected = hasFocus
            holder.binding.tvChannelname.isSelected = hasFocus
            // Fokus erhalten → Timer starten
            if (hasFocus) {
                holder.binding.overlayFull.visibility = View.GONE
                focusRunnable?.let { focusHandler.removeCallbacks(it) } // falls vorher noch aktiv
                focusRunnable = Runnable {
                    if (tvChannelWithEpg.tvChannelPosition.catAndChannelAccount != helpViewModel.currentPlayingChannelPosition?.catAndChannelAccount) {
                        onClickListener.onClick(tvChannelWithEpg)
                    }
                    fragment.closeScrollTvChannelRV()
                }
                focusHandler.postDelayed(focusRunnable!!, 2000) // 2 Sekunden
            } else {
                holder.binding.overlayFull.visibility = View.VISIBLE
                // Fokus verloren → Timer abbrechen
                focusRunnable?.let { focusHandler.removeCallbacks(it) }
            }
        }

        holder.binding.relLayoutScrollTvchannel.setOnClickListener {
            focusRunnable?.let { focusHandler.removeCallbacks(it) } // falls noch aktiv
            if (tvChannelWithEpg.tvChannelPosition.catAndChannelAccount != helpViewModel.currentPlayingChannelPosition?.catAndChannelAccount) {
                onClickListener.onClick(tvChannelWithEpg)
            }
            fragment.closeScrollTvChannelRV()
        }
    }

    companion object {
        private val SCROLLTVCHANNEL_COMPERATOR = object : DiffUtil.ItemCallback<TvChannelWithEpg>() {
            override fun areItemsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg) =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (tvChannelWithEpg: TvChannelWithEpg) -> Unit) {
        fun onClick(tvChannelWithEpg: TvChannelWithEpg) = clickListener(tvChannelWithEpg)
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
}
