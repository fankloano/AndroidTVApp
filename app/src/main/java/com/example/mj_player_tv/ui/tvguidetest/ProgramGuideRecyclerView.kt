package com.example.mj_player_tv.ui.tvguidetest

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.widget.RelativeLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.ProgramguideRecyclerviewBinding
import com.example.mj_player_tv.databinding.TvguideRecyclerviewBinding
import com.example.mj_player_tv.databinding.TvguideRecyclerviewBinding.bind
import com.example.mj_player_tv.ui.tvguide.adapters.TvGuideChannelListAdapter
import com.example.mj_player_tv.ui.tvguide.adapters.TvGuideChannelLogosAdapter
import com.example.mj_player_tv.ui.tvguide.onBackPressed
import com.example.mj_player_tv.ui.tvguide.onDownPressed
import com.example.mj_player_tv.ui.tvguide.onEnterPressed
import com.example.mj_player_tv.ui.tvguide.onLeftPressed
import com.example.mj_player_tv.ui.tvguide.onRightPressed
import com.example.mj_player_tv.ui.tvguide.onUpPressed
import com.example.mj_player_tv.utils.EpgScrollSyncManager
import dev.androidbroadcast.vbpd.viewBinding

class ProgramGuideRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {

    private val binding by viewBinding(ProgramguideRecyclerviewBinding::bind)
    // Interfaces für Fokus & Selektion
    var manager = ProgramGuideManager()

    val scrollSyncHelper = EpgScrollSyncManager()
    private var channels: List<TvChannelWithEpg> = emptyList()

    private val channelsAdapter = ProgramGuideChannelAdapter(manager, scrollSyncHelper)
    interface ProgramSelectionListener {
        fun onProgramSelected(channel: ChannelPositions, program: EpgDataOB)
    }

    var programSelectionListener: ProgramSelectionListener? = null

    // Fokus-Tracking
    private var focusedChannelIndex = 0
    private var focusedProgramIndex = 0

    private var lastSelectedEpgView: View? = null

    var lastHorizontalOffset = 0

    enum class MoveDirection { UP, DOWN, LEFT, RIGHT }
    // Manager (Channels + Schedules)

    init {
        layoutManager = LinearLayoutManager(context, VERTICAL, false)
        isFocusable = true
        isFocusableInTouchMode = true

        // GlobalFocusListener um Fokusänderungen zu tracken
        viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus != null) {
                // Hier könntest du aktuelle Program-/Channel-Position ermitteln
            }
        }
    }

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
        focusProgramHorizontalAt(focusedChannelIndex, focusedProgramIndex)
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
            } ?: 0
        }

        focusedChannelIndex = newChannelIndex
        scrollChannelToPivot(focusedChannelIndex)
        focusProgramHorizontalAt(focusedChannelIndex, focusedProgramIndex)

    }

    private fun scrollChannelToPivot(channelIndex: Int) {
        val lm = layoutManager as? LinearLayoutManager ?: return
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
        val vh = findViewHolderForAdapterPosition(channelIndex) as? ProgramGuideChannelViewHolder ?: return
        val rv = vh.binding.rvChannelPrograms
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return

        val child = layoutManager.findViewByPosition(programIndex)
        if (child == null) {
            // Falls Item noch nicht sichtbar ist → Position per LayoutManager setzen
            layoutManager.scrollToPositionWithOffset(programIndex, 0)
            return
        }

        // Pixel-Berechnung: wir wollen das Item mittig im RV haben
        val parentCenter = rv.width / 2
        val childCenter = (child.left + child.right) / 2
        val dx = childCenter - parentCenter

        // Jetzt: synchroner Scroll
        scrollSyncHelper.jumpSyncTo(scrollSyncHelper.getTotalScrollX() + dx)

        child.requestFocus()

        val channelWithEpg = channels[channelIndex]
        programSelectionListener?.onProgramSelected(
            channelWithEpg.tvChannelPosition,
            channelWithEpg.epgList[programIndex]
        )
    }


    fun initFirstView(channelList: List<TvChannelWithEpg>) {
        initTimeLineRecycler()
        initChannelRecycler(channelList)
        this.channels = channelList
    }

    private fun initTimeLineRecycler() {

    }

    private fun initChannelRecycler(channelList: List<TvChannelWithEpg>) {
        layoutManager = LinearLayoutManager(context, VERTICAL, false)
        adapter = channelsAdapter
        channelsAdapter.submitList(channelList)
    }

    fun getCurrentProgram(epgList: List<EpgDataOB>): Int {
        val currentTimeInSeconds = System.currentTimeMillis() / 1000
        return epgList.indexOfFirst { it.startTimestamp <= currentTimeInSeconds && it.stopTimestamp >= currentTimeInSeconds }
    }
}
