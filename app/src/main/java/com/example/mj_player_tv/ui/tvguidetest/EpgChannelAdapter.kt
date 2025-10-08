package com.example.mj_player_tv.ui.tvguidetest

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.RvItemTvguideTvepgBinding

class EpgChannelAdapter(
    private val epgRecyclerView: EpgRecyclerview
) : ListAdapter<TvChannelWithEpg, EpgChannelAdapter.ChannelViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TvChannelWithEpg>() {
            override fun areItemsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg): Boolean {
                return oldItem.tvChannelPosition.catAndChannelAccount == newItem.tvChannelPosition.catAndChannelAccount
            }

            override fun areContentsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg): Boolean {
                return oldItem == newItem
            }
        }
    }

    inner class ChannelViewHolder(val binding: RvItemTvguideTvepgBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = RvItemTvguideTvepgBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = getItem(position)
        holder.binding.tvChannelname.text = channel.tvChannelPosition.tvchannel.target.showingName
        val epgAdapter = EpgDataAdapter(epgRecyclerView)
        // Horizontal RecyclerView für Sendungen
        holder.binding.rvChannelPrograms.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = epgAdapter
        }
        epgAdapter.channelId = channel.tvChannelPosition.catAndChannelAccount
        channel.epgList.forEachIndexed { index, show ->
            Log.d("NEW EPG GUIDE", "CHANNEL: ${channel.tvChannelPosition.tvchannel.target.showingName} = INDEX: $index =  EPG: ${show.name}")

        }
        epgAdapter.submitList(channel.epgList)
    }
}
