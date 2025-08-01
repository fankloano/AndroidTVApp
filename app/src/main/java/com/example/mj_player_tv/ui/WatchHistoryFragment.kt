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
import com.example.mj_player_tv.ui.adapter.WatchHistoryMoviesAdapter
import com.example.mj_player_tv.ui.adapter.WatchHistorySeriesAdapter
import com.example.mj_player_tv.ui.adapter.WatchHistoryTvChannelsAdapter
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
import com.example.mj_player_tv.database.entity.EpisodesOB
import com.example.mj_player_tv.viewmodel.MoviesViewModel
import com.example.mj_player_tv.viewmodel.MoviesViewModelFactory
import com.example.mj_player_tv.viewmodel.SeriesViewModel
import com.example.mj_player_tv.viewmodel.SeriesViewModelFactory

@UnstableApi
class WatchHistoryFragment : Fragment(R.layout.fragment_history) {

    private var _binding: FragmentHistoryBinding? = null

    private val binding get() = _binding!!

    private var tvchannelsAdapter: WatchHistoryTvChannelsAdapter? = null
    private var moviesAdapter: WatchHistoryMoviesAdapter? = null
    private var seriesAdapter: WatchHistorySeriesAdapter? = null

    private val accountBox = ObjectBox.store.boxFor(Accounts::class.java)
    private val movieBox = ObjectBox.store.boxFor(MovieOB::class.java)
    private val seriesBox = ObjectBox.store.boxFor(SeriesOB::class.java)
    private val tvchBox = ObjectBox.store.boxFor(TvChannelOB::class.java)


    private var moviesList: MutableList<MovieOB>? = null

    private var seriesList: MutableList<SeriesOB>? = null

