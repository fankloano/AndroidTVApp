package com.example.mj_player_tv.database.help

import android.view.Gravity

data class TimeLineData(
    val timeId: String,
    val time: String,
    val gravity: Int = Gravity.CENTER,
    val width: Int,
    val textSizeSp: Float
)