package com.example.mj_player_tv.ui.tvguide

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.ShowTag
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.TvguideRecyclerviewBinding
import com.example.mj_player_tv.ui.tvguide.EPGUtils.dayShift
import com.example.mj_player_tv.ui.tvguide.EPGUtils.getCellWidth
import com.example.mj_player_tv.ui.tvguide.EPGUtils.startEpgTime
import com.example.mj_player_tv.ui.tvguide.EPGUtils.startTime
import com.example.mj_player_tv.ui.tvguide.adapters.TimeLineAdapter
import com.example.mj_player_tv.ui.tvguide.adapters.TvGuideChannelListAdapter
import com.example.mj_player_tv.ui.tvguide.adapters.TvGuideChannelLogosAdapter
import com.example.mj_player_tv.ui.tvguide.adapters.TvGuideEpgAdapter
import com.example.mj_player_tv.utils.Constants
import com.example.mj_player_tv.utils.views.RecyclerWithPositionView
import com.volkov.EPGConfig
import com.volkov.epgrecycler.dpToPx
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import kotlin.math.abs

class TvGuideRecyclerview @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding by viewBinding(TvguideRecyclerviewBinding::bind)

    private var channels: List<TvChannelWithEpg> = emptyList()
    private var lastSelectedShowView: View? = null


    interface OnEventListener {
        fun onShowSelected(channelPosition: ChannelPositions, epgData: EpgDataOB)
        fun onShowClick(channelPosition: ChannelPositions, epgData: EpgDataOB)
        fun onShowExit()
    }

    var listener: OnEventListener? = null

    private var isLoadingNewData = false

    enum class MoveDirection { UP, DOWN, LEFT, RIGHT, NONE }

    private val horizontalScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            Log.d("TVGUIDE_SCROLL", "onScrolled source=${debugRvDesc(recyclerView)} dx=$dx isSyncing=$isSyncingHorizontal")

            // Wenn wir gerade programmgesteuert synchronisieren, nur Offset updaten, NICHT neu propagieren
            if (isSyncingHorizontal) {
                timeLineScrollPosition = currentTimeLineOffset()
                logAllOffsets("onScrolled (ignored)")
                return
            }

            // Wenn von Timeline, aktualisiere offset
            if (recyclerView == binding.rvTimeLine) {
                timeLineScrollPosition = currentTimeLineOffset()
            }

            // Propagiere (dies ruft syncHorizontalScroll, welches isSyncing setzt)
            syncHorizontalScroll(recyclerView, dx)

            updateTimeIndicator(withSubmit = false, DateTime())

            // existing load-more logic (unchanged)
            if (!isLoadingNewData) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                Log.d("NACHLADEN TV GUIDE", "DX: $dx FIRSTVISIBLE: $firstVisible")
                if (dx > 0 && lastVisible >= timeAdapter.itemCount - 5) {
                    isLoadingNewData = true
                    Log.d("NACHLADEN TV GUIDE", "ZUKUNFT")
                } else if (dx < 0 && firstVisible <= 1) {
                    isLoadingNewData = true
                    Log.d("NACHLADEN TV GUIDE", "VERGANGENHEIT")
                }
            }
        }
    }


    @Suppress("unused")
    fun setTimeZone(zone: DateTimeZone) {
        EPGUtils.timeZone = zone
    }

    fun setStartHour(startHour: Int) {
        EPGUtils.startHour = startHour
    }

    fun setEndHour(endHour: Int) {
        EPGUtils.endHour = endHour
    }

    fun setDayShift(day: Int) {
        if (day < 0 || day > 6) return
        dayShift = day
    }

    private val verticalScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            Log.d("VERTICAL SCROLL", "dy: $dy")
            scrollChannels(recyclerView, dy)
            scrollChannelsLogo(recyclerView, dy)
        }
    }

    private val timeAdapter = TimeLineAdapter()
    private val channelLogosAdapter = TvGuideChannelLogosAdapter()
    private var timeLineScrollPosition = 0

    // Prevent feedback-loop when programmatically syncing scrolls
    private var isSyncingHorizontal = false

    private fun currentTimeLineOffset(): Int = binding.rvTimeLine.computeHorizontalScrollOffset()

    private fun debugRvDesc(rv: RecyclerView?): String =
        when {
            rv == null -> "null"
            rv == binding.rvTimeLine -> "timeline"
            else -> rv.tag?.toString() ?: "child@${rv.hashCode()}"
        }

    private fun logAllOffsets(prefix: String = "") {
        try {
            val t = currentTimeLineOffset()
            val childOffsets = binding.rvChannels.children.mapNotNull { it as? RecyclerWithPositionView }
                .mapIndexed { i, r -> "c$i:${r.computeHorizontalScrollOffset()}" }
                .joinToString(",")
            Log.d("TVGUIDE_OFFSETS", "$prefix timeline=$t children=[$childOffsets]")
        } catch (e: Exception) {
            Log.w("TVGUIDE_OFFSETS", "logAllOffsets failed: ${e.message}")
        }
    }

    private val focusListener = OnFocusChangeListener { _, hasFocus ->
        if (hasFocus) lastSelectedShowView?.isSelected = true
    }

    init {
        inflate(context, R.layout.tvguide_recyclerview, this)
        binding.rvTimeLine.apply {
            tag = Constants.TIME_HEADER
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = timeAdapter
            setHasFixedSize(true)
            itemAnimator = null
            addOnScrollListener(horizontalScrollListener)
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent) = true
            })
        }

        binding.rvChannelsLogos.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = channelLogosAdapter
            setHasFixedSize(true)
            itemAnimator = null
            onFocusChangeListener = focusListener
            addOnScrollListener(verticalScrollListener)
        }
    }

    fun initView(channels: List<TvChannelWithEpg>, initChannel: Long? = null) {
        stopUpdates()
        val now = DateTime()
        setTimeHeader()
        initChannelRecycler()
        this.channels = channels
        setChannelsLogo(channels)
        setChannels(channels, now)
        scrollToNow(now)
        startUpdates()
    }
    // --- Tag handling ---
    private fun View.setShowTag(channelId: String, showId: String) {
        setTag(R.id.tag_epg_show, ShowTag(channelId, showId))
    }
    private fun View.getShowTag(): ShowTag? = getTag(R.id.tag_epg_show) as? ShowTag

    // --- Navigation ---
    private fun getNextShowByDirection(direction: MoveDirection): View? {
        val currentTag = lastSelectedShowView?.getShowTag() ?: return null
        val currentChannel = channels.firstOrNull { it.tvChannelPosition.catAndChannelAccount == currentTag.channelId } ?: return null
        val currentChannelIndex = channels.indexOf(currentChannel)
        val currentChannelShow = currentChannel.epgList.firstOrNull { it.idByAccountData == currentTag.showId } ?: return null

        val (channel, desiredShow) = when (direction) {
            MoveDirection.UP, MoveDirection.DOWN -> {
                val adjacentChannel = channels.getOrNull(currentChannelIndex + if (direction == MoveDirection.DOWN) 1 else -1) ?: return null
                val foundingTime = if (currentChannelShow.isLiveShow) System.currentTimeMillis() / 1000 else currentChannelShow.startTimestamp
                val match = adjacentChannel.epgList.find {
                    it.startTimestamp <= foundingTime && it.stopTimestamp > foundingTime
                } ?: adjacentChannel.epgList.minByOrNull { abs(it.startTimestamp - foundingTime) }
                Pair(adjacentChannel, match)
            }
            MoveDirection.LEFT -> {
                val idx = currentChannel.epgList.indexOf(currentChannelShow)
                Pair(currentChannel, currentChannel.epgList.getOrNull(idx - 1))
            }
            MoveDirection.RIGHT -> {
                val idx = currentChannel.epgList.indexOf(currentChannelShow)
                Pair(currentChannel, currentChannel.epgList.getOrNull(idx + 1))
            }
            MoveDirection.NONE -> return null
        }

        desiredShow ?: return null
        selectShow(channel, desiredShow)
        return lastSelectedShowView
    }

    private val EpgDataOB.isLiveShow: Boolean
        get() = startTimestamp < System.currentTimeMillis() / 1000 && stopTimestamp > System.currentTimeMillis() / 1000

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = when {
        event.onDownPressed() -> { getNextShowByDirection(MoveDirection.DOWN); true }
        event.onUpPressed() -> { getNextShowByDirection(MoveDirection.UP); true }
        event.onLeftPressed() -> { getNextShowByDirection(MoveDirection.LEFT); true }
        event.onRightPressed() -> { getNextShowByDirection(MoveDirection.RIGHT); true }
        event.onEnterPressed() -> {
            lastSelectedShowView?.getShowTag()?.let { tag ->
                val channel = channels.firstOrNull { it.tvChannelPosition.catAndChannelAccount == tag.channelId } ?: return true
                val epgData = channel.epgList.firstOrNull { it.idByAccountData == tag.showId } ?: return true
                listener?.onShowClick(channel.tvChannelPosition, epgData)
            }
            true
        }
        event.onBackPressed() -> {
            lastSelectedShowView?.getShowTag()?.let { tag ->
                val channel = channels.firstOrNull { it.tvChannelPosition.catAndChannelAccount == tag.channelId } ?: return true
                val epgData = channel.epgList.firstOrNull { it.idByAccountData == tag.showId } ?: return true
                if (epgData.isLiveShow) listener?.onShowExit() else selectAndScrollToNow()
            }
            true
        }
        else -> super.dispatchKeyEvent(event)
    }

    // --- Recycler setup ---
    private fun initChannelRecycler() {
        binding.rvChannels.apply {
            val channelListAdapter = TvGuideChannelListAdapter(this@TvGuideRecyclerview::currentTimeLineOffset)
            layoutManager = object : LinearLayoutManager(context) {
                override fun onInterceptFocusSearch(focused: View, direction: Int) = null
            }
            adapter = channelListAdapter
            setHasFixedSize(true)
            addOnScrollListener(verticalScrollListener)
            onFocusChangeListener = focusListener
        }
    }

    private fun updateTimeIndicator(withSubmit: Boolean = true, now: DateTime) {
        // Berechne die Position des Indikators in Pixeln vom Anfang des EPG-Inhalts
        val indicatorPosition = getCellWidth(startEpgTime, now)

        // Berechne die Position des Indikators relativ zum sichtbaren Bereich
        val relativePosition = indicatorPosition - timeLineScrollPosition

        Log.d("INDICATOR_DEBUG", "indicatorPosition: $indicatorPosition, timeLineScrollPosition: $timeLineScrollPosition, relativePosition: $relativePosition")

        val isVisible = relativePosition in (0..binding.rvTimeLine.width)
        binding.timeIndicator.isVisible = isVisible && EPGConfig.showTimeLine
        binding.tvTimeLineLabel.isVisible = isVisible && EPGConfig.showTimeLine

        binding.timeIndicator.updateLayoutParams<LayoutParams> {
            // Setze den Margin basierend auf der relativen Position
            marginStart = relativePosition
        }

        // Positioniere das Label, um es über dem Indikator zu zentrieren
        binding.tvTimeLineLabel.updateLayoutParams<LayoutParams> {
            marginStart = relativePosition - (binding.tvTimeLineLabel.width / 2)
        }

        binding.tvTimeLineLabel.text = DateTime().toString("HH:mm")

        if (!withSubmit) return
        binding.rvChannels.children.mapNotNull { it as? RecyclerView }.forEach {
            it.children.mapNotNull { view -> view.getShowTag() }.forEach { showTag ->
                val channel = channels.singleOrNull { channel -> channel.tvChannelPosition.catAndChannelAccount == showTag.channelId }
                val currentShow = channel?.epgList?.getCurrentShow()

                // Finde die Ansicht, die dem aktuellen ShowTag entspricht, und setze den Status
                binding.rvChannels.findViewWithTag<View>(showTag)?.isActivated = currentShow?.idByAccountData == showTag.showId
            }
        }
    }

    private fun updateVisibleEpgProgress(now: DateTime) {
        val layoutManager = binding.rvChannels.layoutManager as? LinearLayoutManager ?: return
        for (i in layoutManager.findFirstVisibleItemPosition()..layoutManager.findLastVisibleItemPosition()) {
            val channelRecycler = layoutManager.findViewByPosition(i) as? RecyclerWithPositionView
                ?: continue
            val epgAdapter = channelRecycler.adapter as? TvGuideEpgAdapter ?: continue
            val channelData = channels.getOrNull(i) ?: continue
            channelData.epgList.getCurrentShow()?.let { currentShow ->
                val showIndex = channelData.epgList.indexOf(currentShow)
                epgAdapter.notifyItemChanged(showIndex, currentShow)
            }
        }
    }

    // --- Coroutine Updates ---
    private var updateJob: Job? = null
    private fun startUpdates() {
        val lifecycle = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        updateJob = lifecycle.launch {
            while (isActive) {
                val now = DateTime()
                updateVisibleEpgProgress(now)
                updateTimeIndicator(false, now)
                delay(10000)
            }
        }
    }
    private fun stopUpdates() { updateJob?.cancel(); updateJob = null }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopUpdates()
    }

    // --- Selection ---
    fun selectCurrentShow(channelId: String?) {
        val channel = channels.singleOrNull { it.tvChannelPosition.catAndChannelAccount == channelId } ?: channels.firstOrNull() ?: return
        val channelIndex = channels.indexOf(channel)
        scrollVerticallyToPosition(channelIndex)
        val currentShow = if (dayShift == 0) channel.epgList.getCurrentShow() ?: channel.epgList.firstOrNull() else channel.epgList.firstOrNull()
        currentShow?.let { selectShow(channel, it) }
    }

    private fun scrollVerticallyToPosition(position: Int) {
        val epgLayoutManager = binding.rvChannels.layoutManager as? LinearLayoutManager ?: return
        val logosLayoutManager = binding.rvChannelsLogos.layoutManager as? LinearLayoutManager ?: return

        val rowHeightPx = EPGConfig.rowHeight.dpToPx
        val recyclerHeight = binding.rvChannels.height
        val offset = recyclerHeight / 2 - rowHeightPx / 2

        when {
            position <= 0 -> {
                epgLayoutManager.scrollToPositionWithOffset(0, 0)
                logosLayoutManager.scrollToPositionWithOffset(0, 0)
            }
            position >= channels.size - 1 -> {
                epgLayoutManager.scrollToPositionWithOffset(channels.size - 1, recyclerHeight - rowHeightPx)
                logosLayoutManager.scrollToPositionWithOffset(channels.size - 1, recyclerHeight - rowHeightPx)
            }
            else -> {
                epgLayoutManager.scrollToPositionWithOffset(position, offset)
                logosLayoutManager.scrollToPositionWithOffset(position, offset)
            }
        }
    }
    @Suppress("unused")
    fun selectShow(
        channelId: String, showId: String
    ) {
        val channel = channels.singleOrNull { it.tvChannelPosition.catAndChannelAccount == channelId } ?: return
        val show = channel.epgList.singleOrNull { it.idByAccountData == showId } ?: return
        selectShow(channel, show)
    }

    private fun selectShow(
        channel: TvChannelWithEpg, show: EpgDataOB
    ) {
        // Vertikal zum ausgewählten Kanal scrollen
        scrollVerticallyToPosition(channels.indexOf(channel))
        val showTag = ShowTag(channel.tvChannelPosition.catAndChannelAccount, show.idByAccountData)

        postDelayed({
            val channelRecycler =
                binding.rvChannels.findViewWithTag<RecyclerWithPositionView>("channel_${channel.tvChannelPosition.catAndChannelAccount}")
                    ?: return@postDelayed

            // 1. Pixelpositionen und Abmessungen berechnen
            val showStartPx = getCellWidth(
                startTime, DateTime(show.startTimestamp * 1000L)
            )
            val showEndPx = getCellWidth(
                startTime, DateTime(show.stopTimestamp * 1000L)
            )
            val showWidth = showEndPx - showStartPx

            // 2. Sichtbaren Bereich und Abmessungen ermitteln
            val visibleStartPx = currentTimeLineOffset()
            val visibleEndPx = visibleStartPx + binding.rvTimeLine.width
            val recyclerWidth = binding.rvTimeLine.width

            // 3. Ziel-Scroll-Position festlegen
            val targetScrollPosition = when {
                // A: Sendung ist vollständig sichtbar -> nicht scrollen
                showStartPx >= visibleStartPx && showEndPx <= visibleEndPx -> timeLineScrollPosition

                // B: Sendung ist am linken Rand abgeschnitten -> zum Start der Sendung scrollen
                showStartPx < visibleStartPx -> showStartPx

                // C: Sendung ist am rechten Rand abgeschnitten UND passt auf den Bildschirm -> mittig platzieren
                showWidth <= recyclerWidth -> showStartPx - (recyclerWidth / 2) + (showWidth / 2)

                // D: Sendung ist am rechten Rand abgeschnitten UND zu breit -> zum Start der Sendung scrollen
                else -> showStartPx
            }
            Log.d("WOHIN SCROLL HORIZONTAL", "TARGETSCROLL: $targetScrollPosition TIMELINESCROLLPOS: $timeLineScrollPosition")
            // 4. Scroll-Differenz berechnen und ausführen
            val scrollBy = targetScrollPosition - visibleStartPx

            if (scrollBy != 0) {
                syncHorizontalScroll(null, scrollBy)
            }

            binding.rvChannels.post {
                val showView = channelRecycler.findViewWithTag<View>(showTag) ?: return@post
                lastSelectedShowView?.isSelected = false
                showView.setShowTag(showTag.channelId, showTag.showId)
                lastSelectedShowView = showView
                showView.isSelected = true
                showView.requestFocus()
                listener?.onShowSelected(channel.tvChannelPosition, show)
                Log.d("TVGUIDE_SELECT", "focused view for show ${show.idByAccountData}")
            }

        }, EPGConfig.focusDelay)
    }

    @Suppress("unused")
    fun scrollToNow(now: DateTime) {
        post {
            val nowOffset = if (dayShift == 0) getCellWidth(startTime, now) - binding.rvChannels.width / 2 else 0
            val scrollBy = nowOffset - currentTimeLineOffset()
            Log.d("TVGUIDE_NOW", "scrollToNow nowOffset=$nowOffset current=${currentTimeLineOffset()} scrollBy=$scrollBy")
            if (scrollBy != 0) syncHorizontalScroll(null, scrollBy)
            updateTimeIndicator(false, now)
        }

    }


    private fun selectAndScrollToNow() {
        val now = DateTime()
        val currentChannel = lastSelectedShowView?.getShowTag()?.let { tag -> channels.firstOrNull { it.tvChannelPosition.catAndChannelAccount == tag.channelId } }
        val currentShow = currentChannel?.epgList?.getCurrentShow()

        if (currentShow == null) {
            // Fallback: Einfach zur aktuellen Zeit scrollen
            scrollToNow(now)
            return
        }

        // Finde die Sendung und lasse selectShow alles handhaben
        selectShow(currentChannel, currentShow)
    }

    private fun setTimeHeader() {
        val timeStepMinutes = 30
        val stepWidthPx = timeStepMinutes * EPGUtils.minuteToPixel
        val hours = mutableListOf<TimeLineData>()
        var currentTime = EPGUtils.startTime
        var hourIndex = 0

        while (currentTime <= EPGUtils.endTime) {
            val timeLabel = currentTime.toString("HH:mm")
            hours.add(TimeLineData(timeId = timeLabel, time = timeLabel, width = stepWidthPx, gravity = Gravity.CENTER, textSizeSp = if (hourIndex % 2 == 0) 13f else 11f))
            currentTime = currentTime.plusMinutes(timeStepMinutes)
            hourIndex++
        }
        timeAdapter.submitList(hours)
    }

    private fun setChannelsLogo(items: List<TvChannelWithEpg>) { channelLogosAdapter.submitList(items) }
    private fun setChannels(items: List<TvChannelWithEpg>, now: DateTime) {
        (binding.rvChannels.adapter as? TvGuideChannelListAdapter)?.submitList(items) {
            updateVisibleEpgProgress(now)
        }
    }

    // --- Neue Funktion zum Prüfen des Timeline-Endes ---
    private fun checkForEndOfTimeline(dx: Int) {
        Log.d("NACHLADEN GEFÄLLIG", "JO LAD MAL")
        // Nur prüfen, wenn nach rechts gescrollt wird
        if (dx <= 0) return

        val layoutManager = binding.rvTimeLine.layoutManager as? LinearLayoutManager ?: return
        val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

        // Prüfen, ob das Ende der Timeline fast erreicht ist (z.B. die letzten 5 Items)
        if (lastVisibleItemPosition >= timeAdapter.itemCount - 5) {
            // Hier deine Logik zum Nachladen des EPG-Materials einfügen
            Log.d("EPG_LOAD", "Ende der Timeline erreicht, lade EPG nach...")
            // Hier könntest du eine Funktion aufrufen, die neue EPG-Daten anfordert.
        }
    }

    private fun scrollChannels(recyclerView: RecyclerView?, dy: Int) {
        // If the event came from the Logos Recycler, scroll the Channels Recycler
        if (binding.rvChannelsLogos == recyclerView) {
            binding.rvChannels.scrollBy(0, dy)
        }
    }

    private fun scrollChannelsLogo(recyclerView: RecyclerView?, dy: Int) {
        // If the event came from the Channels Recycler, scroll the Logos Recycler
        if (binding.rvChannels == recyclerView) {
            binding.rvChannelsLogos.scrollBy(0, dy)
        }
    }

    private fun List<EpgDataOB>.getCurrentShow(): EpgDataOB? {
        val currentTime = System.currentTimeMillis() / 1000
        return this.find { it.startTimestamp < currentTime && it.stopTimestamp > currentTime }
    }

    private fun syncHorizontalScroll(sourceRv: RecyclerView?, dx: Int) {
        if (dx == 0) return
        if (isSyncingHorizontal) {
            Log.d("TVGUIDE_SYNC", "sync suppressed (already syncing) source=${debugRvDesc(sourceRv)} dx=$dx")
            return
        }

        isSyncingHorizontal = true
        Log.d("TVGUIDE_SYNC", ">>> start sync from=${debugRvDesc(sourceRv)} dx=$dx")
        logAllOffsets("before")

        try {
            if (binding.rvTimeLine != sourceRv) {
                binding.rvTimeLine.scrollBy(dx, 0)
                Log.d("TVGUIDE_SYNC", "timeline scrolled by $dx → offset=${binding.rvTimeLine.computeHorizontalScrollOffset()}")
            }

            val rvChildren = binding.rvChannels.children.mapNotNull { it as? RecyclerWithPositionView }.toList()
            rvChildren.forEach { childRv ->
                if (childRv != sourceRv) {
                    childRv.scrollBy(dx, 0)
                    Log.d("TVGUIDE_SYNC", "childRv scrolled by $dx → offset=${childRv.computeHorizontalScrollOffset()}")
                }
            }

            timeLineScrollPosition = currentTimeLineOffset()
            logAllOffsets("after")
            Log.d("TVGUIDE_SYNC", "<<< end sync offset=$timeLineScrollPosition")
        } finally {
            this.post { isSyncingHorizontal = false }
        }
    }
}