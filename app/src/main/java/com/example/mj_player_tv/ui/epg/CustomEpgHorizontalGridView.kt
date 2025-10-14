package com.example.mj_player_tv.ui.epg

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.Interpolator
import androidx.leanback.widget.HorizontalGridView
import androidx.recyclerview.widget.RecyclerView

class CustomEpgHorizontalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalGridView(context, attrs, defStyleAttr) {

    private var suppressNextAutoScroll = false

    init {
        addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val offset = computeHorizontalScrollOffset()
                Log.d("CustomHGV-${hashCode()}", "onScrolled dx=$dx offset=$offset focused=${findFocus()?.tag}")
            }
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                val stateName = when (newState) {
                    SCROLL_STATE_IDLE -> "IDLE"
                    SCROLL_STATE_DRAGGING -> "DRAGGING"
                    SCROLL_STATE_SETTLING -> "SETTLING"
                    else -> "UNKNOWN($newState)"
                }
                Log.d("CustomHGV-${hashCode()}", "Scroll state changed: $stateName")
            }
        })
    }

    fun suppressAutoScrollForNextFocus() {
        suppressNextAutoScroll = true
        // Keep a log so you know when it's requested
        Log.d("CustomHGV-${hashCode()}", ">> suppressAutoScrollForNextFocus() gesetzt")
        // Immediately stop any running scroll to avoid leftover animation
        stopScroll()
    }

    // Prevent automatic rectangle scrolling (called by framework to keep child visible)
    override fun requestChildRectangleOnScreen(child: View, rect: android.graphics.Rect, immediate: Boolean): Boolean {
        if (suppressNextAutoScroll) {
            Log.d("CustomHGV-${hashCode()}", "requestChildRectangleOnScreen suppressed (immediate=$immediate)")
            // Do NOT clear flag here — consume it later in requestChildFocus after focus is actually set
            return false
        }
        return super.requestChildRectangleOnScreen(child, rect, immediate)
    }

    // Prevent "immediate" scrollBy calls
    override fun scrollBy(x: Int, y: Int) {
        if (suppressNextAutoScroll) {
            Log.d("CustomHGV-${hashCode()}", "scrollBy suppressed x=$x y=$y")
            // do NOT clear flag here
            return
        }
        super.scrollBy(x, y)
    }

    // Prevent animated smoothScrollBy (many internal calls use the interpolator overload)
    override fun smoothScrollBy(dx: Int, dy: Int, interpolator: android.view.animation.Interpolator?) {
        if (suppressNextAutoScroll) {
            Log.d("CustomHGV-${hashCode()}", "smoothScrollBy suppressed dx=$dx dy=$dy")
            // do NOT clear flag here
            return
        }
        super.smoothScrollBy(dx, dy, interpolator)
    }

    // Also override other smoothScrollBy signature if present
    override fun smoothScrollBy(dx: Int, dy: Int) {
        if (suppressNextAutoScroll) {
            Log.d("CustomHGV-${hashCode()}", "smoothScrollBy (no interp) suppressed dx=$dx dy=$dy")
            return
        }
        super.smoothScrollBy(dx, dy)
    }

    // Some implementations call scrollTo; block it too
    override fun scrollTo(x: Int, y: Int) {
        if (suppressNextAutoScroll) {
            Log.d("CustomHGV-${hashCode()}", "scrollTo suppressed x=$x y=$y")
            return
        }
        super.scrollTo(x, y)
    }

    // The framework will still call requestChildFocus — consume the suppression here
    override fun requestChildFocus(child: View?, focused: View?) {
        Log.d("CustomHGV-${hashCode()}", "requestChildFocus: child=${child?.tag}, focused=${focused?.tag}, scrollOffset=${computeHorizontalScrollOffset()} suppress=$suppressNextAutoScroll")

        // Let framework set focus (so keyboard/focus state is correct).
        super.requestChildFocus(child, focused)

        if (suppressNextAutoScroll) {
            // We've allowed focus to be set but we still want to ensure no scrolling happens.
            // Stop any internal animation that may still be running and clear the flag.
            post {
                stopScroll()
                suppressNextAutoScroll = false
                Log.d("CustomHGV-${hashCode()}", "suppress consumed in requestChildFocus; stopScroll() called")
            }
        }
    }
}
