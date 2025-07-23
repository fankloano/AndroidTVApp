package com.example.mj_player_tv.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.help.PlaylistUpdate
import com.example.mj_player_tv.databinding.RvItemEpgsourceTimeoffsetBinding
import com.example.mj_player_tv.databinding.RvItemPlaylistupdateBinding
import com.example.mj_player_tv.repository.PlaylistUpdateProcessState
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.PlaylistUpdateViewModel

class PlaylistUpdateAdapter(private val playlistUpdateViewModel: PlaylistUpdateViewModel) : ListAdapter<PlaylistUpdate, PlaylistUpdateAdapter.ViewHolder>(
    PL_UPDATE_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemPlaylistupdateBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(playlist: PlaylistUpdate) {
            binding.apply {
                tvCurrentAccount.text = playlist.playlistName
                val status = playlist.playlistStatus
                when (status) {
                    is PlaylistUpdateProcessState.CurrentAccount -> {
                        tvStatus.text = "UPDATING"
                    }
                    is PlaylistUpdateProcessState.NoData -> {
                        tvStatus.text = "NO DATA RECEIVED"
                        ivPlUpdateIcon.isActivated = true
                        ivPlUpdateIcon.isSelected = false
                        playlistUpdateViewModel.removePlaylistAfterDelay(playlist.playlistName)
                    }
                    is PlaylistUpdateProcessState.Success -> {
                        tvStatus.text = "SUCCESS"
                        ivPlUpdateIcon.isSelected = true
                        ivPlUpdateIcon.isActivated = false
                        playlistUpdateViewModel.removePlaylistAfterDelay(playlist.playlistName)
                    }
                    is PlaylistUpdateProcessState.Error -> {
                        tvStatus.text = "ERROR"
                        ivPlUpdateIcon.isActivated = true
                        ivPlUpdateIcon.isSelected = false
                        playlistUpdateViewModel.removePlaylistAfterDelay(playlist.playlistName)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemPlaylistupdateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)

        return viewHolder
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playlistName = getItem(position)!!
        holder.bind(playlistName)
    }

    companion object {
        private val PL_UPDATE_COMPERATOR = object : DiffUtil.ItemCallback<PlaylistUpdate>() {
            override fun areItemsTheSame(oldItem: PlaylistUpdate, newItem: PlaylistUpdate) =
                oldItem == newItem


            override fun areContentsTheSame(oldItem: PlaylistUpdate, newItem: PlaylistUpdate) =
                oldItem == newItem &&
                        oldItem.playlistStatus == newItem.playlistStatus &&
                        oldItem.playlistName == newItem.playlistName
        }
    }

    class OnClickListener(val clickListener: (timeOffSet: String, position: Int) -> Unit) {
        fun onClick(timeOffSet: String, position: Int) = clickListener(timeOffSet, position)
    }
}