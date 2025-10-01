package com.example.mj_player_tv.ui.tvguidetest

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
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
import dev.androidbroadcast.vbpd.viewBinding

class ProgramGuideRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {

    private val binding by viewBinding(ProgramguideRecyclerviewBinding::bind)
    // Interfaces für Fokus & Selektion
    var manager = ProgramGuideManager()
    private var channels: List<TvChannelWithEpg> = emptyList()

    private val channelsAdapter = ProgramGuideChannelAdapter(manager)
    interface ProgramSelectionListener {
        fun onProgramSelected(channel: ChannelPositions, program: EpgDataOB)
    }

    var programSelectionListener: ProgramSelectionListener? = null

    // Fokus-Tracking
    private var focusedChannelIndex = 0
    private var focusedProgramIndex = 0

    // Feature Flags
    var featureFocusWrapAround = true
    var featureKeepCurrentProgramFocused = true

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

    private fun moveFocusVertical(direction: MoveDirection) {
        val newChannelIndex = when(direction) {
            MoveDirection.UP -> focusedChannelIndex - 1
            MoveDirection.DOWN -> focusedChannelIndex + 1
            else -> focusedChannelIndex
        }

        if (newChannelIndex !in channels.indices) return

        focusedChannelIndex = newChannelIndex

        // Berechne neuen Program-Index
        val oldProgram = channels[focusedChannelIndex].epgList.getOrNull(focusedProgramIndex)
        val newPrograms = channels[focusedChannelIndex].epgList
        focusedProgramIndex = oldProgram?.let { old ->
            newPrograms.indexOfFirst { it.startTimestamp <= System.currentTimeMillis() && System.currentTimeMillis() < it.stopTimestamp }
                .takeIf { it >= 0 } ?: newPrograms.indexOfFirst { it.startTimestamp >= old.startTimestamp }.takeIf { it >= 0 } ?: 0
        } ?: 0

        focusProgramAt(focusedChannelIndex, focusedProgramIndex)
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
            focusProgramAt(focusedChannelIndex, focusedProgramIndex)
        }
    }


    private fun focusProgramAt(channelIndex: Int, programIndex: Int) {
        val vh = findViewHolderForAdapterPosition(channelIndex) as? ProgramGuideChannelViewHolder ?: return
        vh.binding.rvChannelPrograms.post {
            (vh.binding.rvChannelPrograms.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(programIndex, 0)
            val child = vh.binding.rvChannelPrograms.layoutManager?.findViewByPosition(programIndex)
            child?.requestFocus()
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
        this.channels = channelList
    }

    private fun initTimeLineRecycler() {

    }

    private fun initChannelRecycler(channelList: List<TvChannelWithEpg>) {
        layoutManager = LinearLayoutManager(context, VERTICAL, false)
        adapter = channelsAdapter
        channelsAdapter.submitList(channelList)
    }
}
