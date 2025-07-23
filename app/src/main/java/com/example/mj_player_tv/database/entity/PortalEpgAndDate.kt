package com.example.mj_player_tv.database.entity


import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class PortalEpgAndDate(
    @Id
    var id: Long = 0,
    val channelIdByAccount: String,
    val datum: String
)
