package com.example.mj_player_tv.ui.tvguide

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.marginStart
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.TvguideRecyclerviewBinding
import com.example.mj_player_tv.ui.tvguide.EPGUtils.timeLabelWidth
import com.example.mj_player_tv.ui.tvguide.adapters.TimeLineAdapter
import com.example.mj_player_tv.ui.tvguide.adapters.TvGuideChannelListAdapter
import com.example.mj_player_tv.ui.tvguide.adapters.TvGuideChannelLogosAdapter
import com.example.mj_player_tv.utils.views.RecyclerWithPositionView
import com.volkov.EPGConfig
import com.volkov.epgrecycler.Constants
import com.volkov.epgrecycler.EPGUtils
import com.volkov.epgrecycler.EPGUtils.getDayLength
import com.volkov.epgrecycler.EPGUtils.maxHour
import com.volkov.epgrecycler.EPGUtils.minuteToPixel
import com.volkov.epgrecycler.EPGUtils.startTime
import com.volkov.epgrecycler.dpToPx
import com.volkov.epgrecycler.getColorRes
import com.volkov.epgrecycler.onDownPressed
import com.volkov.epgrecycler.onEnterPressed
import com.volkov.epgrecycler.onLeftPressed
import com.volkov.epgrecycler.onRightPressed
import com.volkov.epgrecycler.onUpPressed
import dev.androidbroadcast.vbpd.viewBinding
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Minutes
import kotlin.math.abs
import kotlin.math.max

