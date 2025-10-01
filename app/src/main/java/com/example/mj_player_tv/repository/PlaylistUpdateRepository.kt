package com.example.mj_player_tv.repository

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.EpgSourceChannel
import com.example.mj_player_tv.database.entity.EpgSource_
import com.example.mj_player_tv.database.entity.MovieCategoryOB
import com.example.mj_player_tv.database.entity.MovieCategoryOB_
import com.example.mj_player_tv.database.entity.PlexCategoryOB
import com.example.mj_player_tv.database.entity.PlexCategoryOB_
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.database.entity.Programme_
import com.example.mj_player_tv.database.entity.SeriesCategoryOB
import com.example.mj_player_tv.database.entity.SeriesCategoryOB_
import com.example.mj_player_tv.database.entity.Settings
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.help.PlaylistUpdate
import com.example.mj_player_tv.utils.Resource
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

object PlaylistUpdateRepository {

    private val stalkerRepository = StalkerRepository()
    private val xtreamRepository = XtreamRepository()
    private val plexRepository = PlexRepository()

    private val epgSourceBox: Box<EpgSource>
    private val epgChannelBox: Box<EpgSourceChannel>
    private val epgDataBox: Box<EpgDataOB>
    private val tvChannelBox: Box<TvChannelOB>
    private val accountBox: Box<Accounts>
    private val settingsBox: Box<Settings>
    private val tvCatBox: Box<TvCategoryOB>
    private val manualPosBox: Box<ChannelPositions>
    private val tvChannBox: Box<TvChannelOB>
    private val movieCatBox: Box<MovieCategoryOB>
    private val seriesCatBox: Box<SeriesCategoryOB>
    private val programmeBox: Box<Programme>
    private val plexCatBox: Box<PlexCategoryOB>

    init {
        val store = ObjectBox.store
        epgSourceBox = store.boxFor(EpgSource::class.java)
        epgChannelBox = store.boxFor(EpgSourceChannel::class.java)
        epgDataBox = store.boxFor(EpgDataOB::class.java)
        tvChannelBox = store.boxFor(TvChannelOB::class.java)
        accountBox = store.boxFor(Accounts::class.java)
        settingsBox = store.boxFor(Settings::class.java)
        tvCatBox = store.boxFor(TvCategoryOB::class.java)
        manualPosBox = store.boxFor(ChannelPositions::class.java)
        tvChannBox = store.boxFor(TvChannelOB::class.java)
        movieCatBox = store.boxFor(MovieCategoryOB::class.java)
        seriesCatBox = store.boxFor(SeriesCategoryOB::class.java)
        programmeBox = store.boxFor(Programme::class.java)
        plexCatBox = store.boxFor(PlexCategoryOB::class.java)
    }

    private val _playlistUpdateProcessState = MutableStateFlow<Map<String, PlaylistUpdate>>(
        emptyMap()
    )
    val playlistUpdateProcessState: StateFlow<Map<String, PlaylistUpdate>> get() = _playlistUpdateProcessState

    fun updateState(playlistName: String, playlistId: Long, state: PlaylistUpdateProcessState) {
        _playlistUpdateProcessState.value += (playlistName to PlaylistUpdate(playlistName, playlistId, state))
    }


    fun removePlaylist(accountName: String) {
        _playlistUpdateProcessState.value -= accountName
    }

    fun resetStalkerUpdateProcessState() {
        _playlistUpdateProcessState.value = emptyMap()
    }

