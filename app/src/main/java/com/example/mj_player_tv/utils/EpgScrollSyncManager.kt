package com.example.mj_player_tv.utils

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class EpgScrollSyncManager {

    private val registered = mutableListOf<RecyclerView>()
    private var isSyncing = false

    fun register(rv: RecyclerView) {
        registered.add(rv)

        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!isSyncing) {
                    syncOthers(recyclerView)
                }
            }
        })
    }

    private fun syncOthers(source: RecyclerView) {
        val offset = source.computeHorizontalScrollOffset()

        isSyncing = true
        registered.forEach { rv ->
            if (rv != source) {
                val current = rv.computeHorizontalScrollOffset()
                val diff = offset - current
                if (diff != 0) {
                    rv.scrollBy(diff, 0)
                }
            }
        }
        isSyncing = false
    }

}