    private var tvChannelsList: MutableList<TvChannelOB>? = null

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

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val testTvQuery = tvchBox.query(TvChannelOB_.timeWatched.greater(0L)).build()
            tvChannelsList = testTvQuery.find()
            testTvQuery.close()
            if (!tvChannelsList.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    if (binding.progressBar.isVisible) {
                        binding.progressBar.visibility = View.GONE
                    }
                    prepareTvChannelsRecyclerView()
                    binding.rvHistoryTvchannels.isSelected = true
                    binding.rvHistoryMovies.isSelected = false
                    binding.rvHistorySeries.isSelected = false
                    binding.rvHistoryTvchannels.requestFocus()
                    tvchannelsAdapter?.submitList(tvChannelsList!!.sortedByDescending { it.timeWatched})
                }
            }
            val movieQuery = movieBox.query(MovieOB_.isCompletelyWatched.equal(true)
                .or(MovieOB_.isPartlyWatched.equal(true))).build()
            moviesList = movieQuery.find()
            movieQuery.close()
            if (!moviesList.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    if (binding.progressBar.isVisible) {
                        binding.progressBar.visibility = View.GONE
                    }
                    if (tvChannelsList.isNullOrEmpty()) {
                        prepareMoviesRecyclerView()
                        moviesAdapter?.submitList(moviesList!!.sortedBy { it.movieName })
                        binding.rvHistoryTvchannels.isSelected = false
                        binding.rvHistoryMovies.isSelected = true
                        binding.rvHistorySeries.isSelected = false
                        binding.rvHistoryMovies.requestFocus()
                    }
                }
            }
            val seriesQuery = seriesBox.query(SeriesOB_.isCompletelyWatched.equal(true)
                .or(SeriesOB_.isPartlyWatched.equal(true))).build()
            seriesList = seriesQuery.find()
            seriesQuery.close()
            if (!seriesList.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    if (binding.progressBar.isVisible) {
                        binding.progressBar.visibility = View.GONE
                    }
                    if (tvChannelsList.isNullOrEmpty() && moviesList.isNullOrEmpty()) {
                        prepareSeriesRecyclerView()
                        seriesAdapter?.submitList(seriesList!!.sortedBy { it.seriesName })
                        binding.rvHistoryTvchannels.isSelected = false
                        binding.rvHistoryMovies.isSelected = false
                        binding.rvHistorySeries.isSelected = true
                        binding.rvHistorySeries.requestFocus()
                    }
                }
            }
            if (moviesList.isNullOrEmpty() && seriesList.isNullOrEmpty() && tvChannelsList.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    if (binding.progressBar.isVisible) {
                        binding.progressBar.visibility = View.GONE
                    }
                    binding.rvHistoryTvchannels.isSelected = true
                    binding.rvHistoryMovies.isSelected = false
                    binding.rvHistorySeries.isSelected = false
                    binding.rvHistoryTvchannels.requestFocus()
                    binding.tvNodatafound.visibility = View.VISIBLE
                }
            }
        }

        binding.rvHistoryTvchannels.setOnKeyListener { _, keyCode, event ->
            if (((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) && event.action == KeyEvent.ACTION_DOWN) {
                binding.recyclerItems.requestFocus()
                return@setOnKeyListener true
            } else if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                helpViewModel.watchstatsContainerOpened = false
                val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                if (mainFragment is WatchlistStatsFragment) {
                    mainFragment.closeFragmentContainer(false)
                }
                parentFragmentManager.popBackStack()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.rvHistoryTvchannels.setOnFocusChangeListener { _, hasFocus ->
            binding.tvSelecttv.isSelected = hasFocus
            if (hasFocus) {
                if (!helpViewModel.isWatchHistoryContainerOpened) {
                    binding.rvHistoryTvchannels.isSelected = true
                    binding.rvHistoryMovies.isSelected = false
                    binding.rvHistorySeries.isSelected = false
                    if (!tvChannelsList.isNullOrEmpty() && (binding.recyclerItems.adapter == moviesAdapter || binding.recyclerItems.adapter == seriesAdapter)) {
                        binding.recyclerItems.visibility = View.VISIBLE
                        binding.tvNodatafound.visibility = View.GONE
                        prepareTvChannelsRecyclerView()
                        tvchannelsAdapter?.submitList(tvChannelsList!!.sortedByDescending { it.timeWatched})
                    } else {
                        if (tvChannelsList.isNullOrEmpty()) {
                            binding.recyclerItems.visibility = View.INVISIBLE
                            binding.tvNodatafound.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        binding.rvHistoryMovies.setOnFocusChangeListener { _, hasFocus ->
            binding.tvSelectmovies.isSelected = hasFocus
            if (hasFocus ) {
                if (!helpViewModel.isWatchHistoryContainerOpened) {
                    helpViewModel.currentWatchListSeriesAccount = null
                    helpViewModel.currentFocusedSerie = null
                    binding.rvHistoryMovies.isSelected = true
                    binding.rvHistoryTvchannels.isSelected = false
                    binding.rvHistorySeries.isSelected = false
                    if (!moviesList.isNullOrEmpty() && (binding.recyclerItems.adapter == seriesAdapter || binding.recyclerItems.adapter == tvchannelsAdapter )) {
                        binding.recyclerItems.visibility = View.VISIBLE
                        binding.tvNodatafound.visibility = View.GONE
                        prepareMoviesRecyclerView()
                        moviesAdapter?.submitList(moviesList!!.sortedBy { it.movieName })
                    } else {
                        if (moviesList.isNullOrEmpty()) {
                            prepareMoviesRecyclerView()
                            binding.recyclerItems.visibility = View.INVISIBLE
                            binding.tvNodatafound.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        binding.rvHistoryMovies.setOnKeyListener { _, keyCode, event ->
            if (((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) && event.action == KeyEvent.ACTION_DOWN) {
                binding.recyclerItems.requestFocus()
                return@setOnKeyListener true
            } else if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                helpViewModel.watchstatsContainerOpened = false
                val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                if (mainFragment is WatchlistStatsFragment) {
                    mainFragment.closeFragmentContainer(false)
                }
                parentFragmentManager.popBackStack()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.rvHistorySeries.setOnFocusChangeListener { _, hasFocus ->
            binding.tvSelectseries.isSelected = hasFocus
            if (hasFocus) {
                if (!helpViewModel.isWatchHistoryContainerOpened) {
                    binding.rvHistoryMovies.isSelected = false
                    binding.rvHistoryTvchannels.isSelected = false
                    binding.rvHistorySeries.isSelected = true
                    if (!seriesList.isNullOrEmpty() && (binding.recyclerItems.adapter == moviesAdapter || binding.recyclerItems.adapter == tvchannelsAdapter )) {
                        binding.recyclerItems.visibility = View.VISIBLE
                        binding.tvNodatafound.visibility = View.GONE
                        prepareSeriesRecyclerView()
                        seriesAdapter?.submitList(seriesList!!.sortedBy { it.seriesName })
                    } else {
                        if (seriesList.isNullOrEmpty()) {
                            prepareSeriesRecyclerView()
                            binding.recyclerItems.visibility = View.INVISIBLE
                            binding.tvNodatafound.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        binding.rvHistorySeries.setOnKeyListener { _, keyCode, event ->
            if (((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) && event.action == KeyEvent.ACTION_DOWN) {
                binding.recyclerItems.requestFocus()
                return@setOnKeyListener true
            } else if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                helpViewModel.watchstatsContainerOpened = false
                val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                if (mainFragment is WatchlistStatsFragment) {
                    mainFragment.closeFragmentContainer(false)
                }
                parentFragmentManager.popBackStack()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        seriesViewModel.focusToSeriesRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                if (!seriesAdapter?.currentList.isNullOrEmpty()) {
                    binding.recyclerItems.requestFocus()
                } else {
                    binding.rvHistorySeries.requestFocus()
                }
                seriesViewModel.clearFocusToSeries()
            }
        }

        moviesViewModel.focusToMoviesRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                if (!moviesAdapter?.currentList.isNullOrEmpty()) {
                    binding.recyclerItems.requestFocus()
                } else {
                    binding.rvHistoryMovies.requestFocus()
                }
                moviesViewModel.clearFocusToMovies()
            }
        }
    }


    fun focusToMovieOrSerie() {
        if (helpViewModel.currentSelectedWatchHistory == "MOVIE") {
            binding.rvHistoryMovies.requestFocus()
        } else if (helpViewModel.currentSelectedWatchHistory == "SERIE") {
            binding.rvHistorySeries.requestFocus()
        } else if (helpViewModel.currentSelectedWatchHistory == "TV") {
            binding.rvHistoryTvchannels.requestFocus()
        } else {
            return
        }
    }

    private fun prepareTvChannelsRecyclerView() {
        tvchannelsAdapter = WatchHistoryTvChannelsAdapter(onTvChannelClickListener, onTvChannelLongClickListener, this, helpViewModel)
        binding.recyclerItems.apply {
            adapter = tvchannelsAdapter
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
            setSpanCount(5)
        }
    }

    private fun prepareMoviesRecyclerView() {
        moviesAdapter = WatchHistoryMoviesAdapter(onMovieClickListener, this, helpViewModel, accountBox, onMovieLongClickListener)
        binding.recyclerItems.apply {
            adapter = moviesAdapter
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
            setSpanCount(4)
        }
    }

    private fun prepareSeriesRecyclerView() {
        seriesAdapter = WatchHistorySeriesAdapter(onSeriesClickListener, onSeriesLongClickListener, this, helpViewModel)
        binding.recyclerItems.apply {
            adapter = seriesAdapter
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
            setSpanCount(4)
        }
    }

    private val onTvChannelClickListener = WatchHistoryTvChannelsAdapter.OnClickListener { tvchannel ->
        helpViewModel.currentSelectedWatchHistory = "TV"
    }

    private val onTvChannelLongClickListener = WatchHistoryTvChannelsAdapter.OnLongClickListener { tvchannel, view ->
        showTvChannelPopUp(tvchannel, view)
    }

    private val onMovieClickListener = WatchHistoryMoviesAdapter.OnClickListener { movie ->

        helpViewModel.currentSelectedWatchHistory = "MOVIE"
        val account = movie.accountId?.let {
            accountBox.get(it)
        }
        if (account != null) {
            if (account.isXtream) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val xtreamMovie = xtreamViewModel.getXtreamMovieDetails(movie, account)
                    helpViewModel.currentFocusedMovie = xtreamMovie
                    helpViewModel.currentMovieAccount = account
                    openMovieDetailFragment()
                }
            } else {
                helpViewModel.currentMovieAccount = account
                helpViewModel.currentFocusedMovie = movie
                openMovieDetailFragment()
            }
        }
    }

    private val onMovieLongClickListener = WatchHistoryMoviesAdapter.OnLongClickListener { movie ->

    }

    private val onSeriesClickListener = WatchHistorySeriesAdapter.OnClickListener { serie ->
        helpViewModel.currentSelectedWatchHistory = "SERIE"
        val account = serie.accountId?.let {
            accountBox.get(it)
        }
        if (account != null) {
            if (account.isXtream) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val seasons =
                        xtreamViewModel.getXtreamSerieDetails(serie, account)
                    serie.totalSeasons = seasons.size
                    helpViewModel.focusedSeasons = seasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                        .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE }).toMutableList()
                    helpViewModel.focusedEpisodes = xtreamViewModel.episodesList.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })).toMutableList()
                    helpViewModel.currentFocusedSerie = serie
                    helpViewModel.currentSeriesAccount = account
                    openSeriesDetailFragment()
                }
            } else if (account.isStalker) {
                viewLifecycleOwner.lifecycleScope.launch {
                    stalkerViewModel.seriesDetailData.postValue(mutableListOf())
                    stalkerViewModel.getSeriesDetail(serie, account)
                    helpViewModel.currentFocusedSerie = serie
                    stalkerViewModel.seriesDetailData.observe(viewLifecycleOwner) { seasons ->
                        serie.totalSeasons = seasons.size
                        helpViewModel.focusedSeasons = seasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                            .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE }).toMutableList()
                        helpViewModel.focusedEpisodes = stalkerViewModel.episodesList.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })).toMutableList()
                    }
                    helpViewModel.currentSeriesAccount = account
                    openSeriesDetailFragment()
                }
            } else {

            }
        }
    }

    private val onSeriesLongClickListener = WatchHistorySeriesAdapter.OnLongClickListener { serie ->

    }

    private fun showTvChannelPopUp(tvchannel: TvChannelOB, view: View) {
        val popup = PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.menu_history_options, popup.menu)

        val singleItem = popup.menu.findItem(R.id.remove_this)
        singleItem.setTitle("Remove ${tvchannel.showingName} from list")
        val allItem = popup.menu.findItem(R.id.remove_all)
        if (tvChannelsList != null && tvChannelsList!!.size > 1) {
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
        tvChannelsList = tvChannelsList?.filter { it.id != tvchannel.id }?.toMutableList()
        tvchannelsAdapter?.submitList(tvChannelsList?.sortedByDescending { it.timeWatched } )
        binding.recyclerItems.requestFocus()
        tvchannel.timeWatched = 0L
        tvchBox.put(tvchannel)
    }

    private fun removeAllChannelsFromList() {
        val channelList = tvChannelsList
        tvChannelsList = mutableListOf()
        tvchannelsAdapter?.submitList(tvChannelsList)
        binding.rvHistoryTvchannels.requestFocus()
        helpViewModel.viewModelScope.launch(Dispatchers.IO) {
            val updatedChannels = channelList?.map {
                it.timeWatched = 0L
                it
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
        binding.containerWatchhistoryVodInfo.visibility = View.VISIBLE
    }

    fun openSeriesDetailFragment() {
        helpViewModel.isWatchHistoryContainerOpened = true
        val transaction = parentFragmentManager.beginTransaction()
        transaction.add(R.id.container_watchhistory_vod_info, SeriesDetailFragment())
        transaction.addToBackStack(null)
        transaction.commit()
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

    private val backStackListener = FragmentManager.OnBackStackChangedListener {
        if (isAdded && parentFragmentManager.fragments.lastOrNull() == this && helpViewModel.isSearchContainerOpened) {
            binding.recyclerItems.requestFocus()
            helpViewModel.isWatchHistoryContainerOpened = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        parentFragmentManager.removeOnBackStackChangedListener(backStackListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        helpViewModel.currentSelectedWatchHistory = ""
        helpViewModel.isWatchHistoryContainerOpened = false
        moviesAdapter = null
        seriesAdapter = null
        tvchannelsAdapter = null
        _binding = null
    }
}