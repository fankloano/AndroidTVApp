package com.example.mj_player_tv.ui.tvguide.viewholders

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.databinding.RvItemTvguideEpgBinding
import com.volkov.epgrecycler.EPGUtils.getCellWidth
import com.volkov.EPGConfig
import com.volkov.epgrecycler.EPGUtils
import com.volkov.epgrecycler.EPGUtils.endTime
import com.volkov.epgrecycler.EPGUtils.startTime
import com.volkov.epgrecycler.dpToPx
import dev.androidbroadcast.vbpd.viewBinding
import org.joda.time.DateTime

class TvGuideEpgViewholder(itemView: View, private val channelId: String) : RecyclerView.ViewHolder(itemView) {

    private val binding by viewBinding(RvItemTvguideEpgBinding::bind)

    @SuppressLint("SetTextI18n")
    fun bind(item: EpgDataOB) {

        binding.root.tag = "${channelId}#${item.idByAccountData}"
        binding.root.updateLayoutParams<RecyclerView.LayoutParams> {
            marginEnd = EPGConfig.marginEnd.dpToPx
        }

        val itemstartDate = DateTime(item.startTimestamp * 1000L)
        val itemendDate = DateTime(item.stopTimestamp * 1000L)

        val hide = itemendDate.isBefore(com.example.mj_player_tv.ui.tvguide.EPGUtils.startEpgTime)
        val start = if (itemstartDate.isBefore(com.example.mj_player_tv.ui.tvguide.EPGUtils.startEpgTime)) com.example.mj_player_tv.ui.tvguide.EPGUtils.startEpgTime else itemstartDate
        val end = if (itemendDate.isAfter(EPGUtils.endTime)) EPGUtils.endTime else itemendDate


        binding.tvProgram.text = item.name
        if (item.sub_title.isNotEmpty()) {
            binding.tvSubTitleProgram.text = item.sub_title
        } else {
            binding.tvSubTitleProgram.visibility = View.GONE
        }

        // Die Breite der Ansicht wird basierend auf der sichtbaren Dauer auf der Timeline berechnet.
        if (!hide) {
            binding.relLayoutFullepgitem.updateLayoutParams<RecyclerView.LayoutParams> {
                width = EPGUtils.getCellWidth(start, end)
                }
            } else {
            binding.relLayoutFullepgitem.updateLayoutParams<RecyclerView.LayoutParams> {
                width = 0
            }
        }
        updateProgress(item, DateTime())

    }
    private val EpgDataOB.isLiveShow: Boolean
        get() = startTimestamp < System.currentTimeMillis() / 1000 && stopTimestamp > System.currentTimeMillis() / 1000

    fun updateProgress(item: EpgDataOB, now: DateTime) {
        if (!item.idByAccountData.startsWith("dummy_")) {
            // Now you can use the 'now' object to calculate progress
            val progressStartTime =
                if (item.startTimestamp < com.example.mj_player_tv.ui.tvguide.EPGUtils.startEpgTime.millis / 1000) {
                    com.example.mj_player_tv.ui.tvguide.EPGUtils.startEpgTime.millis / 1000
                } else {
                    item.startTimestamp
                }

            val progressEndTime = if (item.stopTimestamp > EPGUtils.endTime.millis / 1000) {
                EPGUtils.endTime.millis / 1000
            } else {
                item.stopTimestamp
            }

            val progress = (now.millis / 1000) - progressStartTime
            val progressMax = progressEndTime - progressStartTime

            binding.progressBar.isVisible =
                item.isLiveShow && (now.millis / 1000) in progressStartTime..progressEndTime
            binding.progressBar.max = progressMax.toInt()
            binding.progressBar.progress = progress.toInt()
        } else {
            binding.progressBar.visibility = View.INVISIBLE
        }
    }
}