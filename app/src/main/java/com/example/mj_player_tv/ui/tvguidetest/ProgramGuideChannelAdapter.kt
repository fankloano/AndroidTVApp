package com.example.mj_player_tv.ui.tvguidetest

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.RvItemTvguideTvchannelBinding
import com.example.mj_player_tv.databinding.RvItemTvguideTvepgBinding
import com.example.mj_player_tv.utils.EpgScrollSyncManager

class ProgramGuideChannelAdapter(
    private val manager: ProgramGuideManager,
    private val scrollSyncHelper: EpgScrollSyncManager
) : ListAdapter<TvChannelWithEpg, ProgramGuideChannelViewHolder>(DIFF_CALLBACK) {


    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TvChannelWithEpg>() {
            override fun areItemsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg) =
                oldItem.tvChannelPosition.catAndChannelAccount == newItem.tvChannelPosition.catAndChannelAccount

            override fun areContentsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg) =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgramGuideChannelViewHolder {
        val binding = RvItemTvguideTvepgBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProgramGuideChannelViewHolder(binding, manager)
    }

    override fun onBindViewHolder(holder: ProgramGuideChannelViewHolder, position: Int) {
        holder.bind(getItem(position), scrollSyncHelper)
        holder.binding.constTvchannel.isFocusable = false
    }
}
