package com.example.mj_player_tv.database.entity

import com.example.mj_player_tv.database.EpgSourceListConverter
import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToMany

@Entity
data class Accounts(
    @Id
    var id: Long = 0,
    var name: String = "",
    var stalkerUrl: String = "",
    var username: String = "",
    var macAddress: String = "",
    var token: String? = "",
    var totalAccountData: String = "",
    var userAgent: String = "",
    var isSelected: Boolean = true,
    var expiryDate: String = "",
    var timezone: String = "",
    var usePlaylistLogos: Boolean = true,
    var useEpgLogos: Boolean = false,
    var isFavoriteCategoryShowing: Boolean = false,
    var isStalker: Boolean = false,
    var isXtream: Boolean = false,
    var isPlex: Boolean = false,
    var totalTvCategories: String = "",
    var totalTvChannels: String = "",
    var totalMovieCategories: String = "",
    var totalSeriesCategories: String = "",
    var lastUpdatedDate: Long = 0L,
    var lastUpdateStatus: Int = 1,
    var tvchannelLoadingOK: Int = 1,
    var tvCategoryLoadingOK: Int = 1,
    var movieCategoryLoadingOK: Int = 1,
    var seriesCategoryLoadingOK: Int = 1,
    var showTv: Boolean = true,
    var showVod: Boolean = true,
    var autoUpdateHours: Int = 72,
    var updateOnAppStart: Boolean = false,
    var orderBy: Int? = 0,
    var orderByCategory: Int? = 0,
    var isUserCategories: Boolean = false,
    var xtreamOutPutFormats: MutableList<String>? = mutableListOf(),
    var xtreamUseDefaultType: Boolean = true,
    var xtreamOtherStreamType: String = "",
    var usePlaylistEpg: Boolean = true,
    var sortMoviesBy: String? = null,
    var sortSeriesBy: String? = null,
    var mainPlexToken: String? = null,
    var plexClientIdentifier: String? = null
) {
    @Backlink(to = "relatedaccount")
    lateinit var epgsources: ToMany<EpgSourcePositions>
    @Backlink(to = "account")
    lateinit var channels: ToMany<TvChannelOB>
    @Backlink(to = "tvaccount")
    lateinit var tvcategories: ToMany<TvCategoryOB>
    @Backlink(to = "movieaccount")
    lateinit var moviecategories: ToMany<MovieCategoryOB>
    @Backlink(to = "seriesaccount")
    lateinit var seriescategories: ToMany<SeriesCategoryOB>
}
