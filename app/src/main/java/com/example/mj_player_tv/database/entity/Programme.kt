package com.example.mj_player_tv.database.entity

import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne

@Entity
data class Programme(
    @Id
    var id: Long = 0,
    @Unique(onConflict = ConflictStrategy.REPLACE)
    var epgForCh: String = "",
    var startTimeStamp: Long = 0L,
    var stopTimeStamp: Long = 0L,
    var rememberInterval: Long = 0L
)
{
    lateinit var epgData: ToOne<EpgDataOB>
    lateinit var tvchannels: ToOne<TvChannelOB>
}