    suspend fun updateStalkerData(
        account: Accounts
    ) {
        updateState(account.name, account.id, PlaylistUpdateProcessState.CurrentAccount(account.name))
        val status = playlistUpdateProcessState.value
        Log.d("STALKERWORKER", "STATUS REPO: $status")
        val tokenResponse = getToken(account.stalkerUrl, account.macAddress, account.userAgent)
        when (tokenResponse) {
            is Resource.Success -> {
                account.token = tokenResponse.data
                Log.d("STALKERWORKER", "UPDATING: tokenneu: ${account.token}")
                val profileResult = getProfile(account.stalkerUrl, account.macAddress, account.userAgent, account.token ?: "")
                account.timezone = profileResult
                val mainInfoResult = getExpiryDate(account.stalkerUrl, account.macAddress, account.userAgent, account.token ?: "", profileResult)
                account.expiryDate = mainInfoResult
                val tvCatResult = updateTvCategories(account.stalkerUrl, account.macAddress, account.userAgent, account.name, account.token ?: "", profileResult, account)

                if (tvCatResult) {
                    account.tvCategoryLoadingOK = 1
                } else {
                    account.tvCategoryLoadingOK = 0
                }

                val tvChannResult = updateTvChannels(account.stalkerUrl, account.macAddress, account.userAgent, account.name, account.token ?: "", profileResult, account)

                if (tvChannResult) {
                    account.tvchannelLoadingOK = 1
                } else {
                    account.tvchannelLoadingOK = 0
                }

                val movieCatResult = updateMovieCategories(account.stalkerUrl, account.macAddress, account.userAgent, account.name, account.token ?: "", profileResult, account)

                if (movieCatResult) {
                    account.movieCategoryLoadingOK = 1
                } else {
                    account.movieCategoryLoadingOK = 0
                }

                val seriesCatResult = updateSeriesCategories(account.stalkerUrl, account.macAddress, account.userAgent, account.name, account.token ?: "", profileResult, account)

                if (seriesCatResult) {
                    account.seriesCategoryLoadingOK = 1
                } else {
                    account.seriesCategoryLoadingOK = 0
                }

                val allResults = listOf(tvCatResult, tvChannResult, movieCatResult, seriesCatResult)

                val allTrue = allResults.all { it }
                val allFalse = allResults.none { it }
                val anyFalse = allResults.any { !it }

                if (allFalse) {
                    account.lastUpdateStatus = 0 // Alle sind false
                    val currentDate = System.currentTimeMillis() / 1000
                    account.lastUpdatedDate = currentDate
                    accountBox.put(account)
                    updateState(account.name, account.id, PlaylistUpdateProcessState.NoData("No Data received!"))
                } else {
                    when {
                        allTrue -> account.lastUpdateStatus = 1 // Alle sind true
                        anyFalse -> account.lastUpdateStatus = 2 // Mindestens ein Ergebnis ist false
                    }
                    val currentDate = System.currentTimeMillis() / 1000
                    account.lastUpdatedDate = currentDate
                    accountBox.put(account)
                    updateState(account.name, account.id, PlaylistUpdateProcessState.Success)
                }
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
                updateState(account.name, account.id, PlaylistUpdateProcessState.Error)
            }
        }
    }

