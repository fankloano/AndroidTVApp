package com.example.mj_player_tv.ui.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.RvItemTvguideTvepgBinding
import com.example.mj_player_tv.utils.views.ProgramsRecyclerView

class ChannelAdapter(
    private val onProgramClick: (EpgDataOB) -> Unit
) : ListAdapter<TvChannelWithEpg, ChannelAdapter.ChannelViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TvChannelWithEpg>() {
            override fun areItemsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg): Boolean =
                oldItem == newItem
        }
    }

    inner class ChannelViewHolder(
        val binding: RvItemTvguideTvepgBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val programsAdapter = ProgramsAdapter(onProgramClick)

        fun bind(tvChannelWithEpg: TvChannelWithEpg) {
            // Channel Name + Logo
            val tvchannelPos = tvChannelWithEpg.tvChannelPosition
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


            binding.rvChannelPrograms.layoutManager =
                LinearLayoutManager(binding.root.context, LinearLayoutManager.HORIZONTAL, false)
            binding.rvChannelPrograms.adapter = programsAdapter
            programsAdapter.submitList(tvChannelWithEpg.epgList)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = RvItemTvguideTvepgBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
