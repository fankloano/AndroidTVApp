package com.example.mj_player_tv.ui.epg

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.recyclerview.widget.RecyclerView

class CustomEpgHorizontalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalGridView(context, attrs, defStyleAttr) {

    var synchronizer: EpgScrollSynchronizer? = null

    init {
        windowAlignment = WINDOW_ALIGN_NO_EDGE
        windowAlignmentOffset = 0
        windowAlignmentOffsetPercent = WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
        itemAlignmentOffsetPercent = 0f
    }

    override fun onScrolled(dx: Int, dy: Int) {
        Log.d("HORIZONTAL SCROLL", "${this.hashCode()} = SCROLL = $dx")
        super.onScrolled(dx, dy)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        event ?: return super.dispatchKeyEvent(null)

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (!isScrollEnabled) isScrollEnabled = true
                true
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (!isScrollEnabled) isScrollEnabled = true
            } else {
                false
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
