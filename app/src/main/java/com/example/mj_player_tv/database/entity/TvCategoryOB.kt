package com.example.mj_player_tv.database.entity

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Uid
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne

@Entity
data class TvCategoryOB(
    @Id
    var id: Long = 0,
    var playlistId: Long? = null,
    var tvCatId: String = "",
    var number: Int? = null,
    @Uid(2835887008446135176L)
    var censored: Int? = null,
    var title: String = "",
    var editedName: String = "",
    var showingName: String = "",
    var accountData: String = "",
    var favorite: Boolean = false,
    var orderBy: Int? = 0,
    @Unique
    var idByAccountData: String = "",
    var newCategory: Boolean = false,
    var userCategory: Boolean = false,
    var startPosition: Int? = null,
    var modifiedPosition: Int? = null,
    var isFavoriteCategory: Boolean = false,
    var isAllChannelsCategory: Boolean = false,
    var epgTimeOffSet: Int? = null
)
{
    lateinit var tvaccount: ToOne<Accounts>
    @Backlink(to = "tvcategory")
    lateinit var tvChannelLink: ToMany<ChannelPositions>
    @Backlink(to = "reltvcategory")
    lateinit var tvchannels: ToMany<TvChannelOB>
}