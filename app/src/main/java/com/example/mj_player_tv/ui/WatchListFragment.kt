package com.example.mj_player_tv.ui

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
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
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.database.entity.Programme_
import com.example.mj_player_tv.database.entity.SeasonsOB
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.database.entity.SeriesOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.entity.TvChannelOB_
import com.example.mj_player_tv.databinding.FragmentHomeBinding
import com.example.mj_player_tv.databinding.FragmentTvChannelsBinding
import com.example.mj_player_tv.databinding.FragmentWatchlistBinding
import com.example.mj_player_tv.ui.adapter.GlobalSearchPlaylistAdapter
import com.example.mj_player_tv.ui.adapter.GlobalSearchTvChannelsAdapter
import com.example.mj_player_tv.ui.adapter.WatchListMoviesAdapter
import com.example.mj_player_tv.ui.adapter.WatchListProgrammeAdapter
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
import androidx.core.view.isVisible
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isNotEmpty
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.help.GlobalSearchDisplayItem
import com.example.mj_player_tv.database.help.GlobalSearchMainCategory
import com.example.mj_player_tv.database.help.WatchlistDisplayItem
import com.example.mj_player_tv.database.help.WatchlistItem
import com.example.mj_player_tv.database.help.WatchlistMainCategory
import com.example.mj_player_tv.ui.adapter.WatchlistItemsAdapter
import com.example.mj_player_tv.viewmodel.MoviesViewModel
import com.example.mj_player_tv.viewmodel.MoviesViewModelFactory
import com.example.mj_player_tv.viewmodel.SeriesViewModel
import com.example.mj_player_tv.viewmodel.SeriesViewModelFactory
import com.rubensousa.dpadrecyclerview.spacing.DpadGridSpacingDecoration
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
class WatchListFragment : Fragment(R.layout.fragment_watchlist) {

    private var _binding: FragmentWatchlistBinding? = null

    private val binding get() = _binding!!

    private lateinit var playlistAdapter: WatchlistPlaylistAdapter
    private lateinit var watchlistItemsAdapter: WatchlistItemsAdapter
    private lateinit var watchlistProgramsAdapter: WatchListProgrammeAdapter

    private var moviesByAccount: Map<Accounts, List<MovieOB>>? = null

    private var seriesByAccount: Map<Accounts, List<SeriesOB>>? = null

    private var programsByAccount: Map<Accounts, List<Programme>>? = null

    private val epgSourceBox = ObjectBox.store.boxFor(EpgSource::class.java)

    private var selectedWatchlistCategory: WatchlistMainCategory? = null

    private var lastLoadedCategory: WatchlistMainCategory? = null

    private var selectedAccount: Accounts? = null

    private var isFirstOpenWatchlist = true

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
        _binding = FragmentWatchlistBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        helpViewModel.fetchWatchListData()
        preparePlaylistRecyclerView()
        prepareItemsRecyclerView()
        prepareProgramsRecyclerview()

