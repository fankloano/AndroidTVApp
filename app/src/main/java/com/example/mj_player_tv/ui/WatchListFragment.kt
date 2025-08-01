package com.example.mj_player_tv.ui

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.core.view.isNotEmpty
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.viewmodel.MoviesViewModel
import com.example.mj_player_tv.viewmodel.MoviesViewModelFactory
import com.example.mj_player_tv.viewmodel.SeriesViewModel
import com.example.mj_player_tv.viewmodel.SeriesViewModelFactory
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
class WatchListFragment : Fragment(R.layout.fragment_watchlist) {

    private var _binding: FragmentWatchlistBinding? = null

    private val binding get() = _binding!!

    private var playlistAdapter: WatchlistPlaylistAdapter? = null
    private var moviesAdapter: WatchListMoviesAdapter? = null
    private var seriesAdapter: WatchlistSeriesAdapter? = null
    private var programmesAdapter: WatchListProgrammeAdapter? = null

    private val accountBox = ObjectBox.store.boxFor(Accounts::class.java)
    private val movieBox = ObjectBox.store.boxFor(MovieOB::class.java)
    private val seriesBox = ObjectBox.store.boxFor(SeriesOB::class.java)
    private val programmeBox = ObjectBox.store.boxFor(Programme::class.java)

    private var wasItemClicked = false

    private var moviesList: MutableList<MovieOB>? = null

    private var seriesList: MutableList<SeriesOB>? = null

    private var programmeList: MutableList<Programme>? = null

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

        preparePlaylistRecyclerView()
        prepareProgrammesRecyclerView()

