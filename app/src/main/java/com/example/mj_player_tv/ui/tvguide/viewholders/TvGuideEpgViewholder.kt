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

        val progressMax = item.stopTimestamp - item.startTimestamp
        val progress = (System.currentTimeMillis() / 1000) - item.startTimestamp
        binding.progressBar.isVisible = item.isLiveShow
        binding.progressBar.max = progressMax.toInt()
        binding.progressBar.progress = progress.toInt()

        val itemstartDate = DateTime(item.startTimestamp * 1000L)
        val itemendDate = DateTime(item.stopTimestamp * 1000L)
        val isBefore = itemstartDate.isBefore(startTime)
        val start = if (itemstartDate.isBefore(startTime)) startTime else itemstartDate
        val end = if (itemendDate.isAfter(endTime)) endTime else itemendDate

        binding.tvProgram.text = item.name
        binding.tvSubTitleProgram.text =
            item.sub_title
        // Berechnet die Standardbreite der Sendung
        val baseWidth = getCellWidth(start, end)

        // Fügt einen zusätzlichen Puffer hinzu, um die Sendungen um 15 Minuten nach rechts zu verschieben
        val bufferOffset = (15 * EPGUtils.minuteToPixel) / 2

        // Die endgültige Breite der Sendung ist die Basisbreite plus der Puffer
        val finalWidth = baseWidth + bufferOffset
        binding.relLayoutFullepgitem.updateLayoutParams<RecyclerView.LayoutParams> {
            width = finalWidth
        }


    }

    private val EpgDataOB.isLiveShow: Boolean
        get() = startTimestamp < System.currentTimeMillis() / 1000 && stopTimestamp > System.currentTimeMillis() / 1000
}