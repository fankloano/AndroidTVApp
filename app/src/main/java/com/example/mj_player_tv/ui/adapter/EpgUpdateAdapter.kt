package com.example.mj_player_tv.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.help.EpgUpdate
import com.example.mj_player_tv.database.help.PlaylistUpdate
import com.example.mj_player_tv.databinding.RvItemEpgsourceTimeoffsetBinding
import com.example.mj_player_tv.databinding.RvItemPlaylistupdateBinding
import com.example.mj_player_tv.repository.EpgUpdateProcessState
import com.example.mj_player_tv.repository.PlaylistUpdateProcessState
import com.example.mj_player_tv.viewmodel.EpgUpdateViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.PlaylistUpdateViewModel

class EpgUpdateAdapter(private val epgUpdateViewModel: EpgUpdateViewModel) : ListAdapter<EpgUpdate, EpgUpdateAdapter.ViewHolder>(
    PL_UPDATE_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemPlaylistupdateBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(epgSource: EpgUpdate) {
            binding.apply {
                tvCurrentAccount.text = epgSource.epgName
                val status = epgSource.epgStatus
                when (status) {
                    is EpgUpdateProcessState.CurrentAccount -> {
                        tvStatus.text = "UPDATING"
                    }
                    is EpgUpdateProcessState.NoData -> {
                    }
                    is EpgUpdateProcessState.Success -> {
                        tvStatus.text = "SUCCESS"
                        ivPlUpdateIcon.isSelected = true
                        ivPlUpdateIcon.isActivated = false
                        epgUpdateViewModel.removeEpgAfterDelay(epgSource.epgName)
                    }
                    is EpgUpdateProcessState.Error -> {
                        tvStatus.text = "ERROR"
                        ivPlUpdateIcon.isActivated = true
                        ivPlUpdateIcon.isSelected = false
                        epgUpdateViewModel.removeEpgAfterDelay(epgSource.epgName)
                    }

                    EpgUpdateProcessState.Loading -> {

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
        private val PL_UPDATE_COMPERATOR = object : DiffUtil.ItemCallback<EpgUpdate>() {
            override fun areItemsTheSame(oldItem: EpgUpdate, newItem: EpgUpdate) =
                oldItem == newItem


            override fun areContentsTheSame(oldItem: EpgUpdate, newItem: EpgUpdate) =
                oldItem == newItem &&
                        oldItem.epgStatus == newItem.epgStatus &&
                        oldItem.epgName == newItem.epgName
        }
    }

    class OnClickListener(val clickListener: (timeOffSet: String, position: Int) -> Unit) {
        fun onClick(timeOffSet: String, position: Int) = clickListener(timeOffSet, position)
    }
}