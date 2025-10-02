package com.example.mj_player_tv.utils

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class EpgScrollSyncManager {
    private val listeners = mutableListOf<RecyclerView>()
    private var isPropagating = false
    private var totalScrollX = 0 // globaler Pixel-Offset für alle

    fun register(rv: RecyclerView) {
        listeners.add(rv)
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dx != 0 && !isPropagating) {
                    totalScrollX += dx
                    propagateScroll(recyclerView, dx)
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
        if (dx == 0) return
        totalScrollX = globalOffset
        propagateScroll(source = null, dx = dx)
    }

    fun getTotalScrollX(): Int = totalScrollX
}


