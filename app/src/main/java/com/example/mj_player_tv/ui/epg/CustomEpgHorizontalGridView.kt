package com.example.mj_player_tv.ui.epg

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.ui.epg.util.EpgUtil
import org.joda.time.DateTime
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class CustomEpgHorizontalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CustomEpgTimeLineGridView(context, attrs, defStyleAttr) {


    companion object {
        private val ONE_HOUR_MILLIS = TimeUnit.HOURS.toMillis(1)
        private val HALF_HOUR_MILLIS = ONE_HOUR_MILLIS / 2
    }

    var thischannel: TvChannelWithEpg? = null

    private lateinit var epgManager: EpgManager
    fun setChannel(channelToSet: TvChannelWithEpg) {
        thischannel = channelToSet
    }

    fun setEpgManager(thisEpgManager: EpgManager) {
        epgManager = thisEpgManager
    }

    // Call this API after RTL is resolved. (i.e. View is measured.)
    private fun isDirectionStart(direction: Int): Boolean {
        return if (layoutDirection == LAYOUT_DIRECTION_LTR)
            direction == FOCUS_LEFT
        else
            direction == FOCUS_RIGHT
    }

    // Call this API after RTL is resolved. (i.e. View is measured.)
    private fun isDirectionEnd(direction: Int): Boolean {
        return if (layoutDirection == LAYOUT_DIRECTION_LTR)
            direction == FOCUS_RIGHT
        else
            direction == FOCUS_LEFT
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        val focusedProgram = (focused as? EpgProgramItemView)?.programData
            ?: return super.focusSearch(focused, direction)

        val fromMillis = epgManager.getVisibleTimeStart()
        val toMillis = epgManager.getVisibleTimeEnd() // musst du evtl. noch ergänzen

        // Nach links scrollen, wenn Programm vor sichtbarem Bereich beginnt
        if (isDirectionStart(direction) || direction == FOCUS_BACKWARD) {
            if (focusedProgram.startTimestamp * 1000 < fromMillis) {
                Log.d("SCROLLE HORIZONTAL FOKUS SEARCH","BEGINNT VOR: ${focusedProgram.name}")

                scrollByTime(
                    max(-ONE_HOUR_MILLIS, focusedProgram.startTimestamp * 1000 - fromMillis)
                )
                return focused
            }
            Log.d("SCROLLE HORIZONTAL FOKUS SEARCH","BEGINNT NICHT VOR: ${focusedProgram.name}")
        }

        // Nach rechts scrollen, wenn Programm über sichtbaren Bereich hinausgeht
        if (isDirectionEnd(direction) || direction == FOCUS_FORWARD) {
            if (focusedProgram.stopTimestamp * 1000 > toMillis) {
                scrollByTime(
                    ONE_HOUR_MILLIS
                )
                return focused
            }
        }

        val target = super.focusSearch(focused, direction)
        if (target !is EpgProgramItemView) {
            Log.d("SCROLLE HORIZONTAL FOKUS SEARCH","NEUES ist kein Programm")

            return target
        }
        if (target.programData != null && target.programData!!.startTimestamp * 1000 < fromMillis) {
            val timeToScroll = (target.programData!!.startTimestamp * 1000 - fromMillis)
            Log.d("SCROLLEN MUSST DU", "SCROLL UM $timeToScroll")
            scrollByTime(timeToScroll)
        }

        val targetProgram = target.programData ?: return target

        // Zielprogramm liegt außerhalb: scrollen
        if (isDirectionStart(direction) || direction == FOCUS_BACKWARD) {
            if (targetProgram.startTimestamp * 1000 < fromMillis && targetProgram.stopTimestamp * 1000 < fromMillis + HALF_HOUR_MILLIS) {
                Log.d("SCROLLE HORIZONTAL FOKUS SEARCH","NEUES BEGINNT VOR: ${targetProgram.name}")

                scrollByTime(
                    max(-ONE_HOUR_MILLIS, targetProgram.startTimestamp * 1000 - fromMillis)
                )
            }
            Log.d("SCROLLE HORIZONTAL FOKUS SEARCH","NEUES BEGINNT NICHT VOR: ${targetProgram.name}")

        } else if (isDirectionEnd(direction) || direction == FOCUS_FORWARD) {
            if (targetProgram.startTimestamp * 1000 > toMillis + ONE_HOUR_MILLIS + HALF_HOUR_MILLIS) {
                scrollByTime(
                    min(TimeUnit.HOURS.toMillis(1), targetProgram.startTimestamp * 1000 - fromMillis)
                )
            }
        }

        return target
    }


    private fun scrollByTime(timeToScroll: Long) {
            epgManager.shiftTime(timeToScroll)
    }

    /** Resets the scroll with the initial offset `currentScrollOffset`.  */
    fun resetScroll(scrollOffset: Int) {
        val channel = thischannel
        val startTime =
            EpgUtil.convertPixelToMillis(scrollOffset) + epgManager.getTimeLineStart()
        val visiblestart = DateTime(epgManager.getVisibleTimeStart()).toString("HH:mm")
        val sschtarttime = DateTime(startTime).toString("HH:mm")
        val position = if (channel == null) {
            -1
        } else {
            epgManager.getProgramIndexAtTime(channel.id, startTime)
        }
        if (position < 0) {
            Log.d(
                "NEU GEBUNDENE ZEEEILE",
                "${channel?.tvChannelPosition?.tvchannel?.target?.showingName} = position -1"
            )
            layoutManager?.scrollToPosition(0)
        } else if (channel?.id != null) {
            val slug = channel.id
            val entry = epgManager.getScheduleForChannelIdAndIndex(slug, position)
            if (entry != null) {
                if (entry.startTimestamp * 1000 == epgManager.getTimeLineStart()) {
                    Log.d(
                        "NEU GEBUNDENE ZEEEILE",
                        "${channel.tvChannelPosition.tvchannel.target?.showingName} =RETURN "
                    )
                    return
                } else {
                    val offset = EpgUtil.convertMillisToPixel(
                        epgManager.getVisibleTimeStart(),
                        entry.startTimestamp * 1000
                    ) - scrollOffset
                    Log.d(
                        "NEU GEBUNDENE ZEEEILE",
                        "${channel.tvChannelPosition.tvchannel.target.showingName} = ${entry.name} SCROOOL: $offset EIGENTLICH: $scrollOffset STARTTIME: ${sschtarttime} VISIBLESTART: $visiblestart"
                    )

                    (layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                        position,
                        offset
                    )
                }
            } else {
                Log.d(
                    "NEU GEBUNDENE ZEEEILE",
                    "${channel.tvChannelPosition.tvchannel.target?.showingName} = kein entry"
                )
                layoutManager?.scrollToPosition(0)
            }
        }
    }
    fun View.getVisibleWidthInParent(parent: RecyclerView): Int {
        val parentRect = Rect()
        parent.getHitRect(parentRect)
        val visibleRect = Rect()
        this.getLocalVisibleRect(visibleRect)
        return visibleRect.width().coerceAtMost(this.width)
    }

    fun View.isFullyVisible(parent: RecyclerView): Boolean =
        getVisibleWidthInParent(parent) == this.width

    fun View.isPartiallyVisible(parent: RecyclerView): Boolean =
        getVisibleWidthInParent(parent) in 1 until this.width

}
