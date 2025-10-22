package com.example.mj_player_tv.ui.epg

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.VgItemEpgTvchannelBinding
import com.example.mj_player_tv.databinding.VgItemEpgRowBinding

class EpgChannelAdapter(
    private val epgManager: EpgManager
) : ListAdapter<TvChannelWithEpg, EpgChannelAdapter.ViewHolder>(ChannelDiffCallback()) {

    class ViewHolder(val binding: VgItemEpgRowBinding) : RecyclerView.ViewHolder(binding.root) {
        var programAdapter: EpgProgramAdapter? = null // Wiederverwendung

        fun bind(item: TvChannelWithEpg) {
            val channelPosition = item.tvChannelPosition
            val tvChannel = channelPosition.tvchannel.target
            val epgLogo = tvChannel.epgLogo
            val playlistLogo = tvChannel.logo
            val linkedEpgChannel = tvChannel.linkedEpgChannel?.target
            binding.tvChannelname.text = tvChannel.showingName

            if (tvChannel.account.target!!.useEpgLogos) {
                if (epgLogo.isNotEmpty() && (linkedEpgChannel?.isExternalEpg == true || tvChannel.alwaysUsesExternalEpg)) {
                    binding.ivChannelLogo.visibility = View.VISIBLE
                    binding.ivChannelLogo.load(epgLogo)
                } else {
                    if (playlistLogo.isNotEmpty()) {
                        binding.ivChannelLogo.visibility = View.VISIBLE
                        binding.ivChannelLogo.load(playlistLogo)
                    } else {
                        binding.ivChannelLogo.visibility = View.INVISIBLE
                    }
                }
            } else {
                if (playlistLogo.isNotEmpty()) {
                    binding.ivChannelLogo.visibility = View.VISIBLE
                    binding.ivChannelLogo.load(playlistLogo)
                } else {
                    binding.ivChannelLogo.visibility = View.INVISIBLE
                }
            }
        }
    }

    class ChannelDiffCallback : DiffUtil.ItemCallback<TvChannelWithEpg>() {
        override fun areItemsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = VgItemEpgRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = getItem(position)
        holder.bind(channel)
            // Erstelle oder wiederverwende ProgramItemAdapter
        if (holder.programAdapter == null) {
            holder.programAdapter = EpgProgramAdapter(epgManager, channel.tvChannelPosition.tvchannel.target.showingName)
            holder.binding.rvChannelPrograms.adapter = holder.programAdapter
        }

            // Aktualisiere Programme
        val programs = epgManager.getProgramsForChannel(
            channel.id
        )
        holder.programAdapter?.submitList(programs)
        holder.binding.rvChannelPrograms.setChannel(channel)
        holder.binding.rvChannelPrograms.setEpgManager(epgManager)
            // Synchronisiere Scroll-Position (wie Egeniq's resetScroll)
        holder.binding.rvChannelPrograms.scrollTo(epgManager.getTimeLineRowScrollOffset(), 0)

    }

    fun updateChannels(newChannels: List<TvChannelWithEpg>) {
        submitList(newChannels)
    }
}