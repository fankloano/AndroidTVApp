package com.example.mj_player_tv.viewmodel

import android.app.Application
import android.view.Gravity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgDataOB_
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.ui.epg.EpgUtils
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class TvGuideViewModel(application: Application) : AndroidViewModel(application) {

    private val accountBox = ObjectBox.store.boxFor(Accounts::class.java)
    private val epgDataBox = ObjectBox.store.boxFor(EpgDataOB::class.java)

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    var currentFocusedTvCategory: TvCategoryOB? = null

    var currentFocusedTvAccount: Accounts? = null

    var timeLineStartSec = 0L

    private val _changePlayingChannel = MutableLiveData<ChannelPositions?>()
    val changePlayingChannel: LiveData<ChannelPositions?> = _changePlayingChannel

    fun requestchangePlayingChannel(channelPositions: ChannelPositions) {
        _changePlayingChannel.value = channelPositions
    }

    fun clearchangePlayingChannel() {
        _changePlayingChannel.value = null
    }

    private val _loadChannelsForCategory = MutableLiveData<TvCategoryOB?>()
    val loadChannelsForCategory: LiveData<TvCategoryOB?> = _loadChannelsForCategory

    fun requestloadChannelsForCategory(tvCategory: TvCategoryOB) {
        _loadChannelsForCategory.value = tvCategory
    }

    fun clearloadChannelsForCategory() {
        _loadChannelsForCategory.value = null
    }

    private val _focusToTvGuideRequest = MutableLiveData<Unit?>()
    val focusToTvGuideRequest: LiveData<Unit?> = _focusToTvGuideRequest

    fun requestFocusOnTvGuide() {
        _focusToTvGuideRequest.value = Unit
    }

    fun clearFocusOnTvGuide() {
        _focusToTvGuideRequest.value = null
    }

    private val _showMenuAndAccountsRequest = MutableLiveData<Unit?>()
    val showMenuAndAccountsRequest: LiveData<Unit?> = _showMenuAndAccountsRequest

    fun requestShowMenuAndAccounts() {
        _showMenuAndAccountsRequest.value = Unit
    }

    fun clearShowMenuAndAccounts() {
        _showMenuAndAccountsRequest.value = null
    }

    private val _hideMenuAndAccountsRequest = MutableLiveData<Unit?>()
    val hideMenuAndAccountsRequest: LiveData<Unit?> = _hideMenuAndAccountsRequest

    fun requestHideMenuAndAccounts() {
        _hideMenuAndAccountsRequest.value = Unit
    }

    fun clearHideMenuAndAccounts() {
        _hideMenuAndAccountsRequest.value = null
    }

    fun getCurrentTimeMarks(now: DateTime): List<TimeLineData> {
        val timeStepMinutes = 30
        val stepWidthPx = timeStepMinutes * EpgUtils.minuteToPixel

        // Aktuelle Zeit abrunden auf die letzte volle Stunde
        var currentTime = now
            .withMinuteOfHour(0)
            .withSecondOfMinute(0)
            .withMillisOfSecond(0)


        val endTime = currentTime.plusHours(12)
        val hours = mutableListOf<TimeLineData>()
        var hourIndex = 0
        EpgUtils.timeLineStartTime = currentTime.minusMinutes(15)
        EpgUtils.timeLineEndTime = endTime
        while (currentTime <= endTime) {
            hours.add(
                TimeLineData(
                    timeId = currentTime,
                    time = currentTime.toString("HH:mm"),
                    width = stepWidthPx,
                    gravity = Gravity.CENTER,
                    textSizeSp = if (hourIndex % 2 == 0) 13f else 11f
                )
            )
            currentTime = currentTime.plusMinutes(timeStepMinutes)
            hourIndex++
        }
        return hours
    }

    private val _channelsWithEpg = MutableStateFlow<List<TvChannelWithEpg>>(emptyList())
    // Exponieren Sie den StateFlow als unveränderlichen StateFlow für die UI
    val channelsWithEpg: StateFlow<List<TvChannelWithEpg>> = _channelsWithEpg
    fun getChannelsForCategory(accountTvCategoryId: Long) : List<TvChannelWithEpg> {
            val tvCategory = tvCatBox.get(accountTvCategoryId)
            currentFocusedTvCategory = tvCategory
                val isPlaylistActive =
                    currentFocusedTvAccount?.epgsources?.any { it.isSelected && it.isPlaylistEpg }
                val sortedChannels = getChannelList()
                return if (sortedChannels.isNotEmpty()) {
                        val channelsWithEpg = sortedChannels.map { tvChannelPosition ->
                            val tvChannel = tvChannelPosition.tvchannel.target
                            val chEpgId = tvChannel.linkedEpgChannel?.target?.chEpgId
                                ?: if (isPlaylistActive == true) {
                                    tvChannel.epgChannel?.target?.chEpgId
                                } else {
                                    null
                                }
                            val originalEpg = epgCache[chEpgId] ?: generateDummyEpg(tvChannelPosition.catAndChannelAccount)
                            val sortedEpg = originalEpg.sortedBy { it.startTimestamp }

                            val cleanedEpg = mutableListOf<EpgDataOB>()
                            var lastShowStopTimestamp = 0L // Startet bei 0, um die erste Sendung zu behandeln
                            if (sortedEpg.isNotEmpty()) {
                                lastShowStopTimestamp = sortedEpg.first().stopTimestamp
                                cleanedEpg.add(sortedEpg.first())

                                for (i in 1 until sortedEpg.size) {
                                    val currentShow = sortedEpg[i]

                                    // Überprüfe auf Lücken
                                    if (currentShow.startTimestamp > lastShowStopTimestamp) {
                                        val gapDurationSeconds = currentShow.startTimestamp - lastShowStopTimestamp
                                        val gapDurationMinutes = gapDurationSeconds / 60

                                        // Füge eine Lücken-Sendung hinzu, wenn die Lücke mindestens 5 Minuten beträgt
                                        if (gapDurationMinutes >= 5) {
                                            cleanedEpg.add(
                                                EpgDataOB(
                                                    id = 0,
                                                    startTimestamp = lastShowStopTimestamp,
                                                    stopTimestamp = currentShow.startTimestamp,
                                                    name = "No Information",
                                                    idByAccountData = "gap_${tvChannelPosition.catAndChannelAccount}_${lastShowStopTimestamp}"
                                                )
                                            )
                                        }
                                    }

                                    // Überprüfe auf Überlappungen
                                    // Wenn die Sendung nach der letzten Sendung beginnt, füge sie hinzu
                                    if (currentShow.startTimestamp >= lastShowStopTimestamp) {
                                        cleanedEpg.add(currentShow)
                                        lastShowStopTimestamp = currentShow.stopTimestamp
                                    }
                                    // Andernfalls (bei Überlappung) überspringen wir die Sendung
                                    // und `lastShowStopTimestamp` bleibt unverändert, um die nächste Sendung zu überprüfen
                                }
                            }
                            TvChannelWithEpg(
                                tvChannel.id,
                                tvChannelPosition,
                                cleanedEpg // Verwende die bereinigten Daten
                            )

                        }.sortedBy { it.tvChannelPosition.position }
                    channelsWithEpg
        } else {
            emptyList()
        }
    }

    fun generateDummyEpg(channelId: String): List<EpgDataOB> {
        val dummyEpgList = mutableListOf<EpgDataOB>()

        // Holen der aktuellen Zeit in der System-Zeitzone
        val now = DateTime.now(DateTimeZone.getDefault())

        // Auf die letzte volle Stunde runden und dann 30 Minuten abziehen
        var currentTime = now.withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0).minusMinutes(30)

        // Das Ende ist das Ende des heutigen Tages
        val dayEnd = now.plusDays(1).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)

        // Dauer der Dummy-Sendung ist eine Stunde
        val showDurationInSeconds = 60 * 60 // 60 Minuten = 3600 Sekunden

        while (currentTime.isBefore(dayEnd)) {
            val endTime = currentTime.plusSeconds(showDurationInSeconds)

            dummyEpgList.add(
                EpgDataOB(
                    id = 0,
                    startTimestamp = currentTime.millis / 1000,
                    stopTimestamp = endTime.millis / 1000,
                    name = "No Information",
                    idByAccountData = "dummy_${channelId}_${currentTime.millis}"
                )
            )
            currentTime = endTime
        }
        return dummyEpgList
    }

    private fun getChannelList(): List<ChannelPositions> {
        val sortedChannels = when {
            currentFocusedTvCategory!!.isAllChannelsCategory -> {
                currentFocusedTvAccount?.channels?.reset()
                val categories = currentFocusedTvAccount?.tvcategories
                    ?.filter {
                        it.favorite && !it.isFavoriteCategory && !it.userCategory
                    }
                val channelPositions: MutableList<ChannelPositions> = mutableListOf()
                categories?.forEach {
                    channelPositions.addAll(it.tvChannelLink)
                }
                channelPositions
            }

            else -> {
                currentFocusedTvCategory!!.tvChannelLink.reset()
                when (currentFocusedTvCategory!!.orderBy) {
                    0 -> {
                        val categoryLinks =
                            currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.originalPosition }.filter {
                                it.isSelected
                            }

                        if (currentFocusedTvCategory!!.isFavoriteCategory) {
                            categoryLinks.filter { it.tvchannel.target.isFavorite }
                        } else {
                            categoryLinks
                        }
                    }

                    1 -> {
                        val categoryLinks =
                            currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.tvchannel.target.showingName }.filter {
                                it.isSelected
                            }
                        if (currentFocusedTvCategory!!.isFavoriteCategory) {
                            categoryLinks.filter { it.tvchannel.target.isFavorite }
                        } else {
                            categoryLinks
                        }
                    }

                    else -> {
                        val categoryLinks =
                            currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.position }.filter {
                                it.isSelected
                            }
                        if (currentFocusedTvCategory!!.isFavoriteCategory) {
                            categoryLinks.filter { it.tvchannel.target.isFavorite }
                        } else {
                            categoryLinks
                        }
                    }
                }
            }
        }
        return sortedChannels
    }

    var epgCache = HashMap<String, MutableList<EpgDataOB>>()

    fun getEpgForTime(account: Accounts) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentAcoount = accountBox.get(account.id)
            val epgSourceIds = currentAcoount.epgsources.filter { it.isSelected }.map {
                it.relatedepgsource.target.id.toInt()
            }.toIntArray()

            // Aktuelle Zeit abrunden auf die letzte volle Stunde
            val startTime = DateTime.now()
                .withMinuteOfHour(0)
                .withSecondOfMinute(0)
                .withMillisOfSecond(0)
                .minusMinutes(30)
            // 2. Ziehe 30 Minuten ab, um den neuen Startzeitpunkt zu erhalten

            val startTimeSec = startTime.millis / 1000
            // 3. Berechne das Ende der Abfrage, 12.5 Stunden später
            val twelveHalfHoursLaterSec = startTime.plusHours(13).millis / 1000

            epgCache = HashMap(
                epgDataBox.query(
                    EpgDataOB_.epgSourceId.oneOf(epgSourceIds)
                        .and(EpgDataOB_.stopTimestamp.greater(startTimeSec))
                        .and(EpgDataOB_.startTimestamp.less(twelveHalfHoursLaterSec))
                ).build().find()
                    .groupBy { it.epgChId ?: "" }
                    .mapValues { entry ->
                        entry.value.sortedBy { it.startTimestamp }.toMutableList()
                    }
            )
        }
    }
}
