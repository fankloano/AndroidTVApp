package com.example.mj_player_tv.ui.adapter


import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.RvItemGlobalsearchTvchannelBinding
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class GlobalSearchProgramsChannelAdapter(
    private val onChannelFocused: (ChannelPositions) -> Unit,
    private val onRightClicked: (Unit) -> Unit,
    private val fragment: GlobalSearchFragment
) : ListAdapter<ChannelPositions, GlobalSearchProgramsChannelAdapter.ViewHolder>(
    MANAGE_TVCHANNELS_COMPERATOR) {

    var currentAccount: Accounts? = null

    inner class ViewHolder(val binding: RvItemGlobalsearchTvchannelBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tvchannelPos: ChannelPositions) {
            binding.apply {
                val tvchannel = tvchannelPos.tvchannel.target

                tvChannel.text = tvchannel.showingName

                val linkedEpgChannel = tvchannel.linkedEpgChannel?.target

                val image = tvchannel.logo
                val epgLogo = linkedEpgChannel?.icon?.firstOrNull()

                binding.ivCatchup.visibility = if (tvchannel.enable_tv_archive == 1) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }

                if (tvchannel.account.target!!.useEpgLogos) {
                    if (!epgLogo.isNullOrEmpty() && (linkedEpgChannel.isExternalEpg || tvchannel.alwaysUsesExternalEpg)) {
                        ivtvChannel.visibility = View.VISIBLE
                        ivtvChannel.load(epgLogo)
                    } else {
                        if (image.isNotEmpty()) {
                            ivtvChannel.visibility = View.VISIBLE
                            ivtvChannel.load(image)
                        } else {
                            ivtvChannel.visibility = View.INVISIBLE
                        }
                    }
                } else {
                    if (image.isNotEmpty()) {
                        ivtvChannel.visibility = View.VISIBLE
                        ivtvChannel.load(image)
                    } else {
                        ivtvChannel.visibility = View.INVISIBLE
                    }
                }

                if (tvchannel.isFavorite) {
                    tvIsFavorite.visibility = View.VISIBLE
                } else {
                    tvIsFavorite.visibility = View.INVISIBLE
                }

                binding.cardviewTvchannel.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    if ((keyCode == KeyEvent.KEYCODE_DPAD_LEFT) && event.action == KeyEvent.ACTION_DOWN) {
                        return@setOnKeyListener true
                    }
                    if ((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) {
                        if (bindingAdapterPosition == 0) {
                            fragment.focusToPlaylist()
                            return@setOnKeyListener true
                        }
                    }
                    if ((keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && event.action == KeyEvent.ACTION_DOWN) {
                        onRightClicked.invoke(Unit)
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemGlobalsearchTvchannelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val viewHolder = ViewHolder(binding)
        return viewHolder
    }

    @UnstableApi
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channelPos = getItem(position)!!
        holder.bind(channelPos)
        holder.binding.cardviewTvchannel.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvChannel.isSelected = hasFocus
            if (hasFocus) {
                holder.binding.overlayFull.visibility = View.GONE
                onChannelFocused.invoke(channelPos)
            } else {
                holder.binding.overlayFull.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        private val MANAGE_TVCHANNELS_COMPERATOR = object : DiffUtil.ItemCallback<ChannelPositions>() {
            override fun areItemsTheSame(oldItem: ChannelPositions, newItem: ChannelPositions) =
                oldItem.catAndChannelAccount == newItem.catAndChannelAccount


            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: ChannelPositions, newItem: ChannelPositions) =
                oldItem.tvchannel.target.id == newItem.tvchannel.target.id
        }
    }

    class OnClickListener(val clickListener: (tvchannel: ChannelPositions) -> Unit) {
        fun onClick(tvchannel: ChannelPositions) = clickListener(tvchannel)
    }

}