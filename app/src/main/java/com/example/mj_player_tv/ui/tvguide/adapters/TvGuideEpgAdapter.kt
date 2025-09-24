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
import org.joda.time.DateTime

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

    // Standard onBindViewHolder that is required
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // It's a good practice to simply pass the call to the payload version
        // This ensures the initial binding is handled correctly
        onBindViewHolder(holder, position, mutableListOf())
    }

    // This is the special onBindViewHolder for payloads that you already implemented
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val payload = payloads[0]
            if (payload is DateTime) {
                // If the payload is a DateTime, update the progress bar only
                (holder as? TvGuideEpgViewholder)?.updateProgress(getItem(position), payload)
            }
        } else {
            // If there are no payloads, do the full initial binding
            when (holder) {
                is TvGuideEpgViewholder -> holder.bind(getItem(position))
                is TvGuideEmptyShowViewholder -> holder.bind(getItem(position))
            }
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