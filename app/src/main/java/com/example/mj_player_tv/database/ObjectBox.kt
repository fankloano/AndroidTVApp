package com.example.mj_player_tv.database

import android.content.Context
import com.example.mj_player_tv.database.entity.MyObjectBox
import io.objectbox.BoxStore
import io.objectbox.config.DebugFlags

object ObjectBox {
    lateinit var store: BoxStore
        private set

    fun init(context: Context) {
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .debugFlags(DebugFlags.LOG_QUERIES or DebugFlags.LOG_TRANSACTIONS_WRITE or DebugFlags.LOG_TRANSACTIONS_READ)
            .build()
    }
}