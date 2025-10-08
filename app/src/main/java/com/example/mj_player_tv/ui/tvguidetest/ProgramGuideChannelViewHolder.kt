package com.example.mj_player_tv.ui.tvguidetest

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.RvItemTvguideTvepgBinding
import com.example.mj_player_tv.ui.tvguide.EpgItemDecoration
import com.example.mj_player_tv.ui.tvguide.adapters.TvGuideEpgAdapter
import com.example.mj_player_tv.utils.EpgScrollSyncManager
import com.example.mj_player_tv.utils.views.NoFocusSearchLinearLayoutManager

class ProgramGuideChannelViewHolder(
    val binding: RvItemTvguideTvepgBinding,
    private val manager: ProgramGuideManager
) : RecyclerView.ViewHolder(binding.root) {

    val epgAdapter = ProgramGuideEpgAdapter()

    fun bind(channelWithEpg: TvChannelWithEpg, scrollSyncHelper: EpgScrollSyncManager) {
        val tag = "channel_${channelWithEpg.tvChannelPosition.catAndChannelAccount}"
        binding.root.tag = tag
        val tvchannelPos = channelWithEpg.tvChannelPosition
        val tvChannel = tvchannelPos.tvchannel.target
        val account = tvChannel.account.target
        val playlistEpgActive = account.usePlaylistEpg
        val linkedEpgChannel = tvChannel.linkedEpgChannel?.target
        binding.tvChannelname.text = tvChannel.showingName
        val image = tvChannel.logo
        val epgLogo = linkedEpgChannel?.icon?.firstOrNull()
        binding.apply {
            tvChannelname.text = tvChannel.showingName
            if (tvChannel.account.target!!.useEpgLogos) {
                if (!epgLogo.isNullOrEmpty() && (linkedEpgChannel.isExternalEpg || tvChannel.alwaysUsesExternalEpg)) {
                    ivChannelLogo.visibility = View.VISIBLE
                    ivChannelLogo.load(epgLogo)
                } else {
                    if (image.isNotEmpty()) {
                        ivChannelLogo.visibility = View.VISIBLE
                        ivChannelLogo.load(image)
                    } else {
                        ivChannelLogo.visibility = View.INVISIBLE
                    }
                }
            } else {
                if (image.isNotEmpty()) {
                    ivChannelLogo.visibility = View.VISIBLE
                    ivChannelLogo.load(image)
                } else {
                    ivChannelLogo.visibility = View.INVISIBLE
                }
            }
        }

        binding.rvChannelPrograms.layoutManager = NoFocusSearchLinearLayoutManager(
            binding.rvChannelPrograms.context,
        )

        binding.rvChannelPrograms.apply {
            adapter = epgAdapter
            addItemDecoration(
                EpgItemDecoration(color = context.getColor(R.color.background_dark))
            )
        }
        epgAdapter.channelId = channelWithEpg.tvChannelPosition.catAndChannelAccount
        epgAdapter.submitList(channelWithEpg.epgList)
        // wichtig: direkt nach dem Layout auf globale Position springen
        binding.rvChannelPrograms.post {
            scrollSyncHelper.jumpSyncTo(scrollSyncHelper.getTotalScrollX())
        }

        // registrieren für Scroll-Sync
        scrollSyncHelper.register(binding.rvChannelPrograms)

    }

    // In der ProgramGuideChannelViewHolder-Klasse
}
