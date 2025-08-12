package com.example.mj_player_tv.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
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
import com.example.mj_player_tv.database.entity.EpisodesOB
import com.example.mj_player_tv.database.help.GlobalSearchDisplayItem
import com.example.mj_player_tv.database.help.GlobalSearchMainCategory
import com.example.mj_player_tv.database.help.StatsDisplayItem
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
import org.threeten.bp.Duration
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.text.isNotEmpty

@UnstableApi
class WatchListFragment : Fragment(R.layout.fragment_watchlist) {

    private var _binding: FragmentWatchlistBinding? = null

    private val binding get() = _binding!!

    private lateinit var playlistAdapter: WatchlistPlaylistAdapter
    private lateinit var watchlistItemsAdapter: WatchlistItemsAdapter
    private lateinit var watchlistProgramsAdapter: WatchListProgrammeAdapter

    private var moviesByAccount: MutableMap<Accounts, MutableList<MovieOB>>? = null

    private var seriesByAccount: MutableMap<Accounts, MutableList<SeriesOB>>? = null

    private var programsByAccount: MutableMap<Accounts, MutableList<Programme>>? = null

    private val movieBox = ObjectBox.store.boxFor(MovieOB::class.java)

    private val seriesBox = ObjectBox.store.boxFor(SeriesOB::class.java)

    private val epgDataBox = ObjectBox.store.boxFor(EpgDataOB::class.java)

