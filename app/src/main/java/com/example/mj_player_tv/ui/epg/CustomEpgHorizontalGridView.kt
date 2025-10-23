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
import java.util.concurrent.TimeUnit
import kotlin.math.max

class CustomEpgHorizontalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CustomEpgTimeLineGridView(context, attrs, defStyleAttr) {

    var thischannel: TvChannelWithEpg? = null

    private lateinit var epgManager: EpgManager
    fun setChannel(channelToSet: TvChannelWithEpg) {
        thischannel = channelToSet
    }

    fun setEpgManager(thisEpgManager: EpgManager) {
        epgManager = thisEpgManager
    }

    fun focusNextProgram(forward: Boolean) {
        val layoutManager = layoutManager as LinearLayoutManager
        val currentView = findFocus() as? EpgProgramItemView ?: return
        val programs = thischannel?.epgList ?: return

        val currentIndex = programs.indexOf(currentView.programData)
        if (currentIndex == -1) return

        val nextIndexRaw = currentIndex + if (forward) 1 else -1
        if (nextIndexRaw !in 0..programs.lastIndex) return
        val nextIndex = nextIndexRaw

        val nextProgram = programs[nextIndex]

        // Prüfen, ob das nächste Program sichtbar ist
        val nextView = (0 until layoutManager.childCount)
            .map { layoutManager.getChildAt(it) as? EpgProgramItemView }
            .firstOrNull { it?.programData == nextProgram }

        if (nextView != null && nextView.isFullyVisible(this)) {
            nextView.requestFocus()
        } else {
            // Berechne Scroll-Offset in Millis → Pixel
            val timeToScroll = nextProgram.startTimestamp * 1000 - epgManager.getVisibleTimeStart()
            val maxScroll = TimeUnit.MINUTES.toMillis(60) // max 30 Minuten pro KeyEvent
            val scrollMillis = timeToScroll.coerceIn(-maxScroll, maxScroll)

            Log.d("SCROLLE HORIZONTAL","NICHT SICHTBAR: $scrollMillis $currentIndex $nextIndex")
            scrollByTime(scrollMillis)

            post {
                nextView?.requestFocus()
            }
        }
    }

    private fun scrollByTime(timeToScroll: Long) {
            epgManager.shiftTime(timeToScroll)
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

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                focusNextProgram(forward = true)
                return true
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                focusNextProgram(forward = false)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
