package com.example.mj_player_tv.ui

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import coil.load
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.MovieOB_
import com.example.mj_player_tv.database.entity.SeasonsOB
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.database.entity.SeriesOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.entity.TvChannelOB_
import com.example.mj_player_tv.databinding.FragmentHistoryBinding
import com.example.mj_player_tv.databinding.FragmentHomeBinding
import com.example.mj_player_tv.databinding.FragmentTvChannelsBinding
import com.example.mj_player_tv.databinding.FragmentWatchlistBinding
import com.example.mj_player_tv.ui.adapter.GlobalSearchPlaylistAdapter
import com.example.mj_player_tv.ui.adapter.GlobalSearchTvChannelsAdapter
import com.example.mj_player_tv.ui.adapter.WatchListMoviesAdapter
import com.example.mj_player_tv.ui.adapter.WatchlistPlaylistAdapter
import com.example.mj_player_tv.ui.adapter.WatchlistSeriesAdapter
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import com.rubensousa.dpadrecyclerview.FocusableDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.mutable.Mutable
import java.security.Key
import androidx.core.view.isGone
import kotlinx.coroutines.delay
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import com.example.mj_player_tv.database.entity.EpisodesOB
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.database.help.StatsDisplayItem
import com.example.mj_player_tv.database.help.StatsMainCategory
import com.example.mj_player_tv.database.help.WatchlistDisplayItem
import com.example.mj_player_tv.database.help.WatchlistMainCategory
import com.example.mj_player_tv.ui.adapter.StatsItemsAdapter
import com.example.mj_player_tv.ui.adapter.WatchlistItemsAdapter
import com.example.mj_player_tv.viewmodel.MoviesViewModel
import com.example.mj_player_tv.viewmodel.MoviesViewModelFactory
import com.example.mj_player_tv.viewmodel.SeriesViewModel
import com.example.mj_player_tv.viewmodel.SeriesViewModelFactory
import kotlinx.coroutines.flow.collectLatest

@UnstableApi
class WatchHistoryFragment : Fragment(R.layout.fragment_history) {

    private var _binding: FragmentHistoryBinding? = null

    private val binding get() = _binding!!

    private lateinit var statsItemsAdapter: StatsItemsAdapter

    private var moviesList: List<StatsDisplayItem.MovieItem>? = null

    private var seriesList: List<StatsDisplayItem.SeriesItem>? = null

    private var tvchannelsList: List<StatsDisplayItem.TvChannelItem>? = null


    private var selectedStatsCategory: StatsMainCategory? = null

    private var lastLoadedCategory: StatsMainCategory? = null

    private var isFirstOpenStats = true

    private val accountBox = ObjectBox.store.boxFor(Accounts::class.java)
    private val tvchBox = ObjectBox.store.boxFor(TvChannelOB::class.java)

    private val stalkerViewModel: StalkerViewModel by activityViewModels {
        StalkerViewModelFactory(
            requireActivity().application
        )
    }

    private val xtreamViewModel: XtreamViewModel by activityViewModels {
        XtreamViewModelFactory(
            requireActivity().application
        )
    }

    private val helpViewModel: HelpViewModel by activityViewModels {
        HelpViewModelFactory(
            requireActivity().application
        )
    }

    private val seriesViewModel: SeriesViewModel by activityViewModels {
        SeriesViewModelFactory(
            requireActivity().application
        )
    }