    suspend fun getToken(
        url: String,
        macAddress: String,
        userAgent: String
    ) : Resource<String> {
        return try {
            val response = stalkerRepository.getToken(
                url,
                "mac=${macAddress}; stb_lang=en; timezone=Europe/Amsterdam;",
                userAgent
            )
            when (response) {
                is Resource.Success -> {
                    if (!response.data.js.token.isNullOrEmpty()) {
                        val token = response.data.js.token
                        Resource.Success(token)
                    } else {
                        Resource.Success("")
                    }
                }

                is Resource.Error -> {
                    Resource.Error("")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Resource.Error("")
        }
    }

    suspend fun getProfile(
        url: String,
        macAddress: String,
        userAgent: String,
        token: String
    ) : String {
        return try {
            val response = stalkerRepository.getProfile(
                url,
                "mac=${macAddress}; stb_lang=en; timezone=Europe/Amsterdam;",
                "Bearer $token",
                userAgent
            )
            when (response) {
                is Resource.Success -> {
                    val timeZone = if (!response.data.js.default_timezone.isNullOrEmpty()) {
                        response.data.js.default_timezone
                    } else {
                        "Europe/Amsterdam"
                    }
                    timeZone
                }

                is Resource.Error -> {
                    "Europe/Amsterdam"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Europe/Amsterdam"
        }
    }

    suspend fun getExpiryDate(
        url: String,
        macAddress: String,
        userAgent: String,
        token: String,
        timeZone: String
    ): String {
        return try {
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
                    expiryDate
                }
                is Resource.Error -> {
                    ""
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }


    //UPDATE TV CATEGORIES

    suspend fun updateTvCategories(
        url: String,
        macAddress: String,
        userAgent: String,
        name: String,
        token: String,
        timeZone: String,
        accountData: Accounts
    ) : Boolean  {
        return try {
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
                    Log.d("STALKERWORKER", "UPDATING: tvcat: ${networkCategories.size}")
                    updateCategoriesInDatabase(accountData, networkCategories)
                    true
                }

                is Resource.Error -> {
                    // Handle error
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
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
                                    null,
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
                                                    null,
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
                        tvCatBox.put(category)
                    }

                    is Resource.Error -> {
                        Log.d("XXX CHANNELS", "Keine vorhanden")
                    }
                }
            }
        }
    }

    fun createSequentialList(start: Int, end: Int): List<Int> {
        return (start..end).toList()
    }

    //UPDATE TV CHANNELS

    suspend fun updateTvChannels(
        url: String,
        macAddress: String,
        userAgent: String,
        name: String,
        token: String,
        timeZone: String,
        accountData: Accounts
    ) : Boolean  {
            return try {
                val epgSource = epgSourceBox.query(EpgSource_.playlistId.equal(accountData.id)).build().findFirst()
                val response = stalkerRepository.getAllTvChannels(
                    url,
                    "mac=${macAddress}; stb_lang=en; timezone=$timeZone;",
                    "Bearer $token",
                    userAgent
                )
                when (response) {
                    is Resource.Success -> {
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
                                null,
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
                        true
                    }

                    is Resource.Error -> {
                        false
                    }
                    else -> {
                        false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
        }
    }

    private fun updateTvChannelsInDatabase(
        accountData: Accounts,
        networkChannels: List<TvChannelOB>
    ) {
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

                if (channelsToDelete.isNotEmpty() && networkChannels.isNotEmpty()) {
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
                                manualPosBox.put(oldPositions)
                                manualPosBox.remove(oldChPos)
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


    suspend fun updateMovieCategories(
        url: String,
        macAddress: String,
        userAgent: String,
        name: String,
        token: String,
        timeZone: String,
        accountData: Accounts
    ) : Boolean {
            return try {
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
                        Log.d("STALKERWORKER", "UPDATING: moveicat: ${newCategories.size}")
                        updateMovieCategoriesInDatabase(accountData, newCategories)
                        true
                    }

                    is Resource.Error -> {
                        false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
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
                if (categoriesToDelete.isNotEmpty() && networkCategories.isNotEmpty()) {
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


    //UPDATE SERIES CATEGORIES

    suspend fun updateSeriesCategories(
        url: String,
        macAddress: String,
        userAgent: String,
        name: String,
        token: String,
        timeZone: String,
        accountData: Accounts
    ) : Boolean  {
       return try {
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
                   Log.d("STALKERWORKER", "UPDATING: seriescat: ${newCategories.size}")
                   updateSeriesCategoriesInDatabase(accountData, newCategories)
                   true
               }
               is Resource.Error -> {
                   false
               }
           }
       } catch (e: Exception) {
           e.printStackTrace()
           false
       }
    }

    private fun updateSeriesCategoriesInDatabase(
        accountData: Accounts,
        networkCategories: List<SeriesCategoryOB>
    ) {
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
                if (categoriesToDelete.isNotEmpty() && networkCategories.isNotEmpty()) {
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


    //XTREAM

    suspend fun updateXtreamData(
        account: Accounts
    ) {
        updateState(account.name, account.id, PlaylistUpdateProcessState.CurrentAccount(account.name))
        val authResponse = updateXtreamAuthentication(account)
        when (authResponse) {
            is Resource.Success -> {
                val tvCatResult = updateXtreamTvCategories(account)

                if (tvCatResult) {
                    account.tvCategoryLoadingOK = 1
                } else {
                    account.tvCategoryLoadingOK = 0
                }

                val tvChannResult = updateXtreamTvChannels(account)

                if (tvChannResult) {
                    account.tvchannelLoadingOK = 1
                } else {
                    account.tvchannelLoadingOK = 0
                }

                val movieCatResult = updateXtreamMovieCategories(account)

                if (movieCatResult) {
                    account.movieCategoryLoadingOK = 1
                } else {
                    account.movieCategoryLoadingOK = 0
                }

                val seriesCatResult = updateXtreamSeriesCategories(account)

                if (seriesCatResult) {
                    account.seriesCategoryLoadingOK = 1
                } else {
                    account.seriesCategoryLoadingOK = 0
                }
                val allResults = listOf(tvCatResult, tvChannResult, movieCatResult, seriesCatResult)

                val allTrue = allResults.all { it }
                val allFalse = allResults.none { it }
                val anyFalse = allResults.any { !it }

                if (allFalse) {
                    account.lastUpdateStatus = 0 // Alle sind false
                    val currentDate = System.currentTimeMillis() / 1000
                    account.lastUpdatedDate = currentDate
                    accountBox.put(account)
                    updateState(account.name, account.id, PlaylistUpdateProcessState.NoData("No Data received!"))
                } else {
                    when {
                        allTrue -> account.lastUpdateStatus = 1 // Alle sind true
                        anyFalse -> account.lastUpdateStatus = 2 // Mindestens ein Ergebnis ist false
                    }
                    val currentDate = System.currentTimeMillis() / 1000
                    account.lastUpdatedDate = currentDate
                    accountBox.put(account)
                    updateState(account.name, account.id, PlaylistUpdateProcessState.Success)
                }
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
                updateState(account.name, account.id, PlaylistUpdateProcessState.Error)
            }
        }
    }


    //UPDATE ACCOUNT

    suspend fun updateXtreamAuthentication(account: Accounts): Resource<Accounts> {
        return try {
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
                        response.data.user_info.allowed_output_formats.contains("ts")

                    account.expiryDate = response.data.user_info.exp_date ?: ""
                    account.timezone = response.data.server_info.timezone
                    account.lastUpdatedDate = currentDate

                    if (account.xtreamUseDefaultType && useDefault) {
                        account.xtreamUseDefaultType = true
                    } else if (!useDefault && account.xtreamUseDefaultType) {
                        account.xtreamUseDefaultType = false
                    }

                    account.xtreamOutPutFormats =
                        response.data.user_info.allowed_output_formats.toMutableList()

                    accountBox.put(account) // Speichern in der Datenbank

                    Resource.Success(account)
                }
                is Resource.Error -> {
                    Resource.Error(response.message)
                }
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "An unknown error occurred")
        }
    }

    //UPDATE TV CATEGORIES

    suspend fun updateXtreamTvCategories(account: Accounts): Boolean {
        return try {
            val response = xtreamRepository.getXtreamTvCategories(
                account.stalkerUrl,
                account.username,
                account.macAddress,
                account.userAgent
            )

            when (response) {
                is Resource.Success -> {
                    // Mappe die Kategorien aus der Antwort auf `TvCategoryOB`-Objekte
                    val networkCategories = response.data.mapIndexed { index, tvcatresponse ->
                        val thisCategory = TvCategoryOB(
                            id = 0,
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
                            isFavoriteCategory = false,
                            isAllChannelsCategory = tvcatresponse.category_id == "*"
                        ).apply {
                            tvaccount.target = account // Verknüpfe mit dem Account
                        }
                        thisCategory
                    }

                    // Aktualisiere die Kategorien in der Datenbank
                    updateCategoriesInDatabase(account, networkCategories)

                    true
                }
                is Resource.Error -> {
                    // Gib eine leere Liste zurück, wenn ein Fehler auftritt
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false // Gib eine leere Liste zurück, wenn eine Ausnahme auftritt
        }
    }


    //UPDATE TV CHANNELS

    suspend fun updateXtreamTvChannels(account: Accounts): Boolean {
        // Hole die entsprechende EPG-Quelle
        val thisEpgSource = epgSourceBox.query(EpgSource_.playlistId.equal(account.id)).build().findFirst()

        // Führe die API-Anfrage aus, um die Kanäle abzurufen
        val response = xtreamRepository.getXtreamAllChannels(
            account.stalkerUrl,
            account.username,
            account.macAddress,
            account.userAgent
        )

        return when (response) {
            is Resource.Success -> {
                // Gruppiere die Kanäle nach Kategorien
                val groupedChannels = response.data.groupBy { it.category_id }

                // Konvertiere die Kanäle aus der Netzwerkanfrage in `TvChannelOB`-Objekte
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
                            null,
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
                // Aktualisiere die Kanäle in der Datenbank
                updateTvChannelsInDatabase(account, networkChannels)

                // Gib die Liste der Kanäle zurück
                true
            }

            is Resource.Error -> {
                // Bei einem Fehler wird eine leere Liste zurückgegeben
                false
            }
        }
    }


    //UPDATE MOVIE CATEGORIES

    suspend fun updateXtreamMovieCategories(
        account: Accounts
    ) : Boolean {
        return try {
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
                        ).apply {
                            movieaccount.target = account
                        }
                        thisCategory
                    }
                    updateMovieCategoriesInDatabase(account, newCategories)
                    true
                }

                is Resource.Error -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    //UPDATE SERIES CATEGORIES

    suspend fun updateXtreamSeriesCategories(
        account: Accounts
    ) : Boolean {
        return try {
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
                    true
                }
                is Resource.Error -> {
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
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

    private fun updatePrefixesAndSuffixesForAddedPlaylist(prefixes: List<String>?, suffixes: List<String>?, tvchannels: List<TvChannelOB>): List<TvChannelOB> {
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
            // Update the edited channels in the database
        }
        return tvchannels
    }

    private suspend fun updateCategoriesInDatabase(
        accountData: Accounts,
        networkCategories: List<TvCategoryOB>
    ) {
        // Hole die bereits vorhandenen Kategorien aus der Datenbank
        val settings = settingsBox.all.first()
        if (settings != null) {
            accountData.tvcategories.reset()
            val currentCategories = accountData.tvcategories

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
            if (categoriesToDelete.isNotEmpty() && networkCategories.isNotEmpty()) {
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

    fun getProgrammeById(programmeUnique: String) : Programme? {
        return programmeBox.query(
            Programme_.epgForCh.equal(programmeUnique)
        ).build().findFirst()
    }


    ///UPDATE PLEX

    suspend fun updatePlexAccount(account: Accounts) {
        updateState(
            account.name,
            account.id,
            PlaylistUpdateProcessState.CurrentAccount(account.name)
        )
        val authentication =
            plexRepository.getPlexAuthentication(account.username, account.macAddress)
        when (authentication) {
            is Resource.Success -> {
                if (authentication.data.authToken.isNotEmpty()) {
                    account.mainPlexToken = authentication.data.authToken
                }
                val accessToken = plexRepository.getPlexUserResources(authentication.data.authToken)
                when (accessToken) {
                    is Resource.Success -> {
                        val server =
                            accessToken.data.firstOrNull { it.clientIdentifier == account.plexClientIdentifier }

                        if (server != null && server.accessToken.isNotEmpty()) {
                            account.token = server.accessToken
                            val sections = plexRepository.getPlexServerLibrarySections(
                                account.stalkerUrl,
                                server.accessToken
                            )
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
                                    val currentMovieSectionsKeys =
                                        currentMovieSections.map { it.plexCatId }.toSet()

                                    // Zu löschende Sektionen: in DB, aber nicht in neuer Antwort
                                    val movieSectionsToDelete =
                                        currentMovieSections.filter { it.plexCatId !in newMovieSectionsKeys }

                                    // Zu hinzufügende Sektionen: in Antwort, aber nicht in DB
                                    val movieSectionsToAdd =
                                        movieSections.filter { it.key !in currentMovieSectionsKeys }
                                            .map {
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
                                    val sameSections =
                                        currentMovieSections.filter { it.plexCatId in newMovieSectionsKeys }
                                    sameSections.forEach { same ->
                                        val name =
                                            movieSections.firstOrNull { it.key == same.plexCatId }?.title
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
                                        PlexCategoryOB_.isMovie.equal(false).and(
                                            PlexCategoryOB_.isAudio.equal(false)
                                        )
                                    ).build()
                                    val currentSeriesSections = currentSeriesSectionsQuery.find()
                                    currentSeriesSectionsQuery.close()
                                    val newSeriesSectionsKeys =
                                        seriesSections.map { it.key }.toSet()
                                    val currentSeriesSectionsKeys =
                                        currentSeriesSections.map { it.plexCatId }.toSet()

                                    val seriesSectionsToDelete =
                                        currentSeriesSections.filter { it.plexCatId !in newSeriesSectionsKeys }

                                    // Zu hinzufügende Sektionen: in Antwort, aber nicht in DB
                                    val seriesSectionsToAdd =
                                        seriesSections.filter { it.key !in currentSeriesSectionsKeys }
                                            .map {
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
                                    val sameSeriesSections =
                                        currentSeriesSections.filter { it.plexCatId in newSeriesSectionsKeys }
                                    sameSeriesSections.forEach { same ->
                                        val name =
                                            seriesSections.firstOrNull { it.key == same.plexCatId }?.title
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
                                        PlexCategoryOB_.isMovie.equal(false).and(
                                            PlexCategoryOB_.isAudio.equal(true)
                                        )
                                    ).build()
                                    val currentAudioSections = currentAudioSectionsQuery.find()
                                    currentAudioSectionsQuery.close()
                                    val newAudioSectionsKeys = seriesSections.map { it.key }.toSet()
                                    val currentAudioSectionsKeys =
                                        currentAudioSections.map { it.plexCatId }.toSet()

                                    val audioSectionsToDelete =
                                        currentAudioSections.filter { it.plexCatId !in newAudioSectionsKeys }

                                    // Zu hinzufügende Sektionen: in Antwort, aber nicht in DB
                                    val audioSectionsToAdd =
                                        audioSections.filter { it.key !in currentAudioSectionsKeys }
                                            .map {
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
                                    val sameAudioSections =
                                        currentAudioSections.filter { it.plexCatId in newAudioSectionsKeys }
                                    sameAudioSections.forEach { same ->
                                        val name =
                                            audioSections.firstOrNull { it.key == same.plexCatId }?.title
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
                                    updateState(
                                        account.name,
                                        account.id,
                                        PlaylistUpdateProcessState.Success
                                    )
                                }

                                is Resource.Error -> {
                                    updateState(
                                        account.name,
                                        account.id,
                                        PlaylistUpdateProcessState.Error
                                    )
                                }
                            }
                        } else {
                            Log.d("WORKER UPDATE HEEELP", "${account.name} = SERVER FEHLER")
                            updateState(account.name, account.id, PlaylistUpdateProcessState.Error)
                        }
                    }

                    is Resource.Error -> {
                        Log.d("WORKER UPDATE HEEELP", "${account.name} = NO RESSOURCES?")
                        updateState(account.name, account.id, PlaylistUpdateProcessState.Error)
                    }
                }
            }
            is Resource.Error -> {
                Log.d("WORKER UPDATE HEEELP", "${account.name} = FEHLA")
                updateState(account.name, account.id, PlaylistUpdateProcessState.Error)
            }
        }
    }
}