        parentFragmentManager.addOnBackStackChangedListener(backStackListener)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val movieQuery = movieBox.query(MovieOB_.isFavorite.equal(true)).build()
            moviesList = movieQuery.find()
            movieQuery.close()
            if (!moviesList.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    if (binding.loadingprogressBar.isVisible) {
                        binding.loadingprogressBar.visibility = View.GONE
                    }
                    prepareMoviesRecyclerView()
                    binding.rvWatchlistMovies.isSelected = true
                    binding.rvWatchlistSeries.isSelected = false
                    binding.rvWatchlistProgramme.isSelected = false
                    binding.rvWatchlistMovies.requestFocus()
                    showMoviePlaylistsRecyclerview(moviesList!!)
                }
            }
            val seriesQuery = seriesBox.query(SeriesOB_.isFavorite.equal(true)).build()
            seriesList = seriesQuery.find()
            seriesQuery.close()
            if (!seriesList.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                if (binding.loadingprogressBar.isVisible) {
                    binding.loadingprogressBar.visibility = View.GONE
                }
                if (moviesList.isNullOrEmpty()) {
                    prepareSeriesRecyclerView()
                    binding.rvWatchlistMovies.isSelected = false
                    binding.rvWatchlistSeries.isSelected = true
                    binding.rvWatchlistProgramme.isSelected = false
                    binding.rvWatchlistSeries.requestFocus()
                    showSeriesPlaylistsRecyclerview(seriesList!!)
                    }
                }
            }
            programmeList = programmeBox.all
            if (!programmeList.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    if (binding.loadingprogressBar.isVisible) {
                        binding.loadingprogressBar.visibility = View.GONE
                    }
                    if (moviesList.isNullOrEmpty() && seriesList.isNullOrEmpty()) {
                        binding.rvWatchlistMovies.isSelected = false
                        binding.rvWatchlistSeries.isSelected = false
                        binding.rvWatchlistProgramme.isSelected = true
                        binding.rvWatchlistProgramme.requestFocus()
                        showProgrammePlaylistsRecyclerview(programmeList!!)
                    }
                }
            }
            if (moviesList.isNullOrEmpty() && seriesList.isNullOrEmpty() && programmeList.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    if (binding.loadingprogressBar.isVisible) {
                        binding.loadingprogressBar.visibility = View.GONE
                    }
                    binding.rvWatchlistMovies.isSelected = true
                    binding.rvWatchlistSeries.isSelected = false
                    binding.rvWatchlistMovies.isSelected = false
                    binding.rvWatchlistMovies.requestFocus()
                    binding.tvNodatafound.visibility = View.VISIBLE
                }
            }
        }

        binding.rvWatchlistMovies.setOnFocusChangeListener { _, hasFocus ->
            binding.tvSelectmovies.isSelected = hasFocus
            if (hasFocus ) {
                Log.d("SERIEDETAILWATCHLIST", "FOKUS FILME")
                helpViewModel.currentWatchListSeriesAccount = null
                helpViewModel.currentFocusedSerie = null
                resetSeriesDetailsUi()
                if (binding.relLayoutProgramdescr.isVisible) {
                    binding.relLayoutProgramdescr.visibility = View.GONE
                }
                if (binding.recyclerProgramme.isVisible) {
                    binding.recyclerProgramme.visibility = View.GONE
                    binding.recyclerItems.visibility = View.VISIBLE
                }
                binding.rvWatchlistMovies.isSelected = true
                binding.rvWatchlistProgramme.isSelected = false
                binding.rvWatchlistSeries.isSelected = false
                if (!helpViewModel.isWatchlistContainerOpened && helpViewModel.currentSelectedWatchlist != "MOVIE") {
                    if (binding.tvNodatafound.isVisible) {
                        binding.tvNodatafound.visibility = View.INVISIBLE
                    }
                    helpViewModel.currentSelectedWatchlist = "MOVIE"
                    if (!moviesList.isNullOrEmpty()) {
                        playlistAdapter?.submitList(null)
                        prepareMoviesRecyclerView()
                        showMoviePlaylistsRecyclerview(moviesList!!)
                    } else {
                        if (moviesList.isNullOrEmpty()) {
                            binding.recyclerPlaylists.visibility = View.INVISIBLE
                            binding.tvNodatafound.visibility = View.VISIBLE
                            binding.recyclerItems.visibility = View.INVISIBLE
                        }
                    }
                }
                helpViewModel.currentSelectedWatchlist = "MOVIE"
            }
        }

        binding.rvWatchlistMovies.setOnKeyListener { _, keyCode, event ->
            if (((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) && event.action == KeyEvent.ACTION_DOWN) {
                if (binding.recyclerPlaylists.isVisible) {
                    binding.recyclerPlaylists.requestFocus()
                } else {
                    binding.rvWatchlistMovies.requestFocus()
                }
                return@setOnKeyListener true
            } else if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                helpViewModel.watchstatsContainerOpened = false
                val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                if (mainFragment is WatchlistStatsFragment) {
                    mainFragment.closeFragmentContainer(true)
                }
                parentFragmentManager.popBackStack()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.rvWatchlistSeries.setOnFocusChangeListener { _, hasFocus ->
            binding.tvSelectseries.isSelected = hasFocus
            if (hasFocus) {
                Log.d("SERIEDETAILWATCHLIST", "FOKUS SERIEN")
                helpViewModel.currentWatchListMovieAccount = null
                helpViewModel.currentFocusedMovie = null
                helpViewModel.currentWatchListProgrammeAccount = null
                resetMovieDetailsUi()
                resetProgramDetails()
                if (binding.relLayoutProgramdescr.isVisible) {
                    binding.relLayoutProgramdescr.visibility = View.GONE
                }
                binding.recyclerProgramme.visibility = View.INVISIBLE
                binding.recyclerItems.visibility = View.VISIBLE
                binding.rvWatchlistMovies.isSelected = false
                binding.rvWatchlistProgramme.isSelected = false
                binding.rvWatchlistSeries.isSelected = true
                if (!helpViewModel.isWatchlistContainerOpened && helpViewModel.currentSelectedWatchlist != "SERIE") {
                    if (binding.tvNodatafound.isVisible) {
                        binding.tvNodatafound.visibility = View.INVISIBLE
                    }
                    helpViewModel.currentSelectedWatchlist = "SERIE"
                    if (!seriesList.isNullOrEmpty()) {
                        playlistAdapter?.submitList(null)
                        prepareSeriesRecyclerView()
                        showSeriesPlaylistsRecyclerview(seriesList!!)
                    } else {
                        if (seriesList.isNullOrEmpty()) {
                            binding.recyclerPlaylists.visibility = View.INVISIBLE
                            binding.tvNodatafound.visibility = View.VISIBLE
                            binding.recyclerItems.visibility = View.INVISIBLE
                        }
                    }
                }
                helpViewModel.currentSelectedWatchlist = "SERIE"
            }
        }

        binding.rvWatchlistSeries.setOnKeyListener { _, keyCode, event ->
            if (((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) && event.action == KeyEvent.ACTION_DOWN) {
                if (binding.recyclerPlaylists.isVisible) {
                    binding.recyclerPlaylists.requestFocus()
                } else {
                    binding.rvWatchlistSeries.requestFocus()
                }
                return@setOnKeyListener true
            } else if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                helpViewModel.watchstatsContainerOpened = false
                val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                if (mainFragment is WatchlistStatsFragment) {
                    mainFragment.closeFragmentContainer(true)
                }
                parentFragmentManager.popBackStack()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.rvWatchlistProgramme.setOnFocusChangeListener { _, hasFocus ->
            binding.tvSelectprogramme.isSelected = hasFocus
            if (hasFocus) {
                Log.d("SERIEDETAILWATCHLIST", "FOKUS PROGRAMME")

                seriesDetailJob?.cancel()
                resetSeriesDetailsUi()
                helpViewModel.currentWatchListSeriesAccount = null
                helpViewModel.currentFocusedSerie = null
                if (!helpViewModel.isWatchlistContainerOpened && helpViewModel.currentSelectedWatchlist != "PROGRAMME") {
                    binding.recyclerItems.visibility = View.INVISIBLE
                    binding.recyclerProgramme.visibility = View.VISIBLE
                    binding.rvWatchlistMovies.isSelected = false
                    binding.rvWatchlistSeries.isSelected = false
                    binding.rvWatchlistProgramme.isSelected = true
                    if (!programmeList.isNullOrEmpty()) {
                        if (binding.tvNodatafound.isVisible) {
                            binding.tvNodatafound.visibility = View.INVISIBLE
                        }
                        playlistAdapter?.submitList(null)
                        helpViewModel.currentSelectedWatchlist = "PROGRAMME"
                        showProgrammePlaylistsRecyclerview(programmeList!!)
                    } else {
                        if (programmeList.isNullOrEmpty()) {
                            binding.recyclerPlaylists.visibility = View.INVISIBLE
                            binding.recyclerProgramme.visibility = View.INVISIBLE
                            binding.tvNodatafound.visibility = View.VISIBLE
                        }
                    }
                }
                helpViewModel.currentSelectedWatchlist = "PROGRAMME"
            }
        }

        binding.rvWatchlistProgramme.setOnKeyListener { _, keyCode, event ->
            if (((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) || (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) && event.action == KeyEvent.ACTION_DOWN) {
                if (binding.recyclerPlaylists.isVisible) {
                    binding.recyclerPlaylists.requestFocus()
                } else {
                    binding.rvWatchlistProgramme.requestFocus()
                }
                return@setOnKeyListener true
            } else if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                helpViewModel.watchstatsContainerOpened = false
                val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                if (mainFragment is WatchlistStatsFragment) {
                    mainFragment.closeFragmentContainer(true)
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
                    binding.rvWatchlistSeries.requestFocus()
                }
                seriesViewModel.clearFocusToSeries()
            }
        }

        moviesViewModel.focusToMoviesRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                if (!moviesAdapter?.currentList.isNullOrEmpty()) {
                    binding.recyclerItems.requestFocus()
                } else {
                    binding.rvWatchlistMovies.requestFocus()
                }
                moviesViewModel.clearFocusToMovies()
            }
        }

    }

    private fun showMoviePlaylistsRecyclerview(movies: MutableList<MovieOB>) {
        if (movies.isNotEmpty()) {
            val uniqueAccounts = movies
                .mapNotNull { it.movieAccount.target }
                .distinctBy { it.id }.sortedBy { it.name }
            if (uniqueAccounts.isNotEmpty()) {
                helpViewModel.currentSelectedWatchlist = "MOVIE"
                playlistAdapter?.submitList(uniqueAccounts)
                binding.recyclerPlaylists.post {
                    binding.recyclerPlaylists.setSelectedPosition(0)
                    showFocusedPlaylistMovies(uniqueAccounts.first())
                }
            } else {
                return
            }
        }
    }

    private fun showSeriesPlaylistsRecyclerview(series: List<SeriesOB>) {
        if (series.isNotEmpty()) {
            val uniqueAccounts = series
                .mapNotNull { it.seriesAccount.target }
                .distinctBy { it.id }.sortedBy { it.name }
            if (uniqueAccounts.isNotEmpty()) {
                helpViewModel.currentSelectedWatchlist = "SERIE"
                playlistAdapter?.submitList(uniqueAccounts)
                binding.recyclerPlaylists.post {
                    binding.recyclerPlaylists.setSelectedPosition(0)
                    showFocusedPlaylistSeries(uniqueAccounts.first())
                }
            } else {
                Log.d("WL SERIE", "LEER")
            }
        }
    }

    private fun showProgrammePlaylistsRecyclerview(programmes: List<Programme>) {
        if (programmes.isNotEmpty()) {
            val uniqueAccounts = programmes
                .mapNotNull { it.tvchannels.target.account.target }
                .distinctBy { it.id }.sortedBy { it.name }
            if (uniqueAccounts.isNotEmpty()) {
                helpViewModel.currentSelectedWatchlist = "PROGRAMME"
                playlistAdapter?.submitList(uniqueAccounts)
                binding.recyclerPlaylists.post {
                    binding.recyclerPlaylists.setSelectedPosition(0)
                    showFocusedPlaylistProgrammes(uniqueAccounts.first())
                }
            } else {
                Log.d("WL PROGRAMME", "LEER")
            }
        }
    }

    fun showFocusedPlaylistMovies(accounts: Accounts) {
        if (helpViewModel.currentWatchListMovieAccount?.id != accounts.id) {
            val oldPosition = playlistAdapter?.currentList?.indexOf(helpViewModel.currentWatchListMovieAccount)
            val newPosition = playlistAdapter?.currentList?.indexOf(accounts)
            helpViewModel.currentWatchListMovieAccount = accounts
            if (oldPosition != null && oldPosition != -1) {
                playlistAdapter?.notifyItemChanged(oldPosition)
            }
            if (newPosition != null) {
                playlistAdapter?.notifyItemChanged(newPosition)
            }
            val filteredMovies = moviesList?.filter { it.movieAccount.target.id == accounts.id }
            if (!filteredMovies.isNullOrEmpty()) {
                if (binding.recyclerItems.adapter == seriesAdapter) {
                    prepareMoviesRecyclerView()
                }
                binding.recyclerPlaylists.visibility = View.VISIBLE
                val sortedMovies = filteredMovies.sortedBy { it.movieName }
                moviesAdapter?.submitList(sortedMovies)
                updateMovieUi(sortedMovies.first())

            } else {
                Toast.makeText(
                    this@WatchListFragment.requireActivity(),
                    "No movies found!",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }
    }

    fun showFocusedPlaylistSeries(accounts: Accounts) {
        Log.d("SERIENWATCHLISTVERGLEICH", "VM: ${helpViewModel.currentWatchListSeriesAccount?.id} ACC: ${accounts.id}")
        if (helpViewModel.currentWatchListSeriesAccount?.id != accounts.id) {
            val oldPosition = playlistAdapter?.currentList?.indexOf(helpViewModel.currentWatchListSeriesAccount)
            val newPosition = playlistAdapter?.currentList?.indexOf(accounts)
            helpViewModel.currentWatchListSeriesAccount = accounts
            if (oldPosition != null) {
                playlistAdapter?.notifyItemChanged(oldPosition)
            }
            if (newPosition != null) {
                playlistAdapter?.notifyItemChanged(newPosition)
            }
            val filteredSeries = seriesList?.filter { it.seriesAccount.target.id == accounts.id }
            if (!filteredSeries.isNullOrEmpty()) {
                if (binding.recyclerItems.adapter == moviesAdapter) {
                    prepareSeriesRecyclerView()
                }
                binding.recyclerPlaylists.visibility = View.VISIBLE
                val sortedSeries = filteredSeries.sortedBy { it.seriesName }
                seriesAdapter?.submitList(sortedSeries)
                updateSeriesUi(sortedSeries.first())
            } else {
                Toast.makeText(
                    this@WatchListFragment.requireActivity(),
                    "No series found!",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }
    }

    fun showFocusedPlaylistProgrammes(accounts: Accounts) {
        if (helpViewModel.currentWatchListProgrammeAccount?.id != accounts.id) {
            val oldPosition = playlistAdapter?.currentList?.indexOf(helpViewModel.currentWatchListProgrammeAccount)
            val newPosition = playlistAdapter?.currentList?.indexOf(accounts)
            helpViewModel.currentWatchListProgrammeAccount = accounts
            if (oldPosition != null) {
                playlistAdapter?.notifyItemChanged(oldPosition)
            }
            if (newPosition != null) {
                playlistAdapter?.notifyItemChanged(newPosition)
            }
            val filteredProgramme = programmeList?.filter { it.tvchannels.target.account.target.id == accounts.id }
            if (!filteredProgramme.isNullOrEmpty()) {
                binding.recyclerPlaylists.visibility = View.VISIBLE
                val sortedProgrammes = filteredProgramme.sortedBy { it.startTimeStamp }
                programmesAdapter?.submitList(sortedProgrammes)
            } else {
                Toast.makeText(
                    this@WatchListFragment.requireActivity(),
                    "No programmes found!",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }
    }

    fun focusToMovieOrSerie() {
        if (helpViewModel.currentSelectedWatchlist == "MOVIE") {
            binding.rvWatchlistMovies.requestFocus()
        } else if (helpViewModel.currentSelectedWatchlist == "SERIE") {
            binding.rvWatchlistSeries.requestFocus()
        } else if (helpViewModel.currentSelectedWatchlist == "PROGRAMME") {
            binding.rvWatchlistProgramme.requestFocus()
        } else {
            return
        }
    }

    fun focusToMovieOrSerieFromDetail() {
        if (helpViewModel.currentSelectedWatchlist == "MOVIE") {
            if (binding.recyclerItems.isVisible && !moviesAdapter?.currentList.isNullOrEmpty()) {
                binding.recyclerItems.requestFocus()
            } else {
                binding.rvWatchlistMovies.requestFocus()
            }
        } else if (helpViewModel.currentSelectedWatchlist == "SERIE") {
            if (binding.recyclerItems.isVisible && !seriesAdapter?.currentList.isNullOrEmpty()) {
                binding.recyclerItems.requestFocus()
            } else {
                binding.rvWatchlistSeries.requestFocus()
            }
        } else if (helpViewModel.currentSelectedWatchlist == "PROGRAMME") {
            if (binding.recyclerProgramme.isVisible && !programmesAdapter?.currentList.isNullOrEmpty()) {
                binding.recyclerItems.requestFocus()
            } else {
                binding.rvWatchlistProgramme.requestFocus()
            }
        } else {
            return
        }
    }

    fun focusToRecyclerview() {
        if (helpViewModel.currentSelectedWatchlist == "PROGRAMME") {
            binding.recyclerProgramme.requestFocus()
        } else {
            binding.recyclerItems.requestFocus()
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

    private fun prepareMoviesRecyclerView() {
        moviesAdapter = WatchListMoviesAdapter(onMovieClickListener, this, helpViewModel)
        binding.recyclerItems.apply {
            adapter = moviesAdapter
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private fun prepareSeriesRecyclerView() {
        seriesAdapter = WatchlistSeriesAdapter(onSeriesClickListener, this, helpViewModel)
        binding.recyclerItems.apply {
            adapter = seriesAdapter
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
        }
    }


    private fun prepareProgrammesRecyclerView() {
        binding.recyclerProgramme.visibility = View.VISIBLE
        programmesAdapter = WatchListProgrammeAdapter(onProgrammeClickListener, this, helpViewModel)
        binding.recyclerProgramme.apply {
            adapter = programmesAdapter
            setSmoothFocusChangesEnabled(false)
            addItemDecoration(
                DpadLinearSpacingDecoration.create(
                    perpendicularEdgeSpacing = 32,
                    itemSpacing = 14,
                    edgeSpacing = 16
                )
            )
        }
    }

    private val onMovieClickListener = WatchListMoviesAdapter.OnClickListener { movie ->
        helpViewModel.currentSelectedWatchlist = "MOVIE"
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

    private val onSeriesClickListener = WatchlistSeriesAdapter.OnClickListener { serie ->
        helpViewModel.currentSelectedWatchlist = "SERIE"
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
            } else {
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
            }
        }
    }

    private val onProgrammeClickListener = WatchListProgrammeAdapter.OnClickListener {
        helpViewModel.currentSelectedWatchlist = "TV"
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
        val epgDataOB = program.epgData.target
        if (binding.relLayoutProgramdescr.isGone) {
            binding.relLayoutProgramdescr.visibility = View.VISIBLE
        }
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
        binding.relLayoutProgramdescr.visibility = View.GONE
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
        moviesList = moviesList?.filter { it.id != movie.id }?.toMutableList()
        val oldMoviePosition = moviesList?.indexOf(movie)
        // Neue Account-Liste berechnen
        val uniqueAccounts = moviesList.orEmpty()
            .mapNotNull { it.movieAccount.target }
            .distinctBy { it.id }
            .sortedBy { it.name }

        playlistAdapter?.submitList(uniqueAccounts)

        // Optional: Wenn aktuell ausgewählter Account keine Filme mehr hat
        // → auf nächsten springen oder UI leer anzeigen

        binding.recyclerPlaylists.post {
            if (uniqueAccounts.isNotEmpty()) {
                if (uniqueAccounts.contains(helpViewModel.currentWatchListMovieAccount)) {
                    val position = uniqueAccounts.indexOf(helpViewModel.currentWatchListMovieAccount)
                    binding.recyclerPlaylists.setSelectedPosition(position)
                    val filteredMovies = moviesList?.filter { it.movieAccount.target.id == helpViewModel.currentWatchListMovieAccount!!.id }
                    moviesAdapter?.submitList(filteredMovies)
                    if (!filteredMovies.isNullOrEmpty()) {
                        if (oldMoviePosition != null && oldMoviePosition > 0) {
                            setMovieDetailsUi(filteredMovies[oldMoviePosition - 1])
                        } else {
                            setMovieDetailsUi(filteredMovies.first())
                        }
                    }
                } else {
                    binding.recyclerPlaylists.setSelectedPosition(0)
                    val filteredMovies = moviesList?.filter { it.movieAccount.target.id == uniqueAccounts.first().id }?.sortedBy { it.movieName }
                    moviesAdapter?.submitList(filteredMovies)
                    if (!filteredMovies.isNullOrEmpty()) {
                        setMovieDetailsUi(filteredMovies.first())
                    }
                }
            } else {
                // Keine Accounts mehr
                binding.recyclerItems.visibility = View.INVISIBLE
                resetMovieDetailsUi()
                binding.tvNodatafound.visibility = View.VISIBLE
            }
        }
    }

    fun updateSerie(serie: SeriesOB) {
        // Entferne Film
        val oldSeriesPosition = seriesList?.indexOf(serie)
        seriesList = seriesList?.filter { it.id != serie.id }?.toMutableList()
        // Neue Account-Liste berechnen
        val uniqueAccounts = seriesList.orEmpty()
            .mapNotNull { it.seriesAccount.target }
            .distinctBy { it.id }
            .sortedBy { it.name }

        playlistAdapter?.submitList(uniqueAccounts)

        // Optional: Wenn aktuell ausgewählter Account keine Filme mehr hat
        // → auf nächsten springen oder UI leer anzeigen

        binding.recyclerPlaylists.post {
            if (uniqueAccounts.isNotEmpty()) {
                if (uniqueAccounts.contains(helpViewModel.currentWatchListSeriesAccount)) {
                    val position = uniqueAccounts.indexOf(helpViewModel.currentWatchListSeriesAccount)
                    binding.recyclerPlaylists.setSelectedPosition(position)
                    val filteredSeries = seriesList?.filter { it.seriesAccount.target.id == helpViewModel.currentWatchListSeriesAccount!!.id }
                    seriesAdapter?.submitList(filteredSeries)
                    if (!filteredSeries.isNullOrEmpty()) {
                        if (oldSeriesPosition != null && oldSeriesPosition > 0) {
                            setSeriesDetailsUi(filteredSeries[oldSeriesPosition - 1])
                        } else {
                            setSeriesDetailsUi(filteredSeries.first())
                        }
                    }
                } else {
                    binding.recyclerPlaylists.setSelectedPosition(0)
                    val filteredSeries = seriesList?.filter { it.seriesAccount.target.id == uniqueAccounts.first().id }?.sortedBy { it.seriesName }
                    seriesAdapter?.submitList(filteredSeries)
                    if (!filteredSeries.isNullOrEmpty()) {
                        setSeriesDetailsUi(filteredSeries.first())
                    }
                }
            } else {
                // Keine Accounts mehr
                resetSeriesDetailsUi()
                seriesAdapter?.submitList(emptyList())
                binding.tvNodatafound.visibility = View.VISIBLE
            }
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
        helpViewModel.currentSelectedWatchlist = ""
        helpViewModel.currentWatchListSeriesAccount = null
        helpViewModel.currentWatchListMovieAccount = null
        helpViewModel.isWatchlistContainerOpened = false
        moviesAdapter = null
        seriesAdapter = null
        playlistAdapter = null
        seriesDetailJob?.cancel()
        _binding = null
    }
}