    private val moviesViewModel: MoviesViewModel by activityViewModels {
        MoviesViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        parentFragmentManager.addOnBackStackChangedListener(backStackListener)

        helpViewModel.fetchStatsData()
        prepareItemsRecyclerView()

        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.statsResults.flowWithLifecycle(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED
            ).collectLatest { results ->
                if (results.isEmpty()) return@collectLatest

                tvchannelsList = results.filterIsInstance<StatsDisplayItem.TvChannelItem>()
                moviesList = results.filterIsInstance<StatsDisplayItem.MovieItem>()
                seriesList = results.filterIsInstance<StatsDisplayItem.SeriesItem>()
                if (isFirstOpenStats) {
                    if (!tvchannelsList.isNullOrEmpty()) {
                        binding.recyclerItems.setSpanCount(5)
                        helpViewModel.selectedStatsCategory = StatsMainCategory.TVCHANNELS
                        selectedStatsCategory = StatsMainCategory.TVCHANNELS
                        statsItemsAdapter.submitList(tvchannelsList)
                        binding.rvHistoryTvchannels.requestFocus()
                    } else if (!moviesList.isNullOrEmpty()) {
                        binding.recyclerItems.setSpanCount(4)
                        helpViewModel.selectedStatsCategory = StatsMainCategory.MOVIES
                        selectedStatsCategory = StatsMainCategory.MOVIES
                        statsItemsAdapter.submitList(moviesList)
                        binding.rvHistoryMovies.requestFocus()
                    } else {
                        if (!seriesList.isNullOrEmpty()) {
                            binding.recyclerItems.setSpanCount(4)
                            helpViewModel.selectedStatsCategory = StatsMainCategory.SERIES
                            selectedStatsCategory = StatsMainCategory.SERIES
                            statsItemsAdapter.submitList(seriesList)
                            binding.rvHistorySeries.requestFocus()
                        } else {
                            helpViewModel.selectedStatsCategory = StatsMainCategory.TVCHANNELS
                            selectedStatsCategory = StatsMainCategory.TVCHANNELS
                            binding.rvHistoryTvchannels.requestFocus()
                        }
                    }
                    isFirstOpenStats = false
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.statsSearching.flowWithLifecycle(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED
            ).collectLatest { searching ->
                if (searching) {
                    binding.tvNodatafound.visibility = View.GONE
                    binding.progressBar.visibility = View.VISIBLE
                } else {
                    if (!helpViewModel.hasFetchedStats) {
                        binding.tvNodatafound.visibility = View.GONE
                        return@collectLatest
                    }

                    binding.progressBar.visibility = View.GONE
                    val hasResults = !(moviesList?.isEmpty() == true &&
                            seriesList?.isEmpty() == true &&
                            tvchannelsList?.isEmpty() == true)

                    if (hasResults) {
                        binding.tvNodatafound.visibility = View.GONE
                    } else {
                        binding.tvNodatafound.visibility = View.VISIBLE
                        binding.rvHistoryTvchannels.requestFocus()
                    }
                }
            }
        }

        binding.rvHistoryMovies.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.rvHistoryMovies.isSelected = true
                binding.rvHistorySeries.isSelected = false
                binding.rvHistoryTvchannels.isSelected = false
                onCategorySelected(StatsMainCategory.MOVIES)
            }
        }

        binding.rvHistoryMovies.setOnKeyListener { _, keyCode, event ->
            if (((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) && event.action == KeyEvent.ACTION_DOWN) {
                if (statsItemsAdapter.currentList.isNotEmpty()) {
                    binding.recyclerItems.requestFocus()
                } else {
                    binding.rvHistoryMovies.requestFocus()
                }
                return@setOnKeyListener true
            } else if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                helpViewModel.watchstatsContainerOpened = false
                helpViewModel.requestFocusOnWatchListCard(false)
                parentFragmentManager.popBackStack()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.rvHistorySeries.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.rvHistoryMovies.isSelected = false
                binding.rvHistorySeries.isSelected = true
                binding.rvHistoryTvchannels.isSelected = false
                onCategorySelected(StatsMainCategory.SERIES)
            }
        }

        binding.rvHistorySeries.setOnKeyListener { _, keyCode, event ->
            if (((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) && event.action == KeyEvent.ACTION_DOWN) {
                if (statsItemsAdapter.currentList.isNotEmpty()) {
                    binding.recyclerItems.requestFocus()
                } else {
                    binding.rvHistorySeries.requestFocus()
                }
                return@setOnKeyListener true
            } else if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                helpViewModel.watchstatsContainerOpened = false
                helpViewModel.requestFocusOnWatchListCard(false)
                parentFragmentManager.popBackStack()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.rvHistoryTvchannels.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                if (!binding.containerWatchhistoryVodInfo.isVisible) {
                    binding.rvHistoryMovies.isSelected = false
                    binding.rvHistorySeries.isSelected = false
                    binding.rvHistoryTvchannels.isSelected = true
                    onCategorySelected(StatsMainCategory.TVCHANNELS)
                }
            }
        }

        binding.rvHistoryTvchannels.setOnKeyListener { _, keyCode, event ->
            if (((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) && event.action == KeyEvent.ACTION_DOWN) {
                if (statsItemsAdapter.currentList.isNotEmpty()) {
                    binding.recyclerItems.requestFocus()
                } else {
                    binding.rvHistoryTvchannels.requestFocus()
                }
                return@setOnKeyListener true
            } else if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                helpViewModel.watchstatsContainerOpened = false
                parentFragmentManager.popBackStack()
                helpViewModel.requestFocusOnWatchListCard(false)
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        seriesViewModel.focusToSeriesRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                if (statsItemsAdapter.currentList.isNotEmpty()) {
                    binding.recyclerItems.requestFocus()
                } else {
                    binding.rvHistorySeries.requestFocus()
                }
                seriesViewModel.clearFocusToSeries()
            }
        }

