package com.example.mj_player_tv.ui.epg

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.leanback.widget.VerticalGridView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.ui.epg.util.EpgUtil
import kotlin.math.abs

class CustomEpgVerticalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VerticalGridView(context, attrs, defStyleAttr) {

    companion object {
        private const val INVALID_INDEX = -1
    }
    interface ChildFocusListener {
        /**
         * Is called before focus is moved. Only children to `ProgramGrid` will be passed. See
         * `ProgramGuideGridView#setChildFocusListener(ChildFocusListener)`.
         */
        fun onRequestChildFocus(oldFocus: View?, newFocus: View?)
    }

    private var selectedProgramView: EpgProgramItemView? = null

    private val programManagerListener = object : EpgManager.Listener {

        override fun onSchedulesUpdated() {
            // Do nothing
        }

        override fun onTimeRangeUpdated() {
        }
    }
    interface ScheduleSelectionListener {
        // Can be null if nothing is selected
        fun onSelectionChanged(schedule: EpgDataOB)
        fun onChannelSelected(channel: ChannelPositions)
        fun onChannelClicked(channel: ChannelPositions)
    }

    init {
        setItemViewCacheSize(0)
    }

    var tvChannelsWithEpg: List<TvChannelWithEpg>? = null

    private lateinit var programGuideManager: EpgManager

    var childFocusListener: ChildFocusListener? = null
    var scheduleSelectionListener: ScheduleSelectionListener? = null

    fun initialize(epgManager: EpgManager) {
        programGuideManager = epgManager
        programGuideManager.listeners.add(programManagerListener)
    }

    private val globalFocusChangeListener =
        ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus is EpgProgramItemView) {
                Toast.makeText(this.context, "${newFocus.programData?.name}", Toast.LENGTH_SHORT).show()
            }

        }

    private fun getNextRowProgram(direction: Int): View? {
        val focusedProgram = findFocus() as? EpgProgramItemView ?: return null
        val horizontalRow = focusedProgram.parent as? CustomEpgHorizontalGridView ?: return null

        // Finde das Layout, das das horizontale RecyclerView enthält
        val parentRow = horizontalRow.parent as? View ?: return null

        val rowIndex = (0 until childCount).firstOrNull { getChildAt(it) == parentRow } ?: return null
        val nextIndex = rowIndex + if (direction == FOCUS_DOWN) 1 else -1
        if (nextIndex !in 0 until childCount) return null

        val nextRowLayout = getChildAt(nextIndex) ?: return null
        val nextHorizontalRow = nextRowLayout.findViewById<CustomEpgHorizontalGridView>(R.id.rv_channel_programs)
            ?: return null

        // ⬇️ Nur Programme im sichtbaren Bereich durchsuchen
        return EpgUtil.findVisibleMatchingProgramView(
            focusedProgram.programData ?: return null,
            nextHorizontalRow
        )
    }


    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN) {
            val direction = when (event.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> FOCUS_UP
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> FOCUS_DOWN
                else -> return super.dispatchKeyEvent(event)
            }

            val nextProgram = getNextRowProgram(direction)
            if (nextProgram != null) {
                nextProgram.post {
                    if (nextProgram.isShown) { // ✅ Nur sichtbare Views
                        nextProgram.requestFocus()
                    } else {
                        Log.w("EPG_FOCUS", "Überspringe unsichtbaren Fokus – ${nextProgram}")
                    }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusChangeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnGlobalFocusChangeListener(globalFocusChangeListener)
        programGuideManager.listeners.remove(programManagerListener)
    }
}
