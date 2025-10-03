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
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.ProgramguideRecyclerviewBinding
import com.example.mj_player_tv.ui.tvguide.onBackPressed
import com.example.mj_player_tv.ui.tvguide.onDownPressed
import com.example.mj_player_tv.ui.tvguide.onEnterPressed
import com.example.mj_player_tv.ui.tvguide.onLeftPressed
import com.example.mj_player_tv.ui.tvguide.onRightPressed
import com.example.mj_player_tv.ui.tvguide.onUpPressed
import com.example.mj_player_tv.utils.EpgScrollSyncManager
import org.joda.time.DateTime
import org.joda.time.Minutes

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
    interface ProgramSelectionListener {
        fun onProgramSelected(channel: ChannelPositions, program: EpgDataOB)
    }

    var programSelectionListener: ProgramSelectionListener? = null

    // Fokus-Tracking
    private var focusedChannelIndex = 0
    private var focusedProgramIndex = 0

    private var lastSelectedEpgView: View? = null


    enum class MoveDirection { UP, DOWN, LEFT, RIGHT }

    interface OnEventListener {
        fun onShowSelected(channelPosition: ChannelPositions, epgData: EpgDataOB)
        fun onShowClick(channelPosition: ChannelPositions, epgData: EpgDataOB)
        fun onShowExit()
    }
    // Manager (Channels + Schedules)

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.dispatchKeyEvent(event)
        return when {
            event.onDownPressed() -> { moveFocusVertical(MoveDirection.DOWN); true }
            event.onUpPressed() -> { moveFocusVertical(MoveDirection.UP); true }
            event.onLeftPressed() -> { moveFocusHorizontal(MoveDirection.LEFT); true }
            event.onRightPressed() -> { moveFocusHorizontal(MoveDirection.RIGHT); true }
            event.onEnterPressed() -> true
            event.onBackPressed() -> true
            else -> super.dispatchKeyEvent(event)
        }
    }

    fun focusEpgData() {
        val selectedChannel = channels[focusedChannelIndex]
        val currentProgramIndex = getCurrentProgram(selectedChannel.epgList)
        focusedProgramIndex = currentProgramIndex
        focusFirstProgram(focusedChannelIndex, focusedProgramIndex)
        setTimeIndicator()

    }

    private fun moveFocusVertical(direction: MoveDirection) {
        val newChannelIndex = when(direction) {
            MoveDirection.UP -> focusedChannelIndex - 1
            MoveDirection.DOWN -> focusedChannelIndex + 1
            else -> focusedChannelIndex
        }

        if (newChannelIndex !in channels.indices) return

        val oldChannelPrograms = channels[focusedChannelIndex].epgList
        val oldProgram = oldChannelPrograms.getOrNull(focusedProgramIndex)

        val newChannelPrograms = channels[newChannelIndex].epgList

        // Prüfen: war alte Sendung aktuell?
        val now = System.currentTimeMillis() / 1000
        val oldIsLive =
            (oldProgram?.startTimestamp ?: 0) <= now && now < (oldProgram?.stopTimestamp ?: 0)

        // ProgramIndex im neuen Channel bestimmen
        focusedProgramIndex = if (oldIsLive) {
            newChannelPrograms.indexOfFirst { it.startTimestamp <= now && now < it.stopTimestamp }
                .takeIf { it >= 0 } ?: 0
        } else {
            oldProgram?.let { old ->
                newChannelPrograms.indexOfFirst { it.startTimestamp >= old.startTimestamp }.takeIf { it >= 0 } ?: 0
            } ?: 1
        }

        focusedChannelIndex = newChannelIndex
        scrollChannelToPivot(focusedChannelIndex)
        focusProgramHorizontalAt(focusedChannelIndex, focusedProgramIndex)

    }

    private fun scrollChannelToPivot(channelIndex: Int) {
        val lm = binding.rvChannels.layoutManager as? LinearLayoutManager ?: return
        val rvHeight = height
        val totalChannels = channels.size

        // Höhe eines Channels schätzen (oder fix nehmen)
        val childHeight = getChildAt(channelIndex)?.height ?: 100 // z.B. 100px fallback

        // Ziel: Fokus in der Mitte
        var desiredOffset = rvHeight / 2 - childHeight / 2

        // Sonderfälle: erster/letzter Channel
        if (channelIndex == 0) desiredOffset = 0
        if (channelIndex == totalChannels - 1) desiredOffset = rvHeight - childHeight

        lm.scrollToPositionWithOffset(channelIndex, desiredOffset)
    }


    private fun moveFocusHorizontal(direction: MoveDirection) {
        val programs = channels[focusedChannelIndex].epgList
        val newProgramIndex = when(direction) {
            MoveDirection.LEFT -> focusedProgramIndex - 1
            MoveDirection.RIGHT -> focusedProgramIndex + 1
            else -> focusedProgramIndex
        }

        if (newProgramIndex in programs.indices) {
            focusedProgramIndex = newProgramIndex
            focusProgramHorizontalAt(focusedChannelIndex, focusedProgramIndex)
        }
    }

    // In der ProgramGuideRecyclerView-Klasse
// Fügen Sie diese Eigenschaft hinzu, um das zuletzt fokussierte Element zu verfolgen

    private fun focusProgramHorizontalAt(channelIndex: Int, programIndex: Int) {
        val vh = binding.rvChannels.findViewHolderForAdapterPosition(channelIndex) as? ProgramGuideChannelViewHolder ?: return
        val rv = vh.binding.rvChannelPrograms
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return

        val child = layoutManager.findViewByPosition(programIndex)
        if (child == null) {
            layoutManager.scrollToPositionWithOffset(programIndex, 0)
            rv.post { focusProgramHorizontalAt(channelIndex, programIndex) }
            return
        }

        // Position des Items relativ zur Timeline
        val childStart = child.left
        val childCenter = (child.left + child.right) / 2
        val rvCenter = rv.width / 2
        val wert = childCenter - rvCenter
        val totalscroll = scrollSyncHelper.getTotalScrollX()
        Log.d("CHANGE_FOCUS", "WERT: $wert und TOTAL: $totalscroll")
        // Absoluter globaler Offset, den jumpSyncTo erwartet
        val targetGlobalOffset = scrollSyncHelper.getTotalScrollX() + (childCenter - rvCenter)
        Log.d("CHANGE_FOCUS", "TARGETGLOBAL: $targetGlobalOffset")

        scrollSyncHelper.jumpSyncTo(targetGlobalOffset)

        child.requestFocus()

        val channelWithEpg = channels[channelIndex]
        programSelectionListener?.onProgramSelected(
            channelWithEpg.tvChannelPosition,
            channelWithEpg.epgList[programIndex]
        )
    }

    fun focusFirstProgram(channelIndex: Int, programIndex: Int) {
        val vh = binding.rvChannels.findViewHolderForAdapterPosition(channelIndex) as? ProgramGuideChannelViewHolder ?: return
        val rv = vh.binding.rvChannelPrograms
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return

        val child = layoutManager.findViewByPosition(programIndex)
        if (child == null) {
            return
        } else {
            child.requestFocus()
            val channelWithEpg = channels[channelIndex]
            programSelectionListener?.onProgramSelected(
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
        binding.rvChannels.apply {
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



    fun getCurrentProgram(epgList: List<EpgDataOB>): Int {
        val currentTimeInSeconds = System.currentTimeMillis() / 1000
        return epgList.indexOfFirst { it.startTimestamp <= currentTimeInSeconds && it.stopTimestamp >= currentTimeInSeconds }
    }
}
