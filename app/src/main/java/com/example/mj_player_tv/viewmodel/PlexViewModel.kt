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
import com.example.mj_player_tv.database.entity.AudioCategoryOB
import com.example.mj_player_tv.database.entity.AudioCategoryOB_
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.EpgSourceChannel
import com.example.mj_player_tv.database.entity.EpgSourceChannel_
import com.example.mj_player_tv.database.entity.EpgSourcePositions
import com.example.mj_player_tv.database.entity.EpgSourcePositions_
import com.example.mj_player_tv.database.entity.EpgSource_
import com.example.mj_player_tv.database.entity.MovieCategoryOB
import com.example.mj_player_tv.database.entity.MovieCategoryOB_
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.PlexCategoryOB
import com.example.mj_player_tv.database.entity.PlexCategoryOB_
import com.example.mj_player_tv.database.entity.SeriesCategoryOB
import com.example.mj_player_tv.database.entity.SeriesCategoryOB_
import com.example.mj_player_tv.database.entity.Settings
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.network.RetrofitInstance
import com.example.mj_player_tv.network.model.plex.items.Metadata
import com.example.mj_player_tv.network.model.plex.resources.PlexGetUserResources
import com.example.mj_player_tv.repository.HelpRepository
import com.example.mj_player_tv.repository.MoviePagingSource
import com.example.mj_player_tv.repository.PlaylistLoadProcessState
import com.example.mj_player_tv.repository.PlaylistUpdateProcessState
import com.example.mj_player_tv.repository.PlaylistUpdateWorker
import com.example.mj_player_tv.repository.PlexPagingSource
import com.example.mj_player_tv.repository.PlexRepository
import com.example.mj_player_tv.repository.XtreamRepository
import com.example.mj_player_tv.utils.Resource
import io.objectbox.Box
import io.objectbox.kotlin.query
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class PlexViewModel(application: Application): AndroidViewModel(application) {

    private val retrofitInstance = RetrofitInstance

    private val accountBox = ObjectBox.store.boxFor(Accounts::class.java)
    private val plexCatBox: Box<PlexCategoryOB> = ObjectBox.store.boxFor(PlexCategoryOB::class.java)

    var plexAccountAdded = 0
    var addedAccount = false

    private val plexRepository = PlexRepository()

    private val _playlistProcessState = MutableStateFlow<PlaylistLoadProcessState?>(null)
    val playlistProcessState: StateFlow<PlaylistLoadProcessState?> = _playlistProcessState


    fun resetPlaylistProcessState() {
        _playlistProcessState.value = null
    }

    private var currentMail = ""
    private var currentPassword = ""
    private var currentToken = ""

    fun resetPlexAccountInfos() {
        currentMail = ""
        currentPassword = ""
        currentToken = ""
    }

    suspend fun getPlexData(email: String, password: String) {
        val authResponse = postPlexAuthentication(email, password).await()
        when (authResponse) {
            is Resource.Success -> {
                if (authResponse.data.isEmpty()) {
                    _playlistProcessState.value = PlaylistLoadProcessState.Error("No Plex Data found")
                } else {
                    _playlistProcessState.value = PlaylistLoadProcessState.GetToken(100)
                    getPlexUserResources(authResponse.data)
                    currentMail = email
                    currentPassword = password
                    currentToken = authResponse.data
                }
            }
            is Resource.Error -> {
                _playlistProcessState.value = PlaylistLoadProcessState.Error(authResponse.message)
            }
        }
    }

    var currentUpdatingPlaylistId: Long? = null

    private val _playlistUpdateProcessState = MutableStateFlow<PlaylistUpdateProcessState?>(null)
    val playlistUpdateProcessState: StateFlow<PlaylistUpdateProcessState?> = _playlistUpdateProcessState

    fun resetPlaylistUpdateProcessState() {
        _playlistUpdateProcessState.value = null
    }

    fun updatePlexAccount(account: Accounts) {
        viewModelScope.launch(Dispatchers.IO) {
            val authentication = plexRepository.getPlexAuthentication(account.username, account.macAddress)
            when (authentication) {
                is Resource.Success -> {
                    if (account.mainPlexToken != authentication.data.authToken && authentication.data.authToken.isNotEmpty()) {
                        account.mainPlexToken = authentication.data.authToken
                        val accessToken = plexRepository.getPlexUserResources(authentication.data.authToken)
                        when (accessToken) {
                            is Resource.Success -> {
                                val server = accessToken.data.firstOrNull { it.clientIdentifier == account.plexClientIdentifier }
                                if (server != null && server.accessToken.isNotEmpty()) {
                                    account.token = server.accessToken
                                    val sections = plexRepository.getPlexServerLibrarySections(account.stalkerUrl, server.accessToken)
                                    when (sections) {
                                        is Resource.Success -> {
                                            val movieSections =
                                                sections.data.MediaContainer.Directory.filter { it.type == "movie" }
                                            val seriesSections =
                                                sections.data.MediaContainer.Directory.filter { it.type == "show" }
                                            val audioSections =
                                                sections.data.MediaContainer.Directory.filter { it.type == "artist" }
                                            val currentMovieSectionsQuery = plexCatBox.query(
                                                PlexCategoryOB_.isMovie.equal(true)
                                            ).build()
                                            val currentMovieSections = currentMovieSectionsQuery.find()
                                            currentMovieSectionsQuery.close()
                                            val newMovieSectionsKeys = movieSections.map { it.key }.toSet()
                                            val currentMovieSectionsKeys = currentMovieSections.map { it.plexCatId }.toSet()

                                            // Zu löschende Sektionen: in DB, aber nicht in neuer Antwort
                                            val movieSectionsToDelete = currentMovieSections.filter { it.plexCatId !in newMovieSectionsKeys }

                                            // Zu hinzufügende Sektionen: in Antwort, aber nicht in DB
                                            val movieSectionsToAdd = movieSections.filter { it.key !in currentMovieSectionsKeys }.map {
                                                PlexCategoryOB(
                                                    0,
                                                    account.id,
                                                    it.key,
                                                    it.title,
                                                    it.title,
                                                    it.title,
                                                    account.name,
                                                    true,
                                                    true,
                                                    false,
                                                    "${it.key}_${account.id}",
                                                    true
                                                )
                                            }
                                            val sameSections = currentMovieSections.filter { it.plexCatId in newMovieSectionsKeys }
                                            sameSections.forEach { same ->
                                                val name = movieSections.firstOrNull { it.key == same.plexCatId }?.title
                                                if (same.newCategory) {
                                                    same.newCategory = false
                                                }
                                                if (name != null && same.title != name) {
                                                    same.title = name
                                                }
                                            }
                                            plexCatBox.remove(movieSectionsToDelete)
                                            plexCatBox.put(sameSections)
                                            plexCatBox.put(movieSectionsToAdd)

                                            val currentSeriesSectionsQuery = plexCatBox.query(
                                                PlexCategoryOB_.isMovie.equal(false).and(PlexCategoryOB_.isAudio.equal(false))
                                            ).build()
                                            val currentSeriesSections = currentSeriesSectionsQuery.find()
                                            currentSeriesSectionsQuery.close()
                                            val newSeriesSectionsKeys = seriesSections.map { it.key }.toSet()
                                            val currentSeriesSectionsKeys = currentSeriesSections.map { it.plexCatId }.toSet()

                                            val seriesSectionsToDelete = currentSeriesSections.filter { it.plexCatId !in newSeriesSectionsKeys }

                                            // Zu hinzufügende Sektionen: in Antwort, aber nicht in DB
                                            val seriesSectionsToAdd = seriesSections.filter { it.key !in currentSeriesSectionsKeys }.map {
                                                PlexCategoryOB(
                                                    0,
                                                    account.id,
                                                    it.key,
                                                    it.title,
                                                    it.title,
                                                    it.title,
                                                    account.name,
                                                    true,
                                                    false,
                                                    false,
                                                    "${it.key}_${account.id}",
                                                    true
                                                )
                                            }
                                            val sameSeriesSections = currentSeriesSections.filter { it.plexCatId in newSeriesSectionsKeys }
                                            sameSeriesSections.forEach { same ->
                                                val name = seriesSections.firstOrNull { it.key == same.plexCatId }?.title
                                                if (same.newCategory) {
                                                    same.newCategory = false
                                                }
                                                if (name != null && same.title != name) {
                                                    same.title = name
                                                }
                                            }
                                            plexCatBox.remove(seriesSectionsToDelete)
                                            plexCatBox.put(sameSeriesSections)
                                            plexCatBox.put(seriesSectionsToAdd)

                                            val currentAudioSectionsQuery = plexCatBox.query(
                                                PlexCategoryOB_.isMovie.equal(false).and(PlexCategoryOB_.isAudio.equal(true))
                                            ).build()
                                            val currentAudioSections = currentAudioSectionsQuery.find()
                                            currentAudioSectionsQuery.close()
                                            val newAudioSectionsKeys = seriesSections.map { it.key }.toSet()
                                            val currentAudioSectionsKeys = currentAudioSections.map { it.plexCatId }.toSet()

                                            val audioSectionsToDelete = currentAudioSections.filter { it.plexCatId !in newAudioSectionsKeys }

                                            // Zu hinzufügende Sektionen: in Antwort, aber nicht in DB
                                            val audioSectionsToAdd = audioSections.filter { it.key !in currentAudioSectionsKeys }.map {
                                                PlexCategoryOB(
                                                    0,
                                                    account.id,
                                                    it.key,
                                                    it.title,
                                                    it.title,
                                                    it.title,
                                                    account.name,
                                                    true,
                                                    false,
                                                    true,
                                                    "${it.key}_${account.id}",
                                                    true
                                                )
                                            }
                                            val sameAudioSections = currentAudioSections.filter { it.plexCatId in newAudioSectionsKeys }
                                            sameAudioSections.forEach { same ->
                                                val name = audioSections.firstOrNull { it.key == same.plexCatId }?.title
                                                if (same.newCategory) {
                                                    same.newCategory = false
                                                }
                                                if (name != null && same.title != name) {
                                                    same.title = name
                                                }
                                            }
                                            plexCatBox.remove(audioSectionsToDelete)
                                            plexCatBox.put(sameAudioSections)
                                            plexCatBox.put(audioSectionsToAdd)
                                            _playlistUpdateProcessState.value = PlaylistUpdateProcessState.Success
                                        }
                                        is Resource.Error -> {
                                            _playlistUpdateProcessState.value = PlaylistUpdateProcessState.Error
                                        }
                                    }
                                }
                            }
                            is Resource.Error -> {
                                _playlistUpdateProcessState.value = PlaylistUpdateProcessState.Error
                            }
                        }
                    }
                }
                is Resource.Error -> {
                    _playlistUpdateProcessState.value = PlaylistUpdateProcessState.Error
                }
            }
        }
    }

    fun postPlexAuthentication(email: String, password: String): Deferred<Resource<String>> = viewModelScope.async {
        val response = plexRepository.getPlexAuthentication(email, password)
        when (response) {
            is Resource.Success -> {
                val token = response.data.authToken
                if (token.isNotEmpty()) {
                    Resource.Success(token)
                } else {
                    Resource.Success("")
                }
            }
            is Resource.Error -> {
                Resource.Error(response.message)
            }
        }
    }

    val serverList: MutableList<PlexGetUserResources> = mutableListOf()

    suspend fun getPlexUserResources(authToken: String) {
        withContext(Dispatchers.IO) {
            val response = plexRepository.getPlexUserResources(authToken)
            when (response) {
                is Resource.Success -> {
                    val serverResources = response.data.filter { it.provides == "server" }
                    if (serverResources.isNotEmpty()) {
                        serverList.addAll(serverResources)
                        _playlistProcessState.value =
                            PlaylistLoadProcessState.GetTvCategories(100, "Servers fetched!")
                        Resource.Success("Ok")
                    } else {
                        _playlistProcessState.value =
                            PlaylistLoadProcessState.TvError("Couldn't find any servers for this account!")
                        Resource.Error("")
                    }
                }

                is Resource.Error -> {
                    _playlistProcessState.value = PlaylistLoadProcessState.Error(response.message)
                    Resource.Error(response.message)
                }
            }
        }
    }

    fun updatePlexTokens() {
        viewModelScope.launch(Dispatchers.IO) {
            val plexAccounts = accountBox.query(
                Accounts_.isPlex.equal(true)
            ).build().find()
            plexAccounts.forEach { plexAccount ->
                if (plexAccount != null) {
                    Log.d("UPDATE PLEX", "UPDATE: ${plexAccount.name} MAIL: ${plexAccount.username} PASS: ${plexAccount.macAddress}")
                    val maintoken = updateMainToken(plexAccount.username, plexAccount.macAddress)
                    Log.d("UPDATE PLEX", "MAINTOKEN = OLD: ${plexAccount.mainPlexToken} NEW: $maintoken")

                    val newAccessToken = updateAccessToken(maintoken, plexAccount.plexClientIdentifier ?: "")

                    Log.d("UPDATE PLEX", "ACCESSTOKEN = OLD: ${plexAccount.token} NEW: $newAccessToken")
                    if (maintoken.isNotEmpty() && maintoken != plexAccount.mainPlexToken) {
                        plexAccount.mainPlexToken = maintoken

                    }
                    if (newAccessToken.isNotEmpty() && newAccessToken != plexAccount.token) {
                        plexAccount.token = newAccessToken

                    }
                    if (maintoken.isEmpty() && newAccessToken.isEmpty()) {
                        plexAccount.lastUpdateStatus = 0

                    }
                    val currentDate = System.currentTimeMillis() / 1000
                    plexAccount.lastUpdatedDate = currentDate
                    accountBox.put(plexAccount)
                }
            }
        }
    }

    suspend fun updateMainToken(email: String, password: String): String {
        val response = plexRepository.getPlexAuthentication(email, password)
        return when (response) {
            is Resource.Success -> response.data.authToken
            is Resource.Error -> ""
        }
    }

    suspend fun updateAccessToken(mainToken: String, identifier: String): String {
        val response = plexRepository.getPlexUserResources(mainToken)
        return when (response) {
            is Resource.Success -> {
                val server = response.data.firstOrNull { it.clientIdentifier == identifier }
                server?.accessToken ?: ""
            }
            is Resource.Error -> ""
        }
    }


    fun getPlexUserLibrarySections(url: String, name: String, accesstoken: String, identifier: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentDate = System.currentTimeMillis() / 1000
            val newPlexAccount = Accounts(
                0,
                name,
                url,
                currentMail,
                currentPassword,
                accesstoken,
                "$currentMail$currentPassword",
                "",
                true,
                "",
                "",
                false,
                false,
                false,
                false,
                false,
                true,
                "",
                "",
                "",
                "",
                currentDate,
                1,
                0,
                0,
                1,
                1,
                false,
                true,
                72,
                false,
                0,
                0,
                false,
                mutableListOf(),
                false,
                "",
                mainPlexToken = currentToken,
                plexClientIdentifier = identifier
            )
            accountBox.put(newPlexAccount)
            val thisAccount = accountBox[newPlexAccount.id]
            val response = plexRepository.getPlexServerLibrarySections(url, accesstoken)
            when (response) {
                is Resource.Success -> {
                    val movieSections =
                        response.data.MediaContainer.Directory.filter { it.type == "movie" }
                    val seriesSections =
                        response.data.MediaContainer.Directory.filter { it.type == "show" }
                    val audioSections =
                        response.data.MediaContainer.Directory.filter { it.type == "artist" }
                    val movieCategories = movieSections.mapIndexed { index, directory ->
                        val movieCategory = PlexCategoryOB(
                            0,
                            thisAccount.id,
                            directory.key,
                            directory.title,
                            directory.title,
                            directory.title,
                            name,
                            true,
                            true,
                            false,
                            "${directory.key}_${thisAccount.id}",
                            false
                        )
                        movieCategory.plexAccount.target = thisAccount
                        movieCategory
                    }
                    if (movieCategories.isNotEmpty()) {
                        plexCatBox.put(movieCategories)
                        _playlistProcessState.value = PlaylistLoadProcessState.GetMovieCategories(
                            100,
                            "Movie categories added!",
                            "0"
                        )
                    } else {
                        _playlistProcessState.value =
                            PlaylistLoadProcessState.MovieError("No movie categories found!")
                    }
                    val seriesCategories = seriesSections.mapIndexed { index, directory ->
                        val seriesCategory = PlexCategoryOB(
                            0,
                            thisAccount.id,
                            directory.key,
                            directory.title,
                            directory.title,
                            directory.title,
                            name,
                            true,
                            false,
                            false,
                            "${directory.key}_${thisAccount.id}"
                        )
                        seriesCategory.plexAccount.target = thisAccount
                        seriesCategory
                    }
                    if (seriesCategories.isNotEmpty()) {
                        plexCatBox.put(seriesCategories)
                        _playlistProcessState.value = PlaylistLoadProcessState.GetSeriesCategories(
                            100,
                            "Series categories added!",
                            "0"
                        )
                    } else {
                        _playlistProcessState.value =
                            PlaylistLoadProcessState.SeriesError("No series categories found!")
                    }
                    val audioCategories = audioSections.mapIndexed { index, directory ->
                        val audioCategory = PlexCategoryOB(
                            0,
                            thisAccount.id,
                            directory.key,
                            directory.title,
                            directory.title,
                            directory.title,
                            name,
                            true,
                            false,
                            true,
                            "${directory.key}_${thisAccount.id}",
                            false
                        )
                        audioCategory.plexAccount.target = thisAccount
                        audioCategory
                    }
                    if (audioCategories.isNotEmpty()) {
                        plexCatBox.put(audioCategories)
                    }
                    setWorker(thisAccount)
                    _playlistProcessState.value = PlaylistLoadProcessState.Success(thisAccount)
                    Resource.Success("OK")
                }

                is Resource.Error -> {
                    accountBox.remove(newPlexAccount)
                    _playlistProcessState.value = PlaylistLoadProcessState.Error(response.message)
                    Resource.Error(response.message)
                }
            }
        }
    }

    private val _totalItems = MutableLiveData<Int>(0)
    val totalItems: LiveData<Int> get() = _totalItems

    fun getItemBySection(account: Accounts, sectionKey: Int): Flow<PagingData<Metadata>> {
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                prefetchDistance = 25,
                initialLoadSize = 50,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                PlexPagingSource(retrofitInstance, account.token ?: "", sectionKey, account.stalkerUrl) { totalmvs ->
                    _totalItems.postValue(totalmvs)
                }
            }
        ).flow
    }

    suspend fun getPlexSameItems(
        account: Accounts,
        type: String,
        item: Metadata
    ): List<Metadata> = withContext(Dispatchers.IO) {

        val sameItems = mutableListOf<Metadata>()

        val categoryIds = when (type) {
            "movie" -> plexCatBox.query(
                PlexCategoryOB_.playlistId.equal(account.id).and(PlexCategoryOB_.isMovie.equal(true))
            ).build().find().map { it.plexCatId }

            "serie" -> plexCatBox.query(
                PlexCategoryOB_.playlistId.equal(account.id).and(PlexCategoryOB_.isMovie.equal(false)).and(PlexCategoryOB_.isAudio.equal(false))
            ).build().find().map { it.plexCatId }

            else -> plexCatBox.query(
                PlexCategoryOB_.playlistId.equal(account.id).and(PlexCategoryOB_.isAudio.equal(true))
            ).build().find().map { it.plexCatId }
        }

        categoryIds.forEach { categoryId ->
            val response = plexRepository.getPlexSameItem(
                account.stalkerUrl,
                account.token.orEmpty(),
                categoryId.toInt(),
                item.guid.orEmpty()
            )

            if (response is Resource.Success) {
                val matches = response.data.MediaContainer.Metadata ?: emptyList()
                sameItems.addAll(matches.filter { it.ratingKey != item.ratingKey })
            }
        }

        return@withContext sameItems
    }

    fun updateItemProgress(account: Accounts, ratingKey: String, currentTimeMs: Long, state: String) {
        viewModelScope.launch {
            val response = plexRepository.updateProgress(account.stalkerUrl, ratingKey, currentTimeMs, state, account.token ?: "")
            when (response) {
                is Resource.Success -> {

                }
                is Resource.Error -> {

                }
            }
        }
    }

    fun markItemAsWatched(account: Accounts, ratingKey: String) {
        viewModelScope.launch {
            val response = plexRepository.markasWatched(account.stalkerUrl, ratingKey, account.token ?: "")
            when (response) {
                is Resource.Success -> {
                    Log.d("GUT", "JA")
                }
                is Resource.Error -> {
                    Log.d("GUT", "NEIN")
                }
            }
        }
    }

    fun markItemAsNotWatched(account: Accounts, ratingKey: String) {
        viewModelScope.launch {
            val response = plexRepository.markasNotWatched(account.stalkerUrl, ratingKey, account.token ?: "")
            when (response) {
                is Resource.Success -> {

                }
                is Resource.Error -> {

                }
            }
        }
    }

    fun addItemToPlexWatchlist(account: Accounts, guidKey: String) {
        viewModelScope.launch {
            val response = plexRepository.addToWatchlist(guidKey, account.mainPlexToken ?: "")
            when (response) {
                is Resource.Success -> {
                    Log.d("GUT", "JA")
                }
                is Resource.Error -> {
                    Log.d("GUT", "NEIN")
                }
            }
        }
    }

    fun removeItemFromWatchlist(account: Accounts, guidKey: String) {
        viewModelScope.launch {
            val response = plexRepository.removeFromWatchlist(guidKey, account.mainPlexToken ?: "")
            when (response) {
                is Resource.Success -> {
                    Log.d("GUT", "JA")
                }
                is Resource.Error -> {
                    Log.d("GUT", "NEIN")
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

}