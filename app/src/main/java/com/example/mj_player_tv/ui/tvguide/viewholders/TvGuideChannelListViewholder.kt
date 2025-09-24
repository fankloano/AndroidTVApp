package com.example.mj_player_tv.ui.tvguide.viewholders

import android.util.Log
import android.view.View
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.RvItemTvguideChannellistBinding
import com.example.mj_player_tv.databinding.RvItemTvguideTvchannelBinding
import com.example.mj_player_tv.ui.tvguide.adapters.TvGuideEpgAdapter
import com.example.mj_player_tv.utils.views.RecyclerWithPositionView
import com.volkov.EPGConfig
import com.volkov.epg_recycler.databinding.ItemChannelBinding
import com.volkov.epgrecycler.EPGUtils
import com.volkov.epgrecycler.dpToPx
import dev.androidbroadcast.vbpd.viewBinding
import org.joda.time.DateTime

class TvGuideChannelListViewholder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val binding by viewBinding(RvItemTvguideChannellistBinding::bind)
    private val epgAdapter = TvGuideEpgAdapter()

    private val horizontalRecyclerView: RecyclerWithPositionView
        get() = binding.root

    fun bind(
        item: TvChannelWithEpg,
        horizontalScrollListener: RecyclerView.OnScrollListener
    ) { // 1. Tag setzen (mit Ihrer neuen ID)
        binding.root.tag = "channel_${item.tvChannelPosition.catAndChannelAccount}"

        Log.d("TVGUIDE CHANNELLISTADAPTER", "CHANNELLISTTAG: channel_${item.tvChannelPosition.catAndChannelAccount}")

        // 2. Layout-Parameter setzen
        binding.root.updateLayoutParams<RecyclerView.LayoutParams> {
            height = EPGConfig.rowHeight.dpToPx
            topMargin = EPGConfig.marginVerticalChannelLogo.dpToPx
            bottomMargin = EPGConfig.marginVerticalChannelLogo.dpToPx
        }

        // 3. Horizontalen RecyclerView einrichten
        // Sie müssen den Adapter nur einmal einrichten, nicht bei jedem Bind
        horizontalRecyclerView.apply {
            val lm = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = epgAdapter
            layoutManager = lm
            setHasFixedSize(false)
            this.scrollListener = horizontalScrollListener
        }

        epgAdapter.channelId = item.tvChannelPosition.catAndChannelAccount
        // 4. Daten an den EPG-Adapter übergeben
        epgAdapter.submitList(item.epgList) {
            // 5. Zur aktuellen Zeit scrollen
            horizontalRecyclerView.post {
                // Die Logik für die Scroll-Position muss noch angepasst werden
                // um mit Ihren Long-Zeitstempeln zu arbeiten.
                val nowInSeconds = System.currentTimeMillis() / 1000
                val startEpgInSeconds = EPGUtils.startTime.millis / 1000
                val scrollOffset = EPGUtils.getCellWidth(DateTime(startEpgInSeconds), DateTime(nowInSeconds))
                horizontalRecyclerView.scrollHorizontallyTo(scrollOffset)
            }
        }
    }
}