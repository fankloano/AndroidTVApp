package com.example.mj_player_tv.database.entity

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne

@Entity
data class EpgSourceChannel(
    @Id(assignable = false)
    var id: Long = 0,
    @Unique(onConflict = ConflictStrategy.REPLACE)
    var chEpgId: String = "",
    var chId: String = "",
    var icon: MutableList<String>? = mutableListOf(),
    var name: String = "",
    var relatedepgSourceId: Long = 0L,
    var display_name: MutableList<String> = mutableListOf(),
    var isExternalEpg: Boolean = false
)
{
    lateinit var epgsource: ToOne<EpgSource>
}