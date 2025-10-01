package com.example.mj_player_tv.ui.tvguide.viewholders

import android.util.Log
import android.view.View
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.RvItemTvguideChannellistBinding
import com.example.mj_player_tv.ui.tvguide.EpgItemDecoration
import com.example.mj_player_tv.ui.tvguide.adapters.TvGuideEpgAdapter
import com.example.mj_player_tv.utils.views.RecyclerWithPositionView
import com.volkov.EPGConfig
import com.volkov.epgrecycler.EPGUtils
import com.volkov.epgrecycler.dpToPx
import dev.androidbroadcast.vbpd.viewBinding
import org.joda.time.DateTime

class TvGuideChannelListViewholder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val binding by viewBinding(RvItemTvguideChannellistBinding::bind)
    private val epgAdapter = TvGuideEpgAdapter()

    private val horizontalRecyclerView: RecyclerWithPositionView
        get() = binding.root

    init {
        horizontalRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = epgAdapter
            setHasFixedSize(false)
            addItemDecoration(
                EpgItemDecoration(color = context.getColor(R.color.background_dark))
            )
        }
    }

    fun syncHorizontalScroll(offset: Int) {
        horizontalRecyclerView.scrollTo(offset, 0) // absoluter Offset
    }

    fun bind(item: TvChannelWithEpg, getCurrentHorizontalOffset: () -> Int) {
        val tag = "channel_${item.tvChannelPosition.catAndChannelAccount}"
        binding.root.tag = tag
        Log.d("CHANNEL_BIND", "Binding ViewHolder tag=$tag position=$bindingAdapterPosition epgSize=${item.epgList.size}")

        // Layout-Parameter
        binding.root.updateLayoutParams<RecyclerView.LayoutParams> {
            height = EPGConfig.rowHeight.dpToPx
            topMargin = EPGConfig.marginVerticalChannelLogo.dpToPx
            bottomMargin = EPGConfig.marginVerticalChannelLogo.dpToPx
        }

        // Adapter-Daten setzen
        epgAdapter.channelId = item.tvChannelPosition.catAndChannelAccount
        epgAdapter.submitList(item.epgList) {
            binding.rvShows.apply {
                post {
                    val offset = getCurrentHorizontalOffset()
                    scrollHorizontallyTo(offset)
                }
            }
        }
    }
}
