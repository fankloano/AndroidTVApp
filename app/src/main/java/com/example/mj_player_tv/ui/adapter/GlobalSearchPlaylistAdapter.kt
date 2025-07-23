package com.example.mj_player_tv.ui.adapter


import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.databinding.RvItemGlobalsearchPlaylistBinding
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class GlobalSearchPlaylistAdapter(private val helpViewModel: HelpViewModel, private val fragment: GlobalSearchFragment) : ListAdapter<Accounts, GlobalSearchPlaylistAdapter.ViewHolder>(
    GLOBALSEARCH_TV_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemGlobalsearchPlaylistBinding, val helpViewModel: HelpViewModel) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: Accounts) {
            binding.apply {
                tvPlaylistname.text = data.name

                binding.constGsPl.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToTextView()
                        return@setOnKeyListener true
                    }

                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToItems()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemGlobalsearchPlaylistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val viewHolder = ViewHolder(binding, helpViewModel)
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val accountdata = getItem(position)!!
        holder.bind(accountdata)
        if (helpViewModel.currentSelectedGlobalSearchCategory == "TV") {
            holder.binding.constGsPl.isSelected = helpViewModel.currentGlobalSearchTvPlaylist?.id == accountdata.id
        } else if (helpViewModel.currentSelectedGlobalSearchCategory == "MOVIE") {
            holder.binding.constGsPl.isSelected = helpViewModel.currentGlobalSearchMoviePlaylist?.id == accountdata.id
        } else if (helpViewModel.currentSelectedGlobalSearchCategory == "SERIE") {
            holder.binding.constGsPl.isSelected = helpViewModel.currentGlobalSearchSeriePlaylist?.id == accountdata.id
        } else {
            holder.binding.constGsPl.isSelected = helpViewModel.currentGlobalSearchProgramPlaylist?.id == accountdata.id
        }
        holder.binding.constGsPl.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                if (helpViewModel.currentSelectedGlobalSearchCategory == "TV") {
                    fragment.showFocusedTvChannels(accountdata)
                } else if (helpViewModel.currentSelectedGlobalSearchCategory == "MOVIE") {
                    fragment.showFocusedMovies(accountdata)
                } else if (helpViewModel.currentSelectedGlobalSearchCategory == "SERIE") {
                    fragment.showFocusedSeries(accountdata)
                } else if (helpViewModel.currentSelectedGlobalSearchCategory == "EPG") {
                    fragment.showTvChannelsWithEpg(accountdata)
                } else {
                    return@setOnFocusChangeListener
                }
            }
        }
    }

    companion object {
        private val GLOBALSEARCH_TV_COMPERATOR = object : DiffUtil.ItemCallback<Accounts>() {
            override fun areItemsTheSame(oldItem: Accounts, newItem: Accounts) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Accounts, newItem: Accounts) =
                oldItem == newItem
        }
    }
}