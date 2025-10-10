package com.example.mj_player_tv.ui.epg

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.databinding.VgItemEpgTvchannelBinding

// ChannelAdapter.kt

class EpgChannelAdapter : ListAdapter<ChannelPositions, EpgChannelAdapter.ChannelViewHolder>(ChannelDiffCallback()) {

    class ChannelViewHolder(private val binding: VgItemEpgTvchannelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // **KRITISCH:** Verhindere, dass der Kanalname/Logo den Fokus erhält.
            binding.root.isFocusable = false
            binding.root.isFocusableInTouchMode = false

        }

        fun bind(item: ChannelPositions) {
            val tvChannel = item.tvchannel.target
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = VgItemEpgTvchannelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChannelDiffCallback : DiffUtil.ItemCallback<ChannelPositions>() {
        override fun areItemsTheSame(oldItem: ChannelPositions, newItem: ChannelPositions): Boolean {
            // Prüfung über die ID, da dies das eindeutige Merkmal ist
            return oldItem.catAndChannelAccount == newItem.catAndChannelAccount
        }

        override fun areContentsTheSame(oldItem: ChannelPositions, newItem: ChannelPositions): Boolean {
            // Prüfung, ob sich die sichtbaren Inhalte geändert haben
            return oldItem == newItem
        }
    }
}