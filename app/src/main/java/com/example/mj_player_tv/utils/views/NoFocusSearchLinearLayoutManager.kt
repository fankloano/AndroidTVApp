package com.example.mj_player_tv.utils.views

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class NoFocusSearchLinearLayoutManager(context: Context) : LinearLayoutManager(context, HORIZONTAL, false) {
    override fun onFocusSearchFailed(focused: View, direction: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): View? {
        // Return null to prevent RecyclerView from moving focus automatically
        return null
    }

    override fun onInterceptFocusSearch(focused: View, direction: Int): View? {
        // verhindert das RecyclerView einen neuen Fokus sucht
        return null
    }
}
