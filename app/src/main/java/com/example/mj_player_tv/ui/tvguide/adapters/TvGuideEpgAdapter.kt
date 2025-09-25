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
            if (payload is EpgDataOB) {
                // Rufe die updateProgress-Methode des ViewHolders auf, um nur den Fortschritt zu aktualisieren
                (holder as? TvGuideEpgViewholder)?.updateProgress(payload)
            }
        } else {
            // If there are no payloads, do the full initial binding
            val item = getItem(position)
            when (holder) {
                is TvGuideEpgViewholder -> {
                    holder.bind(item)
                    holder.updateUiState(item) // Stellt den Hintergrund und die Sichtbarkeit ein
                    holder.updateProgress(item) // Fortschritt immer aktualisieren
                }
                is TvGuideEmptyShowViewholder -> holder.bind(item)
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