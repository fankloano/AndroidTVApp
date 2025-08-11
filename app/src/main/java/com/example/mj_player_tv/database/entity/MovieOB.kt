package com.example.mj_player_tv.database.entity

import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne

@Entity
data class MovieOB(
    @Id
    var id: Long = 0, // Der Primärschlüssel, muss manuell verwaltet werden
    @Unique(onConflict = ConflictStrategy.REPLACE)
    var idByAccountData: String = "",
    val movieId: String = "",
    val relatedMovieCategoryId: String = "",
    val accountName: String = "",
    val accountId: Long? = null,
    val movieName: String? = "",
    val movieCmd: String? = "",
    var movieTime: Int? = 0,
    val movieYear: String = "",
    val rate: String? = "",
    val rating_imdb: String? = "",
    var screenshot_uri: String? = "",
    var genres_str: String? = "",
    var actors: String? = "",
    val added: String? = "",
    var age: String? = "",
    var description: String? = "",
    var director: String? = "",
    var country: String? = "",
    var backdropPath: String? = "",
    var tmdb_id: String? = "",
    val o_name: String? = "",
    var currentPosition: Long = 0L,
    var isCompletelyWatched: Boolean = false,
    var isPartlyWatched: Boolean = false,
    var percentagePlayed: Double = 0.0,
    var isFavorite: Boolean = false,
    var xtreamExtension: String = "",
    var movieCodec: String? = null,
    var audioCodec: String? = null,
    var plexRatingKey: String? = null
)
{
    lateinit var moviecat: ToOne<MovieCategoryOB>
    lateinit var movieAccount: ToOne<Accounts>
}