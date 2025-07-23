package com.example.mj_player_tv.network.model.stalker.tvcategory

import android.os.Parcelable

data class TvCategoryJs(
    val alias: String,
    val censored: Int?,
    val id: String,
    val modified: String?,
    val number: Int?,
    val title: String
)

