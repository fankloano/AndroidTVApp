package com.example.mj_player_tv.database.entity

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne

@Entity
data class PlexCategoryOB(
    @Id
    var id: Long = 0,
    var playlistId: Long? = null,
    var plexCatId: String = "",
    var title: String = "",
    var editedName: String = "",
    var showingName: String = "",
    var accountData: String = "",
    var favorite: Boolean = false,
    var isMovie: Boolean = false,
    var isAudio: Boolean = false,
    @Unique
    var idByAccountData: String = "",
    var newCategory: Boolean = false,
    var currentPosition: Int? = 0,
    var oldPosition: Int? = 0
)
{
    lateinit var plexAccount: ToOne<Accounts>
}
