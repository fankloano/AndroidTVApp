package com.example.mj_player_tv.database.entity

import com.example.mj_player_tv.database.IntListConverter
import io.objectbox.annotation.Backlink
import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne

@Entity
data class SeasonsOB(
    @Id
    var id: Long = 0,
    @Unique(onConflict = ConflictStrategy.REPLACE)
    val seriesSeasonIdByAccountData: String = "",
    val seriesIdByAccount: String = "",
    val seasonNumber: String = "",
    val playlistId: Long = 0L,
    val seasonsName: String = "",
    val seriesCmd: String = "",
    val rating_imdb: String? = "",
    val screenshot_uri: String? = "",
    var backdropPath: String = "",
    val description: String? = "",
    val tmdb_id: String? = "",
    @Convert(converter = IntListConverter::class, dbType = String::class)
    val episodesCount: List<Int> = emptyList(),
    var isSeasonFullyWatched: Boolean = false,
    var isSeasonPartlyWatched: Boolean = false,
    var seasonPercentagePlayed: Double = 0.0
)
{
    lateinit var serie: ToOne<SeriesOB>
    @Backlink(to = "season")
    lateinit var episodes: ToMany<EpisodesOB>
}