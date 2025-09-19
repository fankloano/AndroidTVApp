package com.example.mj_player_tv.ui.tvguide.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.ui.tvguide.DiffCallback
import com.example.mj_player_tv.ui.tvguide.viewholders.TvGuideChannelListViewholder
import com.example.mj_player_tv.utils.Common.getView

class TvGuideChannelListAdapter(
    private val horizontalScrollListener: RecyclerView.OnScrollListener
) : ListAdapter<TvChannelWithEpg, TvGuideChannelListViewholder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TvGuideChannelListViewholder {
        return TvGuideChannelListViewholder(getView(parent, R.layout.rv_item_tvguide_channellist))
    }

    override fun onBindViewHolder(
        holder: TvGuideChannelListViewholder, // Typ ist hier spezifisch!
        position: Int
    ) {
        holder.setIsRecyclable(false)
        holder.bind(
            getItem(position),
            horizontalScrollListener,
        )
    }
}