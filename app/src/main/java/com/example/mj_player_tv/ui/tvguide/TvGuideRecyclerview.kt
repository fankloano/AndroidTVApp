package com.example.mj_player_tv.ui.tvguide

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.TvguideRecyclerviewBinding
import com.example.mj_player_tv.ui.tvguide.adapters.TimeLineAdapter
import com.example.mj_player_tv.ui.tvguide.adapters.TvGuideChannelListAdapter
import com.example.mj_player_tv.ui.tvguide.adapters.TvGuideChannelLogosAdapter
import com.example.mj_player_tv.ui.tvguide.adapters.TvGuideEpgAdapter
import com.example.mj_player_tv.utils.views.RecyclerWithPositionView
import com.volkov.EPGConfig
import com.volkov.epgrecycler.Constants
import com.volkov.epgrecycler.EPGUtils
import com.volkov.epgrecycler.dpToPx
import dev.androidbroadcast.vbpd.viewBinding
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import kotlin.math.max
import kotlin.math.min

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

    enum class MoveDirection {
        UP, DOWN, LEFT, RIGHT, NONE
    }

    private val horizontalScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            scrollAll(recyclerView, dx)
            scrollTimeHeader(recyclerView, dx)
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
        EPGUtils.dayShift = day
    }

    private val timeAdapter = TimeLineAdapter()

    private val channelLogosAdapter = TvGuideChannelLogosAdapter()
    private var timeLineScrollPosition = 0

    private val focusListener = OnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            lastSelectedShowView?.isSelected = true
        }
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
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    return true
                }
            })
        }

        binding.rvChannelsLogos.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = channelLogosAdapter
            setHasFixedSize(true)
            itemAnimator = null
            onFocusChangeListener = focusListener
        }
    }

    fun initView(
        channels: List<TvChannelWithEpg>,
        initChannel: Long? = null
    ) {
        handler.removeCallbacks(updateRunnable)
        val now = DateTime()
        setTimeHeader()
        initChannelRecycler()
        this.channels = channels
        setChannelsLogo(channels)
        setChannels(channels, now)
        scrollToNow(now)
        // Starte den Timer für die automatische Aktualisierung
        handler.post(updateRunnable)
    }

    private fun getNextShowByDirection(direction: MoveDirection): View? {
        val currentChannelTag = lastSelectedShowView?.tag?.toString()?.split("#") ?: return null
        val channelId = currentChannelTag.firstOrNull() ?: return null
        val showId = currentChannelTag.getOrNull(1) ?: return null
        val currentChannel = channels.firstOrNull { it.tvChannelPosition.catAndChannelAccount == channelId } ?: return null
        val currentChannelIndex = channels.indexOf(currentChannel)

        val currentChannelShow = currentChannel.epgList.firstOrNull { it.idByAccountData == showId } ?: return null

        val (channel, desiredShow) = when (direction) {
            MoveDirection.UP -> {
                val prevChannel = channels.getOrNull(currentChannelIndex - 1) ?: return null
                // Bestimme die Suchzeit: ist die aktuelle Sendung live?
                val foundingTime = if (currentChannelShow.isLiveShow) {
                    System.currentTimeMillis() / 1000
                } else {
                    currentChannelShow.startTimestamp
                }

                val desiredShow = prevChannel.epgList.singleOrNull {
                    it.startTimestamp <= foundingTime && it.stopTimestamp > foundingTime
                } ?: prevChannel.epgList.lastOrNull() // Fallback zum letzten Element, falls kein Treffer

                Pair(prevChannel, desiredShow)
            }

            MoveDirection.DOWN -> {
                val nextChannel = channels.getOrNull(currentChannelIndex + 1) ?: return null
                // Bestimme die Suchzeit: ist die aktuelle Sendung live?
                val foundingTime = if (currentChannelShow.isLiveShow) {
                    System.currentTimeMillis() / 1000
                } else {
                    currentChannelShow.startTimestamp
                }

                val desiredShow = nextChannel.epgList.singleOrNull {
                    it.startTimestamp <= foundingTime && it.stopTimestamp > foundingTime
                } ?: nextChannel.epgList.firstOrNull() // Fallback zum ersten Element, falls kein Treffer

                Pair(nextChannel, desiredShow)
            }

            MoveDirection.LEFT -> {
                // Unveränderte Logik für die horizontale Navigation
                val currentShowIndex = currentChannel.epgList.indexOf(currentChannelShow)
                val desiredShow = currentChannel.epgList.getOrNull(currentShowIndex - 1) ?: return null
                Pair(currentChannel, desiredShow)
            }

            MoveDirection.RIGHT -> {
                // Unveränderte Logik für die horizontale Navigation
                val currentShowIndex = currentChannel.epgList.indexOf(currentChannelShow)
                val desiredShow = currentChannel.epgList.getOrNull(currentShowIndex + 1) ?: return null
                Pair(currentChannel, desiredShow)
            }

            MoveDirection.NONE -> return null
        }

        // Wenn keine gewünschte Sendung gefunden wird, breche ab
        desiredShow ?: return null

        selectShow(channel, desiredShow)
        return lastSelectedShowView
    }

    private val EpgDataOB.isLiveShow: Boolean
        get() = startTimestamp < System.currentTimeMillis() / 1000 && stopTimestamp > System.currentTimeMillis() / 1000

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when {
            event.onDownPressed() -> {
                // Nur zur nächsten Sendung im nächsten Kanal navigieren.
                getNextShowByDirection(MoveDirection.DOWN)
                true
            }
            event.onUpPressed() -> {
                // Nur zur nächsten Sendung im vorherigen Kanal navigieren.
                getNextShowByDirection(MoveDirection.UP)
                true
            }
            event.onLeftPressed() -> {
                // Zur nächsten Sendung nach links navigieren.
                getNextShowByDirection(MoveDirection.LEFT)
                true
            }
            event.onRightPressed() -> {
                // Zur nächsten Sendung nach rechts navigieren.
                getNextShowByDirection(MoveDirection.RIGHT)
                true
            }
            event.onEnterPressed() -> {
                // Auf eine EPG-Sendung klicken.
                lastSelectedShowView?.let {
                    val tag = it.tag.toString().split("#")
                    // Das ist die richtige Stelle, um den Klick-Event zu verarbeiten
                    if (tag.size == 2) {
                        val channel = channels.first { it.tvChannelPosition.catAndChannelAccount == tag[0] }
                        val epgData = channel.epgList.first { it.idByAccountData == tag[1] }
                        listener?.onShowClick(channel.tvChannelPosition, epgData)
                    }
                }
                true
            }
            event.onBackPressed() -> {
                lastSelectedShowView?.let { lastShowView ->
                    val tag = lastShowView.tag.toString().split("#")
                    // Das ist die richtige Stelle, um den Klick-Event zu verarbeiten
                    if (tag.size == 2) {
                        val channel = channels.first { it.tvChannelPosition.catAndChannelAccount == tag[0] }
                        val epgData = channel.epgList.first { it.idByAccountData == tag[1] }
                        val isLive = epgData.isLiveShow
                        if (isLive) {
                            listener?.onShowExit()
                        } else {
                            selectAndScrollToNow()
                        }
                    }
                }
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun initChannelRecycler() {
        binding.rvChannels.apply {
            val channelListAdapter = TvGuideChannelListAdapter(horizontalScrollListener)
            layoutManager = object : LinearLayoutManager(context) {
                override fun onInterceptFocusSearch(focused: View, direction: Int): View? = null
            }
            adapter = channelListAdapter
            setHasFixedSize(true)
            onFocusChangeListener = focusListener
            binding.rvChannels.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    syncChannelLogoScroll()
                }
            })
        }
    }

    private fun syncChannelLogoScroll() {
        val channelLayoutManager = binding.rvChannels.layoutManager as LinearLayoutManager
        val logoLayoutManager = binding.rvChannelsLogos.layoutManager as LinearLayoutManager

        val firstVisible = channelLayoutManager.findFirstVisibleItemPosition()
        val offset = channelLayoutManager.findViewByPosition(firstVisible)?.top ?: 0

        logoLayoutManager.scrollToPositionWithOffset(firstVisible, offset)
    }

    // Korrigierte updateTimeIndicator-Methode
    fun updateTimeIndicator(now: DateTime) {
        val indicatorPosition = EPGUtils.getCellWidth(com.example.mj_player_tv.ui.tvguide.EPGUtils.startEpgTime, now)
        val relativePosition = indicatorPosition - timeLineScrollPosition

        val isVisible = relativePosition in (0..binding.rvTimeLine.width)

        binding.timeIndicator.isVisible = isVisible && EPGConfig.showTimeLine
        binding.timeIndicatorBubble.isVisible = binding.timeIndicator.isVisible

        binding.timeIndicator.updateLayoutParams<LayoutParams> {
            marginStart = relativePosition
        }

        binding.tvTimeLineLabel.text = now.toString("HH:mm")

    }

    private fun updateVisibleEpgProgress(now: DateTime) {
        val layoutManager = binding.rvChannels.layoutManager as? LinearLayoutManager ?: return

        // Iteriere durch alle sichtbaren Channel-Views
        for (i in layoutManager.findFirstVisibleItemPosition()..layoutManager.findLastVisibleItemPosition()) {
            val channelRecycler = layoutManager.findViewByPosition(i) as? RecyclerWithPositionView ?: continue
            val epgAdapter = channelRecycler.adapter as? TvGuideEpgAdapter ?: continue
            val channelData = channels.getOrNull(i) ?: continue

            val currentShow = channelData.epgList.getCurrentShow()

            if (currentShow != null) {
                val showIndex = channelData.epgList.indexOf(currentShow)

                // Benutze einen Payload, um nur den Fortschrittsbalken zu aktualisieren
                epgAdapter.notifyItemChanged(showIndex, now)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            val now = DateTime()
            updateTimeIndicator(now)
            updateVisibleEpgProgress(now) // Korrigierter Aufruf
            handler.postDelayed(this, 60000)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(updateRunnable)
    }
    fun selectCurrentShow(channelId: String?) {
        val channel =
            channels.singleOrNull { it.tvChannelPosition.catAndChannelAccount == channelId } ?: channels.firstOrNull() ?: return
        val channelIndex = channels.indexOf(channel)
        scrollVerticallyToPosition(channelIndex)
        val currentShow =
            if (EPGUtils.dayShift == 0) channel.epgList.getCurrentShow() ?: channel.epgList.firstOrNull()
            else channel.epgList.firstOrNull()
        currentShow?.let {
            selectShow(channel, currentShow)
        }
    }

    private fun scrollVerticallyToPosition(position: Int) {
        val layoutManager = binding.rvChannels.layoutManager as? LinearLayoutManager ?: return

        val rowHeightPx = EPGConfig.rowHeight.dpToPx
        val recyclerHeight = binding.rvChannels.height

        // Zieloffset so berechnen, dass die Zeile mittig steht
        val offset = recyclerHeight / 2 - rowHeightPx / 2

        // Grenzen beachten (erste und letzte Channels nicht nach außen ziehen)
        if (position <= 0) {
            layoutManager.scrollToPositionWithOffset(0, 0)
        } else if (position >= channels.size - 1) {
            layoutManager.scrollToPositionWithOffset(channels.size - 1, recyclerHeight - rowHeightPx)
        } else {
            layoutManager.scrollToPositionWithOffset(position, offset)
        }
    }

    private fun selectShow(
        channel: TvChannelWithEpg, show: EpgDataOB
    ) {
        scrollVerticallyToPosition(channels.indexOf(channel))
        val tag = "${channel.tvChannelPosition.catAndChannelAccount}#${show.idByAccountData}"

        postDelayed({
            val channelRecycler =
                binding.rvChannels.findViewWithTag<RecyclerWithPositionView>("channel_${channel.tvChannelPosition.catAndChannelAccount}")
                    ?: return@postDelayed

            // 1. Position der Sendung und sichtbare Breite berechnen
            val epgVisibleWidth = resources.displayMetrics.widthPixels - 160.dpToPx - 5.dpToPx
            val showsBefore = channel.epgList.takeWhile { it.idByAccountData != show.idByAccountData }
            var showStartPosition = 0
            showsBefore.forEach { prevShow ->
                val start = if (DateTime(prevShow.startTimestamp * 1000L).isBefore(EPGUtils.startTime)) EPGUtils.startTime else DateTime(prevShow.startTimestamp * 1000L)
                val end = if (DateTime(prevShow.stopTimestamp * 1000L).isAfter(EPGUtils.endTime)) EPGUtils.endTime else DateTime(prevShow.stopTimestamp * 1000L)
                showStartPosition += EPGUtils.getCellWidth(start, end)
                showStartPosition += EPGConfig.marginEnd.dpToPx
            }
            val showWidth = EPGUtils.getCellWidth(DateTime(show.startTimestamp * 1000L), DateTime(show.stopTimestamp * 1000L))

            // 2. Gewünschte Scroll-Position für die Zentrierung berechnen
            val desiredScrollPosition = showStartPosition - (epgVisibleWidth / 2) + (showWidth / 2)

            // 3. Maximale horizontale Scroll-Position berechnen, um nicht über das Ende zu scrollen
            // Sie müssen die Gesamtbreite aller Sendungen (totalTimelineWidth) selbst berechnen
            val totalTimelineWidth = EPGUtils.getCellWidth(EPGUtils.startTime, EPGUtils.endTime)

            // 4. Die gewünschte Position beschränken, um Überlauf zu verhindern
            val maxScrollPosition = totalTimelineWidth - epgVisibleWidth
            val finalScrollPosition = max(0, min(desiredScrollPosition, maxScrollPosition))

            // 5. Scroll-Delta berechnen und den Scroll-Vorgang ausführen
            val scrollBy = finalScrollPosition - timeLineScrollPosition
            if (scrollBy != 0) {
                scrollTimeHeader(null, scrollBy)
                scrollAll(null, scrollBy)
            }

            val showView = channelRecycler.findViewWithTag<View>(tag) ?: return@postDelayed
            showView.requestFocus()
            lastSelectedShowView?.isSelected = false
            lastSelectedShowView = showView
            showView.isSelected = true
            listener?.onShowSelected(channel.tvChannelPosition, show)
        }, EPGConfig.focusDelay)
    }

    @Suppress("unused")
    fun scrollToNow(now: DateTime) {
        post {
            val nowOffset = if (EPGUtils.dayShift == 0) EPGUtils.getCellWidth(
                EPGUtils.startTime, now
            ) - binding.rvChannels.width / 2
            else 0
            val scrollBy = nowOffset - timeLineScrollPosition
            scrollTimeHeader(null, scrollBy)
            scrollAll(null, scrollBy)

            // Aktualisieren Sie den Time Indicator mit demselben Zeitstempel
            updateTimeIndicator(now)
        }
    }

    // In der TvGuideRecyclerview-Klasse
    private fun selectAndScrollToNow() {
        val now = DateTime()
        // Schritt 1: Finde den aktuellen Kanal und die aktuelle Sendung
        // Du musst hier den aktuell fokussierten Kanal finden oder den Standardkanal verwenden

        val currentChannel = lastSelectedShowView?.let {
            val tag = it.tag.toString().split("#")
            // Das ist die richtige Stelle, um den Klick-Event zu verarbeiten
            if (tag.size == 2) {
                val channel = channels.first { it.tvChannelPosition.catAndChannelAccount == tag[0] }
                channel
            } else {
                return
            }
        }

        val currentShow = currentChannel?.epgList?.getCurrentShow()
        if (currentShow == null) {
            // Fallback-Logik, falls keine aktuelle Sendung gefunden wird
            scrollToNow(now)
            return
        }

        // Schritt 2: Scrolle horizontal zur aktuellen Zeit
        val nowOffset = if (EPGUtils.dayShift == 0) EPGUtils.getCellWidth(
            EPGUtils.startTime, now
        ) - binding.rvChannels.width / 2
        else 0
        val scrollBy = nowOffset - timeLineScrollPosition
        scrollTimeHeader(null, scrollBy)
        scrollAll(null, scrollBy)

        // Schritt 3: Setze den Fokus auf die aktuelle Sendung
        selectShow(currentChannel, currentShow)
    }

    private fun setTimeHeader() {
        val timeStepMinutes = 30 // oder 60, je nachdem, welche Zeitmarken Sie möchten
        val stepWidthPx = timeStepMinutes * EPGUtils.minuteToPixel

        val hours = mutableListOf<TimeLineData>()
        var currentTime = EPGUtils.startTime
        var hourIndex = 0

        while (currentTime.isBefore(EPGUtils.endTime) || currentTime.isEqual(EPGUtils.endTime)) {
            val timeLabel = currentTime.toString("HH:mm")
            val isFullHour = hourIndex % 2 == 0
            // 1. Die Logik für die Ausrichtung

            hours.add(
                TimeLineData(
                    timeId = timeLabel,
                    time = timeLabel,
                    width = stepWidthPx,
                    gravity = Gravity.CENTER, // Füge die berechnete Ausrichtung zum Datenmodell hinzu
                    textSizeSp = if (isFullHour) 13f else 11f
                )
            )

            currentTime = currentTime.plusMinutes(timeStepMinutes)
            hourIndex++
        }
        timeAdapter.submitList(hours.toList())
    }



    private fun setChannelsLogo(items: List<TvChannelWithEpg>) {
        channelLogosAdapter.submitList(items)
    }

    private fun setChannels(items: List<TvChannelWithEpg>, now: DateTime) {
        (binding.rvChannels.adapter as? TvGuideChannelListAdapter)?.submitList(items) {
            // Dieser Block wird ausgeführt, wenn das Rendering abgeschlossen ist
            // Hier können wir sicher sein, dass die Views existieren
            // Rufen Sie die Methode zum Aktualisieren des Fortschritts hier auf,
            // um sicherzustellen, dass die Fortschrittsbalken initial korrekt sind
            updateVisibleEpgProgress(now)
            updateTimeIndicator(now)
        }
    }

    private fun scrollAll(recyclerView: RecyclerView?, dx: Int) {
        val rvChildren =
            binding.rvChannels.children.map { it as? RecyclerWithPositionView }.toList()
        rvChildren.forEach {
            if (recyclerView != it) it?.scrollHorizontallyBy(dx)
        }
    }

    private fun scrollTimeHeader(recyclerView: RecyclerView?, dx: Int) {
        timeLineScrollPosition = max(timeLineScrollPosition + dx, 0)
        EPGUtils.currentEpgTime = EPGUtils.startTime.plusMinutes(timeLineScrollPosition / EPGUtils.minuteToPixel)
        updateTimeIndicator(DateTime())
        if (binding.rvTimeLine != recyclerView) {
            binding.rvTimeLine.apply {
                clearOnScrollListeners()
                scrollBy(dx, 0)
                addOnScrollListener(horizontalScrollListener)
            }
        }
    }

    private fun List<EpgDataOB>.getCurrentShow(): EpgDataOB? {
        val currentTime = System.currentTimeMillis() / 1000
        return this.find { it.startTimestamp < currentTime && it.stopTimestamp > currentTime }
    }


}