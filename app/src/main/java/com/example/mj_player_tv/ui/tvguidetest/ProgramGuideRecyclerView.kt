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
        selectShow(channels.indexOf(channel), channel, channel.epgList.indexOf(desiredShow), desiredShow)
        return lastSelectedShowView
    }

    private fun selectShow(channelIndex: Int, channel: TvChannelWithEpg, programIndex: Int, show: EpgDataOB) {
        scrollChannelToPivot(channelIndex)

        postDelayed({
            // Hol den ViewHolder für den Channel
            val vh = binding.rvChannellogos.findViewHolderForAdapterPosition(channelIndex)
                    as? ProgramGuideChannelViewHolder ?: return@postDelayed

            // Das horizontale RecyclerView im ViewHolder
            val channelRecycler = vh.binding.rvChannelPrograms

            val lm = channelRecycler.layoutManager ?: return@postDelayed
            val child = lm.findViewByPosition(programIndex) ?: return@postDelayed

            // Zentriere das Item horizontal
            val childCenter = (child.left + child.right) / 2
            val rvCenter = channelRecycler.width / 2
            val targetGlobalOffset = scrollSyncHelper.getTotalScrollX() + (childCenter - rvCenter)
            scrollSyncHelper.jumpSyncTo(targetGlobalOffset)

            // Fokus setzen
            channelRecycler.post {
                lastSelectedShowView?.isSelected = false
                lastSelectedShowView = child
                child.isSelected = true
                child.requestFocus()
                programSelectionListener?.onShowSelected(channel.tvChannelPosition, show)
            }

        }, 100)
    }


    fun selectAndScrollToNow() {

    }

    private fun scrollChannelToPivot(channelIndex: Int) {
        val lm = binding.rvChannellogos.layoutManager as? LinearLayoutManager ?: return
        val rvHeight = height
        val totalChannels = channels.size

        // Höhe eines Channels schätzen (oder fix nehmen)
        val childHeight = getChildAt(channelIndex)?.height ?: 100 // z.B. 100px fallback

        // Ziel: Fokus in der Mitte
        var desiredOffset = rvHeight / 2 - childHeight / 2
        val tschennel = channels[channelIndex].tvChannelPosition.tvchannel.target.showingName
        // Sonderfälle: erster/letzter Channel
        if (channelIndex == 0) desiredOffset = 0
        if (channelIndex == totalChannels - 1) desiredOffset = rvHeight - childHeight
        Log.d("SCROLL VERTICALLY", "SCROLL PIVOT: $tschennel")

        lm.scrollToPositionWithOffset(channelIndex, desiredOffset)
    }

    fun focusFirstProgram(channelIndex: Int, programIndex: Int) {
        val vh = binding.rvChannellogos.findViewHolderForAdapterPosition(channelIndex) as? ProgramGuideChannelViewHolder ?: return
        val rv = vh.binding.rvChannelPrograms
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return

        val child = layoutManager.findViewByPosition(programIndex)
        if (child == null) {
            return
        } else {
            val channelWithEpg = channels[channelIndex]
            val show = channelWithEpg.epgList[programIndex]
            selectShow(channelIndex, channelWithEpg, programIndex, show)
            programSelectionListener?.onShowSelected(
                channelWithEpg.tvChannelPosition,
                channelWithEpg.epgList[programIndex]
            )
        }
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
                    timeId = timeLabel,
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
        currentShow?.let { selectShow(channelIndex, channel, channel.epgList.indexOf(currentShow), it) }
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
