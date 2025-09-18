package com.example.mj_player_tv.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

object Common {


    fun getWidthInPercent(context: Context, percent: Int): Int {
        val width = context.resources.displayMetrics.widthPixels
        return (width * percent) / 100
    }

    fun getView(parent: ViewGroup, layout: Int): View {
        return LayoutInflater.from(parent.context).inflate(layout, parent, false)
    }

}