package com.example.mj_player_tv.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpisodesOB
import com.example.mj_player_tv.database.entity.EpisodesOB_
import com.example.mj_player_tv.database.entity.SeasonsOB
import com.example.mj_player_tv.database.entity.SeasonsOB_
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.databinding.FragmentSeriesDetailBinding
import com.example.mj_player_tv.network.model.tmdb.seasondetails.TMDBSeasonDetails
import com.example.mj_player_tv.ui.adapter.EpisodesAdapter
import com.example.mj_player_tv.ui.adapter.SeasonsAdapter
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.SeriesViewModel
import com.example.mj_player_tv.viewmodel.SeriesViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration

@UnstableApi
class SeriesDetailFragment : Fragment(R.layout.fragment_series_detail) {

    private var _binding: FragmentSeriesDetailBinding? = null

    private val binding get() = _binding!!

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val seriesBox: Box<SeriesOB> = ObjectBox.store.boxFor(SeriesOB::class.java)

    private val seasonBox: Box<SeasonsOB> = ObjectBox.store.boxFor(SeasonsOB::class.java)

    private val episodeBox: Box<EpisodesOB> = ObjectBox.store.boxFor(EpisodesOB::class.java)

    private lateinit var seasonsAdapter: SeasonsAdapter

    private lateinit var episodesAdapter: EpisodesAdapter

    private var selectedSeason: SeasonsOB? = null

    private var currentAccount: Accounts? = null

    private var isFirstOpen = true

    private var firstEpisodeOpen = true

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSeriesDetailBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loadseriesDetailProgressBar.visibility = View.VISIBLE

        prepareSeasonsRecyclerView()

        prepareEpisodesRecyclerView()

        binding.constSeries.requestFocus()

