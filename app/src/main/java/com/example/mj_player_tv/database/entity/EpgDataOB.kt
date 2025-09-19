package com.example.mj_player_tv.database.entity


import io.objectbox.annotation.Backlink
import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne

@Entity
data class EpgDataOB(
    @Id
    var id: Long = 0,
    @Unique(onConflict = ConflictStrategy.REPLACE)
    var idByAccountData: String = "",
    var epgId: String = "",
    var chId: String = "",
    var datum: String = "",
    var name: String = "",
    var sub_title: String = "",
    var descr: String = "",
    var category: MutableList<String>? = null,
    var director: MutableList<String>? = null,
    var actor: MutableList<String>? = null,
    var date: String = "",
    var country: MutableList<String>? = null,
    var showIcon: String = "",
    var episode_num: String = "",
    var rating: String = "",
    var startTime: String = "",
    var endTime: String = "",
    @Index
    var startTimestamp: Long = 0L,
    @Index
    var stopTimestamp: Long = 0L,
    var mark_archive: Int? = null,
    var accountData: String = "",
    @Index
    var epgSourceId: Int? = null,
    @Index
    var epgChId: String? = "",
    var isRemembered: Boolean = false
)