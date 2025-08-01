package com.example.mj_player_tv.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.Accounts_
import com.example.mj_player_tv.database.entity.AudioCategoryOB
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.ChannelPositions_
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
import com.example.mj_player_tv.database.entity.PlexCategoryOB
import com.example.mj_player_tv.database.entity.PlexCategoryOB_
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.database.entity.Programme_
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
import com.example.mj_player_tv.database.entity.TvChannelOB_
import com.example.mj_player_tv.database.help.AccountMovieCategory
import com.example.mj_player_tv.database.help.AccountSeriesCategory
import com.example.mj_player_tv.database.help.AccountTvCategory
import com.example.mj_player_tv.database.help.GlobalSearchItem
import com.example.mj_player_tv.database.help.GlobalSearchMainCategory
import com.example.mj_player_tv.network.model.plex.items.Metadata
import com.example.mj_player_tv.network.model.stalker.movies.MovieData
import com.example.mj_player_tv.network.model.tmdb.imdb_id.TMDB_imdb_id
import com.example.mj_player_tv.network.model.tmdb.moviedetails.TMDBMovieDetails
import com.example.mj_player_tv.network.model.tmdb.seasondetails.TMDBSeasonDetails
import com.example.mj_player_tv.network.model.tmdb.seriesdetails.TMDBSeriesDetails
import com.example.mj_player_tv.repository.EpgUpdateWorker
import com.example.mj_player_tv.repository.ExternEpgProcessState
import com.example.mj_player_tv.repository.HelpRepository
import com.example.mj_player_tv.repository.MatchEpgProcessState
import com.example.mj_player_tv.repository.PlaylistUpdateWorker
import com.example.mj_player_tv.repository.StalkerRepository
import com.example.mj_player_tv.repository.XtreamRepository
import com.example.mj_player_tv.utils.ReminderReceiver
import com.example.mj_player_tv.utils.Resource
import io.objectbox.Box
import io.objectbox.android.AndroidScheduler
import io.objectbox.query.QueryBuilder
import io.objectbox.reactive.DataSubscription
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class HelpViewModel(application: Application): AndroidViewModel(application) {

    private val accountBox = ObjectBox.store.boxFor(Accounts::class.java)
    private val tvChannBox: Box<TvChannelOB> = ObjectBox.store.boxFor(TvChannelOB::class.java)
    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)
    private val tvChanPosBox: Box<ChannelPositions> = ObjectBox.store.boxFor(ChannelPositions::class.java)
    private val movieCatBox: Box<MovieCategoryOB> = ObjectBox.store.boxFor(MovieCategoryOB::class.java)
    private val seriesCatBox: Box<SeriesCategoryOB> = ObjectBox.store.boxFor(SeriesCategoryOB::class.java)
    private val plexCatBox: Box<PlexCategoryOB> = ObjectBox.store.boxFor(PlexCategoryOB::class.java)
    private val movieBox: Box<MovieOB> = ObjectBox.store.boxFor(MovieOB::class.java)
    private val seriesBox: Box<SeriesOB> = ObjectBox.store.boxFor(SeriesOB::class.java)
    private val seasonsBox: Box<SeasonsOB> = ObjectBox.store.boxFor(SeasonsOB::class.java)
    private val episodesBox: Box<EpisodesOB> = ObjectBox.store.boxFor(EpisodesOB::class.java)
    private val epgSourceBox = ObjectBox.store.boxFor(EpgSource::class.java)
    private val epgDataBox = ObjectBox.store.boxFor(EpgDataOB::class.java)
    private val epgChannelBox = ObjectBox.store.boxFor(EpgSourceChannel::class.java)
    private val epgPositionBox = ObjectBox.store.boxFor(EpgSourcePositions::class.java)
    private val settingsBox = ObjectBox.store.boxFor(Settings::class.java)
    private val epgSourcePosBox = ObjectBox.store.boxFor(EpgSourcePositions::class.java)
    private val programmeBox = ObjectBox.store.boxFor(Programme::class.java)

    private val helperRepository = HelpRepository(application)

    private val stalkerRepository = StalkerRepository()
    private val xtreamRepository = XtreamRepository()

    //SETTINGS_CONTAINER

    var isFirstAppStart = true

    var settings: Settings? = null

    var addAccount = 0

    var wasTvSectionOpened = false

    var lastSelectedPlaylistOptionsId: Int? = null

    var lastSelectedPlaylistSettingId: Int? = null

    var lastSelectedPlaylistInfoId: Int? = null

    var lastSelectedLayoutSettingId: Int? = null

    var lastSelectedManageCategoryId: Int? = null

    var lastSelectedEpgSettingId: Int? = null

    var lastSelectedCategoryOptionsMenuId: Int? = null

    var isSettingsContainerOpened: Boolean = false

    var isCategoryManagementOpened: Boolean = false

    var isFullEpgContainerOpened: Boolean = false

    var isChannelOptionsContainerOpened: Boolean = false

    var clickedEpgSourceOptions: EpgSource? = null

    var lastSelectedSettingsMenuViewId: Int? = null

    var lastSelectedPlaylistsPosition: Int? = null

    var assignChannelToEpgActive: Boolean = false

    var isTvAccountMenuOpened: Boolean = false

    var isCurrentlyPlayingTv: Boolean = false

    var currentAssignEpgChannel: TvChannelOB? = null

    var currentAssignChannelPosition: ChannelPositions? = null

    var assignEpgChannelListFiltered: Boolean = false

    var focusShowEpgOrDescription: Boolean = false

    var movieFullScreenOpened: Boolean = false

    var serieFullScreenOpened: Boolean = false

    var isMovieAccountMenuOpened: Boolean = false

    var isSeriesAccountMenuOpened: Boolean = false

    var isPlexAccountMenuOpened: Boolean = false

    var isTvFullScreen: Boolean = false

    var currentFocusedChannPosition: ChannelPositions? = null

    var currentFocusedTvCategory: TvCategoryOB? = null

    var currentFocusedTvAccount: Accounts? = null

    var lastSelectedTvCategoryPosition: Int? = null

    var fullScreenFromAbside = false

    var isLogoSettingsOpened: Boolean = false

    var currentAccountPosition: Int? = null

    var changedTvCategoriesAccountId: Long? = null

    var changedMovieCategoriesAccountId: Long? = null

    var changedSeriesCategoriesAccountId: Long? = null

    var currentSelectedTvCategory: TvCategoryOB? = null

    var selectedAccountData: Accounts? = null

    var currentTvAccount: Accounts? = null

    var currentMovieAccount: Accounts? = null

    var currentAudioAccount: Accounts? = null

    var currentSeriesAccount: Accounts? = null

    var currentPlexAccount: Accounts? = null

    var currentPlexMovieCategory: MovieCategoryOB? = null

    var currentPlexSeriesCategory: SeriesCategoryOB? = null

    var currentPlexAudioCategory: AudioCategoryOB? = null

    var clickedPlexMovieItem: Metadata? = null

    var playMovieSelectionModified: Int? = null

    var playSeriesSelectionModified: Int? = null

    var epgPreviewEpgDetail = false

    var isPlaylistEnableChanged: Boolean = false

    var currentSelectedEpgForSelectedChannel: EpgDataOB? = null

    var currentSelectedFullEpgData: EpgDataOB? = null

    var playlistEpgChanged: Boolean = false

    var modifiedChannelList: Boolean = false

    var fullScreenClickedChannel: TvChannelOB? = null

    var lastPlayedChannel: TvChannelOB? = null

    var currentFocusedChannel: TvChannelOB? = null

    var currentPlayingChannelPosition: ChannelPositions? = null

    var currentPlayingChannel: TvChannelOB? = null

    var currentPlayingTvCategory: TvCategoryOB? = null

    var currentPlayingTvAccount: Accounts? = null

    var currentHudFocusedChannel: TvChannelOB? = null

    var currentHudFocusedChannelPosition: ChannelPositions? = null

    var fullScreenFocusedChannel: TvChannelOB? = null

    var fullScreenFocusedChannelPosition: ChannelPositions? = null

    var fullScreenFocusedTvCategory: TvCategoryOB? = null

    var fullScreenFocusedAccount: Accounts? = null

    var currentFullEpgProgramId: String = ""

    var currentPlayingEpgProgramId: String = ""

    var currentAccountName: String? = null

    var currentAccountUrl: String? = null

    var currentAccountUsername: String? = null

    var currentAccountPassword: String? = null

    var showAllEpgChannelSources = false

    var dataToModifyPlaylist: Int = -1

    var wasPlaylistChanged = false

    var isTvAccountsMenuFocused = false

    var isTvCategoryMenuFocused = false

    var isTvChannelsMenuFocused = false

    var firstOpenTvChFrag = true

    var isPlayingCatchup = false

    var lastPlayingCatchupEpgId: String = ""

    var catchupProgramDuration: Long = 0L

    var catchupEpgData: EpgDataOB? = null

    var isLoadingMovieCategory: String? = null

    var globalSearchCatchupUrl = ""

    var currentFocusedMovie: MovieOB? = null

    var currentFocusedSerie: SeriesOB? = null

    var currentFocusedSeason: SeasonsOB? = null

    var currentFocusedEpisode: EpisodesOB? = null

    var playingMovie: MovieOB? = null

    var playingSerie: SeriesOB? = null

    var playingSeason: SeasonsOB? = null

    var playingEpisode: EpisodesOB? = null

    var focusedSeasons: MutableList<SeasonsOB>? = null

    var focusedEpisodes: MutableList<EpisodesOB>? = null

    var changeTimeOffSetEpgSourceId: Long? = null

    var changeAutoUpdateInterval: Int = -1

    var actualChannelList: List<TvChannelOB>? = null

    var actualEpgData: MutableList<EpgDataOB> = mutableListOf()

    var currentMovieCategoryOB: MovieCategoryOB? = null

    var currentSeriesCategoryOB: SeriesCategoryOB? = null

    var isSearchContainerOpened = false

    var channelFromSearchContainer = false

    var isWatchlistContainerOpened = false

    var isWatchHistoryContainerOpened = false

    var currentMovieImage: String? = null

    var currentSeriesImage: String? = null

    var currentSelectedEpgChannelSource: EpgSource? = null

    var currentlyPlayingUrl = ""

    var currentlyPlayingMovieUrl = ""

    var currentEpgTab = ""

    var globalSearchClickedTvChannelPos: ChannelPositions? = null

    var isFullScreenFullEpg = false

    var changeChannelOrder = false

    var isNowChangingChannelOrder = false

    var onlyCategoryChanged = false

    var isChannelHide = false

    var runTvChannelsSubmitList = true

    var addChannelsToUserCategory = false

    var addChannelsToUserCategoryFromCategory: TvCategoryOB? = null

    var addChannelsToUserCategoryAccount = false

    var addChannelsToUserCategoryFromAccount: Accounts? = null

    var categoryToAddChannelsInto: TvCategoryOB? = null

    var playlistSuccessFullyAdded = false

    var movieSelectionOption = 0

    var selectedGlobalSearchCategory: GlobalSearchMainCategory? = null

    var selectedGlobalSearchAccount: Accounts? = null

    var currentSelectedWatchlist: String = ""

    var currentWatchListMovieAccount: Accounts? = null

    var currentWatchListSeriesAccount: Accounts? = null

    var currentWatchListProgrammeAccount: Accounts? = null

    var currentSelectedWatchHistory: String = ""

    var isTvAccountFocused = false

    var isMovieAccountFocused = false

    var isSeriesAccountFocused = false

    var clickedTvAccountId: Long? = 0L

    var clickedTvAccountPosition: Int = -1

    var clickedMovieAccountId: Long? = 0L

    var clickedMovieAccountPosition: Int = -1

    var clickedSeriesAccountId: Long? = 0L

    var clickedSeriesAccountPosition: Int = -1

    var clickedPlexCategoryId: Long? = 0L

    var currentPlexItemId: String = ""

    var watchstatsContainerOpened = false

    var lastSelectedChannelOptionsMenuView: Int? = null

    private var accountSubscription: DataSubscription? = null

    private var tvCategorySubscription: DataSubscription? = null

    private var movieaccountSubscription: DataSubscription? = null

    private var movieCategorySubscription: DataSubscription? = null

    private var seriesaccountSubscription: DataSubscription? = null

    private var seriesCategorySubscription: DataSubscription? = null

    private var plexaccountSubscription: DataSubscription? = null

    val tvAccountsLiveData = MutableLiveData<List<Accounts>>()

    val movieAccountsLiveData = MutableLiveData<List<Accounts>>()

    val plexAccountsLiveData = MutableLiveData<List<Accounts>>()

    fun updateFocusedChannel(tvChannelPos: ChannelPositions) {
        currentFocusedChannel = tvChannelPos.tvchannel.target
        currentFocusedChannPosition = tvChannelPos
    }

    fun updateFocusedTvCategory(tvCategoryOB: TvCategoryOB) {
        currentFocusedTvCategory = tvCategoryOB
    }

    fun updateFocusedTvAccount(tvAccountOB: Accounts) {
        currentFocusedTvAccount = tvAccountOB
    }

    var lastAccounts: List<Accounts>? = null

    val tvAccountsWithCategoriesLiveData = MutableLiveData<List<AccountTvCategory>>()

    val movieAccountsWithCategoriesLiveData = MutableLiveData<List<AccountMovieCategory>>()

    val seriesAccountsWithCategoriesLiveData = MutableLiveData<List<AccountSeriesCategory>>()

    fun getTvAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val accountQuery = accountBox.query()
                .equal(Accounts_.isSelected, true)
                .equal(Accounts_.showTv, true)
                .build()

            val categoryQuery = tvCatBox.query().build()

            accountSubscription?.cancel()
            tvCategorySubscription?.cancel()

            accountSubscription = accountQuery.subscribe()
                .on(AndroidScheduler.mainThread())
                .observer { accs ->
                    combineAccountsWithTvCategories(accs, categoryQuery.find())
                }

            tvCategorySubscription = categoryQuery.subscribe()
                .on(AndroidScheduler.mainThread())
                .observer { cats ->
                    combineAccountsWithTvCategories(accountQuery.find(), cats)
                }

            // Initial load
            combineAccountsWithTvCategories(accountQuery.find(), categoryQuery.find())
        }
    }

    private fun combineAccountsWithTvCategories(
        accounts: List<Accounts>,
        categories: List<TvCategoryOB>
    ) {
        val combined = accounts.map { acc ->
            AccountTvCategory.Account(
                id = acc.id,
                name = acc.name,
                categories = categories.filter { it.playlistId == acc.id && it.favorite }
                    .map { cat ->
                        AccountTvCategory.TvCategory(
                            id = cat.id,
                            name = cat.showingName,
                            parentId = acc.id,
                            tvCategoryId = cat.tvCatId,
                            isFavoriteCategory = cat.favorite,
                            isAllChannelsCategory = cat.isAllChannelsCategory
                        )
                    }
            )
        }

        tvAccountsWithCategoriesLiveData.postValue(combined)
    }

    fun getMovieAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val accountQuery = accountBox.query()
                .equal(Accounts_.isSelected, true)
                .equal(Accounts_.showVod, true)
                .equal(Accounts_.isPlex, false)
                .build()

            val categoryQuery = movieCatBox.query().build()

            movieaccountSubscription?.cancel()
            movieCategorySubscription?.cancel()

            movieaccountSubscription = accountQuery.subscribe()
                .on(AndroidScheduler.mainThread())
                .observer { accs ->
                    combineAccountsWithMovieCategories(accs, categoryQuery.find())
                }

            movieCategorySubscription = categoryQuery.subscribe()
                .on(AndroidScheduler.mainThread())
                .observer { cats ->
                    combineAccountsWithMovieCategories(accountQuery.find(), cats)
                }

            // Initial load
            combineAccountsWithMovieCategories(accountQuery.find(), categoryQuery.find())
        }
    }

    private fun combineAccountsWithMovieCategories(
        accounts: List<Accounts>,
        categories: List<MovieCategoryOB>
    ) {
        val combined = accounts.map { acc ->
            AccountMovieCategory.Account(
                id = acc.id,
                name = acc.name,
                categories = categories.filter { it.playlistId == acc.id && it.favorite }
                    .map { cat ->
                        AccountMovieCategory.MovieCategory(
                            id = cat.id,
                            name = cat.title,
                            parentId = acc.id,
                            movieCategoryId = cat.movieCatId,
                            isFavoriteCategory = cat.favorite
                        )
                    }
            )
        }

        movieAccountsWithCategoriesLiveData.postValue(combined)
    }

    fun getSeriesAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val accountQuery = accountBox.query()
                .equal(Accounts_.isSelected, true)
                .equal(Accounts_.showVod, true)
                .equal(Accounts_.isPlex, false)
                .build()

            val categoryQuery = seriesCatBox.query().build()

            seriesaccountSubscription?.cancel()
            seriesCategorySubscription?.cancel()

            seriesaccountSubscription = accountQuery.subscribe()
                .on(AndroidScheduler.mainThread())
                .observer { accs ->
                    combineAccountsWithSeriesCategories(accs, categoryQuery.find())
                }

            seriesCategorySubscription = categoryQuery.subscribe()
                .on(AndroidScheduler.mainThread())
                .observer { cats ->
                    combineAccountsWithSeriesCategories(accountQuery.find(), cats)
                }

            // Initial load
            combineAccountsWithSeriesCategories(accountQuery.find(), categoryQuery.find())
        }
    }

    private fun combineAccountsWithSeriesCategories(
        accounts: List<Accounts>,
        categories: List<SeriesCategoryOB>
    ) {
        val combined = accounts.map { acc ->
            AccountSeriesCategory.Account(
                id = acc.id,
                name = acc.name,
                categories = categories.filter { it.playlistId == acc.id && it.favorite }
                    .map { cat ->
                        AccountSeriesCategory.SeriesCategory(
                            id = cat.id,
                            name = cat.title,
                            parentId = acc.id,
                            seriesCategoryId = cat.seriesCatId,
                            isFavoriteCategory = cat.favorite
                        )
                    }
            )
        }

        seriesAccountsWithCategoriesLiveData.postValue(combined)
    }

    fun checkCategoryActivated(tvcategory: TvCategoryOB) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!tvcategory.favorite) {
                tvcategory.favorite = true
                tvCatBox.put(tvcategory)
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            val accountQuery = accountBox.query(
                Accounts_.isSelected.equal(true).and(Accounts_.updateOnAppStart.equal(true))
            ).build()
            val accountsToUpdate = accountQuery.find()
            accountQuery.close()
            for (account in accountsToUpdate) {
                setWorker(account)
            }
        }
    }

    fun getPlexAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val query = accountBox.query()
                .equal(Accounts_.isSelected, true)
                .equal(Accounts_.isPlex, true)
                .build()

            plexaccountSubscription = query.subscribe()
                .on(AndroidScheduler.mainThread())
                .observer { accounts ->
                    plexAccountsLiveData.postValue(accounts)
                }
        }
    }


    fun getUserAccount(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // Erstelle eine Abfrage, um zu prüfen, ob ein Account mit isUserCategories = true existiert
            val accountQuery = accountBox.query(Accounts_.isUserCategories.equal(true)).build()
            val account = accountQuery.findFirst()
            accountQuery.close()

            // Wenn kein Account gefunden wurde, erstelle einen neuen Account
            if (account == null) {
                // Erstelle ein neues Accounts-Objekt mit den gewünschten Standardwerten
                val userAccount = Accounts(
                    id = 0, // Oder ein geeigneter Wert, falls erforderlich
                    name = context.resources.getString(R.string.my_categories), // Setze hier einen Standardwert oder lasse es leer
                    stalkerUrl = "",
                    username = "",
                    macAddress = "",
                    token = "",
                    totalAccountData = "",
                    userAgent = "",
                    isSelected = false,
                    expiryDate = "",
                    timezone = "",
                    usePlaylistLogos = true,
                    useEpgLogos = false,
                    isFavoriteCategoryShowing = false,
                    isStalker = false,
                    isXtream = false,
                    isPlex = false,
                    totalTvCategories = "",
                    totalTvChannels = "",
                    totalMovieCategories = "",
                    totalSeriesCategories = "",
                    lastUpdatedDate = 0L,
                    lastUpdateStatus = 1,
                    tvchannelLoadingOK = 1,
                    tvCategoryLoadingOK = 1,
                    movieCategoryLoadingOK = 1,
                    seriesCategoryLoadingOK = 1,
                    showTv = true,
                    showVod = false,
                    autoUpdateHours = 0,
                    updateOnAppStart = false,
                    orderBy = 0,
                    orderByCategory = 0,
                    isUserCategories = true,
                )
                userAccount.epgsources.addAll(emptyList())
                // Speichern des neuen Accounts-Objekts in der Datenbank
                accountBox.put(userAccount)
            }
        }
    }


    fun getSettings() {
        settings = settingsBox.all.firstOrNull()
        if (settings?.tvReminderTime == 0L) {
            settings?.tvReminderTime = 10L
            settings?.let { settingsBox.put(it) }
        }
    }

    fun updateSelectedAccountData(account: Accounts) {
        selectedAccountData = account
    }



    suspend fun deleteEpgSourceRelatedData(epgSource: EpgSource) {
        cancelAutomaticEpgWorker(epgSource.id)
        withContext(Dispatchers.IO) {
            val epgPositions = epgSourcePosBox.query(
                EpgSourcePositions_.epgSourceIdentifier.equal(epgSource.id)
            ).build().find()
            val positionsToDelete = epgPositions.filter { it.position != -1 }
            for (epgSourcePosition in positionsToDelete) {
                val accountSources = epgSourcePosition.accountId?.let { accountBox.get(it) }?.epgsources
                accountSources?.filter { it.isSelected }?.forEach {
                    val oldPosition = it.position
                    if (oldPosition > epgSourcePosition.position) {
                        it.position = oldPosition - 1
                    }
                    epgSourcePosBox.put(it)
                }
            }
            epgSourcePosBox.remove(epgPositions)
        }
        epgSourceBox.remove(epgSource)

        withContext(Dispatchers.IO) {
            val epgToRemoveQuery = epgDataBox.query(EpgDataOB_.epgSourceId.equal(epgSource.id))
                .build()
            epgToRemoveQuery.remove()
            epgToRemoveQuery.close()
        }
        withContext(Dispatchers.IO) {
            val epgChannelToRemoveQuery = epgChannelBox.query(
                EpgSourceChannel_.relatedepgSourceId.equal(epgSource.id)
            ).build()
            epgChannelToRemoveQuery.remove()
            epgChannelToRemoveQuery.close()
        }

        withContext(Dispatchers.IO) {
            val resetLogos =  tvChannBox.query(
                TvChannelOB_.epgSourceId.equal(epgSource.id)
            ).build().find()
            resetLogos.forEach {
                it.epgLogo = ""
                it.epgSourceId = null
                it.alwaysUsesExternalEpg = false
                it.usesExternalEpg = false
            }
            tvChannBox.put(resetLogos)
        }
        epgTimeOffsetCompleteSuccessful()
    }


    fun updateTvChannelsTimeOffSet(epgSourceId: Int, newTimeOffset: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var channelsToChange : MutableList<TvChannelOB> = mutableListOf()
                channelsToChange = tvChannBox.query(TvChannelOB_.epgSourceId.equal(epgSourceId)).build().find()
                channelsToChange.forEach {
                    it.epgTimeOffSet = newTimeOffset
                }
            }
        }
    }

    private val _assignChannelToEpgComplete = MutableLiveData<Int>(2)
    var assignChannelToEpg: LiveData<Int> = _assignChannelToEpgComplete

    fun assignChannelToEpgCompleteSuccessful() {
        _assignChannelToEpgComplete.postValue(1)
    }

    fun assignChannelToEpgCompleteReset() {
        _assignChannelToEpgComplete.postValue(2)
    }

    private val _epgTimeOffsetComplete = MutableLiveData<Int>(2)
    var epgTimeOffsetComplete: LiveData<Int> = _epgTimeOffsetComplete

    fun epgTimeOffsetCompleteSuccessful() {
        _epgTimeOffsetComplete.postValue(1)
    }

    fun epgTimeOffsetCompleteReset() {
        _epgTimeOffsetComplete.postValue(2)
    }

    private val _channelNamesModifiedComplete = MutableLiveData<Int>(2)
    var channelNamesModifiedComplete: LiveData<Int> = _channelNamesModifiedComplete

    fun channelNamesModifiedCompleteSuccessful() {
        _channelNamesModifiedComplete.postValue(1)
    }

    fun channelNamesModifiedCompleteReset() {
        _channelNamesModifiedComplete.postValue(2)
    }

    private val _epgSourceChangeComplete = MutableLiveData<Int>(2)
    var epgSourceChangeComplete: LiveData<Int> = _epgSourceChangeComplete

    fun epgSourceChangeCompleteSuccessful() {
        _epgSourceChangeComplete.postValue(1)
    }

    fun epgSourceChangeCompleteReset() {
        _epgSourceChangeComplete.postValue(2)
    }

    private val _updateLogoSourceComplete = MutableLiveData<Int>(2)
    var updateLogoSourceComplete: LiveData<Int> = _updateLogoSourceComplete

    fun updateLogoSourceCompleteSuccessful() {
        _updateLogoSourceComplete.postValue(1)
    }

    fun updateLogoSourceCompleteReset() {
        _updateLogoSourceComplete.postValue(2)
    }

    private val _updateTvSortingComplete = MutableLiveData<Int>(2)
    var updateTvSortingComplete: LiveData<Int> = _updateTvSortingComplete

    fun updateTvSortingCompleteCompleteSuccessful() {
        _updateTvSortingComplete.postValue(1)
    }

    fun updateTvSortingCompleteCompleteReset() {
        _updateTvSortingComplete.postValue(2)
    }

    private val _matchAndUpdateComplete = MutableLiveData<Int>(2)
    var matchAndUpdateComplete: LiveData<Int> = _matchAndUpdateComplete

    fun matchAndUpdateCompleteSuccessful() {
        _matchAndUpdateComplete.postValue(1)
    }

    fun matchAndUpdateCompleteReset() {
        _matchAndUpdateComplete.postValue(2)
    }

    private val _updateTvCategoriesComplete = MutableLiveData<Int>(2)
    var updateTvCategoriesComplete: LiveData<Int> = _updateTvCategoriesComplete

    fun updateTvCategoriesCompleteSuccessful() {
        _updateTvCategoriesComplete.postValue(1)
    }

    fun updateTvCategoriesCompleteReset() {
        _updateTvCategoriesComplete.postValue(2)
    }

    private val _updateNamesTvCategoriesComplete = MutableLiveData<Int>(2)
    var updateNamesTvCategoriesComplete: LiveData<Int> = _updateNamesTvCategoriesComplete

    fun updateNamesTvCategoriesCompleteSuccessful() {
        _updateNamesTvCategoriesComplete.postValue(1)
    }

    fun updateNamesTvCategoriesCompleteReset() {
        _updateNamesTvCategoriesComplete.postValue(2)
    }


    private val _updateMovieCategoriesComplete = MutableLiveData<Int>(2)
    var updateMovieCategoriesComplete: LiveData<Int> = _updateMovieCategoriesComplete

    fun updateMovieCategoriesCompleteSuccessful() {
        _updateMovieCategoriesComplete.postValue(1)
    }

    fun updateMovieCategoriesCompleteReset() {
        _updateMovieCategoriesComplete.postValue(2)
    }

    private val _updateSeriesCategoriesComplete = MutableLiveData<Int>(2)
    var updateSeriesCategoriesComplete: LiveData<Int> = _updateSeriesCategoriesComplete

    fun updateSeriesCategoriesCompleteSuccessful() {
        _updateSeriesCategoriesComplete.postValue(1)
    }

    fun updateSeriesCategoriesCompleteReset() {
        _updateSeriesCategoriesComplete.postValue(2)
    }

    private val _updateTvAccountsComplete = MutableLiveData<Int>(2)
    var updateTvAccountsComplete: LiveData<Int> = _updateTvAccountsComplete

    fun updateTvAccountsCompleteSuccessful() {
        _updateTvAccountsComplete.postValue(1)
    }

    fun updateTvAccountsCompleteReset() {
        _updateTvAccountsComplete.postValue(2)
    }

    fun matchAndUpdateChannelsWithEpg(accountData: Accounts) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val job1 = launch { matchChannelsAndEpg(accountData) }
                job1.join()
                matchAndUpdateCompleteSuccessful()
            }
        }
    }

    fun updateChannelsNotUseEpgSource(epgSourceId: Long, accountId: Long, isExternEpg: Boolean) =
        viewModelScope.launch(Dispatchers.IO) {
                epgSourceChangeCompleteReset()
                val account = accountBox.get(accountId)
                val builder = tvChannBox
                    .query(TvChannelOB_.accountId.equal(accountId))
                builder.link(TvChannelOB_.linkedEpgChannel)
                    .apply(EpgSourceChannel_.relatedepgSourceId.equal(epgSourceId))
                val channels = builder.build().find()
                if (isExternEpg) {
                    channels.forEach {
                        Log.d("CHANNEL TO DELETE EPG", "CHANNELNAME: ${it.showingName}")
                    }
                    channels.forEachParallel {
                        it.epgLogo = ""
                        it.usesExternalEpg = false
                        it.alwaysUsesExternalEpg = false
                        it.epgSourceId = account.epgsources.firstOrNull { it.isPlaylistEpg }?.id
                        it.linkedEpgChannel?.target = if (account.epgsources.filter { it.isSelected }.any { it.isPlaylistEpg }) {
                            it.epgChannel?.target
                        } else {
                            null
                        }
                        tvChannBox.put(it)
                    }
                    epgSourceChangeCompleteSuccessful()
                } else {
                    channels.forEach {
                        Log.d("CHANNEL TO DELETE EPG", "INTERN: CHANNELNAME: ${it.showingName}")
                    }
                    channels.forEachParallel {
                        it.linkedEpgChannel?.target = null
                        tvChannBox.put(it)
                    }
                    account.usePlaylistEpg = false
                    accountBox.put(account)
                    epgSourceChangeCompleteSuccessful()
            }
        }

    //GET CHANNEL INFO:


    fun getCurrentTimeWithMilliseconds(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS")
        return sdf.format(Date())  // Gibt die aktuelle Zeit im gewünschten Format zurück
    }

    ///////EPG/////////


    //EXTERNAL EPG
    val epgProcessState: StateFlow<ExternEpgProcessState?> = helperRepository.epgProcessState
    fun resetEpgProcessState() {
        helperRepository.resetEpgProcessState()
    }

    fun loadEpgDataFromExternalSource(url: String, name: String) {
        Log.d("EPGSOURCEADDED", "ADDED: $name")
        val currentDate = System.currentTimeMillis() / 1000
        viewModelScope.launch(Dispatchers.IO) {
            val epgSource = EpgSource(
                0,
                name = name,
                url = url,
                null,
                false,
                true,
                false,
                false,
                false,
                7,
                3,
                0,
                0,
                0L,
                48,
                currentDate,
                false,
                name
            )
            epgSourceBox.put(epgSource)
            val thisEpgSourceQuery = epgSourceBox.query(EpgSource_.uniqueEpgSourceId.equal(name)).build()
            val thisEpgSource = thisEpgSourceQuery.findFirst()
            thisEpgSourceQuery.close()
            if (thisEpgSource != null) {
                val allAccountsQuery = accountBox.query(Accounts_.isStalker.equal(true)
                    .or(Accounts_.isXtream.equal(true)
                        .and(Accounts_.isUserCategories.equal(false)))).build()
                val accounts = allAccountsQuery.find()
                allAccountsQuery.close()
                accounts.forEach {
                    val epgPosition = EpgSourcePositions(
                        id = 0,
                        it.id,
                        thisEpgSource.id,
                        - 1,
                        false,
                        false,
                        "${it.id}_${thisEpgSource.uniqueEpgSourceId}"
                    )
                    epgPosition.relatedaccount.target = it
                    epgPosition.relatedepgsource.target = thisEpgSource
                    epgSourcePosBox.put(epgPosition)
                }
                val response = helperRepository.downloadEpgFromExternalSource(
                    url,
                    thisEpgSource.id
                )
                when (response) {
                    is Resource.Success -> {
                        Log.d("EXTERN EPG SUCCESS", "OK")
                        thisEpgSource.updateSuccessful = true
                        epgSourceBox.put(thisEpgSource)
                        // Aktuelles Datum als Unix-Timestamp
                        val now = System.currentTimeMillis() / 1000L
                        // Berechnung der Grenzzeiten in Unix-Timestamps
                        val minTime = now - (thisEpgSource.minDays * 24 * 60 * 60)
                        val maxTime = now + (thisEpgSource.maxDays * 24 * 60 * 60)

                        // Löschen der veralteten EPG-Daten
                        val deleteDataQuery = epgDataBox.query(
                            EpgDataOB_.epgSourceId.equal(thisEpgSource.id)
                                .and(EpgDataOB_.startTimestamp.less(minTime))
                                .or(EpgDataOB_.stopTimestamp.greater(maxTime))
                        ).build()
                        deleteDataQuery.remove()
                        deleteDataQuery.close()
                        Log.d("EXTERN EPG SUCCESS", "ALLES ERLEDIGT")
                    }
                    is Resource.Error -> {
                        epgSourcePosBox.remove(epgSource.relatedaccounts)
                        epgSourceBox.remove(epgSource)
                        Log.d("EXTERN EPG SUCCESS", "${response.message}")
                        resetEpgProcessState()
                    }
                }
            }
        }
    }

    private val _matchEpgProcessState = MutableStateFlow<MatchEpgProcessState?>(null)
    val matchEpgProcessState: StateFlow<MatchEpgProcessState?> = _matchEpgProcessState

    fun resetMatchEpgProcessState() {
        _matchEpgProcessState.value = null
    }


    suspend fun matchChannelsAndEpg(accountData: Accounts) {
        val matchedChannels = mutableListOf<TvChannelOB>()
        val matchedChannelIds = Collections.synchronizedSet(mutableSetOf<String>())
        _matchEpgProcessState.value = MatchEpgProcessState.Loading
        withContext(Dispatchers.IO) {
            try {
                val epgSources = accountData.epgsources
                    .filter { it.isSelected }
                    .sortedBy { it.position }
                    .map { it.relatedepgsource.target }

                epgSources.forEachIndexed { index, epgSource ->
                    Log.d(
                        "PLAYLISTEPG MATCHING:",
                        "POS: $index == ${epgSource.name}"
                    )
                }
                val onlyExternalEpgSources = epgSources.filter { !it.isPlaylistEpg }
                val playlistEpg = epgSources.find { it.isPlaylistEpg }
                val playlistIndex = playlistEpg?.let { epgSources.indexOf(it) } ?: -1

                val selectedCategories = accountData.tvcategories
                    .filter { it.favorite && !it.isFavoriteCategory && !it.isAllChannelsCategory }

                val epgChannelMap = onlyExternalEpgSources.associateWith { epgSource ->
                    epgSource.epgchs.associateWith { epgChannel ->
                        epgChannel.display_name.map { it.lowercase() }.toHashSet()
                    }
                }


                // Parallelisiere Matching durch async
                val matchedCategoryJobs = selectedCategories.chunked(10).map { categoryChunk ->
                    async {
                        val matchedChannelsInChunk = mutableListOf<TvChannelOB>()
                        categoryChunk.forEach { category ->
                            val channelsInCategory = category.tvChannelLink.map { it.tvchannel.target }
                            channelsInCategory.forEach { tvChannel ->
                                if (!matchedChannelIds.contains(tvChannel.idByAccountData)) {
                                    val matchingEpgChannel = findMatchingEpgChannel(tvChannel, epgChannelMap)
                                    if (matchingEpgChannel != null) {
                                        val modifiedChannel = updateTvChannelWithEpgChannel(tvChannel, matchingEpgChannel, playlistIndex, epgSources)
                                        matchedChannelIds.add(tvChannel.idByAccountData)
                                        matchedChannelsInChunk.add(modifiedChannel)
                                    }
                                }
                            }
                        }
                        matchedChannelsInChunk
                    }
                }

                matchedCategoryJobs.awaitAll().flatten().let { matchedChannels.addAll(it) }

                tvChannBox.put(matchedChannels)
                _matchEpgProcessState.value = MatchEpgProcessState.Success
                // Zeitdifferenz berechnen und in Sekunden umwandeln
                matchAndUpdateCompleteSuccessful()
            } catch (e: Exception) {
                Log.e("ERROR MATCHING CHANNELS", "Exception in matchChannelsAndEpg: ${e.message}", e)
            }
        }
    }

    private fun findMatchingEpgChannel(
        tvChannel: TvChannelOB,
        epgChannelMap: Map<EpgSource, Map<EpgSourceChannel, Set<String>>>
    ): EpgSourceChannel? {
        val tvChannelId = tvChannel.xmltv_id.lowercase()
        val tvChannelName = tvChannel.showingName.lowercase()
        for ((_, epgChannels) in epgChannelMap) {
            // 1. Suche über die XMLTV-ID
            val matchingEpgChannel = epgChannels.keys.firstOrNull { it.chId.equals(tvChannelId, ignoreCase = true) }
            if (matchingEpgChannel != null) {
                return matchingEpgChannel
            }

            // 2. Suche über den Namen
            val matchingByName = epgChannels.entries.firstOrNull { (_, names) ->
                names.contains(tvChannelName)
            }?.key
            if (matchingByName != null) {
                return matchingByName
            }
        }
        Log.d("MATCHING TV & EPG", "${tvChannel.showingName}  === NULL")
        return null
    }

    private fun updateTvChannelWithEpgChannel(
        tvChannel: TvChannelOB,
        epgChannel: EpgSourceChannel,
        playlistIndex: Int,
        epgSources: List<EpgSource>
    ): TvChannelOB {
        // Überprüfen, ob der EPG-Kanal verwendet werden soll
        val epgSourceIndex = epgSources.indexOf(epgChannel.epgsource.target)
        val shouldUseExternalEpg = ((playlistIndex == -1 || epgSourceIndex < playlistIndex) && !tvChannel.alwaysUsesExternalEpg) || ((
                playlistIndex == -1 || epgSourceIndex > playlistIndex) && tvChannel.epgChannel?.target == null
                )
        return tvChannel.apply {
            if (shouldUseExternalEpg) {
                // Externe EPG-Daten verwenden
                Log.d("MATCHING TV & EPG", "${tvChannel.showingName} MATCHED: ${epgChannel.name} === EXTERN")
                usesExternalEpg = true
                epgSourceId = epgChannel.epgsource.target.id
                epgLogo = epgChannel.icon?.firstOrNull().orEmpty()
                linkedEpgChannel?.target = epgChannel
            } else {
                // Interne EPG-Daten verwenden
                Log.d("MATCHING TV & EPG", "${tvChannel.showingName} MATCHED: ${epgChannel.name} === INTERN")
                usesExternalEpg = false
                epgSourceId = null
                epgLogo = epgChannel.icon?.firstOrNull().orEmpty()
                linkedEpgChannel?.target = if (playlistIndex != -1) {
                    tvChannel.epgChannel?.target
                } else {
                    null
                }
            }
        }
    }

    // Erweiterungsfunktion, um parallele Verarbeitung zu ermöglichen
    suspend fun <T> Iterable<T>.forEachParallel(action: suspend (T) -> Unit) {
        coroutineScope {
            map { async { action(it) } }.awaitAll()
        }
    }

    var singleMatch = false

    suspend fun matchSingleChannelWithEpgChannels(tvChannel: TvChannelOB, account: Accounts): TvChannelOB {
        singleMatch = false
        account.epgsources.filter { it.isSelected }.sortedBy { it.position }.forEach {
            if (!singleMatch) {
                val sourceEpgChannels = it.relatedepgsource.target.epgchs
                val matchingEpgChannel = sourceEpgChannels.find { epgChannel ->
                    epgChannel.display_name.any {
                        it.equals(
                            tvChannel.showingName,
                            ignoreCase = true
                        )
                    } || epgChannel.chId.equals(tvChannel.xmltv_id, ignoreCase = true)
                }
                if (matchingEpgChannel != null) {
                    singleMatch = true
                    Log.d("MATCHSINGLE", tvChannel.showingName)
                    tvChannel.usesExternalEpg = true
                    tvChannel.epgSourceId = it.id
                    tvChannel.epgLogo =
                        if (matchingEpgChannel.icon?.isNotEmpty() == true) matchingEpgChannel.icon!!.first() else ""
                    tvChannel.epgChannel?.target = matchingEpgChannel
                }
            }
        }
        return tvChannel
    }

    suspend fun updatePrefixesAndSuffixes(
        prefixes: List<String>,
        suffixes: List<String>
    ) {
        withContext(Dispatchers.IO) {
            val allChannels = tvChannBox.all

            val modifiedChannels = allChannels.mapNotNull { channel ->
                var modifiedName = channel.editedName

                prefixes.firstOrNull { modifiedName.startsWith(it) }?.let {
                    modifiedName = modifiedName.removePrefix(it).trim()
                }

                suffixes.firstOrNull { modifiedName.endsWith(it) }?.let {
                    modifiedName = modifiedName.removeSuffix(it).trim()
                }

                if (channel.showingName != modifiedName) {
                    channel.showingName = modifiedName
                    channel
                } else null
            }

            if (modifiedChannels.isNotEmpty()) {
                tvChannBox.put(modifiedChannels)
            }

            epgTimeOffsetCompleteSuccessful()
        }
    }




    suspend fun updatePrefixesAndSuffixesTvCategories(
        prefixes: List<String>,
        suffixes: List<String>
    ) {
        withContext(Dispatchers.IO) {
            val modifiedCategories = tvCatBox.all.mapNotNull { category ->
                var modifiedName = category.editedName

                prefixes.firstOrNull { modifiedName.startsWith(it) }?.let {
                    modifiedName = modifiedName.removePrefix(it).trim()
                }

                suffixes.firstOrNull { modifiedName.endsWith(it) }?.let {
                    modifiedName = modifiedName.removeSuffix(it).trim()
                }

                if (category.showingName != modifiedName) {
                    category.showingName = modifiedName
                    category
                } else null
            }

            if (modifiedCategories.isNotEmpty()) {
                tvCatBox.put(modifiedCategories)
            }

            epgTimeOffsetCompleteSuccessful()
        }
    }


    suspend fun updatePrefixesAndSuffixesMovieCategories(prefixes: MutableList<String>, suffixes: MutableList<String>) {
        viewModelScope.launch {
            // Update the channel names in the database
            val modifiedMovieCategories: MutableList<MovieCategoryOB> = mutableListOf()
            val modifiedSeriesCategories: MutableList<SeriesCategoryOB> = mutableListOf()
            val allCategories = withContext(Dispatchers.IO) { movieCatBox.all }
            val allSeriesCategories = withContext(Dispatchers.IO) { seriesCatBox.all }
            allCategories.map { category ->
                var modifiedName = category.editedName
                prefixes.forEach { prefix ->
                    if (modifiedName.startsWith(prefix)) {
                        modifiedName = modifiedName.removePrefix(prefix).trim()
                    }
                }
                suffixes.forEach { suffix ->
                    if (modifiedName.endsWith(suffix)) {
                        modifiedName = modifiedName.removeSuffix(suffix).trim()
                    }
                }
                category.showingName= modifiedName
                modifiedMovieCategories.add(category)
            }
            movieCatBox.put(modifiedMovieCategories)
            allSeriesCategories.map { category ->
                var modifiedName = category.editedName
                prefixes.forEach { prefix ->
                    if (modifiedName.startsWith(prefix)) {
                        modifiedName = modifiedName.removePrefix(prefix).trim()
                    }
                }
                suffixes.forEach { suffix ->
                    if (modifiedName.endsWith(suffix)) {
                        modifiedName = modifiedName.removeSuffix(suffix).trim()
                    }
                }
                category.showingName= modifiedName
                modifiedSeriesCategories.add(category)
            }
            seriesCatBox.put(modifiedSeriesCategories)
            updateMovieCategoriesCompleteSuccessful()
        }
    }

    fun deleteOldEpgData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val currentDate = Calendar.getInstance().time
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                val epgSources = epgSourceBox.all
                for (source in epgSources) {
                    val minDays = source.minDays
                    val calendar = Calendar.getInstance()
                    calendar.time = currentDate
                    calendar.add(Calendar.DAY_OF_YEAR, -minDays)
                    val cutoffDate = calendar.time
                    val cutoffDateString = dateFormat.format(cutoffDate)
                    val deleteEpgQuery = epgDataBox.query(EpgDataOB_.datum.less(cutoffDateString).and(EpgDataOB_.epgSourceId.equal(source.id))).build()
                    deleteEpgQuery.remove()
                    deleteEpgQuery.close()
                }
            }
        }
    }

    fun deletePlaylist(accountData: Accounts) {
        viewModelScope.launch(Dispatchers.IO) {
            cancelAutomaticWorker(accountData.id)
            val accountId = accountData.id
            if (!accountData.isPlex) {
                val tvChannelQuery = tvChannBox.query(
                    TvChannelOB_.accountId.equal(
                        accountId
                    )
                )
                    .build()
                val tvChannelsToDelete = tvChannelQuery.find()
                tvChannelQuery.close()
                tvChannBox.remove(tvChannelsToDelete)
                accountData.tvcategories.forEach {
                    tvChanPosBox.remove(it.tvChannelLink)
                }
                val tvCategoriesToDelete =
                    tvCatBox.query(TvCategoryOB_.playlistId.equal(accountId)).build().find()
                tvCatBox.remove(tvCategoriesToDelete)
            }
            if (accountData.isPlex) {
                val plexCategoriesToDelete = plexCatBox.query(PlexCategoryOB_.playlistId.equal(accountId)).build().find()
                plexCatBox.remove(plexCategoriesToDelete)
            }
            val movieCategoriesToDelete = movieCatBox.query(MovieCategoryOB_.playlistId.equal(accountId)).build().find()
            movieCatBox.remove(movieCategoriesToDelete)
            val seriesCategoriesToDelete = seriesCatBox.query(SeriesCategoryOB_.playlistId.equal(accountId)).build().find()
            seriesCatBox.remove(seriesCategoriesToDelete)
            if (!accountData.isPlex) {
                epgPositionBox.remove(accountData.epgsources)
                val playlistEpgSource =
                    accountData.epgsources.first { it.isPlaylistEpg }.relatedepgsource.target
                epgSourceBox.remove(playlistEpgSource)
            }
            movieBox.query(
                MovieOB_.accountId.equal(accountId)
            ).build().remove()
            val series = seriesBox.query(
                SeriesOB_.accountId.equal(accountId)
            ).build().find()
            series.forEach {
                val seasons = seasonsBox.query(
                    SeasonsOB_.seriesIdByAccount.equal(it.idByAccountData)
                ).build().find()
                val episodes = episodesBox.query(
                    EpisodesOB_.seriesIdByAccount.equal(it.idByAccountData)
                ).build().find()
                episodesBox.remove(episodes)
                seasonsBox.remove(seasons)
            }
            seriesBox.remove(series)
            accountBox.remove(accountData.id)
            cancelAutomaticWorker(accountId)
        }
    }


    fun setWorker(account: Accounts) {

        val workManager = WorkManager.getInstance(this.getApplication<Application>().applicationContext)

        // Überprüfe, ob ein Worker für diese Playlist läuft oder geplant ist
        val workInfos = workManager.getWorkInfosByTag("autoupdate_${account.id}").get()
        val isRunningOrQueued = workInfos.any {
            it.state == WorkInfo.State.RUNNING
        }

        if (isRunningOrQueued) {
            return // Kein neuer Worker wird geplant
        }

        // Erstelle den Worker
        val workRequest = OneTimeWorkRequestBuilder<PlaylistUpdateWorker>()
            .setInputData(workDataOf("accountId" to account.id))
            .addTag("autoupdate_${account.id}")
            .build()

        // Plane den Worker
        workManager.enqueueUniqueWork(
            "autoupdate_${account.id}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        Log.d("NEWWORKER", "ACCOUNTWORKER: ${account.name} JETZT")
    }

    fun setWorkerWithDelay(account: Accounts) {
        val workManager = WorkManager.getInstance(this.getApplication<Application>().applicationContext)
        val delay = account.autoUpdateHours * 3600000L
        val executionTimeMillis = System.currentTimeMillis() + delay

        // Formatierte Zeit (z.B. "HH:mm:ss")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val executionTime = dateFormat.format(Date(executionTimeMillis))
        val nextWorkRequest = OneTimeWorkRequestBuilder<PlaylistUpdateWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("accountId" to account.id))
            .addTag("autoupdate_${account.id}")
            .build()
        workManager.enqueueUniqueWork("autoupdate_${account.id}", ExistingWorkPolicy.REPLACE, nextWorkRequest)
        Log.d("NEWWORKER", "ACCOUNTWORKER: ${account.name} UM: $executionTime")
    }

    private fun cancelAutomaticWorker(accountId: Long) {
        // Abbrechen des Workers mit dem Tag, der das Account-ID enthält
        WorkManager.getInstance(this.getApplication<Application>().applicationContext)
            .cancelAllWorkByTag("autoupdate_$accountId")
    }

    private fun cancelAutomaticEpgWorker(epgSourceId: Long) {
        // Abbrechen des Workers mit dem Tag, der das Account-ID enthält
        WorkManager.getInstance(this.getApplication<Application>().applicationContext)
            .cancelAllWorkByTag("autoupdate_$epgSourceId")
    }

    fun setEpgWorker(epgSource: EpgSource) {
        val workManager = WorkManager.getInstance(this.getApplication<Application>().applicationContext)

        // Überprüfe, ob ein Worker für diese Playlist läuft oder geplant ist
        val workInfos = workManager.getWorkInfosByTag("autoupdate_${epgSource.name}").get()
        val isRunningOrQueued = workInfos.any {
            it.state == WorkInfo.State.RUNNING
        }

        if (isRunningOrQueued) {
            return // Kein neuer Worker wird geplant
        }

        workManager.cancelAllWorkByTag("autoupdate_${epgSource.name}")
        // Erstelle den Worker
        val workRequest = OneTimeWorkRequestBuilder<EpgUpdateWorker>()
            .setInputData(workDataOf("epgSourceId" to epgSource.id)) // Stelle sicher, dass "epgId" korrekt übergeben wird
            .addTag("autoupdate_${epgSource.name}")
            .build()

        // Plane den Worker
        workManager.enqueueUniqueWork("autoupdateepg_${epgSource.id}",ExistingWorkPolicy.REPLACE, workRequest)
    }

    fun setEpgWorkerWithDelay(epgSource: EpgSource) {
        val workManager = WorkManager.getInstance(this.getApplication<Application>().applicationContext)
        val delay = epgSource.automaticUpdateDays * 3600000L
        val executionTimeMillis = System.currentTimeMillis() + delay
        val now = System.currentTimeMillis() / 1000L

        // Berechnung der Grenzzeiten in Unix-Timestamps
        val minTime = now - (epgSource.minDays * 24 * 60 * 60)
        val maxTime = now + (epgSource.maxDays * 24 * 60 * 60)
        // Formatierte Zeit (z.B. "HH:mm:ss")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val executionTime = dateFormat.format(Date(executionTimeMillis))
        val nextWorkRequest = OneTimeWorkRequestBuilder<EpgUpdateWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("epgSourceId" to epgSource.id))
            .addTag("autoupdate_${epgSource.name}")
            .build()
        workManager.enqueueUniqueWork("autoupdateepg_${epgSource.id}",ExistingWorkPolicy.REPLACE, nextWorkRequest)
        Log.d("NEWWORKER", "EPGWORKER: ${epgSource.name} UM: $executionTime")
    }

    private val _searchResults = MutableStateFlow<List<GlobalSearchItem>>(emptyList())
    val searchResults: StateFlow<List<GlobalSearchItem>> = _searchResults.asStateFlow()

    fun resetGlobalSearchData() {
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var globalSearchJob: Job? = null

    fun cancelGlobalSearchJob() {
        globalSearchJob?.cancel()
        _isSearching.value = false
    }

    var hasSearched = false

    fun makeGlobalSearch(showFilteredCategories: Boolean, searchString: String) {
        globalSearchJob?.cancel()
        globalSearchJob = viewModelScope.launch {
            Log.d("GLOBALSEARCHFOR", "$showFilteredCategories")
            _isSearching.value = true
            withContext(Dispatchers.IO) {

                val builder = tvChanPosBox.query()
                builder.link(ChannelPositions_.tvchannel).apply(
                    TvChannelOB_.showingName.contains(searchString, QueryBuilder.StringOrder.CASE_INSENSITIVE)
                )
                builder.link(ChannelPositions_.tvcategory).apply(
                    TvCategoryOB_.favorite.equal(true)
                )
                val tvChannelPositions = if (showFilteredCategories) {
                    builder.link(ChannelPositions_.tvcategory).apply(
                        TvCategoryOB_.favorite.equal(true)
                    )
                    builder.build().find().groupBy { it.tvcategory.target.tvaccount.target }.toSortedMap(
                        compareBy { it.name.lowercase() }
                    )
                } else {
                    builder.build().find().groupBy { it.tvcategory.target.tvaccount.target }.toSortedMap(
                        compareBy { it.name.lowercase() }
                    )
                }
                tvChannelPositions.forEach { (account, channels) ->
                    if (channels.isNotEmpty()) {
                        _searchResults.update { it + GlobalSearchItem.TvChannels(account, channels) }
                    }
                }

                val searchProgramsJob = async { searchPrograms(searchString, showFilteredCategories) }

                val playlistsQuery = accountBox.query(Accounts_.isSelected.equal(true)).order(Accounts_.name).build()
                val playlists = playlistsQuery.find()
                playlistsQuery.close()

                // Parallel für jede Playlist ausführen
                val tasks = playlists.sortedBy { it.name }.map { playlist ->

                    async { processPlaylist(playlist, searchString, showFilteredCategories) }
                }

                // Warten, bis alle Playlists fertig sind
                tasks.awaitAll()
                searchProgramsJob.await()
                hasSearched = true
            }
            _isSearching.value = false
        }
    }

    private suspend fun processPlaylist(playlist: Accounts, searchString: String, showFilteredCategories: Boolean) {
        val currentMoviesMap = movieBox.query(MovieOB_.accountId.equal(playlist.id))
            .build().find().associateBy { it.idByAccountData }

        val currentSeriesMap = seriesBox.query(SeriesOB_.accountId.equal(playlist.id))
            .build().find().associateBy { it.idByAccountData }

        // Starte parallele Tasks für Programme, Filme und Serien
        coroutineScope {
            val moviesJob = async {
                if (showFilteredCategories) {
                    val favMovieCats = movieCatBox.query(MovieCategoryOB_.favorite.equal(true)).build().find()
                    val allMovies = mutableListOf<MovieOB>()
                    for (cat in favMovieCats) {
                        val moviesForCat = when {
                            playlist.isStalker -> searchStalkerMoviesByCategory(playlist, cat.movieCatId, searchString, currentMoviesMap).await()
                            playlist.isXtream -> searchXtreamMoviesByCategory(playlist, cat.movieCatId, searchString, currentMoviesMap).await()
                            else -> emptyList()
                        }
                        allMovies.addAll(moviesForCat)
                    }
                    allMovies
                } else {
                    getMoviesForPlaylist(playlist, searchString, emptyMap())
                }
            }

            val seriesJob = async {
                if (showFilteredCategories) {
                    val favSeriesCats = seriesCatBox.query(SeriesCategoryOB_.favorite.equal(true)).build().find()
                    val allSeries = mutableListOf<SeriesOB>()
                    for (cat in favSeriesCats) {
                        val seriesForCat = when {
                            playlist.isStalker -> searchStalkerSeriesByCategory(playlist, cat.seriesCatId, searchString, currentSeriesMap).await()
                            playlist.isXtream -> searchXtreamSeriesByCategory(playlist, cat.seriesCatId, searchString, currentSeriesMap).await()
                            else -> emptyList()
                        }
                        allSeries.addAll(seriesForCat)
                    }
                    allSeries
                } else {
                    getSeriesForPlaylist(playlist, searchString, emptyMap())
                }
            }

            val movies = moviesJob.await()
            if (movies.isNotEmpty()) {
                _searchResults.update { it + GlobalSearchItem.Movies(playlist, movies) }
            }

            val series = seriesJob.await()
            if (series.isNotEmpty()) {
                _searchResults.update { it + GlobalSearchItem.Series(playlist, series) }
            }
        }
    }

    fun searchPrograms(searchQuery: String, showFilteredCategories: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {

            val matchingEpgEntries = epgDataBox
                .query(EpgDataOB_.name.contains(searchQuery, QueryBuilder.StringOrder.CASE_INSENSITIVE))
                .build()
                .find()


            val uniqueEpgChIds = matchingEpgEntries.mapNotNull { it.epgChId }.distinct()

            val matchingEpgSourceChannels = epgChannelBox
                .query(EpgSourceChannel_.chEpgId.oneOf(uniqueEpgChIds.toTypedArray()))
                .build()
                .find()


            val epgChannelIdMap = matchingEpgSourceChannels.associateBy { it.chEpgId }

            // Liste der TV-Kanäle initialisieren
            val matchingChannels: MutableList<String> = mutableListOf()
            matchingEpgSourceChannels.forEach { epgSourceCh ->
                val builder = tvChannBox.query()
                builder.link(TvChannelOB_.linkedEpgChannel).apply(
                    EpgSourceChannel_.chEpgId.equal(epgSourceCh.chEpgId)
                )
                matchingChannels.addAll(builder.build().find().mapNotNull { it.idByAccountData }.distinct())
            }

            val matchingChannelPositions = if (!showFilteredCategories) {
                tvChanPosBox.query(
                    ChannelPositions_.channel.oneOf(matchingChannels.toTypedArray())
                ).build().find()
            } else {
                tvChanPosBox.query(
                    ChannelPositions_.channel.oneOf(matchingChannels.toTypedArray())
                ).build().find().filter { it.tvcategory.target?.favorite == true }
            }

            if (matchingChannelPositions.isNotEmpty()) {

                val groupedTvChannels = matchingChannelPositions.map { tvChannel ->
                    val epgChannelId = tvChannel.tvchannel.target.linkedEpgChannel?.target?.chEpgId
                    val epgDataForChannel = matchingEpgEntries.filter { it.epgChId == epgChannelId }
                    tvChannel to epgDataForChannel
                }

                val accountToTvChannelMap: Map<Accounts, Map<ChannelPositions, List<EpgDataOB>>> =
                    groupedTvChannels
                        .filter {
                            it.first.tvcategory.target?.tvaccount?.target != null
                        }
                        .groupBy {
                            it.first.tvcategory.target!!.tvaccount.target!!
                        }
                        .mapValues { entry ->
                            entry.value.groupBy { it.first }
                                .mapValues { tvChannelEntry -> tvChannelEntry.value.flatMap { it.second } }
                        }


                accountToTvChannelMap.forEach { (account, channelMap) ->
                    if (channelMap.isNotEmpty()) {
                        val asList = channelMap.map { it.key to it.value }
                        _searchResults.update { it + GlobalSearchItem.Programs(account, asList) }
                    }
                }

            } else {
                return@launch
            }
        }
    }

    private suspend fun getMoviesForPlaylist(
        playlist: Accounts,
        searchString: String,
        currentMoviesMap: Map<String, MovieOB>
    ): List<MovieOB> {
        return when {
            playlist.isXtream -> {
                val response = xtreamRepository.getXtreamAllMovies(playlist)
                when (response) {
                    is Resource.Success -> {
                        response.data.filter { it.name?.contains(searchString, ignoreCase = true) == true }.map { movieData ->
                            val idByAccountData = "${movieData.stream_id}_${playlist.id}"
                            currentMoviesMap[idByAccountData] ?: MovieOB(
                                id = 0,
                                idByAccountData = idByAccountData,
                                movieId = movieData.stream_id.toString(),
                                relatedMovieCategoryId = movieData.category_id ?: "",
                                accountName = playlist.name,
                                accountId = playlist.id,
                                movieName = movieData.name,
                                screenshot_uri = movieData.stream_icon,
                                xtreamExtension = movieData.container_extension ?: ""
                            )
                        }
                    }
                    is Resource.Error -> {
                        emptyList()
                    }
                }
            }
            playlist.isStalker -> {
                val response = stalkerRepository.searchMoviesByCategory(
                    playlist.stalkerUrl,
                    "mac=${playlist.macAddress}; stb_lang=de; timezone=${playlist.timezone};",
                    "Bearer ${playlist.token}",
                    "0",
                    1,
                    playlist.userAgent,
                    searchString
                )
                when (response) {
                    is Resource.Success -> {
                        val searchMoviesList: MutableSet<MovieOB> = mutableSetOf()
                        val maxItemsPerPage =  response.data.js.max_page_items
                        val totalItems = response.data.js.total_items
                        val totalPages = ceil(totalItems.toDouble() / maxItemsPerPage.toDouble()).toInt()
                        val stalkermovies = response.data.js.data.map { movieData ->
                            val idByAccountData = "${movieData.id}_${playlist.id}"
                            currentMoviesMap[idByAccountData] ?: convertToMovie(movieData, playlist)
                        }
                        searchMoviesList.addAll(stalkermovies)
                        if (totalPages > 1) {
                            val pagesToCheck = createSequentialList(2, totalPages)
                            for (page in pagesToCheck) {
                                val newresponse = stalkerRepository.searchMoviesByCategory(
                                    playlist.stalkerUrl,
                                    "mac=${playlist.macAddress}; stb_lang=de; timezone=${playlist.timezone};",
                                    "Bearer ${playlist.token}",
                                    "0",
                                    page,
                                    playlist.userAgent,
                                    searchString
                                )
                                when (newresponse) {
                                    is Resource.Success -> {
                                        val newmovies = newresponse.data.js.data.map { movieData ->
                                            val idByAccountData = "${movieData.id}_${playlist.id}"
                                            currentMoviesMap[idByAccountData] ?: convertToMovie(movieData, playlist)
                                        }
                                        searchMoviesList.addAll(newmovies)
                                    }
                                    is Resource.Error -> {

                                    }
                                }
                            }
                        }
                        searchMoviesList.toMutableList()
                    }
                    is Resource.Error -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    fun searchStalkerMoviesByCategory(
        account: Accounts,
        categoryId: String,
        searchTerm: String,
        currentMoviesMap: Map<String, MovieOB>
    ): Deferred<Set<MovieOB>> = viewModelScope.async{
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

    fun searchXtreamMoviesByCategory(
        account: Accounts,
        movieCategoryId: String,
        searchString: String,
        currentMoviesMap: Map<String, MovieOB>
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
                    val movies = response.data.filter { it.name?.contains(searchString, ignoreCase = true) == true }.map { movieData ->
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


    private suspend fun getSeriesForPlaylist(
        playlist: Accounts,
        searchString: String,
        currentSeriesMap: Map<String, SeriesOB>
    ): List<SeriesOB> {
        return when {
            playlist.isXtream -> {
                val response = xtreamRepository.getXtreamAllSeries(playlist)
                when (response) {
                    is Resource.Success -> {
                        response.data
                            .filter { it.name?.contains(searchString, ignoreCase = true) == true }
                            .map { seriesData ->
                                val idByAccountData = "${seriesData.series_id}_${playlist.id}"
                                val existing = currentSeriesMap[idByAccountData]

                                if (existing != null) {
                                    existing
                                } else {
                                    val seriesOB = SeriesOB(
                                        id = 0,
                                        idByAccountData = idByAccountData,
                                        accountId =  playlist.id,
                                        seriesId = seriesData.series_id.toString(),
                                        relatedSeriesCategoryId = seriesData.category_id ?: "",
                                        seriesName = seriesData.name ?: "",
                                        screenshot_uri = seriesData.cover,
                                        // alle weiteren Felder, die du brauchst
                                    )
                                    // ️ Relation setzen:
                                    seriesOB.seriesAccount.target = playlist
                                    seriesOB
                                }
                            }
                    }

                    is Resource.Error -> emptyList()
                }
            }
            playlist.isStalker -> {
                val response = stalkerRepository.searchSeriesByCategory(
                    playlist.stalkerUrl,
                    "mac=${playlist.macAddress}; stb_lang=de; timezone=${playlist.timezone};",
                    "Bearer ${playlist.token}",
                    "0",
                    1,
                    playlist.userAgent,
                    searchString
                )
                when (response) {
                    is Resource.Success -> {
                        val searchSeriesList: MutableSet<SeriesOB> = mutableSetOf()
                        val maxItemsPerPage =  response.data.js.max_page_items
                        val totalItems = response.data.js.total_items
                        val totalPages = ceil(totalItems.toDouble() / maxItemsPerPage.toDouble()).toInt()
                        val stalkerseries = response.data.js.data.map { seriesData ->
                            val idByAccountData = "${seriesData.id}_${playlist.id}"
                            currentSeriesMap[idByAccountData] ?: convertToSeriesOB(seriesData, playlist)
                        }
                        searchSeriesList.addAll(stalkerseries)
                        if (totalPages > 1) {
                            val pagesToCheck = createSequentialList(2, totalPages)
                            for (page in pagesToCheck) {
                                val newresponse = stalkerRepository.searchSeriesByCategory(
                                    playlist.stalkerUrl,
                                    "mac=${playlist.macAddress}; stb_lang=de; timezone=${playlist.timezone};",
                                    "Bearer ${playlist.token}",
                                    "0",
                                    page,
                                    playlist.userAgent,
                                    searchString
                                )
                                when (newresponse) {
                                    is Resource.Success -> {
                                        val newSeries = newresponse.data.js.data.map { seriesData ->
                                            val idByAccountData = "${seriesData.id}_${playlist.id}"
                                            currentSeriesMap[idByAccountData] ?: convertToSeriesOB(seriesData, playlist)
                                        }
                                        searchSeriesList.addAll(newSeries)
                                    }
                                    is Resource.Error -> {

                                    }
                                }
                            }
                        }
                        searchSeriesList.toMutableList()
                    }
                    is Resource.Error -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    fun searchStalkerSeriesByCategory(
        account: Accounts,
        categoryId: String,
        searchTerm: String,
        currentSeriesMap: Map<String, SeriesOB>
    ): Deferred<Set<SeriesOB>> = viewModelScope.async{
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

    fun searchXtreamSeriesByCategory(
        account: Accounts,
        serieCategoryId: String,
        searchString: String,
        currentSeriesMap: Map<String, SeriesOB>
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

                    val serien = response.data.filter { it.name?.contains(searchString, ignoreCase = true) == true }.map { seriesData ->
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

    private fun convertToSeriesOB(seriesData: com.example.mj_player_tv.network.model.stalker.series.SeriesData, account: Accounts): SeriesOB {
        return SeriesOB(
            id = 0,
            idByAccountData = "${seriesData.id}_${account.id}",
            seriesId = seriesData.id ?: "",
            relatedSeriesCategoryId = seriesData.category_id ?: "",
            seriesName = seriesData.name ?: "",
            seriesCmd = seriesData.cmd ?: "",
            seriesTime = seriesData.time?.toIntOrNull(),
            seriesYear = seriesData.year ?: "",
            rate = seriesData.rate ?: "",
            rating_imdb = seriesData.rating_imdb ?: "",
            screenshot_uri = seriesData.screenshot_uri ?: "",
            genres_str = seriesData.genres_str ?: "",
            actors = seriesData.actors ?: "",
            added = seriesData.added ?: "",
            age = seriesData.age ?: "",
            description = seriesData.description ?: "",
            director = seriesData.director ?: "",
            tmdb_id = seriesData.tmdb_id ?: "",
            o_name = seriesData.o_name ?: "",
            currentPosition = 0L,
            isFavorite = false,
            isCompletelyWatched = seriesData.isFullyWatched,
            isPartlyWatched = seriesData.isPartlyWatched,
            seriesPercentagePlayed = 0.0,
            lastWatchedEpisode = 1,
            lastWatchedSeason = 1,
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


    fun createSequentialList(start: Int, end: Int): List<Int> {
        return (start..end).toList()
    }

    ////REMINDER////

    private val _reminderAdded = MutableLiveData<Int>(2)
    var reminderAdded: LiveData<Int> = _reminderAdded

    fun updateReminderAddedSuccessful() {
        _reminderAdded.postValue(1)
    }

    fun updateReminderAddedReset() {
        _reminderAdded.postValue(2)
    }

    fun scheduleReminder(context: Context, programme: Programme) {
        val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager

        val tvChannel = programme.tvchannels.target

        val timeOffSet =
            tvChannel.epgTimeOffSet ?: tvChannel.reltvcategory.target?.epgTimeOffSet
            ?: tvChannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
            ?: 0
        // Alarmzeit berechnen (Startzeit - Erinnerungsintervall)
        val alarmZeit = (programme.startTimeStamp * 1000) -
                (programme.rememberInterval * 60 * 1000)

        val lesbar = formatUnixTimestampToTime(alarmZeit, 0)
        // Intent für BroadcastReceiver
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("programm_unqiue", programme.epgForCh)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            programme.id.toInt(), // Eindeutige ID für jeden Alarm
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Alarm setzen (funktioniert auch im Standby)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            alarmZeit,
            pendingIntent
        )
        programmeBox.put(programme)
        Log.d("PROGRAMME REMINDER", "NEU: $programme ALARMZEIT: $lesbar")

        updateReminderAddedSuccessful()
    }

    fun setReminder(context: Context, programme: Programme, timeOffset: Int) {
        val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {

                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            }
        }

        val epg = programme.epgData.target

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("programme_id", programme.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            programme.id.toInt(), // eindeutige ID
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d("REMIND INTERN EPG", "AFTER: $epg")

        val triggerAtMillis = getReminderTriggerTimeWithOffset(
            epg.startTimestamp ?: 0L,
            timeOffset
        )

        alarmManager.setExact(
            AlarmManager.RTC,
            triggerAtMillis,
            pendingIntent
        )
    }


    fun getReminderTriggerTimeWithOffset(startTimeUnixSeconds: Long, timeOffsetHours: Int, minutesBefore: Int = 5): Long {
        val adjustedTimeMillis = (startTimeUnixSeconds + timeOffsetHours * 3600) * 1000
        return adjustedTimeMillis - minutesBefore * 60 * 1000
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

    suspend fun removeOldRemindedPrograms() {
        withContext(Dispatchers.IO) {
            val oldPrograms = programmeBox.query(
                Programme_.stopTimeStamp.less(System.currentTimeMillis() / 1000)
            ).build().remove()
        }
    }

    ////TMDB////
    fun getTmdbMovieImage(url: String, movieId: Int, apiKey: String): Deferred<Resource<String?>> =
        viewModelScope.async {
            val response = helperRepository.getTmdbMovieImage(url, movieId, apiKey)
            when (response) {
                is Resource.Success -> {
                    val tmdbImage = response.data?.backdrops?.getOrNull(0)?.file_path
                    val element =
                        tmdbImage?.let { "https://image.tmdb.org/t/p/original$it" }
                    Resource.Success(element)
                }
                is Resource.Error -> {
                    Resource.Error("")
                }
            }
        }

    var currentTmdBSeriesDetails: TMDBSeriesDetails? = null

    fun getTmdbSeriesDetails(url: String, seriesId: Int, apiKey: String): Deferred<Resource<TMDBSeriesDetails?>> =
        viewModelScope.async {
            val response = helperRepository.getTmdbSeriesDetails(url, seriesId, apiKey)
            when (response) {
                is Resource.Success -> {
                    currentTmdBSeriesDetails = response.data
                    Resource.Success(response.data)
                }
                is Resource.Error -> {
                    Resource.Error(response.message)
                }
            }
        }

    fun getTmdbSeasonDetails(url: String, seriesId: Int, seasonNumber: Int, apiKey: String): Deferred<TMDBSeasonDetails?> =
        viewModelScope.async {
            val response = helperRepository.getTmdbSeasonDetails(url, seriesId, seasonNumber, apiKey)
            when (response) {
                is Resource.Success -> {
                    response.data
                }
                is Resource.Error -> {
                    null
                }
            }
        }

    fun getTmdbMovieDetails(url: String, movieId: Int, apiKey: String): Deferred<Resource<TMDBMovieDetails?>> =
        viewModelScope.async {
            val response = helperRepository.getTmdbMovieDetails(url, movieId, apiKey)
            when (response) {
                is Resource.Success -> {
                    Resource.Success(response.data)
                }
                is Resource.Error -> {
                    Resource.Error(response.message)
                }
            }
        }

    fun getTmdbMovieDetailsByImdb(url: String, imdbId: String, apiKey: String): Deferred<Resource<TMDB_imdb_id?>> =
        viewModelScope.async {
            val response = helperRepository.getTmdbMovieDetailsByImdb(url, imdbId, apiKey)
            when (response) {
                is Resource.Success -> {
                    Resource.Success(response.data)
                }
                is Resource.Error -> {
                    Resource.Error(response.message)
                }
            }
        }
}