        if (helpViewModel.currentFocusedSerie != null) {
            currentAccount = helpViewModel.currentFocusedSerie?.accountId?.let { accountBox.get(it) }
            Log.d("SERIESDETAILGLOBALSEARCH", "$currentAccount")
            if (currentAccount != null) {
                if (currentAccount!!.isXtream) {
                    if (!helpViewModel.focusedSeasons.isNullOrEmpty()) {
                        binding.loadseriesDetailProgressBar.visibility = View.INVISIBLE
                        seasonsAdapter.submitList(helpViewModel.focusedSeasons)
                        binding.rvLayoutSeriesSeasons.post {

                            if (helpViewModel.currentFocusedSerie?.lastWatchedSeason != 0) {
                                val lastSeason =
                                    helpViewModel.focusedSeasons?.firstOrNull { it.seasonNumber.toIntOrNull() == helpViewModel.currentFocusedSerie?.lastWatchedSeason }
                                val lastSeasonPosition =
                                    seasonsAdapter.currentList.indexOf(lastSeason)
                                Log.d("XTREAM SERIESDETAIL", "SEASON POSITION: $lastSeasonPosition = ${lastSeason?.seasonNumber}")
                                binding.rvLayoutSeriesSeasons.setSelectedPosition(lastSeasonPosition)
                                if (lastSeason != null) {
                                    showEpisodesForSeason(lastSeason)
                                } else {
                                    if (helpViewModel.focusedSeasons!!.firstOrNull() != null) {
                                        showEpisodesForSeason(helpViewModel.focusedSeasons!!.first())
                                    } else {
                                        noDataReceived()
                                    }
                                }
                            } else {
                                if (helpViewModel.focusedSeasons!!.firstOrNull() != null) {
                                    showEpisodesForSeason(helpViewModel.focusedSeasons!!.first())
                                } else {
                                    noDataReceived()
                                }
                            }
                        }
                        helpViewModel.currentFocusedSerie?.let {
                            showDetailUi(it)
                        }
                    } else {
                        noDataReceived()
                    }
                } else {
                    if (stalkerViewModel.seriesCache.containsKey(helpViewModel.currentFocusedSerie?.idByAccountData)) {
                        if (!helpViewModel.focusedSeasons.isNullOrEmpty()) {
                            binding.loadseriesDetailProgressBar.visibility = View.INVISIBLE
                            seasonsAdapter.submitList(helpViewModel.focusedSeasons)
                            binding.rvLayoutSeriesSeasons.post {
                                if (helpViewModel.currentFocusedSerie?.lastWatchedSeason != 0) {
                                    val lastSeason =
                                        helpViewModel.focusedSeasons?.firstOrNull { it.seasonNumber.toIntOrNull() == helpViewModel.currentFocusedSerie?.lastWatchedSeason }
                                    val lastSeasonPosition =
                                        seasonsAdapter.currentList.indexOf(lastSeason)
                                    binding.rvLayoutSeriesSeasons.setSelectedPosition(
                                        lastSeasonPosition
                                    )
                                    if (lastSeason != null) {
                                        showEpisodesForSeason(lastSeason)
                                    } else {
                                        if (helpViewModel.focusedSeasons!!.firstOrNull() != null) {
                                            showEpisodesForSeason(helpViewModel.focusedSeasons!!.first())
                                        } else {
                                            noDataReceived()
                                        }
                                    }
                                } else {
                                    if (helpViewModel.focusedSeasons!!.firstOrNull() != null) {
                                        showEpisodesForSeason(helpViewModel.focusedSeasons!!.first())
                                    } else {
                                        noDataReceived()
                                    }
                                }
                            }
                            helpViewModel.currentFocusedSerie?.let {
                                showDetailUi(it)
                            }
                        } else {
                            noDataReceived()
                        }
                    } else {
                        stalkerViewModel.seriesDetailData.observe(viewLifecycleOwner) { seasons ->
                            if (seasons.isNotEmpty() && isFirstOpen) {
                                helpViewModel.focusedSeasons = seasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                                    .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE }).toMutableList()
                                helpViewModel.focusedEpisodes = stalkerViewModel.episodesList.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })).toMutableList()
                                binding.loadseriesDetailProgressBar.visibility = View.INVISIBLE
                                seasonsAdapter.submitList(helpViewModel.focusedSeasons)
                                binding.rvLayoutSeriesSeasons.post {
                                    if (helpViewModel.currentFocusedSerie?.lastWatchedSeason != 0) {
                                        val lastSeason =
                                            seasons.firstOrNull { it.seasonNumber.toIntOrNull() == helpViewModel.currentFocusedSerie?.lastWatchedSeason }
                                        val lastSeasonPosition =
                                            seasonsAdapter.currentList.indexOf(lastSeason)
                                        binding.rvLayoutSeriesSeasons.setSelectedPosition(
                                            lastSeasonPosition
                                        )
                                        if (lastSeason != null) {
                                            Log.d("CHECK STALKER LOADING", "1")
                                            showEpisodesForSeason(lastSeason)
                                        } else {
                                            if (helpViewModel.focusedSeasons!!.firstOrNull() != null) {
                                                showEpisodesForSeason(helpViewModel.focusedSeasons!!.first())
                                                Log.d("CHECK STALKER LOADING", "2")
                                            } else {
                                                noDataReceived()
                                                Log.d("CHECK STALKER LOADING", "3")
                                            }
                                        }
                                    } else {
                                        if (helpViewModel.focusedSeasons!!.firstOrNull() != null) {
                                            showEpisodesForSeason(helpViewModel.focusedSeasons!!.first())
                                            Log.d("CHECK STALKER LOADING", "4")
                                        } else {
                                            noDataReceived()
                                            Log.d("CHECK STALKER LOADING", "5")
                                        }
                                    }
                                }
                                helpViewModel.currentFocusedSerie?.let {
                                    showDetailUi(it)
                                }
                                isFirstOpen = false
                            } else {
                                if (seasons.isEmpty() && isFirstOpen) {
                                    lifecycleScope.launch {
                                        delay(2000) // z. B. 400ms warten
                                        val latestSeasons = stalkerViewModel.seriesDetailData.value
                                        if (latestSeasons.isNullOrEmpty()) {
                                            noDataReceived()
                                            Log.d("CHECK STALKER LOADING", "6")
                                        }
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }


        val dp30 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 30f, resources.displayMetrics
        ).toInt()

        binding.constSeries.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                closeFragment()
                return@setOnKeyListener true
            }
            false
        }


        binding.btnPlay.setOnFocusChangeListener { _, hasFocus ->
            val params = binding.btnPlay.layoutParams
            if (hasFocus) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.btnPlay.layoutParams = params
            } else {
                params.width = dp30
                binding.btnPlay.layoutParams = params
            }
        }

        binding.btnPlay.setOnKeyListener { v, keyCode, event ->
            if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) {
                closeFragment()
                return@setOnKeyListener true
            }
            false
        }

        binding.btnPlay.setOnClickListener {
            selectedSeason = helpViewModel.currentFocusedSeason
            playSerie(PlaySeriesFragment())
        }

        binding.btnaddFavorite.setOnFocusChangeListener { _, hasFocus ->
            val params = binding.btnaddFavorite.layoutParams
            if (hasFocus) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.btnaddFavorite.layoutParams = params
            } else {
                params.width = dp30
                binding.btnaddFavorite.layoutParams = params
            }
        }

        binding.btnaddFavorite.setOnKeyListener { v, keyCode, event ->
            if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) {
                closeFragment()
                return@setOnKeyListener true
            }
            false
        }

        binding.btnaddFavorite.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                Log.d("SERIEDETAILWATCHLIST", "${helpViewModel.currentFocusedSerie}")
                if (!helpViewModel.currentFocusedSerie!!.isFavorite) {
                    helpViewModel.currentFocusedSerie!!.isFavorite = true
                    binding.btnaddFavorite.text = "Remove from Watchlist"
                    binding.btnaddFavorite.isSelected = true
                    binding.ivFavorite.visibility = View.VISIBLE
                    helpViewModel.currentFocusedSerie?.let {
                        Log.d("SERIE TOTAL SEASON", "FAVORITE: ${it.totalSeasons}")
                        it.seriesAccount.target = helpViewModel.currentSeriesAccount
                        it.seriescat.target = helpViewModel.currentSeriesCategoryOB
                        seriesBox.put(it)
                    }
                } else {
                    helpViewModel.currentFocusedSerie!!.isFavorite = false
                    binding.btnaddFavorite.text = "Add to Watchlist"
                    binding.btnaddFavorite.isSelected = false
                        binding.ivFavorite.visibility = View.GONE
                    if (helpViewModel.currentFocusedSerie != null) {
                        if (helpViewModel.currentFocusedSerie!!.isPartlyWatched || helpViewModel.currentFocusedSerie!!.isCompletelyWatched) {
                            helpViewModel.currentFocusedSerie?.let {
                                it.seriesAccount.target = helpViewModel.currentSeriesAccount
                                it.seriescat.target = helpViewModel.currentSeriesCategoryOB
                                seriesBox.put(it)
                            }
                        } else {
                            seasonBox.query(
                                SeasonsOB_.seriesIdByAccount.equal(helpViewModel.currentFocusedSerie!!.idByAccountData)
                            ).build().remove()
                            episodeBox.query(
                                EpisodesOB_.seriesIdByAccount.equal(helpViewModel.currentFocusedSerie!!.idByAccountData)
                            ).build().remove()
                            helpViewModel.currentFocusedSerie?.let {
                                seriesBox.remove(it)
                            }
                        }
                    }
                }
                binding.btnaddFavorite.requestFocus()
                updateFavorite()
                updateSeriesInRV()
            }
        }

        binding.btnaddWatched.setOnFocusChangeListener { _, hasFocus ->
            val params = binding.btnaddWatched.layoutParams
            if (hasFocus) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.btnaddWatched.layoutParams = params
            } else {
                params.width = dp30
                binding.btnaddWatched.layoutParams = params
            }
        }

        binding.btnaddWatched.setOnKeyListener { v, keyCode, event ->
            if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) {
                closeFragment()
                return@setOnKeyListener true
            }
            false
        }

        binding.btnaddWatched.setOnClickListener {
            if (helpViewModel.currentFocusedSerie != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (!helpViewModel.currentFocusedSerie!!.isCompletelyWatched) {
                        helpViewModel.currentFocusedSerie!!.isCompletelyWatched = true
                        helpViewModel.currentFocusedSerie!!.isPartlyWatched = false
                        binding.btnaddWatched.text = "Mark as unwatched"
                        binding.btnPlay.text = "Re-Watch"
                        helpViewModel.currentFocusedSerie!!.seriesPercentagePlayed = 1.0
                        helpViewModel.currentFocusedSerie!!.currentPosition = 0L
                        helpViewModel.currentFocusedSerie!!.lastWatchedEpisode = 1
                        helpViewModel.currentFocusedSerie!!.lastWatchedSeason = 1
                        helpViewModel.currentFocusedSeason!!.isSeasonPartlyWatched = false
                        helpViewModel.currentFocusedSeason!!.isSeasonFullyWatched = true
                        helpViewModel.currentFocusedSeason!!.seasonPercentagePlayed = 1.0
                        helpViewModel.currentFocusedEpisode!!.episodePercentagePlayed = 1.0
                        helpViewModel.currentFocusedEpisode!!.isEpisodePartlyWatched = false
                        helpViewModel.currentFocusedEpisode!!.isEpisodeFullyWatched = true
                        helpViewModel.currentFocusedEpisode!!.currentPosition = 0
                        binding.progressBar.progress = 100
                        binding.tvRemainingTime.text = "Completed!"
                        binding.tvRemainingTime.visibility = View.VISIBLE
                        binding.btnaddWatched.isSelected = true
                        if (helpViewModel.currentFocusedSerie != null) {
                            helpViewModel.currentFocusedSerie?.let {
                                Log.d("SERIE TOTAL SEASON", "WATCHED: ${it.totalSeasons}")
                                it.seriesAccount.target = helpViewModel.currentSeriesAccount
                                it.seriescat.target = helpViewModel.currentSeriesCategoryOB
                                seriesBox.put(it)
                            }
                            val seasons = seasonBox.query(
                                SeasonsOB_.seriesIdByAccount.equal(helpViewModel.currentFocusedSerie!!.idByAccountData)
                            ).build().find()
                            val episodes = episodeBox.query(
                                EpisodesOB_.seriesIdByAccount.equal(helpViewModel.currentFocusedSerie!!.idByAccountData)
                            ).build().find()
                            seasons.forEach {
                                it.isSeasonPartlyWatched = false
                                it.isSeasonFullyWatched = true
                                it.seasonPercentagePlayed = 1.0
                            }
                            helpViewModel.focusedSeasons?.forEach {
                                it.isSeasonPartlyWatched = false
                                it.isSeasonFullyWatched = true
                                it.seasonPercentagePlayed = 1.0
                            }
                            episodes.forEach {
                                it.isEpisodeFullyWatched = true
                                it.isEpisodePartlyWatched = false
                                it.episodePercentagePlayed = 1.0
                                it.currentPosition = 0L
                            }
                            helpViewModel.focusedEpisodes?.forEach {
                                it.isEpisodeFullyWatched = true
                                it.isEpisodePartlyWatched = false
                                it.episodePercentagePlayed = 1.0
                                it.currentPosition = 0L
                            }
                            seasonBox.put(seasons)
                            episodeBox.put(episodes)
                            seasonsAdapter.submitList(null)
                            seasonsAdapter.submitList(helpViewModel.focusedSeasons)
                            val seasonPosition = seasonsAdapter.currentList.indexOf(helpViewModel.currentFocusedSeason)
                            binding.rvLayoutSeriesSeasons.post {
                                binding.rvLayoutSeriesSeasons.setSelectedPosition(seasonPosition)
                            }
                            episodesAdapter.submitList(null)
                            episodesAdapter.submitList(helpViewModel.focusedEpisodes?.filter { it.seasonNumber == helpViewModel.currentFocusedSeason?.seasonNumber }?.sortedBy { it.episodeNumber })
                            val episodePosition = episodesAdapter.currentList.indexOf(helpViewModel.currentFocusedEpisode)
                            binding.rvLayoutSeriesEpisodes.post {
                                binding.rvLayoutSeriesEpisodes.setSelectedPosition(episodePosition)
                            }
                        }
                        showFocusedEpisodeInfos(helpViewModel.currentFocusedEpisode!!)
                        binding.btnaddWatched.requestFocus()
                        updateSeriesInRV()
                        helpViewModel.focusedSeasons?.forEach {
                            Log.d("SEASON ÜBERSICHT ALLE OK", "${helpViewModel.currentFocusedSerie?.seriesName} = ${it.seasonNumber} PARTLY: ${it.isSeasonPartlyWatched} FULL: ${it.isSeasonFullyWatched}")
                        }
                    } else {
                        helpViewModel.currentFocusedSerie!!.isCompletelyWatched = false
                        helpViewModel.currentFocusedSerie!!.isPartlyWatched = false
                        binding.btnaddWatched.text = "Mark as watched"
                        binding.btnPlay.text = "Play S${helpViewModel.currentFocusedSerie?.lastWatchedSeason} E${helpViewModel.currentFocusedSerie?.lastWatchedEpisode}"
                        helpViewModel.currentFocusedSerie!!.seriesPercentagePlayed = 0.0
                        helpViewModel.currentFocusedSerie!!.currentPosition = 0L
                        helpViewModel.currentFocusedSerie!!.lastWatchedEpisode = 1
                        helpViewModel.currentFocusedSerie!!.lastWatchedSeason = 1
                        helpViewModel.currentFocusedSeason!!.isSeasonPartlyWatched = false
                        helpViewModel.currentFocusedSeason!!.isSeasonFullyWatched = false
                        helpViewModel.currentFocusedSeason!!.seasonPercentagePlayed = 0.0
                        helpViewModel.currentFocusedEpisode!!.episodePercentagePlayed = 0.0
                        helpViewModel.currentFocusedEpisode!!.isEpisodePartlyWatched = false
                        helpViewModel.currentFocusedEpisode!!.isEpisodeFullyWatched = false
                        helpViewModel.currentFocusedEpisode!!.currentPosition = 0
                        binding.btnaddWatched.isSelected = false
                        binding.progressBar.progress = 0
                        binding.tvRemainingTime.visibility = View.GONE
                        if (helpViewModel.currentFocusedSerie != null) {
                            val seasons = seasonBox.query(
                                SeasonsOB_.seriesIdByAccount.equal(helpViewModel.currentFocusedSerie!!.idByAccountData)
                            ).build().find()
                            val episodes = episodeBox.query(
                                EpisodesOB_.seriesIdByAccount.equal(helpViewModel.currentFocusedSerie!!.idByAccountData)
                            ).build().find()
                            if (helpViewModel.currentFocusedSerie!!.isFavorite) {
                                helpViewModel.currentFocusedSerie?.let {
                                    it.seriesAccount.target = helpViewModel.currentSeriesAccount
                                    it.seriescat.target = helpViewModel.currentSeriesCategoryOB
                                    seriesBox.put(it)
                                }
                                seasons.forEach {
                                    it.isSeasonPartlyWatched = false
                                    it.isSeasonFullyWatched = false
                                    it.seasonPercentagePlayed = 0.0
                                }
                                helpViewModel.focusedSeasons?.forEach {
                                    it.isSeasonPartlyWatched = false
                                    it.isSeasonFullyWatched = false
                                    it.seasonPercentagePlayed = 0.0
                                }
                                episodes.forEach {
                                    it.isEpisodeFullyWatched = false
                                    it.episodePercentagePlayed = 0.0
                                    it.currentPosition = 0
                                }
                                helpViewModel.focusedEpisodes?.forEach {
                                    it.isEpisodeFullyWatched = false
                                    it.currentPosition = 0
                                    it.episodePercentagePlayed = 0.0
                                }
                                seasonBox.put(seasons)
                                episodeBox.put(episodes)
                            } else {
                                helpViewModel.focusedSeasons?.forEach {
                                    it.isSeasonFullyWatched = false
                                    it.isSeasonPartlyWatched = false
                                    it.seasonPercentagePlayed = 0.0
                                }
                                helpViewModel.focusedEpisodes?.forEach {
                                    it.isEpisodeFullyWatched = false
                                    it.isEpisodePartlyWatched = false
                                    it.currentPosition = 0
                                    it.episodePercentagePlayed = 0.0
                                }
                                seasonBox.remove(seasons)
                                episodeBox.remove(episodes)
                                helpViewModel.currentFocusedSerie?.let {
                                    seriesBox.remove(it)
                                }
                            }
                            seasonsAdapter.submitList(null)
                            helpViewModel.currentFocusedSeason = helpViewModel.focusedSeasons?.firstOrNull()
                            seasonsAdapter.submitList(helpViewModel.focusedSeasons)
                            val seasonPosition = seasonsAdapter.currentList.indexOf(helpViewModel.currentFocusedSeason)
                            binding.rvLayoutSeriesSeasons.post {
                                binding.rvLayoutSeriesSeasons.setSelectedPosition(seasonPosition)
                            }
                            helpViewModel.currentFocusedEpisode = helpViewModel.focusedEpisodes?.filter { it.seasonNumber == helpViewModel.currentFocusedSeason?.seasonNumber }?.sortedBy { it.episodeNumber }?.firstOrNull()
                            episodesAdapter.submitList(null)
                            episodesAdapter.submitList(helpViewModel.focusedEpisodes?.filter { it.seasonNumber == helpViewModel.currentFocusedSeason?.seasonNumber }?.sortedBy { it.episodeNumber })
                            val episodePosition = episodesAdapter.currentList.indexOf(helpViewModel.currentFocusedEpisode)
                            binding.rvLayoutSeriesEpisodes.post {
                                binding.rvLayoutSeriesEpisodes.setSelectedPosition(episodePosition)
                            }
                        }
                        showFocusedEpisodeInfos(helpViewModel.currentFocusedEpisode!!)
                        binding.btnaddWatched.requestFocus()
                        updateSeriesInRV()
                    }
                }
            }
        }


        binding.btnInfo.setOnFocusChangeListener { _, hasFocus ->
            val params = binding.btnInfo.layoutParams
            if (hasFocus) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.btnInfo.layoutParams = params
            } else {
                params.width = dp30
                binding.btnInfo.layoutParams = params
            }
        }

        binding.btnInfo.setOnKeyListener { v, keyCode, event ->
            if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) {
                closeFragment()
                return@setOnKeyListener true
            }
            false
        }

        binding.focusBlocker.setOnKeyListener { v, keyCode, event ->
            true
        }

        seriesViewModel.focusRequest.observe(viewLifecycleOwner) {
            setFocusToNextEpisodeAndSeason()
        }
    }

    private fun noDataReceived() {
        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(
                this@SeriesDetailFragment.requireActivity(),
                "No data received for ${helpViewModel.currentFocusedSerie!!.seriesName}",
                Toast.LENGTH_SHORT
            ).show()
            delay(500)
            isFirstOpen = false
            closeFragment()
        }
    }

    private fun prepareSeasonsRecyclerView() {
        seasonsAdapter = SeasonsAdapter(binding.rvLayoutSeriesSeasons, onSeasonClickListener, onSeasonLongClickListener, helpViewModel, this)
        binding.rvLayoutSeriesSeasons.apply {
            adapter = seasonsAdapter
            setSmoothFocusChangesEnabled(false)
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(true, false)
        }
    }

    private fun prepareEpisodesRecyclerView() {
        episodesAdapter = EpisodesAdapter(onEpisodeClickListener, onEpisodeLongClickListener, helpViewModel, this)
        binding.rvLayoutSeriesEpisodes.apply {
            adapter = episodesAdapter
            setSmoothFocusChangesEnabled(false)
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(true, false)
            addItemDecoration(
                DpadLinearSpacingDecoration.create(
                    itemSpacing = 12,
                    edgeSpacing = 6,
                    perpendicularEdgeSpacing = 6
                )
            )
        }
    }

    fun updateSeriesInRV() {
        if (helpViewModel.isWatchlistContainerOpened) {
            val watchlistFragment =
                parentFragmentManager.findFragmentById(R.id.container_watchlist_stats)
            if (watchlistFragment is WatchListFragment && helpViewModel.currentFocusedSerie != null) {
                watchlistFragment.updateSerie(helpViewModel.currentFocusedSerie!!)
            }
        } else {
            seriesViewModel.requestUpdateSerieInRV()
        }
    }

    private val onSeasonClickListener = SeasonsAdapter.OnClickListener {
        focusToEpisodes()
    }

    private val onSeasonLongClickListener = SeasonsAdapter.OnLongClickListener { season, view ->
        val position = seasonsAdapter.currentList.indexOf(season)
        showSeasonPopup(season, view, position)
    }

    private val onEpisodeClickListener = EpisodesAdapter.OnClickListener {
        focusToPlayButton()
    }

    private val onEpisodeLongClickListener = EpisodesAdapter.OnLongClickListener { episode, view ->
        val position = episodesAdapter.currentList.indexOf(episode)
        showEpisodePopup(episode, view, position)
    }

    private fun showSeasonPopup(season: SeasonsOB, view: View, position: Int) {
        val popup = PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.menu_season_options, popup.menu)
        val seasonFullySeen = season.isSeasonFullyWatched // <- Eigene Logik, z. B. aus DB oder List
        val seasonPartlySeen = season.isSeasonPartlyWatched
        val seenItem = popup.menu.findItem(R.id.mark_seen)
        val unseenItem = popup.menu.findItem(R.id.mark_unseen)
        if (seasonFullySeen) {
            seenItem.setVisible(false)
            unseenItem.setVisible(true)
        } else {
            if (seasonPartlySeen) {
                seenItem.setVisible(true)
                unseenItem.setVisible(true)
            } else {
                unseenItem.setVisible(false)
                seenItem.setVisible(true)
            }
        }
        var wasItemClicked = false
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.mark_seen -> {
                    wasItemClicked = true
                    markSeasonAsSeen(season)
                    true
                }
                R.id.mark_unseen -> {
                    wasItemClicked = true
                    markSeasonAsUnseen(season)
                    true
                }
                else -> false
            }
        }
        popup.setOnDismissListener {
            if (!wasItemClicked) {
                wasItemClicked = false
                binding.rvLayoutSeriesSeasons.requestFocus()
            }
        }
        popup.show()
    }


    private fun showConfirmMarkAllAsSeenDialog(onConfirm: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setMessage("Mark all unseen seasons as seen?")
            .setPositiveButton("Yes") { _, _ -> onConfirm() }
            .setNegativeButton("No", null)
            .show()
    }


    private fun showEpisodePopup(episode: EpisodesOB, view: View, position: Int) {
        val popup = PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.menu_episode_options, popup.menu)
        val episodeFullySeen =
            episode.isEpisodeFullyWatched // <- Eigene Logik, z. B. aus DB oder List
        val episodePartlySeen = episode.isEpisodePartlyWatched
        val seenItem = popup.menu.findItem(R.id.mark_seen)
        val unseenItem = popup.menu.findItem(R.id.mark_unseen)
        if (!episodeFullySeen) {
            if (episodePartlySeen) {
                seenItem.setVisible(true)
                unseenItem.setVisible(true)
            } else {
                seenItem.setVisible(true)
                unseenItem.setVisible(false)
            }
        } else {
            unseenItem.setVisible(true)
            seenItem.setVisible(false)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.mark_seen -> {
                    markEpisodeAsSeen(episode)
                    true
                }
                R.id.mark_unseen -> {
                    markEpisodeAsUnseen(episode)
                    true
                }
                R.id.play_episode -> {
                    if (helpViewModel.currentFocusedEpisode?.currentPosition == 0L) {
                        playSerie(PlaySeriesFragment())
                    } else {
                        showPlayOptionDialog(this@SeriesDetailFragment.requireActivity(), episode.currentPosition, position, episode)
                    }
                    true
                }
                else -> false
            }
        }
        popup.setOnDismissListener {
            binding.rvLayoutSeriesEpisodes.requestFocus()
        }
        popup.show()
    }

    var playEpisodeAfterCalculation = false

    private fun showPlayOptionDialog(context: Context, resumePosition: Long, position: Int, episode: EpisodesOB) {
        val options = arrayOf(
            "Resume from ${formatTime(resumePosition)}",
            "Start from beginning"
        )

        AlertDialog.Builder(context)
            .setTitle("Play episode")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playSerie(PlaySeriesFragment())
                    1 -> {
                        playEpisodeAfterCalculation = true
                        markEpisodeAsUnseen(episode)
                    }
                }
            }
            .show()
    }

    private fun formatTime(positionMillis: Long): String {
        val seconds = positionMillis / 1000
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", minutes, secs)
    }

    private fun markEpisodeAsSeen(episode: EpisodesOB) {
        val position = episodesAdapter.currentList.indexOf(episode)
        episode.isEpisodeFullyWatched = true
        episode.isEpisodePartlyWatched = false
        episode.episodePercentagePlayed = 1.0
        episode.currentPosition = 0L
        episodeBox.put(episode)
        episodesAdapter.notifyItemChanged(position)
        val lastEpisodeInSeason = isLastEpisodeFromSeason(episode)

        if (lastEpisodeInSeason) {
            val isLastSeasonOfSerie = if (helpViewModel.currentFocusedSeason != null) {
                isLastSeasonOfSeries(helpViewModel.currentFocusedSeason!!)
            } else {
                false
            }
            if (!isLastSeasonOfSerie) {
                val nextSeason = getNextSeasonofSerie(episode.seasonNumber?.toIntOrNull() ?: 0)
                helpViewModel.currentFocusedSerie?.lastWatchedSeason = nextSeason?.seasonNumber?.toIntOrNull() ?: 0
                val firstEpisodeOfNewSeason =
                    helpViewModel.focusedEpisodes?.filter { it.seasonNumber == nextSeason?.seasonNumber }
                        ?.minByOrNull { it.episodeNumber }
                helpViewModel.currentFocusedSerie?.lastWatchedEpisode = firstEpisodeOfNewSeason?.episodeNumber ?: 0
            } else {
                val firstSeason = getFirstSeasonNumber()
                helpViewModel.currentFocusedSerie?.lastWatchedSeason = firstSeason?.seasonNumber?.toIntOrNull() ?: 0
                helpViewModel.currentFocusedSerie?.lastWatchedEpisode = getFirstEpisodeFromFirstSeasonNumber(firstSeason)?.episodeNumber ?: 0
            }
        } else {
            val nextEpisode =
                helpViewModel.focusedEpisodes?.filter { it.seasonNumber == episode.seasonNumber && it.episodeNumber > episode.episodeNumber }
                    ?.minByOrNull { it.episodeNumber }
            if (nextEpisode != null) {
                helpViewModel.currentFocusedSerie?.lastWatchedEpisode =
                    nextEpisode.episodeNumber ?: 0
            } else {
                val nextEpisodeOverall =
                    helpViewModel.focusedEpisodes?.firstOrNull {
                        (it.seasonNumber?.toIntOrNull() ?: 0) > (episode.seasonNumber?.toIntOrNull()
                            ?: 0) && !it.isEpisodeFullyWatched
                    }
                if (nextEpisodeOverall != null) {
                    helpViewModel.currentFocusedSerie?.lastWatchedEpisode = nextEpisodeOverall.episodeNumber
                    helpViewModel.currentFocusedSerie?.lastWatchedSeason = nextEpisodeOverall.seasonNumber?.toIntOrNull() ?: 0
                } else {
                    val episodeUnseenOverall = helpViewModel.focusedEpisodes?.firstOrNull { !it.isEpisodeFullyWatched }
                    if (episodeUnseenOverall != null) {
                        helpViewModel.currentFocusedSerie?.lastWatchedEpisode = episodeUnseenOverall.episodeNumber
                        helpViewModel.currentFocusedSerie?.lastWatchedSeason = episodeUnseenOverall.seasonNumber?.toIntOrNull() ?: 0
                    } else {
                        helpViewModel.currentFocusedSerie?.lastWatchedEpisode = 0
                        helpViewModel.currentFocusedSerie?.lastWatchedSeason = 0
                    }
                }
            }
        }
        updateSeason(episode)
        updateSeries()
    }

    private fun getFirstSeasonNumber(): SeasonsOB? {
        return helpViewModel.focusedSeasons?.sortedBy { it.seasonNumber.toIntOrNull() }?.first()
    }

    private fun getFirstEpisodeFromFirstSeasonNumber(season: SeasonsOB?) : EpisodesOB? {
        return helpViewModel.focusedEpisodes?.filter { it.seasonNumber == season?.seasonNumber }
            ?.minByOrNull { it.episodeNumber }
    }

    private fun isLastEpisodeFromSeason(episode: EpisodesOB): Boolean {
        return if (
            helpViewModel.focusedEpisodes?.filter {
                it.seasonNumber == episode.seasonNumber }?.none {
                    it.episodeNumber > episode.episodeNumber } == true
        ) {
            true
        } else {
            false
        }
    }

    private fun markEpisodeAsUnseen(episode: EpisodesOB) {
        val position = episodesAdapter.currentList.indexOf(episode)
        episode.isEpisodeFullyWatched = false
        episode.isEpisodePartlyWatched = false
        episode.episodePercentagePlayed = 0.0
        episode.currentPosition = 0
        episodeBox.put(episode)
        episodesAdapter.notifyItemChanged(position)
        val firstUnseenEpisode =
            helpViewModel.focusedEpisodes?.firstOrNull { !it.isEpisodeFullyWatched || it.isEpisodePartlyWatched }
        if (firstUnseenEpisode != null) {
            helpViewModel.currentFocusedSerie?.lastWatchedSeason =
                firstUnseenEpisode.seasonNumber?.toIntOrNull() ?: 0
            helpViewModel.currentFocusedSerie?.lastWatchedEpisode =
                firstUnseenEpisode.episodeNumber ?: 0
        } else {
            helpViewModel.currentFocusedSerie?.lastWatchedSeason = 0
            helpViewModel.currentFocusedSerie?.lastWatchedEpisode = 0
        }
        updateSeason(episode)
        updateSeries()
    }

    private fun markSeasonAsSeen(season: SeasonsOB) {
        val position = seasonsAdapter.currentList.indexOf(season)
        season.isSeasonFullyWatched = true
        season.isSeasonPartlyWatched = false
        season.seasonPercentagePlayed = 1.0
        helpViewModel.focusedEpisodes?.filter { it.seasonNumber == season.seasonNumber }?.forEach {
            it.episodePercentagePlayed = 1.0
            it.isEpisodeFullyWatched = true
            it.isEpisodePartlyWatched = false
            it.currentPosition = 0
        }
        helpViewModel.focusedEpisodes?.let {
            episodeBox.put(it)
        }
        seasonBox.put(season)
        seasonsAdapter.notifyItemChanged(position)
        episodesAdapter.notifyDataSetChanged()
        val allSeasonsWatched = helpViewModel.focusedSeasons?.all { it.isSeasonFullyWatched } == true
        if (allSeasonsWatched) {
            val firstSeason = getFirstSeasonNumber()
            helpViewModel.currentFocusedSerie?.lastWatchedSeason = firstSeason?.seasonNumber?.toIntOrNull() ?: 0
            helpViewModel.currentFocusedSerie?.lastWatchedEpisode = getFirstEpisodeFromFirstSeasonNumber(firstSeason)?.episodeNumber ?: 0
        } else {
            val nextSeason = getNextSeasonofSerie(season.seasonNumber.toIntOrNull() ?: 0)
            if (nextSeason != null) {
                helpViewModel.currentFocusedSerie?.lastWatchedSeason = nextSeason.seasonNumber.toIntOrNull() ?: 0
                val firstEpisodeOfNextSeason = getFirstEpisodeOfSeason(nextSeason)
                if (firstEpisodeOfNextSeason != null) {
                    helpViewModel.currentFocusedSerie?.lastWatchedEpisode = firstEpisodeOfNextSeason.episodeNumber
                } else {
                    val nextEpisodeOverall =
                        helpViewModel.focusedEpisodes?.firstOrNull {
                            (it.seasonNumber?.toIntOrNull() ?: 0) > (season.seasonNumber.toIntOrNull()
                                ?: 0) && !it.isEpisodeFullyWatched
                        }
                    if (nextEpisodeOverall != null) {
                        helpViewModel.currentFocusedSerie?.lastWatchedEpisode = nextEpisodeOverall.episodeNumber
                        helpViewModel.currentFocusedSerie?.lastWatchedSeason = nextEpisodeOverall.seasonNumber?.toIntOrNull() ?: 0
                    } else {
                        val episodeUnseenOverall = helpViewModel.focusedEpisodes?.firstOrNull { !it.isEpisodeFullyWatched }
                        if (episodeUnseenOverall != null) {
                            helpViewModel.currentFocusedSerie?.lastWatchedEpisode = episodeUnseenOverall.episodeNumber
                            helpViewModel.currentFocusedSerie?.lastWatchedSeason = episodeUnseenOverall.seasonNumber?.toIntOrNull() ?: 0
                        } else {
                            helpViewModel.currentFocusedSerie?.lastWatchedEpisode = 0
                            helpViewModel.currentFocusedSerie?.lastWatchedSeason = 0
                        }
                    }
                }
            }
        }
        updateSeries()
    }

    private fun markSeasonAsUnseen(season: SeasonsOB) {
        val position = seasonsAdapter.currentList.indexOf(season)
        helpViewModel.focusedEpisodes?.filter { it.seasonNumber == season.seasonNumber }?.forEach {
            it.isEpisodeFullyWatched = false
            it.isEpisodePartlyWatched = false
            it.episodePercentagePlayed = 0.0
            it.currentPosition = 0
        }
        helpViewModel.focusedEpisodes?.let {
            episodeBox.put(it)
        }
        season.isSeasonFullyWatched = false
        season.isSeasonPartlyWatched = false
        season.seasonPercentagePlayed = 0.0
        seasonBox.put(season)
        seasonsAdapter.notifyItemChanged(position)
        episodesAdapter.notifyDataSetChanged()
        val firstSeason = getFirstSeasonNumber()
        helpViewModel.currentFocusedSerie?.lastWatchedSeason = firstSeason?.seasonNumber?.toIntOrNull() ?: 0
        helpViewModel.currentFocusedSerie?.lastWatchedEpisode = getFirstEpisodeOfSeason(season)?.episodeNumber ?: 0
        updateSeries()
    }

    private fun updateSeason(episode: EpisodesOB) {
        val season = helpViewModel.focusedSeasons?.firstOrNull { it.seasonNumber == episode.seasonNumber }
        val seasonEpisodes = helpViewModel.focusedEpisodes?.filter { it.seasonNumber == episode.seasonNumber }
        if (seasonEpisodes?.all { it.isEpisodeFullyWatched } == true) {
            season?.isSeasonFullyWatched = true
            season?.isSeasonPartlyWatched = false
            season?.seasonPercentagePlayed = 1.0
        } else {
            if (seasonEpisodes?.any { it.isEpisodeFullyWatched || it.isEpisodePartlyWatched } == true) {
                season?.isSeasonFullyWatched = false
                season?.isSeasonPartlyWatched = true
                season?.seasonPercentagePlayed = calculateSeasonPercentagePlayed(episode.seasonNumber ?: "")
            } else {
                season?.isSeasonFullyWatched = false
                season?.isSeasonPartlyWatched = false
                season?.seasonPercentagePlayed = 0.0
            }
        }
        season?.let {
            seasonBox.put(it)
        }
        val position = seasonsAdapter.currentList.indexOf(season)
        seasonsAdapter.notifyItemChanged(position)
    }

    private fun updateSeries() {
        if (helpViewModel.focusedSeasons?.all { it.isSeasonFullyWatched } == true) {
            helpViewModel.currentFocusedSerie?.isCompletelyWatched = true
            helpViewModel.currentFocusedSerie?.isPartlyWatched = false
            helpViewModel.currentFocusedSerie?.seriesPercentagePlayed = 1.0
            helpViewModel.currentFocusedSerie?.currentPosition = 0L
        } else {
            if (helpViewModel.focusedSeasons?.any { it.isSeasonFullyWatched || it.isSeasonPartlyWatched } == true) {
                helpViewModel.currentFocusedSerie?.isCompletelyWatched = false
                helpViewModel.currentFocusedSerie?.isPartlyWatched = true
                helpViewModel.currentFocusedSerie?.seriesPercentagePlayed = calculateSeriesPercentagePlayed()
            } else {
                helpViewModel.currentFocusedSerie?.isCompletelyWatched = false
                helpViewModel.currentFocusedSerie?.isPartlyWatched = false
                helpViewModel.currentFocusedSerie?.seriesPercentagePlayed = 0.0
            }
        }
        helpViewModel.currentFocusedSerie?.let {
            seriesBox.put(it)
            saveSeriesChangesInDBAndCache()
            updateSerieStatus()
        }
    }

    private fun getNextSeasonofSerie(seasonNumber: Int): SeasonsOB? {
        return helpViewModel.focusedSeasons?.sortedBy { it.seasonNumber }?.firstOrNull {
            (it.seasonNumber.toIntOrNull() ?: 0) > seasonNumber
        }
    }

    private fun getFirstEpisodeOfSeason(season: SeasonsOB): EpisodesOB? {
        return helpViewModel.focusedEpisodes?.firstOrNull { it.seasonNumber == season.seasonNumber }
    }

    private fun isLastSeasonOfSeries(season: SeasonsOB): Boolean {
        return if (helpViewModel.focusedSeasons?.none {
                (it.seasonNumber.toIntOrNull() ?: 0) > (season.seasonNumber.toIntOrNull() ?: 0)
            } == true) {
            true
        } else {
            false
        }
    }


    fun calculateSeriesPercentagePlayed(): Double {
        val episodes = helpViewModel.focusedEpisodes
        if (episodes.isNullOrEmpty()) return 0.0

        val totalPercentage = episodes.sumOf { it.episodePercentagePlayed }
        val totalEpisodes = episodes.size.toDouble()

        return (totalPercentage / totalEpisodes)  // In Prozent umwandeln
    }

    fun calculateSeasonPercentagePlayed(seasonNumber: String): Double {
        val episodes = helpViewModel.focusedEpisodes?.filter { it.seasonNumber == seasonNumber }
        if (episodes.isNullOrEmpty()) return 0.0

        val totalPercentage = episodes.sumOf { it.episodePercentagePlayed }
        val totalEpisodes = episodes.size.toDouble() // Anzahl der Episoden als Double für die Division

        return totalPercentage / totalEpisodes
    }

    private fun updateSerieStatus() { // Speichern
        updateSeriesInRV()
        saveSeriesChangesInDBAndCache()
    }


    private fun saveSeriesChangesInDBAndCache() {
        if (helpViewModel.currentFocusedSerie != null) {
            val account =
                helpViewModel.currentFocusedSerie?.seriescat?.target?.seriesaccount?.target
            if (account != null) {
                if (account.isStalker) {
                    stalkerViewModel.seriesCache[helpViewModel.currentFocusedSerie!!.idByAccountData] =
                        Pair(
                            helpViewModel.focusedSeasons ?: mutableListOf(),
                            helpViewModel.focusedEpisodes ?: mutableListOf()
                        )
                } else {
                    xtreamViewModel.seriesCache[helpViewModel.currentFocusedSerie!!.idByAccountData] =
                        Pair(
                            helpViewModel.focusedSeasons ?: mutableListOf(),
                            helpViewModel.focusedEpisodes ?: mutableListOf()
                        )
                }
                helpViewModel.currentFocusedSeason?.let {
                    seasonBox.put(it)
                }
                val episodes = helpViewModel.focusedEpisodes?.filter { it.seasonNumber == helpViewModel.currentFocusedSeason?.seasonNumber }
                episodes?.let {
                    episodeBox.put(it)
                }
                helpViewModel.currentFocusedSerie?.let {
                    it.seriesAccount.target = helpViewModel.currentSeriesAccount
                    it.seriescat.target = helpViewModel.currentSeriesCategoryOB
                    seriesBox.put(it)
                }
            }
        }
    }

    fun showDetailUi(serie: SeriesOB) {
        binding.tvSeriestitle.text = serie.seriesName
        if (helpViewModel.isSearchContainerOpened) {
                getSeriesImage()
        }

        binding.tvCategories.text = serie.genres_str?.ifEmpty {
                ""
            } ?: ""
        binding.smallRating.rating = if (!serie.rating_imdb.isNullOrEmpty()) {
            val formattedRating = formatRating(serie.rating_imdb)
            val ratingValue = formattedRating.toFloatOrNull() ?: 0.0f
            (ratingValue / 2.0f)
        } else {
            0.0f
        }
        binding.smallRating.visibility = View.VISIBLE
        binding.tvRating.text = formatRating(serie.rating_imdb).ifEmpty {
            "0.0"
        }
        binding.tvRating.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = serie.seriesPercentagePlayed.toInt() ?: 0

        binding.tvAge.text = if (!serie.age.isNullOrEmpty()) {
            binding.tvAge.visibility = View.VISIBLE
            serie.age
        } else {
            binding.tvAge.visibility = View.GONE
            ""
        }
        binding.tvReleaseyear.text = if (!serie.seriesYear.isNullOrEmpty()) {
            binding.tvReleaseyear.visibility = View.VISIBLE
            val year = if (serie.seriesYear.length >= 4) serie.seriesYear.substring(0, 4) else "n/a"
            year
        } else {
            binding.tvReleaseyear.visibility = View.VISIBLE
            "n/a"
        }
        if (serie.isFavorite) {
            binding.btnaddFavorite.isSelected = true
            binding.btnaddFavorite.text = "Remove from Watchlist"
        } else {
            binding.btnaddFavorite.isSelected = false
            binding.btnaddFavorite.text = "Add to Watchlist"
        }
        if (serie.isPartlyWatched) {
            binding.btnaddWatched.isSelected = false
        } else if (serie.isCompletelyWatched) {
            binding.btnaddWatched.isSelected = true
            binding.btnaddWatched.text = "Mark as unwatched"
        } else {
            binding.btnaddWatched.isSelected = false
            binding.btnaddWatched.text = "Mark as watched"
        }

        if (serie.isPartlyWatched) {
            binding.tvRemainingTime.visibility = View.VISIBLE

        } else if (serie.isCompletelyWatched) {
            binding.tvRemainingTime.visibility = View.INVISIBLE
            binding.tvRemainingTime.text = "Completed!"
            binding.progressBar.progress = 100
        } else {
            binding.tvRemainingTime.visibility = View.INVISIBLE
        }
        binding.relLayoutSeriesSettings.visibility = View.VISIBLE
    }

    fun updateFavorite() {
        if (helpViewModel.currentFocusedSerie != null) {
            binding.ivFavorite.visibility = if (helpViewModel.currentFocusedSerie!!.isFavorite) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
        }
    }

    fun focusToEpisodes() {
        if (episodesAdapter.currentList.isNotEmpty() && helpViewModel.currentFocusedSeason != null) {
            binding.seasonVisibilityView.visibility = View.VISIBLE
            binding.rvLayoutSeriesEpisodes.requestFocus()
        }
    }

    fun focusToPreviousSeason() {
        if (seasonsAdapter.currentList.isNotEmpty()) {
            if (helpViewModel.currentFocusedSeason == helpViewModel.focusedSeasons?.first()) {
                focusToPlayButton()
            } else {
                val previousSeason = helpViewModel.focusedSeasons?.lastOrNull { it.seasonNumber < helpViewModel.currentFocusedSeason?.seasonNumber.toString() }
                if (previousSeason != null) {
                    val lastSeasonPos = seasonsAdapter.currentList.indexOf(helpViewModel.currentFocusedSeason)
                    val newSeasonPos = seasonsAdapter.currentList.indexOf(previousSeason)
                    seasonsAdapter.notifyItemChanged(lastSeasonPos)
                    seasonsAdapter.notifyItemChanged(newSeasonPos)
                    binding.rvLayoutSeriesSeasons.setSelectedPosition(newSeasonPos)
                    showEpisodesForSeason(previousSeason)
                }
            }
        }
    }

    fun focusToNextSeason() {
        if (seasonsAdapter.currentList.isNotEmpty()) {
            if (helpViewModel.currentFocusedSeason == helpViewModel.focusedSeasons?.last()) {
                return
            } else {
                val nextSeason = helpViewModel.focusedSeasons?.firstOrNull { it.seasonNumber > helpViewModel.currentFocusedSeason?.seasonNumber.toString() }
                if (nextSeason != null) {
                    val lastSeasonPos = seasonsAdapter.currentList.indexOf(helpViewModel.currentFocusedSeason)
                    val newSeasonPos = seasonsAdapter.currentList.indexOf(nextSeason)
                    seasonsAdapter.notifyItemChanged(lastSeasonPos)
                    seasonsAdapter.notifyItemChanged(newSeasonPos)
                    binding.rvLayoutSeriesSeasons.setSelectedPosition(newSeasonPos)
                    showEpisodesForSeason(nextSeason)
                }
            }
        }
    }

    fun setOnlyNewEpisode() {
        val position = episodesAdapter.currentList.indexOf(helpViewModel.currentFocusedEpisode)
        binding.rvLayoutSeriesEpisodes.setSelectedPosition(position)
        if (!helpViewModel.serieFullScreenOpened) {
            binding.rvLayoutSeriesEpisodes.requestFocus()
            binding.fullscreenSerie.visibility = View.GONE
        }
    }

    var changedSeason = false

    fun setNewSeasonAndEpisode(oldSeasonIndex: Int) {
        if (seasonsAdapter.currentList.isNotEmpty()) {
            val newSeasonPos = seasonsAdapter.currentList.indexOf( helpViewModel.currentFocusedSeason)
            seasonsAdapter.notifyItemChanged(oldSeasonIndex )
            seasonsAdapter.notifyItemChanged(newSeasonPos)
            binding.rvLayoutSeriesSeasons.setSelectedPosition(newSeasonPos)
            changedSeason = true
            helpViewModel.currentFocusedSeason?.let { showEpisodesForSeason(it) }
        }
    }


    fun focusToSeasons() {
        if (seasonsAdapter.currentList.isNotEmpty()) {
            binding.seasonVisibilityView.visibility = View.INVISIBLE
            binding.rvLayoutSeriesSeasons.requestFocus()
        }
    }

    fun focusToPlayButton() {
        binding.btnPlay.requestFocus()
    }

    fun closeFragment() {
        seriesViewModel.requestFocusToSeries()
        parentFragmentManager.popBackStack()
    }


    var currentTmdbSeriesDetailJob: Job? = null

    private fun getSeriesImage() {
        currentTmdbSeriesDetailJob?.cancel()
        val settings = helpViewModel.settings
        if (settings != null && helpViewModel.currentFocusedSerie != null) {
            currentTmdbSeriesDetailJob = viewLifecycleOwner.lifecycleScope.launch {
                if (helpViewModel.currentFocusedSerie?.backdropPath.isNullOrEmpty()) {
                    if (!helpViewModel.currentFocusedSerie?.tmdb_id.isNullOrEmpty() && settings.tmdbApiKey.isNotEmpty()) {
                        if (helpViewModel.currentFocusedSerie!!.tmdb_id?.startsWith("tt") == true) {
                            val tmdbSeriesDetailsByImdbId =
                                helpViewModel.getTmdbMovieDetailsByImdb(
                                    url = "https://api.themoviedb.org/3/find/",
                                    imdbId = helpViewModel.currentFocusedSerie?.tmdb_id!!,
                                    apiKey = settings.tmdbApiKey
                                ).await()
                            when (tmdbSeriesDetailsByImdbId) {
                                is Resource.Success -> {
                                    val backgroundImage =
                                        tmdbSeriesDetailsByImdbId.data?.movie_results?.first()?.backdrop_path.let { "https://image.tmdb.org/t/p/original$it" }
                                    binding.ivSeriesposter.visibility = View.VISIBLE
                                    Log.d("SERIENDETAILSACHE","IMDB: Bild: $backgroundImage")
                                    binding.ivSeriesposter.load(backgroundImage)
                                    helpViewModel.currentSeriesImage = backgroundImage
                                }

                                is Resource.Error -> {
                                    val seriesPoster = helpViewModel.currentFocusedSerie?.screenshot_uri
                                    if (!seriesPoster.isNullOrEmpty()) {
                                        binding.ivSeriesposter.visibility = View.VISIBLE
                                        Log.d("SERIENDETAILSACHE","IMDB: ERROR: $seriesPoster")
                                        binding.ivSeriesposter.load(seriesPoster)
                                        helpViewModel.currentSeriesImage = seriesPoster
                                    } else {
                                        Log.d("SERIENDETAILSACHE","IMDB: ERROR: NIX")
                                        binding.ivSeriesposter.visibility = View.INVISIBLE
                                    }
                                }
                            }
                        } else {
                            val tmdbSerieDetails =
                                helpViewModel.currentFocusedSerie?.tmdb_id?.toIntOrNull()?.let {
                                    helpViewModel.getTmdbSeriesDetails(
                                        url = "https://api.themoviedb.org/3/tv/",
                                        seriesId = it,
                                        apiKey = settings.tmdbApiKey
                                    ).await()
                                }
                            when (tmdbSerieDetails) {
                                is Resource.Success -> {
                                    if (helpViewModel.currentFocusedSerie?.backdropPath.isNullOrEmpty()) {
                                        val backgroundImage =
                                            tmdbSerieDetails.data?.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
                                        binding.ivSeriesposter.visibility = View.VISIBLE
                                        Log.d("SERIENDETAILSACHE","TMDB: Bild: $backgroundImage")

                                        binding.ivSeriesposter.load(backgroundImage)
                                        helpViewModel.currentFocusedSerie?.backdropPath = backgroundImage ?: ""
                                        helpViewModel.currentSeriesImage = backgroundImage
                                    } else {
                                        val seriesPoster = helpViewModel.currentFocusedSerie?.screenshot_uri
                                        if (!seriesPoster.isNullOrEmpty()) {
                                            binding.ivSeriesposter.visibility = View.VISIBLE
                                            Log.d("SERIENDETAILSACHE","TMDB: Ok aber leer: $seriesPoster")
                                            binding.ivSeriesposter.load(seriesPoster)
                                            helpViewModel.currentSeriesImage = seriesPoster
                                        } else {
                                            Log.d("SERIENDETAILSACHE","TMDB: Ok: NIX")
                                            binding.ivSeriesposter.visibility = View.INVISIBLE
                                        }
                                    }
                                }

                                is Resource.Error -> {
                                    val seriesPoster = helpViewModel.currentFocusedSerie?.screenshot_uri
                                    if (!seriesPoster.isNullOrEmpty()) {
                                        binding.ivSeriesposter.visibility = View.VISIBLE
                                        Log.d("SERIENDETAILSACHE","TMDB: ERROR: $seriesPoster")
                                        binding.ivSeriesposter.load(seriesPoster)
                                        helpViewModel.currentSeriesImage = seriesPoster
                                    } else {
                                        Log.d("SERIENDETAILSACHE","TMDB: ERROR: NIX")
                                        binding.ivSeriesposter.visibility = View.INVISIBLE
                                    }
                                }
                                null -> return@launch
                            }
                        }
                    } else {
                        if (!helpViewModel.currentFocusedSerie?.screenshot_uri.isNullOrEmpty()) {
                            binding.ivSeriesposter.visibility = View.VISIBLE
                            Log.d("SERIENDETAILSACHE","KEIN TMDB: ApiBild")
                            binding.ivSeriesposter.load(helpViewModel.currentFocusedSerie?.screenshot_uri)
                            helpViewModel.currentSeriesImage = helpViewModel.currentFocusedSerie?.screenshot_uri
                        } else {
                            Log.d("SERIENDETAILSACHE","KEIN TMDB Kein ApiBild")
                            binding.ivSeriesposter.visibility = View.INVISIBLE
                        }
                    }
                } else {
                    binding.ivSeriesposter.visibility = View.VISIBLE
                    Log.d("SERIENDETAILSACHE","HAT SCHON BACKGROUNDBILD")
                    binding.ivSeriesposter.load(helpViewModel.currentFocusedSerie?.backdropPath)
                    helpViewModel.currentSeriesImage = helpViewModel.currentFocusedSerie?.backdropPath
                }
            }
        }
    }

    fun showEpisodesForSeason(season: SeasonsOB) {
        if (season.seriesSeasonIdByAccountData == helpViewModel.currentFocusedSeason?.seriesSeasonIdByAccountData && !changedSeason) return
        val oldPosition = seasonsAdapter.currentList.indexOf(helpViewModel.currentFocusedSeason)
        val newPosition = seasonsAdapter.currentList.indexOf(season)
        helpViewModel.currentFocusedSeason = helpViewModel.focusedSeasons?.firstOrNull { it.seriesSeasonIdByAccountData == season.seriesSeasonIdByAccountData }
        seasonsAdapter.notifyItemChanged(oldPosition)
        seasonsAdapter.notifyItemChanged(newPosition)
        viewLifecycleOwner.lifecycleScope.launch {
            val settings = helpViewModel.settings
            var tmdbseasoninfo: TMDBSeasonDetails? = null

            // API-Call nur ausführen, wenn alle Bedingungen erfüllt sind
            if (settings != null && settings.tmdbApiKey.isNotEmpty() && helpViewModel.currentFocusedSerie != null && helpViewModel.currentFocusedSerie?.tmdb_id?.isNotEmpty() == true) {
                try {
                    val tmdbId = helpViewModel.currentFocusedSerie?.tmdb_id?.toIntOrNull() ?: return@launch
                    val seasonInfo = withContext(Dispatchers.IO) {
                        helpViewModel.getTmdbSeasonDetails(
                            url = "https://api.themoviedb.org/3/tv/",
                            seriesId = tmdbId,
                            seasonNumber = season.seasonNumber.toInt(),
                            apiKey = settings.tmdbApiKey
                        ).await()
                    }
                    if (seasonInfo != null) {
                        tmdbseasoninfo = seasonInfo
                        val imageUrl = "https://image.tmdb.org/t/p/original${tmdbseasoninfo.poster_path}"
                        binding.ivSeriesposter.load(imageUrl)
                    } else {
                        val imageUrl = helpViewModel.currentSeriesImage ?: helpViewModel.currentFocusedSerie?.screenshot_uri
                        if (imageUrl != null) {
                            binding.ivSeriesposter.load(imageUrl)
                        }
                        Log.e("TMDB ERROR", "API-Call erfolgreich, aber keine Staffel-Daten erhalten.")
                    }
                } catch (e: Exception) {
                    Log.e("TMDB ERROR", "Fehler beim Abrufen der TMDB-Daten: ${e.message}")
                    val imageUrl = helpViewModel.currentSeriesImage ?: helpViewModel.currentFocusedSerie?.screenshot_uri
                    if (imageUrl != null) {
                        binding.ivSeriesposter.load(imageUrl)
                    }
                }
            } else {
                val imageUrl = helpViewModel.currentSeriesImage ?: helpViewModel.currentFocusedSerie?.screenshot_uri
                if (imageUrl != null) {
                    binding.ivSeriesposter.load(imageUrl)
                }
            }

            // Lade Episoden aus der jeweiligen Quelle (Xtream oder Stalker)

            // Falls API-Daten vorhanden sind, aktualisiere die Episoden mit TMDB-Bildern
            if (tmdbseasoninfo != null) {
                helpViewModel.focusedEpisodes?.filter { it.seasonNumber == season.seasonNumber }?.toMutableList()?.forEach { episode ->
                    val thisEpisode = tmdbseasoninfo.episodes.firstOrNull { it.episode_number == episode.episodeNumber }
                    val tmdbEpisodeImage = thisEpisode?.still_path?.let { "https://image.tmdb.org/t/p/original$it" }
                    val tmdbEpisodeName = thisEpisode?.name
                    val tmdbEpisodeTime = thisEpisode?.runtime?.toString() ?: episode.episodeTime // Nur überschreiben, wenn nicht null
                    val tmdbEpisodeDescr = thisEpisode?.overview
                    episode.episodeImg = tmdbEpisodeImage ?: episode.episodeImg
                    episode.episodeName = tmdbEpisodeName ?: episode.episodeName
                    episode.episodeTime = tmdbEpisodeTime
                    episode.episodeDescription = tmdbEpisodeDescr ?: episode.episodeDescription
                }
            }
            // Aktualisiere die Episodenliste im Adapter

            if (!helpViewModel.focusedEpisodes?.filter { it.seasonNumber == season.seasonNumber }?.toMutableList().isNullOrEmpty()) {
                episodesAdapter.submitList(helpViewModel.focusedEpisodes?.filter { it.seasonNumber == season.seasonNumber }
                    ?.toMutableList())
            } else {
                binding.rvLayoutSeriesSeasons.requestFocus()
            }
            // Setze die letzte geschaut Episode falls vorhanden
            binding.rvLayoutSeriesEpisodes.post {
                if (firstEpisodeOpen) {
                    if (season.seasonNumber.toIntOrNull() == helpViewModel.currentFocusedSerie?.lastWatchedSeason) {
                        val thisEpisode =
                            episodesAdapter.currentList.firstOrNull { it.episodeNumber == helpViewModel.currentFocusedSerie?.lastWatchedEpisode }
                        val thisEpisodePosition = episodesAdapter.currentList.indexOf(thisEpisode)
                        binding.rvLayoutSeriesEpisodes.setSelectedPosition(thisEpisodePosition)
                        val position = seasonsAdapter.currentList.indexOf(helpViewModel.currentFocusedSeason)
                        seasonsAdapter.notifyItemChanged(position)
                        thisEpisode?.let {
                            showFocusedEpisodeInfos(it)
                        }
                        binding.rvLayoutSeriesEpisodes.requestFocus()
                    } else {
                        binding.rvLayoutSeriesEpisodes.setSelectedPosition(0)
                        val position = seasonsAdapter.currentList.indexOf(helpViewModel.currentFocusedSeason)
                        seasonsAdapter.notifyItemChanged(position)
                        val thisEpisode = helpViewModel.focusedEpisodes?.firstOrNull { it.seasonNumber?.toIntOrNull() == 1 && it.episodeNumber == 1 }
                        thisEpisode?.let {
                            showFocusedEpisodeInfos(it)
                        }

                        binding.rvLayoutSeriesEpisodes.requestFocus()
                    }
                    firstEpisodeOpen = false
                }
                if (changedSeason) {
                    val position = episodesAdapter.currentList.indexOf(helpViewModel.currentFocusedEpisode)
                    binding.rvLayoutSeriesEpisodes.setSelectedPosition(position)
                    changedSeason = false
                    if (!helpViewModel.serieFullScreenOpened) {
                        binding.rvLayoutSeriesEpisodes.requestFocus()
                        binding.fullscreenSerie.visibility = View.GONE
                    }
                }
            }
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

    private fun formatDuration(duration: Int): String {
        return if (duration < 60) {
            "$duration min"
        } else {
            val hours = duration / 60
            val minutes = duration % 60
            "${hours}h ${minutes}min"
        }
    }

    fun showFocusedEpisodeInfos(episode: EpisodesOB) {
        if (helpViewModel.currentFocusedEpisode != episode || seriesViewModel.changeEpisodeInfoUi) {
            seriesViewModel.changeEpisodeInfoUi = false
            helpViewModel.currentFocusedEpisode = helpViewModel.focusedEpisodes?.firstOrNull { it.seriesSeasonEpisodeIdByAccountData == episode.seriesSeasonEpisodeIdByAccountData }
            binding.tvEpisodedescription.text = episode.episodeDescription?.ifEmpty {
                "No description available"
            }
            binding.tvSeasontext.text = "${episode.seasonName} | Episode ${episode.episodeNumber}"
            binding.tvEpisodetext.text = "${episode.episodeName}"
            val seasonText = if (episode.seasonNumber == "0") {
                ""
            } else {
                "${episode.seasonNumber}"
            }
            binding.btnPlay.text = if (
                helpViewModel.currentFocusedSerie?.lastWatchedSeason == helpViewModel.currentFocusedSeason?.seasonNumber?.toIntOrNull() &&
                helpViewModel.currentFocusedSerie?.lastWatchedEpisode == episode.episodeNumber
            ) {
                if (episode.isEpisodePartlyWatched) {
                    "Continue S${seasonText} E${episode.episodeNumber}"
                } else if (episode.isEpisodeFullyWatched) {
                    "Rewatch S${seasonText} E${episode.episodeNumber}"
                } else {
                    "Play S${seasonText} E${episode.episodeNumber}"
                }
            } else {
                "Play S${seasonText} E${episode.episodeNumber}"
            }
        }
    }

    fun playSerie(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.fullscreen_serie, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
        helpViewModel.serieFullScreenOpened = true
        binding.focusBlocker.requestFocus()
        binding.fullscreenSerie.visibility = View.VISIBLE
    }

    fun setFocusToNextEpisodeAndSeason() {
        if (selectedSeason != helpViewModel.currentFocusedSeason) {
            val oldIndex = seasonsAdapter.currentList.indexOf(selectedSeason)
            setNewSeasonAndEpisode(oldIndex)
            helpViewModel.currentFocusedSeason?.let {
                showEpisodesForSeason(it)
            }
        } else {
            setOnlyNewEpisode()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}