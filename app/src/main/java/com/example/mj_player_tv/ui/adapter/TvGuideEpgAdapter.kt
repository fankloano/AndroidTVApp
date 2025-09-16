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
import com.rubensousa.dpadrecyclerview.ChildAlignment
import com.rubensousa.dpadrecyclerview.DpadRecyclerView
import com.rubensousa.dpadrecyclerview.layoutmanager.PivotLayoutManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

@UnstableApi
class TvGuideEpgAdapter(
    private val fragment: TvGuideFragment,
    private val channelPosition: ChannelPositions,
    private val helpViewModel: HelpViewModel) : ListAdapter<EpgDataOB, TvGuideEpgAdapter.ViewHolder>(
    FULLEPG_COMPERATOR) {

    var timelineStartSec: Long = 0

    var focusedChannelId = ""

    var pxPerMinute = 5f

    inner class ViewHolder(val binding: RvItemTvguideEpgBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(epgData: EpgDataOB) {
            val tvchannel = channelPosition.tvchannel.target
            val timeOffSetSec = calculateTimeOffsetInSeconds(
                tvchannel.epgTimeOffSet
                    ?: channelPosition.tvcategory.target.epgTimeOffSet
                    ?: tvchannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                    ?: 0
            )
            fun EpgDataOB.shiftedStart() = (this.startTimestamp ?: 0) + timeOffSetSec
            fun EpgDataOB.shiftedStop()  = (this.stopTimestamp ?: 0) + timeOffSetSec

            binding.tvProgram.text = epgData.name
            if (epgData.sub_title.isNotEmpty()) {
                binding.tvSubTitleProgram.visibility = View.VISIBLE
                binding.tvSubTitleProgram.text = epgData.sub_title
            } else {
                binding.tvSubTitleProgram.visibility = View.GONE
            }
            binding.ivReminder.visibility = if (epgData.isRemembered) View.VISIBLE else View.INVISIBLE


            val params = binding.relLayoutFullepgitem.layoutParams as RecyclerView.LayoutParams
            val startSec = max(epgData.shiftedStart(), timelineStartSec)
            val endSec = epgData.shiftedStop()
            val durationSec = endSec - startSec
            val offsetSec = startSec - timelineStartSec

            val durationMinutes = durationSec / 60f
            val startOffsetMinutes = offsetSec / 60f

            params.width = (( durationMinutes - startOffsetMinutes) * pxPerMinute).toInt()


            binding.relLayoutFullepgitem.setOnFocusChangeListener { _, hasFocus ->
                binding.tvProgram.isSelected = hasFocus
                binding.tvSubTitleProgram.isSelected = hasFocus
            }
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

                //fragment.showDetailEpg(epgData)

            if (hasFocus) {
            }
        }

        holder.binding.relLayoutFullepgitem.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

            val rv = holder.itemView.parent as? DpadRecyclerView ?: return@setOnKeyListener false
            val itemView = holder.itemView
            val rvWidth = rv.width

            return@setOnKeyListener when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val itemEnd = itemView.right
                    val rvVisibleEnd = rv.scrollX + rvWidth

                    if (itemEnd > rvVisibleEnd) {
                        rv.scrollBy(50, 0)
                        true
                    } else {
                        false // Event weitergeben, Fokus springt zum nächsten Item
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val itemStart = itemView.left
                    val rvStart = rv.scrollX

                    if (itemStart < rvStart) {
                        rv.scrollBy(-50, 0)
                        true
                    } else {
                        false // Event weitergeben, Fokus springt zum vorherigen Item
                    }
                }
                else -> false
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
