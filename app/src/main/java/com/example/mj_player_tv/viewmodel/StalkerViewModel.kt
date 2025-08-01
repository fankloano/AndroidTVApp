package com.example.mj_player_tv.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.Accounts_
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.EpgSourceChannel
import com.example.mj_player_tv.database.entity.EpgSourceChannel_
import com.example.mj_player_tv.database.entity.EpgSourcePositions
import com.example.mj_player_tv.database.entity.EpgSourcePositions_
import com.example.mj_player_tv.database.entity.EpgSource_
import com.example.mj_player_tv.database.entity.EpisodesOB
import com.example.mj_player_tv.database.entity.EpisodesOB_
import com.example.mj_player_tv.database.entity.MovieCategoryOB
import com.example.mj_player_tv.database.entity.MovieCategoryOB_
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.MovieOB_
import com.example.mj_player_tv.database.entity.PortalEpgAndDate
import com.example.mj_player_tv.database.entity.SeasonsOB
import com.example.mj_player_tv.database.entity.SeasonsOB_
import com.example.mj_player_tv.database.entity.SeriesCategoryOB
import com.example.mj_player_tv.database.entity.SeriesCategoryOB_
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.database.entity.SeriesOB_
import com.example.mj_player_tv.database.entity.Settings
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.help.Episode
import com.example.mj_player_tv.database.help.Movie
import com.example.mj_player_tv.database.help.Season
import com.example.mj_player_tv.database.help.Serie
import com.example.mj_player_tv.network.RetrofitInstance
import com.example.mj_player_tv.network.model.stalker.epgforday.SimpleTableData
import com.example.mj_player_tv.network.model.stalker.movies.MovieData
import com.example.mj_player_tv.network.model.stalker.seriesdetails.SeriesData
import com.example.mj_player_tv.repository.MoviePagingSource
import com.example.mj_player_tv.repository.PlaylistLoadProcessState
import com.example.mj_player_tv.repository.PlaylistUpdateProcessState
import com.example.mj_player_tv.repository.PlaylistUpdateWorker
import com.example.mj_player_tv.repository.SeriePagingSource
import com.example.mj_player_tv.repository.StalkerRepository
import com.example.mj_player_tv.utils.Resource
import io.objectbox.Box
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class StalkerViewModel(application: Application): AndroidViewModel(application) {

    private val stalkerRepository = StalkerRepository()

    private val retrofitInstance = RetrofitInstance

    val accountBox = ObjectBox.store.boxFor(Accounts::class.java)
    val tvCategoryBox = ObjectBox.store.boxFor(TvCategoryOB::class.java)
    val movieCategoryBox = ObjectBox.store.boxFor(MovieCategoryOB::class.java)
    val seriesCategoryBox = ObjectBox.store.boxFor(SeriesCategoryOB::class.java)
    val tvChannelBox = ObjectBox.store.boxFor(TvChannelOB::class.java)
    val movieBox = ObjectBox.store.boxFor(MovieOB::class.java)
    val epgSourceBox = ObjectBox.store.boxFor(EpgSource::class.java)
    val epgChannelBox = ObjectBox.store.boxFor(EpgSourceChannel::class.java)
    val epgDataBox = ObjectBox.store.boxFor(EpgDataOB::class.java)
    val portalEpgAndDateBox = ObjectBox.store.boxFor(PortalEpgAndDate::class.java)
    val manualPositionBox = ObjectBox.store.boxFor(ChannelPositions::class.java)
    val epgPositionBox = ObjectBox.store.boxFor(EpgSourcePositions::class.java)
    val settingsBox = ObjectBox.store.boxFor(Settings::class.java)
    val seriesBox = ObjectBox.store.boxFor(SeriesOB::class.java)
    val seasonsBox = ObjectBox.store.boxFor(SeasonsOB::class.java)
    val episodesBox = ObjectBox.store.boxFor(EpisodesOB::class.java)

    var xxxChannelsCount = 0
    var normalChannelsCount = 0

    var newPlaylistAccountId: Int? = null

    private val _playlistProcessState = MutableStateFlow<PlaylistLoadProcessState?>(null)
    val playlistProcessState: StateFlow<PlaylistLoadProcessState?> = _playlistProcessState


    fun resetPlaylistProcessState() {
        _playlistProcessState.value = null
    }

    fun createSequentialList(start: Int, end: Int): List<Int> {
        return (start..end).toList()
    }

    var isPlaylistEnableChanged: Boolean = false
    var currentProcessName = ""

    suspend fun getStalkerData(url: String, macAddress: String, name: String, userAgent: String) {
        viewModelScope.launch {
            currentProcessName = name
            _playlistProcessState.value = PlaylistLoadProcessState.Loading
            val tokenResponse = getToken(url, macAddress, userAgent, name).await()
            when (tokenResponse) {
                is Resource.Success -> {
                    if (!tokenResponse.data.isNullOrEmpty()) {
                        val profilResponse =
                            getProfile(url, macAddress, userAgent, tokenResponse.data).await()
                        when (profilResponse) {
                            is Resource.Success -> {
                                val mainInfoResponse = if (!profilResponse.data.isNullOrEmpty()) {
                                    getMainInfo(
                                        url,
                                        macAddress,
                                        userAgent,
                                        tokenResponse.data,
                                        profilResponse.data,
                                        name
                                    ).await()
                                } else {
                                    getMainInfo(
                                        url,
                                        macAddress,
                                        userAgent,
                                        tokenResponse.data,
                                        "Europe/Amsterdam",
                                        name
                                    ).await()
                                }
                                when (mainInfoResponse) {
                                    is Resource.Success -> {
                                        _playlistProcessState.value =
                                            PlaylistLoadProcessState.GetToken(100)
                                        val tvDeferred =
                                            getTvCategories(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                profilResponse.data ?: "Europe/Amsterdam"
                                            ).await()
                                        val channelsDeferred =
                                            getTvChannels(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                profilResponse.data ?: "Europe/Amsterdam"
                                            ).await()
                                        val movieDeferred =
                                            getMovieCategories(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                profilResponse.data ?: "Europe/Amsterdam"
                                            ).await()
                                        val seriesDeferred =
                                            getSeriesCategories(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                profilResponse.data ?: "Europe/Amsterdam"
                                            ).await()
                                        if (tvDeferred.isEmpty() && movieDeferred
                                                .isEmpty() && seriesDeferred.isEmpty() && channelsDeferred.isEmpty()
                                        ) {
                                            _playlistProcessState.value =
                                                PlaylistLoadProcessState.Error("No Playlist Data found")
                                            val accountData =
                                                accountBox.query(Accounts_.name.equal(name)).build()
                                                    .findFirst()
                                            if (accountData != null) {
                                                val epgSource = epgSourceBox.query(
                                                    EpgSource_.playlistId.equal(accountData.id)
                                                ).build().findFirst()
                                                if (epgSource != null) {
                                                    val epgPosition = epgPositionBox.query(
                                                        EpgSourcePositions_.accountEpgSourceUnique.equal(
                                                            "${accountData.id}_${epgSource.url}"
                                                        )
                                                    ).build().findFirst()
                                                    if (epgPosition != null) {
                                                        epgPositionBox.remove(epgPosition)
                                                    }
                                                    epgSourceBox.remove(epgSource.id)
                                                }
                                                accountBox.remove(accountData.id)
                                                newPlaylistAccountId = null
                                            }
                                        } else {
                                            _playlistProcessState.value =
                                                PlaylistLoadProcessState.GetChannels(
                                                    100,
                                                    ((xxxChannelsCount + normalChannelsCount).toString())
                                                )
                                            val accountData =
                                                accountBox.query(Accounts_.name.equal(name)).build()
                                                    .findFirst()
                                            if (accountData != null) {
                                                if (tvDeferred.isEmpty()) {
                                                    accountData.tvchannelLoadingOK = 0
                                                } else {
                                                    accountData.tvchannelLoadingOK = 1
                                                }
                                                if (movieDeferred.isEmpty()) {
                                                    accountData.movieCategoryLoadingOK = 0
                                                } else {
                                                    accountData.movieCategoryLoadingOK = 1
                                                }
                                                if (seriesDeferred.isEmpty()) {
                                                    accountData.seriesCategoryLoadingOK = 0
                                                } else {
                                                    accountData.seriesCategoryLoadingOK = 1
                                                }
                                                if (channelsDeferred.isEmpty()) {
                                                    accountData.tvchannelLoadingOK = 0
                                                } else {
                                                    accountData.tvchannelLoadingOK = 1
                                                }
                                                if (channelsDeferred.isEmpty() && seriesDeferred.isEmpty() && movieDeferred.isEmpty() && tvDeferred.isEmpty()) {
                                                    accountData.lastUpdateStatus = 0
                                                } else {
                                                    if (channelsDeferred.isEmpty() || seriesDeferred.isEmpty() || movieDeferred.isEmpty() || tvDeferred.isEmpty()) {
                                                        accountData.lastUpdateStatus = 2
                                                    } else {
                                                        accountData.lastUpdateStatus = 1
                                                    }
                                                }
                                                addChannelsToTvCategory(accountData.id)
                                                addExternalEpgToAccount(accountData)
                                                val currentDate = System.currentTimeMillis() / 1000
                                                accountData.lastUpdatedDate = currentDate
                                                accountBox.put(accountData)
                                                setWorker(accountData)
                                                _playlistProcessState.value =
                                                    PlaylistLoadProcessState.Success(accountData)
                                            }
                                        }
                                    }

                                    is Resource.Error -> {
                                        _playlistProcessState.value =
                                            PlaylistLoadProcessState.GetToken(100)
                                        val tvDeferred =
                                            getTvCategories(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                profilResponse.data ?: "Europe/Amsterdam"
                                            )
                                        val movieDeferred =
                                            getMovieCategories(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                profilResponse.data ?: "Europe/Amsterdam"
                                            )
                                        val seriesDeferred =
                                            getSeriesCategories(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                profilResponse.data ?: "Europe/Amsterdam"
                                            )
                                        val channelsDeferred =
                                            getTvChannels(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                profilResponse.data ?: "Europe/Amsterdam"
                                            )
                                        awaitAll(
                                            tvDeferred,
                                            channelsDeferred,
                                            movieDeferred,
                                            seriesDeferred
                                        )
                                        if (tvDeferred.await().isEmpty() && movieDeferred.await()
                                                .isEmpty() && seriesDeferred.await().isEmpty() && channelsDeferred.await().isEmpty()
                                        ) {
                                            _playlistProcessState.value =
                                                PlaylistLoadProcessState.Error("No Playlist Data found")
                                            val accountData =
                                                accountBox.query(Accounts_.name.equal(name)).build()
                                                    .findFirst()
                                            if (accountData != null) {
                                                val epgSource = epgSourceBox.query(
                                                    EpgSource_.playlistId.equal(accountData.id)
                                                ).build().findFirst()
                                                if (epgSource != null) {
                                                    val epgPosition = epgPositionBox.query(
                                                        EpgSourcePositions_.accountEpgSourceUnique.equal(
                                                            "${accountData.id}_${epgSource.url}"
                                                        )
                                                    ).build().findFirst()
                                                    if (epgPosition != null) {
                                                        epgPositionBox.remove(epgPosition)
                                                    }
                                                    epgSourceBox.remove(epgSource.id)
                                                }
                                                accountBox.remove(accountData.id)
                                                newPlaylistAccountId = null
                                            }
                                        } else {
                                            val accountData =
                                                accountBox.query(Accounts_.name.equal(name)).build()
                                                    .findFirst()
                                            if (accountData != null) {
                                                if (tvDeferred.await().isEmpty()) {
                                                    accountData.tvchannelLoadingOK = 0
                                                } else {
                                                    accountData.tvchannelLoadingOK = 1
                                                }
                                                if (movieDeferred.await().isEmpty()) {
                                                    accountData.movieCategoryLoadingOK = 0
                                                } else {
                                                    accountData.movieCategoryLoadingOK = 1
                                                }
                                                if (seriesDeferred.await().isEmpty()) {
                                                    accountData.seriesCategoryLoadingOK = 0
                                                } else {
                                                    accountData.seriesCategoryLoadingOK = 1
                                                }
                                                if (channelsDeferred.await().isEmpty()) {
                                                    accountData.tvchannelLoadingOK = 0
                                                } else {
                                                    accountData.tvchannelLoadingOK = 1
                                                }
                                                if (channelsDeferred.await().isEmpty() && seriesDeferred.await().isEmpty() && movieDeferred.await().isEmpty() && tvDeferred.await().isEmpty()) {
                                                    accountData.lastUpdateStatus = 0
                                                } else {
                                                    if (channelsDeferred.await().isEmpty() || seriesDeferred.await().isEmpty() || movieDeferred.await().isEmpty() || tvDeferred.await().isEmpty()) {
                                                        accountData.lastUpdateStatus = 2
                                                    } else {
                                                        accountData.lastUpdateStatus = 1
                                                    }
                                                }
                                                _playlistProcessState.value =
                                                    PlaylistLoadProcessState.GetChannels(
                                                        100,
                                                        ((xxxChannelsCount + normalChannelsCount).toString())
                                                    )

                                                addChannelsToTvCategory(accountData.id)
                                                addExternalEpgToAccount(accountData)
                                                val currentDate = System.currentTimeMillis() / 1000
                                                accountData.lastUpdatedDate = currentDate
                                                accountBox.put(accountData)
                                                setWorker(accountData)
                                                _playlistProcessState.value =
                                                    PlaylistLoadProcessState.Success(accountData)
                                            }
                                        }
                                    }
                                }
                            }

                            is Resource.Error -> {
                                val mainInfoResponse =
                                    getMainInfo(
                                        url,
                                        macAddress,
                                        userAgent,
                                        tokenResponse.data,
                                        "Europe/Amsterdam",
                                        name
                                    ).await()
                                when (mainInfoResponse) {
                                    is Resource.Success -> {
                                        _playlistProcessState.value =
                                            PlaylistLoadProcessState.GetToken(100)
                                        val tvDeferred =
                                            getTvCategories(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                "Europe/Amsterdam"
                                            ).await()
                                        val channelsDeferred =
                                            getTvChannels(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                "Europe/Amsterdam"
                                            ).await()
                                        val movieDeferred =
                                            getMovieCategories(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                "Europe/Amsterdam"
                                            ).await()
                                        val seriesDeferred =
                                            getSeriesCategories(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                "Europe/Amsterdam"
                                            ).await()
                                        if (tvDeferred.isEmpty() && movieDeferred
                                                .isEmpty() && seriesDeferred.isEmpty() && channelsDeferred.isEmpty()
                                        ) {
                                            _playlistProcessState.value =
                                                PlaylistLoadProcessState.Error("No Playlist Data found")
                                            val accountData =
                                                accountBox.query(Accounts_.name.equal(name)).build()
                                                    .findFirst()
                                            if (accountData != null) {
                                                val epgSource = epgSourceBox.query(
                                                    EpgSource_.playlistId.equal(accountData.id)
                                                ).build().findFirst()
                                                if (epgSource != null) {
                                                    val epgPosition = epgPositionBox.query(
                                                        EpgSourcePositions_.accountEpgSourceUnique.equal(
                                                            "${accountData.id}_${epgSource.url}"
                                                        )
                                                    ).build().findFirst()
                                                    if (epgPosition != null) {
                                                        epgPositionBox.remove(epgPosition)
                                                    }
                                                    epgSourceBox.remove(epgSource.id)
                                                }
                                                accountBox.remove(accountData.id)
                                                newPlaylistAccountId = null
                                            }
                                        } else {
                                            _playlistProcessState.value =
                                                PlaylistLoadProcessState.GetChannels(
                                                    100,
                                                    ((xxxChannelsCount + normalChannelsCount).toString())
                                                )
                                            val accountData =
                                                accountBox.query(Accounts_.name.equal(name)).build()
                                                    .findFirst()
                                            if (accountData != null) {
                                                if (tvDeferred.isEmpty()) {
                                                    accountData.tvchannelLoadingOK = 0
                                                } else {
                                                    accountData.tvchannelLoadingOK = 1
                                                }
                                                if (movieDeferred.isEmpty()) {
                                                    accountData.movieCategoryLoadingOK = 0
                                                } else {
                                                    accountData.movieCategoryLoadingOK = 1
                                                }
                                                if (seriesDeferred.isEmpty()) {
                                                    accountData.seriesCategoryLoadingOK = 0
                                                } else {
                                                    accountData.seriesCategoryLoadingOK = 1
                                                }
                                                if (channelsDeferred.isEmpty()) {
                                                    accountData.tvchannelLoadingOK = 0
                                                } else {
                                                    accountData.tvchannelLoadingOK = 1
                                                }
                                                if (channelsDeferred.isEmpty() && seriesDeferred.isEmpty() && movieDeferred.isEmpty() && tvDeferred.isEmpty()) {
                                                    accountData.lastUpdateStatus = 0
                                                } else {
                                                    if (channelsDeferred.isEmpty() || seriesDeferred.isEmpty() || movieDeferred.isEmpty() || tvDeferred.isEmpty()) {
                                                        accountData.lastUpdateStatus = 2
                                                    } else {
                                                        accountData.lastUpdateStatus = 1
                                                    }
                                                }
                                                addChannelsToTvCategory(accountData.id)
                                                addExternalEpgToAccount(accountData)
                                                val currentDate = System.currentTimeMillis() / 1000
                                                accountData.lastUpdatedDate = currentDate
                                                accountBox.put(accountData)
                                                setWorker(accountData)
                                                _playlistProcessState.value =
                                                    PlaylistLoadProcessState.Success(accountData)
                                            }
                                        }
                                    }

                                    is Resource.Error -> {
                                        _playlistProcessState.value =
                                            PlaylistLoadProcessState.GetToken(100)
                                        val tvDeferred =
                                            getTvCategories(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                "Europe/Amsterdam"
                                            )
                                        val movieDeferred =
                                            getMovieCategories(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                "Europe/Amsterdam"
                                            )
                                        val seriesDeferred =
                                            getSeriesCategories(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                "Europe/Amsterdam"
                                            )
                                        val channelsDeferred =
                                            getTvChannels(
                                                url,
                                                macAddress,
                                                userAgent,
                                                name,
                                                tokenResponse.data,
                                                "Europe/Amsterdam"
                                            )
                                        awaitAll(
                                            tvDeferred,
                                            channelsDeferred,
                                            movieDeferred,
                                            seriesDeferred
                                        )
                                        if (tvDeferred.await().isEmpty() && movieDeferred.await()
                                                .isEmpty() && seriesDeferred.await().isEmpty() && channelsDeferred.await().isEmpty()
                                        ) {
                                            _playlistProcessState.value =
                                                PlaylistLoadProcessState.Error("No Playlist Data found")
                                            val accountData =
                                                accountBox.query(Accounts_.name.equal(name)).build()
                                                    .findFirst()
                                            if (accountData != null) {
                                                val epgSource = epgSourceBox.query(
                                                    EpgSource_.playlistId.equal(accountData.id)
                                                ).build().findFirst()
                                                if (epgSource != null) {
                                                    val epgPosition = epgPositionBox.query(
                                                        EpgSourcePositions_.accountEpgSourceUnique.equal(
                                                            "${accountData.id}_${epgSource.url}"
                                                        )
                                                    ).build().findFirst()
                                                    if (epgPosition != null) {
                                                        epgPositionBox.remove(epgPosition)
                                                    }
                                                    epgSourceBox.remove(epgSource.id)
                                                }
                                                accountBox.remove(accountData.id)
                                                newPlaylistAccountId = null
                                            }
                                        } else {
                                            val accountData =
                                                accountBox.query(Accounts_.name.equal(name)).build()
                                                    .findFirst()
                                            if (accountData != null) {
                                                if (tvDeferred.await().isEmpty()) {
                                                    accountData.tvchannelLoadingOK = 0
                                                } else {
                                                    accountData.tvchannelLoadingOK = 1
                                                }
                                                if (movieDeferred.await().isEmpty()) {
                                                    accountData.movieCategoryLoadingOK = 0
                                                } else {
                                                    accountData.movieCategoryLoadingOK = 1
                                                }
                                                if (seriesDeferred.await().isEmpty()) {
                                                    accountData.seriesCategoryLoadingOK = 0
                                                } else {
                                                    accountData.seriesCategoryLoadingOK = 1
                                                }
                                                if (channelsDeferred.await().isEmpty()) {
                                                    accountData.tvchannelLoadingOK = 0
                                                } else {
                                                    accountData.tvchannelLoadingOK = 1
                                                }
                                                if (channelsDeferred.await().isEmpty() && seriesDeferred.await().isEmpty() && movieDeferred.await().isEmpty() && tvDeferred.await().isEmpty()) {
                                                    accountData.lastUpdateStatus = 0
                                                } else {
                                                    if (channelsDeferred.await().isEmpty() || seriesDeferred.await().isEmpty() || movieDeferred.await().isEmpty() || tvDeferred.await().isEmpty()) {
                                                        accountData.lastUpdateStatus = 2
                                                    } else {
                                                        accountData.lastUpdateStatus = 1
                                                    }
                                                }
                                                _playlistProcessState.value =
                                                    PlaylistLoadProcessState.GetChannels(
                                                        100,
                                                        ((xxxChannelsCount + normalChannelsCount).toString())
                                                    )

                                                addChannelsToTvCategory(accountData.id)
                                                addExternalEpgToAccount(accountData)
                                                val currentDate = System.currentTimeMillis() / 1000
                                                accountData.lastUpdatedDate = currentDate
                                                accountBox.put(accountData)
                                                setWorker(accountData)
                                                _playlistProcessState.value =
                                                    PlaylistLoadProcessState.Success(accountData)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is Resource.Error -> {
                    _playlistProcessState.value =
                        PlaylistLoadProcessState.TokenError(tokenResponse.message)
                }
            }
        }
    }

    var currentUpdatingPlaylistId: Long? = null

    private val _playlistUpdateProcessState = MutableStateFlow<PlaylistUpdateProcessState?>(null)
    val playlistUpdateProcessState: StateFlow<PlaylistUpdateProcessState?> = _playlistUpdateProcessState

    fun resetPlaylistUpdateProcessState() {
        _playlistUpdateProcessState.value = null
    }

    suspend fun updateStalkerData(accountData: Accounts) {
        currentUpdatingPlaylistId = accountData.id
        _playlistUpdateProcessState.value = PlaylistUpdateProcessState.CurrentAccount(accountData.name)
        val tokenRespone = getToken(
            accountData.stalkerUrl,
            accountData.macAddress,
            accountData.userAgent,
            accountData.name
        ).await()
        when (tokenRespone) {
            is Resource.Success -> {
                if (!tokenRespone.data.isNullOrEmpty()) {
                    accountData.token = tokenRespone.data
                    accountBox.put(accountData)
                    val profilResponse =
                        getProfile(
                            accountData.stalkerUrl,
                            accountData.macAddress,
                            accountData.userAgent,
                            tokenRespone.data
                        ).await()
                    when (profilResponse) {
                        is Resource.Success -> {
                            val timeZone = profilResponse.data ?: accountData.timezone
                            accountData.timezone = timeZone
                            val mainInfoResponse =
                                getExpiryDate(
                                    accountData.stalkerUrl,
                                    accountData.macAddress,
                                    accountData.userAgent,
                                    tokenRespone.data,
                                    timeZone,
                                    accountData.name
                                ).await()
                            when (mainInfoResponse) {
                                is Resource.Success -> {
                                    accountData.expiryDate = mainInfoResponse.data.toString()
                                    viewModelScope.launch {
                                        val tvDeferred =
                                            updateTvCategories(
                                                accountData.stalkerUrl,
                                                accountData.macAddress,
                                                accountData.userAgent,
                                                accountData.name,
                                                tokenRespone.data,
                                                timeZone,
                                                accountData
                                            )
                                        val movieDeferred =
                                            updateMovieCategories(
                                                accountData.stalkerUrl,
                                                accountData.macAddress,
                                                accountData.userAgent,
                                                accountData.name,
                                                tokenRespone.data,
                                                timeZone,
                                                accountData
                                            )
                                        val seriesDeferred =
                                            updateSeriesCategories(
                                                accountData.stalkerUrl,
                                                accountData.macAddress,
                                                accountData.userAgent,
                                                accountData.name,
                                                tokenRespone.data,
                                                timeZone,
                                                accountData
                                            )
                                        awaitAll(
                                            tvDeferred,
                                            movieDeferred,
                                            seriesDeferred
                                        )
                                        val channelsDeferred =
                                            updateTvChannels(
                                                accountData.stalkerUrl,
                                                accountData.macAddress,
                                                accountData.userAgent,
                                                accountData.name,
                                                tokenRespone.data,
                                                timeZone,
                                                accountData
                                            ).await()
                                        Log.d("UPDATE STALKER", "${accountData.name} = ${channelsDeferred.size}")
                                        if (tvDeferred.await().isEmpty()) {
                                            accountData.tvchannelLoadingOK = 0
                                        } else {
                                            accountData.tvchannelLoadingOK = 1
                                        }
                                        if (movieDeferred.await().isEmpty()) {
                                            accountData.movieCategoryLoadingOK = 0
                                        } else {
                                            accountData.movieCategoryLoadingOK = 1
                                        }
                                        if (seriesDeferred.await().isEmpty()) {
                                            accountData.seriesCategoryLoadingOK = 0
                                        } else {
                                            accountData.seriesCategoryLoadingOK = 1
                                        }
                                        if (channelsDeferred.isEmpty()) {
                                            accountData.tvchannelLoadingOK = 0
                                        } else {
                                            accountData.tvchannelLoadingOK = 1
                                        }
                                        if (channelsDeferred.isEmpty() && seriesDeferred.await().isEmpty() && movieDeferred.await().isEmpty() && tvDeferred.await().isEmpty()) {
                                            accountData.lastUpdateStatus = 0
                                        } else {
                                            if (channelsDeferred.isEmpty() || seriesDeferred.await().isEmpty() || movieDeferred.await().isEmpty() || tvDeferred.await().isEmpty()) {
                                                accountData.lastUpdateStatus = 2
                                            } else {
                                                accountData.lastUpdateStatus = 1
                                            }
                                        }
                                        val currentDate = System.currentTimeMillis() / 1000
                                        accountData.lastUpdatedDate = currentDate
                                        accountBox.put(accountData)
                                        setWorker(accountData)
                                        _playlistUpdateProcessState.value = PlaylistUpdateProcessState.Success
                                    }
                                }
                                is Resource.Error -> {
                                    accountData.expiryDate = ""
                                    viewModelScope.launch {
                                        val tvDeferred =
                                            updateTvCategories(
                                                accountData.stalkerUrl,
                                                accountData.macAddress,
                                                accountData.userAgent,
                                                accountData.name,
                                                tokenRespone.data,
                                                timeZone,
                                                accountData
                                            )
                                        val movieDeferred =
                                            updateMovieCategories(
                                                accountData.stalkerUrl,
                                                accountData.macAddress,
                                                accountData.userAgent,
                                                accountData.name,
                                                tokenRespone.data,
                                                timeZone,
                                                accountData
                                            )
                                        val seriesDeferred =
                                            updateSeriesCategories(
                                                accountData.stalkerUrl,
                                                accountData.macAddress,
                                                accountData.userAgent,
                                                accountData.name,
                                                tokenRespone.data,
                                                timeZone,
                                                accountData
                                            )
                                        awaitAll(
                                            tvDeferred,
                                            movieDeferred,
                                            seriesDeferred
                                        )
                                        val channelsDeferred =
                                            updateTvChannels(
                                                accountData.stalkerUrl,
                                                accountData.macAddress,
                                                accountData.userAgent,
                                                accountData.name,
                                                tokenRespone.data,
                                                timeZone,
                                                accountData
                                            ).await()
                                        Log.d("UPDATE STALKER", "${accountData.name} = ${channelsDeferred.size}")

                                        if (tvDeferred.await().isEmpty()) {
                                            accountData.tvchannelLoadingOK = 0
                                        } else {
                                            accountData.tvchannelLoadingOK = 1
                                        }
                                        if (movieDeferred.await().isEmpty()) {
                                            accountData.movieCategoryLoadingOK = 0
                                        } else {
                                            accountData.movieCategoryLoadingOK = 1
                                        }
                                        if (seriesDeferred.await().isEmpty()) {
                                            accountData.seriesCategoryLoadingOK = 0
                                        } else {
                                            accountData.seriesCategoryLoadingOK = 1
                                        }
                                        if (channelsDeferred.isEmpty()) {
                                            accountData.tvchannelLoadingOK = 0
                                        } else {
                                            accountData.tvchannelLoadingOK = 1
                                        }
                                        if (channelsDeferred.isEmpty() && seriesDeferred.await().isEmpty() && movieDeferred.await().isEmpty() && tvDeferred.await().isEmpty()) {
                                            accountData.lastUpdateStatus = 0
                                        } else {
                                            if (channelsDeferred.isEmpty() || seriesDeferred.await().isEmpty() || movieDeferred.await().isEmpty() || tvDeferred.await().isEmpty()) {
                                                accountData.lastUpdateStatus = 2
                                            } else {
                                                accountData.lastUpdateStatus = 1
                                            }
                                        }
                                        val currentDate = System.currentTimeMillis() / 1000
                                        accountData.lastUpdatedDate = currentDate
                                        accountBox.put(accountData)
                                        setWorker(accountData)
                                        _playlistUpdateProcessState.value = PlaylistUpdateProcessState.Success
                                    }
                                }
                            }
                        }

                        is Resource.Error -> {
                            val currentDate = System.currentTimeMillis() / 1000
                            accountData.tvchannelLoadingOK = 0
                            accountData.tvCategoryLoadingOK = 0
                            accountData.movieCategoryLoadingOK = 0
                            accountData.seriesCategoryLoadingOK = 0
                            accountData.lastUpdatedDate = currentDate
                            accountData.lastUpdateStatus = 0
                            accountBox.put(accountData)
                            _playlistUpdateProcessState.value = PlaylistUpdateProcessState.Error
                        }
                    }
                } else {
                    val currentDate = System.currentTimeMillis() / 1000
                    accountData.tvchannelLoadingOK = 0
                    accountData.tvCategoryLoadingOK = 0
                    accountData.movieCategoryLoadingOK = 0
                    accountData.seriesCategoryLoadingOK = 0
                    accountData.lastUpdatedDate = currentDate
                    accountData.lastUpdateStatus = 0
                    accountBox.put(accountData)
                    _playlistUpdateProcessState.value = PlaylistUpdateProcessState.Error
                }
                currentUpdatingPlaylistId = null
            }

            is Resource.Error -> {
                val currentDate = System.currentTimeMillis() / 1000
                accountData.tvchannelLoadingOK = 0
                accountData.tvCategoryLoadingOK = 0
                accountData.movieCategoryLoadingOK = 0
                accountData.seriesCategoryLoadingOK = 0
                accountData.lastUpdatedDate = currentDate
                accountData.lastUpdateStatus = 0
                accountBox.put(accountData)
                _playlistUpdateProcessState.value = PlaylistUpdateProcessState.Error
            }
        }
    }

    suspend fun updateTokenAndProfile() {
        withContext(Dispatchers.IO) {
            val stalkerAccountsQuery = accountBox.query(
                Accounts_.isStalker.equal(true).and(Accounts_.isSelected.equal(true))
            ).build()
            val stalkerAccounts = stalkerAccountsQuery.find()
            stalkerAccountsQuery.close()
            if (stalkerAccounts.isEmpty()) {
                return@withContext
            } else {
                stalkerAccounts.map { account ->
                    Log.d("UPDATE STALKER ACCOUNTS", "NOW: ${account.name}")
                    async {
                        val tokenResult = getToken(
                            account.stalkerUrl,
                            account.macAddress,
                            account.userAgent,
                            account.name
                        ).await()

                        if (tokenResult is Resource.Success) {
                            account.token = tokenResult.data
                            Log.d("UPDATE STALKER ACCOUNTS", "NOW TOKEN: ${account.token}")
                            // Jetzt: getProfile mit neuem Token
                            val profileResult = getProfile(
                                account.stalkerUrl,
                                account.macAddress,
                                account.userAgent,
                                tokenResult.data ?: ""
                            ).await()

                            if (profileResult is Resource.Success) {
                                // Optional: verarbeite Profil-Daten, falls nötig
                                account.timezone = profileResult.data ?: ""
                                Log.d(
                                    "UPDATE STALKER ACCOUNTS",
                                    "NOW TIMEZONE: ${account.timezone}"
                                )
                                account.lastUpdateStatus = 1
                            }
                        } else {
                            // Token holen fehlgeschlagen – setze Fehlerstatus
                            account.tvchannelLoadingOK = 0
                            account.tvCategoryLoadingOK = 0
                            account.movieCategoryLoadingOK = 0
                            account.seriesCategoryLoadingOK = 0
                            account.lastUpdateStatus = 0
                        }

                        accountBox.put(account)
                    }
                }
            }
        }
    }

    private fun setWorker(account: Accounts) {
        val workManager = WorkManager.getInstance(this.getApplication<Application>().applicationContext)

        // Überprüfe, ob ein Worker für diese Playlist läuft oder geplant ist
        val workInfos = workManager.getWorkInfosByTag("autoupdate_${account.id}").get()
        val isRunningOrQueued = workInfos.any {
            it.state == WorkInfo.State.RUNNING
        }

        if (isRunningOrQueued) {
            Log.d("WORKER", "Ein Worker für Playlist ${account.name} läuft oder ist geplant. Kein neuer Worker wird hinzugefügt.")
            return // Kein neuer Worker wird geplant
        }

        // Berechne den Delay für das nächste Update
        val lastUpdateTimeMillis = account.lastUpdatedDate * 1000 // in Millisekunden
        val autoUpdateMillis = account.autoUpdateHours * 3600000 // in Millisekunden
        val delay = maxOf(0, (lastUpdateTimeMillis + autoUpdateMillis) - System.currentTimeMillis())

// Berechne das geplante Datum und die Uhrzeit
        val plannedExecutionTimeMillis = System.currentTimeMillis() + delay
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val plannedExecutionTime = dateFormat.format(Date(plannedExecutionTimeMillis))
        // Erstelle den Worker
        val workRequest = OneTimeWorkRequestBuilder<PlaylistUpdateWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("accountId" to account.id))
            .addTag("autoupdate_${account.id}")
            .build()

        // Plane den Worker
        workManager.enqueueUniqueWork(
            "autoupdate_${account.id}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        Log.d("WORKER", "Worker für Playlist ${account.name} geplant am: $plannedExecutionTime")
    }

    fun getToken(
        url: String,
        macAddress: String,
        userAgent: String,
        name: String
    ): Deferred<Resource<String?>> = viewModelScope.async {
        val response = stalkerRepository.getToken(
            url,
            "mac=${macAddress}; stb_lang=en; timezone=Europe/Amsterdam;",
            userAgent
        )
        when (response) {
            is Resource.Success -> {
                if (response.data.js.token?.isNotEmpty() == true) {
                    val token = response.data.js.token
                    Resource.Success(token)
                } else {
                    Log.d("EMPTY OR NULL TOKEN", "TOKEN: ${response.data.js}")
                    Resource.Success("")
                }
            }

            is Resource.Error -> {
                Log.d("TOKEN ERROR", response.message)
                Resource.Error(response.message)
            }
        }
    }

    fun getProfile(
        url: String,
        macAddress: String,
        userAgent: String,
        token: String
    ): Deferred<Resource<String?>> = viewModelScope.async {
        val response = stalkerRepository.getProfile(
            url,
            "mac=${macAddress}; stb_lang=en; timezone=Europe/Amsterdam;",
            "Bearer $token",
            userAgent
        )
        when (response) {
            is Resource.Success -> {
                val timeZone = if (response.data.js.default_timezone.isNotEmpty()) {
                    response.data.js.default_timezone
                } else {
                    "Europe/Amsterdam"
                }
                Resource.Success(timeZone)
            }

            is Resource.Error -> {
                Resource.Error(response.message)
            }
        }
    }

    fun getMainInfo(
        url: String,
        macAddress: String,
        userAgent: String,
        token: String,
        timeZone: String,
        name: String
    ): Deferred<Resource<String?>> = viewModelScope.async {
        val response = stalkerRepository.getMainInfo(
            url,
            "mac=${macAddress}; stb_lang=en; timezone=$timeZone;",
            "Bearer $token",
            userAgent
        )
        when (response) {
            is Resource.Success -> {
                val currentDate = System.currentTimeMillis() / 1000
                val expiryDate = response.data.js.phone ?: ""
                val newAccountData = Accounts(
                    0,
                    name,
                    url,
                    "",
                    macAddress,
                    token,
                    "$url$macAddress",
                    userAgent,
                    true,
                    expiryDate,
                    timeZone,
                    true,
                    false,
                    false,
                    true,
                    false,
                    false,
                    "",
                    "",
                    "",
                    "",
                    lastUpdatedDate = currentDate,
                    1,
                    1,
                    1,
                    1,
                    1,
                    true,
                    true,
                    72,
                    false,
                    0,
                    0,
                    false,
                    mutableListOf(),
                    true,
                    ""
                )
                isPlaylistEnableChanged = true

                // Den neuen Account in die Datenbank einfügen
                accountBox.put(newAccountData)

                // Erneut den neuen Account aus der Datenbank laden, um die generierte ID zu erhalten
                val thisAccount = accountBox[newAccountData.id]

                if (thisAccount != null) {
                    val epgSource = EpgSource(
                        0,
                        thisAccount.name,
                        thisAccount.id.toString(),
                        thisAccount.id,
                        false,
                        false,
                        true,
                        true,
                        false,
                        7,
                        3,
                        0,
                        0,
                        currentDate,
                        2,
                        currentDate,
                    )
                    // Die neue EpgSource in die Datenbank einfügen
                    epgSource.epgchs.addAll(emptyList())
                    epgSourceBox.put(epgSource)
                    val thisEpgSource = epgSourceBox.query(EpgSource_.playlistId.equal(thisAccount.id)).build().findFirst()
                    if (thisEpgSource != null) {
                        val newEpgPosition = EpgSourcePositions(
                            id = 0,
                            thisAccount.id,
                            thisEpgSource.id,
                            0,
                            true,
                            true,
                            "${thisAccount.id}"
                        )
                        newEpgPosition.relatedepgsource.target = epgSource
                        newEpgPosition.relatedaccount.target = newAccountData
                        epgPositionBox.put(newEpgPosition)
                        thisAccount.epgsources.add(newEpgPosition)
                    }
                    accountBox.put(thisAccount)
                }
                Resource.Success(expiryDate)
            }

            is Resource.Error -> {
                val currentDate = System.currentTimeMillis() / 1000
                val expiryDate = ""
                val newAccountData = Accounts(
                    0,
                    name,
                    url,
                    "",
                    macAddress,
                    token,
                    "$url$macAddress",
                    userAgent,
                    true,
                    expiryDate,
                    timeZone,
                    true,
                    false,
                    false,
                    true,
                    false,
                    false,
                    "",
                    "",
                    "",
                    "",
                    lastUpdatedDate = currentDate,
                    1,
                    1,
                    1,
                    1,
                    1,
                    true,
                    true,
                    72,
                    false,
                    0,
                    0,
                    false,
                    mutableListOf(),
                    true,
                    ""
                )
                isPlaylistEnableChanged = true

                // Den neuen Account in die Datenbank einfügen
                accountBox.put(newAccountData)

                // Erneut den neuen Account aus der Datenbank laden, um die generierte ID zu erhalten
                val thisAccount = accountBox[newAccountData.id]

                if (thisAccount != null) {
                    val currentDate = System.currentTimeMillis() / 1000
                    val epgSource = EpgSource(
                        0,
                        name,
                        thisAccount.id.toString(),
                        thisAccount.id,
                        false,
                        false,
                        true,
                        true,
                        false,
                        7,
                        3,
                        0,
                        0,
                        currentDate,
                        2,
                        currentDate,
                    )
                    // Die neue EpgSource in die Datenbank einfügen
                    epgSourceBox.put(epgSource)
                    val thisEpgSource = epgSourceBox.query(EpgSource_.playlistId.equal(thisAccount.id)).build().findFirst()
                    if (thisEpgSource != null) {
                        val newEpgPosition = EpgSourcePositions(
                            id = 0,
                            thisAccount.id,
                            thisEpgSource.id,
                            0,
                            true,
                            true,
                            "${thisAccount.id}_${thisEpgSource.url}"
                        )
                        newEpgPosition.relatedepgsource.target = epgSource
                        newEpgPosition.relatedaccount.target = thisAccount
                        epgPositionBox.put(newEpgPosition)
                        thisAccount.epgsources.add(newEpgPosition)
                    }
                    accountBox.put(thisAccount)
                    val acc = accountBox.get(thisAccount.id)
                    Log.d("NEWACCOUNTDATA", "$acc")

                }
                Resource.Error(expiryDate)
            }
        }
    }

    fun getExpiryDate(
        url: String,
        macAddress: String,
        userAgent: String,
        token: String,
        timeZone: String,
        name: String
    ): Deferred<Resource<String?>> = viewModelScope.async {
        val response = stalkerRepository.getMainInfo(
            url,
            "mac=${macAddress}; stb_lang=en; timezone=$timeZone;",
            "Bearer $token",
            userAgent
        )
        when (response) {
            is Resource.Success -> {
                val expiryDate = response.data.js.phone ?: ""
                // Den neuen Account in die Datenbank einfügen
                Resource.Success(expiryDate)
            }
            is Resource.Error -> {
                val expiryDate = ""
                Resource.Error(expiryDate)
            }
        }
    }


    fun getTvCategories(
        url: String,
        macAddress: String,
        userAgent: String,
        name: String,
        token: String,
        timeZone: String
    ): Deferred<List<TvCategoryOB>> = viewModelScope.async {
        _playlistProcessState.value = PlaylistLoadProcessState.GetTvCategories(
            1,
            "Load Tv Categories..",
        )
        val accountQuery = accountBox
            .query(Accounts_.totalAccountData.equal("$url$macAddress"))
            .build()
        val accountData = accountQuery.findFirst()
        if (accountData != null) {
            val response = stalkerRepository.getTvCategory(
                url,
                "mac=${macAddress}; stb_lang=de; timezone=$timeZone;",
                "Bearer $token",
                userAgent
            )
            when (response) {
                is Resource.Success -> {
                    val categories = response.data.js.map { tvcatresponse ->
                        val thisCategory = TvCategoryOB(
                            0,
                            accountData.id,
                            tvcatresponse.id,
                            tvcatresponse.number,
                            tvcatresponse.censored,
                            tvcatresponse.title,
                            tvcatresponse.title,
                            tvcatresponse.title,
                            accountData = name,
                            favorite = false,
                            0,
                            idByAccountData = "${tvcatresponse.id}_${accountData.id}",
                            false,
                            false,
                            null,
                            null,
                            false,
                            isAllChannelsCategory = if (tvcatresponse.id == "*") {
                                true
                            } else {
                                false
                            }
                        )
                        thisCategory.tvaccount.target = accountData
                        thisCategory
                    }
                    val settings = settingsBox.all.first()
                    if (!categories.isNullOrEmpty() && settings != null) {
                        val favoriteCategory = TvCategoryOB(
                            0,
                            accountData.id,
                            "FAVORITE_${accountData.id}",
                            null,
                            0,
                            "Favorites",
                            "Favorites",
                            "Favorites",
                            name,
                            false,
                            0,
                            "FAVORITE_${accountData.id}",
                            false,
                            false,
                            null,
                            null,
                            true,
                            false
                        )
                        favoriteCategory.tvaccount.target = accountData
                        tvCategoryBox.put(favoriteCategory)
                        val categoriesToAdd =
                            if (settings.tvcategoryPrefixes.isNotEmpty() || settings.tvcategorySuffixes.isNotEmpty()) {
                                val updatedTvCategoriesList =
                                    updatePrefixesAndSuffixesCategoriesForAddedPlaylist(
                                        settings.tvcategoryPrefixes,
                                        settings.tvcategorySuffixes,
                                        categories
                                    )
                                updatedTvCategoriesList
                            } else {
                                categories
                            }
                        accountData.totalMovieCategories = categoriesToAdd.size.toString()
                        accountBox.put(accountData)
                        tvCategoryBox.put(categoriesToAdd)
                        val censoredCategories = categoriesToAdd.filter { it.censored == 1 }
                        if (censoredCategories.isNotEmpty()) {
                            addCensoredChannelsToTvCategory(
                                censoredCategories,
                                accountData,
                                name,
                                settings
                            )
                        }
                        _playlistProcessState.value =
                            PlaylistLoadProcessState.GetTvCategories(
                                100,
                                categoriesToAdd.size.toString()
                            )
                        categoriesToAdd
                    } else {
                        emptyList()
                    }
                }

                is Resource.Error -> {
                    _playlistProcessState.value = PlaylistLoadProcessState.TvError(response.message)
                    emptyList()
                }
            }
        } else {
            emptyList()
        }
    }

    suspend fun addCensoredChannelsToTvCategory(
        censoredCategories: List<TvCategoryOB>,
        accountData: Accounts,
        name: String,
        settings: Settings
    ) {
// Setze die Startposition für die erste Seite auf 0
        var globalPosition = 0

        val thisEpgSource =
            epgSourceBox.query(EpgSource_.playlistId.equal(accountData.id)).build().findFirst()
        val xxxChannels: MutableList<TvChannelOB> = mutableListOf()

        if (thisEpgSource != null) {
            for (category in censoredCategories) {
                val channelsResponse = stalkerRepository.getOrderedTvChannels(
                    accountData.stalkerUrl,
                    "mac=${accountData.macAddress}; stb_lang=de; timezone=${accountData.timezone};",
                    "Bearer ${accountData.token}",
                    accountData.userAgent,
                    category.tvCatId,
                    1
                )

                when (channelsResponse) {
                    is Resource.Success -> {
                        if (channelsResponse.data.js.data.isNotEmpty()) {
                            val initialChannels = channelsResponse.data.js.data.map {
                                val thisChannel = TvChannelOB(
                                    0,
                                    accountData.id,
                                    it.id.toInt(),
                                    it.number,
                                    it.cmd ?: "",
                                    it.logo ?: "",
                                    "",
                                    it.tv_genre_id?.toInt(),
                                    "${it.tv_genre_id}_${accountData.id}",
                                    it.name,
                                    it.name,
                                    it.name,
                                    it.xmltv_id?.lowercase() ?: "",
                                    it.enable_tv_archive,
                                    it.tv_archive_duration,
                                    it.archive,
                                    name,
                                    "${it.id}_${accountData.id}",
                                    thisEpgSource?.id,
                                    true,
                                    false,
                                    false,
                                    0L,
                                    false,
                                    null,
                                    "${it.tv_genre_id}_${accountData.id}"
                                )
                                thisChannel.account.target = accountData
                                thisChannel.reltvcategory.target = category
                                thisChannel
                            }
                            xxxChannels.addAll(initialChannels)

                            val maxChannelsPage = channelsResponse.data.js.max_page_items
                            val totalChannels = channelsResponse.data.js.total_items
                            val totalPagesChannels =
                                ceil(totalChannels.toDouble() / maxChannelsPage).toInt()
                            val totalPagesForChannel = createSequentialList(2, totalPagesChannels)

                            for (page in totalPagesForChannel) {
                                val censoredChannels = stalkerRepository.getOrderedTvChannels(
                                    accountData.stalkerUrl,
                                    "mac=${accountData.macAddress}; stb_lang=de; timezone=${accountData.timezone};",
                                    "Bearer ${accountData.token}",
                                    accountData.userAgent,
                                    category.tvCatId,
                                    page
                                )

                                when (censoredChannels) {
                                    is Resource.Success -> {
                                        if (censoredChannels.data.js.data.isNotEmpty()) {
                                            val channels = censoredChannels.data.js.data.map {
                                                val thisChannel = TvChannelOB(
                                                    0,
                                                    accountData.id,
                                                    it.id.toInt(),
                                                    it.number,
                                                    it.cmd ?: "",
                                                    it.logo ?: "",
                                                    "",
                                                    it.tv_genre_id?.toInt(),
                                                    "${it.tv_genre_id}_${accountData.id}",
                                                    it.name,
                                                    it.name,
                                                    it.name,
                                                    it.xmltv_id?.lowercase() ?: "",
                                                    it.enable_tv_archive,
                                                    it.tv_archive_duration,
                                                    it.archive,
                                                    name,
                                                    "${it.id}_${accountData.id}",
                                                    thisEpgSource.id,
                                                    true,
                                                    false,
                                                    false,
                                                    0L,
                                                    false,
                                                    null,
                                                    "${it.tv_genre_id}_${accountData.id}"
                                                )
                                                thisChannel.account.target = accountData
                                                thisChannel.reltvcategory.target = category
                                                thisChannel
                                            }
                                            xxxChannels.addAll(channels)
                                        }
                                    }

                                    is Resource.Error -> {
                                        Log.d("XXX CHANNELS", "Keine mehr vorhanden")
                                    }
                                }
                            }
                        }

                        // Aktualisiere die Präfixe und Suffixe und speichere die Kanäle
                        val finalChannelList = updatePrefixesAndSuffixesForAddedPlaylist(
                            settings.prefixes,
                            settings.suffixes,
                            xxxChannels
                        )
                        tvChannelBox.put(finalChannelList)
                        tvCategoryBox.put(category)
                        xxxChannelsCount = finalChannelList.size
                    }

                    is Resource.Error -> {
                        Log.d("XXX CHANNELS", "Keine vorhanden")
                    }
                }
            }
        }
    }

    fun addNewChannelsToTvCategory(account: Accounts, newChannels: List<TvChannelOB>) {
        val thisaccount = accountBox.get(account.id)
        val uncensoredCategories = thisaccount.tvcategories.filter { it.censored == 0 }
        for (category in uncensoredCategories) {
            val thisChannels = newChannels.filter { it.relatedtvCategoryId == category.idByAccountData }.map {
                it.reltvcategory.target = category
                it
            }

            tvChannelBox.put(thisChannels)
            setPositionsForModifiedAndNewChannels(account, thisChannels, category)
        }
    }

    fun setPositionsForModifiedAndNewChannels(account: Accounts, channelsByCategory: List<TvChannelOB>, categoryOB: TvCategoryOB) {
        val currentPositions = categoryOB.tvChannelLink
        val currentChannels = categoryOB.tvchannels.sortedBy { it.number.toIntOrNull() ?: 0 }
        channelsByCategory.forEach {

            val position = currentChannels.indexOf(it)
            // Update Positionen für Channels, die durch neue Channels beeinflusst werden
            val modifiedOriginalPositions = currentPositions.filter { it.originalPosition >= position }.map { chPos ->
                chPos.originalPosition += 1
                chPos
            }
            manualPositionBox.put(modifiedOriginalPositions)

            val modifiedPositions = currentPositions.filter { it.position >= position }.map { chPos ->
                chPos.position += 1
                chPos
            }
            manualPositionBox.put(modifiedPositions)
            val newPosition = ChannelPositions(
                id = 0,
                channel = it.idByAccountData,
                playlistId = categoryOB.playlistId!!,
                relatedtvCategoryId = categoryOB.idByAccountData,
                position = position,
                originalPosition = position,
                catAndChannelAccount = "${categoryOB.idByAccountData}_${it.idByAccountData}_${account.id}"
            )
            newPosition.tvchannel.target = it
            newPosition.tvcategory.target = categoryOB
            manualPositionBox.put(newPosition)
        }
    }

    fun addExternalEpgToAccount(account: Accounts) {
        val externalEpgQuery = epgSourceBox.query(EpgSource_.isExternalEpg.equal(true)).build()
        val externalEpgs = externalEpgQuery.find()
        externalEpgs.forEach { epgSource ->
            val epgPosition = EpgSourcePositions(
                id = 0,
                account.id,
                epgSource.id,
                -1,
                false,
                false,
                "${account.id}_${epgSource.uniqueEpgSourceId}"
            )
            epgPosition.relatedaccount.target = account
            epgPosition.relatedepgsource.target = epgSource
            epgPositionBox.put(epgPosition)
        }
    }

    fun addChannelsToTvCategory(accountId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = accountBox.get(accountId)
            val alltvCategories = account.tvcategories
            alltvCategories.forEach { tvCat ->
                val thisChannels = account.channels.filter { it.relatedtvCategoryId == tvCat.idByAccountData }
                val updatedChannels = thisChannels.map {
                    it.reltvcategory.target = tvCat
                    it
                }
                tvChannelBox.put(updatedChannels)
                val manualPositions = thisChannels.sortedBy { it.number.toIntOrNull() ?: 0 }.mapIndexed { index, tvChannel ->
                    val manualPosition = ChannelPositions(
                        id = 0, // ObjectBox generiert die ID automatisch
                        channel = tvChannel.idByAccountData,
                        playlistId = tvCat.playlistId,
                        relatedtvCategoryId = tvCat.idByAccountData,
                        position = index,
                        originalPosition = index,
                        catAndChannelAccount = "${tvCat.idByAccountData}_${tvChannel.idByAccountData}_${account.id}"
                    )
                    // Initialisieren Sie die `ToOne`-Beziehung
                    manualPosition.tvchannel.target = tvChannel
                    manualPosition.tvcategory.target = tvCat

                    manualPosition
                }
                manualPositionBox.put(manualPositions)
            }
        }
    }

    fun updateTvCategories(
        url: String,
        macAddress: String,
        userAgent: String,
        name: String,
        token: String,
        timeZone: String,
        accountData: Accounts
    ) : Deferred<List<TvCategoryOB>> = viewModelScope.async {
        val response = stalkerRepository.getTvCategory(
            url,
            "mac=${macAddress}; stb_lang=de; timezone=$timeZone;",
            "Bearer $token",
            userAgent
        )
        when (response) {
            is Resource.Success -> {
                val networkCategories = response.data.js.map { tvcatresponse ->
                    val thisCategory = TvCategoryOB(
                        0,
                        accountData.id,
                        tvcatresponse.id,
                        tvcatresponse.number,
                        tvcatresponse.censored,
                        tvcatresponse.title,
                        tvcatresponse.title,
                        tvcatresponse.title,
                        accountData = name,
                        favorite = false,
                        0,
                        idByAccountData = "${tvcatresponse.id}_${accountData.id}",
                        true,
                        false,
                        null,
                        null,
                        false,
                        isAllChannelsCategory = if (tvcatresponse.id == "*") {
                            true
                        } else {
                            false
                        }
                    )
                    thisCategory.tvaccount.target = accountData
                    thisCategory
                }
                updateCategoriesInDatabase(accountData, networkCategories)
                networkCategories
            }

            is Resource.Error -> {
                // Handle error
                emptyList()
            }
        }
    }

    private suspend fun updateCategoriesInDatabase(
        accountData: Accounts,
        networkCategories: List<TvCategoryOB>
    ) {
        withContext(Dispatchers.IO) {
            // Hole die bereits vorhandenen Kategorien aus der Datenbank
            val settings = settingsBox.all.first()
            if (settings != null) {
                accountData.tvcategories.reset()
                val currentCategories = accountData.tvcategories

                currentCategories.forEach {
                    Log.d("UPDATE CAT: CURRENT", "${accountData.name} = ${it.showingName} POS-SIZE: ${it.tvChannelLink.size}")
                }

                // Filtere nur die Kategorien heraus, die noch nicht in der Datenbank sind
                val newCategories = networkCategories.filter { networkCategory ->
                    currentCategories.none { it.idByAccountData == networkCategory.idByAccountData }
                }

                // Filtere die Kategorien heraus, die in der Datenbank vorhanden, aber nicht in der Netzwerkanfrage sind
                val categoriesToDelete = currentCategories.filter { currentCategory ->
                    networkCategories.none { it.idByAccountData == currentCategory.idByAccountData || currentCategory.isFavoriteCategory }
                }

                // Füge die neuen Kategorien zur Datenbank hinzu
                if (newCategories.isNotEmpty()) {
                    newCategories.forEach {
                        Log.d("UPDATE CAT: NEWCATEGORIES", "${accountData.name} = ${it.showingName}")
                    }
                    tvCategoryBox.put(newCategories)
                    val censoredCategories = newCategories.filter { it.censored == 1 }
                    addCensoredChannelsToTvCategory(censoredCategories, accountData, accountData.name, settings)
                }
                if (categoriesToDelete.isNotEmpty()) {
                    categoriesToDelete.forEach {
                        Log.d("UPDATE CAT: DELETE", "${accountData.name} = ${it.showingName}")
                    }
                    tvCategoryBox.remove(categoriesToDelete)
                }

                val updatedCategories = currentCategories.map { category ->
                    val thisCategory =
                        networkCategories.find { it.idByAccountData == category.idByAccountData }
                    if (thisCategory != null) {
                        if (category.title != thisCategory.title) {
                            category.title = thisCategory.title
                            category.editedName = thisCategory.editedName
                            val newCategoryName = updatePrefixesAndSuffixesForModifiedCategories(
                                settings.tvcategoryPrefixes,
                                settings.tvcategorySuffixes,
                                category
                            )
                            category.showingName = newCategoryName.showingName
                        }
                        if (category.newCategory) {
                            category.newCategory = false
                        }
                    }
                    category
                }

                tvCategoryBox.put(updatedCategories)

                updatedCategories.forEach {
                    Log.d("UPDATE CAT: UPDATE", "${accountData.name} = ${it.showingName} POS-SIZE: ${it.tvChannelLink.size}")
                }

                accountData.totalTvCategories = networkCategories.size.toString()
                accountBox.put(accountData)
            }
        }
    }

    private fun updatePrefixesAndSuffixesCategoriesForAddedPlaylist(prefixes: List<String>?, suffixes: List<String>?, tvcategories: List<TvCategoryOB>): List<TvCategoryOB> {
        viewModelScope.launch {
            tvcategories.map { tvcategory ->
                var modifiedName = tvcategory.editedName
                if (!prefixes.isNullOrEmpty()) {
                    prefixes.forEach { prefix ->
                        if (modifiedName.startsWith(prefix)) {
                            modifiedName = modifiedName.removePrefix(prefix).trim()
                        }
                    }
                }
                if (!suffixes.isNullOrEmpty()) {
                    suffixes.forEach { suffix ->
                        if (modifiedName.endsWith(suffix)) {
                            modifiedName = modifiedName.removeSuffix(suffix).trim()
                        }
                    }
                }
                tvcategory.showingName = modifiedName
            }
            // Update the edited channels in the database
        }
        return tvcategories
    }

    private fun updatePrefixesAndSuffixesForModifiedCategories(prefixes: List<String>?, suffixes: List<String>?, tvcategory: TvCategoryOB): TvCategoryOB {
        var modifiedName = tvcategory.editedName
        if (!prefixes.isNullOrEmpty()) {
            prefixes.forEach { prefix ->
                if (modifiedName.startsWith(prefix)) {
                    modifiedName = modifiedName.removePrefix(prefix).trim()
                }
            }
        }
        if (!suffixes.isNullOrEmpty()) {
            suffixes.forEach { suffix ->
                if (modifiedName.endsWith(suffix)) {
                    modifiedName = modifiedName.removeSuffix(suffix).trim()
                }
            }
        }
        tvcategory.showingName = modifiedName
        return tvcategory
    }


    fun getTvChannels(
        url: String,
        macAddress: String,
        userAgent: String,
        name: String,
        token: String,
        timeZone: String
    ): Deferred<List<TvChannelOB>> = viewModelScope.async {
        _playlistProcessState.value = PlaylistLoadProcessState.GetChannels(
            1,
            "Load Tv Channels..",
        )
        val accountQuery = accountBox
            .query(Accounts_.totalAccountData.equal("$url$macAddress"))
            .build()
        val accountData = accountQuery.findFirst()
        val thisEpgSource = epgSourceBox.query(EpgSource_.playlistId.equal(accountData!!.id)).build().findFirst()
        val response = stalkerRepository.getAllTvChannels(
            url,
            "mac=${macAddress}; stb_lang=de; timezone=$timeZone;",
            "Bearer $token",
            userAgent
        )
        when (response) {
            is Resource.Success -> {
                val groupedChannels = response.data.js.data.groupBy { it.tv_genre_id }
                val channelList = groupedChannels.flatMap { (genreId, channels) ->
                    channels.mapIndexed { index, it ->
                        val thisChannel = TvChannelOB(
                            0,
                            accountData.id,
                            it.id?.toIntOrNull(),
                            it.number ?: "",
                            it.cmd ?: "",
                            it.logo ?: "",
                            "",
                            it.tv_genre_id?.toIntOrNull(),
                            "${it.tv_genre_id}_${accountData.id}",
                            it.name ?: "",
                            it.name ?: "",
                            it.name ?: "",
                            it.xmltv_id?.lowercase() ?: "",
                            it.enable_tv_archive,
                            it.tv_archive_duration,
                            it.archive,
                            name,
                            "${it.id}_${accountData.id}",
                            thisEpgSource?.id,
                            true,
                            false,
                            false,
                            0L,
                            false,
                            null,
                            "${it.tv_genre_id}_${accountData.id}"
                        )
                        thisChannel.account.target = accountData
                        thisChannel
                    }
                }
                if (channelList.isNotEmpty()) {
                    val allSettings = settingsBox.all
                    val settings = allSettings.first()
                    if (settings != null) {
                        if (settings.prefixes.isNotEmpty() || settings.suffixes.isNotEmpty()) {
                            val updatedChannelList = updatePrefixesAndSuffixesForAddedPlaylist(
                                settings.prefixes,
                                settings.suffixes,
                                channelList
                            )
                            normalChannelsCount = updatedChannelList.size
                            accountData.totalTvChannels = updatedChannelList.size.toString()
                            accountBox.put(accountData)
                            tvChannelBox.put(updatedChannelList)
                            _playlistProcessState.value =
                                PlaylistLoadProcessState.GetChannels(
                                    100,
                                    updatedChannelList.size.toString()
                                )
                            updatedChannelList
                        } else {
                            tvChannelBox.put(channelList)
                            accountData.totalTvChannels = channelList.size.toString()
                            normalChannelsCount = channelList.size
                            accountBox.put(accountData)
                            _playlistProcessState.value =
                                PlaylistLoadProcessState.GetChannels(
                                    100,
                                    channelList.size.toString()
                                )
                            channelList
                        }
                    } else {
                        emptyList()
                    }
                } else {
                    _playlistProcessState.value =
                        PlaylistLoadProcessState.ChannelsError("Error: No channels found!")
                    emptyList()
                }
            }

            is Resource.Error -> {
                _playlistProcessState.value =
                    PlaylistLoadProcessState.ChannelsError(response.message)
                Log.d("TVCATACCOUNT", "NO CHANN FOUND: ${response.message}")
                emptyList()
            }

            else -> {
                emptyList()
            }
        }
    }

    private fun updatePrefixesAndSuffixesForAddedPlaylist(prefixes: List<String>?, suffixes: List<String>?, tvchannels: List<TvChannelOB>): List<TvChannelOB> {
        viewModelScope.launch {
            tvchannels.map { channel ->
                var modifiedName = channel.editedName
                if (!prefixes.isNullOrEmpty()) {
                    prefixes.forEach { prefix ->
                        if (modifiedName.startsWith(prefix)) {
                            modifiedName = modifiedName.removePrefix(prefix).trim()
                        }
                    }
                }
                if (!suffixes.isNullOrEmpty()) {
                    suffixes.forEach { suffix ->
                        if (modifiedName.endsWith(suffix)) {
                            modifiedName = modifiedName.removeSuffix(suffix).trim()
                        }
                    }
                }
                channel.showingName = modifiedName
            }
            // Update the edited channels in the database
        }
        return tvchannels
    }

    fun updateTvChannels(
        url: String,
        macAddress: String,
        userAgent: String,
        name: String,
        token: String,
        timeZone: String,
        accountData: Accounts
    ) : Deferred<List<TvChannelOB>> = viewModelScope.async {
        withContext(Dispatchers.IO) {
            val epgSource = epgSourceBox.query(EpgSource_.playlistId.equal(accountData.id)).build().findFirst()
            val response = stalkerRepository.getAllTvChannels(
                url,
                "mac=${macAddress}; stb_lang=en; timezone=$timeZone;",
                "Bearer $token",
                userAgent
            )
            when (response) {
                is Resource.Success -> {
                    Log.d("UPDATE STALKER", "${accountData.name} = GET THEM: ${response.data.js.data.size}")

                    val networkTvChannels = response.data.js.data.map {
                        val thisChannel = TvChannelOB(
                            0,
                            accountData.id,
                            it.id?.toIntOrNull(),
                            it.number ?: "",
                            it.cmd ?: "",
                            it.logo ?: "",
                            "",
                            it.tv_genre_id?.toIntOrNull(),
                            "${it.tv_genre_id}_${accountData.id}",
                            it.name ?: "",
                            it.name ?: "",
                            it.name ?: "",
                            it.xmltv_id?.lowercase() ?: "",
                            it.enable_tv_archive,
                            it.tv_archive_duration,
                            it.archive,
                            name,
                            "${it.id}_${accountData.id}",
                            epgSource?.id,
                            true,
                            false,
                            false,
                            0L,
                            true,
                            null,
                            "${it.tv_genre_id}_${accountData.id}"
                        )
                        thisChannel.account.target = accountData
                        thisChannel
                    }
                    updateTvChannelsInDatabase(accountData, networkTvChannels)
                    networkTvChannels
                }

                is Resource.Error -> {
                    Log.d("UPDATE STALKER", "${accountData.name} = ERROR GET THEM: ${response.message}")
                    emptyList()
                }

                else -> {
                    emptyList()
                }
            }
        }
    }

    private suspend fun updateTvChannelsInDatabase(
        accountData: Accounts,
        networkChannels: List<TvChannelOB>
    ) {
        withContext(Dispatchers.IO) {
            val settings = settingsBox.all.first()
            if (settings != null) {

                val currentChannels = accountData.channels
                // Filtere nur die Kanäle heraus, die noch nicht in der Datenbank sind
                val newChannels = networkChannels.filter { networkChannel ->
                    currentChannels.none { it.idByAccountData == networkChannel.idByAccountData }
                }

                // Filtere die Kanäle heraus, die in der Datenbank vorhanden, aber nicht in der Netzwerkanfrage sind
                val channelsToDelete = currentChannels.filter { currentTvChannel ->
                    networkChannels.none { it.idByAccountData == currentTvChannel.idByAccountData }
                }

                if (channelsToDelete.isNotEmpty()) {
                    channelsToDelete.forEach { tvChannel ->
                        Log.d("UPDATE CHANN: DELETE", "${accountData.name} = ${tvChannel.showingName}")
                        // Hole die zugehörigen ChannelPosition-Einträge
                        val channelPositionsToDelete = tvChannel.tvcategoryLink

                        // Für jede zu löschende Position
                        channelPositionsToDelete.forEach { manualPosition ->
                            val oldPosition = manualPosition.position
                            val oldOriginalPosition = manualPosition.originalPosition
                            val category = manualPosition.tvcategory.target

                            // Hole alle Positionen für diese Kategorie
                            val allPositionsForThisCategory = category.tvChannelLink

                            // Reduziere die Positionen um 1 für alle Channels, die nach der zu löschenden Position kommen
                            val positionsToChange = allPositionsForThisCategory.filter { it.position > oldPosition }.map { changePositon ->
                                changePositon.position -= 1
                                changePositon// Speichere die neue Position
                            }
                            manualPositionBox.put(positionsToChange)
                            // Reduziere die Originalpositionen um 1 für alle Channels, die nach der zu löschenden Originalposition kommen
                            val originalPositionsToChange = allPositionsForThisCategory.filter { it.originalPosition > oldOriginalPosition }.map { changeOriginalPosition ->
                                changeOriginalPosition.originalPosition -= 1
                                changeOriginalPosition // Speichere die neue Originalposition
                            }
                            manualPositionBox.put(originalPositionsToChange)
                        }

                        // Entferne die gefundenen ChannelPosition-Einträge
                        manualPositionBox.remove(channelPositionsToDelete)
                    }
                    tvChannelBox.remove(channelsToDelete)
                }

                val changedCategoryChannels: MutableList<TvChannelOB> = mutableListOf()
                currentChannels.forEach { channel ->
                    val thisChannel = networkChannels.find { it.idByAccountData == channel.idByAccountData }
                    if (thisChannel != null) {
                        if (channel.name != thisChannel.name) {
                            channel.name = thisChannel.name
                            channel.editedName = thisChannel.editedName
                            val modifiedChannel = updatePrefixesAndSuffixesForModifiedChannels(settings.prefixes, settings.suffixes, channel)
                            channel.showingName = modifiedChannel.showingName
                        }
                        channel.xmltv_id = thisChannel.xmltv_id
                        channel.logo = thisChannel.logo
                        channel.number = thisChannel.number
                        if (channel.newChannel) {
                            Log.d("UPDATECHANELS", "TO FALSE = ${channel.showingName}")
                            channel.newChannel = false
                        }
                        if (channel.tv_genre_id != thisChannel.tv_genre_id) {
                            val oldCategory = channel.reltvcategory.target
                            channel.tv_genre_id = thisChannel.tv_genre_id
                            channel.relatedtvCategoryId = thisChannel.relatedtvCategoryId
                            val oldPositions = oldCategory.tvChannelLink
                            val oldChPos = oldPositions.firstOrNull { it.channel == channel.idByAccountData }
                            if (oldChPos != null) {
                                val oldPosition = oldChPos.position
                                val oldOriginalPosition = oldChPos.originalPosition
                                oldPositions.filter { it.position > oldPosition }.forEach {
                                    it.position -= 1
                                }
                                oldPositions.filter { it.originalPosition > oldOriginalPosition }.forEach {
                                    it.originalPosition -= 1
                                }
                                manualPositionBox.put(oldPositions)
                                manualPositionBox.remove(oldChPos)
                            }
                            changedCategoryChannels.add(channel)
                        }
                    }
                }
                tvChannelBox.put(currentChannels)

                newChannels.forEach {
                    Log.d("UPDATE CHANN: NEW", "${accountData.name} = ${it.showingName}")
                }
                changedCategoryChannels.forEach {
                    Log.d("UPDATE CHANN: UPDATE", "${accountData.name} = ${it.showingName}")
                }

                val channelsToModifyPosition = changedCategoryChannels + newChannels

                addNewChannelsToTvCategory(accountData, channelsToModifyPosition)

                accountData.totalTvChannels = networkChannels.size.toString()
                accountBox.put(accountData)
            }
        }
    }


    private fun updatePrefixesAndSuffixesForModifiedChannels(prefixes: List<String>?, suffixes: List<String>?, tvchannel: TvChannelOB): TvChannelOB {
        var modifiedName = tvchannel.editedName
        if (!prefixes.isNullOrEmpty()) {
            prefixes.forEach { prefix ->
                if (modifiedName.startsWith(prefix)) {
                    modifiedName = modifiedName.removePrefix(prefix).trim()
                }
            }
        }
        if (!suffixes.isNullOrEmpty()) {
            suffixes.forEach { suffix ->
                if (modifiedName.endsWith(suffix)) {
                    modifiedName = modifiedName.removeSuffix(suffix).trim()
                }
            }
        }
        tvchannel.showingName = modifiedName
        return tvchannel
    }

    suspend fun getShortEpgByChannel(
        account: Accounts,
        tvChannelOB: TvChannelOB
    ): List<EpgDataOB>? =
        withContext(Dispatchers.IO) {
            val epgResult = stalkerRepository.getShortEpgByChannel(
                account.stalkerUrl,
                channelId = tvChannelOB.channelId.toString(),
                "mac=${account.macAddress}; stb_lang=de; timezone=${account.timezone};",
                "Bearer ${account.token}",
                account.userAgent
            )
            when (epgResult) {
                is Resource.Success -> {
                    val epgSource =
                        epgSourceBox.query(EpgSource_.playlistId.equal(account.id)).build()
                            .findFirst()

                    if (!epgResult.data?.js.isNullOrEmpty() && epgSource != null) {
                        val epg = epgResult.data?.js?.map {
                            val dateTimeString = it.time
                            val datum = dateTimeString?.let { dts -> extractDate(dts) }
                            EpgDataOB(
                                0,
                                "${account.id}_${it.ch_id}_${it.start_timestamp}",
                                it.id.toString(),
                                it.ch_id ?: "",
                                datum ?: "",
                                it.name ?: "",
                                "",
                                it.descr ?: "",
                                mutableListOf(),
                                mutableListOf(),
                                mutableListOf(),
                                "",
                                mutableListOf(),
                                "",
                                "",
                                "",
                                it.t_time ?: "",
                                it.t_time_to ?: "",
                                it.start_timestamp?.toLong(),
                                it.stop_timestamp?.toLong(),
                                it.mark_archive,
                                account.id.toString(),
                                epgSource.id.toInt(),
                                "${epgSource.id}_${it.ch_id}"
                            )
                        }
                        if (!epg.isNullOrEmpty()) {
                            return@withContext epg
                        } else {
                            return@withContext null
                        }
                    } else {
                        return@withContext null
                    }
                }

                is Resource.Error -> {
                    return@withContext null
                }
            }
        }

    fun getMovieCategories(
        url: String,
        macAddress: String,
        userAgent: String,
        name: String,
        token: String,
        timeZone: String
    ): Deferred<List<MovieCategoryOB>> = viewModelScope.async {
        _playlistProcessState.value = PlaylistLoadProcessState.GetMovieCategories(
            1,
            "Load Movie Categories..",
            ""
        )
        val accountQuery = accountBox
            .query(Accounts_.totalAccountData.equal("$url$macAddress"))
            .build()
        val accountData = accountQuery.findFirst()

        val response = stalkerRepository.getMovieCategory(
            url,
            "mac=${macAddress}; stb_lang=de; timezone=$timeZone;",
            "Bearer $token",
            userAgent
        )
        when (response) {
            is Resource.Success -> {
                val categories = response.data.js.map { moviecatresponse ->
                    val thisCategory = MovieCategoryOB(
                        0,
                        accountData!!.id,
                        moviecatresponse.id,
                        moviecatresponse.title,
                        moviecatresponse.title,
                        moviecatresponse.title,
                        accountData = name,
                        favorite = false,
                        null,
                        idByAccountData = "${moviecatresponse.id}_${accountData.id}",
                        false
                    )
                    thisCategory.movieaccount.target = accountData
                    thisCategory
                }
                if (categories.isNotEmpty()) {
                    val settings = settingsBox.all.first()
                    if (settings != null) {
                        val categoriesToAdd =
                            if (settings.moviecategoryPrefixes.isNotEmpty() || settings.moviecategorySuffixes.isNotEmpty()) {
                                val updatedTvCategoriesList =
                                    updatePrefixesAndSuffixeMovieCategoriesForAddedPlaylist(
                                        settings.moviecategoryPrefixes,
                                        settings.moviecategorySuffixes,
                                        categories
                                    )
                                updatedTvCategoriesList
                            } else {
                                categories
                            }
                        movieCategoryBox.put(categoriesToAdd)
                        accountData!!.totalMovieCategories = categoriesToAdd.size.toString()
                        accountBox.put(accountData)
                        val allmovies = stalkerRepository.getMoviesByCategory(
                            url,
                            "mac=${macAddress}; stb_lang=de; timezone=$timeZone;",
                            "Bearer $token",
                            "*",
                            0,
                            userAgent
                        )
                        when (allmovies) {
                            is Resource.Success -> {
                                val totalMovies = allmovies.data.js.total_items.toString()
                                _playlistProcessState.value = PlaylistLoadProcessState.GetMovieCategories(
                                    100,
                                    categories.size.toString(),
                                    totalMovies
                                )
                            }
                            is Resource.Error -> {
                                _playlistProcessState.value = PlaylistLoadProcessState.GetMovieCategories(
                                    100,
                                    categories.size.toString(),
                                    "0"
                                )
                            }
                        }
                        categoriesToAdd
                    } else {
                        emptyList()
                    }
                } else {
                    _playlistProcessState.value =
                        PlaylistLoadProcessState.MovieError("Error: No tv categories found!")
                    emptyList()
                }
            }

            is Resource.Error -> {
                _playlistProcessState.value =
                    PlaylistLoadProcessState.MovieError(response.message)
                emptyList()
            }
        }
    }

    fun updateMovieCategories(
        url: String,
        macAddress: String,
        userAgent: String,
        name: String,
        token: String,
        timeZone: String,
        accountData: Accounts
    ) : Deferred<List<MovieCategoryOB>> = viewModelScope.async {
        withContext(Dispatchers.IO) {
            val response = stalkerRepository.getMovieCategory(
                url,
                "mac=${macAddress}; stb_lang=de; timezone=$timeZone;",
                "Bearer $token",
                userAgent
            )
            when (response) {
                is Resource.Success -> {
                    val newCategories = response.data.js.map { moviecatresponse ->
                        val thisCategory = MovieCategoryOB(
                            0,
                            accountData.id,
                            moviecatresponse.id,
                            moviecatresponse.title,
                            moviecatresponse.title,
                            moviecatresponse.title,
                            accountData = name,
                            favorite = false,
                            null,
                            idByAccountData = "${moviecatresponse.id}_${accountData.id}",
                            true
                        )
                        thisCategory.movieaccount.target = accountData
                        thisCategory
                    }
                    updateMovieCategoriesInDatabase(accountData, newCategories)
                    newCategories
                }
                is Resource.Error -> {
                    emptyList()
                }
            }
        }
    }

    private suspend fun updateMovieCategoriesInDatabase(
        accountData: Accounts,
        networkCategories: List<MovieCategoryOB>
    ) {
        withContext(Dispatchers.IO) {
            // Hole die bereits vorhandenen Kategorien aus der Datenbank
            val settings = settingsBox.all.first()
            if (settings != null) {
                val currentMovieCategoriesQuery = movieCategoryBox
                    .query(MovieCategoryOB_.playlistId.equal(accountData.id))
                    .build()
                val currentMovieCategories = currentMovieCategoriesQuery.find()
                // Filtere nur die Kategorien heraus, die noch nicht in der Datenbank sind
                val newCategories = networkCategories.filter { networkCategory ->
                    currentMovieCategories.none { it.idByAccountData == networkCategory.idByAccountData }
                }

                // Filtere die Kategorien heraus, die in der Datenbank vorhanden, aber nicht in der Netzwerkanfrage sind
                val categoriesToDelete = currentMovieCategories.filter { currentCategory ->
                    networkCategories.none { it.idByAccountData == currentCategory.idByAccountData }
                }

                // Füge die neuen Kategorien zur Datenbank hinzu
                if (newCategories.isNotEmpty()) {
                    newCategories.forEach {
                        Log.d("UPDATEPLAYLIST", "NEWMOVIECAT: ${it.showingName}")
                    }
                    movieCategoryBox.put(newCategories)
                    accountBox.put(accountData)
                }
                if (categoriesToDelete.isNotEmpty()) {
                    movieCategoryBox.remove(categoriesToDelete)
                }

                val updatedCategories = currentMovieCategories.map { category ->
                    val thisCategory =
                        networkCategories.find { it.idByAccountData == category.idByAccountData }
                    if (thisCategory != null) {
                        if (category.title != thisCategory.title) {
                            category.title = thisCategory.title
                            category.editedName = thisCategory.editedName
                            val newCategoryName =
                                updatePrefixesAndSuffixesForModifiedMovieCategories(
                                    settings.moviecategoryPrefixes,
                                    settings.moviecategorySuffixes,
                                    category
                                )
                            category.showingName = newCategoryName.showingName
                        }
                        if (category.newCategory) {
                            category.newCategory = false
                        }
                    }
                    category
                }
                updatedCategories.forEach {
                    Log.d("UPDATEPLAYLIST", "UPDATEDMOVIECAT: ${it.showingName}")
                }
                movieCategoryBox.put(updatedCategories)
                accountData.totalMovieCategories = networkCategories.size.toString()
                accountBox.put(accountData)
            }
        }
    }

    private val _totalMovies = MutableLiveData<Int>(0)
    val totalMovies: LiveData<Int> get() = _totalMovies


    fun getMoviesByCategory(account: Accounts, categoryId: String, movieBox: Box<MovieOB>, sortby: String): Flow<PagingData<MovieOB>> {
        return Pager(
            config = PagingConfig(
                pageSize = 14,          // 14 Filme pro Seite
                prefetchDistance = 42,    // Wenn noch 10 Filme übrig sind, lade die nächste Seite
                initialLoadSize = 14,     // Lade die ersten 3 Seiten (42 Filme)
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                MoviePagingSource(retrofitInstance, account, categoryId, sortby, movieBox) { totalmvs ->
                    _totalMovies.postValue(totalmvs)
                }
            }
        ).flow
    }

    fun resetTotalMovies() {
        _totalMovies.value = 0
    }


    fun searchMoviesByCategory(
        account: Accounts,
        categoryId: String,
        searchTerm: String): Deferred<Set<MovieOB>> = viewModelScope.async{
        val response = stalkerRepository.searchMoviesByCategory(
            account.stalkerUrl,
            "mac=${account.macAddress}; stb_lang=de; timezone=${account.timezone};",
            "Bearer ${account.token}",
            categoryId,
            1,
            account.userAgent,
            searchTerm
        )
        when (response) {
            is Resource.Success -> {
                val searchMoviesList: MutableSet<MovieOB> = mutableSetOf()
                val currentMoviesMap: Map<String, MovieOB> = movieBox.query(
                    MovieOB_.accountId.equal(account.id).and(MovieOB_.relatedMovieCategoryId.equal(categoryId))
                ).build().find().associateBy { it.idByAccountData }

                val maxItemsPerPage =  response.data.js.max_page_items
                val totalItems = response.data.js.total_items
                val totalPages = ceil(totalItems.toDouble() / maxItemsPerPage.toDouble()).toInt()
                val movies = response.data.js.data.map { movieData ->
                    val idByAccountData = "${movieData.id}_${account.id}"
                    currentMoviesMap[idByAccountData] ?: convertToMovie(movieData, account)
                }
                searchMoviesList.addAll(movies)
                if (totalPages > 1) {
                    val pagesToCheck = createSequentialList(2, totalPages)
                    for (page in pagesToCheck) {
                        val newresponse = stalkerRepository.searchMoviesByCategory(
                            account.stalkerUrl,
                            "mac=${account.macAddress}; stb_lang=de; timezone=${account.timezone};",
                            "Bearer ${account.token}",
                            categoryId,
                            page,
                            account.userAgent,
                            searchTerm
                        )
                        when (newresponse) {
                            is Resource.Success -> {
                                val newmovies = newresponse.data.js.data.map { movieData ->
                                    val idByAccountData = "${movieData.id}_${account.id}"
                                    currentMoviesMap[idByAccountData] ?: convertToMovie(movieData, account)
                                }
                                searchMoviesList.addAll(newmovies)
                            }
                            is Resource.Error -> {

                            }
                        }
                    }
                }
                searchMoviesList
            }
            is Resource.Error -> {
                emptySet()
            }
        }
    }

    fun convertToMovie(movieData: MovieData, account: Accounts): MovieOB {
        return MovieOB(
            id = 0,
            idByAccountData = "${movieData.id}_${account.id}",
            movieId = movieData.id ?: "",
            relatedMovieCategoryId = movieData.category_id ?: "",
            accountName = account.name,
            accountId = account.id,
            movieName = movieData.name ?: "",
            movieCmd = movieData.cmd ?: "",
            movieTime = movieData.time,
            movieYear = movieData.year ?: "",
            rate = movieData.rate ?: "",
            rating_imdb = movieData.rating_imdb ?: "",
            screenshot_uri = movieData.screenshot_uri ?: "",
            genres_str = movieData.genres_str ?: "",
            actors = movieData.actors ?: "",
            added = toUnixTimestamp(movieData.added) ?: "",
            age = movieData.age ?: "",
            description = movieData.description ?: "",
            director = movieData.director ?: "",
            tmdb_id = movieData.tmdb_id,
            o_name = movieData.o_name ?: "",
            currentPosition = 0L, // Platzhalter für aktuelle Position
            isFavorite = false,
            isCompletelyWatched = movieData.isFullyWatched,
            isPartlyWatched = movieData.isPartlyWatched,
            percentagePlayed = 0.0 // Platzhalter für Prozentwert
        )
    }

    fun getMovieLink(
        url: String,
        cmd: String,
        cookie: String,
        token: String,
        userAgent: String
    ): Deferred<Resource<String?>> = viewModelScope.async {
        val response = stalkerRepository.getMovieLink(url, cmd, cookie, token, userAgent)
        when (response) {
            is Resource.Success -> {
                val movieUrl = response.data?.js?.cmd
                Resource.Success("$movieUrl")
            }

            is Resource.Error -> {
                Resource.Error(response.message)
            }
        }
    }

    fun getSeriesCategories(
        url: String,
        macAddress: String,
        userAgent: String,
        name: String,
        token: String,
        timeZone: String
    ): Deferred<List<SeriesCategoryOB>> = viewModelScope.async {
        _playlistProcessState.value = PlaylistLoadProcessState.GetSeriesCategories(
            1,
            "Load Series Categories..",
            ""
        )
        val accountQuery = accountBox
            .query(Accounts_.totalAccountData.equal("$url$macAddress"))
            .build()
        val accountData = accountQuery.findFirst()

        val response = stalkerRepository.getSeriesCategory(
            url,
            "mac=${macAddress}; stb_lang=de; timezone=$timeZone;",
            "Bearer $token",
            userAgent
        )
        when (response) {
            is Resource.Success -> {
                val categories = response.data.js.map { seriescatresponse ->
                    val thisCategory = SeriesCategoryOB(
                        0,
                        accountData!!.id,
                        seriescatresponse.id,
                        seriescatresponse.title,
                        seriescatresponse.title,
                        seriescatresponse.title,
                        accountData = name,
                        favorite = false,
                        idByAccountData = "${seriescatresponse.id}_${accountData.id}",
                        false
                    )
                    thisCategory.seriesaccount.target = accountData
                    thisCategory
                }
                if (categories.isNotEmpty()) {
                    val settings = settingsBox.all.first()
                    if (settings != null) {
                        val categoriesToAdd =
                            if (settings.moviecategoryPrefixes.isNotEmpty() || settings.moviecategorySuffixes.isNotEmpty()) {
                                val updatedTvCategoriesList =
                                    updatePrefixesAndSuffixeSeriesCategoriesForAddedPlaylist(
                                        settings.moviecategoryPrefixes,
                                        settings.moviecategorySuffixes,
                                        categories
                                    )
                                updatedTvCategoriesList
                            } else {
                                categories
                            }
                        seriesCategoryBox.put(categoriesToAdd)
                        accountData!!.totalSeriesCategories = categoriesToAdd.size.toString()
                        accountBox.put(accountData)
                        val allseries = stalkerRepository.getSeriesByCategory(
                            url,
                            "mac=${macAddress}; stb_lang=de; timezone=$timeZone;",
                            "Bearer $token",
                            "*",
                            0,
                            userAgent
                        )
                        when (allseries) {
                            is Resource.Success -> {
                                val totalSeries = allseries.data.js.total_items.toString()
                                _playlistProcessState.value = PlaylistLoadProcessState.GetSeriesCategories(
                                    100,
                                    categories.size.toString(),
                                    totalSeries
                                )
                            }
                            is Resource.Error -> {
                                _playlistProcessState.value = PlaylistLoadProcessState.GetSeriesCategories(
                                    100,
                                    categories.size.toString(),
                                    "0"
                                )
                            }
                        }
                        categoriesToAdd
                    } else {
                        emptyList()
                    }
                } else {
                    _playlistProcessState.value =
                        PlaylistLoadProcessState.SeriesError("Error: No series categories found!")
                    emptyList()
                }
            }

            is Resource.Error -> {
                _playlistProcessState.value =
                    PlaylistLoadProcessState.SeriesError(response.message)
                emptyList()
            }
        }
    }

    fun updateSeriesCategories(
        url: String,
        macAddress: String,
        userAgent: String,
        name: String,
        token: String,
        timeZone: String,
        accountData: Accounts
    ) : Deferred<List<SeriesCategoryOB>> = viewModelScope.async {
        withContext(Dispatchers.IO) {
            val response = stalkerRepository.getSeriesCategory(
                url,
                "mac=${macAddress}; stb_lang=de; timezone=$timeZone;",
                "Bearer $token",
                userAgent
            )
            when (response) {
                is Resource.Success -> {
                    val newCategories = response.data.js.map { seriescatresponse ->
                        val thisCategory = SeriesCategoryOB(
                            0,
                            accountData.id,
                            seriescatresponse.id,
                            seriescatresponse.title,
                            seriescatresponse.title,
                            seriescatresponse.title,
                            accountData = name,
                            favorite = false,
                            idByAccountData = "${seriescatresponse.id}_${accountData.id}",
                            true
                        )
                        thisCategory.seriesaccount.target = accountData
                        thisCategory
                    }
                    updateSeriesCategoriesInDatabase(accountData, newCategories)
                    newCategories
                }
                is Resource.Error -> {
                    emptyList()
                }
            }
        }
    }

    private suspend fun updateSeriesCategoriesInDatabase(
        accountData: Accounts,
        networkCategories: List<SeriesCategoryOB>
    ) {
        withContext(Dispatchers.IO) {
            // Hole die bereits vorhandenen Kategorien aus der Datenbank
            val settings = settingsBox.all.first()
            if (settings != null) {
                val currentSeriesCategoriesQuery = seriesCategoryBox
                    .query(SeriesCategoryOB_.playlistId.equal(accountData.id))
                    .build()
                val currentSeriesCategories = currentSeriesCategoriesQuery.find()
                // Filtere nur die Kategorien heraus, die noch nicht in der Datenbank sind
                val newCategories = networkCategories.filter { networkCategory ->
                    currentSeriesCategories.none { it.idByAccountData == networkCategory.idByAccountData }
                }

                // Filtere die Kategorien heraus, die in der Datenbank vorhanden, aber nicht in der Netzwerkanfrage sind
                val categoriesToDelete = currentSeriesCategories.filter { currentCategory ->
                    networkCategories.none { it.idByAccountData == currentCategory.idByAccountData }
                }

                // Füge die neuen Kategorien zur Datenbank hinzu
                if (newCategories.isNotEmpty()) {

                    seriesCategoryBox.put(newCategories)
                    accountBox.put(accountData)
                }
                if (categoriesToDelete.isNotEmpty()) {
                    seriesCategoryBox.remove(categoriesToDelete)
                }

                val updatedCategories = currentSeriesCategories.map { category ->
                    val thisCategory =
                        networkCategories.find { it.idByAccountData == category.idByAccountData }
                    if (thisCategory != null) {
                        if (category.title != thisCategory.title) {
                            category.title = thisCategory.title
                            category.editedName = thisCategory.editedName
                            val newCategoryName =
                                updatePrefixesAndSuffixesForModifiedSeriesCategories(
                                    settings.moviecategoryPrefixes,
                                    settings.moviecategorySuffixes,
                                    category
                                )
                            category.showingName = newCategoryName.showingName
                        }
                        if (category.newCategory) {
                            category.newCategory = false
                        }
                    }
                    category
                }

                seriesCategoryBox.put(updatedCategories)
                accountData.totalSeriesCategories = networkCategories.size.toString()
                accountBox.put(accountData)
            }
        }
    }

    private val _totalSeries = MutableLiveData<Int>(0)
    val totalSeries: LiveData<Int> get() = _totalSeries


    fun getSeriesByCategory(account: Accounts, categoryId: String, seriesBox: Box<SeriesOB>, sortby: String): Flow<PagingData<SeriesOB>> {
        return Pager(
            config = PagingConfig(
                pageSize = 14,          // 14 Filme pro Seite
                prefetchDistance = 42,    // Wenn noch 10 Filme übrig sind, lade die nächste Seite
                initialLoadSize = 14,     // Lade die ersten 3 Seiten (42 Filme)
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                SeriePagingSource(retrofitInstance, account, categoryId, sortby, seriesBox) { totalss ->
                    _totalSeries.postValue(totalss)
                }
            }
        ).flow
    }

    var seriesDetailData =  MutableLiveData<MutableList<SeasonsOB>>()

    val seriesCache: MutableMap<String, Pair<MutableList<SeasonsOB>, MutableList<EpisodesOB>>> = mutableMapOf()

    suspend fun getSeriesDetail(
        serieDetail: SeriesOB,
        account: Accounts
    ) {
        withContext(Dispatchers.IO) {
            seriesCache[serieDetail.idByAccountData]?.let { cachedData ->
                // Wenn die Serie im Cache ist, lade nur die Seasons und Episoden
                episodesList = cachedData.second.toMutableList()
                return@let cachedData.first.toList()  // Seasons abrufen
            }
            val response = stalkerRepository.getSeriesDetails(
                account.stalkerUrl,
                "mac=${account.macAddress}; stb_lang=de; timezone=${account.timezone};",
                "Bearer ${account.token}",
                serieDetail.seriesId,
                account.userAgent
            )
            when (response) {
                is Resource.Success -> {
                    val existingSerieInDB = seriesBox.query(
                        SeriesOB_.idByAccountData.equal(serieDetail.idByAccountData)
                    ).build().findFirst() != null
                    val existingSeasonsMap = seasonsBox.query(
                        SeasonsOB_.seriesIdByAccount.equal(serieDetail.idByAccountData)
                    ).build().find().associateBy { it.seriesSeasonIdByAccountData }

                    val newSeasons = response.data.map { apiSeason ->
                        val idByAccountData = "${apiSeason.id}_${serieDetail.seriesId}_${account.id}"
                        val seasonNumber = if (apiSeason.name.startsWith("Season")) {
                            apiSeason.name.removePrefix("Season").trim()
                        } else {
                            apiSeason.name
                        }
                        existingSeasonsMap[idByAccountData] ?: SeasonsOB(
                            seriesSeasonIdByAccountData = idByAccountData,
                            seriesIdByAccount = serieDetail.idByAccountData,
                            seasonNumber = seasonNumber,
                            playlistId = account.id,
                            seasonsName = apiSeason.name,
                            seriesCmd = apiSeason.cmd,
                            rating_imdb = apiSeason.rating_im,
                            screenshot_uri = apiSeason.screenshot_uri,
                            backdropPath = apiSeason.screenshot_uri,
                            description = apiSeason.description,
                            tmdb_id = apiSeason.tmdb_id,
                            episodesCount = apiSeason.series,
                            isSeasonFullyWatched = false,
                            isSeasonPartlyWatched = false,
                            seasonPercentagePlayed = 0.0
                        ).apply {
                            serie.target = serieDetail
                        }
                    }.toMutableList()
                    val newEpisodes = getAllEpisodes(newSeasons, serieDetail, account)
                    if (existingSerieInDB) {
                        if (serieDetail.totalSeasons < newSeasons.size) {
                            serieDetail.newSeasons = true
                        }
                        if (serieDetail.totalEpisodes < newEpisodes.size) {
                            serieDetail.newEpisodes = true
                            serieDetail.totalEpisodes = newEpisodes.size
                            if (serieDetail.isCompletelyWatched) {
                                serieDetail.isCompletelyWatched = false
                                serieDetail.isPartlyWatched = true
                            }
                            seriesBox.put(serieDetail)
                        }
                    }

                    seriesCache[serieDetail.idByAccountData] = Pair(newSeasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                        .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE }).toMutableList(), episodesList)
                    seriesDetailData.postValue(newSeasons)

                }
                is Resource.Error -> {
                    seriesDetailData.postValue(mutableListOf()) // Rückgabe einer leeren Liste im Fehlerfall
                }
            }
        }
    }

    var episodesList: MutableList<EpisodesOB> = mutableListOf()

    fun getAllEpisodes(seasons: List<SeasonsOB>, serieDetail: SeriesOB, account: Accounts) : List<EpisodesOB> {
        episodesList.clear()
        val existingEpisodesMap = episodesBox.query(
            EpisodesOB_.seriesIdByAccount.equal(serieDetail.idByAccountData)
        ).build().find().associateBy { it.seriesSeasonEpisodeIdByAccountData }
        seasons.forEach { thisSeason ->
            val episodes = thisSeason.episodesCount.map { episode ->
                val idByAccountData = "${episode}_${thisSeason.seasonNumber}_${serieDetail.seriesId}_${account.id}"
                existingEpisodesMap[idByAccountData] ?: EpisodesOB(
                    seriesSeasonEpisodeIdByAccountData = idByAccountData,
                    seriesSeasonIdByAccount = thisSeason.seriesSeasonIdByAccountData,
                    seriesIdByAccount = serieDetail.idByAccountData,
                    episodeNumber = episode,
                    seasonNumber = thisSeason.seasonNumber,
                    seasonName = thisSeason.seasonsName,
                    seriesName = serieDetail.seriesName,
                    episodeName = "Episode $episode",
                    episodeTime = "",
                    episodeCmd = thisSeason.seriesCmd,
                    episodeImg = "",
                    episodeDescription = "",
                    currentPosition = 0,
                    isEpisodeFullyWatched = false,
                    isEpisodePartlyWatched = false,
                    episodePercentagePlayed = 0.0,
                    containerExtension = ""
                ).apply {
                    thisSeason.episodes.add(this)
                }
            }
            episodesList.addAll(episodes)
        }
        return episodesList.toList()
    }

    fun searchSeriesByCategory(
        account: Accounts,
        categoryId: String,
        searchTerm: String): Deferred<Set<SeriesOB>> = viewModelScope.async{
        val response = stalkerRepository.searchSeriesByCategory(
            account.stalkerUrl,
            "mac=${account.macAddress}; stb_lang=de; timezone=${account.timezone};",
            "Bearer ${account.token}",
            categoryId,
            1,
            account.userAgent,
            searchTerm
        )
        when (response) {
            is Resource.Success -> {
                val searchSeriesList: MutableSet<SeriesOB> = mutableSetOf()
                val currentSeriesMap: Map<String, SeriesOB> = seriesBox.query(
                    SeriesOB_.accountId.equal(account.id).and(SeriesOB_.relatedSeriesCategoryId.equal(categoryId))
                ).build().find().associateBy { it.idByAccountData }

                val maxItemsPerPage =  response.data.js.max_page_items
                val totalItems = response.data.js.total_items
                val totalPages = ceil(totalItems.toDouble() / maxItemsPerPage.toDouble()).toInt()
                val series = response.data.js.data.map { seriesData ->
                    val idByAccountData = "${seriesData.id}_${account.id}"
                    currentSeriesMap[idByAccountData] ?: convertToSeriesOB(seriesData, account)
                }
                searchSeriesList.addAll(series)
                if (totalPages > 1) {
                    val pagesToCheck = createSequentialList(2, totalPages)
                    for (page in pagesToCheck) {
                        val newresponse = stalkerRepository.searchSeriesByCategory(
                            account.stalkerUrl,
                            "mac=${account.macAddress}; stb_lang=de; timezone=${account.timezone};",
                            "Bearer ${account.token}",
                            categoryId,
                            page,
                            account.userAgent,
                            searchTerm
                        )
                        when (newresponse) {
                            is Resource.Success -> {
                                val newseries = newresponse.data.js.data.map { seriesData ->
                                    val idByAccountData = "${seriesData.id}_${account.id}"
                                    currentSeriesMap[idByAccountData] ?: convertToSeriesOB(seriesData, account)
                                }
                                searchSeriesList.addAll(newseries)
                            }
                            is Resource.Error -> {

                            }
                        }
                    }
                }
                searchSeriesList
            }
            is Resource.Error -> {
                emptySet()
            }
        }
    }



    private fun convertToSeriesOB(seriesData: com.example.mj_player_tv.network.model.stalker.series.SeriesData, account: Accounts): SeriesOB {
        return SeriesOB(
            id = 0, // Erst speichern, wenn notwendig
            idByAccountData = "${seriesData.id}_${account.id}",
            seriesId = seriesData.id ?: "",
            relatedSeriesCategoryId = seriesData.category_id ?: "",
            accountName = account.name,
            accountId = account.id,
            seriesName = seriesData.name ?: "",
            seriesCmd = seriesData.cmd ?: "",
            seriesTime = seriesData.time?.toIntOrNull(),
            seriesYear = seriesData.year ?: "",
            rate = seriesData.rate ?: "",
            rating_imdb = seriesData.rating_imdb ?: "",
            screenshot_uri = seriesData.screenshot_uri ?: "",
            genres_str = seriesData.genres_str ?: "",
            actors = seriesData.actors ?: "",
            added = toUnixTimestamp(seriesData.added) ?: "",
            age = seriesData.age ?: "",
            description = seriesData.description ?: "",
            director = seriesData.director ?: "",
            tmdb_id = seriesData.tmdb_id ?: "",
            o_name = seriesData.o_name ?: "",
            currentPosition = 0L, // Platzhalter für aktuelle Position
            isFavorite = false,
            isCompletelyWatched = seriesData.isFullyWatched,
            isPartlyWatched = seriesData.isPartlyWatched,
            seriesPercentagePlayed = 0.0, // Platzhalter für Prozentwert
            lastWatchedSeason = 1,
            lastWatchedEpisode = 1,
            totalSeasons = 0,
            totalEpisodes = 0,
            newSeasons = false,
            newEpisodes = false
        )
    }

    fun toUnixTimestamp(dateString: String?): String? {
        if (dateString.isNullOrBlank()) return null // Rückgabe "0" falls `null` oder leer
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            LocalDateTime.parse(dateString, formatter).toEpochSecond(ZoneOffset.UTC).toString()
        } catch (e: Exception) {
            "0" // Rückgabe "0" falls das Datum ungültig ist
        }
    }

    fun getSeriesLink(
        url: String,
        cmd: String,
        series: String,
        cookie: String,
        token: String,
        userAgent: String
    ): Deferred<Resource<String?>> = viewModelScope.async {
        val response = stalkerRepository.getSeriesLink(url, cmd, series, cookie, token, userAgent)
        when (response) {
            is Resource.Success -> {
                val seriesUrl = response.data?.js?.cmd
                Resource.Success(seriesUrl)
            }

            is Resource.Error -> {
                Resource.Error(response.message)
            }
        }
    }

    private fun updatePrefixesAndSuffixeMovieCategoriesForAddedPlaylist(prefixes: List<String>?, suffixes: List<String>?, moviecategories: List<MovieCategoryOB>): List<MovieCategoryOB> {
        viewModelScope.launch {
            moviecategories.map { moviecategory ->
                var modifiedName = moviecategory.editedName
                if (!prefixes.isNullOrEmpty()) {
                    prefixes.forEach { prefix ->
                        if (modifiedName.startsWith(prefix)) {
                            modifiedName = modifiedName.removePrefix(prefix).trim()
                        }
                    }
                }
                if (!suffixes.isNullOrEmpty()) {
                    suffixes.forEach { suffix ->
                        if (modifiedName.endsWith(suffix)) {
                            modifiedName = modifiedName.removeSuffix(suffix).trim()
                        }
                    }
                }
                moviecategory.showingName = modifiedName
            }
            // Update the edited channels in the database
        }
        return moviecategories
    }

    private fun updatePrefixesAndSuffixeSeriesCategoriesForAddedPlaylist(prefixes: List<String>?, suffixes: List<String>?, seriescategories: List<SeriesCategoryOB>): List<SeriesCategoryOB> {
        viewModelScope.launch {
            seriescategories.map { seriescategory ->
                var modifiedName = seriescategory.editedName
                if (!prefixes.isNullOrEmpty()) {
                    prefixes.forEach { prefix ->
                        if (modifiedName.startsWith(prefix)) {
                            modifiedName = modifiedName.removePrefix(prefix).trim()
                        }
                    }
                }
                if (!suffixes.isNullOrEmpty()) {
                    suffixes.forEach { suffix ->
                        if (modifiedName.endsWith(suffix)) {
                            modifiedName = modifiedName.removeSuffix(suffix).trim()
                        }
                    }
                }
                seriescategory.showingName = modifiedName
            }
            // Update the edited channels in the database
        }
        return seriescategories
    }

    private fun updatePrefixesAndSuffixesForModifiedMovieCategories(prefixes: List<String>?, suffixes: List<String>?, moviecategory: MovieCategoryOB): MovieCategoryOB {
        var modifiedName = moviecategory.editedName
        if (!prefixes.isNullOrEmpty()) {
            prefixes.forEach { prefix ->
                if (modifiedName.startsWith(prefix)) {
                    modifiedName = modifiedName.removePrefix(prefix).trim()
                }
            }
        }
        if (!suffixes.isNullOrEmpty()) {
            suffixes.forEach { suffix ->
                if (modifiedName.endsWith(suffix)) {
                    modifiedName = modifiedName.removeSuffix(suffix).trim()
                }
            }
        }
        moviecategory.showingName = modifiedName
        return moviecategory
    }

    private fun updatePrefixesAndSuffixesForModifiedSeriesCategories(prefixes: List<String>?, suffixes: List<String>?, seriesCategory: SeriesCategoryOB): SeriesCategoryOB {
        var modifiedName = seriesCategory.editedName
        if (!prefixes.isNullOrEmpty()) {
            prefixes.forEach { prefix ->
                if (modifiedName.startsWith(prefix)) {
                    modifiedName = modifiedName.removePrefix(prefix).trim()
                }
            }
        }
        if (!suffixes.isNullOrEmpty()) {
            suffixes.forEach { suffix ->
                if (modifiedName.endsWith(suffix)) {
                    modifiedName = modifiedName.removeSuffix(suffix).trim()
                }
            }
        }
        seriesCategory.showingName = modifiedName
        return seriesCategory
    }

    fun getTvChannelLink(
        url: String,
        cmd: String,
        cookie: String,
        token: String,
        userAgent: String
    ): Deferred<Resource<String?>> = viewModelScope.async {
        val response = stalkerRepository.getTvChannelLink(url, cmd, cookie, token, userAgent)
        when (response) {
            is Resource.Success -> {
                val channelUrl = response.data?.js?.cmd
                Resource.Success("$channelUrl")
            }

            is Resource.Error -> {
                Resource.Error(response.message)
            }
        }
    }

    fun getTvCatchupLink(
        url: String,
        cmd: String,
        cookie: String,
        token: String,
        userAgent: String
    ): Deferred<Resource<String?>> = viewModelScope.async {
        val response = stalkerRepository.getTvCatchupLink(url, cmd, cookie, token, userAgent)
        when (response) {
            is Resource.Success -> {
                val catchupUrl = response.data?.js?.cmd
                Resource.Success("$catchupUrl")
            }
            is Resource.Error -> {
                Resource.Error(response.message)
            }
        }
    }

    fun getAllEpg(
        url: String,
        macAddress: String,
        token: String,
        userAgent: String,
        timeZone: String,
        account: Accounts,
        isUpdating: Boolean
    ): Deferred<Resource<String?>> = viewModelScope.async {
        val thisEpgSource = epgSourceBox.query(EpgSource_.playlistId.equal(account.id)).build().findFirst()
        if (thisEpgSource != null) {
            val response = stalkerRepository.getAllEpg(
                url,
                "mac=${macAddress}; stb_lang=de; timezone=$timeZone;",
                token,
                userAgent,
                account,
                isUpdating,
                thisEpgSource
            )
            when (response) {
                is Resource.Success -> {
                    val currentDate = System.currentTimeMillis() / 1000
                    thisEpgSource.lastUpdatedDate = currentDate
                    thisEpgSource.updateSuccessful = true
                    Resource.Success("OK")
                }

                is Resource.Error -> {
                    val currentDate = System.currentTimeMillis() / 1000
                    thisEpgSource.lastUpdatedDate = currentDate
                    thisEpgSource.updateSuccessful = false
                    Resource.Error(response.message)
                }

                null -> TODO()
            }
        }
        Resource.Error("")
    }


    suspend fun checkChannelsAndShortEpg(channelOB: TvChannelOB): TvChannelOB {
        val currentAccountInUse = channelOB.playlistId?.let { accountBox.get(it) }
        withContext(Dispatchers.IO) {
            if (currentAccountInUse != null) {
                try {
                    val epg = getShortEpgByChannel(currentAccountInUse, channelOB)
                    val currentTime = System.currentTimeMillis() / 1000
                    val currentProgram = epg?.firstOrNull { it.startTimestamp!! < currentTime && it.stopTimestamp!! > currentTime }
                    Log.d("EPG FOR CHANNEL FETCHED", "${channelOB.showingName} = NEWEPG: ${epg?.size} CURRENT: ${currentProgram?.name}")

                    if (!epg.isNullOrEmpty()) {
                        val playlistEpgSource =
                            currentAccountInUse.epgsources.filter { it.isSelected }
                                .find { it.isPlaylistEpg }
                        if (playlistEpgSource != null) {
                            val epgChExists = withContext(Dispatchers.IO) {
                                channelOB.epgChannel?.target?.chEpgId?.let {
                                    EpgSourceChannel_.chEpgId.equal(
                                        it
                                    )
                                }?.let { epgChannelBox.query(it).build().findFirst() }
                            }
                            if (epgChExists != null) {
                                epgDataBox.put(epg)
                                epgChannelBox.put(epgChExists)
                                channelOB.epgChannel?.target = epgChExists
                                channelOB.linkedEpgChannel?.target = epgChExists
                                tvChannelBox.put(channelOB)
                            } else {
                                val newEpgChannel = EpgSourceChannel(
                                    0,
                                    "${playlistEpgSource.id}_${channelOB.channelId}",
                                    "${channelOB.channelId}",
                                    mutableListOf(channelOB.logo),
                                    channelOB.name,
                                    playlistEpgSource.id,
                                    mutableListOf(
                                        channelOB.name,
                                        channelOB.editedName,
                                        channelOB.showingName
                                    ),
                                    false
                                )
                                epgDataBox.put(epg)
                                newEpgChannel.epgsource.target =
                                    playlistEpgSource.relatedepgsource.target
                                epgChannelBox.put(newEpgChannel)
                                channelOB.epgChannel?.target = newEpgChannel
                                channelOB.linkedEpgChannel?.target = newEpgChannel
                                tvChannelBox.put(channelOB)
                            }
                        }
                    }
                    return@withContext channelOB
                } catch (e: Exception) {
                    // Fehlerbehandlung hier (z.B., Loggen, Benutzer informieren)
                    return@withContext null
                }
            } else {
                return@withContext null
            }
        }
        return channelOB
    }


    var fetchEpgDataForDay: Boolean = true

    private val _portalEpgFetchedComplete = MutableLiveData<Int>(2)
    var portalEpgFetchedComplete: LiveData<Int> = _portalEpgFetchedComplete

    fun portalEpgFetchedCompleteCompleteSuccessful() {
        _portalEpgFetchedComplete.postValue(1)
    }

    fun portalEpgFetchedCompleteCompleteReset() {
        _portalEpgFetchedComplete.postValue(2)
    }


    var epgByChannelData: MutableList<EpgDataOB> = mutableListOf()

    var isPortalEpgLoading: Boolean = true

    var epgLoadJob: Job? = null

    var epgByDayJobs: MutableList<Job> = mutableListOf()

    fun getEpgByChannelByDay(
        channel: TvChannelOB,
        date: String
    ) {
        epgLoadJob = viewModelScope.launch {
            epgByDayJobs = mutableListOf()
            epgByChannelData = mutableListOf()
            isPortalEpgLoading = true

            val account = accountBox.get(channel.playlistId!!)
            val epgSource = account!!.epgsources.find { it.isPlaylistEpg }?.relatedepgsource?.target
            val epgChannel =
                if (channel.epgChannel?.target != null) {
                    channel.epgChannel!!.target
                } else {
                    val newEpgChannel = EpgSourceChannel(
                        id = 0,
                        "${epgSource?.id}_${channel.channelId}",
                        "${channel.channelId}",
                        mutableListOf(channel.logo),
                        channel.showingName,
                        epgSource!!.id,
                        mutableListOf(channel.name, channel.editedName, channel.showingName),
                        false
                    )
                    newEpgChannel.epgsource.target = epgSource
                    epgChannelBox.put(newEpgChannel)
                    channel.epgChannel?.target = newEpgChannel
                    tvChannelBox.put(channel)
                    newEpgChannel
                }

            if (epgSource != null) {
                Log.d(
                    "EPGBYPORTALDAY",
                    "CHANNEL: ${channel.showingName} DATUM $date SEITE: 1 gestartet!"
                )

                // Abruf der ersten Seite
                val response = stalkerRepository.getEpgByChannelByDay(
                    account.stalkerUrl,
                    "mac=${account.macAddress}; stb_lang=de; timezone=${account.timezone};",
                    "Bearer ${account.token}",
                    channel.channelId.toString(),
                    date,
                    1,
                    account.userAgent
                )

                when (response) {
                    is Resource.Success -> {
                        if (!response.data.js.data.isNullOrEmpty()) {
                            val maxEpgDataPerPage = response.data.js.max_page_items
                            val totalEpgData = response.data.js.total_items
                            val totalEpgPages =
                                ceil(totalEpgData.toDouble() / maxEpgDataPerPage).toInt()

                            val newEpg = response.data.js.data.map {
                                val dateTimeString = it.time
                                val datum = extractDate(dateTimeString)
                                EpgDataOB(
                                    0,
                                    "${account.id}_${it.ch_id}_${it.start_timestamp}",
                                    it.id,
                                    it.ch_id,
                                    datum,
                                    it.name,
                                    "",
                                    it.descr,
                                    mutableListOf(),
                                    mutableListOf(),
                                    mutableListOf(),
                                    "",
                                    mutableListOf(),
                                    "",
                                    "",
                                    "",
                                    it.t_time,
                                    it.t_time_to,
                                    it.start_timestamp.toLong(),
                                    it.stop_timestamp.toLong(),
                                    it.mark_archive,
                                    account.id.toString(),
                                    epgSource.id.toInt(),
                                    "${epgSource.id}_${it.ch_id}"
                                )
                            }
                            epgByChannelData.addAll(newEpg)

                            if (totalEpgPages > 1) {

                                coroutineScope {
                                    repeat(totalEpgPages - 1) { pageIndex ->
                                        delay(300L * pageIndex) // Starte den Abruf jeder Seite alle 0,3 Sekunden

                                        if (!isPortalEpgLoading) {
                                            return@repeat
                                        }

                                        launch {
                                            epgByChannelByDayRequest(
                                                account,
                                                epgSource,
                                                channel,
                                                date,
                                                pageIndex + 2, // Seite 2 bis totalEpgPages
                                                epgChannel
                                            )
                                        }
                                    }
                                }
                            }

                            epgLoadJob?.cancel()
                            isPortalEpgLoading = false
                            addPortalEpgToDatabase(epgChannel.id, channel)
                            portalEpgFetchedCompleteCompleteSuccessful()
                        }
                    }

                    is Resource.Error -> {
                        isPortalEpgLoading = false // Beende die Schleife im Falle eines Fehlers
                        portalEpgFetchedCompleteCompleteSuccessful()
                    }
                }
            }
        }
    }

    suspend fun epgByChannelByDayRequest(
        account: Accounts,
        epgSource: EpgSource?,
        channel: TvChannelOB,
        date: String,
        page: Int,
        epgChannel: EpgSourceChannel
    ) {
        val response = stalkerRepository.getEpgByChannelByDay(
            account.stalkerUrl,
            "mac=${account.macAddress}; stb_lang=de; timezone=${account.timezone};",
            "Bearer ${account.token}",
            channel.channelId.toString(),
            date,
            page,
            account.userAgent
        )

        when (response) {
            is Resource.Success -> {
                if (!response.data.js.data.isNullOrEmpty()) {
                    val newEpg = response.data.js.data.map {
                        val dateTimeString = it.time
                        val datum = extractDate(dateTimeString)
                        EpgDataOB(
                            0,
                            "${account.id}_${it.ch_id}_${it.start_timestamp}",
                            it.id,
                            it.ch_id,
                            datum,
                            it.name,
                            "",
                            it.descr,
                            mutableListOf(),
                            mutableListOf(),
                            mutableListOf(),
                            "",
                            mutableListOf(),
                            "",
                            "",
                            "",
                            it.t_time,
                            it.t_time_to,
                            it.start_timestamp.toLong(),
                            it.stop_timestamp.toLong(),
                            it.mark_archive,
                            account.id.toString(),
                            epgSource!!.id.toInt(),
                            "${epgSource.id}_${it.ch_id}"
                        )
                    }
                    epgByChannelData.addAll(newEpg)
                }
            }
            is Resource.Error -> {
                isPortalEpgLoading = false
            }
        }
    }

    fun extractDate(timeString: String): String {
        // Definiere das Format des Eingabestrings
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        // Definiere das Ausgabeformat
        val outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        // Parse den Zeitstring in ein LocalDateTime Objekt
        val dateTime = LocalDateTime.parse(timeString, inputFormatter)

        // Extrahiere das LocalDate aus LocalDateTime
        val date = dateTime.toLocalDate()

        // Formatiere das LocalDate Objekt als Datum
        return date.format(outputFormatter)
    }


    fun addPortalEpgToDatabase(epgChId: Long, channel: TvChannelOB) {
        val epgChannel = epgChannelBox.get(epgChId)
        epgDataBox.put(epgByChannelData)
        epgChannelBox.put(epgChannel)
        channel.epgChannel?.target = epgChannel
        tvChannelBox.put(channel)
    }

    //FIND MATCH FOR EXTERNAL EPG:
    var maxEpgDataPerPage: Int? = null
    var totalEpgData: Int? = null
    var totalEpgPages: Int? = null
    val epgListFromServer = mutableListOf<SimpleTableData>()

    suspend fun findEpgMatch(externalEpgEvent: EpgDataOB, channel: TvChannelOB, date: String, currentTvCategory: TvCategoryOB) : Resource<SimpleTableData?> =
        withContext(Dispatchers.IO) {

            val account = channel.playlistId?.let { accountBox.get(it) }

            val response = account?.let {
                stalkerRepository.getEpgByChannelByDay(
                    it.stalkerUrl,
                    "mac=${account.macAddress}; stb_lang=de; timezone=${account.timezone};",
                    "Bearer ${account.token}",
                    channel.channelId.toString(),
                    date,
                    1,
                    account.userAgent
                )
            }
            when (response) {
                is Resource.Success -> {
                    maxEpgDataPerPage = response.data.js.max_page_items
                    totalEpgData = response.data.js.total_items
                    totalEpgPages = ceil(totalEpgData!!.toDouble() / maxEpgDataPerPage!!).toInt()
                    val totalEpgPagesToUse = createSequentialList(1, totalEpgPages!!)
                    for (page in totalEpgPagesToUse) {
                        val newResponse = stalkerRepository.getEpgByChannelByDay(
                            account.stalkerUrl,
                            "mac=${account.macAddress}; stb_lang=de; timezone=${account.timezone};",
                            "Bearer ${account.token}",
                            channel.channelId.toString(),
                            date,
                            page,
                            account.userAgent
                        )
                        when (newResponse) {
                            is Resource.Success -> {
                                epgListFromServer.addAll(newResponse.data.js.data)
                            }
                            is Resource.Error -> {
                                Resource.Error(newResponse.message)
                            }
                        }
                    }
                    val thisEpg = findClosestMatch(externalEpgEvent, epgListFromServer, channel, currentTvCategory)
                    Resource.Success(thisEpg)
                }
                is Resource.Error -> {
                    Log.d("CATCHUPMATCHING", "${response.message}")
                    Resource.Error(response.message)
                }

                null -> TODO()
            }
        }

    private fun findClosestMatch(externalEvent: EpgDataOB, serverEpg: List<SimpleTableData>, currentChannel: TvChannelOB, tvcategory: TvCategoryOB): SimpleTableData? {
        val timeOffSet = currentChannel.epgTimeOffSet ?: tvcategory.epgTimeOffSet ?: currentChannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0
        val timeOffSetSeconds = calculateTimeOffsetInSeconds(timeOffSet)
        var closestMatch: SimpleTableData? = null
        var closestTimeDiff = Long.MAX_VALUE

        for (serverEvent in serverEpg) {
            // Berechne den Zeitunterschied in Millisekunden
            val timeDifference = Math.abs((serverEvent.start_timestamp + timeOffSetSeconds) - (externalEvent.startTimestamp!! + timeOffSetSeconds))
            if (timeDifference < closestTimeDiff) {
                closestMatch = serverEvent
                closestTimeDiff = timeDifference
            }
        }
        return closestMatch
    }

    fun calculateTimeOffsetInSeconds(timeOffset: Int): Long {
        return (timeOffset * 3600).toLong()
    }
}