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

        prepareSeasonsRecyclerView()

        prepareEpisodesRecyclerView()

        binding.constSeries.requestFocus()

        if (helpViewModel.currentFocusedSerie != null) {
            currentAccount = helpViewModel.currentFocusedSerie?.accountId?.let { accountBox.get(it) }
            if (currentAccount != null) {
                if (currentAccount!!.isXtream) {
                    if (!helpViewModel.focusedSeasons.isNullOrEmpty()) {
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
                focusToEpisodes()
                return@setOnKeyListener true
            }
            false
        }

        binding.btnPlay.setOnClickListener {
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
                focusToEpisodes()
                return@setOnKeyListener true
            }
            false
        }

        binding.btnaddFavorite.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
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
                focusToEpisodes()
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
                focusToEpisodes()
                return@setOnKeyListener true
            }
            false
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

    fun updateSeasonAndEpisodes() {
        helpViewModel.focusedSeasons?.let {
            seasonsAdapter.submitList(null)
            seasonsAdapter.submitList(it)
            binding.rvLayoutSeriesSeasons.post {
                val position = seasonsAdapter.currentList.indexOf(helpViewModel.currentFocusedSeason)
                binding.rvLayoutSeriesSeasons.setSelectedPosition(position)
            }
        }
        helpViewModel.focusedEpisodes?.let {
            episodesAdapter.submitList(null)
            episodesAdapter.submitList(helpViewModel.focusedEpisodes?.filter { it.seasonNumber == helpViewModel.currentFocusedSeason?.seasonNumber }?.toMutableList())
            binding.rvLayoutSeriesEpisodes.post {
                val position = episodesAdapter.currentList.indexOf(helpViewModel.currentFocusedEpisode)
                Log.d("LASTFOCUSEDEPISODE", "${helpViewModel.currentFocusedEpisode?.episodeNumber} FROM SEASON: ${helpViewModel.currentFocusedEpisode?.seasonNumber} = $position")
                binding.rvLayoutSeriesEpisodes.setSelectedPosition(position)
                binding.rvLayoutSeriesEpisodes.requestFocus()
            }
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
            val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (mainFragment is SeriesFragment) {
                if (helpViewModel.currentFocusedSerie != null) {
                    mainFragment.updateSingleSerie(helpViewModel.currentFocusedSerie!!)
                }
            }
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
                    markSeasonAsSeen(season, position)
                    true
                }
                R.id.mark_unseen -> {
                    wasItemClicked = true
                    resetEpisodes(season, position)
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

    private fun markSeasonAsSeen(season: SeasonsOB, position: Int) {
        val allEpisodes = helpViewModel.focusedEpisodes ?: return
        val allSeasons = helpViewModel.focusedSeasons ?: return

        // Markiere aktuelle Season und Episoden als gesehen
        season.isSeasonFullyWatched = true
        season.isSeasonPartlyWatched = false
        season.seasonPercentagePlayed = 1.0

        allEpisodes.filter { it.seasonNumber == season.seasonNumber }.forEach {
            it.isEpisodeFullyWatched = true
            it.isEpisodePartlyWatched = false
            it.episodePercentagePlayed = 1.0
            it.currentPosition = 0
        }

        episodesAdapter.notifyDataSetChanged()
        seasonsAdapter.notifyItemChanged(position)
        binding.rvLayoutSeriesSeasons.requestFocus()

        val serie = helpViewModel.currentFocusedSerie ?: return
        val currentSeasonNumber = season.seasonNumber.toIntOrNull() ?: return
        val isLastSeason = allSeasons.maxOfOrNull { it.seasonNumber.toIntOrNull() ?: 0 } == currentSeasonNumber
        val hasUnseenPreviousSeasons = allSeasons.any {
            val num = it.seasonNumber.toIntOrNull() ?: 0
            num < currentSeasonNumber && !it.isSeasonFullyWatched
        }

        if (isLastSeason && hasUnseenPreviousSeasons) {
            showConfirmMarkAllAsSeenDialog {
                // Markiere ALLES als gesehen
                allSeasons.forEach { s ->
                    s.isSeasonFullyWatched = true
                    s.isSeasonPartlyWatched = false
                    s.seasonPercentagePlayed = 1.0
                }
                allEpisodes.forEach { e ->
                    e.isEpisodeFullyWatched = true
                    e.isEpisodePartlyWatched = false
                    e.episodePercentagePlayed = 1.0
                    e.currentPosition = 0
                }

                serie.seriesPercentagePlayed = 1.0
                serie.isCompletelyWatched = true
                serie.isPartlyWatched = false
                serie.currentPosition = 0
                serie.lastWatchedSeason = allSeasons.firstOrNull()?.seasonNumber?.toIntOrNull() ?: 0
                serie.lastWatchedEpisode = allEpisodes.firstOrNull()?.episodeNumber ?: 0

                episodesAdapter.notifyDataSetChanged()
                seasonsAdapter.notifyDataSetChanged()

                saveSeriesChangesInDBAndCache()
                updateSerieStatus()
            }
            return // Abbrechen, denn das Handling wird im Dialog gemacht
        }

        if (allSeasons.all { it.isSeasonFullyWatched }) {
            // ALLE Staffeln gesehen
            serie.seriesPercentagePlayed = 1.0
            serie.isCompletelyWatched = true
            serie.isPartlyWatched = false
            serie.currentPosition = 0

            serie.lastWatchedSeason = allSeasons.firstOrNull()?.seasonNumber?.toIntOrNull() ?: 0
            serie.lastWatchedEpisode = allEpisodes.firstOrNull()?.episodeNumber ?: 0
        } else {
            // Nur diese Staffel gesehen → Suche nächste Staffel
            serie.isPartlyWatched = true
            serie.seriesPercentagePlayed = calculateSeriesPercentagePlayed(allEpisodes)
            val currentSeasonNumber = season.seasonNumber.toIntOrNull() ?: return
            val nextSeason = allSeasons
                .filter { !it.isSeasonFullyWatched }
                .firstOrNull { (it.seasonNumber.toIntOrNull() ?: 0) > currentSeasonNumber }

            nextSeason?.let { next ->
                val nextSeasonNumber = next.seasonNumber.toIntOrNull() ?: return@let
                val firstEpisode = allEpisodes
                    .filter { it.seasonNumber == next.seasonNumber }
                    .minByOrNull { it.episodeNumber }

                if (firstEpisode != null) {
                    serie.lastWatchedSeason = nextSeasonNumber
                    serie.lastWatchedEpisode = firstEpisode.episodeNumber
                }
            }
        }

        saveSeriesChangesInDBAndCache()
        updateSerieStatus()
    }

    private fun showConfirmMarkAllAsSeenDialog(onConfirm: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setMessage("Mark all unseen seasons as seen?")
            .setPositiveButton("Yes") { _, _ -> onConfirm() }
            .setNegativeButton("No", null)
            .show()
    }


    private fun resetEpisodes(season: SeasonsOB, position: Int) {
        season.isSeasonFullyWatched = false
        season.isSeasonPartlyWatched = false
        season.seasonPercentagePlayed = 0.0
        helpViewModel.focusedEpisodes?.filter { it.seasonNumber == helpViewModel.currentFocusedSeason?.seasonNumber }?.forEach {
            it.isEpisodeFullyWatched = false
            it.isEpisodePartlyWatched = false
            it.episodePercentagePlayed = 0.0
            it.currentPosition = 0
        }
        episodesAdapter.notifyDataSetChanged()
        seasonsAdapter.notifyItemChanged(position)
        binding.rvLayoutSeriesSeasons.requestFocus()
        if (helpViewModel.focusedSeasons?.all { it.isSeasonFullyWatched } == true) {
            helpViewModel.currentFocusedSerie?.seriesPercentagePlayed = 1.0
            helpViewModel.currentFocusedSerie?.isCompletelyWatched = true
            helpViewModel.currentFocusedSerie?.isPartlyWatched = false
            helpViewModel.currentFocusedSerie?.currentPosition = 0
            helpViewModel.currentFocusedSerie?.lastWatchedSeason = helpViewModel.currentFocusedSeason?.seasonNumber?.toIntOrNull() ?: 0
            helpViewModel.currentFocusedSerie?.lastWatchedEpisode =
                helpViewModel.focusedEpisodes?.filter { it.seasonNumber == helpViewModel.currentFocusedSeason?.seasonNumber }
                    ?.minByOrNull { it.episodeNumber }?.episodeNumber ?: 0
        } else {
            helpViewModel.focusedEpisodes?.let { episodes ->
                if (episodes.none { it.isEpisodeFullyWatched || it.isEpisodePartlyWatched }) {
                    val firstSeason = helpViewModel.focusedSeasons?.minByOrNull {
                        it.seasonNumber.toIntOrNull() ?: 0
                    }
                    helpViewModel.currentFocusedSerie?.lastWatchedSeason = firstSeason?.seasonNumber?.toIntOrNull() ?: 0
                    helpViewModel.currentFocusedSerie?.lastWatchedEpisode =
                        helpViewModel.focusedEpisodes?.filter {
                            it.seasonNumber == firstSeason?.seasonNumber
                        }?.minByOrNull { it.episodeNumber }?.episodeNumber ?: 0

                } else {
                    val lastFullWatchedSeason =
                        helpViewModel.focusedSeasons?.filter { it.isSeasonFullyWatched }
                            ?.minByOrNull { it.seasonNumber }
                    if (lastFullWatchedSeason != helpViewModel.currentFocusedSeason) {
                        val nextSeason = helpViewModel.focusedSeasons?.filter {
                            (it.seasonNumber.toIntOrNull()
                                ?: 0) > (helpViewModel.currentFocusedSeason?.seasonNumber?.toIntOrNull()
                                ?: 0)
                        }?.minByOrNull { it.seasonNumber }
                        if (nextSeason != null) {
                            helpViewModel.currentFocusedSerie?.lastWatchedSeason = nextSeason.seasonNumber.toIntOrNull() ?: 0
                            helpViewModel.currentFocusedSerie?.lastWatchedEpisode =
                                helpViewModel.focusedEpisodes?.filter { it.seasonNumber == nextSeason.seasonNumber }
                                    ?.minByOrNull { it.episodeNumber }?.episodeNumber ?: 0
                        } else {
                            helpViewModel.currentFocusedSerie?.lastWatchedSeason = 0
                            helpViewModel.currentFocusedSerie?.lastWatchedEpisode = 0
                        }
                    } else {
                        helpViewModel.currentFocusedSerie?.seriesPercentagePlayed =
                            calculateSeriesPercentagePlayed(episodes)
                        helpViewModel.currentFocusedSerie?.isCompletelyWatched = false
                        helpViewModel.currentFocusedSerie?.isPartlyWatched =
                            if (helpViewModel.focusedSeasons?.any { it.isSeasonFullyWatched || it.isSeasonPartlyWatched } == true) {
                                true
                            } else {
                                false
                            }
                        helpViewModel.currentFocusedSerie?.lastWatchedSeason =
                            helpViewModel.currentFocusedSeason?.seasonNumber?.toIntOrNull() ?: 0
                        helpViewModel.currentFocusedSerie?.lastWatchedEpisode =
                            episodes.filter { it.seasonNumber == helpViewModel.currentFocusedSeason?.seasonNumber }
                                .minByOrNull { it.episodeNumber }?.episodeNumber ?: 0
                    }
                }
            }
        }
        saveSeriesChangesInDBAndCache()
        updateSerieStatus()
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
                    markEpisodeAsSeen(episode, position)
                    true
                }
                R.id.mark_unseen -> {
                    markEpisodeAsUnseen(episode, position)
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
                        markEpisodeAsUnseen(episode, position)
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


    private fun markEpisodeAsSeen(episode: EpisodesOB, position: Int) {
        helpViewModel.viewModelScope.launch(Dispatchers.IO) {
            val currentSeason = helpViewModel.currentFocusedSeason ?: return@launch
            val allEpisodes = helpViewModel.focusedEpisodes ?: return@launch
            val allSeasons = helpViewModel.focusedSeasons ?: return@launch
            val serie = helpViewModel.currentFocusedSerie ?: return@launch

            // Aktuelle Episode als gesehen markieren
            episode.isEpisodeFullyWatched = true
            episode.isEpisodePartlyWatched = false
            episode.episodePercentagePlayed = 1.0
            episode.currentPosition = 0L
            episodeBox.put(episode)

            val episodesFromCurrentSeason = allEpisodes.filter { it.seasonNumber == currentSeason.seasonNumber }

            // Staffel abschließen, falls alle Episoden gesehen
            if (episodesFromCurrentSeason.all { it.isEpisodeFullyWatched }) {
                currentSeason.isSeasonFullyWatched = true
                currentSeason.isSeasonPartlyWatched = false
                currentSeason.seasonPercentagePlayed = 1.0
                seasonBox.put(currentSeason)

                withContext(Dispatchers.Main) {
                    val seasonPosition = seasonsAdapter.currentList.indexOf(currentSeason)
                    seasonsAdapter.notifyItemChanged(seasonPosition)
                }
            } else {
                // Staffel nur teilweise gesehen
                currentSeason.isSeasonFullyWatched = false
                currentSeason.isSeasonPartlyWatched = true
                currentSeason.seasonPercentagePlayed = calculateSeasonPercentagePlayed(currentSeason.seasonNumber)
                seasonBox.put(currentSeason)

                withContext(Dispatchers.Main) {
                    val seasonPosition = seasonsAdapter.currentList.indexOf(currentSeason)
                    seasonsAdapter.notifyItemChanged(seasonPosition)
                }
            }

            withContext(Dispatchers.Main) {
                episodesAdapter.notifyItemChanged(position)
            }

            // Nächste Episode bestimmen
            val sortedEpisodes = allEpisodes.sortedWith(
                compareBy({ it.seasonNumber?.toIntOrNull() ?: 0 }, { it.episodeNumber })
            )

            val currentIndex = sortedEpisodes.indexOfFirst {
                it.seasonNumber == episode.seasonNumber && it.episodeNumber == episode.episodeNumber
            }

            val nextEpisode = sortedEpisodes.getOrNull(currentIndex + 1)

            if (nextEpisode != null) {
                // → Noch Episoden vorhanden
                serie.lastWatchedSeason = nextEpisode.seasonNumber?.toIntOrNull() ?: 0
                serie.lastWatchedEpisode = nextEpisode.episodeNumber
                serie.isPartlyWatched = true
            } else {
                // → Alles gesehen
                serie.isCompletelyWatched = true
                serie.isPartlyWatched = false
                serie.seriesPercentagePlayed = 1.0
                serie.currentPosition = 0
            }

            saveSeriesChangesInDBAndCache()
            withContext(Dispatchers.Main) {
                updateSerieStatus()
            }
        }
    }


    private fun markEpisodeAsUnseen(episode: EpisodesOB, position: Int) {
        helpViewModel.viewModelScope.launch(Dispatchers.IO) {
            val currentSeason = helpViewModel.currentFocusedSeason ?: return@launch

            // 1. Episode zurücksetzen
            episode.isEpisodeFullyWatched = false
            episode.isEpisodePartlyWatched = false
            episode.episodePercentagePlayed = 0.0
            episode.currentPosition = 0L
            episodeBox.put(episode)
            withContext(Dispatchers.Main) {
                episodesAdapter.notifyItemChanged(position)
            }
            // 2. Season-Episoden filtern
            val seasonEpisodes = helpViewModel.focusedEpisodes
                ?.filter { it.seasonNumber == currentSeason.seasonNumber } ?: return@launch

            // 3. Prüfen wie viele Episoden noch gesehen sind
            val hasFullyWatched = seasonEpisodes.any { it.isEpisodeFullyWatched }
            val hasPartlyWatched = seasonEpisodes.any { it.isEpisodePartlyWatched }

            if (!hasFullyWatched && !hasPartlyWatched) {
                // Staffel komplett ungesehen
                currentSeason.isSeasonFullyWatched = false
                currentSeason.isSeasonPartlyWatched = false
                currentSeason.seasonPercentagePlayed = 0.0
            } else {
                // Staffel teilweise gesehen
                currentSeason.isSeasonFullyWatched = false
                currentSeason.isSeasonPartlyWatched = true
                currentSeason.seasonPercentagePlayed = calculateSeasonPercentagePlayed(currentSeason.seasonNumber)
            }

            // 5. Season speichern & UI updaten
            seasonBox.put(currentSeason)
            val seasonPosition = seasonsAdapter.currentList.indexOf(currentSeason)
            withContext(Dispatchers.Main) {
                seasonsAdapter.notifyItemChanged(seasonPosition)
            }
            // 6. Nächste "lastWatchedEpisode" setzen (vorherige Episode suchen)
            val allEpisodes = helpViewModel.focusedEpisodes ?: return@launch

            val sortedEpisodes = allEpisodes.sortedWith(
                compareBy({ it.seasonNumber?.toIntOrNull() ?: 0 }, { it.episodeNumber })
            )

            val currentIndex = sortedEpisodes.indexOfFirst {
                it.seasonNumber == episode.seasonNumber &&
                        it.episodeNumber == episode.episodeNumber
            }

            val previousEpisode = sortedEpisodes.getOrNull(currentIndex - 1)

            if (previousEpisode != null && previousEpisode.isEpisodeFullyWatched) {
                helpViewModel.currentFocusedSerie?.apply {
                    lastWatchedSeason = previousEpisode.seasonNumber?.toIntOrNull() ?: 0
                    lastWatchedEpisode = previousEpisode.episodeNumber
                    isPartlyWatched = true
                    isCompletelyWatched = false
                }
            } else {
                helpViewModel.currentFocusedSerie?.apply {
                    // Nichts mehr vorher gesehen → zurücksetzen
                    lastWatchedSeason = 0
                    lastWatchedEpisode = 0
                    isPartlyWatched = false
                    isCompletelyWatched = false
                    seriesPercentagePlayed = 0.0
                    currentPosition = 0
                }
            }

            updateSerieStatus()
            if (playEpisodeAfterCalculation) {
                playEpisodeAfterCalculation = false
                playSerie(PlaySeriesFragment())
            }
        }
    }


    fun calculateSeriesPercentagePlayed(episodes: List<EpisodesOB>): Double {
        if (episodes.isEmpty()) return 0.0

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

    private fun updateSerieStatus() {
        val allEpisodes = helpViewModel.focusedEpisodes ?: return
        val currentSerie = helpViewModel.currentFocusedSerie ?: return

        val totalEpisodes = allEpisodes.size
        val watchedEpisodes = allEpisodes.count { it.isEpisodeFullyWatched }

        when {
            watchedEpisodes == 0 -> {
                currentSerie.isCompletelyWatched = false
                currentSerie.isPartlyWatched = false
                currentSerie.seriesPercentagePlayed = 0.0
            }
            watchedEpisodes == totalEpisodes -> {
                currentSerie.isCompletelyWatched = true
                currentSerie.isPartlyWatched = false
                currentSerie.seriesPercentagePlayed = 1.0
            }
            else -> {
                currentSerie.isCompletelyWatched = false
                currentSerie.isPartlyWatched = true
                currentSerie.seriesPercentagePlayed = calculateSeriesPercentagePlayed(allEpisodes)
            }
        }

        seriesBox.put(currentSerie) // Speichern
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
        val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (containerFragment is SeriesFragment) {
            containerFragment.setFullVisibility()
        }
        helpViewModel.currentSeriesImage = null
        helpViewModel.currentFocusedSeason = null
        helpViewModel.currentFocusedEpisode = null
        seasonsAdapter.submitList(null)
        episodesAdapter.submitList(null)
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
        if (helpViewModel.currentFocusedEpisode != episode) {
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
        binding.fullscreenSerie.requestFocus()
        binding.fullscreenSerie.visibility = View.VISIBLE
    }

    fun closeFullScreenSerie() {
        binding.fullscreenSerie.visibility = View.GONE
        helpViewModel.serieFullScreenOpened = false
        updateSeasonAndEpisodes()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}