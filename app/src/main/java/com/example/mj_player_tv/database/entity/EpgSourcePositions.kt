package com.example.mj_player_tv.database.entity

import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne

@Entity
data class EpgSourcePositions(
    @Id var id: Long = 0,
    var accountId: Long? = 0L,
    var epgSourceIdentifier: Long = 0L,
    var position: Int = -1,
    var isSelected: Boolean = false,
    var isPlaylistEpg: Boolean = false,
    @Unique(onConflict = ConflictStrategy.REPLACE)
    var accountEpgSourceUnique: String = "",
) {
    lateinit var relatedepgsource: ToOne<EpgSource>
    lateinit var relatedaccount: ToOne<Accounts>
}
