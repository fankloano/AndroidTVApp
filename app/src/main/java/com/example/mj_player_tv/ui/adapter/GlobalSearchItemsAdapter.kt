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
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.help.GlobalSearchDisplayItem
import com.example.mj_player_tv.databinding.RvItemGlobalsearchProgramsBinding
import com.example.mj_player_tv.databinding.RvItemGlobalsearchTvchannelBinding
import com.example.mj_player_tv.databinding.RvItemGlobalsearchmoviesBinding
import com.example.mj_player_tv.databinding.RvItemGlobalsearchseriesBinding
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class GlobalSearchItemsAdapter(
    private val helpViewModel: HelpViewModel,
    private val fragment: GlobalSearchFragment,
    private val onItemClick: (GlobalSearchDisplayItem) -> Unit
) : ListAdapter<GlobalSearchDisplayItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_TVCHANNELS = 1
        private const val TYPE_MOVIES = 2
        private const val TYPE_SERIES = 3
        private const val TYPE_PROGRAMS = 4
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is GlobalSearchDisplayItem.ChannelItem -> TYPE_TVCHANNELS
            is GlobalSearchDisplayItem.MovieItem -> TYPE_MOVIES
            is GlobalSearchDisplayItem.SeriesItem -> TYPE_SERIES
            is GlobalSearchDisplayItem.ProgramItem -> TYPE_PROGRAMS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TVCHANNELS -> {
                val binding = RvItemGlobalsearchTvchannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                TvChannelsViewHolder(binding, helpViewModel, fragment)
            }
            TYPE_MOVIES -> {
                val binding = RvItemGlobalsearchmoviesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                MoviesViewHolder(binding, helpViewModel, fragment)
            }
            TYPE_SERIES -> {
                val binding = RvItemGlobalsearchseriesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SeriesViewHolder(binding, helpViewModel, fragment)
            }
            TYPE_PROGRAMS -> {
                val binding = RvItemGlobalsearchProgramsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ProgramsViewHolder(binding, helpViewModel, fragment)
            }
            else -> throw IllegalArgumentException("Invalid viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GlobalSearchDisplayItem.ChannelItem -> (holder as TvChannelsViewHolder).bind(item)
            is GlobalSearchDisplayItem.MovieItem -> (holder as MoviesViewHolder).bind(item)
            is GlobalSearchDisplayItem.SeriesItem -> (holder as SeriesViewHolder).bind(item)
            is GlobalSearchDisplayItem.ProgramItem -> (holder as ProgramsViewHolder).bind(item)
        }
    }

    inner class TvChannelsViewHolder(
        private val binding: RvItemGlobalsearchTvchannelBinding,
        private val helpViewModel: HelpViewModel,
        private val fragment: GlobalSearchFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(channelItem: GlobalSearchDisplayItem.ChannelItem) {
            binding.apply {
                val tvchannel = channelItem.channel.tvchannel.target

                binding.tvChannel.text = tvchannel.showingName

                val linkedEpgChannel = tvchannel.linkedEpgChannel?.target

                val image = tvchannel.logo
                val epgLogo = linkedEpgChannel?.icon?.firstOrNull()

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
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition in 0..6) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }

                binding.cardviewTvchannel.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        binding.overlayFull.visibility = View.GONE
                    } else {
                        binding.overlayFull.visibility = View.VISIBLE
                    }
                }

                cardviewTvchannel.setOnClickListener {
                    onItemClick(channelItem)
                }
            }
        }
    }

    inner class MoviesViewHolder(
        private val binding: RvItemGlobalsearchmoviesBinding,
        private val helpViewModel: HelpViewModel,
        private val fragment: GlobalSearchFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(movieItem: GlobalSearchDisplayItem.MovieItem) {
            val movie = movieItem.movie
            binding.apply {
                tvMovies.text = movie.movieName

                val image = movie.screenshot_uri
                if (!image.isNullOrEmpty()) {
                    ivMovies.load(image)
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
            }
        }
    }

    inner class SeriesViewHolder(
        private val binding: RvItemGlobalsearchseriesBinding,
        private val helpViewModel: HelpViewModel,
        private val fragment: GlobalSearchFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(seriesItem: GlobalSearchDisplayItem.SeriesItem) {
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
            }
        }
    }

    inner class ProgramsViewHolder(
        private val binding: RvItemGlobalsearchProgramsBinding,
        private val helpViewModel: HelpViewModel,
        private val fragment: GlobalSearchFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var epgAdapter: GlobalSearchEpgListAdapter
        private lateinit var channelAdapter: GlobalSearchProgramsChannelAdapter

        fun bind(item: GlobalSearchDisplayItem.ProgramItem) {
            val channelList = item.programs.map { it.first }
            val programMap = item.programs.toMap()

            // 1. Kanal-RecyclerView (links)
            channelAdapter = GlobalSearchProgramsChannelAdapter { selectedChannel ->
                // Update rechte Liste (EPGs) + Detail
                val epgs = programMap[selectedChannel] ?: emptyList()
                epgAdapter.submitList(epgs)

                binding.tvSelectedChannel.text = selectedChannel.tvchannel.target.showingName
                updateDetail(epgs.firstOrNull())
            }

            binding.recyclerEpg.adapter = channelAdapter
            channelAdapter.submitList(channelList)

            // 2. EPG-Liste (mittig)
            epgAdapter = GlobalSearchEpgListAdapter(
                onEpgFocused = { selectedEpg ->
                    updateDetail(selectedEpg)
                },
                helpViewModel = helpViewModel // ✅ Übergib hier das ViewModel
            )

            binding.recyclerEpglist.adapter = epgAdapter

            // Initialauswahl: Erster Kanal
            val firstChannel = channelList.firstOrNull()
            val initialEpgs = programMap[firstChannel] ?: emptyList()

            epgAdapter.submitList(initialEpgs)
            binding.tvSelectedChannel.text = firstChannel?.tvchannel?.target?.showingName.orEmpty()
            updateDetail(initialEpgs.firstOrNull())
        }

        private fun updateDetail(epg: EpgDataOB?) {
            if (epg == null) {
                binding.tvDetailepgName.text = ""
                binding.tvDetailepgSubtitle.text = ""
                binding.tvDetailepgDescription.text = ""
                return
            }

            binding.tvDetailepgName.text = epg.name
            binding.tvDetailepgSubtitle.text = epg.sub_title ?: ""
            binding.tvDetailepgDescription.text = epg.descr ?: ""
        }
    }


    class DiffCallback : DiffUtil.ItemCallback<GlobalSearchDisplayItem>() {
        override fun areItemsTheSame(oldItem: GlobalSearchDisplayItem, newItem: GlobalSearchDisplayItem): Boolean {
            return when {
                oldItem is GlobalSearchDisplayItem.ChannelItem && newItem is GlobalSearchDisplayItem.ChannelItem -> oldItem.channel.id == newItem.channel.id
                oldItem is GlobalSearchDisplayItem.MovieItem && newItem is GlobalSearchDisplayItem.MovieItem -> oldItem.movie.id == newItem.movie.id
                oldItem is GlobalSearchDisplayItem.SeriesItem && newItem is GlobalSearchDisplayItem.SeriesItem -> oldItem.series.id == newItem.series.id
                oldItem is GlobalSearchDisplayItem.ProgramItem && newItem is GlobalSearchDisplayItem.ProgramItem -> {
                    // Vergleiche z. B. die Liste der Kanal-IDs
                    val oldIds = oldItem.programs.map { it.first.id }
                    val newIds = newItem.programs.map { it.first.id }
                    oldIds == newIds
                }

                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: GlobalSearchDisplayItem, newItem: GlobalSearchDisplayItem): Boolean {
            return oldItem == newItem
        }
    }
}
