package com.example.mj_player_tv.ui.adapter


import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.databinding.RvItemGlobalsearchPlaylistBinding
import com.example.mj_player_tv.databinding.RvItemWatchlistPlaylistBinding
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.ui.WatchListFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class WatchlistPlaylistAdapter(private val helpViewModel: HelpViewModel, private val fragment: WatchListFragment) : ListAdapter<Accounts, WatchlistPlaylistAdapter.ViewHolder>(
    GLOBALSEARCH_TV_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemWatchlistPlaylistBinding, val helpViewModel: HelpViewModel) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: Accounts) {
            binding.apply {
                tvPlaylistname.text = data.name

                binding.constGsPl.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToMovieOrSerie()
                        return@setOnKeyListener true
                    }

                    if ((keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToRecyclerview()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemWatchlistPlaylistBinding.inflate(
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
        if (helpViewModel.currentSelectedWatchlist == "MOVIE") {
            holder.binding.constGsPl.isSelected =
                helpViewModel.currentWatchListMovieAccount?.id == accountdata.id
        } else if (helpViewModel.currentSelectedWatchlist == "SERIE") {
            holder.binding.constGsPl.isSelected = helpViewModel.currentWatchListSeriesAccount?.id == accountdata.id
        } else {
            holder.binding.constGsPl.isSelected = helpViewModel.currentWatchListProgrammeAccount?.id == accountdata.id
        }
        holder.binding.constGsPl.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvPlaylistname.isSelected = hasFocus
            Log.d("WATCHLISTMOVIE", "HAT FOKUS ${accountdata.name} = $hasFocus")
            if (hasFocus) {
                if (helpViewModel.currentSelectedWatchlist == "MOVIE") {
                    fragment.showFocusedPlaylistMovies(accountdata)
                } else if (helpViewModel.currentSelectedWatchlist == "SERIE") {
                    fragment.showFocusedPlaylistSeries(accountdata)
                } else if (helpViewModel.currentSelectedWatchlist == "PROGRAMME") {
                    fragment.showFocusedPlaylistProgrammes(accountdata)
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