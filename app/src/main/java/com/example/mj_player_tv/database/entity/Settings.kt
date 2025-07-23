package com.example.mj_player_tv.database.entity

import com.example.mj_player_tv.database.StringListConverter
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class Settings(
    @Id
    var id: Long = 0,
    val language: Int = 0,
    @Convert(converter = StringListConverter::class, dbType = String::class)
    var prefixes: MutableList<String> = mutableListOf(),
    @Convert(converter = StringListConverter::class, dbType = String::class)
    var suffixes: MutableList<String> = mutableListOf(),
    @Convert(converter = StringListConverter::class, dbType = String::class)
    var tvcategoryPrefixes: MutableList<String> = mutableListOf(),
    @Convert(converter = StringListConverter::class, dbType = String::class)
    var tvcategorySuffixes: MutableList<String> = mutableListOf(),
    @Convert(converter = StringListConverter::class, dbType = String::class)
    var moviecategoryPrefixes: MutableList<String> = mutableListOf(),
    @Convert(converter = StringListConverter::class, dbType = String::class)
    var moviecategorySuffixes: MutableList<String> = mutableListOf(),
    @Convert(converter = StringListConverter::class, dbType = String::class)
    var searchString: MutableList<String> = mutableListOf(),
    val searchByActivatedCategories: Boolean = false,
    var epgSourceSorting: Int = 0,
    var playlistSorting: Int = 0,
    var tmdbApiKey: String = "14ed8032efb4f37adc0cb636ac8d56af",
    var showClock: Boolean = true,
    var playMoviesWithVlc: Boolean = false,
    var sortMoviesBy: String? = "added",
    var sortSeriesBy: String? = "added",
    var globalSearchFilteredCategories: Boolean = false,
    var tvReminderTime: Long = 10L
)