        moviesViewModel.focusToMoviesRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                if (statsItemsAdapter.currentList.isNotEmpty()) {
                    binding.recyclerItems.requestFocus()
                } else {
                    binding.rvHistoryMovies.requestFocus()
                }
                moviesViewModel.clearFocusToMovies()
            }
        }
    }

    private fun prepareItemsRecyclerView() {
        statsItemsAdapter = StatsItemsAdapter(helpViewModel, this, accountBox) { clickedItem, view ->
            when (clickedItem) {
                is StatsDisplayItem.MovieItem -> {
                    val selectedAccount = clickedItem.movie.movieAccount.target
                    if (selectedAccount != null) {
                        if (selectedAccount.isXtream) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val xtreamMovie = xtreamViewModel.getXtreamMovieDetails(clickedItem.movie, selectedAccount!!)
                                helpViewModel.currentFocusedMovie = xtreamMovie
                                helpViewModel.currentMovieAccount = selectedAccount
                                openMovieDetailFragment()
                            }
                        } else if (selectedAccount.isStalker) {
                            helpViewModel.currentMovieAccount = selectedAccount
                            helpViewModel.currentFocusedMovie = clickedItem.movie
                            openMovieDetailFragment()
                        }
                    }
                }
                is StatsDisplayItem.SeriesItem -> {
                    val selectedAccount = clickedItem.series.seriesAccount.target
                    if (selectedAccount != null) {
                        if (clickedItem.series.idByAccountData == helpViewModel.currentFocusedSerie?.idByAccountData) {
                            seriesViewModel.openedSameSeries = true
                        }
                        if (selectedAccount.isXtream) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val seasons =
                                    xtreamViewModel.getXtreamSerieDetails(clickedItem.series,
                                        selectedAccount
                                    )
                                clickedItem.series.totalSeasons = seasons.size
                                helpViewModel.focusedSeasons =
                                    seasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                                        .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE })
                                        .toMutableList()
                                helpViewModel.focusedEpisodes =
                                    xtreamViewModel.episodesList.sortedWith(
                                        compareBy(
                                            { it.seasonNumber },
                                            { it.episodeNumber })
                                    ).toMutableList()
                                helpViewModel.currentFocusedSerie = clickedItem.series
                                helpViewModel.currentSeriesAccount = selectedAccount
                                openSeriesDetailFragment()
                            }
                        } else {
                            viewLifecycleOwner.lifecycleScope.launch {
                                stalkerViewModel.seriesDetailData.postValue(mutableListOf())
                                stalkerViewModel.getSeriesDetail(clickedItem.series,
                                    selectedAccount
                                )
                                helpViewModel.currentFocusedSerie = clickedItem.series
                                stalkerViewModel.seriesDetailData.observe(viewLifecycleOwner) { seasons ->
                                    clickedItem.series.totalSeasons = seasons.size
                                    helpViewModel.focusedSeasons =
                                        seasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                                            .thenBy {
                                                it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE
                                            }).toMutableList()
                                    helpViewModel.focusedEpisodes =
                                        stalkerViewModel.episodesList.sortedWith(
                                            compareBy(
                                                { it.seasonNumber },
                                                { it.episodeNumber })
                                        ).toMutableList()
                                }
                                helpViewModel.currentSeriesAccount = selectedAccount
                                openSeriesDetailFragment()
                            }
                        }
                    }
                }
                is StatsDisplayItem.TvChannelItem -> {
                    showTvChannelPopUp(clickedItem.tvchannel, view)
                }
            }
        }

        binding.recyclerItems.apply {
            adapter = statsItemsAdapter
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
        }
    }


    fun onCategorySelected(category: StatsMainCategory) {
        if (selectedStatsCategory == category) {
            // Kategorie ist schon aktiv → nichts tun
            return
        }
        statsItemsAdapter.submitList(null)
        helpViewModel.selectedStatsCategory = category
        when (category) {
            StatsMainCategory.MOVIES -> {
                helpViewModel.currentFocusedMovie = null
                binding.recyclerItems.setSpanCount(4)
                if (moviesList.isNullOrEmpty()) {
                    binding.tvNodatafound.visibility = View.VISIBLE
                } else {
                    binding.tvNodatafound.visibility = View.INVISIBLE
                    statsItemsAdapter.submitList(moviesList)
                }
            }
            StatsMainCategory.SERIES -> {
                helpViewModel.currentFocusedSerie = null
                binding.recyclerItems.setSpanCount(4)
                if (seriesList.isNullOrEmpty()) {
                    binding.tvNodatafound.visibility = View.VISIBLE
                } else {
                    binding.tvNodatafound.visibility = View.INVISIBLE
                    statsItemsAdapter.submitList(seriesList)
                }
            }
            StatsMainCategory.TVCHANNELS -> {
                binding.recyclerItems.setSpanCount(5)
                if (tvchannelsList.isNullOrEmpty()) {
                    binding.tvNodatafound.visibility = View.VISIBLE
                } else {
                    binding.tvNodatafound.visibility = View.INVISIBLE
                    statsItemsAdapter.submitList(tvchannelsList)
                }
            }
        }
        selectedStatsCategory = category
    }

    private fun showTvChannelPopUp(tvchannel: TvChannelOB, view: View) {
        val popup = PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.menu_history_options, popup.menu)

        val singleItem = popup.menu.findItem(R.id.remove_this)
        singleItem.setTitle("Remove ${tvchannel.showingName} from list")
        val allItem = popup.menu.findItem(R.id.remove_all)
        if (tvchannelsList != null && tvchannelsList!!.size > 1) {
            allItem.setVisible(true)
        } else {
            allItem.setVisible(false)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.remove_this -> {
                    removeChannelFromList(tvchannel)
                    true
                }
                R.id.remove_all -> {
                    removeAllChannelsFromList()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun removeChannelFromList(tvchannel: TvChannelOB) {
        tvchannelsList = tvchannelsList?.filter { it.tvchannel.id != tvchannel.id }?.toMutableList()
        statsItemsAdapter.submitList(tvchannelsList?.sortedByDescending { it.tvchannel.timeWatched } )
        binding.recyclerItems.requestFocus()
        tvchannel.timeWatched = 0L
        tvchBox.put(tvchannel)
    }

    private fun removeAllChannelsFromList() {
        val channelList = tvchannelsList
        tvchannelsList = mutableListOf()
        statsItemsAdapter.submitList(tvchannelsList)
        binding.rvHistoryTvchannels.requestFocus()
        helpViewModel.viewModelScope.launch(Dispatchers.IO) {
            val updatedChannels = channelList?.map {
                it.tvchannel.timeWatched = 0L
                it.tvchannel
            }
            if (updatedChannels != null) {
                tvchBox.put(updatedChannels)
            }
        }
    }

    fun openMovieDetailFragment() {
        helpViewModel.isWatchHistoryContainerOpened = true
        val transaction = parentFragmentManager.beginTransaction()
        transaction.add(R.id.container_watchhistory_vod_info, MovieDetailFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.focusBlocker.requestFocus()
        binding.containerWatchhistoryVodInfo.visibility = View.VISIBLE
    }

    fun openSeriesDetailFragment() {
        helpViewModel.isWatchHistoryContainerOpened = true
        val transaction = parentFragmentManager.beginTransaction()
        transaction.add(R.id.container_watchhistory_vod_info, SeriesDetailFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.focusBlocker.requestFocus()
        binding.containerWatchhistoryVodInfo.visibility = View.VISIBLE
    }

    private fun formatDuration(duration: Int, accountId: Long): String {
        val currentAccount = accountBox.get(accountId)

        return when {
            currentAccount.isStalker -> {  // Dauer kommt in MINUTEN
                val hours = duration / 60
                val minutes = duration % 60
                formatTime(hours, minutes)
            }
            currentAccount.isXtream -> {   // Dauer kommt in SEKUNDEN
                val hours = duration / 3600
                val minutes = (duration % 3600) / 60
                formatTime(hours, minutes)
            }
            else -> ""
        }
    }

    private fun formatTime(hours: Int, minutes: Int): String {
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
            hours > 0 -> "${hours}h"
            else -> "${minutes}min"
        }
    }

    fun formatRating(rating: String?): String {
        val ratingValue = rating?.toFloatOrNull()
        return when {
            ratingValue == null -> ""
            ratingValue == ratingValue.toInt().toFloat() -> String.format("%.1f", ratingValue).replace(",", ".")
            else -> String.format("%.1f", ratingValue).replace(",", ".")
        }
    }

    fun focusToCategory() {
        when (selectedStatsCategory) {
            StatsMainCategory.MOVIES -> binding.rvHistoryMovies.requestFocus()
            StatsMainCategory.SERIES -> binding.rvHistorySeries.requestFocus()
            StatsMainCategory.TVCHANNELS -> binding.rvHistoryTvchannels.requestFocus()
            null -> {}
        }
    }

    private val backStackListener = FragmentManager.OnBackStackChangedListener {
        if (isAdded && parentFragmentManager.fragments.lastOrNull() == this && helpViewModel.isSearchContainerOpened) {
            binding.recyclerItems.requestFocus()
            helpViewModel.isWatchlistContainerOpened = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        parentFragmentManager.removeOnBackStackChangedListener(backStackListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        helpViewModel.selectedStatsCategory = null
        selectedStatsCategory = null
        helpViewModel.isWatchHistoryContainerOpened = false
        helpViewModel.currentFocusedMovie = null
        helpViewModel.currentFocusedSerie = null
        helpViewModel.resetStatsData()
        helpViewModel.cancelStatsJob()
        _binding = null
    }
}