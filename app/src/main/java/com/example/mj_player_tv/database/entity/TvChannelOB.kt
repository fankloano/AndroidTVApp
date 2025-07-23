package com.example.mj_player_tv.database.entity

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Uid
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne

@Entity
data class TvChannelOB(
    @Id
    var id: Long = 0,
    @Index
    var playlistId: Long? = null,
    var channelId: Int? = null,
    var number: String = "",
    var cmd: String = "",
    var logo: String = "",
    var epgLogo: String = "",
    var tv_genre_id: Int? = null,
    @Index
    var relatedtvCategoryId: String = "",
    var name: String = "",
    var editedName: String = "",
    @Index
    var showingName: String = "",
    var xmltv_id: String = "",
    @Uid(9015815363700429417L)
    var enable_tv_archive: Int? = null,
    var tv_archive_duration: Int? = null,
    @Uid(6975995817137907483L)
    var archive: Int? = null,
    var accountData: String = "",
    @Unique(onConflict = ConflictStrategy.REPLACE)
    var idByAccountData: String = "",
    var epgSourceId: Long? = null,
    var usesPlaylistEpg: Boolean = true,
    var usesExternalEpg: Boolean = false,
    var alwaysUsesExternalEpg: Boolean = false,
    @Index
    var timeWatched: Long = 0L,
    var newChannel: Boolean = false,
    var epgTimeOffSet: Int? = null,
    var newTvCategoryId: String = "",
    var isCopy: Boolean = false,
    var isFavorite: Boolean = false
)
{
    var epgChannel: ToOne<EpgSourceChannel>? = null
    var linkedEpgChannel: ToOne<EpgSourceChannel>? = null
    lateinit var account: ToOne<Accounts>
    lateinit var reltvcategory: ToOne<TvCategoryOB>
    @Backlink(to = "tvchannel")
    lateinit var tvcategoryLink: ToMany<ChannelPositions>
}


