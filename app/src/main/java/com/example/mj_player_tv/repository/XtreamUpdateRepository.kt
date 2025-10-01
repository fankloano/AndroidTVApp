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
import com.example.mj_player_tv.database.entity.SeriesCategoryOB
import com.example.mj_player_tv.database.entity.SeriesCategoryOB_
import com.example.mj_player_tv.database.entity.Settings
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.network.RetrofitInstance
import com.example.mj_player_tv.utils.Resource
import io.objectbox.Box
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object XtreamUpdateRepository {

    private val xtreamRepository = XtreamRepository()

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
    }

    private val _xtreamUpdateProcessState = MutableStateFlow<PlaylistUpdateProcessState?>(null)
    val xtreamUpdateProcessState: StateFlow<PlaylistUpdateProcessState?> get() = _xtreamUpdateProcessState
    // Repository

    fun updateState(state: PlaylistUpdateProcessState) {
        Log.d("STALKERWORKER", "REPO State updated: $state")
        _xtreamUpdateProcessState.value = state
    }

    fun resetXtreamUpdateProcessState() {
        _xtreamUpdateProcessState.value = null
    }

    suspend fun updateXtreamData(
        account: Accounts
    ) {
        updateState(PlaylistUpdateProcessState.CurrentAccount(account.name))
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
                    updateState(PlaylistUpdateProcessState.NoData("No Data received!"))
                } else {
                    when {
                        allTrue -> account.lastUpdateStatus = 1 // Alle sind true
                        anyFalse -> account.lastUpdateStatus = 2 // Mindestens ein Ergebnis ist false
                    }
                    val currentDate = System.currentTimeMillis() / 1000
                    account.lastUpdatedDate = currentDate
                    accountBox.put(account)
                    updateState(PlaylistUpdateProcessState.Success)
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
                updateState(PlaylistUpdateProcessState.Error)
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
                                    null,
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
                    }

                    is Resource.Error -> {
                        Log.d("XXX CHANNELS", "Keine vorhanden")
                    }
                }
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

    private fun updatePrefixesAndSuffixesForAddedPlaylist(
        prefixes: List<String>?,
        suffixes: List<String>?,
        tvchannels: List<TvChannelOB>
    ): List<TvChannelOB> {
        tvchannels.forEach { channel ->
            var modifiedName = channel.editedName
            prefixes?.forEach { prefix ->
                if (modifiedName.startsWith(prefix)) {
                    modifiedName = modifiedName.removePrefix(prefix).trim()
                }
            }
            suffixes?.forEach { suffix ->
                if (modifiedName.endsWith(suffix)) {
                    modifiedName = modifiedName.removeSuffix(suffix).trim()
                }
            }
            channel.showingName = modifiedName
        }
        return tvchannels
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

    fun updateTvChannelsInDatabase(
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

}