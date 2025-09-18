package com.example.mj_player_tv.ui.tvguide.viewholders

import android.view.View
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.RvItemTvguideTvchannelBinding
import com.volkov.EPGConfig
import com.volkov.epgrecycler.dpToPx
import dev.androidbroadcast.vbpd.viewBinding

class TvGuideChannelLogosViewholder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val binding by viewBinding(RvItemTvguideTvchannelBinding::bind)

    fun bind(item: TvChannelWithEpg) {

        val tvchannelPos = item.tvChannelPosition
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

        binding.root.updateLayoutParams<RecyclerView.LayoutParams> {
            height = EPGConfig.rowLogoHeight.dpToPx
            marginStart = EPGConfig.marginHorizontalChannelLogo.dpToPx
            marginEnd = EPGConfig.marginHorizontalChannelLogo.dpToPx
            topMargin = EPGConfig.marginVerticalChannelLogo.dpToPx
            bottomMargin = EPGConfig.marginVerticalChannelLogo.dpToPx
        }
    }
}