package com.example.mj_player_tv.ui.adapter

import android.content.Intent
import android.net.Uri
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.PopupMenu
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.database.entity.Programme_
import com.example.mj_player_tv.database.help.GlobalSearchDisplayItem
import com.example.mj_player_tv.database.help.WatchlistDisplayItem
import com.example.mj_player_tv.databinding.RvItemGlobalsearchProgramsBinding
import com.example.mj_player_tv.databinding.RvItemGlobalsearchTvchannelBinding
import com.example.mj_player_tv.databinding.RvItemGlobalsearchmoviesBinding
import com.example.mj_player_tv.databinding.RvItemGlobalsearchseriesBinding
import com.example.mj_player_tv.databinding.RvItemMoviesBinding
import com.example.mj_player_tv.databinding.RvItemSeriesBinding
import com.example.mj_player_tv.databinding.RvItemWatchlistProgrammeBinding
import com.example.mj_player_tv.databinding.RvItemWatchlistProgramsBinding
import com.example.mj_player_tv.ui.FullEpgFragment
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.ui.WatchListFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import io.objectbox.Box
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
class WatchlistItemsAdapter(
    private val helpViewModel: HelpViewModel,
    private val fragment: WatchListFragment,
    private val onItemClick: (WatchlistDisplayItem) -> Unit,
    private val onLongItemClick: (WatchlistDisplayItem, View) -> Unit
) : ListAdapter<WatchlistDisplayItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_MOVIES = 1
        private const val TYPE_SERIES = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is WatchlistDisplayItem.MovieItem -> TYPE_MOVIES
            is WatchlistDisplayItem.SeriesItem -> TYPE_SERIES
            else -> {0}
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_MOVIES -> {
                val binding = RvItemGlobalsearchmoviesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                MoviesViewHolder(binding, helpViewModel, fragment)
            }
            TYPE_SERIES -> {
                val binding = RvItemGlobalsearchseriesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SeriesViewHolder(binding, helpViewModel, fragment)
            }
            else -> throw IllegalArgumentException("Invalid viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is WatchlistDisplayItem.MovieItem -> (holder as MoviesViewHolder).bind(item)
            is WatchlistDisplayItem.SeriesItem -> (holder as SeriesViewHolder).bind(item)
            is WatchlistDisplayItem.ProgramItem -> {

            }
        }
    }

    inner class MoviesViewHolder(
        private val binding: RvItemGlobalsearchmoviesBinding,
        private val helpViewModel: HelpViewModel,
        private val fragment: WatchListFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(movieItem: WatchlistDisplayItem.MovieItem) {
            val movie = movieItem.movie
            binding.apply {
                tvMovies.text = movie.movieName

                val image = movie.screenshot_uri
                if (!image.isNullOrEmpty()) {
                    Log.d("GLOBIMOVIEBILDI", "${movie.movieName} = $image")
                    ivMovies.load(image)
                } else {
                    Log.d("GLOBIMOVIEBILDI", "${movie.movieName} ||| NO IMAGE")
                }

                binding.tvIsFavorite.visibility = if (movie.isFavorite) View.VISIBLE else View.INVISIBLE

                binding.tvIsFullyWatched.visibility = if (movie.isCompletelyWatched) View.VISIBLE else View.INVISIBLE

                binding.tvIsPartlyWatched.visibility = if (movie.isPartlyWatched) View.VISIBLE else View.INVISIBLE

                binding.cardviewTvchannel.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition in 0..6) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }
                cardviewTvchannel.setOnClickListener {
                    onItemClick(movieItem)
                }

                binding.cardviewTvchannel.setOnLongClickListener {
                    onLongItemClick.invoke(movieItem, binding.cardviewTvchannel)
                    true
                }

                binding.cardviewTvchannel.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        fragment.updateMovieUi(movie)
                    }
                }
            }
        }
    }

    inner class SeriesViewHolder(
        private val binding: RvItemGlobalsearchseriesBinding,
        private val helpViewModel: HelpViewModel,
        private val fragment: WatchListFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(seriesItem: WatchlistDisplayItem.SeriesItem) {
            val serie = seriesItem.series
            binding.apply {
                tvSeries.text = serie.seriesName

                val image = serie.screenshot_uri
                if (!image.isNullOrEmpty()) {
                    ivSeries.load(image)
                }

                binding.tvIsFavorite.visibility = if (serie.isFavorite) View.VISIBLE else View.INVISIBLE

                binding.tvIsFullyWatched.visibility = if (serie.isCompletelyWatched) View.VISIBLE else View.INVISIBLE

                binding.tvIsPartlyWatched.visibility = if (serie.isPartlyWatched) View.VISIBLE else View.INVISIBLE

                binding.cardviewSerie.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition in 0..6) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }

                cardviewSerie.setOnClickListener {
                    onItemClick(seriesItem)
                }

                binding.cardviewSerie.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        fragment.updateSeriesUi(serie)
                    }
                }

                binding.cardviewSerie.setOnLongClickListener {
                    onLongItemClick.invoke(seriesItem, binding.cardviewSerie)
                    true
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<WatchlistDisplayItem>() {
        override fun areItemsTheSame(oldItem: WatchlistDisplayItem, newItem: WatchlistDisplayItem): Boolean {
            val result = when {
                oldItem is WatchlistDisplayItem.MovieItem && newItem is WatchlistDisplayItem.MovieItem -> {
                    oldItem.movie.idByAccountData == newItem.movie.idByAccountData
                }
                oldItem is WatchlistDisplayItem.SeriesItem && newItem is WatchlistDisplayItem.SeriesItem -> {
                    oldItem.series.idByAccountData == newItem.series.idByAccountData
                }
                else -> false
            }
            return result
        }

        override fun areContentsTheSame(oldItem: WatchlistDisplayItem, newItem: WatchlistDisplayItem): Boolean {
            val result = oldItem == newItem
            return result
        }
    }

}
