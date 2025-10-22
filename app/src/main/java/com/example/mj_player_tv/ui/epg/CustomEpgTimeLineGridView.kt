package com.example.mj_player_tv.ui.epg

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

open class CustomEpgTimeLineGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {

    init {
        layoutManager = object : LinearLayoutManager(context, HORIZONTAL, false) {
            override fun onRequestChildFocus(
                parent: RecyclerView,
                state: State,
                child: View,
                focused: View?
            ): Boolean {
                // This disables the default scroll behavior for focus movement.
                return true
            }
        }


        isFocusable = false

        setItemViewCacheSize(0)
    }

    final override fun setItemViewCacheSize(size: Int) {
        super.setItemViewCacheSize(size)
    }
}