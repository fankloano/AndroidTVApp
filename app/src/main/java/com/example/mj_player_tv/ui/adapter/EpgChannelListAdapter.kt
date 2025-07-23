package com.example.mj_player_tv.ui.adapter


import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.database.entity.EpgSourceChannel
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.RvItemAssignChannelToEpgBinding
import com.example.mj_player_tv.ui.AssingChannelToEpgFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel


@UnstableApi
class EpgChannelListAdapter(private val onClickListener: EpgChannelListAdapter.OnClickListener, private val helpViewModel: HelpViewModel, private var selectedChannel: TvChannelOB, private val fragment: AssingChannelToEpgFragment) : ListAdapter<EpgSourceChannel, EpgChannelListAdapter.ViewHolder>(
    MANAGE_EPGLIST_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemAssignChannelToEpgBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(epgChannel: EpgSourceChannel) {
            binding.apply {
                tvEpgChannel.text = epgChannel.name
                val image = epgChannel.icon?.firstOrNull()
                if (!image.isNullOrEmpty()) {
                    ivEpgLogoImage.load(image)
                } else {
                    ivEpgLogoImage.visibility = View.INVISIBLE
                }

                binding.relLayoutItemManage.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.setFocusToRightMenu()
                        return@setOnKeyListener true
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.setFocusToTvChannels()
                        return@setOnKeyListener true
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        fragment.closeFragment()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemAssignChannelToEpgBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)
        return viewHolder
    }

    @UnstableApi
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val epgChannel = getItem(position)!!
        holder.bind(epgChannel)

        val cbAssignEpg = holder.binding.cbEpgChannels
        cbAssignEpg.isChecked = selectedChannel.linkedEpgChannel?.target?.chEpgId == epgChannel.chEpgId
        // Klickereignis für die CheckBox behandeln
        holder.itemView.setOnClickListener {
            if (!cbAssignEpg.isChecked) {
                if (epgChannel.isExternalEpg) {
                    selectedChannel.linkedEpgChannel?.target = epgChannel
                    selectedChannel.usesExternalEpg = true
                } else {
                    if (epgChannel.chEpgId != selectedChannel.epgChannel?.target?.chEpgId) {
                        selectedChannel.linkedEpgChannel?.target = epgChannel
                        selectedChannel.usesExternalEpg = true
                        selectedChannel.linkedEpgChannel?.target = epgChannel
                    } else {
                        selectedChannel.usesExternalEpg = false
                        selectedChannel.usesPlaylistEpg = true
                        selectedChannel.epgChannel?.target = epgChannel
                    }
                }
                cbAssignEpg.isChecked = if (selectedChannel.usesExternalEpg) {
                    selectedChannel.linkedEpgChannel?.target?.chEpgId == epgChannel.chEpgId
                } else {
                    selectedChannel.epgChannel?.target?.chEpgId == epgChannel.chEpgId
                }
                helpViewModel.currentFocusedChannel = selectedChannel
                notifyDataSetChanged()
                onClickListener.onClick(epgChannel, position, true)
            } else {
                    helpViewModel.currentFocusedChannel = selectedChannel
                    notifyDataSetChanged()
                    onClickListener.onClick(epgChannel, position, false)
            }
        }

        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                epgChannel.epgsource.target?.let { fragment.showEpgSourceNameWhenAll(it) }
            }
        }
    }

    companion object {
        private val MANAGE_EPGLIST_COMPERATOR = object : DiffUtil.ItemCallback<EpgSourceChannel>() {
            override fun areItemsTheSame(oldItem: EpgSourceChannel, newItem: EpgSourceChannel) =
                oldItem.chEpgId == newItem.chEpgId


            override fun areContentsTheSame(oldItem: EpgSourceChannel, newItem: EpgSourceChannel) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (epgChannel: EpgSourceChannel, position: Int, isChecked: Boolean) -> Unit) {
        fun onClick(epgChannel: EpgSourceChannel, position: Int, isChecked: Boolean) = clickListener(epgChannel, position, isChecked)
    }

    fun updateChannel(newChannel: TvChannelOB) {
        selectedChannel = newChannel
        notifyDataSetChanged()
    }
}