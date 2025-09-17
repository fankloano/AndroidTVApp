package com.example.mj_player_tv.ui.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.RvItemTvguideTvepgBinding
import com.example.mj_player_tv.repository.TvGuideScrollSyncManager
import com.example.mj_player_tv.utils.EpgScrollSyncManager
import com.example.mj_player_tv.utils.views.ProgramsRecyclerView
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.TvGuideViewModel

class ChannelAdapter(
    private val onProgramClick: (EpgDataOB) -> Unit,
    private val helpViewModel: HelpViewModel,
    private val tvGuideViewModel: TvGuideViewModel,
    private val scrollSyncManager: TvGuideScrollSyncManager // << Manager vom Fragment

) : ListAdapter<TvChannelWithEpg, ChannelAdapter.ChannelViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TvChannelWithEpg>() {
            override fun areItemsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg): Boolean =
                oldItem == newItem
        }
    }

    inner class ChannelViewHolder(
        val binding: RvItemTvguideTvepgBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tvChannelWithEpg: TvChannelWithEpg) {
            // Channel Name + Logo
            val tvchannelPos = tvChannelWithEpg.tvChannelPosition
            val tvchannel = tvchannelPos.tvchannel.target
            val account = tvchannel.account.target
            val playlistEpgActive = account.usePlaylistEpg
            val linkedEpgChannel = tvchannel.linkedEpgChannel?.target
            binding.tvChannelname.text = tvchannel.showingName
            val image = tvchannel.logo
            val epgLogo = linkedEpgChannel?.icon?.firstOrNull()


            if (tvchannel.account.target!!.useEpgLogos) {
                if (!epgLogo.isNullOrEmpty() && (linkedEpgChannel.isExternalEpg || tvchannel.alwaysUsesExternalEpg)) {
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

            val epgList = tvChannelWithEpg.epgList
            val start = System.currentTimeMillis() / 1000 // Sekunden
            val end = start + 12 * 3600 // 12h später
            Log.d("TVGUIDE EPG", "${tvchannel.showingName} START = ${epgList.size}")
            val programsWithGaps = if (epgList.isEmpty()) {
                // Channel hat kein EPG → stündliche Platzhalter
                generateHourlyPlaceholders(start, end)
            } else {
                // Channel hat EPG → Lücken zwischen Sendungen füllen
                fillGaps(epgList, start, end)
            }
            Log.d("TVGUIDE EPG", "${tvchannel.showingName} END = ${programsWithGaps.size}")

            val adapter = ProgramsAdapter(tvGuideViewModel)
            binding.rvChannelPrograms.adapter = adapter
            adapter.submitList(programsWithGaps)
            scrollSyncManager.register(binding.rvChannelPrograms)
        }

        fun fillGaps(
            epgList: List<EpgDataOB>,
            startOfWindow: Long,
            endOfWindow: Long
        ): List<EpgDataOB> {
            val result = mutableListOf<EpgDataOB>()
            var cursor = startOfWindow

            for (program in epgList) {
                if ((program.startTimestamp ?: 0L) > cursor) {
                    // Lücke → Dummy EPG
                    result += EpgDataOB(
                        name = "No Information",
                        startTimestamp = cursor,
                        stopTimestamp = program.startTimestamp
                    )
                }
                result += program
                cursor = program.stopTimestamp ?: 0L
            }

            if (cursor < endOfWindow) {
                result += EpgDataOB(
                    name = "No Information",
                    startTimestamp = cursor,
                    stopTimestamp = endOfWindow
                )
            }

            return result
        }

        fun generateHourlyPlaceholders(startOfWindow: Long, endOfWindow: Long): List<EpgDataOB> {
            val result = mutableListOf<EpgDataOB>()
            var cursor = startOfWindow
            while (cursor < endOfWindow) {
                val nextHour = cursor + 3600
                result += EpgDataOB(
                    name = "No Information",
                    startTimestamp = cursor,
                    stopTimestamp = minOf(nextHour, endOfWindow)
                )
                cursor = nextHour
            }
            return result
        }


    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = RvItemTvguideTvepgBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