class TvGuideRecyclerview @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding by viewBinding(TvguideRecyclerviewBinding::bind)

    private var channels: List<TvChannelWithEpg> = emptyList()

    private var currentDummyIndex = -1

    private var lastSelectedShowView: View? = null
    private var lastSelectedShowViewTemp: View? = null
    private var lastSelectedDummyView: View? = null

    interface OnEventListener {
        fun onShowSelected(channelId: String, showId: String)
        fun onShowClick(channelId: String, showId: String)
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
            updateTimeIndicator(withSubmit = false)
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

    private val verticalScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            scrollChannels(recyclerView, dy)
            scrollChannelsLogo(recyclerView, dy)
        }
    }

    private val timeAdapter = TimeLineAdapter()

    private val channelLogosAdapter = TvGuideChannelLogosAdapter()
    private var timeLineScrollPosition = 0

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
            addOnScrollListener(verticalScrollListener)
            onFocusChangeListener = focusListener
        }
    }

    fun initView(
        channels: List<TvChannelWithEpg>,
        initChannel: Long? = null
    ) {
        setTimeHeader()
        initChannelRecycler()
        this.channels = channels
        setChannelsLogo(channels)
        setChannels(channels)
        scrollToNow()
        post {
            binding.rvChannels.requestFocus()
            selectCurrentShow(initChannel ?: channels.firstOrNull()?.id)
        }
    }

    private fun getNextShowByDirection(direction: MoveDirection): View? {
        val currentChannelTag = lastSelectedShowView?.tag?.toString()?.split("#") ?: return null
        val channelId = currentChannelTag.firstOrNull() ?: return null
        val showId = currentChannelTag.getOrNull(1) ?: return null
        val currentChannel = channels.firstOrNull { it.tvChannelPosition.catAndChannelAccount == channelId } ?: return null
        val currentChannelIndex = channels.indexOf(currentChannel)
        val (channel, desiredShow) = when (direction) {
            MoveDirection.UP -> {
                val prevChannel = channels.getOrNull(currentChannelIndex - 1) ?: return null
                val currentChannelShow =
                    currentChannel.epgList.firstOrNull { it.idByAccountData == showId } ?: return null
                val foundingTime = currentChannelShow.startTimestamp + 1
                val desiredShow = prevChannel.epgList.singleOrNull {
                    it.startTimestamp < foundingTime && it.stopTimestamp > foundingTime
                } ?: prevChannel.epgList.firstOrNull {
                    it.startTimestamp > foundingTime
                } ?: prevChannel.epgList.lastOrNull {
                    it.stopTimestamp < foundingTime
                } ?: return null
                Pair(prevChannel, desiredShow)
            }

            MoveDirection.DOWN -> {
                val nextChannel = channels.getOrNull(currentChannelIndex + 1) ?: return null
                val currentChannelShow =
                    currentChannel.epgList.firstOrNull { it.idByAccountData == showId } ?: return null
                val foundingTime = currentChannelShow.startTimestamp + 1
                val desiredShow = nextChannel.epgList.firstOrNull {
                    it.startTimestamp < foundingTime && it.stopTimestamp > foundingTime
                } ?: nextChannel.epgList.firstOrNull {
                    it.startTimestamp > foundingTime
                } ?: nextChannel.epgList.lastOrNull {
                    it.stopTimestamp < foundingTime
                } ?: return null
                Pair(nextChannel, desiredShow)
            }

            MoveDirection.LEFT -> {
                val currentChannelShow =
                    currentChannel.epgList.singleOrNull { it.idByAccountData == showId } ?: return null
                val currentShowIndex = currentChannel.epgList.indexOf(currentChannelShow)
                val desiredShow =
                    currentChannel.epgList.getOrNull(currentShowIndex - 1) ?: return null
                Pair(currentChannel, desiredShow)
            }

            MoveDirection.RIGHT -> {
                val currentChannelShow =
                    currentChannel.epgList.singleOrNull { it.idByAccountData == showId } ?: return null
                val currentShowIndex = currentChannel.epgList.indexOf(currentChannelShow)
                val desiredShow =
                    currentChannel.epgList.getOrNull(currentShowIndex + 1) ?: return null
                Pair(currentChannel, desiredShow)
            }

            MoveDirection.NONE -> return null
        }
        selectShow(channel, desiredShow)
        return lastSelectedShowView
    }

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
                    val tag = lastSelectedShowView?.tag.toString().split("#")
                    listener?.onShowClick(tag[0], tag[1])
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
            addOnScrollListener(verticalScrollListener)
            onFocusChangeListener = focusListener
        }
    }

    fun updateTimeIndicator(withSubmit: Boolean = true) {
        val now = DateTime()

        // Berechnet die aktuelle Position in Pixeln, basierend auf der vergangenen Zeit.
        val indicatorPosition = EPGUtils.getCellWidth(EPGUtils.startTime, now)

        // Fügt den gleichen Puffer wie bei den Sendungen hinzu.
        val bufferOffset = (15 * EPGUtils.minuteToPixel) / 2

        // Die finale Position ist der Standard-Offset plus der Puffer.
        val finalIndicatorPosition = indicatorPosition + bufferOffset

        // Prüft, ob der Indikator im sichtbaren Bereich ist.
        val isVisible = finalIndicatorPosition - timeLineScrollPosition in (0..binding.rvTimeLine.width)

        binding.timeIndicator.isVisible = isVisible && EPGConfig.showTimeLine
        binding.tvTimeLineLabel.isVisible = binding.timeIndicator.isVisible

        // Setzt die neue, gepufferte Startposition für den Indikator.
        binding.timeIndicator.updateLayoutParams<LayoutParams> {
            marginStart = finalIndicatorPosition
        }

        binding.tvTimeLineLabel.updateLayoutParams<LayoutParams> {
            marginStart = binding.timeIndicator.marginStart - 25.dpToPx
        }

        binding.tvTimeLineLabel.text = DateTime().toString("HH:mm")
        if (!withSubmit) return
        binding.rvChannels.children.toList().map { it as? RecyclerView }.forEach {
            it?.children?.toList()?.map { view -> view.tag?.toString() ?: "" }?.forEach { tag ->
                if (tag.isNotEmpty() && tag.contains("#")) {
                    val v = tag.split("#")
                    val channelId = v.firstOrNull()
                    channels.singleOrNull { channel -> channel.tvChannelPosition.catAndChannelAccount == channelId }?.let { channel ->
                        val currentShow = channel.epgList.getCurrentShow()
                        findViewWithTag<View>(tag).isActivated = currentShow?.idByAccountData == v[1]
                    }
                }
            }
        }
    }

    private fun selectCurrentShow(channelId: Long?) {
        val channel =
            channels.singleOrNull { it.id == channelId } ?: channels.firstOrNull() ?: return
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
        val rowHeightPx = EPGConfig.rowHeight.dpToPx
        val marginTopPx = EPGConfig.marginTop.dpToPx
        val scrollToBot = marginTopPx * (position + 1) + rowHeightPx * (position + 1)
        val scrollToTop = scrollToBot - rowHeightPx
        val recycler = binding.rvChannelsLogos
        val recyclerRange = recycler.verticalPosition..recycler.verticalPosition + recycler.height
        if (scrollToTop in recyclerRange && scrollToBot in recyclerRange) return
        val topScroll = abs(scrollToTop - recyclerRange.first)
        val botScroll = abs(scrollToBot - recyclerRange.last)
        if (topScroll < botScroll) {
            scrollChannelsLogo(null, -topScroll)
            scrollChannels(null, -topScroll)
        } else {
            scrollChannelsLogo(null, botScroll)
            scrollChannels(null, botScroll)
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
        scrollVerticallyToPosition(channels.indexOf(channel))
        val tag = "${channel.id}#${show.id}"
        postDelayed({
            val channelRecycler =
                binding.rvChannels.findViewWithTag<RecyclerWithPositionView>("channel_${channel.tvChannelPosition.catAndChannelAccount}")
                    ?: return@postDelayed

            // Correct way to find the show's horizontal offset
            val showsBefore = channel.epgList.takeWhile { it.idByAccountData != show.idByAccountData }
            var cumulativeWidth = 0
            showsBefore.forEach { prevShow ->
                val start = if (DateTime(prevShow.startTimestamp * 1000L).isBefore(EPGUtils.startTime)) EPGUtils.startTime else DateTime(prevShow.startTimestamp * 1000L)
                val end = if (DateTime(prevShow.stopTimestamp * 1000L).isAfter(EPGUtils.endTime)) EPGUtils.endTime else DateTime(prevShow.stopTimestamp * 1000L)
                cumulativeWidth += EPGUtils.getCellWidth(start, end)
                cumulativeWidth += EPGConfig.marginEnd.dpToPx
            }

            // Correct scroll position is the cumulative width of all previous shows
            val scrollTo = cumulativeWidth

            // The rest of your logic remains the same
            val showWidth = EPGUtils.getCellWidth(DateTime(show.startTimestamp * 1000L), DateTime(show.stopTimestamp * 1000L))
            val scrollToEnd = scrollTo + showWidth
            val recyclerRange =
                channelRecycler.horizontalPosition..channelRecycler.horizontalPosition + channelRecycler.width
            apply {
                if (scrollTo in recyclerRange && scrollToEnd in recyclerRange) return@apply
                if (showWidth <= channelRecycler.width) {
                    val leftScroll = abs(scrollTo - recyclerRange.first)
                    val rightScroll = abs(scrollToEnd - recyclerRange.last)
                    if (leftScroll < rightScroll) {
                        scrollTimeHeader(null, -leftScroll)
                        scrollAll(null, -leftScroll)
                    } else {
                        scrollTimeHeader(null, rightScroll)
                        scrollAll(null, rightScroll)
                    }
                } else {
                    val scrollBy = scrollTo - timeLineScrollPosition
                    scrollTimeHeader(null, scrollBy)
                    scrollAll(null, scrollBy)
                    updateTimeIndicator()
                }
            }

            val showView = channelRecycler.findViewWithTag<View>(tag) ?: return@postDelayed
            showView.requestFocus()
            lastSelectedShowView?.isSelected = false
            lastSelectedShowView = showView
            showView.isSelected = true
            listener?.onShowSelected(channel.tvChannelPosition.catAndChannelAccount, show.idByAccountData)
        }, EPGConfig.focusDelay)
    }
    @Suppress("unused")
    fun scrollToNow() {
        post {
            val nowOffset = if (EPGUtils.dayShift == 0) EPGUtils.getCellWidth(
                EPGUtils.startTime, DateTime()
            ) - binding.rvChannels.width / 2
            else 0
            val scrollBy = nowOffset - timeLineScrollPosition
            scrollTimeHeader(null, scrollBy)
            scrollAll(null, scrollBy)
            updateTimeIndicator()
        }
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

    private fun setChannels(items: List<TvChannelWithEpg>) {
        Log.d("TVUGUIDE CHECK","CHANNELLISTSIZE: ${items.size}")
        (binding.rvChannels.adapter as? TvGuideChannelListAdapter)?.submitList(items)
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
        updateTimeIndicator()
        if (binding.rvTimeLine != recyclerView) {
            binding.rvTimeLine.apply {
                clearOnScrollListeners()
                scrollBy(dx, 0)
                addOnScrollListener(horizontalScrollListener)
            }
        }
    }

    private fun scrollChannels(recyclerView: RecyclerView?, dy: Int) {
        if (binding.rvChannels != recyclerView) {
            binding.rvChannels.apply {
                clearOnScrollListeners()
                scrollBy(0, dy)
                addOnScrollListener(verticalScrollListener)
            }
        }
    }

    private fun scrollChannelsLogo(recyclerView: RecyclerView?, dy: Int) {
        if (binding.rvChannelsLogos != recyclerView) {
            binding.rvChannelsLogos.apply {
                clearOnScrollListeners()
                scrollBy(0, dy)
                addOnScrollListener(verticalScrollListener)
            }
        }
    }


    private fun List<EpgDataOB>.getCurrentShow(): EpgDataOB? {
        val currentTime = System.currentTimeMillis() / 1000
        return this.find { it.startTimestamp < currentTime && it.stopTimestamp > currentTime }
    }
}