    private val programmeBox = ObjectBox.store.boxFor(Programme::class.java)

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
                ).mapValues { it.value.flatten().toMutableList() }.toMutableMap()

                val seriesResults = results.filterIsInstance<WatchlistItem.Series>()
                seriesByAccount = seriesResults.groupBy(
                    keySelector = { it.account },
                    valueTransform = { it.series }
                ).mapValues { it.value.flatten().toMutableList() }.toMutableMap()

                val programsResults = results.filterIsInstance<WatchlistItem.Programs>()
                programsByAccount = programsResults.groupBy(
                    keySelector = { it.account },
                    valueTransform = { it.programs }
                ).mapValues { it.value.flatten().toMutableList() }.toMutableMap()

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
                        val playlists = when(cat) {
                            WatchlistMainCategory.PROGRAMS -> programsByAccount?.keys
                            WatchlistMainCategory.MOVIES -> moviesByAccount?.keys
                            WatchlistMainCategory.SERIES -> seriesByAccount?.keys
                        }?.toList()?.sortedBy { it.name } ?: emptyList()
                        playlistAdapter.submitList(playlists)
                        val items = getDisplayableItemsFor(account, cat)
                        when (cat) {
                            WatchlistMainCategory.PROGRAMS -> {
                                val programItems =
                                    items.filterIsInstance<WatchlistDisplayItem.ProgramItem>()
                                watchlistProgramsAdapter.submitList(programItems)
                            }
                            else -> {
                                watchlistItemsAdapter.submitList(items)
                            }
                        }
                        if (playlists.isEmpty()) {
                            when (cat) {
                                WatchlistMainCategory.PROGRAMS -> binding.rvWatchlistProgramme.requestFocus()
                                WatchlistMainCategory.MOVIES -> {
                                    resetMovieDetailsUi()
                                    binding.tvNodatafound.visibility = View.VISIBLE
                                    binding.rvWatchlistMovies.requestFocus()
                                }
                                WatchlistMainCategory.SERIES -> {
                                    resetSeriesDetailsUi()
                                    binding.tvNodatafound.visibility = View.VISIBLE
                                    binding.rvWatchlistSeries.requestFocus()
                                }
                            }
                        } else {
                            if (items.isEmpty()) {
                                when (cat) {
                                    WatchlistMainCategory.MOVIES -> {
                                        resetMovieDetailsUi()
                                    }
                                    WatchlistMainCategory.SERIES -> {
                                        resetSeriesDetailsUi()
                                    }
                                    WatchlistMainCategory.PROGRAMS -> {

                                    }
                                }
                                binding.recyclerPlaylists.post {
                                    binding.recyclerPlaylists.requestFocus()
                                }
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
                    if (playlistAdapter.currentList.isNotEmpty()) {
                        binding.recyclerPlaylists.requestFocus()
                    } else {
                        resetSeriesDetailsUi()
                        binding.rvWatchlistSeries.requestFocus()
                        binding.tvNodatafound.visibility = View.VISIBLE
                    }
                }
                seriesViewModel.clearFocusToSeries()
            }
        }

        seriesViewModel.removeSeriesFromWatchlistOrStats.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                helpViewModel.currentFocusedSerie?.let {
                    helpViewModel.removeSerieFromWatchlist(it)
                }
                seriesViewModel.clearRemoveSeriesFromWatchlistOrStats()
            }
        }

        moviesViewModel.focusToMoviesRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                if (watchlistItemsAdapter.currentList.isNotEmpty()) {
                    binding.recyclerItems.requestFocus()
                } else {
                    if (playlistAdapter.currentList.isNotEmpty()) {
                        binding.recyclerPlaylists.requestFocus()
                    } else {
                        resetMovieDetailsUi()
                        binding.rvWatchlistMovies.requestFocus()
                        binding.tvNodatafound.visibility = View.VISIBLE
                    }
                }
                moviesViewModel.clearFocusToMovies()
            }
        }

        moviesViewModel.removeMovieFromWatchlistOrStats.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                helpViewModel.currentFocusedMovie?.let {
                    helpViewModel.removeMovieFromWatchlist(it)
                }
                moviesViewModel.clearRemoveMovieFromWatchlistOrStats()
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
        watchlistItemsAdapter = WatchlistItemsAdapter(helpViewModel, this,  { clickedItem ->
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
                        if (clickedItem.series.idByAccountData == helpViewModel.currentFocusedSerie?.idByAccountData) {
                            seriesViewModel.openedSameSeries = true
                        }
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
                                stalkerViewModel.getSeriesDetail(clickedItem.series, selectedAccount!!)
                                stalkerViewModel.seriesCacheLive.observe(viewLifecycleOwner) { cache ->
                                    val cachedData = cache[helpViewModel.currentFocusedSerie?.idByAccountData]
                                    if (cachedData != null) {
                                        val (seasons, episodes) = cachedData
                                        helpViewModel.currentFocusedSerie?.totalSeasons = seasons.size
                                        helpViewModel.focusedSeasons = seasons
                                            .sortedWith(
                                                compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                                                    .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE }
                                            )
                                            .toMutableList()

                                        // Episodes setzen
                                        helpViewModel.focusedEpisodes = episodes
                                            .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                                            .toMutableList()
                                    }
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
        }, onLongItemClick = { clickedItem, view ->
            when (clickedItem) {
                is WatchlistDisplayItem.MovieItem -> {
                    showMoviePopUp(clickedItem.movie, view)
                }
                is WatchlistDisplayItem.SeriesItem -> {
                    showSeriesPopUp(clickedItem.series, view)
                }
                is WatchlistDisplayItem.ProgramItem -> {

                }
            }
        })
        binding.recyclerItems.apply {
            adapter = watchlistItemsAdapter
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private fun showMoviePopUp(movie: MovieOB, anchor: View) {
        val context = anchor.context
        val popupView = LayoutInflater.from(context).inflate(R.layout.menu_popup, null)
        val widthInDp = 250
        val widthInPx = (widthInDp * popupView.context.resources.displayMetrics.density).toInt()
        val popupWindow = PopupWindow(
            popupView,
            widthInPx,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.elevation = 8f

        // Buttons holen
        val removeOption = popupView.findViewById<TextView>(R.id.optionRemove)
        val fullWatchedOption = popupView.findViewById<TextView>(R.id.optionFullWatched)
        val itemName = popupView.findViewById<TextView>(R.id.itemName)
        itemName.text = movie.movieName
        itemName.isSelected = true
        // Sichtbarkeit wie vorher bei PopupMenu
        removeOption.text = "Remove movie from watchlist"
        fullWatchedOption.text = "Add movie to user-list"

        // Click-Listener
        removeOption.setOnClickListener {
            removieMovieFromList(movie)
            popupWindow.dismiss()
        }

        fullWatchedOption.setOnClickListener {
            setMovieAsCompletelyWatched(movie)
            popupWindow.dismiss()
        }

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorX = location[0]
        val anchorY = location[1]

// Popup messen (wichtig, bevor man Positionen berechnet)
        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight

// Mittelpunkt vom Anchor
        val centerX = anchorX + (anchor.width / 2)
        val centerY = anchorY + (anchor.height / 2)

// Popup so setzen, dass sein Mittelpunkt = Anchor-Mittelpunkt ist
        val xPosition = centerX - (popupWidth / 2)
        val yPosition = centerY - (popupHeight / 2)

        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, xPosition, yPosition)

        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, xPosition, yPosition)
        showDimOverlay()
        removeOption.requestFocus()

        popupWindow.setOnDismissListener {
            removeDimOverlay()
        }
    }

    private fun removieMovieFromList(movie: MovieOB) {
        movie.isFavorite = false
        if (!movie.isCompletelyWatched || !movie.isPartlyWatched) {
            movieBox.remove(movie)
        } else {
            movieBox.put(movie)
        }
        helpViewModel.removeMovieFromWatchlist(movie)
    }

    fun removeMovieFromAccount(account: Accounts, moviesToRemove: MovieOB) {
        moviesByAccount?.get(account)?.remove(moviesToRemove)
    }

    private fun setMovieAsCompletelyWatched(movie: MovieOB) {
        return
    }


    private fun showSeriesPopUp(serie: SeriesOB, anchor: View) {
        val context = anchor.context
        val popupView = LayoutInflater.from(context).inflate(R.layout.menu_popup, null)
        val widthInDp = 250
        val widthInPx = (widthInDp * popupView.context.resources.displayMetrics.density).toInt()
        val popupWindow = PopupWindow(
            popupView,
            widthInPx,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.elevation = 8f

        // Buttons holen
        val removeOption = popupView.findViewById<TextView>(R.id.optionRemove)
        val fullWatchedOption = popupView.findViewById<TextView>(R.id.optionFullWatched)
        val itemName = popupView.findViewById<TextView>(R.id.itemName)
        itemName.text = serie.seriesName
        itemName.isSelected = true
        // Sichtbarkeit wie vorher bei PopupMenu
        removeOption.text = "Remove series from watchlist"
        fullWatchedOption.text = "Add series to user-list"

        // Click-Listener
        removeOption.setOnClickListener {
            removieSeriesFromList(serie)
            popupWindow.dismiss()
        }

        fullWatchedOption.setOnClickListener {
            setSeriesAsCompletelyWatched(serie)
            popupWindow.dismiss()
        }

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorX = location[0]
        val anchorY = location[1]

// Popup messen (wichtig, bevor man Positionen berechnet)
        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight

// Mittelpunkt vom Anchor
        val centerX = anchorX + (anchor.width / 2)
        val centerY = anchorY + (anchor.height / 2)

// Popup so setzen, dass sein Mittelpunkt = Anchor-Mittelpunkt ist
        val xPosition = centerX - (popupWidth / 2)
        val yPosition = centerY - (popupHeight / 2)

        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, xPosition, yPosition)

        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, xPosition, yPosition)
        showDimOverlay()
        removeOption.requestFocus()

        popupWindow.setOnDismissListener {
            removeDimOverlay()
        }
    }


    private fun removieSeriesFromList(serie: SeriesOB) {
        serie.isFavorite = false
        if (!serie.isCompletelyWatched || !serie.isPartlyWatched) {
            seriesBox.remove(serie)
        } else {
            seriesBox.put(serie)
        }
        helpViewModel.removeSerieFromWatchlist(serie)
    }

    fun removeSeriesFromAccount(account: Accounts, seriesToRemove: SeriesOB) {
        seriesByAccount?.get(account)?.remove(seriesToRemove)
    }

    private fun setSeriesAsCompletelyWatched(serie: SeriesOB) {
        return
    }


    private var dimView: View? = null

    private fun showDimOverlay() {
        dimView = View(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#B3000000")) // halbtransparent schwarz
            isClickable = true // blockiert Klicks darunter
        }
        (requireActivity().window.decorView as ViewGroup).addView(
            dimView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun removeDimOverlay() {
        dimView?.let { (requireActivity().window.decorView as ViewGroup).removeView(it) }
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

    private val onProgrammeClickListerner = WatchListProgrammeAdapter.OnClickListener { programItem, view ->
        showProgrammePopUp(programItem, view)
    }
    private fun showProgrammePopUp(programItem: WatchlistDisplayItem.ProgramItem, anchor: View) {
        val context = anchor.context
        val popupView = LayoutInflater.from(context).inflate(R.layout.menu_popup_program, null)
        val widthInDp = 400
        val widthInPx = (widthInDp * popupView.context.resources.displayMetrics.density).toInt()
        val popupWindow = PopupWindow(
            popupView,
            widthInPx,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 8f
        val tvchannelPos = programItem.programs.tvchannels.target
        val tvChannel = programItem.programs.tvchannels.target.tvchannel.target
        val epg = programItem.programs.epgData.target
        val itemName = popupView.findViewById<TextView>(R.id.itemName)
        itemName.text = tvChannel.showingName
        itemName.isSelected = true
        // Views aus dem Layout holen
        val playOption = popupView.findViewById<TextView>(R.id.optionRemove)
        val replayOption = popupView.findViewById<TextView>(R.id.optionReplay)
        val reminderOption = popupView.findViewById<TextView>(R.id.optionFullWatched)
        val epgName = popupView.findViewById<TextView>(R.id.programName)
        val channelLogo = popupView.findViewById<ImageView>(R.id.itemLogo)
        epgName.visibility = View.VISIBLE
        epgName.text = epg.name
        epgName.isSelected = true

        val currentTime = System.currentTimeMillis() / 1000
        val isProgramFinished = (epg.stopTimestamp ?: 0L) < currentTime
        if (isProgramFinished) {
            return
        }
        val isProgramNotStarted = (epg.startTimestamp ?: 0) > currentTime
        val isCatchupChannel = tvChannel.enable_tv_archive == 1
        val linkedEpgChannel = tvChannel.linkedEpgChannel?.target
        val image = tvChannel.logo
        val epgLogo = linkedEpgChannel?.icon?.firstOrNull()
        if (tvChannel.account.target!!.useEpgLogos) {
            if (!epgLogo.isNullOrEmpty() && (linkedEpgChannel.isExternalEpg || tvChannel.alwaysUsesExternalEpg)) {
                channelLogo.visibility = View.VISIBLE
                channelLogo.load(epgLogo)
            } else {
                if (image.isNotEmpty()) {
                    channelLogo.visibility = View.VISIBLE
                    channelLogo.load(image)
                } else {
                    channelLogo.visibility = View.INVISIBLE
                }
            }
        } else {
            if (image.isNotEmpty()) {
                channelLogo.visibility = View.VISIBLE
                channelLogo.load(image)
            } else {
                channelLogo.visibility = View.INVISIBLE
            }
        }

        val isProgramCurrentlyPlaying = (((epg.stopTimestamp ?: 0L) > currentTime &&
                currentTime >= (epg.startTimestamp ?: 0)))

        playOption.visibility = if (isProgramCurrentlyPlaying) View.VISIBLE else View.GONE
        replayOption.visibility = if (isCatchupChannel && (isProgramCurrentlyPlaying)) View.VISIBLE else View.GONE
        reminderOption.visibility = if (isProgramNotStarted) View.VISIBLE else View.GONE


        replayOption.text = when {
            isProgramCurrentlyPlaying && isCatchupChannel -> "Play from beginning"
            else -> ""
        }

        val isProgrammReminded = programmeBox.query(
            Programme_.epgForCh.equal("${epg.idByAccountData}_${tvChannel.idByAccountData}")
        ).build().findFirst()

        reminderOption.text = if (isProgrammReminded != null) {
            "Remove reminder"
        } else {
            "Set reminder"
        }

        if (isProgramCurrentlyPlaying) {
            playOption.requestFocus()
        } else {
            if (isProgramNotStarted) {
                reminderOption.requestFocus()
            }
        }

        // Click Listener
        playOption.setOnClickListener {
            playChannel(tvchannelPos)
            popupWindow.dismiss()
        }
        replayOption.setOnClickListener {
            replayProgram(tvchannelPos, epg)
            popupWindow.dismiss()
        }
        reminderOption.setOnClickListener {
            checkReminder(programItem, tvchannelPos, anchor)
            popupWindow.dismiss()
        }

        // Position mittig über dem Item
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorX = location[0]
        val anchorY = location[1]
        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight

        val xPos = anchorX + (anchor.width / 2) - (popupWidth / 2)
        val yPos = anchorY + (anchor.height / 2) - (popupHeight / 2)

        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, xPos, yPos)

        // Hintergrund abdunkeln
        showDimOverlay()
        popupWindow.setOnDismissListener {
            removeDimOverlay()
            binding.recyclerProgramme.requestFocus()
        }
    }

    fun playChannel(tvChannelPos: ChannelPositions) {
        helpViewModel.currentFocusedChannPosition = tvChannelPos
        helpViewModel.channelFromSearchContainer = true
        helpViewModel.currentFocusedTvAccount = tvChannelPos.tvcategory.target.tvaccount.target
        helpViewModel.currentFocusedTvCategory = tvChannelPos.tvcategory.target
        helpViewModel.checkCategoryActivated(tvChannelPos.tvcategory.target)
        Log.d("CLICKEDFROMGLOBALSEARCH", "${helpViewModel.currentFocusedChannPosition?.tvchannel?.target?.showingName} IN ${helpViewModel.currentFocusedTvCategory?.showingName} FROM ACC: ${helpViewModel.currentFocusedTvAccount?.name}")
        (requireActivity() as? MainActivity)?.checkTvChannelsFragmentFromGlobalSearch()
    }

    fun replayProgram(tvChannelPos: ChannelPositions, clickedEpgData: EpgDataOB) {
        val tvCategory = tvChannelPos.tvcategory.target
        val tvChannel = tvChannelPos.tvchannel.target
        helpViewModel.currentFocusedChannPosition = tvChannelPos
        helpViewModel.currentFocusedChannel = tvChannel
        helpViewModel.currentFocusedTvCategory = tvCategory
        if (tvChannel.linkedEpgChannel?.target?.isExternalEpg == true) {
            if (tvChannel.account.target.isXtream) {
                clickedEpgData.let { epgData ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val thisEpg = xtreamViewModel.findEpgMatch(
                            epgData,
                            tvChannel,
                            tvCategory!!
                        )
                        when (thisEpg) {
                            is Resource.Success -> {
                                if (thisEpg.data != null) {
                                    val startTime = thisEpg.data.start
                                    val endTime = thisEpg.data.end
                                    getXtreamCatchup(
                                        tvChannelPos,
                                        startTime,
                                        endTime,
                                        clickedEpgData
                                    )
                                }
                            }
                            is Resource.Error -> {
                                Toast.makeText(
                                    this@WatchListFragment.requireActivity(),
                                    "Error fetching Catchup Link!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }
                        }
                    }
                }
            } else {
                clickedEpgData.let { epgData ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val thisEpg = stalkerViewModel.findEpgMatch(
                            epgData,
                            tvChannel,
                            epgData.datum,
                            tvCategory!!
                        )
                        Log.d("CATCHUP STALKER", "NOT EXTERN: ${epgData.name}")
                        when (thisEpg) {
                            is Resource.Success -> {
                                if (thisEpg.data != null) {
                                    val epgId = thisEpg.data.id
                                    getStalkerCatchupLink(
                                        tvChannelPos,
                                        epgId,
                                        epgData
                                    )
                                }
                            }

                            is Resource.Error -> {
                                Toast.makeText(
                                    this@WatchListFragment.requireActivity(),
                                    "Error fetching Catchup Link!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }
                        }
                    }
                }
            }
        } else {
            if (tvChannel.linkedEpgChannel?.target?.epgsource?.target?.isXtreamEpg == true) {
                clickedEpgData.let { epgData ->
                    getXtreamCatchup(tvChannelPos, epgData.startTime, epgData.endTime, clickedEpgData)
                }
            } else {
                clickedEpgData.let { epgData ->
                    getStalkerCatchupLink(tvChannelPos, epgData.epgId, clickedEpgData)
                }
            }
        }
    }

    fun getXtreamCatchup(tvChannelPos: ChannelPositions, startTime: String, endTime: String, clickedEpgData: EpgDataOB) {
        val account = tvChannelPos.tvchannel.target.account.target
        if (account != null) {
            val accountUrl = account.stalkerUrl
            val accountUserName = account.username
            val accountPassword = account.macAddress
            val epgStart = startTime.substring(0, 10) + ":" + startTime.substring(
                11,
                13
            ) + "-" + startTime.substring(14, 16)
            val duration = calculateDurationInMinutes(startTime, endTime)
            val url =
                "$accountUrl/streaming/timeshift.php?username=$accountUserName&password=$accountPassword&stream=${tvChannelPos.tvchannel.target.channelId}&start=$epgStart&duration=$duration"
            helpViewModel.globalSearchCatchupUrl = url
            helpViewModel.isPlayingCatchup = true
            helpViewModel.catchupEpgData = clickedEpgData
            helpViewModel.catchupPlayingChannelPosition = tvChannelPos
            helpViewModel.currentFocusedTvAccount = account
            helpViewModel.currentFocusedTvCategory = tvChannelPos.tvcategory.target
            helpViewModel.currentFocusedChannPosition = tvChannelPos
            helpViewModel.currentFocusedChannel = tvChannelPos.tvchannel.target
            helpViewModel.channelFromSearchContainer = true
            (requireActivity() as MainActivity).checkTvChannelsFragmentFromGlobalSearch()
        }
    }

    fun calculateDurationInMinutes(startString: String, endString: String): Long {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val startDateTime = LocalDateTime.parse(startString, formatter)
        val endDateTime = LocalDateTime.parse(endString, formatter)
        return Duration.between(startDateTime, endDateTime).toMinutes()
    }

    fun getStalkerCatchupLink(tvChannelPos: ChannelPositions, epgId: String, clickedEpgData: EpgDataOB) {
        viewLifecycleOwner.lifecycleScope.launch {
            val account = tvChannelPos.tvchannel.target.account.target
            if (account != null) {
                val catchUp = stalkerViewModel.getTvCatchupLink(
                    account.stalkerUrl,
                    cmd = "/media/$epgId.mpg",
                    cookie = "mac=${account.macAddress}; stb_lang=en; timezone=${account.timezone};",
                    token = "Bearer ${account.token}",
                    account.userAgent
                ).await()
                when (catchUp) {
                    is Resource.Success -> {
                        Log.d("CATCHUP STALKER", "CATCHUPDATA: ${catchUp.data}")
                        helpViewModel.isPlayingCatchup = true
                        helpViewModel.catchupEpgData = clickedEpgData
                        helpViewModel.catchupPlayingChannelPosition = tvChannelPos
                        helpViewModel.currentFocusedTvAccount = account
                        helpViewModel.currentFocusedTvCategory = tvChannelPos.tvcategory.target
                        helpViewModel.channelFromSearchContainer = true
                        helpViewModel.currentFocusedChannPosition = tvChannelPos
                        helpViewModel.currentFocusedChannel = tvChannelPos.tvchannel.target
                        helpViewModel.globalSearchCatchupUrl = catchUp.data?.removePrefix("ffmpeg")?.trim() ?: ""
                        (requireActivity() as MainActivity).checkTvChannelsFragmentFromGlobalSearch()
                    }
                    is Resource.Error -> {
                        Toast.makeText(
                            this@WatchListFragment.requireActivity(),
                            "Error fetching Catchup Link!\n${catchUp.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.recyclerItems.requestFocus()
                    }
                }
            }
        }
    }

    private fun checkReminder(programmItem: WatchlistDisplayItem.ProgramItem, channPos: ChannelPositions, view: View) {
        val tvChannel = channPos.tvchannel.target
        val epg = programmItem.programs.epgData.target
        val isProgramme = programmeBox.query(Programme_.epgForCh.equal("${epg.idByAccountData}_${tvChannel.idByAccountData}")).build().findFirst()
        if (isProgramme != null) {
            programmeBox.remove(isProgramme)
            epg.isRemembered = false
            epgDataBox.put(epg)
            val currentEpgPos = watchlistProgramsAdapter.currentList.indexOf(programmItem)
            watchlistProgramsAdapter.notifyItemChanged(currentEpgPos)
        } else {
            val timeOffSet =
                tvChannel.epgTimeOffSet ?: channPos.tvcategory.target?.epgTimeOffSet
                ?: tvChannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                ?: 0
            val thisProgramme = Programme(
                0,
                "${epg.idByAccountData}_${tvChannel.idByAccountData}",
                epg.startTimestamp ?: 0L,
                epg.stopTimestamp ?: 0L,
                helpViewModel.settings?.tvReminderTime ?: 10L
            )
            programmeBox.put(thisProgramme)
            thisProgramme.apply {
                epgData.target = epg
                tvchannels.target = channPos
            }
            programmeBox.put(thisProgramme)
            epg.isRemembered = true
            epgDataBox.put(epg)
            val currentEpgPos = watchlistProgramsAdapter.currentList.indexOf(programmItem)
            watchlistProgramsAdapter.notifyItemChanged(currentEpgPos)
            if (!android.provider.Settings.canDrawOverlays(view.context)) {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + this.requireContext().packageName)
                )
                this.requireActivity().startActivity(intent)
            }
            helpViewModel.setReminder(this.requireContext(), thisProgramme, timeOffSet)
        }
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
            if (binding.recyclerPlaylists.isComputingLayout) {
                binding.recyclerPlaylists.post {
                    playlistAdapter.notifyItemChanged(oldPosition)
                    playlistAdapter.notifyItemChanged(newPosition)
                }
            } else {
                playlistAdapter.notifyItemChanged(oldPosition)
                playlistAdapter.notifyItemChanged(newPosition)
            }

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
                                        val movie = tmdbMovieDetailsByImdbId.data?.movie_results?.firstOrNull()
                                        val backdropPath = movie?.backdrop_path
                                        val posterPath = movie?.poster_path
                                        val screenshotUri = helpViewModel.currentFocusedMovie?.screenshot_uri
                                        val backdropImageUrl = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
                                        val posterImageUrl = posterPath?.let { "https://image.tmdb.org/t/p/original$it" }
                                        val imageToLoad = when {
                                            !backdropImageUrl.isNullOrEmpty() -> backdropImageUrl
                                            !posterImageUrl.isNullOrEmpty() -> posterImageUrl
                                            !screenshotUri.isNullOrEmpty() -> screenshotUri
                                            else -> null
                                        }
                                        if (imageToLoad != null) {
                                            binding.ivMovieposter.visibility = View.VISIBLE
                                            binding.ivMovieposter.load(imageToLoad)
                                        } else {
                                            binding.ivMovieposter.visibility = View.INVISIBLE
                                        }
                                        if (screenshotUri.isNullOrEmpty() && posterPath != null) {
                                            helpViewModel.currentFocusedMovie?.screenshot_uri = posterPath
                                        }
                                        helpViewModel.currentFocusedMovie?.backdropPath = imageToLoad ?: ""
                                    }

                                    is Resource.Error -> {
                                        val moviePoster = helpViewModel.currentFocusedMovie?.screenshot_uri
                                        if (!moviePoster.isNullOrEmpty()) {
                                            binding.ivMovieposter.visibility = View.VISIBLE
                                            binding.ivMovieposter.load(moviePoster)
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
                                        val data = tmdbMovieDetails.data
                                        val backdropPath = data?.backdrop_path
                                        val posterPath = data?.poster_path
                                        val screenshotUri = helpViewModel.currentFocusedMovie?.screenshot_uri
                                        val backdropImageUrl = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
                                        val posterImageUrl = posterPath?.let { "https://image.tmdb.org/t/p/original$it" }
                                        val imageToLoad = when {
                                            !backdropImageUrl.isNullOrEmpty() -> backdropImageUrl
                                            !posterImageUrl.isNullOrEmpty() -> posterImageUrl
                                            !screenshotUri.isNullOrEmpty() -> screenshotUri
                                            else -> null
                                        }
                                        if (imageToLoad != null) {
                                            binding.ivMovieposter.visibility = View.VISIBLE
                                            binding.ivMovieposter.load(imageToLoad)
                                        } else {
                                            binding.ivMovieposter.visibility = View.INVISIBLE
                                        }
                                        if (screenshotUri.isNullOrEmpty() && posterPath != null) {
                                            helpViewModel.currentFocusedMovie?.screenshot_uri = posterPath
                                        }
                                        helpViewModel.currentFocusedMovie?.backdropPath = imageToLoad ?: ""
                                    }

                                    is Resource.Error -> {
                                        val moviePoster = helpViewModel.currentFocusedMovie?.screenshot_uri
                                        if (!moviePoster.isNullOrEmpty()) {
                                            binding.ivMovieposter.visibility = View.VISIBLE
                                            binding.ivMovieposter.load(moviePoster)
                                        } else {
                                            binding.ivMovieposter.visibility = View.INVISIBLE
                                        }
                                    }
                                }
                            }
                        } else {
                            if (!movie.screenshot_uri.isNullOrEmpty()) {
                                binding.ivMovieposter.visibility = View.VISIBLE
                                binding.ivMovieposter.load(movie.screenshot_uri)
                            } else {
                                binding.ivMovieposter.visibility = View.INVISIBLE
                            }
                        }
                    } else {
                        binding.ivMovieposter.visibility = View.VISIBLE
                        binding.ivMovieposter.load(movie.backdropPath)
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
                                    val movie = tmdbMovieDetailsByImdbId.data?.movie_results?.firstOrNull()
                                    val backdropPath = movie?.backdrop_path
                                    val posterPath = movie?.poster_path
                                    val screenshotUri = helpViewModel.currentFocusedMovie?.screenshot_uri
                                    val backdropImageUrl = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
                                    val posterImageUrl = posterPath?.let { "https://image.tmdb.org/t/p/original$it" }
                                    val imageToLoad = when {
                                        !backdropImageUrl.isNullOrEmpty() -> backdropImageUrl
                                        !posterImageUrl.isNullOrEmpty() -> posterImageUrl
                                        !screenshotUri.isNullOrEmpty() -> screenshotUri
                                        else -> null
                                    }
                                    if (imageToLoad != null) {
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(imageToLoad)
                                    } else {
                                        binding.ivMovieposter.visibility = View.INVISIBLE
                                    }
                                    if (screenshotUri.isNullOrEmpty() && posterPath != null) {
                                        helpViewModel.currentFocusedMovie?.screenshot_uri = posterPath
                                    }
                                    helpViewModel.currentFocusedMovie?.backdropPath = imageToLoad ?: ""
                                }

                                is Resource.Error -> {
                                    val moviePoster = movie.screenshot_uri
                                    if (!moviePoster.isNullOrEmpty()) {
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(moviePoster)
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
                                    val data = tmdbMovieDetails.data
                                    val backdropPath = data?.backdrop_path
                                    val posterPath = data?.poster_path
                                    val screenshotUri = helpViewModel.currentFocusedMovie?.screenshot_uri
                                    val backdropImageUrl = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
                                    val posterImageUrl = posterPath?.let { "https://image.tmdb.org/t/p/original$it" }
                                    val imageToLoad = when {
                                        !backdropImageUrl.isNullOrEmpty() -> backdropImageUrl
                                        !posterImageUrl.isNullOrEmpty() -> posterImageUrl
                                        !screenshotUri.isNullOrEmpty() -> screenshotUri
                                        else -> null
                                    }
                                    if (imageToLoad != null) {
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(imageToLoad)
                                    } else {
                                        binding.ivMovieposter.visibility = View.INVISIBLE
                                    }
                                    if (screenshotUri.isNullOrEmpty() && posterPath != null) {
                                        helpViewModel.currentFocusedMovie?.screenshot_uri = posterPath
                                    }
                                    helpViewModel.currentFocusedMovie?.backdropPath = imageToLoad ?: ""
                                }

                                is Resource.Error -> {
                                    val moviePoster = movie.screenshot_uri
                                    if (!moviePoster.isNullOrEmpty()) {
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(moviePoster)
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
        binding.tvReleaseyear.text = if (movie.movieYear.isNotEmpty()) {
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
                serie.seriesAccount.target.isStalker && stalkerViewModel.seriesCacheLive.value?.containsKey(serie.idByAccountData) != true) {
                getSeriesDetailInfo(serie)
            } else {
                if (serie.seriesAccount.target.isXtream) {
                    helpViewModel.focusedSeasons = xtreamViewModel.seriesCache[serie.idByAccountData]?.first
                    helpViewModel.focusedEpisodes = xtreamViewModel.seriesCache[serie.idByAccountData]?.second?.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))?.toMutableList()
                    serie.totalSeasons = helpViewModel.focusedSeasons?.size ?: 1
                } else {
                    helpViewModel.focusedSeasons = stalkerViewModel.seriesCacheLive.value?.get(serie.idByAccountData)?.first?.toMutableList() ?: mutableListOf()
                    helpViewModel.focusedEpisodes = stalkerViewModel.seriesCacheLive.value
                        ?.get(serie.idByAccountData)
                        ?.second
                        ?.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                        ?.toMutableList() ?: mutableListOf()
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
                stalkerViewModel.getSeriesDetail(serie, serie.seriesAccount.target)
                stalkerViewModel.seriesCacheLive.observe(viewLifecycleOwner) { cache ->
                    val cachedData = cache[serie.idByAccountData]
                    if (cachedData != null) {
                        val (seasons, episodes) = cachedData
                        serie.totalSeasons = seasons.size
                        helpViewModel.focusedSeasons = seasons
                            .sortedWith(
                                compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                                    .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE }
                            )
                            .toMutableList()

                        // Episodes setzen
                        helpViewModel.focusedEpisodes = episodes
                            .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                            .toMutableList()
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
        selectedAccount = null
        selectedWatchlistCategory = null
        seriesDetailJob?.cancel()
        helpViewModel.resetWatchlistData()
        helpViewModel.cancelWatchlistJob()
        _binding = null
    }
}