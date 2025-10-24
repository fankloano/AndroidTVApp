package com.example.mj_player_tv.ui.epg

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.VgItemEpgRowBinding
import com.example.mj_player_tv.ui.tvguide.EpgItemDecoration

class EpgChannelAdapter(
    private val epgManager: EpgManager
) : RecyclerView.Adapter<EpgChannelAdapter.ViewHolder>() {

    private val channels = mutableListOf<TvChannelWithEpg>()

    fun setChannels(newChannels: List<TvChannelWithEpg>) {
        channels.clear()
        channels.addAll(newChannels)
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: VgItemEpgRowBinding) : RecyclerView.ViewHolder(binding.root) {
        var programAdapter: EpgProgramAdapter? = null

        fun bind(channel: TvChannelWithEpg) {
            val tvChannel = channel.tvChannelPosition.tvchannel.target
            binding.tvChannelname.text = tvChannel.showingName

            // Logo setzen
            val logo = if (tvChannel.account.target!!.useEpgLogos && tvChannel.epgLogo.isNotEmpty()) {
                tvChannel.epgLogo
            } else {
                tvChannel.logo
            }

            if (logo.isNotEmpty()) {
                binding.ivChannelLogo.visibility = View.VISIBLE
                binding.ivChannelLogo.load(logo)
            } else {
                binding.ivChannelLogo.visibility = View.INVISIBLE
            }

            // Adapter erstellen, falls null
            if (programAdapter == null) {
                programAdapter = EpgProgramAdapter(epgManager, tvChannel.showingName)
                binding.rvChannelPrograms.adapter = programAdapter
                binding.rvChannelPrograms.addItemDecoration(
                    EpgItemDecoration(binding.rvChannelPrograms.context.getColor(R.color.background_dark))
                )
            }

            // Programme synchron setzen
            val programs = epgManager.getProgramsForChannel(channel.id)
            programAdapter?.setPrograms(programs)

            // Channel + EpgManager setzen
            binding.rvChannelPrograms.setChannel(channel)
            binding.rvChannelPrograms.setEpgManager(epgManager)
            binding.rvChannelPrograms.post {
                binding.rvChannelPrograms.resetScroll(epgManager.getTimeLineRowScrollOffset())
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = VgItemEpgRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = channels[position]
        holder.bind(channel)
    }

    override fun getItemCount(): Int = channels.size
}
