package com.example.mj_player_tv.ui.adapter


import android.annotation.SuppressLint
import android.util.Log
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
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.help.Movie
import com.example.mj_player_tv.databinding.RvItemGlobalsearchEpgBinding
import com.example.mj_player_tv.databinding.RvItemGlobalsearchTvchannelBinding
import com.example.mj_player_tv.databinding.RvItemMoviesBinding
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.ui.MoviesFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class GlobalSearchEpgAdapter(private val fragment: GlobalSearchFragment, private val helpViewModel: HelpViewModel) : ListAdapter<ChannelPositions, GlobalSearchEpgAdapter.ViewHolder>(
    MANAGE_TVCHANNELS_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemGlobalsearchEpgBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tvchannelPos: ChannelPositions) {
            binding.apply {
                val tvchannel = tvchannelPos.tvchannel.target
                programName.text = tvchannel.showingName

                val linkedEpgChannel = tvchannel.linkedEpgChannel?.target
                val image = tvchannel.logo
                val epgLogo = linkedEpgChannel?.icon?.firstOrNull()
                if (tvchannel.account.target!!.useEpgLogos) {
                    if (!epgLogo.isNullOrEmpty() && (linkedEpgChannel.isExternalEpg || tvchannel.alwaysUsesExternalEpg)) {
                        ivProgram.visibility = View.VISIBLE
                        ivProgram.load(epgLogo)
                    } else {
                        if (image.isNotEmpty()) {
                            ivProgram.visibility = View.VISIBLE
                            ivProgram.load(image)
                        } else {
                            ivProgram.visibility = View.INVISIBLE
                        }
                    }
                } else {
                    if (image.isNotEmpty()) {
                        ivProgram.visibility = View.VISIBLE
                        ivProgram.load(image)
                    } else {
                        ivProgram.visibility = View.INVISIBLE
                    }
                }

                binding.cardviewEpg.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition == 0) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    if ((keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToEpgList()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemGlobalsearchEpgBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val viewHolder = ViewHolder(binding)
        return viewHolder
    }

    @UnstableApi
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tvchannel = getItem(position)!!
        holder.bind(tvchannel)
        holder.binding.cardviewEpg.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.programName.isSelected = hasFocus
            if (hasFocus) {
                fragment.showEpgList(tvchannel)
                holder.binding.overlayFull.visibility = View.GONE
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
}