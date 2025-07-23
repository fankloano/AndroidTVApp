package com.example.mj_player_tv.database.entity

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne

@Entity
data class AudioCategoryOB(
    @Id
    var id: Long = 0,
    var playlistId: Long? = null,
    var seriesCatId: String = "",
    var title: String = "",
    var editedName: String = "",
    var showingName: String = "",
    var accountData: String = "",
    var favorite: Boolean = false,
    @Unique
    var idByAccountData: String = "",
    var newCategory: Boolean = false,
    var sortMusicBy: String? = null
)
{
    lateinit var audioaccount: ToOne<Accounts>
}