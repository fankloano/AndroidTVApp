package com.example.mj_player_tv.database.entity

import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne

@Entity
data class ChannelPositions(
    @Id var id: Long = 0,
    var channel: String = "", // This is the foreign key reference to the Channel entity
    var playlistId: Long? = 0L,
    var relatedtvCategoryId: String = "",
    @Index
    var position: Int = 0,
    @Index
    var originalPosition: Int = 0,
    @Unique(onConflict = ConflictStrategy.REPLACE)
    var catAndChannelAccount: String = "",
    var isCopiedChannel: Boolean = false,
    var isSelected: Boolean = true
) {
    lateinit var tvcategory: ToOne<TvCategoryOB>
    lateinit var tvchannel: ToOne<TvChannelOB>
}
