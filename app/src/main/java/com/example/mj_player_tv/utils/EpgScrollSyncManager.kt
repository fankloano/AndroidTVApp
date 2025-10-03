package com.example.mj_player_tv.utils

import android.util.Log
import androidx.recyclerview.widget.RecyclerView

class EpgScrollSyncManager {
    private val listeners = mutableListOf<RecyclerView>()
    private var isPropagating = false
    private var totalScrollX = 0 // globaler Pixel-Offset für alle

    /**
     * Registriere nur die RecyclerViews, die synchronisiert werden sollen
     * (Timeline + Program RecyclerViews).
     * Alles andere, z. B. accountTvCategory, wird nicht registriert.
     */
    fun register(rv: RecyclerView) {
        listeners.add(rv)
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!isPropagating) {
                    // globaler Offset
                    val newOffset = recyclerView.computeHorizontalScrollOffset()
                    val delta = newOffset - totalScrollX
                    if (delta != 0) {
                        totalScrollX = newOffset
                        propagateScroll(recyclerView, delta)
                        Log.d("EPG_SCROLL_SYNC", "onScrolled → dx=$dx, newOffset=$newOffset, delta=$delta")
                    }
                }
            }
        })
    }

    private fun propagateScroll(source: RecyclerView?, dx: Int) {
        isPropagating = true
        listeners.forEach { rv ->
            if (rv != source) {
                rv.scrollBy(dx, 0)
            }
        }
        isPropagating = false
    }

    fun jumpSyncTo(globalOffset: Int) {
        val dx = globalOffset - totalScrollX
        if (dx != 0) {
            Log.d("EPG_SCROLL_SYNC", "jumpSyncTo → globalOffset=$globalOffset, dx=$dx, oldTotalScrollX=$totalScrollX")
            totalScrollX = globalOffset
            propagateScroll(source = null, dx = dx)
        }
    }

    fun getTotalScrollX(): Int = totalScrollX
}
