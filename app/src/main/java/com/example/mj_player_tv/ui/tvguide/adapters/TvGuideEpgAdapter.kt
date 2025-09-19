package com.example.mj_player_tv.ui.tvguide.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.ui.tvguide.EpgDataOBDiffCallback
import com.example.mj_player_tv.ui.tvguide.viewholders.TvGuideEmptyShowViewholder
import com.example.mj_player_tv.ui.tvguide.viewholders.TvGuideEpgViewholder
import com.example.mj_player_tv.utils.Common.getView
import com.volkov.epgrecycler.adapters.models.DataModel

class TvGuideEpgAdapter() : ListAdapter<EpgDataOB, RecyclerView.ViewHolder>(EpgDataOBDiffCallback()) {

    var channelId = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SHOW -> TvGuideEpgViewholder(
                getView(parent, R.layout.rv_item_tvguide_epg),
                channelId = channelId
            )
            else -> TvGuideEmptyShowViewholder(getView(parent, R.layout.rv_item_tvguide_epg))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        holder.setIsRecyclable(false)
        when (holder) {
            is TvGuideEpgViewholder -> holder.bind(getItem(position))
            is TvGuideEmptyShowViewholder -> holder.bind(getItem(position))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            else -> VIEW_TYPE_SHOW
        }
    }

    companion object {
        private const val VIEW_TYPE_SHOW = 0
        private const val VIEW_TYPE_SHOW_EMPTY = 1
    }
}