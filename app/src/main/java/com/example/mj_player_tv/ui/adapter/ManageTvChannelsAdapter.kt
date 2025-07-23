package com.example.mj_player_tv.ui.adapter


import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.RvItemManageTvchannelsBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel

class ManageTvChannelsAdapter(private val onClickListener: ManageTvChannelsAdapter.OnClickListener, private val helpViewModel: HelpViewModel) : ListAdapter<ChannelPositions, ManageTvChannelsAdapter.ViewHolder>(
    MANAGE_TVCHANNELS_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemManageTvchannelsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tvchannelPos: ChannelPositions) {
            binding.apply {
                val tvchannel = tvchannelPos.tvchannel.target
                tvTvChannels.text = tvchannel.showingName

                ivNew.visibility = if (tvchannel.newChannel) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                binding.relLayoutItemManage.setOnClickListener {
                    cbTvChannels.isChecked = !cbTvChannels.isChecked
                    tvchannelPos.isSelected = cbTvChannels.isChecked
                    onClickListener.onClick(tvchannelPos)
                }
            }
            binding.relLayoutItemManage.setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemManageTvchannelsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)

        if (viewType == 0) {
            // Setze den Fokus auf das erste Element
            viewHolder.itemView.nextFocusUpId = R.id.btn_selectAll
        }

        return viewHolder
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tvChannel = getItem(position)!!
        holder.bind(tvChannel)
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = true

        holder.binding.relLayoutItemManage.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvTvChannels.isSelected = hasFocus
        }

        val checkbox = holder.binding.cbTvChannels
        checkbox.isChecked = tvChannel.isSelected
        checkbox.isActivated = tvChannel.isSelected
        checkbox.setOnClickListener {
            if (checkbox.isChecked) {
                tvChannel.isSelected = true
                helpViewModel.currentFocusedChannPosition = tvChannel
                onClickListener.onClick(tvChannel)
            }
            if (!checkbox.isChecked) {
                tvChannel.isSelected = false
                helpViewModel.currentFocusedChannPosition = tvChannel
                onClickListener.onClick(tvChannel)
            }
        }
    }

    companion object {
        private val MANAGE_TVCHANNELS_COMPERATOR = object : DiffUtil.ItemCallback<ChannelPositions>() {
            override fun areItemsTheSame(oldItem: ChannelPositions, newItem: ChannelPositions) =
                oldItem.catAndChannelAccount == newItem.catAndChannelAccount


            override fun areContentsTheSame(oldItem: ChannelPositions, newItem: ChannelPositions) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (tvChannel: ChannelPositions) -> Unit) {
        fun onClick(tvChannel: ChannelPositions) = clickListener(tvChannel)
    }
}