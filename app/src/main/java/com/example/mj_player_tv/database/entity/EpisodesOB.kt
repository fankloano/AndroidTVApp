package com.example.mj_player_tv.database.entity

import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne

@Entity
data class EpisodesOB(
    @Id
    var id: Long = 0,
    @Unique(onConflict = ConflictStrategy.REPLACE)
    val seriesSeasonEpisodeIdByAccountData: String = "",
    val seriesSeasonIdByAccount: String = "",
    val seriesIdByAccount: String = "",
    val episodeNumber: Int = 0,
    val seasonNumber: String? = "",
    val seasonName: String? = "",
    val seriesName: String? = "",
    var episodeName: String? = "",
    var episodeTime: String? = "",
    val episodeCmd: String? = "",
    var episodeImg: String? = "",
    var episodeDescription: String? = "",
    var currentPosition: Long = 0,
    var isEpisodeFullyWatched: Boolean = false,
    var isEpisodePartlyWatched: Boolean = false,
    var episodePercentagePlayed: Double = 0.0,
    val containerExtension: String = "",
    val videoCodec: String? = null,
    val audioCodec: String? = null
)
{
    lateinit var season: ToOne<SeasonsOB>
}

