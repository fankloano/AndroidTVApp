package com.example.mj_player_tv.database.entity

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToMany

@Entity
data class EpgSource(
    @Id
    var id: Long = 0,
    var name: String = "",
    var url: String = "",
    var playlistId: Long? = null,
    var standard: Boolean = false,
    var isExternalEpg: Boolean = false,
    var isPlaylistEpg: Boolean = false,
    var isStalkerEpg: Boolean = false,
    var isXtreamEpg: Boolean = false,
    var maxDays: Int = 7,
    var minDays: Int = 3,
    var timeOffSet: Int = 0,
    var previousTimeOffSet: Int = 0,
    var lastDeletedDate: Long = 0L,
    var automaticUpdateDays: Int = 48,
    var lastUpdatedDate: Long = 0L,
    var updateSuccessful: Boolean = true,
    @Unique(onConflict = ConflictStrategy.REPLACE)
    var uniqueEpgSourceId: String = ""
) {
    @Backlink(to = "relatedepgsource")
    lateinit var relatedaccounts: ToMany<EpgSourcePositions>
    @Backlink(to = "epgsource")
    lateinit var epgchs: ToMany<EpgSourceChannel>
}
