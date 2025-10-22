package com.example.mj_player_tv.ui.epg

import android.content.Context
import android.util.AttributeSet
import kotlin.math.abs

open class EpgTimeLineRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : CustomEpgTimeLineGridView(context, attrs, defStyle) {

    private var scrollPosition: Int = 0
    /** Returns the current scroll position  */
    val currentScrollOffset: Int
        get() = abs(scrollPosition)

    fun resetScroll() {
        layoutManager?.scrollToPosition(0)
        scrollPosition = 0
    }

    /** Scrolls horizontally to the given position.  */
    fun scrollTo(scrollOffset: Int, smoothScroll: Boolean) {
        val dx = scrollOffset - currentScrollOffset
        if (smoothScroll) {
            if (layoutDirection == LAYOUT_DIRECTION_LTR) {
                smoothScrollBy(dx, 0)
            } else {
                smoothScrollBy(-dx, 0)
            }
        } else {
            if (layoutDirection == LAYOUT_DIRECTION_LTR) {
                scrollBy(dx, 0)
            } else {
                scrollBy(-dx, 0)
            }
        }
    }


    override fun onScrolled(dx: Int, dy: Int) {
        scrollPosition += dx
    }

}