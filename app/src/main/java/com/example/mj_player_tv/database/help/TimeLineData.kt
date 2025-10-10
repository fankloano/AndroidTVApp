package com.example.mj_player_tv.database.help

import android.view.Gravity
import org.joda.time.DateTime

data class TimeLineData(
    val timeId: DateTime?,
    val time: String,
    val gravity: Int = Gravity.CENTER,
    val width: Int,
    val textSizeSp: Float
)