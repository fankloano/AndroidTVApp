package com.example.mj_player_tv.ui.tvguidetest

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.RvItemTvguideTvepgBinding

class ProgramGuideChannelViewHolder(
    val binding: RvItemTvguideTvepgBinding,
    private val manager: ProgramGuideManager,
) : RecyclerView.ViewHolder(binding.root) {

    private val epgAdapter = ProgramGuideEpgAdapter()

    fun bind(channelWithEpg: TvChannelWithEpg) {
        val channelPosition = channelWithEpg.tvChannelPosition
        val tvChannel = channelPosition.tvchannel.target
        binding.apply {
            tvChannelname.text = tvChannel.showingName
        }

        val playlistActive = tvChannel.account.target.epgsources
            .any { it.isSelected && it.isPlaylistEpg }

        val chEpgId = tvChannel.extern_xmltv_id ?: if (playlistActive) tvChannel.xmltv_id else null

        val epgData = manager.getSchedulesForChannel(chEpgId)
        binding.rvChannelPrograms.layoutManager = LinearLayoutManager(
            binding.rvChannelPrograms.context,
            LinearLayoutManager.HORIZONTAL,
            false
        )
        binding.rvChannelPrograms.adapter = epgAdapter
        epgAdapter.submitList(epgData)
    }
}