        parentFragmentManager.addOnBackStackChangedListener(backStackListener)

        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.watchlistResults.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED).collectLatest { results ->

                val movieResults = results.filterIsInstance<WatchlistItem.Movies>()
                moviesByAccount = movieResults.groupBy(
                    keySelector = { it.account },
                    valueTransform = { it.movies }
                ).mapValues { it.value.flatten() }

                val seriesResults = results.filterIsInstance<WatchlistItem.Series>()
                seriesByAccount = seriesResults.groupBy(
                    keySelector = { it.account },
                    valueTransform = { it.series }
                ).mapValues { it.value.flatten() }

                val programsResults = results.filterIsInstance<WatchlistItem.Programs>()
                programsByAccount = programsResults.groupBy(
                    keySelector = { it.account },
                    valueTransform = { it.programs }
                ).mapValues { it.value.flatten() }

                if (isFirstOpenWatchlist) {
                    val (firstCategory, firstMap) = listOf(
                        WatchlistMainCategory.MOVIES to moviesByAccount,
                        WatchlistMainCategory.SERIES to seriesByAccount,
                        WatchlistMainCategory.PROGRAMS to programsByAccount
                    ).firstOrNull { it.second?.isNotEmpty() == true }
                        ?: return@collectLatest // keine Ergebnisse

                    val firstAccount = firstMap?.keys?.minByOrNull { it.name } ?: return@collectLatest

                    helpViewModel.selectedWatchlistCategory = firstCategory
                    selectedWatchlistCategory = firstCategory
                    helpViewModel.selectedWatchlistAccount = firstAccount
                    selectedAccount = firstAccount

                    val initialPlaylists = firstMap.keys.toList().sortedBy { it.name }
                    playlistAdapter.submitList(initialPlaylists)

                    // 👉 Rufe updateMovieUI() direkt nach dem Laden der Movies auf

                    when (firstCategory) {
                        WatchlistMainCategory.MOVIES -> {
                            binding.constraintVod.visibility = View.VISIBLE
                            val firstMovie = moviesByAccount?.get(firstAccount)?.firstOrNull()
                            firstMovie?.let {
                                updateMovieUi(it)
                            }
                        }
                        WatchlistMainCategory.SERIES -> {
                            binding.constraintVod.visibility = View.VISIBLE
                            val firstSerie = seriesByAccount?.get(firstAccount)?.firstOrNull()
                            firstSerie?.let {
                                updateSeriesUi(it)
                            }
                        }
                        WatchlistMainCategory.PROGRAMS -> {
                            binding.constraintProgramme.visibility = View.VISIBLE
                            val firstProgram = programsByAccount?.get(firstAccount)?.firstOrNull()
                            firstProgram?.let {
                                setProgramDetails(firstProgram)
                                binding.recyclerProgramme.requestFocus()
                            }
                        }
                    }

                    binding.root.post {
                        when (firstCategory) {
                            WatchlistMainCategory.MOVIES -> {
                                binding.rvWatchlistMovies.requestFocus()
                            }
                            WatchlistMainCategory.SERIES -> binding.rvWatchlistSeries.requestFocus()
                            WatchlistMainCategory.PROGRAMS -> binding.rvWatchlistProgramme.requestFocus()
                        }
                    }
                    isFirstOpenWatchlist = false
                }

                selectedAccount?.let { account ->
                    selectedWatchlistCategory?.let { cat ->
                        val items = getDisplayableItemsFor(account, cat)
                        when (cat) {
                            WatchlistMainCategory.PROGRAMS -> {
                                val programItems = items.filterIsInstance<WatchlistDisplayItem.ProgramItem>()
                                watchlistProgramsAdapter.submitList(programItems)
                            } else -> {
                                watchlistItemsAdapter.submitList(items)
                            }
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.watchlistSearching.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED).collectLatest { searching ->
                if (searching) {
                    // Zeige z.B. ProgressBar, lade Spinner etc.
                    binding.tvNodatafound.visibility = View.GONE
                    binding.loadingprogressBar.visibility = View.VISIBLE
                } else {
                    if (!helpViewModel.hasFetchedWatchlist) {
                        // Noch keine Suche gestartet -> kein "No Data" anzeigen
                        binding.tvNodatafound.visibility = View.GONE
                        return@collectLatest
                    }

                    // Verberge ProgressBar, zeige UI mit Ergebnissen
                    binding.loadingprogressBar.visibility = View.GONE
                    val hasResults = !(moviesByAccount?.values?.flatten().isNullOrEmpty()
                            && seriesByAccount?.values?.flatten().isNullOrEmpty()
                            && programsByAccount?.values?.flatten().isNullOrEmpty())

                    if (hasResults) {
                        binding.tvNodatafound.visibility = View.GONE
                    } else {
                        binding.tvNodatafound.visibility = View.VISIBLE
                        binding.rvWatchlistMovies.requestFocus()
                    }
                }
            }
        }


        binding.rvWatchlistMovies.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.rvWatchlistMovies.isSelected = true
                binding.rvWatchlistSeries.isSelected = false
                binding.rvWatchlistProgramme.isSelected = false
                onCategorySelected(WatchlistMainCategory.MOVIES)
            }
        }

        binding.rvWatchlistMovies.setOnKeyListener { _, keyCode, event ->
            if (((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) && event.action == KeyEvent.ACTION_DOWN) {
                if (playlistAdapter.currentList.isNotEmpty()) {
                    binding.recyclerPlaylists.requestFocus()
                } else {
                    binding.rvWatchlistMovies.requestFocus()
                }
                return@setOnKeyListener true
            } else if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                helpViewModel.watchstatsContainerOpened = false
                helpViewModel.requestFocusOnWatchListCard(true)
                parentFragmentManager.popBackStack()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.rvWatchlistSeries.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.rvWatchlistMovies.isSelected = false
                binding.rvWatchlistSeries.isSelected = true
                binding.rvWatchlistProgramme.isSelected = false
                onCategorySelected(WatchlistMainCategory.SERIES)
            }
        }

        binding.rvWatchlistSeries.setOnKeyListener { _, keyCode, event ->
            if (((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) && event.action == KeyEvent.ACTION_DOWN) {
                if (playlistAdapter.currentList.isNotEmpty()) {
                    binding.recyclerPlaylists.requestFocus()
                } else {
                    binding.rvWatchlistMovies.requestFocus()
                }
                return@setOnKeyListener true
            } else if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                helpViewModel.watchstatsContainerOpened = false
                helpViewModel.requestFocusOnWatchListCard(true)
                parentFragmentManager.popBackStack()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.rvWatchlistProgramme.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.rvWatchlistMovies.isSelected = false
                binding.rvWatchlistSeries.isSelected = false
                binding.rvWatchlistProgramme.isSelected = true
                onCategorySelected(WatchlistMainCategory.PROGRAMS)
            }
        }

        binding.rvWatchlistProgramme.setOnKeyListener { _, keyCode, event ->
            if (((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) && event.action == KeyEvent.ACTION_DOWN) {
                if (playlistAdapter.currentList.isNotEmpty()) {
                    binding.recyclerPlaylists.requestFocus()
                } else {
                    binding.rvWatchlistMovies.requestFocus()
                }
                return@setOnKeyListener true
            } else if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                helpViewModel.watchstatsContainerOpened = false
                helpViewModel.requestFocusOnWatchListCard(true)
                parentFragmentManager.popBackStack()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        seriesViewModel.focusToSeriesRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                if (watchlistItemsAdapter.currentList.isNotEmpty()) {
                    binding.recyclerItems.requestFocus()
                } else {
                    binding.rvWatchlistSeries.requestFocus()
                }
                seriesViewModel.clearFocusToSeries()
            }
        }

        moviesViewModel.focusToMoviesRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                if (watchlistItemsAdapter.currentList.isNotEmpty()) {
                    binding.recyclerItems.requestFocus()
                } else {
                    binding.rvWatchlistMovies.requestFocus()
                }
                moviesViewModel.clearFocusToMovies()
            }
        }
    }

    fun focusToPlaylist() {
        binding.recyclerPlaylists.requestFocus()
    }

    private fun preparePlaylistRecyclerView() {
        playlistAdapter = WatchlistPlaylistAdapter(helpViewModel, this)
        binding.recyclerPlaylists.apply {
            adapter = playlistAdapter
            setFocusOutAllowed(throughFront = false, throughBack = false)
            setFocusOutSideAllowed(throughFront = true, throughBack = true)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private fun prepareItemsRecyclerView() {
        watchlistItemsAdapter = WatchlistItemsAdapter(helpViewModel, this, epgSourceBox) { clickedItem ->
            when (clickedItem) {
                is WatchlistDisplayItem.MovieItem -> {
                    if (selectedAccount != null) {
                        if (selectedAccount!!.isXtream) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val xtreamMovie = xtreamViewModel.getXtreamMovieDetails(clickedItem.movie, selectedAccount!!)
                                helpViewModel.currentFocusedMovie = xtreamMovie
                                helpViewModel.currentMovieAccount = selectedAccount
                                openMovieDetailFragment()
                            }
                        } else if (selectedAccount!!.isStalker) {
                            helpViewModel.currentMovieAccount = selectedAccount
                            helpViewModel.currentFocusedMovie = clickedItem.movie
                            openMovieDetailFragment()
                        }
                    }
                }
                is WatchlistDisplayItem.SeriesItem -> {
                    if (selectedAccount != null) {
                        if (selectedAccount!!.isXtream) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val seasons =
                                    xtreamViewModel.getXtreamSerieDetails(clickedItem.series, selectedAccount!!)
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
                                stalkerViewModel.getSeriesDetail(clickedItem.series, selectedAccount!!)
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
                is WatchlistDisplayItem.ProgramItem -> {
                    return@WatchlistItemsAdapter
                }
            }
        }
        binding.recyclerItems.apply {
            adapter = watchlistItemsAdapter
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private fun prepareProgramsRecyclerview() {
        watchlistProgramsAdapter = WatchListProgrammeAdapter(onProgrammeClickListerner, this, helpViewModel)
        binding.recyclerProgramme.apply {
            adapter = watchlistProgramsAdapter
            setFocusOutAllowed(throughFront = false, throughBack = false)
            setFocusOutSideAllowed(throughFront = true, throughBack = true)
            setSmoothFocusChangesEnabled(false)
            addItemDecoration(DpadLinearSpacingDecoration.create(
                    itemSpacing = 6,
                    edgeSpacing = 10,
                    perpendicularEdgeSpacing = 10
                )
            )
        }
    }

    private val onProgrammeClickListerner = WatchListProgrammeAdapter.OnClickListener {

    }

    fun openMovieDetailFragment() {
        helpViewModel.isWatchlistContainerOpened = true
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_watchlist_vod_info, MovieDetailFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.containerWatchlistVodInfo.visibility = View.VISIBLE
    }

    fun openSeriesDetailFragment() {
        helpViewModel.isWatchlistContainerOpened = true
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_watchlist_vod_info, SeriesDetailFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.containerWatchlistVodInfo.visibility = View.VISIBLE
    }

    fun updateItemList(account: Accounts) {
        if (account == selectedAccount && selectedWatchlistCategory == lastLoadedCategory) {
            return
        }
        if (selectedWatchlistCategory != null) {
            when (selectedWatchlistCategory) {
                WatchlistMainCategory.PROGRAMS -> {
                    watchlistProgramsAdapter.submitList(null)

                } else -> {
                    watchlistItemsAdapter.submitList(null)
                }
            }
            val oldPosition = playlistAdapter.currentList.indexOf(helpViewModel.selectedWatchlistAccount)
            val newPosition = playlistAdapter.currentList.indexOf(account)
            helpViewModel.selectedGlobalSearchAccount = account
            selectedAccount = account
            lastLoadedCategory = selectedWatchlistCategory
            playlistAdapter.notifyItemChanged(oldPosition)
            playlistAdapter.notifyItemChanged(newPosition)
            val items = getDisplayableItemsFor(account, selectedWatchlistCategory!!)
            if (items.isNotEmpty()) {
                when (selectedWatchlistCategory) {
                    WatchlistMainCategory.PROGRAMS -> {
                        val programItems = items.filterIsInstance<WatchlistDisplayItem.ProgramItem>()
                        watchlistProgramsAdapter.submitList(programItems)
                        val firstProgram = programItems.firstOrNull()?.programs
                        firstProgram?.let { setProgramDetails(it) }
                    }
                    WatchlistMainCategory.SERIES-> {
                        watchlistItemsAdapter.submitList(items)
                        val firstSeries = items
                            .filterIsInstance<WatchlistDisplayItem.SeriesItem>()
                            .firstOrNull()
                            ?.series
                        firstSeries?.let { updateSeriesUi(it) }
                    }
                    WatchlistMainCategory.MOVIES -> {
                        watchlistItemsAdapter.submitList(items)
                        val firstMovie = items
                            .filterIsInstance<WatchlistDisplayItem.MovieItem>()
                            .firstOrNull()
                            ?.movie
                        firstMovie?.let { updateMovieUi(it) }
                    }
                    null -> {}
                }
            }
        }
    }

    private fun getDisplayableItemsFor(account: Accounts, category: WatchlistMainCategory): List<WatchlistDisplayItem> {
        return when (category) {
            WatchlistMainCategory.PROGRAMS -> programsByAccount?.get(account)?.map { WatchlistDisplayItem.ProgramItem(it) } ?: emptyList()
            WatchlistMainCategory.MOVIES -> moviesByAccount?.get(account)?.map { WatchlistDisplayItem.MovieItem(it) } ?: emptyList()
            WatchlistMainCategory.SERIES -> seriesByAccount?.get(account)?.map { WatchlistDisplayItem.SeriesItem(it) } ?: emptyList()
        }
    }

    fun onCategorySelected(category: WatchlistMainCategory) {
        if (selectedWatchlistCategory == category) {
            // Kategorie ist schon aktiv → nichts tun
            return
        }
        playlistAdapter.submitList(null)
        watchlistItemsAdapter.submitList(null)
        helpViewModel.selectedWatchlistCategory = category
        selectedWatchlistCategory = category
        val accounts = when (category) {
            WatchlistMainCategory.MOVIES -> {
                helpViewModel.currentFocusedMovie = null
                resetSeriesDetailsUi()
                binding.recyclerItems.setSpanCount(7)
                moviesByAccount?.keys?.toList()
            }
            WatchlistMainCategory.SERIES -> {
                helpViewModel.currentFocusedSerie = null
                resetMovieDetailsUi()
                resetProgramDetails()
                hideProgramsLayout()
                showVodLayout()
                binding.recyclerItems.setSpanCount(7)
                seriesByAccount?.keys?.toList()
            }
            WatchlistMainCategory.PROGRAMS -> {
                hideVodLayout()
                showProgramsLayout()
                resetSeriesDetailsUi()
                binding.recyclerItems.setSpanCount(1)
                programsByAccount?.keys?.toList()
            }
        }?.sortedBy { it.name }

        if (!accounts.isNullOrEmpty()) {
            binding.tvNodatafound.visibility = View.INVISIBLE
            playlistAdapter.submitList(accounts)
            helpViewModel.selectedWatchlistAccount = accounts.firstOrNull()
            selectedAccount = accounts.firstOrNull()
            selectedAccount?.let { updateItemList(it) }
        } else {
            lastLoadedCategory = category
            helpViewModel.selectedWatchlistAccount = null
            selectedAccount = null
            binding.tvNodatafound.visibility = View.VISIBLE
        }
    }

    private fun hideVodLayout() {
        binding.constraintVod.visibility = View.GONE
    }

    private fun showVodLayout() {
        binding.constraintVod.visibility = View.VISIBLE
    }

    private fun hideProgramsLayout() {
        binding.constraintProgramme.visibility = View.GONE
    }

    private fun showProgramsLayout() {
        binding.constraintProgramme.visibility = View.VISIBLE
    }


    fun focusToItems() {
        when (selectedWatchlistCategory) {
            WatchlistMainCategory.PROGRAMS -> {
                binding.recyclerProgramme.requestFocus()
            } else -> {
                binding.recyclerItems.requestFocus()
            }
        }
    }
    fun focusToTextView() {
        when (selectedWatchlistCategory) {
            WatchlistMainCategory.MOVIES -> binding.rvWatchlistMovies.requestFocus()
            WatchlistMainCategory.SERIES -> binding.rvWatchlistSeries.requestFocus()
            WatchlistMainCategory.PROGRAMS -> binding.rvWatchlistProgramme.requestFocus()
            else -> {} // optional: nichts tun oder Default setzen
        }
    }

    fun updateMovieUi(movie: MovieOB) {
        if (helpViewModel.currentFocusedMovie?.idByAccountData != movie.idByAccountData) {
            helpViewModel.currentFocusedMovie = movie
            resetMovieDetailsUi()
            setMovieDetailsUi(movie)
        }
    }

    var currentTmdbMovieDetailJob: Job? = null

    fun setMovieDetailsUi(movie: MovieOB) {
        val settings = helpViewModel.settings
        if (settings != null) {
            binding.ivFavorite.visibility = if (movie.isFavorite) {
                View.VISIBLE
            } else {
                View.GONE
            }
            binding.tvTotSeasons.visibility = View.GONE
            setMovieDetailsNotImages(movie)
            currentTmdbMovieDetailJob?.cancel()
            currentTmdbMovieDetailJob = viewLifecycleOwner.lifecycleScope.launch {
                if (movie.movieAccount.target.isXtream) {
                    if (!xtreamViewModel.modifiedXtreamMovies.contains(movie.idByAccountData)) {
                        val thisMovie = xtreamViewModel.getXtreamMovieDetails(movie, movie.movieAccount.target)
                        movie.movieTime = thisMovie.movieTime
                        movie.director = thisMovie.director
                        movie.actors = thisMovie.actors
                        movie.description = thisMovie.description
                        movie.age = thisMovie.age
                        movie.country = thisMovie.country
                        movie.genres_str = thisMovie.genres_str
                        movie.backdropPath = thisMovie.backdropPath
                        movie.tmdb_id = thisMovie.tmdb_id.toString()
                        xtreamViewModel.modifiedXtreamMovies.add(movie.idByAccountData)
                    }
                    if (!movie.backdropPath.isNullOrEmpty()) {
                        if (!movie.tmdb_id.isNullOrEmpty() && settings.tmdbApiKey.isNotEmpty()) {
                            if (movie.tmdb_id!!.startsWith("tt")) {
                                val tmdbMovieDetailsByImdbId =
                                    helpViewModel.getTmdbMovieDetailsByImdb(
                                        url = "https://api.themoviedb.org/3/find/",
                                        imdbId = movie.tmdb_id!!,
                                        apiKey = settings.tmdbApiKey
                                    ).await()
                                when (tmdbMovieDetailsByImdbId) {
                                    is Resource.Success -> {
                                        val backgroundImage =
                                            tmdbMovieDetailsByImdbId.data?.movie_results?.first()?.backdrop_path.let { "https://image.tmdb.org/t/p/original$it" }
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(backgroundImage)
                                        helpViewModel.currentMovieImage = backgroundImage
                                    }

                                    is Resource.Error -> {
                                        val moviePoster = movie.screenshot_uri
                                        if (!moviePoster.isNullOrEmpty()) {
                                            binding.ivMovieposter.visibility = View.VISIBLE
                                            binding.ivMovieposter.load(moviePoster)
                                            helpViewModel.currentMovieImage = moviePoster
                                        } else {
                                            helpViewModel.currentMovieImage = ""
                                            binding.ivMovieposter.visibility = View.INVISIBLE
                                        }
                                    }
                                }
                            } else {
                                val tmdbMovieDetails = helpViewModel.getTmdbMovieDetails(
                                    url = "https://api.themoviedb.org/3/movie/",
                                    movieId = movie.tmdb_id!!.toInt(),
                                    apiKey = settings.tmdbApiKey
                                ).await()
                                when (tmdbMovieDetails) {
                                    is Resource.Success -> {
                                        val backgroundImage =
                                            tmdbMovieDetails.data?.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(backgroundImage)
                                        helpViewModel.currentMovieImage = backgroundImage
                                    }

                                    is Resource.Error -> {
                                        val moviePoster = movie.screenshot_uri
                                        if (!moviePoster.isNullOrEmpty()) {
                                            binding.ivMovieposter.visibility = View.VISIBLE
                                            binding.ivMovieposter.load(moviePoster)
                                            helpViewModel.currentMovieImage = moviePoster
                                        } else {
                                            helpViewModel.currentMovieImage = ""
                                            binding.ivMovieposter.visibility = View.INVISIBLE
                                        }
                                    }
                                }
                            }
                        } else {
                            if (!movie.screenshot_uri.isNullOrEmpty()) {
                                binding.ivMovieposter.visibility = View.VISIBLE
                                binding.ivMovieposter.load(movie.screenshot_uri)
                                helpViewModel.currentMovieImage = movie.screenshot_uri
                            } else {
                                helpViewModel.currentMovieImage = ""
                                binding.ivMovieposter.visibility = View.INVISIBLE
                            }
                        }
                    } else {
                        binding.ivMovieposter.visibility = View.VISIBLE
                        binding.ivMovieposter.load(movie.backdropPath)
                        helpViewModel.currentMovieImage = movie.backdropPath
                    }
                    setMovieDetailsNotImages(movie)
                } else {
                    if (movie.tmdb_id!!.isNotEmpty() && settings.tmdbApiKey.isNotEmpty()) {
                        if (movie.tmdb_id!!.startsWith("tt")) {
                            val tmdbMovieDetailsByImdbId = helpViewModel.getTmdbMovieDetailsByImdb(
                                url = "https://api.themoviedb.org/3/find/",
                                imdbId = movie.tmdb_id!!,
                                apiKey = settings.tmdbApiKey
                            ).await()
                            when (tmdbMovieDetailsByImdbId) {
                                is Resource.Success -> {
                                    val backgroundImage =
                                        tmdbMovieDetailsByImdbId.data?.movie_results?.first()?.backdrop_path.let { "https://image.tmdb.org/t/p/original$it" }
                                    binding.ivMovieposter.visibility = View.VISIBLE
                                    binding.ivMovieposter.load(backgroundImage)
                                    helpViewModel.currentMovieImage = backgroundImage
                                }

                                is Resource.Error -> {
                                    val moviePoster = movie.screenshot_uri
                                    if (!moviePoster.isNullOrEmpty()) {
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(moviePoster)
                                        helpViewModel.currentMovieImage = moviePoster
                                    } else {
                                        binding.ivMovieposter.visibility = View.INVISIBLE
                                    }
                                }
                            }
                        } else {
                            val tmdbMovieDetails = helpViewModel.getTmdbMovieDetails(
                                url = "https://api.themoviedb.org/3/movie/",
                                movieId = movie.tmdb_id!!.toInt(),
                                apiKey = settings.tmdbApiKey
                            ).await()
                            when (tmdbMovieDetails) {
                                is Resource.Success -> {
                                    val backgroundImage =
                                        tmdbMovieDetails.data?.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
                                    binding.ivMovieposter.visibility = View.VISIBLE
                                    binding.ivMovieposter.load(backgroundImage)
                                    helpViewModel.currentMovieImage = backgroundImage
                                }

                                is Resource.Error -> {
                                    val moviePoster = movie.screenshot_uri
                                    if (!moviePoster.isNullOrEmpty()) {
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(moviePoster)
                                        helpViewModel.currentMovieImage = moviePoster
                                    } else {
                                        binding.ivMovieposter.visibility = View.INVISIBLE
                                    }
                                }
                            }
                        }
                    } else {
                        currentTmdbMovieDetailJob?.cancel()
                        if (!movie.screenshot_uri.isNullOrEmpty()) {
                            binding.ivMovieposter.visibility = View.VISIBLE
                            binding.ivMovieposter.load(movie.screenshot_uri)
                            helpViewModel.currentMovieImage = movie.screenshot_uri
                        } else {
                            binding.ivMovieposter.visibility = View.INVISIBLE
                        }
                    }
                }
            }
        }
    }

    fun setMovieDetailsNotImages(movie: MovieOB) {
        binding.tvMovietitle.text = if (!movie.movieName.isNullOrEmpty()) {
            binding.tvMovietitle.visibility = View.VISIBLE
            movie.movieName
        } else {
            "No Title!"
        }
        binding.tvMovietitle.isSelected = true
        binding.tvDuration.text = if (movie.movieTime != null) {
            binding.tvDuration.visibility = View.VISIBLE
            val durationText = formatDuration(movie.movieTime!!, movie.accountId!!)
            durationText
        } else {
            binding.tvDuration.visibility = View.VISIBLE
            "0min"
        }
        binding.tvReleaseyear.text = if (!movie.movieYear.isNullOrEmpty()) {
            binding.tvReleaseyear.visibility = View.VISIBLE
            val year = if (movie.movieYear.length >= 4) movie.movieYear.substring(0, 4) else "n/a"
            year
        } else {
            binding.tvReleaseyear.visibility = View.VISIBLE
            "n/a"
        }
        binding.tvCategories.text = if (!movie.genres_str.isNullOrEmpty()) {
            binding.tvCategories.visibility = View.VISIBLE
            movie.genres_str
        } else {
            binding.tvCategories.visibility = View.INVISIBLE
            ""
        }
        binding.tvMoviedescription.text = if (!movie.description.isNullOrEmpty()) {
            binding.tvMoviedescription.visibility = View.VISIBLE
            movie.description
        } else {
            "No description available"
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = movie.percentagePlayed.toInt()

        binding.tvRating.text = if (!movie.rating_imdb.isNullOrEmpty()) {
            binding.tvRating.visibility = View.VISIBLE
            val formattedRating = formatRating(movie.rating_imdb)
            formattedRating
        } else {
            binding.tvRating.visibility = View.VISIBLE
            "0.0"
        }
        binding.smallRating.rating = if (!movie.rating_imdb.isNullOrEmpty()) {
            binding.smallRating.visibility = View.VISIBLE
            val formattedRating = formatRating(movie.rating_imdb)
            val ratingValue = formattedRating.toFloatOrNull() ?: 0.0f
            (ratingValue / 2.0f) // Teile die Bewertung durch 2, um sie auf die Skala der 5 Sterne anzupassen
        } else {
            binding.smallRating.visibility = View.VISIBLE
            0.0f // Wenn die Bewertung leer oder null ist, setze die Bewertung auf 0
        }
        binding.tvActor.visibility = View.VISIBLE
        binding.tvActors.text = if (!movie.actors.isNullOrEmpty()) {
            binding.tvActors.visibility = View.VISIBLE
            movie.actors
        } else {
            binding.tvActors.visibility = View.VISIBLE
            "n/a"
        }
        binding.tvDirector.visibility = View.VISIBLE
        binding.tvDirectors.text = if (!movie.director.isNullOrEmpty()) {
            binding.tvDirectors.visibility = View.VISIBLE
            movie.director
        } else {
            binding.tvDirectors.visibility = View.VISIBLE
            "n/a"
        }
        binding.tvAge.text = if (!movie.age.isNullOrEmpty()) {
            binding.tvAge.visibility = View.VISIBLE
            movie.age
        } else {
            binding.tvAge.visibility = View.INVISIBLE
            ""
        }

        if (movie.isPartlyWatched) {
            binding.tvRemainingTime.visibility = View.VISIBLE

            // Prüfe, ob die movieTime in Minuten oder Sekunden ist
            val movieTimeInMinutes = if (movie.movieAccount.target.isXtream) {
                (movie.movieTime ?: 0) / 60 // Sekunden zu Minuten umrechnen
            } else {
                movie.movieTime ?: 0 // Bereits in Minuten
            }

            // Berechne die verbleibende Zeit
            val remainingTimeMinutes = movieTimeInMinutes - (movieTimeInMinutes * movie.percentagePlayed)

            // Formatierung der verbleibenden Zeit
            val remainingTimeText = if (remainingTimeMinutes < 60) {
                "${remainingTimeMinutes.toInt()}min remaining"
            } else {
                val hours = remainingTimeMinutes.toInt() / 60
                val minutes = remainingTimeMinutes.toInt() % 60
                "${hours}h ${minutes}min remaining"
            }

            binding.tvRemainingTime.text = remainingTimeText
            binding.progressBar.progress = (movie.percentagePlayed * 100).toInt()
        } else if (movie.isCompletelyWatched) {
            binding.tvRemainingTime.visibility = View.VISIBLE
            binding.tvRemainingTime.text = "Completed!"
            binding.progressBar.progress = 100
        } else {
            binding.tvRemainingTime.visibility = View.INVISIBLE
        }
    }

    fun resetMovieDetailsUi() {
        binding.ivFavorite.visibility = View.INVISIBLE
        binding.ivMovieposter.visibility = View.INVISIBLE
        binding.tvMovietitle.visibility = View.INVISIBLE
        binding.tvDuration.visibility = View.INVISIBLE
        binding.tvReleaseyear.visibility = View.INVISIBLE
        binding.tvCategories.visibility = View.INVISIBLE
        binding.tvMoviedescription.visibility = View.INVISIBLE
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvRating.visibility = View.INVISIBLE
        binding.smallRating.visibility = View.INVISIBLE
        binding.tvActor.visibility = View.INVISIBLE
        binding.tvActors.visibility = View.INVISIBLE
        binding.tvDirectors.visibility = View.INVISIBLE
        binding.tvDirector.visibility = View.INVISIBLE
        binding.tvAge.visibility = View.INVISIBLE
        binding.tvRemainingTime.visibility = View.INVISIBLE
    }

    private fun formatDuration(duration: Int, accountId: Long): String {
        val currentAccount = selectedAccount

        return when {
            currentAccount?.isStalker == true -> {  // Dauer kommt in MINUTEN
                val hours = duration / 60
                val minutes = duration % 60
                formatTime(hours, minutes)
            }
            currentAccount?.isXtream == true -> {   // Dauer kommt in SEKUNDEN
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

    fun updateSeriesUi(serie: SeriesOB) {
        if (serie.idByAccountData != helpViewModel.currentFocusedSerie?.idByAccountData) {
            helpViewModel.currentFocusedSerie = serie
            resetSeriesDetailsUi()
            setSeriesDetailsUi(serie)
        }
    }

    var currentTmdbSerieDetailJob: Job? = null

    fun setSeriesDetailsUi(serie: SeriesOB) {
        helpViewModel.currentTmdBSeriesDetails = null
        val settings = helpViewModel.settings
        if (settings != null) {
            binding.ivFavorite.visibility = if (serie.isFavorite) {
                View.VISIBLE
            } else {
                View.GONE
            }
            setSeriesDetailsNotImages(serie)
            if ((serie.seriesAccount.target.isXtream && !xtreamViewModel.seriesCache.containsKey(serie.idByAccountData)) ||
                serie.seriesAccount.target.isStalker && !stalkerViewModel.seriesCache.containsKey(serie.idByAccountData)) {
                getSeriesDetailInfo(serie)
            } else {
                if (serie.seriesAccount.target.isXtream) {
                    helpViewModel.focusedSeasons = xtreamViewModel.seriesCache[serie.idByAccountData]?.first
                    helpViewModel.focusedEpisodes = xtreamViewModel.seriesCache[serie.idByAccountData]?.second?.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))?.toMutableList()
                    serie.totalSeasons = helpViewModel.focusedSeasons?.size ?: 1
                } else {
                    helpViewModel.focusedSeasons = stalkerViewModel.seriesCache[serie.idByAccountData]?.first
                    helpViewModel.focusedEpisodes = stalkerViewModel.seriesCache[serie.idByAccountData]?.second?.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))?.toMutableList()
                    serie.totalSeasons = helpViewModel.focusedSeasons?.size ?: 1
                }
            }
            currentTmdbSerieDetailJob?.cancel()
            currentTmdbSerieDetailJob = viewLifecycleOwner.lifecycleScope.launch {
                if (serie.backdropPath.isNullOrEmpty()) {
                    if (!serie.tmdb_id.isNullOrEmpty() && settings.tmdbApiKey.isNotEmpty()) {
                        if (serie.tmdb_id.startsWith("tt")) {
                            val tmdbSeriesDetailsByImdbId =
                                helpViewModel.getTmdbMovieDetailsByImdb(
                                    url = "https://api.themoviedb.org/3/find/",
                                    imdbId = serie.tmdb_id,
                                    apiKey = settings.tmdbApiKey
                                ).await()
                            when (tmdbSeriesDetailsByImdbId) {
                                is Resource.Success -> {
                                    val backgroundImage =
                                        tmdbSeriesDetailsByImdbId.data?.movie_results?.first()?.backdrop_path.let { "https://image.tmdb.org/t/p/original$it" }
                                    binding.ivMovieposter.visibility = View.VISIBLE
                                    binding.ivMovieposter.load(backgroundImage)
                                }

                                is Resource.Error -> {
                                    val seriesPoster = serie.screenshot_uri
                                    if (!seriesPoster.isNullOrEmpty()) {
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(seriesPoster)
                                    } else {
                                        binding.ivMovieposter.visibility = View.INVISIBLE
                                    }
                                }
                            }
                        } else {
                            val tmdbSerieDetails = helpViewModel.getTmdbSeriesDetails(
                                url = "https://api.themoviedb.org/3/tv/",
                                seriesId = serie.tmdb_id.toInt(),
                                apiKey = settings.tmdbApiKey
                            ).await()
                            when (tmdbSerieDetails) {
                                is Resource.Success -> {
                                    if (serie.backdropPath.isNullOrEmpty()) {
                                        val backgroundImage =
                                            tmdbSerieDetails.data?.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(backgroundImage)
                                        serie.backdropPath = backgroundImage ?: ""
                                    }
                                }
                                is Resource.Error -> {
                                    val seriesPoster = serie.screenshot_uri
                                    if (!seriesPoster.isNullOrEmpty()) {
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(seriesPoster)
                                    } else {
                                        binding.ivMovieposter.visibility = View.INVISIBLE
                                    }
                                }
                            }
                        }
                    } else {
                        if (!serie.screenshot_uri.isNullOrEmpty()) {
                            binding.ivMovieposter.visibility = View.VISIBLE
                            binding.ivMovieposter.load(serie.screenshot_uri)
                        } else {
                            binding.ivMovieposter.visibility = View.INVISIBLE
                        }
                    }
                } else {
                    binding.ivMovieposter.visibility = View.VISIBLE
                    binding.ivMovieposter.load(serie.backdropPath)
                }
            }
        }
    }

    var seriesDetailJob: Job? = null

    fun getSeriesDetailInfo(serie: SeriesOB) {
        seriesDetailJob?.cancel()
        seriesDetailJob = helpViewModel.viewModelScope.launch {
            if (serie.seriesAccount.target.isXtream) {
                val seasons =
                    xtreamViewModel.getXtreamSerieDetails(serie, serie.seriesAccount.target)
                serie.totalSeasons = seasons.size
                helpViewModel.focusedSeasons = seasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                    .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE }).toMutableList()
                helpViewModel.focusedEpisodes = xtreamViewModel.episodesList.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })).toMutableList()
                binding.tvTotSeasons.text = if (seasons.isEmpty()) {
                    binding.tvTotSeasons.visibility = View.INVISIBLE
                    ""
                } else {
                    binding.tvTotSeasons.visibility = View.VISIBLE
                    if (seasons.size == 1) {
                        "1 Season"
                    } else {
                        "${seasons.size} Seasons"
                    }
                }
            } else {
                stalkerViewModel.seriesDetailData.postValue(mutableListOf())
                stalkerViewModel.getSeriesDetail(serie, serie.seriesAccount.target)
                stalkerViewModel.seriesDetailData.observe(viewLifecycleOwner) { seasons ->
                    serie.totalSeasons = seasons.size
                    helpViewModel.focusedSeasons = seasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                        .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE }).toMutableList()
                    helpViewModel.focusedEpisodes = stalkerViewModel.episodesList.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })).toMutableList()
                    binding.tvTotSeasons.text = if (seasons.isEmpty()) {
                        binding.tvTotSeasons.visibility = View.INVISIBLE
                        ""
                    } else {
                        binding.tvTotSeasons.visibility = View.VISIBLE
                        if (seasons.size == 1) {
                            "1 Season"
                        } else {
                            "${seasons.size} Seasons"
                        }
                    }
                }
            }
            binding.tvTotSeasons.text = if (serie.totalSeasons != 0) {
                binding.tvTotSeasons.visibility = View.VISIBLE
                if (serie.totalSeasons == 1) {
                    "${serie.totalSeasons} Season"
                } else {
                    "${serie.totalSeasons} Seasons"
                }
            } else {
                binding.tvTotSeasons.visibility = View.INVISIBLE
                ""
            }
        }
    }

    fun setSeriesDetailsNotImages(serie: SeriesOB) {
        binding.tvDuration.visibility = View.GONE
        binding.tvMovietitle.text = if (serie.seriesName.isNotEmpty()) {
            binding.tvMovietitle.visibility = View.VISIBLE
            binding.tvMovietitle.isSelected = true
            serie.seriesName
        } else {
            "No Title!"
        }
        binding.tvReleaseyear.text = if (!serie.seriesYear.isNullOrEmpty()) {
            binding.tvReleaseyear.visibility = View.VISIBLE
            val year = if (serie.seriesYear.length >= 4) serie.seriesYear.substring(0, 4) else "n/a"
            year
        } else {
            binding.tvReleaseyear.visibility = View.VISIBLE
            "n/a"
        }
        binding.tvAge.text = if (!serie.age.isNullOrEmpty()) {
            binding.tvAge.visibility = View.VISIBLE
            serie.age
        } else {
            binding.tvAge.visibility = View.GONE
            ""
        }
        binding.tvCategories.text = if (!serie.genres_str.isNullOrEmpty()) {
            binding.tvCategories.visibility = View.VISIBLE
            serie.genres_str
        } else {
            binding.tvCategories.visibility = View.INVISIBLE
            ""
        }
        binding.tvMoviedescription.text = if (!serie.description.isNullOrEmpty()) {
            binding.tvMoviedescription.visibility = View.VISIBLE
            serie.description
        } else {
            "No description available"
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = serie.seriesPercentagePlayed.toInt()

        binding.tvRating.text = if (!serie.rating_imdb.isNullOrEmpty()) {
            binding.tvRating.visibility = View.VISIBLE
            val formattedRating = formatRating(serie.rating_imdb)
            formattedRating
        } else {
            binding.tvRating.visibility = View.VISIBLE
            "0.0"
        }
        binding.smallRating.rating = if (!serie.rating_imdb.isNullOrEmpty()) {
            binding.smallRating.visibility = View.VISIBLE
            val formattedRating = formatRating(serie.rating_imdb)
            val ratingValue = formattedRating.toFloatOrNull() ?: 0.0f
            (ratingValue / 2.0f) // Teile die Bewertung durch 2, um sie auf die Skala der 5 Sterne anzupassen
        } else {
            binding.smallRating.visibility = View.VISIBLE
            0.0f // Wenn die Bewertung leer oder null ist, setze die Bewertung auf 0
        }
        binding.tvActor.visibility = View.VISIBLE
        binding.tvActors.text = if (!serie.actors.isNullOrEmpty()) {
            binding.tvActors.visibility = View.VISIBLE
            serie.actors
        } else {
            binding.tvActors.visibility = View.VISIBLE
            "n/a"
        }
        binding.tvDirector.visibility = View.VISIBLE
        binding.tvDirectors.text = if (!serie.director.isNullOrEmpty()) {
            binding.tvDirectors.visibility = View.VISIBLE
            serie.director
        } else {
            binding.tvDirectors.visibility = View.VISIBLE
            "n/a"
        }
        if (serie.seriesPercentagePlayed != 0.0) {
            if (serie.isCompletelyWatched) {
                binding.progressBar.progress = 100
                binding.tvRemainingTime.visibility = View.VISIBLE
                binding.tvRemainingTime.text = "Completed!"
            } else if (serie.isPartlyWatched) {
                binding.tvRemainingTime.visibility = View.VISIBLE
                // Schritt 1: Berechne den Fortschritt in Prozent
                val progressPercentage = serie.seriesPercentagePlayed * 100  // Wandelt den Fortschritt in Prozent um (von 0.0 bis 100.0)

// Schritt 2: Runden auf maximal 2 Dezimalstellen
                val formattedPercentage = String.format("%.2f", progressPercentage)  // Formatierung auf 2 Dezimalstellen

// Schritt 3: Update der ProgressBar
                val progressBarPercentage = (serie.seriesPercentagePlayed * 100).toInt()  // ProgressBar erwartet einen Integer zwischen 0 und 100
                binding.progressBar.progress = progressBarPercentage

// Schritt 4: Anzeige des Fortschritts als Text
                binding.tvRemainingTime.text = "$formattedPercentage% watched.."  // Zeigt den Fortschritt als Text im Prozentformat an

            } else {
                binding.tvRemainingTime.visibility = View.INVISIBLE
            }
        } else {
            binding.tvRemainingTime.visibility = View.INVISIBLE
        }
    }

    fun resetSeriesDetailsUi() {
        binding.ivFavorite.visibility = View.INVISIBLE
        binding.ivMovieposter.visibility = View.INVISIBLE
        binding.tvMovietitle.visibility = View.INVISIBLE
        binding.tvReleaseyear.visibility = View.INVISIBLE
        binding.tvCategories.visibility = View.INVISIBLE
        binding.tvMoviedescription.visibility = View.INVISIBLE
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvRating.visibility = View.INVISIBLE
        binding.smallRating.visibility = View.INVISIBLE
        binding.tvActor.visibility = View.INVISIBLE
        binding.tvActors.visibility = View.INVISIBLE
        binding.tvDirectors.visibility = View.INVISIBLE
        binding.tvDirector.visibility = View.INVISIBLE
        binding.tvAge.visibility = View.INVISIBLE
        binding.tvTotSeasons.visibility = View.INVISIBLE
        binding.tvRemainingTime.visibility = View.INVISIBLE
        binding.tvDuration.visibility = View.INVISIBLE
    }

    fun setProgramDetails(programme: Programme) {
        resetProgramDetails()
        setProgramUi(programme)
    }

    private fun setProgramUi(program: Programme) {
        if (binding.relLayoutProgramdescr.isInvisible) {
            binding.relLayoutProgramdescr.visibility = View.VISIBLE
        }
        val epgDataOB = program.epgData.target
        binding.tvCurrentProgram.text = epgDataOB.name
        binding.tvCurrentSubtitle.text = epgDataOB.sub_title
        binding.tvCurrentSubtitle.visibility = if (epgDataOB.sub_title.isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.tvDescription.text = epgDataOB.descr.ifEmpty {
            "no description available.."
        }

    }

    fun resetProgramDetails() {
        binding.tvCurrentProgram.text = ""
        binding.tvCurrentSubtitle.text = ""
        binding.tvCurrentCategory.text = ""
        binding.tvCurrentCountry.text = ""
        binding.tvCurrentDate.text = ""
        binding.tvDescription.text = ""
    }

    fun formatUnixTimestampToTime(unixTimestamp: Long, timeOffset: Int): String {
        try {
            // Konvertiere den Unix-Zeitstempel in ein Date-Objekt
            val date = Date(unixTimestamp * 1000)

            // Erstelle ein SimpleDateFormat-Objekt für das gewünschte Zeitformat
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            // Berechne den Zeitversatz in Stunden (positiv oder negativ)
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.add(Calendar.HOUR_OF_DAY, timeOffset)

            // Gib das formatierte Datum und die Uhrzeit zurück
            return timeFormat.format(calendar.time)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    fun updateMovie(movie: MovieOB) {
        // Entferne Film

    }

    fun updateSerie(serie: SeriesOB) {
        // Entferne Serie

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
        helpViewModel.currentFocusedMovie = null
        helpViewModel.currentFocusedSerie = null
        helpViewModel.selectedWatchlistCategory = null
        helpViewModel.selectedWatchlistAccount = null
        helpViewModel.isWatchlistContainerOpened = false
        seriesDetailJob?.cancel()
        helpViewModel.resetWatchlistData()
        helpViewModel.cancelWatchlistJob()
        _binding = null
    }
}