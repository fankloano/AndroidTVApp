package com.example.mj_player_tv.ui.tvguide.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.ui.tvguide.viewholders.TvGuideChannelLogosViewholder
import com.volkov.epgrecycler.getView

class TvGuideChannelLogosAdapter : ListAdapter<TvChannelWithEpg, TvGuideChannelLogosViewholder>(DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TvGuideChannelLogosViewholder {
        return TvGuideChannelLogosViewholder(getView(parent, R.layout.rv_item_tvguide_tvchannel))
    }

    override fun onBindViewHolder(
        holder: TvGuideChannelLogosViewholder,
        position: Int
    ) {
        val item = getItem(position)
        holder.bind(item)
    }
}