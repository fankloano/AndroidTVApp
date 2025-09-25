package com.example.mj_player_tv.ui.tvguide.viewholders

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.databinding.RvItemTvguideEpgBinding
import com.volkov.epgrecycler.EPGUtils.getCellWidth
import com.volkov.EPGConfig
import com.volkov.epgrecycler.EPGUtils
import com.volkov.epgrecycler.EPGUtils.endTime
import com.volkov.epgrecycler.EPGUtils.startTime
import com.volkov.epgrecycler.context
import com.volkov.epgrecycler.dpToPx
import dev.androidbroadcast.vbpd.viewBinding
import org.joda.time.DateTime
import kotlin.math.max
import kotlin.math.min

class TvGuideEpgViewholder(itemView: View, private val channelId: String) : RecyclerView.ViewHolder(itemView) {

    private val binding by viewBinding(RvItemTvguideEpgBinding::bind)

    @SuppressLint("SetTextI18n")
    fun bind(item: EpgDataOB) {

        binding.root.tag = "${channelId}#${item.idByAccountData}"

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
    }
    private val EpgDataOB.isLiveShow: Boolean
        get() = startTimestamp < System.currentTimeMillis() / 1000 && stopTimestamp > System.currentTimeMillis() / 1000


    // In der TvGuideEpgViewholder-Klasse
// Entferne die updateBackground-Methode und füge diese beiden Funktionen hinzu
// Diese Methode aktualisiert nur den Hintergrund und die Sichtbarkeit des Fortschritts
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