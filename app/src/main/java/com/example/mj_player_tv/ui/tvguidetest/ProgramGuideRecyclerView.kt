package com.example.mj_player_tv.ui.tvguidetest

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.ShowTag
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.ProgramguideRecyclerviewBinding
import com.example.mj_player_tv.ui.tvguide.EPGUtils.dayShift
import com.example.mj_player_tv.ui.tvguidetest.ProgramGuideUtils.epgStartTime
import com.example.mj_player_tv.ui.tvguidetest.ProgramGuideUtils.getCellWidth
import com.example.mj_player_tv.utils.EpgScrollSyncManager
import org.joda.time.DateTime
import org.joda.time.Minutes
import kotlin.math.abs

class ProgramGuideRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {


    private val binding: ProgramguideRecyclerviewBinding

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.programguide_recyclerview, this, true)
        binding = ProgramguideRecyclerviewBinding.bind(view)

    }
    // Interfaces für Fokus & Selektion
    var manager = ProgramGuideManager()

    private val scrollSyncHelper = EpgScrollSyncManager()
    private var channels: List<TvChannelWithEpg> = emptyList()
    private val channelsAdapter = ProgramGuideChannelAdapter(manager, scrollSyncHelper)

    private val timeLineAdapter = ProgramGuideTimeLineAdapter()
    var programSelectionListener: ProgramSelectionListener? = null

    private fun currentTimeLineOffset(): Int = binding.rvTimeLine.computeHorizontalScrollOffset()

    // Fokus-Tracking
    private var focusedChannelIndex = 0
    private var focusedProgramIndex = 0

    private var lastSelectedShowView: View? = null


    enum class MoveDirection { UP, DOWN, LEFT, RIGHT, NONE }

    interface ProgramSelectionListener {
        fun onShowSelected(channelPosition: ChannelPositions, epgData: EpgDataOB)
        fun onShowClick(channelPosition: ChannelPositions, epgData: EpgDataOB)
        fun onShowExit()
    }
    // Manager (Channels + Schedules)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = when {
            event.onDownPressed() -> {
                val show = getNextShowByDirection(MoveDirection.DOWN)
                if (show == null) {
                    Log.d("SHOOOOOOW", "= NULL")
                } else {
                    Log.d("SHOOOOOOW", "SHOW: $show")
                }
                true
            }
            event.onUpPressed() -> { getNextShowByDirection(MoveDirection.UP); true }
            event.onLeftPressed() -> { getNextShowByDirection(MoveDirection.LEFT); true }
            event.onRightPressed() -> { getNextShowByDirection(MoveDirection.RIGHT); true }
            event.onEnterPressed() -> {
                lastSelectedShowView?.getShowTag()?.let { tag ->
                    val channel = channels.firstOrNull { it.tvChannelPosition.catAndChannelAccount == tag.channelId } ?: return true
                    val epgData = channel.epgList.firstOrNull { it.idByAccountData == tag.showId } ?: return true
                    programSelectionListener?.onShowClick(channel.tvChannelPosition, epgData)
                }
                true
            }
            event.onBackPressed() -> {
                lastSelectedShowView?.getShowTag()?.let { tag ->
                    val channel = channels.firstOrNull { it.tvChannelPosition.catAndChannelAccount == tag.channelId } ?: return true
                    val epgData = channel.epgList.firstOrNull { it.idByAccountData == tag.showId } ?: return true
                    if (epgData.isLiveShow) programSelectionListener?.onShowExit() else selectAndScrollToNow()
                }
                true
            }
            else -> super.dispatchKeyEvent(event)
    }

    private fun View.setShowTag(channelId: String, showId: String) {
        setTag(R.id.tag_epg_show, ShowTag(channelId, showId))
    }
    private fun View.getShowTag(): ShowTag? = getTag(R.id.tag_epg_show) as? ShowTag

    private val EpgDataOB.isLiveShow: Boolean
        get() = startTimestamp < System.currentTimeMillis() / 1000 && stopTimestamp > System.currentTimeMillis() / 1000

    fun focusEpgData() {
        val selectedChannel = channels[focusedChannelIndex]
        val currentProgramIndex = getCurrentProgram(selectedChannel.epgList)
        focusedProgramIndex = currentProgramIndex
        focusFirstProgram(focusedChannelIndex, focusedProgramIndex)
        setTimeIndicator()
    }

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

    private fun selectShow(
        channel: TvChannelWithEpg, show: EpgDataOB
    ) {
        // Vertikal zum ausgewählten Kanal scrollen
        scrollChannelToPivot(channels.indexOf(channel))
        val showTag = ShowTag(channel.tvChannelPosition.catAndChannelAccount, show.idByAccountData)

        postDelayed({
            binding.rvChannellogos.post {
                val vh = binding.rvChannellogos.findViewHolderForAdapterPosition(channels.indexOf(channel))
                        as? ProgramGuideChannelViewHolder

                if (vh == null) {
                    // Der ViewHolder ist noch nicht gebunden/wieder sichtbar. Erneut versuchen,
                    // wenn das Layout sich beruhigt hat.
                    binding.rvChannellogos.postDelayed({
                        selectShow(channel, show)
                    }, 50) // Kurze Verzögerung für Wiederholungsversuch
                    return@post
                }
                val recyclerView = vh.binding.rvChannelPrograms
                // 1. Pixelpositionen und Abmessungen berechnen
                val showStartPx = getCellWidth(
                    epgStartTime, DateTime(show.startTimestamp * 1000L)
                )
                val showEndPx = getCellWidth(
                    epgStartTime, DateTime(show.stopTimestamp * 1000L)
                )
                val showWidth = showEndPx - showStartPx

                // 2. Sichtbaren Bereich und Abmessungen ermitteln
                val visibleStartPx = currentTimeLineOffset()
                val visibleEndPx = visibleStartPx + binding.rvTimeLine.width
                val recyclerWidth = binding.rvTimeLine.width

                // 3. Ziel-Scroll-Position festlegen
                val targetScrollPosition = when {
                    // A: Sendung ist vollständig sichtbar -> nicht scrollen
                    showStartPx >= visibleStartPx && showEndPx <= visibleEndPx -> currentTimeLineOffset()

                    // B: Sendung ist am linken Rand abgeschnitten -> zum Start der Sendung scrollen
                    showStartPx < visibleStartPx -> showStartPx

                    // C: Sendung ist am rechten Rand abgeschnitten UND passt auf den Bildschirm -> mittig platzieren
                    showWidth <= recyclerWidth -> showStartPx - (recyclerWidth / 2) + (showWidth / 2)

                    // D: Sendung ist am rechten Rand abgeschnitten UND zu breit -> zum Start der Sendung scrollen
                    else -> showStartPx
                }
                // 4. Scroll-Differenz berechnen und ausführen
                val scrollBy = targetScrollPosition - visibleStartPx

                if (scrollBy != 0) {
                    scrollSyncHelper.jumpSyncTo(scrollBy)
                }

                recyclerView.post {
                    val showView =
                        recyclerView.findViewWithTag<View>(showTag)
                            ?: return@post
                    lastSelectedShowView?.isSelected = false
                    showView.setShowTag(showTag.channelId, showTag.showId)
                    lastSelectedShowView = showView
                    showView.isSelected = true
                    showView.requestFocus()
                    programSelectionListener?.onShowSelected(channel.tvChannelPosition, show)
                    Log.d("TVGUIDE_SELECT", "focused view for show ${show.idByAccountData}")
                }
            }
        }, 100)
    }

    fun selectAndScrollToNow() {

    }

    private fun scrollChannelToPivot(channelIndex: Int) {
        val layoutManager = binding.rvChannellogos.layoutManager as? LinearLayoutManager ?: return
        val rvHeight = binding.rvChannellogos.height
        val totalItems = layoutManager.itemCount

        // Hole die ViewHolder-Höhe (falls nicht verfügbar, Defaultwert)
        val vh = binding.rvChannellogos.findViewHolderForAdapterPosition(channelIndex)
        val itemHeight = vh?.itemView?.height ?: 50

        // Maximaler Offset, um keine Lücken unten zu erzeugen
        val maxOffset = rvHeight - itemHeight

        // Ideales Offset für mittige Positionierung
        val centerOffset = (rvHeight - itemHeight) / 2

        val offset = when {
            // Erster Channel → oben
            channelIndex == 0 -> 0

            // Letzter Channel → unten
            channelIndex == totalItems - 1 -> maxOffset

            // Alle anderen → mittig
            else -> centerOffset
        }

        layoutManager.scrollToPositionWithOffset(channelIndex, offset)
    }


    fun focusFirstProgram(channelIndex: Int, programIndex: Int) {
        val channelWithEpg = channels[channelIndex]
        val show = channelWithEpg.epgList[programIndex]
        selectShow( channelWithEpg, show)
        programSelectionListener?.onShowSelected(
            channelWithEpg.tvChannelPosition,
            channelWithEpg.epgList[programIndex]
        )
    }

    fun initFirstView(channelList: List<TvChannelWithEpg>) {
        initTimeLineRecycler()
        initChannelRecycler(channelList)
        initFirstTimeLine()
        this.channels = channelList
    }

    fun initFirstTimeLine() {
        val timeStepMinutes = 30
        val stepWidthPx = timeStepMinutes * ProgramGuideUtils.minuteToPixel

        // Aktuelle Zeit abrunden auf die letzte volle Stunde
        var currentTime = DateTime.now()
            .withMinuteOfHour(0)
            .withSecondOfMinute(0)
            .withMillisOfSecond(0)


        val endTime = currentTime.plusHours(12)
        val hours = mutableListOf<TimeLineData>()
        var hourIndex = 0
        ProgramGuideUtils.epgStartTime = currentTime.minusMinutes(15)
        ProgramGuideUtils.epgEndTime = endTime
        while (currentTime <= endTime) {
            val timeLabel = currentTime.toString("HH:mm")
            hours.add(
                TimeLineData(
                    timeId = currentTime,
                    time = timeLabel,
                    width = stepWidthPx,
                    gravity = Gravity.CENTER,
                    textSizeSp = if (hourIndex % 2 == 0) 13f else 11f
                )
            )
            currentTime = currentTime.plusMinutes(timeStepMinutes)
            hourIndex++
        }
        timeLineAdapter.submitList(hours)
        setTimeIndicator()
        Log.d("TEST THE STARTTIMES", "START: ${ProgramGuideUtils.epgStartTime} END: ${ProgramGuideUtils.epgEndTime}")
    }

    private fun initChannelRecycler(channelList: List<TvChannelWithEpg>) {
        binding.rvChannellogos.apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            adapter = channelsAdapter
            channelsAdapter.submitList(channelList)
        }
    }

    private fun initTimeLineRecycler() {
        binding.rvTimeLine.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = timeLineAdapter
            setHasFixedSize(true)
            itemAnimator = null
            scrollSyncHelper.register(this)
            binding.rvTimeLine.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    updateTimeIndicator(recyclerView.computeHorizontalScrollOffset())
                }
            })
        }
    }

    private var timeIndicatorBaseX = 0f

    fun setTimeIndicator() {
        val now = DateTime.now()
        val startTime = ProgramGuideUtils.epgStartTime

        // Minuten seit Start
        val diffMinutes = Minutes.minutesBetween(startTime, now).minutes

        // Pixelposition relativ zur gesamten EPG-Zeitleiste
        val xPos = diffMinutes * ProgramGuideUtils.minuteToPixel

        // Speichern für spätere Berechnungen
        timeIndicatorBaseX = xPos.toFloat()

        // Erste Position (ScrollOffset = 0)
        binding.timeIndicator.translationX = timeIndicatorBaseX
        binding.timeIndicator.visibility = View.VISIBLE
        Log.d("TIME_INDICATOR", "init → now=$now, start=$startTime, diffMin=$diffMinutes, baseX=$xPos px")
    }

    fun updateTimeIndicator(scrollOffSet: Int) {
        val newX = timeIndicatorBaseX - scrollOffSet
        binding.timeIndicator.translationX = newX
        val rvWidth = binding.rvTimeLine.width
        binding.timeIndicator.visibility = if (newX in 0f..rvWidth.toFloat()) View.VISIBLE else View.GONE
        binding.timeIndicatorBubble.visibility = binding.timeIndicator.visibility
        Log.d("TIME_INDICATOR", "scroll → totalScrollX=$scrollOffSet, baseX=$timeIndicatorBaseX, newX=$newX")
    }


    fun selectCurrentShow(channelId: String?) {
        val channel = channels.singleOrNull { it.tvChannelPosition.catAndChannelAccount == channelId } ?: channels.firstOrNull() ?: return
        val channelIndex = channels.indexOf(channel)
        scrollChannelToPivot(channelIndex)
        val currentShow = if (dayShift == 0) channel.epgList.getCurrentShow() ?: channel.epgList.firstOrNull() else channel.epgList.firstOrNull()
        currentShow?.let { selectShow(channel, it) }
    }
    fun getCurrentProgram(epgList: List<EpgDataOB>): Int {
        val currentTimeInSeconds = System.currentTimeMillis() / 1000
        return epgList.indexOfFirst { it.startTimestamp <= currentTimeInSeconds && it.stopTimestamp >= currentTimeInSeconds }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        binding.root.viewTreeObserver.addOnGlobalFocusChangeListener { oldFocus, newFocus ->
            Log.w(
                "FOCUS_GLOBAL",
                "Focus changed: ${oldFocus?.javaClass?.simpleName ?: "null"} → ${newFocus?.javaClass?.simpleName ?: "null"}"
            )
        }

        Log.d("FOCUS_GLOBAL", "GlobalFocusChangeListener attached")
    }

    private fun List<EpgDataOB>.getCurrentShow(): EpgDataOB? {
        val currentTime = System.currentTimeMillis() / 1000
        return this.find { it.startTimestamp < currentTime && it.stopTimestamp > currentTime }
    }
}
