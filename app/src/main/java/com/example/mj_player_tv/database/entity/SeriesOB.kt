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
data class SeriesOB(
    @Id
    var id: Long = 0, // Der Primärschlüssel, muss manuell verwaltet werden
    @Unique(onConflict = ConflictStrategy.REPLACE)
    var idByAccountData: String = "",
    val seriesId: String = "",
    val relatedSeriesCategoryId: String = "",
    val accountName: String = "",
    val accountId: Long? = null,
    val seriesName: String = "",
    val seriesCmd: String? = "",
    var seriesTime: Int? = 0,
    val seriesYear: String? = "",
    val rate: String? = "",
    val rating_imdb: String? = "",
    val screenshot_uri: String? = "",
    var backdropPath: String? = "",
    val genres_str: String? = "",
    val actors: String? = "",
    val added: String? = "",
    val age: String? = "",
    val description: String? = "",
    val director: String? = "",
    val tmdb_id: String? = "",
    val o_name: String? = "",
    var currentPosition: Long? = 0L,
    var isCompletelyWatched: Boolean = false,
    var isPartlyWatched: Boolean = false,
    var seriesPercentagePlayed: Double = 0.0,
    var lastWatchedSeason: Int = 1,
    var lastWatchedEpisode: Int = 1,
    var totalSeasons: Int = 0,
    var totalEpisodes: Int = 0,
    var isFavorite: Boolean = false,
    var xtreamExtension: String = "",
    var newSeasons: Boolean = false,
    var newEpisodes: Boolean = false
)
{
    lateinit var seriescat: ToOne<SeriesCategoryOB>
    @Backlink(to = "serie")
    lateinit var seasons: ToMany<SeasonsOB>
    lateinit var seriesAccount: ToOne<Accounts>
}