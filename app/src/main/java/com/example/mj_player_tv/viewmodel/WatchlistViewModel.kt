package com.example.mj_player_tv.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.mj_player_tv.MyApplication
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.MovieOB_
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.database.entity.SeriesOB_
import com.example.mj_player_tv.database.help.WatchlistItem
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.collections.component1
import kotlin.collections.component2

class WatchlistViewModel(application: Application) : AndroidViewModel(application) {

    private val movieBox: Box<MovieOB> = ObjectBox.store.boxFor(MovieOB::class.java)
    private val seriesBox: Box<SeriesOB> = ObjectBox.store.boxFor(SeriesOB::class.java)

    private val programmeBox = ObjectBox.store.boxFor(Programme::class.java)

    private val _watchlistResults = MutableStateFlow<List<WatchlistItem>>(emptyList())
    val watchlistResults: StateFlow<List<WatchlistItem>> = _watchlistResults.asStateFlow()

    fun resetWatchlistData() {
        _watchlistResults.value = emptyList()
        _watchlistSearching.value = false
        hasFetchedWatchlist = false
    }

    var hasFetchedWatchlist = false

    private val _watchlistSearching = MutableStateFlow(false)
    val watchlistSearching: StateFlow<Boolean> = _watchlistSearching.asStateFlow()

    private var watchlistJob: Job? = null

    fun cancelWatchlistJob() {
        watchlistJob?.cancel()
        _watchlistSearching.value = false
    }

    fun fetchWatchListData() {
        watchlistJob?.cancel()

        watchlistJob = viewModelScope.launch(Dispatchers.IO) {
            _watchlistSearching.value = true

            val movieJob = async { getWatchlistMovies() }
            val seriesJob = async { getWatchlistSeries() }
            val programmesJob = async { getWatchlistProgrammes() }

            val movieItems = movieJob.await()
            val seriesItems = seriesJob.await()
            val programmeItems = programmesJob.await()
            programmeItems.forEach { item ->
                if (item is WatchlistItem.Programs) {
                    item.programs.forEach { program ->
                        val epg = program.epgData.target
                        Log.d("WATCHLIST PROGRAMME", "Program for ${program.tvchannels.target.tvchannel.target.account.target.name }}: ${program.tvchannels.target.tvchannel.target.showingName} START: ${program.startTimeStamp} END: ${program.stopTimeStamp}")
                    }
                }
            }

            // Alles kombinieren und nur EINMAL setzen
            _watchlistResults.value = movieItems + seriesItems + programmeItems

            _watchlistSearching.value = false
            hasFetchedWatchlist = true
        }
    }

    fun removeMovieFromWatchlist(movie: MovieOB) {
        val currentList = _watchlistResults.value.toMutableList()

        val updatedList = currentList.mapNotNull { item ->
            when (item) {
                is WatchlistItem.Movies -> {
                    if (item.movies.contains(movie)) {
                        val newMovies = item.movies - movie
                        if (newMovies.isEmpty()) {
                            null // Account hat keine Filme mehr → entferne ihn komplett
                        } else {
                            WatchlistItem.Movies(item.account, newMovies)
                        }
                    } else item
                }

                else -> item
            }
        }
        _watchlistResults.value = updatedList
    }

    fun removeSerieFromWatchlist(serie: SeriesOB) {
        val currentList = _watchlistResults.value.toMutableList()

        val updatedList = currentList.mapNotNull { item ->
            when (item) {
                is WatchlistItem.Series -> {
                    if (item.series.contains(serie)) {
                        val newSeries = item.series - serie
                        if (newSeries.isEmpty()) {
                            null // Account hat keine Serien mehr → entferne ihn komplett
                        } else {
                            WatchlistItem.Series(item.account, newSeries)
                        }
                    } else item
                }
                else -> item
            }
        }
        _watchlistResults.value = updatedList
    }

    fun removeProgrammeFromWatchlist(program: Programme) {
        val currentList = _watchlistResults.value.toMutableList()

        val updatedList = currentList.mapNotNull { item ->
            when (item) {
                is WatchlistItem.Programs -> {
                    if (item.programs.contains(program)) {
                        val newPrograms = item.programs - program
                        if (newPrograms.isEmpty()) {
                            null // Account hat keine Serien mehr → entferne ihn komplett
                        } else {
                            WatchlistItem.Programs(item.account, newPrograms)
                        }
                    } else item
                }
                else -> item
            }
        }
        _watchlistResults.value = updatedList
    }

    fun getWatchlistMovies(): List<WatchlistItem> {
        val movies = movieBox.query(MovieOB_.isFavorite.equal(true)).build().use { it.find() }
        if (movies.isEmpty()) return emptyList()

        return movies
            .filter { it.movieAccount.target != null }
            .groupBy { it.movieAccount.target }
            .map { (account, list) -> WatchlistItem.Movies(account, list) }
    }

    fun getWatchlistSeries(): List<WatchlistItem> {
        val series = seriesBox.query(SeriesOB_.isFavorite.equal(true)).build().use { it.find() }
        if (series.isEmpty()) return emptyList()

        return series
            .filter { it.seriesAccount.target != null }
            .groupBy { it.seriesAccount.target }
            .map { (account, list) -> WatchlistItem.Series(account, list) }
    }

    fun getWatchlistProgrammes(): List<WatchlistItem> {
        val programmes = programmeBox.all
        if (programmes.isEmpty()) return emptyList()

        return programmes
            .filter { it.tvchannels.target.tvchannel.target.account.target != null }
            .groupBy { it.tvchannels.target.tvchannel.target.account.target }
            .map { (account, list) -> WatchlistItem.Programs(account, list) }
    }

}
