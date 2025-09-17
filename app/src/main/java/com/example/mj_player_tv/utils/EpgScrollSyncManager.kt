package com.example.mj_player_tv.utils

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class EpgScrollSyncManager {
    private val listeners = mutableListOf<RecyclerView>()

    fun register(rv: RecyclerView) {
        listeners.add(rv)
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dx != 0) {
                    propagateScroll(recyclerView, dx)
                }
            }
        })
    }

    private fun propagateScroll(source: RecyclerView, dx: Int) {
        listeners.forEach { rv ->
            if (rv != source) {
                rv.scrollBy(dx, 0)
            }
        }
    }
}

