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
import com.example.mj_player_tv.databinding.RvItemHistoryTvchannelBinding
import com.example.mj_player_tv.databinding.RvItemHistoryVodBinding
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.ui.WatchHistoryFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class WatchHistoryTvChannelsAdapter(
    private val onClickListener: WatchHistoryTvChannelsAdapter.OnClickListener,
    private val onLongClickListener: WatchHistoryTvChannelsAdapter.OnLongClickListener,
    private val fragment: WatchHistoryFragment,
    private val helpViewModel: HelpViewModel) : ListAdapter<TvChannelOB, WatchHistoryTvChannelsAdapter.ViewHolder>(
    MANAGE_TVCHANNELS_COMPERATOR) {

    var currentAccount: Accounts? = null

    inner class ViewHolder(val binding: RvItemHistoryTvchannelBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tvchannel: TvChannelOB) {
            binding.apply {

                tvChannelname.text = tvchannel.showingName
                tvChannelcategory.text = "[ ${tvchannel.reltvcategory.target.showingName} ]"

                tvAccount.text = tvchannel.account.target.name

                val linkedEpgChannel = tvchannel.linkedEpgChannel?.target

                val image = tvchannel.logo
                val epgLogo = linkedEpgChannel?.icon?.firstOrNull()

                if (tvchannel.account.target!!.useEpgLogos) {
                    if (!epgLogo.isNullOrEmpty() && (linkedEpgChannel.isExternalEpg || tvchannel.alwaysUsesExternalEpg)) {
                        ivTvchannelImage.visibility = View.VISIBLE
                        ivTvchannelImage.load(epgLogo)
                    } else {
                        if (image.isNotEmpty()) {
                            ivTvchannelImage.visibility = View.VISIBLE
                            ivTvchannelImage.load(image)
                        } else {
                            ivTvchannelImage.visibility = View.INVISIBLE
                        }
                    }
                } else {
                    if (image.isNotEmpty()) {
                        ivTvchannelImage.visibility = View.VISIBLE
                        ivTvchannelImage.load(image)
                    } else {
                        ivTvchannelImage.visibility = View.INVISIBLE
                    }
                }

                if (tvchannel.isFavorite) {
                    ivTvchannelFavorite.visibility = View.VISIBLE
                } else {
                    ivTvchannelFavorite.visibility = View.INVISIBLE
                }

                tvPlayingtimeCount.text = formatWatchTime(tvchannel.timeWatched)

                binding.cardViewTv.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        helpViewModel.currentSelectedWatchHistory = "TV"
                        fragment.focusToMovieOrSerie()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition < 4) {
                        helpViewModel.currentSelectedWatchHistory = "TV"
                        fragment.focusToMovieOrSerie()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }
            }
        }

        fun formatWatchTime(secondsTotal: Long): String {
            val hours = secondsTotal / 3600
            val minutes = (secondsTotal % 3600) / 60
            val seconds = secondsTotal % 60

            return buildString {
                if (hours > 0) append("${hours}h ")
                if (minutes > 0 || hours > 0) append("${minutes}min ")
                append("${seconds}sec")
            }.trim()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemHistoryTvchannelBinding.inflate(
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
        holder.binding.cardViewTv.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvChannelname.isSelected = hasFocus
            holder.binding.tvPlayingtime.isSelected = hasFocus
            holder.binding.tvPlayingtimeCount.isSelected = hasFocus
            holder.binding.tvAccount.isSelected = hasFocus
            holder.binding.tvChannelcategory.isSelected = hasFocus
            if (hasFocus) {
                holder.binding.overlayFull.visibility = View.GONE
            } else {
                holder.binding.overlayFull.visibility = View.VISIBLE
            }
        }

        holder.binding.cardViewTv.setOnClickListener {
            onClickListener.onClick(channelPos)
        }

        holder.binding.cardViewTv.setOnLongClickListener {
            onLongClickListener.onLongClick(channelPos, holder.binding.cardViewTv)
            true
        }

    }

    companion object {
        private val MANAGE_TVCHANNELS_COMPERATOR = object : DiffUtil.ItemCallback<TvChannelOB>() {
            override fun areItemsTheSame(oldItem: TvChannelOB, newItem: TvChannelOB) =
                oldItem.idByAccountData == newItem.idByAccountData


            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: TvChannelOB, newItem: TvChannelOB) =
                oldItem.id == newItem.id
        }
    }

    class OnClickListener(val clickListener: (tvchannel: TvChannelOB) -> Unit) {
        fun onClick(tvchannel: TvChannelOB) = clickListener(tvchannel)
    }

    class OnLongClickListener(val onlongclickListener: (tvchannel: TvChannelOB, view: View) -> Unit) {
        fun onLongClick(tvchannel: TvChannelOB, view: View) = onlongclickListener(tvchannel, view)
    }

}