package com.example.mj_player_tv.viewmodel

import android.app.Application
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.example.mj_player_tv.database.entity.EpgDataOB_
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
import com.example.mj_player_tv.database.entity.SeasonsOB
import com.example.mj_player_tv.database.entity.SeasonsOB_
import com.example.mj_player_tv.database.entity.SeriesCategoryOB
import com.example.mj_player_tv.database.entity.SeriesCategoryOB_
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.database.entity.SeriesOB_
import com.example.mj_player_tv.database.entity.Settings
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvCategoryOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.help.Episode
import com.example.mj_player_tv.database.help.Movie
import com.example.mj_player_tv.database.help.Season
import com.example.mj_player_tv.database.help.Serie
import com.example.mj_player_tv.network.model.stalker.epgforday.SimpleTableData
import com.example.mj_player_tv.network.model.stalker.seriesdetails.SeriesData
import com.example.mj_player_tv.network.model.xtreamcodes.epgbychannel.EpgListings
import com.example.mj_player_tv.network.model.xtreamcodes.epgbychannel.XtreamEpgByChannel
import com.example.mj_player_tv.network.model.xtreamcodes.seriesdetails.XtreamSeriesDetails
import com.example.mj_player_tv.repository.HelpRepository
import com.example.mj_player_tv.repository.PlaylistLoadProcessState
import com.example.mj_player_tv.repository.PlaylistUpdateProcessState
import com.example.mj_player_tv.repository.PlaylistUpdateWorker
import com.example.mj_player_tv.repository.XtreamRepository
import com.example.mj_player_tv.utils.Resource
import io.objectbox.Box
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.concurrent.formatDuration
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class XtreamViewModel(application: Application): AndroidViewModel(application) {
    private val accountBox = ObjectBox.store.boxFor(Accounts::class.java)
    private val tvChannBox: Box<TvChannelOB> = ObjectBox.store.boxFor(TvChannelOB::class.java)
    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)
    private val movieCatBox: Box<MovieCategoryOB> = ObjectBox.store.boxFor(MovieCategoryOB::class.java)
    private val seriesCatBox: Box<SeriesCategoryOB> = ObjectBox.store.boxFor(SeriesCategoryOB::class.java)
    private val epgSourceBox = ObjectBox.store.boxFor(EpgSource::class.java)
    private val epgDataBox = ObjectBox.store.boxFor(EpgDataOB::class.java)
    private val epgChannelBox = ObjectBox.store.boxFor(EpgSourceChannel::class.java)
    private val settingsBox = ObjectBox.store.boxFor(Settings::class.java)
    private val epgSourcePosBox = ObjectBox.store.boxFor(EpgSourcePositions::class.java)
    private val manualPosBox = ObjectBox.store.boxFor(ChannelPositions::class.java)
    private val moviesBox = ObjectBox.store.boxFor(MovieOB::class.java)
    private val seriesBox = ObjectBox.store.boxFor(SeriesOB::class.java)
    private val seasonsBox = ObjectBox.store.boxFor(SeasonsOB::class.java)
    private val episodesBox = ObjectBox.store.boxFor(EpisodesOB::class.java)

    private val xtreamRepository = XtreamRepository()

    var xxxChannelsCount = 0
    var normalChannelsCount = 0
    var currentProcessName = ""

    var episodesList: MutableList<EpisodesOB> = mutableListOf()

    var movieSearchList: MutableList<MovieOB> = mutableListOf()

    var seriesSearchList: MutableList<SeriesOB> = mutableListOf()

    private val _playlistProcessState = MutableStateFlow<PlaylistLoadProcessState?>(null)
    val playlistProcessState: StateFlow<PlaylistLoadProcessState?> = _playlistProcessState


    fun resetPlaylistProcessState() {
        _playlistProcessState.value = null
    }

    var addingAccount: Accounts? = null

    suspend fun getXtreamData(
        url: String,
        username: String,
        password: String,
        name: String,
        userAgent: String
    ) {
        viewModelScope.launch {
            currentProcessName = name
            _playlistProcessState.value = PlaylistLoadProcessState.Loading
            val authResponse =
                getXtreamAuthentication(url, username, password, userAgent, name).await()
            when (authResponse) {
                is Resource.Success -> {
                    val account = addingAccount
                    Log.d("XTREAM LOAD", "THISACCOUNT: ${account}")
                    _playlistProcessState.value = PlaylistLoadProcessState.GetToken(100)
                    if (account != null) {
                        Log.d("XTREAM LOAD", "THISACCOUNT: OK!")
                        val tvcatDeferred = getXtreamTvCategories(account).await()
                        Log.d("XTREAM LOAD", "TVCAT: ${tvcatDeferred.size}")
                        val channelsDeferred = getTvChannels(account).await()
                        Log.d("XTREAM LOAD", "TVCH: ${channelsDeferred.size}")
                        val moviecatDeferred =
                            getXtreamMovieCategories(account).await()
                        Log.d("XTREAM LOAD", "MOVIE: ${moviecatDeferred.size}")
                        val seriescatDeferred =
                            getXtreamSeriesCategories(account).await()
                        Log.d("XTREAM LOAD", "SERIES: ${seriescatDeferred.size}")

                        if (tvcatDeferred.isEmpty() && moviecatDeferred
                                .isEmpty() && seriescatDeferred.isEmpty() && channelsDeferred.isEmpty()
                        ) {
                            _playlistProcessState.value =
                                PlaylistLoadProcessState.Error("No Playlist Data found!")
                            val epgSource =
                                epgSourceBox.query(EpgSource_.playlistId.equal(account.id)).build()
                                    .findFirst()
                            if (epgSource != null) {
                                val epgPosition = epgSourcePosBox.query(
                                    EpgSourcePositions_.accountEpgSourceUnique.equal("${account.id}_${epgSource.url}")
                                ).build().findFirst()
                                if (epgPosition != null) {
                                    epgSourcePosBox.remove(epgPosition)
                                }
                                epgSourceBox.remove(epgSource.id)
                            }
                            accountBox.remove(account.id)
                        } else {
                            _playlistProcessState.value = PlaylistLoadProcessState.GetChannels(
                                100,
                                (xxxChannelsCount + normalChannelsCount).toString()
                            )
                            addChannelsToTvCategory(account.id)
                            if (tvcatDeferred.isEmpty()) {
                                account.tvchannelLoadingOK = 0
                            } else {
                                account.tvchannelLoadingOK = 1
                            }
                            if (moviecatDeferred.isEmpty()) {
                                account.movieCategoryLoadingOK = 0
                            } else {
                                account.movieCategoryLoadingOK = 1
                            }
                            if (seriescatDeferred.isEmpty()) {
                                account.seriesCategoryLoadingOK = 0
                            } else {
                                account.seriesCategoryLoadingOK = 1
                            }
                            if (channelsDeferred.isEmpty()) {
                                account.tvchannelLoadingOK = 0
                            } else {
                                account.tvchannelLoadingOK = 1
                            }
                            if (channelsDeferred.isEmpty() && seriescatDeferred.isEmpty() && moviecatDeferred.isEmpty() && tvcatDeferred.isEmpty()) {
                                account.lastUpdateStatus = 0
                            } else {
                                if (channelsDeferred.isEmpty() || seriescatDeferred.isEmpty() || moviecatDeferred.isEmpty() || tvcatDeferred.isEmpty()) {
                                    account.lastUpdateStatus = 2
                                } else {
                                    account.lastUpdateStatus = 1
                                }
                            }
                            val currentDate = System.currentTimeMillis() / 1000
                            account.lastUpdatedDate = currentDate
                            accountBox.put(account)
                            setWorker(account)
                            _playlistProcessState.value =
                                PlaylistLoadProcessState.Success(account)
                        }
                    } else {
                        Log.d("XTREAM LOAD", "THISACCOUNT: NOT OK!")
                    }
                }

                is Resource.Error -> {
                    Log.d("AUTHRESPOnse", "ERROR")
                    _playlistProcessState.value = PlaylistLoadProcessState.Error(authResponse.message)
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


    var currentUpdatingPlaylistId: Long? = null

    private val _playlistUpdateProcessState = MutableStateFlow<PlaylistUpdateProcessState?>(null)
    val playlistUpdateProcessState: StateFlow<PlaylistUpdateProcessState?> = _playlistUpdateProcessState

    fun resetPlaylistUpdateProcessState() {
        _playlistUpdateProcessState.value = null
    }

    suspend fun updateXtreamData(
        account: Accounts
    ) {
        viewModelScope.launch {
            currentUpdatingPlaylistId = account.id
            _playlistUpdateProcessState.value = PlaylistUpdateProcessState.CurrentAccount(account.name)
            val authResponse =
                updateXtreamAuthentication(account).await()
            when (authResponse) {
                is Resource.Success -> {
                    val tvcatDeferred = updateXtreamTvCategories(account).await()
                    val channelsDeferred = updateTvChannels(account).await()
                    val moviecatDeferred =
                        updateXtreamMovieCategories(account).await()
                    val seriescatDeferred =
                        updateXtreamSeriesCategories(account).await()
                    if (tvcatDeferred.isEmpty()) {
                        account.tvchannelLoadingOK = 0
                    } else {
                        account.tvchannelLoadingOK = 1
                    }
                    if (channelsDeferred.isEmpty()) {
                        account.tvchannelLoadingOK = 0
                    } else {
                        account.tvchannelLoadingOK = 1
                    }
                    if (moviecatDeferred.isEmpty()) {
                        account.movieCategoryLoadingOK = 0
                    } else {
                        account.movieCategoryLoadingOK = 1
                    }
                    if (seriescatDeferred.isEmpty()) {
                        account.seriesCategoryLoadingOK = 0
                    } else {
                        account.seriesCategoryLoadingOK = 1
                    }
                    if (channelsDeferred.isEmpty() && seriescatDeferred.isEmpty() && moviecatDeferred.isEmpty() && channelsDeferred.isEmpty()) {
                        account.lastUpdateStatus = 0
                    } else {
                        if (channelsDeferred.isEmpty() || seriescatDeferred.isEmpty() || moviecatDeferred.isEmpty() || tvcatDeferred.isEmpty()) {
                            account.lastUpdateStatus = 2
                        } else {
                            account.lastUpdateStatus = 1
                        }
                    }
                    val currentDate = System.currentTimeMillis() / 1000
                    account.lastUpdatedDate = currentDate
                    accountBox.put(account)
                    currentUpdatingPlaylistId = null
                    _playlistUpdateProcessState.value = PlaylistUpdateProcessState.Success
                }
                is Resource.Error -> {
                    val currentDate = System.currentTimeMillis() / 1000
                    account.tvchannelLoadingOK = 0
                    account.tvCategoryLoadingOK = 0
                    account.movieCategoryLoadingOK = 0
                    account.seriesCategoryLoadingOK = 0
                    account.lastUpdatedDate = currentDate
                    account.lastUpdateStatus = 0
                    accountBox.put(account)
                    currentUpdatingPlaylistId = null
                    _playlistUpdateProcessState.value = PlaylistUpdateProcessState.Error
                }
            }
        }
    }

    fun getXtreamAuthentication(
        url: String,
        username: String,
        password: String,
        userAgent: String,
        name: String
    ): Deferred<Resource<String>> = viewModelScope.async {
        val response = xtreamRepository.getXtreamAuthentication(
            url,
            username,
            password,
            userAgent
        )
        when (response) {
            is Resource.Success -> {
                val currentDate = System.currentTimeMillis() / 1000
                val useDefault =
                    if (response.data.user_info.allowed_output_formats.contains("ts")) {
                        true
                    } else {
                        false
                    }
                val newAccountData = Accounts(
                    0,
                    name,
                    url,
                    username,
                    password,
                    "",
                    "$url$username$password",
                    userAgent,
                    true,
                    response.data.user_info.exp_date ?: "",
                    response.data.server_info.timezone,
                    true,
                    false,
                    false,
                    false,
                    true,
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
                    response.data.user_info.allowed_output_formats.toMutableList(),
                    useDefault,
                    ""
                )
                // Den neuen Account in die Datenbank einfügen
                accountBox.put(newAccountData)

                // Erneut den neuen Account aus der Datenbank laden, um die generierte ID zu erhalten
                val thisAccount = accountBox[newAccountData.id]
                val epgSource = EpgSource(
                    0,
                    thisAccount.name,
                    thisAccount.id.toString(),
                    thisAccount.id,
                    false,
                    false,
                    true,
                    false,
                    true,
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
                        "${thisAccount.id}_${thisEpgSource.url}"
                    )
                    newEpgPosition.relatedepgsource.target = epgSource
                    newEpgPosition.relatedaccount.target = thisAccount
                    epgSourcePosBox.put(newEpgPosition)
                }
                accountBox.put(thisAccount)
                addExternalEpgToAccount(thisAccount)
                addingAccount = thisAccount
                Resource.Success("OK")
            }

            is Resource.Error -> {
                Resource.Error(response.message)
            }
        }
    }


    fun updateXtreamAuthentication(
        account: Accounts
    ): Deferred<Resource<Accounts>> = viewModelScope.async {
        val response = xtreamRepository.getXtreamAuthentication(
            account.stalkerUrl,
            account.username,
            account.macAddress,
            account.userAgent
        )
        when (response) {
            is Resource.Success -> {
                val currentDate = System.currentTimeMillis() / 1000
                val useDefault =
                    if (response.data.user_info.allowed_output_formats.contains("ts")) {
                        true
                    } else {
                        false
                    }
                account.expiryDate = response.data.user_info.exp_date ?: ""
                account.timezone = response.data.server_info.timezone
                account.lastUpdatedDate = currentDate
                if (account.xtreamUseDefaultType && useDefault) {
                    account.xtreamUseDefaultType = true
                } else {
                    if (!useDefault) {
                        if (account.xtreamUseDefaultType) {
                            account.xtreamUseDefaultType = false
                        }
                    }
                }
                account.xtreamOutPutFormats = response.data.user_info.allowed_output_formats.toMutableList()

                accountBox.put(account)

                Resource.Success(account)
            }

            is Resource.Error -> {
                Resource.Error(response.message)
            }
        }
    }

    fun getXtreamTvCategories(
        account: Accounts
    ): Deferred<List<TvCategoryOB>> = viewModelScope.async {
        _playlistProcessState.value = PlaylistLoadProcessState.GetTvCategories(
            1,
            "Load Tv Categories.."
        )
        val response = xtreamRepository.getXtreamTvCategories(
            account.stalkerUrl,
            account.username,
            account.macAddress,
            account.userAgent
        )
        when (response) {
            is Resource.Success -> {
                val categories = response.data.mapIndexed { index, tvcatresponse ->
                    val thisCategory = TvCategoryOB(
                        0,
                        account.id,
                        tvcatresponse.category_id ?: "",
                        index,
                        tvcatresponse.parent_id,
                        tvcatresponse.category_name ?: "",
                        tvcatresponse.category_name ?: "",
                        tvcatresponse.category_name ?: "",
                        accountData = account.name,
                        favorite = false,
                        0,
                        idByAccountData = "${tvcatresponse.category_id}_${account.id}",
                        false,
                        false,
                        null,
                        null,
                        false,
                        isAllChannelsCategory = if (tvcatresponse.category_id == "*") {
                            true
                        } else {
                            false
                        }
                    )
                    thisCategory.tvaccount.target = account
                    thisCategory
                }
                val settings = settingsBox.all.first()
                if (!categories.isNullOrEmpty() && settings != null) {
                    val favoriteCategory = TvCategoryOB(
                        0,
                        account.id,
                        "FAVORITE_${account.id}",
                        null,
                        0,
                        "Favorites",
                        "Favorites",
                        "Favorites",
                        account.name,
                        false,
                        0,
                        "FAVORITE_${account.id}",
                        false,
                        false,
                        null,
                        null,
                        true,
                        false
                    )
                    favoriteCategory.tvaccount.target = account
                    tvCatBox.put(favoriteCategory)
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
                    account.totalMovieCategories = categoriesToAdd.size.toString()
                    accountBox.put(account)
                    tvCatBox.put(categoriesToAdd)
                    val censoredCategories = categoriesToAdd.filter { it.censored == 1 }
                    if (censoredCategories.isNotEmpty()) {
                        addCensoredChannelsToTvCategory(
                            censoredCategories,
                            account,
                            account.name,
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
                Log.d("XTREAM GET ACCOUNTDATA", "TV ERROR: ${response.message}")
                _playlistProcessState.value = PlaylistLoadProcessState.TvError(response.message)
                emptyList()
            }
        }
    }

    fun updateXtreamTvCategories(
        account: Accounts
    ): Deferred<List<TvCategoryOB>> = viewModelScope.async {
        val response = xtreamRepository.getXtreamTvCategories(
            account.stalkerUrl,
            account.username,
            account.macAddress,
            account.userAgent
        )
        when (response) {
            is Resource.Success -> {
                val networkCategories = response.data.mapIndexed { index, tvcatresponse ->
                    val thisCategory = TvCategoryOB(
                        0,
                        account.id,
                        tvcatresponse.category_id ?: "",
                        index,
                        tvcatresponse.parent_id,
                        tvcatresponse.category_name ?: "",
                        tvcatresponse.category_name ?: "",
                        tvcatresponse.category_name ?: "",
                        accountData = account.name,
                        favorite = false,
                        0,
                        idByAccountData = "${tvcatresponse.category_id}_${account.id}",
                        false,
                        false,
                        null,
                        null,
                        false,
                        isAllChannelsCategory = if (tvcatresponse.category_id == "*") {
                            true
                        } else {
                            false
                        }
                    )
                    thisCategory.tvaccount.target = account
                    thisCategory
                }
                updateCategoriesInDatabase(account, networkCategories)
                networkCategories
            }
            is Resource.Error -> {
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

                    tvCatBox.put(newCategories)
                    val censoredCategories = newCategories.filter { it.censored == 1 }
                    addCensoredChannelsToTvCategory(censoredCategories, accountData, accountData.name, settings)
                }
                if (categoriesToDelete.isNotEmpty()) {

                    tvCatBox.remove(categoriesToDelete)
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

                tvCatBox.put(updatedCategories)

                updatedCategories.forEach {
                    Log.d("UPDATE CAT: UPDATE", "${accountData.name} = ${it.showingName} POS-SIZE: ${it.tvChannelLink.size}")
                }

                accountData.totalTvCategories = networkCategories.size.toString()
                accountBox.put(accountData)
            }
        }
    }

    fun getXtreamMovieCategories(
        account: Accounts
    ): Deferred<List<MovieCategoryOB>> = viewModelScope.async {
        _playlistProcessState.value = PlaylistLoadProcessState.GetMovieCategories(
            1,
            "Load Movies Categories..",
            ""
        )
        val response = xtreamRepository.getXtreamMovieCategories(
            account.stalkerUrl,
            account.username,
            account.macAddress,
            account.userAgent
        )
        when (response) {
            is Resource.Success -> {
                val categories = response.data.map { moviecatresponse ->
                    val thisCategory = MovieCategoryOB(
                        0,
                        account.id,
                        moviecatresponse.category_id ?: "",
                        moviecatresponse.category_name  ?: "",
                        moviecatresponse.category_name  ?: "",
                        moviecatresponse.category_name  ?: "",
                        accountData = account.name,
                        favorite = false,
                        null,
                        idByAccountData = "${moviecatresponse.category_id}_${account.id}",
                        false
                    )
                    thisCategory.movieaccount.target = account
                    thisCategory
                }
                if (categories.isNotEmpty()) {
                    val settings = settingsBox.all.first()
                    if (settings != null) {
                        val categoriesToAdd =
                            if (settings.moviecategoryPrefixes.isNotEmpty() || settings.moviecategorySuffixes.isNotEmpty()) {
                                val updatedMovieCategoriesList =
                                    updatePrefixesAndSuffixeMovieCategoriesForAddedPlaylist(
                                        settings.moviecategoryPrefixes,
                                        settings.moviecategorySuffixes,
                                        categories
                                    )
                                updatedMovieCategoriesList
                            } else {
                                categories
                            }
                        movieCatBox.put(categoriesToAdd)
                        account.totalMovieCategories = categoriesToAdd.size.toString()
                        accountBox.put(account)
                        val allMovies = xtreamRepository.getXtreamAllMovies(
                            account
                        )
                        when (allMovies) {
                            is Resource.Success -> {
                                Log.d("XTREAM TOTAL MOVIES SIZE", "SIZE: ${allMovies.data.size}")
                                _playlistProcessState.value =
                                    PlaylistLoadProcessState.GetMovieCategories(
                                        100,
                                        categories.size.toString(),
                                        allMovies.data.size.toString()
                                    )
                            }

                            is Resource.Error -> {
                                Log.d("XTREAM GET ACCOUNTDATA", "ALL MOVIES: ${allMovies.message}")

                                _playlistProcessState.value =
                                    PlaylistLoadProcessState.GetMovieCategories(
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
                        PlaylistLoadProcessState.MovieError("Error: No movie categories found!")
                    emptyList()
                }
            }

            is Resource.Error -> {
                Log.d("XTREAM GET ACCOUNTDATA", "MOVIES ERROR: ${response.message}")
                emptyList()
            }
        }
    }

    fun updateXtreamMovieCategories(
        account: Accounts
    ): Deferred<List<MovieCategoryOB>> = viewModelScope.async {
        val response = xtreamRepository.getXtreamMovieCategories(
            account.stalkerUrl,
            account.username,
            account.macAddress,
            account.userAgent
        )
        when (response) {
            is Resource.Success -> {
                val newCategories = response.data.map { moviecatresponse ->
                    val thisCategory = MovieCategoryOB(
                        0,
                        account.id,
                        moviecatresponse.category_id ?: "",
                        moviecatresponse.category_name ?: "",
                        moviecatresponse.category_name ?: "",
                        moviecatresponse.category_name ?: "",
                        accountData = account.name,
                        favorite = false,
                        null,
                        idByAccountData = "${moviecatresponse.category_id}_${account.id}",
                        false
                    )
                    thisCategory.movieaccount.target = account
                    thisCategory
                }
                updateMovieCategoriesInDatabase(account, newCategories)
                newCategories
            }

            is Resource.Error -> emptyList()
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
                val currentMovieCategoriesQuery = movieCatBox
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
                    movieCatBox.put(newCategories)
                    accountBox.put(accountData)
                }
                if (categoriesToDelete.isNotEmpty()) {
                    movieCatBox.remove(categoriesToDelete)
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
                movieCatBox.put(updatedCategories)
                accountData.totalMovieCategories = networkCategories.size.toString()
                accountBox.put(accountData)
            }
        }
    }

    fun getXtreamSeriesCategories(
        account: Accounts
    ): Deferred<List<SeriesCategoryOB>> = viewModelScope.async {
        _playlistProcessState.value = PlaylistLoadProcessState.GetSeriesCategories(
            1,
            "Load Series Categories..",
            ""
        )
        val response = xtreamRepository.getXtreamSeriesCategories(
            account.stalkerUrl,
            account.username,
            account.macAddress,
            account.userAgent
        )
        when (response) {
            is Resource.Success -> {
                val categories = response.data.map { seriescatresponse ->
                    val thisCategory = SeriesCategoryOB(
                        0,
                        account.id,
                        seriescatresponse.category_id ?: "",
                        seriescatresponse.category_name ?: "",
                        seriescatresponse.category_name ?: "",
                        seriescatresponse.category_name ?: "",
                        accountData = account.name,
                        favorite = false,
                        idByAccountData = "${seriescatresponse.category_id}_${account.id}",
                        false
                    )
                    thisCategory.seriesaccount.target = account
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
                        seriesCatBox.put(categoriesToAdd)
                        account.totalSeriesCategories = categoriesToAdd.size.toString()
                        accountBox.put(account)
                        val allSeries = xtreamRepository.getXtreamAllSeries(
                            account
                        )
                        when (allSeries) {
                            is Resource.Success -> {
                                _playlistProcessState.value =
                                    PlaylistLoadProcessState.GetSeriesCategories(
                                        100,
                                        categories.size.toString(),
                                        allSeries.data.size.toString()
                                    )
                            }

                            is Resource.Error -> {
                                Log.d("XTREAM GET ACCOUNTDATA", "ALL SERIES: ${allSeries.message}")

                                PlaylistLoadProcessState.GetSeriesCategories(
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
                Log.d("XTREAM GET ACCOUNTDATA", "SERIES ERROR: ${response.message}")
                _playlistProcessState.value =
                    PlaylistLoadProcessState.SeriesError(response.message)
                emptyList()
            }
        }
    }

    fun updateXtreamSeriesCategories(
        account: Accounts
    ): Deferred<List<SeriesCategoryOB>> = viewModelScope.async {

        val response = xtreamRepository.getXtreamSeriesCategories(
            account.stalkerUrl,
            account.username,
            account.macAddress,
            account.userAgent
        )
        when (response) {
            is Resource.Success -> {
                val newCategories = response.data.map { seriescatresponse ->
                    val thisCategory = SeriesCategoryOB(
                        0,
                        account.id,
                        seriescatresponse.category_id ?: "",
                        seriescatresponse.category_name ?: "",
                        seriescatresponse.category_name ?: "",
                        seriescatresponse.category_name ?: "",
                        accountData = account.name,
                        favorite = false,
                        idByAccountData = "${seriescatresponse.category_id}_${account.id}",
                        false
                    )
                    thisCategory.seriesaccount.target = account
                    thisCategory
                }
                updateSeriesCategoriesInDatabase(account, newCategories)
                newCategories
            }
            is Resource.Error -> {
                _playlistProcessState.value =
                    PlaylistLoadProcessState.SeriesError(response.message)
                emptyList()
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
                val currentSeriesCategoriesQuery = seriesCatBox
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
                    seriesCatBox.put(newCategories)
                    accountBox.put(accountData)
                }
                if (categoriesToDelete.isNotEmpty()) {
                    seriesCatBox.remove(categoriesToDelete)
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

                seriesCatBox.put(updatedCategories)
                accountData.totalSeriesCategories = networkCategories.size.toString()
                accountBox.put(accountData)
            }
        }
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
        account: Accounts
    ): Deferred<List<TvChannelOB>> = viewModelScope.async {
        _playlistProcessState.value = PlaylistLoadProcessState.GetChannels(
            1,
            "Load Tv Channels.."
        )
        val thisEpgSource =
            epgSourceBox.query(EpgSource_.playlistId.equal(account.id)).build().findFirst()
        val response = xtreamRepository.getXtreamAllChannels(
            account.stalkerUrl,
            account.username,
            account.macAddress,
            account.userAgent
        )
        when (response) {
            is Resource.Success -> {
                val groupedChannels = response.data.groupBy { it.category_id }
                val channelList = groupedChannels.flatMap { (genreId, channels) ->
                    channels.mapIndexed { index, it ->
                        val thisChannel = TvChannelOB(
                            0,
                            account.id,
                            it.stream_id,
                            it.num.toString(),
                            "",
                            it.stream_icon ?: "",
                            "",
                            it.category_id?.toIntOrNull() ?: 0,
                            "${it.category_id}_${account.id}",
                            it.name ?: "",
                            it.name ?: "",
                            it.name ?: "",
                            it.epg_channel_id?.lowercase() ?: "",
                            it.tv_archive,
                            it.tv_archive_duration,
                            it.tv_archive,
                            account.name,
                            "${it.stream_id}_${account.id}",
                            thisEpgSource?.id,
                            true,
                            false,
                            false,
                            0L,
                            false,
                            null,
                            "${it.category_id}_${account.id}"
                        )
                        thisChannel.account.target = account
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
                            account.totalTvChannels = updatedChannelList.size.toString()
                            accountBox.put(account)
                            tvChannBox.put(updatedChannelList)
                            _playlistProcessState.value =
                                PlaylistLoadProcessState.GetChannels(
                                    100,
                                    updatedChannelList.size.toString()
                                )
                            updatedChannelList
                        } else {
                            tvChannBox.put(channelList)
                            account.totalTvChannels = channelList.size.toString()
                            accountBox.put(account)
                            normalChannelsCount = channelList.size
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
                Log.d("XTREAM GET ACCOUNTDATA", "CHANNELS ERROR: ${response.message}")
                _playlistProcessState.value =
                    PlaylistLoadProcessState.ChannelsError(response.message)
                emptyList()
            }
        }
    }

    fun updateTvChannels(
        account: Accounts
    ): Deferred<List<TvChannelOB>> = viewModelScope.async {
        val thisEpgSource =
            epgSourceBox.query(EpgSource_.playlistId.equal(account.id)).build().findFirst()
        val response = xtreamRepository.getXtreamAllChannels(
            account.stalkerUrl,
            account.username,
            account.macAddress,
            account.userAgent
        )
        when (response) {
            is Resource.Success -> {
                val groupedChannels = response.data.groupBy { it.category_id }
                val networkChannels = groupedChannels.flatMap { (genreId, channels) ->
                    channels.mapIndexed { index, it ->
                        val thisChannel = TvChannelOB(
                            0,
                            account.id,
                            it.stream_id,
                            it.num.toString(),
                            "",
                            it.stream_icon ?: "",
                            "",
                            it.category_id?.toIntOrNull() ?: 0,
                            "${it.category_id}_${account.id}",
                            it.name ?: "",
                            it.name ?: "",
                            it.name ?: "",
                            it.epg_channel_id?.lowercase() ?: "",
                            it.tv_archive,
                            it.tv_archive_duration,
                            it.tv_archive,
                            account.name,
                            "${it.stream_id}_${account.id}",
                            thisEpgSource?.id,
                            true,
                            false,
                            false,
                            0L,
                            false,
                            null,
                            "${it.category_id}_${account.id}"
                        )
                        thisChannel.account.target = account
                        thisChannel
                    }
                }
                updateTvChannelsInDatabase(account, networkChannels)
                networkChannels
            }
            is Resource.Error -> {
                emptyList()
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
                            manualPosBox.put(positionsToChange)
                            // Reduziere die Originalpositionen um 1 für alle Channels, die nach der zu löschenden Originalposition kommen
                            val originalPositionsToChange = allPositionsForThisCategory.filter { it.originalPosition > oldOriginalPosition }.map { changeOriginalPosition ->
                                changeOriginalPosition.originalPosition -= 1
                                changeOriginalPosition // Speichere die neue Originalposition
                            }
                            manualPosBox.put(originalPositionsToChange)
                        }

                        // Entferne die gefundenen ChannelPosition-Einträge
                        manualPosBox.remove(channelPositionsToDelete)
                    }
                    tvChannBox.remove(channelsToDelete)
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
                                manualPosBox.put(oldPositions)
                                manualPosBox.remove(oldChPos)
                            }
                            changedCategoryChannels.add(channel)
                        }
                    }
                }
                tvChannBox.put(currentChannels)

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

    fun addNewChannelsToTvCategory(account: Accounts, newChannels: List<TvChannelOB>) {
        val thisaccount = accountBox.get(account.id)
        val uncensoredCategories = thisaccount.tvcategories.filter { it.censored == 0 }
        for (category in uncensoredCategories) {
            val thisChannels = newChannels.filter { it.relatedtvCategoryId == category.idByAccountData }.map {
                it.reltvcategory.target = category
                it
            }

            tvChannBox.put(thisChannels)
            setPositionsForModifiedAndNewChannels(account, thisChannels, category)
        }
    }

    fun setPositionsForModifiedAndNewChannels(account: Accounts, channelsByCategory: List<TvChannelOB>, categoryOB: TvCategoryOB) {
        val currentPositions = categoryOB.tvChannelLink
        val currentChannels = categoryOB.tvchannels.sortedBy { it.number.toIntOrNull() ?: 0 }
        channelsByCategory.forEach {
            Log.d("UPDATE CHANN: NEWPOSITION", "${categoryOB.tvaccount.target.name} = ${it.showingName}")

            val position = currentChannels.indexOf(it)
            // Update Positionen für Channels, die durch neue Channels beeinflusst werden
            val modifiedOriginalPositions = currentPositions.filter { it.originalPosition >= position }.map { chPos ->
                chPos.originalPosition += 1
                chPos
            }
            manualPosBox.put(modifiedOriginalPositions)

            val modifiedPositions = currentPositions.filter { it.position >= position }.map { chPos ->
                chPos.position += 1
                chPos
            }
            manualPosBox.put(modifiedPositions)
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
            manualPosBox.put(newPosition)
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

    fun getMoviesByCategory(
        account: Accounts,
        movieCategoryId: String
    ): Deferred<List<MovieOB>> = viewModelScope.async {
        val response = xtreamRepository.getXtreamMoviesByCategory(
            account.stalkerUrl,
            account.username,
            account.macAddress,
            account.userAgent,
            movieCategoryId
        )
        when (response) {
            is Resource.Success -> {
                if (response.data.isNotEmpty()) {
                    val currentMoviesMap = moviesBox.query(
                        MovieOB_.accountId.equal(account.id)
                            .and(MovieOB_.relatedMovieCategoryId.equal(movieCategoryId))
                    ).build().find().associateBy { it.idByAccountData }
                    val movies = response.data.map { movieData ->
                        val idByAccountData = "${movieData.stream_id}_${account.id}"

                        currentMoviesMap[idByAccountData] ?: MovieOB(
                            id = 0,
                            idByAccountData = "${movieData.stream_id}_${account.id}",
                            movieId = movieData.stream_id.toString(),
                            relatedMovieCategoryId = movieData.category_id ?: "",
                            accountName = account.name,
                            accountId = account.id, // Setze dies auf null oder einen entsprechenden Wert
                            movieName = movieData.name,
                            movieCmd = "",
                            movieTime = null,
                            movieYear = movieData.year ?: "",
                            rate = "",
                            rating_imdb = movieData.rating,
                            screenshot_uri = movieData.stream_icon,
                            genres_str = "",
                            actors = "",
                            added = movieData.added,
                            age = "",
                            description = "",
                            director = movieData.director,
                            tmdb_id = "",
                            o_name = "",
                            currentPosition = 0L, // Platzhalter für aktuelle Position
                            isFavorite = false,
                            isCompletelyWatched = false,
                            isPartlyWatched = false,
                            percentagePlayed = 0.0,
                            xtreamExtension = movieData.container_extension ?: ""
                        )
                    }
                    movies.ifEmpty {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }

            is Resource.Error -> {
                emptyList()
            }
        }
    }

    var modifiedXtreamMovies: MutableList<String> = mutableListOf()

    suspend fun getXtreamMovieDetails(movieDetail: MovieOB, account: Accounts): MovieOB {
        val response = xtreamRepository.getXtreamMovieDetails(
            account.stalkerUrl,
            account.username,
            account.macAddress,
            account.userAgent,
            movieDetail.movieId
        )
        when (response) {
            is Resource.Success -> {
                val movieInfo = response.data.info
                movieDetail.movieTime = movieInfo.duration_secs
                movieDetail.director = movieInfo.director
                movieDetail.actors = movieInfo.actors
                movieDetail.description = movieInfo.description
                movieDetail.age = movieInfo.age
                movieDetail.country = movieInfo.country
                movieDetail.genres_str = movieInfo.genre
                movieDetail.backdropPath = movieInfo.backdrop_path?.firstOrNull() ?: ""
                movieDetail.tmdb_id = movieInfo.tmdb_id.toString()
            }
            is Resource.Error -> {

            }
        }
        return movieDetail
    }

    fun getSeriesByCategory(
        account: Accounts,
        serieCategoryId: String
    ): Deferred<List<SeriesOB>> = viewModelScope.async {
        val response = xtreamRepository.getXtreamSeriesByCategory(
            account.stalkerUrl,
            account.username,
            account.macAddress,
            account.userAgent,
            serieCategoryId
        )

        when (response) {
            is Resource.Success -> {
                Log.d("XTREAM INFO", "$serieCategoryId == ${response.data.size}")
                if (response.data.isNotEmpty()) {
                    // Lade existierende Serien aus der DB
                    val currentSeriesMap = seriesBox.query(
                        SeriesOB_.accountId.equal(account.id)
                            .and(SeriesOB_.relatedSeriesCategoryId.equal(serieCategoryId))
                    ).build().find().associateBy { it.idByAccountData }

                    val serien = response.data.map { seriesData ->
                        val idByAccountData = "${seriesData.series_id}_${account.id}"

                        // Falls Serie in der DB existiert, verwende die Originalinstanz
                        currentSeriesMap[idByAccountData] ?: SeriesOB(
                            idByAccountData = idByAccountData,
                            seriesId = seriesData.series_id.toString(),
                            relatedSeriesCategoryId = seriesData.category_id,
                            accountName = account.name,
                            accountId = account.id,
                            seriesName = seriesData.name ?: "",
                            seriesCmd = "",
                            seriesTime = seriesData.episode_run_time?.toIntOrNull() ?: 0,
                            seriesYear = seriesData.year,
                            rate = "",
                            rating_imdb = seriesData.rating,
                            screenshot_uri = seriesData.cover,
                            genres_str = seriesData.genre,
                            actors = seriesData.cast,
                            added = seriesData.last_modified,
                            age = "",
                            description = seriesData.plot,
                            director = seriesData.director,
                            tmdb_id = seriesData.tmdb,
                            o_name = "",
                            currentPosition = 0L, // Platzhalter für aktuelle Position
                            isFavorite = false,
                            isCompletelyWatched = false,
                            isPartlyWatched = false,
                            seriesPercentagePlayed = 0.0,
                            totalSeasons = 0,
                            totalEpisodes = 0
                        )
                    }
                    serien.ifEmpty {
                        emptyList()
                    }
                } else {
                    Log.d("XTREAM INFO", "$serieCategoryId == EMPTY")
                    emptyList()
                }
            }

            is Resource.Error -> {
                Log.d("XTREAM INFO", "$serieCategoryId == ERROR")
                emptyList()
            }
        }
    }

    // Datenstruktur für das Caching
    // Einfacher Cache, der Seasons und Episoden unabhängig speichert
    val seriesCache: MutableMap<String, Pair<MutableList<SeasonsOB>, MutableList<EpisodesOB>>> = mutableMapOf()

    suspend fun getXtreamSerieDetails(
        serieDetail: SeriesOB,
        account: Accounts
    ): MutableList<SeasonsOB> {
        // Falls die Serie bereits im Cache ist, verwende die zwischengespeicherten Daten
        seriesCache[serieDetail.idByAccountData]?.let { cachedData ->
            return cachedData.first.toMutableList()
        }

        val response = xtreamRepository.getXtreamSeriesDetails(
            account.stalkerUrl,
            account.username,
            account.macAddress,
            account.userAgent,
            serieDetail.seriesId
        )

        return when (response) {
            is Resource.Success -> {
                val existingSerieInDB = seriesBox.query(
                    SeriesOB_.idByAccountData.equal(serieDetail.idByAccountData)
                ).build().findFirst() != null

                // Lade bereits existierende Staffeln aus der DB
                val existingSeasonsMap = seasonsBox.query(
                    SeasonsOB_.seriesIdByAccount.equal(serieDetail.idByAccountData)
                ).build().find().associateBy { it.seriesSeasonIdByAccountData }

                val newSeasons = if (response.data.seasons.isNotEmpty()) {
                    val seasonList = response.data.seasons.map { apiSeason ->
                            val idByAccountData = "${apiSeason.id}_${serieDetail.seriesId}_${account.id}"
                            existingSeasonsMap[idByAccountData] ?: SeasonsOB(
                                seriesSeasonIdByAccountData = idByAccountData,
                                seriesIdByAccount = serieDetail.idByAccountData,
                                seasonNumber = apiSeason.season_number.toString(),
                                playlistId = account.id,
                                seasonsName = apiSeason.name,
                                seriesCmd = apiSeason.id.toString(),
                                rating_imdb = apiSeason.vote_average.toString(),
                                screenshot_uri = apiSeason.cover,
                                backdropPath = apiSeason.cover_big,
                                description = apiSeason.plot,
                                tmdb_id = apiSeason.tmdb_id,
                                isSeasonFullyWatched = false,
                                isSeasonPartlyWatched = false,
                                seasonPercentagePlayed = 0.0,
                                episodesCount = (1..apiSeason.episode_count).toMutableList()
                            ).apply {
                                serie.target = serieDetail
                            }
                        }
                    if (response.data.episodes.isNotEmpty()) {
                        seasonList.filter { it.seasonNumber in response.data.episodes.keys }.toMutableList()
                    } else {
                        mutableListOf()
                    }
                    } else {
                        response.data.episodes.map { (season, episodes) ->
                            val idByAccountData = "${season}_${serieDetail.seriesId}_${account.id}"
                            existingSeasonsMap[idByAccountData] ?: SeasonsOB(
                                seriesSeasonIdByAccountData = idByAccountData,
                                seriesIdByAccount = serieDetail.idByAccountData,
                                seasonNumber = season,
                                playlistId = account.id,
                                seasonsName = "Season $season",
                                seriesCmd = season,
                                rating_imdb = "",
                                screenshot_uri = "",
                                backdropPath = "",
                                description = "",
                                tmdb_id = "",
                                episodesCount = (1..episodes.size).toMutableList(),
                                isSeasonFullyWatched = false,
                                isSeasonPartlyWatched = false,
                                seasonPercentagePlayed = 0.0
                            ).apply {
                                serie.target = serieDetail
                            }
                        }.toMutableList()
                }

                // Episoden laden und mit den bestehenden Staffeln verknüpfen
                val newEpisodes = getAllEpisodes(response.data, serieDetail, account, newSeasons)
                episodesList.addAll(newEpisodes)
                if (existingSerieInDB) {
                    if (serieDetail.totalSeasons < newSeasons.size) {
                        serieDetail.newSeasons = true
                    }
                    if (serieDetail.totalEpisodes < newEpisodes.size) {
                        serieDetail.newEpisodes = true
                        if (serieDetail.isCompletelyWatched) {
                            serieDetail.isCompletelyWatched = false
                            serieDetail.isPartlyWatched = true
                            seriesBox.put(serieDetail)
                        }
                    }
                }

                // **Daten in den Cache schreiben**
                seriesCache[serieDetail.idByAccountData] = Pair(newSeasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                    .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE }).toMutableList(), newEpisodes)

                return newSeasons
            }
            is Resource.Error -> mutableListOf()
        }
    }


    fun getAllEpisodes(
        xtreamSeriesDetails: XtreamSeriesDetails,
        serieDetail: SeriesOB,
        account: Accounts,
        seasons: MutableList<SeasonsOB>
    ): MutableList<EpisodesOB> {
        episodesList.clear()
        val existingEpisodesMap = episodesBox.query(
            EpisodesOB_.seriesIdByAccount.equal(serieDetail.idByAccountData)
        ).build().find().associateBy { it.seriesSeasonEpisodeIdByAccountData }

        return if (seasons.isNotEmpty()) {
                xtreamSeriesDetails.episodes.flatMap { (seasonNumber, episodeList) ->
                    val season = seasons.firstOrNull { it.seasonNumber == seasonNumber }
                    episodeList.map { apiEpisode ->
                        val idByAccountData =
                            "${apiEpisode.episode_num}_${seasonNumber}_${serieDetail.seriesId}_${account.id}"

                        existingEpisodesMap[idByAccountData] ?: EpisodesOB(
                            seriesSeasonEpisodeIdByAccountData = idByAccountData,
                            seriesSeasonIdByAccount = season?.seriesSeasonIdByAccountData ?: "",
                            seriesIdByAccount = serieDetail.idByAccountData,
                            episodeNumber = apiEpisode.episode_num ?: 0,
                            seasonNumber = seasonNumber,
                            seasonName = season?.seasonsName ?: "",
                            seriesName = serieDetail.seriesName,
                            episodeName = apiEpisode.title,
                            episodeTime = (apiEpisode.info.duration_secs?.div(60)).toString(),
                            episodeCmd = apiEpisode.id,
                            episodeImg = apiEpisode.info.movie_image,
                            episodeDescription = apiEpisode.info.plot,
                            currentPosition = 0,
                            isEpisodeFullyWatched = false,
                            isEpisodePartlyWatched = false,
                            episodePercentagePlayed = 0.0,
                            containerExtension = apiEpisode.container_extension ?: ""
                        ).apply {
                            season?.episodes?.add(this)
                        }
                    }
                }.toMutableList()
            } else {
               mutableListOf()
        }
    }


    fun getAllEpg(account: Accounts): Deferred<String> = viewModelScope.async {
        _playlistProcessState.value = PlaylistLoadProcessState.GetEpg(
            1,
            "Load EPG.."
        )
        val epgSourceQuery = epgSourceBox.query(EpgSource_.playlistId.equal(account.id)).build()
        val thisEpgSource = epgSourceQuery.findFirst()
        epgSourceQuery.close()
        if (thisEpgSource != null) {
            val response = xtreamRepository.getXtreamEpg(
                account.stalkerUrl,
                account.username,
                account.macAddress,
                thisEpgSource,
                account
            )
            when (response) {
                is Resource.Success -> {
                    _playlistProcessState.value = PlaylistLoadProcessState.GetEpg(
                        100,
                        response.data
                    )
                    return@async "OK" // Das Ergebnis "OK" wird zurückgegeben
                }

                is Resource.Error -> {
                    _playlistProcessState.value = PlaylistLoadProcessState.EpgError(
                        response.message
                    )
                    return@async "NOTOK" // Das Ergebnis "NOTOK" wird zurückgegeben
                }
            }
        }
        return@async "" // Leerer String als Fallback-Rückgabe
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
            epgSourcePosBox.put(epgPosition)
        }
    }

    suspend fun getShortEpg(
        account: Accounts,
        tvChannelOB: TvChannelOB
    ): List<EpgDataOB>? =
        withContext(Dispatchers.IO) {
            val epgResult = xtreamRepository.getXtreamShortEpg(
                account.stalkerUrl,
                account.username,
                account.macAddress,
                account.userAgent,
                tvChannelOB.channelId.toString()
            )
            when (epgResult) {
                is Resource.Success -> {
                    val epgSource =
                        epgSourceBox.query(EpgSource_.playlistId.equal(account.id)).build()
                            .findFirst()
                    if (epgSource != null && epgResult.data.epg_listings.isNotEmpty()) {
                        val epg = epgResult.data.epg_listings.map {
                            val dateTimeString = it.start
                            val datum = extractDate(dateTimeString)
                            val title = decodeBase64(it.title)
                            val descr = decodeBase64(it.description)
                            EpgDataOB(
                                0,
                                "${account.id}_${it.channel_id}_${it.start_timestamp}",
                                it.id,
                                it.channel_id,
                                datum,
                                title,
                                "",
                                descr,
                                mutableListOf(),
                                mutableListOf(),
                                mutableListOf(),
                                "",
                                mutableListOf(),
                                "",
                                "",
                                "",
                                it.start,
                                it.end,
                                it.start_timestamp.toLong(),
                                it.stop_timestamp.toLong(),
                                0,
                                account.id.toString(),
                                epgSource.id.toInt(),
                                "${epgSource.id}_${it.channel_id}"
                            )
                        }
                        if (epg.isNotEmpty()) {
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

    suspend fun getEpgByChannel(
        account: Accounts,
        tvChannelOB: TvChannelOB
    ): List<EpgDataOB> =
        withContext(Dispatchers.IO) {
            val epgResult =
                xtreamRepository.getXtreamEpgByChannel(
                    account.stalkerUrl,
                    account.username,
                    account.macAddress,
                    account.userAgent,
                    tvChannelOB.channelId.toString()
                )
            when (epgResult) {
                is Resource.Success -> {
                    val epgSource =
                        epgSourceBox.query(EpgSource_.playlistId.equal(account.id)).build()
                            .findFirst()
                    if (epgSource != null && epgResult.data.epg_listings.isNotEmpty()) {
                        val newepg = epgResult.data.epg_listings.map {
                            val dateTimeString = it.start
                            val datum = extractDate(dateTimeString)
                            val title = decodeBase64(it.title)
                            val descr = decodeBase64(it.description)
                            EpgDataOB(
                                0,
                                "${account.id}_${tvChannelOB.idByAccountData}_${it.start_timestamp}",
                                it.id,
                                it.channel_id,
                                datum,
                                title,
                                "",
                                descr,
                                mutableListOf(),
                                mutableListOf(),
                                mutableListOf(),
                                "",
                                mutableListOf(),
                                "",
                                "",
                                "",
                                it.start,
                                it.end,
                                it.start_timestamp.toLong(),
                                it.stop_timestamp.toLong(),
                                it.has_archive,
                                account.id.toString(),
                                epgSource.id.toInt(),
                                "${epgSource.id}_${it.channel_id}"
                            )
                        }
                        if (newepg.isNotEmpty()) {
                            return@withContext newepg
                        } else {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }

                is Resource.Error -> {
                    emptyList()
                }
            }
        }

    fun getFullEpgByChannel(
        channelOB: TvChannelOB
    ): Deferred<List<EpgDataOB>> = viewModelScope.async {
        val account = channelOB.account.target
        val epgResult =
            xtreamRepository.getXtreamEpgByChannel(
                account.stalkerUrl,
                account.username,
                account.macAddress,
                account.userAgent,
                channelOB.channelId.toString()
            )
        when (epgResult) {
            is Resource.Success -> {
                val epgSource =
                    epgSourceBox.query(EpgSource_.playlistId.equal(account.id)).build()
                        .findFirst()
                if (epgSource != null && epgResult.data.epg_listings.isNotEmpty()) {
                    val newepg = epgResult.data.epg_listings.map {
                        val dateTimeString = it.start
                        val datum = extractDate(dateTimeString)
                        val title = decodeBase64(it.title)
                        val descr = decodeBase64(it.description)
                        EpgDataOB(
                            0,
                            "${account.id}_${it.channel_id}_${it.start_timestamp}",
                            it.id,
                            it.channel_id,
                            datum,
                            title,
                            "",
                            descr,
                            mutableListOf(),
                            mutableListOf(),
                            mutableListOf(),
                            "",
                            mutableListOf(),
                            "",
                            "",
                            "",
                            it.start,
                            it.end,
                            it.start_timestamp.toLong(),
                            it.stop_timestamp.toLong(),
                            it.has_archive,
                            account.id.toString(),
                            epgSource.id.toInt(),
                            "${epgSource.id}_${it.channel_id}"
                        )
                    }.toMutableList()
                    if (newepg.isNotEmpty()) {
                        val playlistEpgSource =
                            account.epgsources.find { it.isSelected && it.isPlaylistEpg }
                        if (playlistEpgSource != null) {
                            val epgChExists = withContext(Dispatchers.IO) {
                                channelOB.epgChannel?.target?.chEpgId?.let {
                                    EpgSourceChannel_.chEpgId.equal(it)
                                }?.let { epgChannelBox.query(it).build().findFirst() }
                            }
                            if (epgChExists != null) {
                                val existingDataQuery = epgDataBox.query(EpgDataOB_.epgChId.equal(epgChExists.chEpgId)).build()
                                val existingDataIds = existingDataQuery.property(EpgDataOB_.idByAccountData).findStrings()
                                existingDataQuery.close()

                                val iterator = newepg.iterator()
                                while (iterator.hasNext()) {
                                    val epg = iterator.next()
                                    if (existingDataIds.contains(epg.idByAccountData)) {
                                        iterator.remove()
                                    }
                                }
                                epgDataBox.put(newepg)
                                epgChannelBox.put(epgChExists)
                                channelOB.epgChannel?.target = epgChExists
                                tvChannBox.put(channelOB)
                            } else {
                                val newEpgChannel = EpgSourceChannel(
                                    0,
                                    "${playlistEpgSource.id}_${channelOB.channelId}",
                                    "${channelOB.channelId}",
                                    mutableListOf(channelOB.logo),
                                    channelOB.showingName,
                                    playlistEpgSource.id,
                                    mutableListOf(
                                        channelOB.name,
                                        channelOB.editedName,
                                        channelOB.showingName
                                    ),
                                    false
                                )
                                epgDataBox.put(newepg)
                                newEpgChannel.epgsource.target =
                                    playlistEpgSource.relatedepgsource.target
                                epgChannelBox.put(newEpgChannel)
                                channelOB.epgChannel?.target = newEpgChannel
                                tvChannBox.put(channelOB)
                            }
                        }
                        newepg
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }

            is Resource.Error -> {
                emptyList()
            }
        }
    }


    fun decodeBase64(encoded: String): String {
        // Dekodiere den Base64-codierten String
        val decodedBytes = Base64.decode(encoded, Base64.DEFAULT)
        // Konvertiere die Bytes in einen String
        return String(decodedBytes, Charsets.UTF_8)
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


    suspend fun checkChannelsAndShortEpg(channelOB: TvChannelOB): TvChannelOB {
        withContext(Dispatchers.IO) {
            val currentAccountInUse = channelOB.playlistId?.let { accountBox.get(it) }
            if (currentAccountInUse != null) {
                try {
                    val newepg = getEpgByChannel(currentAccountInUse, channelOB)
                    if (newepg.isNotEmpty()) {
                        val playlistEpgSource =
                            currentAccountInUse.epgsources.filter { it.isSelected }
                                .find { it.isPlaylistEpg }
                        if (playlistEpgSource != null) {
                            val epgChExists = withContext(Dispatchers.IO) {
                                channelOB.epgChannel?.target?.chEpgId?.let {
                                    EpgSourceChannel_.chEpgId.equal(it)
                                }?.let { epgChannelBox.query(it).build().findFirst() }
                            }
                            if (epgChExists != null) {
                                epgChannelBox.put(epgChExists)
                                channelOB.epgChannel?.target = epgChExists
                                tvChannBox.put(channelOB)

                            } else {
                                val newEpgChannel = EpgSourceChannel(
                                    0,
                                    "${playlistEpgSource.id}_${channelOB.channelId}",
                                    "${channelOB.channelId}",
                                    mutableListOf(channelOB.logo),
                                    channelOB.showingName,
                                    playlistEpgSource.id,
                                    mutableListOf(
                                        channelOB.name,
                                        channelOB.editedName,
                                        channelOB.showingName
                                    ),
                                    false
                                )
                                newEpgChannel.epgsource.target =
                                    playlistEpgSource.relatedepgsource.target
                                epgChannelBox.put(newEpgChannel)
                                channelOB.epgChannel?.target = newEpgChannel
                                tvChannBox.put(channelOB)
                            }
                        }
                    }
                    return@withContext channelOB
                } catch (e: Exception) {
                    Log.d("XTREAM SINGLE CHANNEL ERROR", "$e")
                    // Fehlerbehandlung hier (z.B., Loggen, Benutzer informieren)
                    return@withContext null
                }
            } else {
                return@withContext null
            }
        }
        return channelOB
    }

    fun addChannelsToTvCategory(accountId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = accountBox.get(accountId)
            val alltvCategories = account.tvcategories
            alltvCategories.forEach { tvCat ->
                val thisChannels =
                    account.channels.filter { it.relatedtvCategoryId == tvCat.idByAccountData }
                val updatedChannels = thisChannels.map {
                    it.reltvcategory.target = tvCat
                    it
                }
                tvChannBox.put(updatedChannels)
                val manualPositions = thisChannels.sortedBy { it.number.toIntOrNull() ?: 0 }
                    .mapIndexed { index, tvChannel ->
                        val manualPosition = ChannelPositions(
                            id = 0, // ObjectBox generiert die ID automatisch
                            channel = tvChannel.idByAccountData,
                            playlistId = tvCat.playlistId,
                            relatedtvCategoryId = tvCat.idByAccountData,
                            position = index,
                            originalPosition = index,
                            catAndChannelAccount = "${tvCat.idByAccountData}_${tvChannel.idByAccountData}_${account.id}",
                        )
                        // Initialisieren Sie die `ToOne`-Beziehung
                        manualPosition.tvchannel.target = tvChannel
                        manualPosition.tvcategory.target = tvCat
                        manualPosition.tvcategory.target = tvCat

                        manualPosition
                    }
                manualPosBox.put(manualPositions)
            }
        }
    }

    private fun updatePrefixesAndSuffixesCategoriesForAddedPlaylist(
        prefixes: List<String>?,
        suffixes: List<String>?,
        tvcategories: List<TvCategoryOB>
    ): List<TvCategoryOB> {
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

    private fun updatePrefixesAndSuffixesForAddedPlaylist(
        prefixes: List<String>?,
        suffixes: List<String>?,
        tvchannels: List<TvChannelOB>
    ): List<TvChannelOB> {
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


    private fun updatePrefixesAndSuffixeMovieCategoriesForAddedPlaylist(
        prefixes: List<String>?,
        suffixes: List<String>?,
        moviecategories: List<MovieCategoryOB>
    ): List<MovieCategoryOB> {
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

    private fun updatePrefixesAndSuffixeSeriesCategoriesForAddedPlaylist(
        prefixes: List<String>?,
        suffixes: List<String>?,
        seriescategories: List<SeriesCategoryOB>
    ): List<SeriesCategoryOB> {
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

    private fun updatePrefixesAndSuffixesForModifiedMovieCategories(
        prefixes: List<String>?,
        suffixes: List<String>?,
        moviecategory: MovieCategoryOB
    ): MovieCategoryOB {
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

    private fun updatePrefixesAndSuffixesForModifiedSeriesCategories(
        prefixes: List<String>?,
        suffixes: List<String>?,
        seriesCategory: SeriesCategoryOB
    ): SeriesCategoryOB {
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

    suspend fun addCensoredChannelsToTvCategory(
        censoredCategories: List<TvCategoryOB>,
        accountData: Accounts,
        name: String,
        settings: Settings
    ) {
        val thisEpgSource =
            epgSourceBox.query(EpgSource_.playlistId.equal(accountData.id)).build().findFirst()
        val xxxChannels: MutableList<TvChannelOB> = mutableListOf()

        if (thisEpgSource != null) {
            for (category in censoredCategories) {
                var chPosition = 0
                val channelsResponse = xtreamRepository.getXtreamChannelsByCategory(
                    accountData.stalkerUrl,
                    accountData.username,
                    accountData.macAddress,
                    accountData.userAgent,
                    category.tvCatId
                )
                when (channelsResponse) {
                    is Resource.Success -> {
                        if (channelsResponse.data.isNotEmpty()) {
                            val initialChannels = channelsResponse.data.map {
                                val thisChannel = TvChannelOB(
                                    0,
                                    accountData.id,
                                    it.stream_id,
                                    it.num.toString(),
                                    "",
                                    it.stream_icon ?: "",
                                    "",
                                    it.category_id?.toInt(),
                                    "${it.category_id}_${accountData.id}",
                                    it.name ?: "",
                                    it.name ?: "",
                                    it.name ?: "",
                                    it.epg_channel_id?.lowercase() ?: "",
                                    it.tv_archive,
                                    it.tv_archive_duration,
                                    it.tv_archive,
                                    name,
                                    "${it.stream_id}_${accountData.id}",
                                    thisEpgSource.id,
                                    true,
                                    false,
                                    false,
                                    0L,
                                    false,
                                    null,
                                    "${it.category_id}_${accountData.id}"
                                )
                                thisChannel.account.target = accountData
                                thisChannel
                            }
                            xxxChannels.addAll(initialChannels)
                            val manualPositions =
                                initialChannels.sortedBy { it.number.toIntOrNull() ?: 0 }
                                    .map { tvChannel ->
                                        val manualPosition = ChannelPositions(
                                            id = 0, // ObjectBox generiert die ID automatisch
                                            channel = tvChannel.idByAccountData,
                                            playlistId = category.playlistId!!,
                                            relatedtvCategoryId = category.idByAccountData,
                                            position = chPosition++,
                                            originalPosition = chPosition++,
                                            catAndChannelAccount = "${category.idByAccountData}_${tvChannel.idByAccountData}_${accountData.id}"
                                        )
                                        // Initialisieren Sie die `ToOne`-Beziehung
                                        manualPosition.tvchannel.target = tvChannel

                                        manualPosition
                                    }
                            manualPosBox.put(manualPositions)
                        }

                        // Aktualisiere die Präfixe und Suffixe und speichere die Kanäle
                        val finalChannelList = updatePrefixesAndSuffixesForAddedPlaylist(
                            settings.prefixes,
                            settings.suffixes,
                            xxxChannels
                        )
                        tvCatBox.put(category)
                        xxxChannelsCount = finalChannelList.size
                    }

                    is Resource.Error -> {
                        Log.d("XXX CHANNELS", "Keine vorhanden")
                    }
                }
            }
        }
    }

    suspend fun findEpgMatch(
        externalEpgEvent: EpgDataOB,
        channel: TvChannelOB,
        currentCategory: TvCategoryOB
    ): Resource<EpgListings?> =
        withContext(Dispatchers.IO) {
            val account = channel.account.target
            val serverEpg = xtreamRepository.getXtreamEpgByChannel(
                account.stalkerUrl,
                account.username,
                account.macAddress,
                account.userAgent,
                channel.channelId.toString()
            )
            when (serverEpg) {
                is Resource.Success -> {
                    val epgListFromServer = serverEpg.data.epg_listings
                    val thisEpg = findClosestMatch(externalEpgEvent, epgListFromServer, channel, currentCategory)
                    Resource.Success(thisEpg)
                }
                is Resource.Error -> {
                    Resource.Error(serverEpg.message)
                }
            }
        }

    private fun findClosestMatch(externalEvent: EpgDataOB, serverEpg: List<EpgListings>, currentChannel: TvChannelOB, currentCategory: TvCategoryOB): EpgListings? {
        val timeOffSet = currentChannel.epgTimeOffSet ?: currentCategory.epgTimeOffSet ?: currentChannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0
        val timeOffSetSeconds = calculateTimeOffsetInSeconds(timeOffSet)
        var closestMatch: EpgListings? = null
        var closestTimeDiff = Long.MAX_VALUE

        for (serverEvent in serverEpg) {
            // Berechne den Zeitunterschied in Millisekunden
            val timeDifference = Math.abs((serverEvent.start_timestamp.toLong() + timeOffSetSeconds) - (externalEvent.startTimestamp!! + timeOffSetSeconds))
            if (timeDifference < closestTimeDiff) {
                closestMatch = serverEvent
                closestTimeDiff = timeDifference
            }
        }
        Log.d("CATCHUP XTREAM", "EXTERN: ${externalEvent.name} SERVER: ${closestMatch?.title}")
        return closestMatch
    }

    fun calculateTimeOffsetInSeconds(timeOffset: Int): Long {
        return (timeOffset * 3600).toLong()
    }

    fun updatePlaylistIfNeeded() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val currentTimeMillis = System.currentTimeMillis() / 1000 // aktuelle Zeit in Sekunden
                val xtreamPlaylists = accountBox.all.filter { it.isXtream }
                for (playlist in xtreamPlaylists) {
                    // Letztes Update-Zeitpunkt in Sekunden
                    val lastUpdatedSeconds = playlist.lastUpdatedDate

                    // Berechne den Zeitpunkt für das nächste fällige Update in Sekunden
                    val nextUpdateDueSeconds = lastUpdatedSeconds + playlist.autoUpdateHours * 60 * 60

                    // Prüfe, ob ein Update erforderlich ist
                    if (currentTimeMillis > nextUpdateDueSeconds || playlist.updateOnAppStart) {
                        // Update erforderlich

                        // Führe das Update durch
                        updateXtreamData(playlist)

                        // Aktualisiere lastUpdatedDate auf die aktuelle Zeit in Sekunden
                        playlist.lastUpdatedDate = System.currentTimeMillis() / 1000

                        // Speichere die Aktualisierung in der Datenbank oder im Speicher
                        accountBox.put(playlist)
                    }
                }
            }
        }
    }

}