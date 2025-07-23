package com.example.mj_player_tv.network

import com.example.mj_player_tv.network.model.MainInfoResponse
import com.example.mj_player_tv.network.model.ProfileResponse
import com.example.mj_player_tv.network.model.stalker.allepg.GetAllEpg
import com.example.mj_player_tv.network.model.stalker.alltvchannels.AllTvChannelsResponse
import com.example.mj_player_tv.network.model.stalker.epgforday.EpgByChannelByDayResponse
import com.example.mj_player_tv.network.model.stalker.moviecategory.MovieCategoryResponse
import com.example.mj_player_tv.network.model.stalker.movies.MoviesByCategoryResponse
import com.example.mj_player_tv.network.model.stalker.movieurl.MovieLinkResponse
import com.example.mj_player_tv.network.model.plex.PlexSignInResponse
import com.example.mj_player_tv.network.model.plex.items.PlexGetSectionItems
import com.example.mj_player_tv.network.model.plex.library.PlexGetUserLibraries
import com.example.mj_player_tv.network.model.plex.resources.PlexGetUserResources
import com.example.mj_player_tv.network.model.stalker.seriesdetails.SeriesDetailResponse
import com.example.mj_player_tv.network.model.stalker.series.SeriesByCategoryResponse
import com.example.mj_player_tv.network.model.stalker.seriescategory.SeriesCategoryResponse
import com.example.mj_player_tv.network.model.stalker.seriesurl.SeriesLinkUrlResponse
import com.example.mj_player_tv.network.model.stalker.shortepg.ShortEpgResponse
import com.example.mj_player_tv.network.model.stalker.sortedtvchannels.TvChannelsResponse
import com.example.mj_player_tv.network.model.tmdb.TmdbMovieImageResponse
import com.example.mj_player_tv.network.model.tmdb.imdb_id.TMDB_imdb_id
import com.example.mj_player_tv.network.model.tmdb.moviedetails.TMDBMovieDetails
import com.example.mj_player_tv.network.model.stalker.token.TokenResponse
import com.example.mj_player_tv.network.model.stalker.tvcatchuplink.GetTvCatchupResponse
import com.example.mj_player_tv.network.model.stalker.tvcategory.TvCategoryResponse
import com.example.mj_player_tv.network.model.stalker.tvchannellink.TvChannelLinkResponse
import com.example.mj_player_tv.network.model.tmdb.seasondetails.TMDBSeasonDetails
import com.example.mj_player_tv.network.model.tmdb.seriesdetails.TMDBSeriesDetails
import com.example.mj_player_tv.network.model.xtreamcodes.XtreamAuthentication
import com.example.mj_player_tv.network.model.xtreamcodes.allmovies.XtreamAllMovies
import com.example.mj_player_tv.network.model.xtreamcodes.allseries.XtreamAllSeries
import com.example.mj_player_tv.network.model.xtreamcodes.alltvchannels.XtreamAllChannels
import com.example.mj_player_tv.network.model.xtreamcodes.channelsbycategory.XtreamChannelsByCategory
import com.example.mj_player_tv.network.model.xtreamcodes.epgbychannel.XtreamEpgByChannel
import com.example.mj_player_tv.network.model.xtreamcodes.moviecategory.XtreamMovieCategory
import com.example.mj_player_tv.network.model.xtreamcodes.moviedetails.XtreamMovieDetails
import com.example.mj_player_tv.network.model.xtreamcodes.moviesbycategory.XtreamMoviesByCategory
import com.example.mj_player_tv.network.model.xtreamcodes.seriesbycategory.XtreamSeriesByCategory
import com.example.mj_player_tv.network.model.xtreamcodes.seriescategory.XtreamSeriesCategory
import com.example.mj_player_tv.network.model.xtreamcodes.seriesdetails.XtreamSeriesDetails
import com.example.mj_player_tv.network.model.xtreamcodes.shortepg.XtreamShortEpg
import com.example.mj_player_tv.network.model.xtreamcodes.tvcategory.XtreamTvCategory
import com.example.mj_player_tv.utils.Resource
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface Api {
    @GET("portal.php?type=stb&action=handshake&prehash=efd15c16dc497e0839ff5accfdc6ed99c32c4e2a")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getToken(@Header("Cookie")cookie: String, @Header("User-Agent")user_agent: String): Response<TokenResponse>

    @GET("portal.php?type=stb&action=get_profile&prehash=efd15c16dc497e0839ff5accfdc6ed99c32c4e2a")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getProfile(@Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<ProfileResponse>

    @GET("portal.php?type=account_info&action=get_main_info&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getMainInfo(@Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<MainInfoResponse>

    @GET("portal.php?type=itv&action=get_genres&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getTvCategory(@Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<TvCategoryResponse>

    @GET("portal.php?type=itv&action=get_ordered_list&force_ch_link_check=&fav=0&sortby=number&hd=0&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getOrderedTvChannels(@Query("genre")genre: String, @Query("p")page: Int, @Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<TvChannelsResponse>

    @GET("portal.php?type=itv&action=get_all_channels&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet",
        "Connection: keep-alive"
    )
    suspend fun getAllChannels(@Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<AllTvChannelsResponse>

    @GET("portal.php?type=itv&action=create_link&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getTvChannelLink(@Query("cmd")cmd: String, @Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<TvChannelLinkResponse>

    @GET("portal.php?type=tv_archive&action=create_link&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getTvCatchup(@Query("cmd")cmd: String, @Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<GetTvCatchupResponse>

    @GET("portal.php?type=itv&action=get_epg_info&period=72&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet",
        "Connection: keep-alive"
    )
    suspend fun getAllEpg(@Header("Cookie") cookie: String, @Header("Authorization") token: String, @Header("User-Agent")user_agent: String): Response<GetAllEpg>

    @GET("portal.php?type=itv&action=get_short_epg&size=10&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getShortEpgByChannel(@Query("ch_id") channelId: String, @Header("Cookie") cookie: String, @Header("Authorization") token: String, @Header("User-Agent")user_agent: String): Response<ShortEpgResponse>

    @GET("portal.php?type=epg&action=get_simple_data_table&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getEpgByChannelByDay(@Query("ch_id") channelId: String, @Query("date") date: String, @Query("p") page: Int, @Header("Cookie") cookie: String, @Header("Authorization") token: String, @Header("User-Agent")user_agent: String): Response<EpgByChannelByDayResponse>


    @GET("portal.php?type=vod&action=get_categories&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getMovieCategory(@Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<MovieCategoryResponse>

    @GET("portal.php?type=vod&action=get_ordered_list&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getMoviesByCategory(@Query("category")category: String, @Query("p")page: Int, @Query("sortby")sortBy: String, @Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<MoviesByCategoryResponse>

    @GET("portal.php?type=vod&action=get_ordered_list&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun searchMoviesByCategory(@Query("category")category: String, @Query("p")page: Int, @Query("search")searchTerm: String, @Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<MoviesByCategoryResponse>

    @GET("portal.php?type=vod&action=create_link&series=&forced_storage=undefined&disable_ad=0&download=0&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getMovieLink(@Query("cmd")cmd: String, @Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<MovieLinkResponse>


    @GET("portal.php?type=series&action=get_categories&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getSeriesCategory(@Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<SeriesCategoryResponse>

    @GET("portal.php?type=series&action=get_ordered_list&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getSeriesByCategory(@Query("category")category: String, @Query("p")page: Int, @Query("sortby")sortBy: String, @Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<SeriesByCategoryResponse>

    @GET("portal.php?type=series&action=get_ordered_list&season_id=0&episode_id=0&fav=0&sortby=added&hd=0&not_ended=0&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getSeriesDetail(@Query("movie_id")seriesId: String, @Query("p")page: Int, @Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<SeriesDetailResponse>

    @GET("portal.php?type=series&action=get_ordered_list&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun searchSeriesByCategory(@Query("category")category: String, @Query("p")page: Int, @Query("search")searchTerm: String, @Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<SeriesByCategoryResponse>

    @GET("portal.php?type=vod&action=create_link&forced_storage=undefined&disable_ad=0&download=0&JsHttpRequest=1-xml")
    @Headers(
        "X-User-Agent: Model: MAG250; Link: Ethernet"
    )
    suspend fun getSeriesLink(@Query("cmd")cmd: String, @Query("series")series: String, @Header("Cookie")cookie: String, @Header("Authorization")token: String, @Header("User-Agent")user_agent: String): Response<SeriesLinkUrlResponse>

    //EXTERNAL EPG
    @Streaming
    @GET
    suspend fun downloadEpgXml(@Url url: String): Response<ResponseBody>

    //TMDB
    @GET("https://api.themoviedb.org/3/movie/{movie_id}/images")
    suspend fun getTmdbMovieImage(@Path("movie_id") movieId: Int, @Query("api_key") apiKey: String): Response<TmdbMovieImageResponse>

    @GET("https://api.themoviedb.org/3/movie/{movie_id}")
    suspend fun getTmdbMovieDetails(@Path("movie_id") movieId: Int, @Query("api_key") apiKey: String): Response<TMDBMovieDetails>

    @GET("https://api.themoviedb.org/3/find/{imdb_id}?external_source=imdb_id")
    suspend fun getTmdbMovieDetailsWithImdbId(@Path("imdb_id") imdbId: String, @Query("api_key") apiKey: String): Response<TMDB_imdb_id>


    @GET("https://api.themoviedb.org/3/tv/{series_id}")
    suspend fun getTmdbSeriesDetails(@Path("series_id") seriesId: Int, @Query("api_key") apiKey: String): Response<TMDBSeriesDetails>

    @GET("https://api.themoviedb.org/3/tv/{series_id}/season/{season_number}")
    suspend fun getTmdbSeasonDetails(@Path("series_id") seriesId: Int, @Path("season_number") seasonNumber: Int, @Query("api_key") apiKey: String): Response<TMDBSeasonDetails>

    //XTREAM CODES
    @GET("player_api.php")
    suspend fun getXtreamAuthentication(@Header("User-Agent")user_agent: String, @Query("username") username: String, @Query("password") password: String): Response<XtreamAuthentication>

    @GET("player_api.php")
    @Headers(
        "Content-Type: application/json"
    )
    suspend fun getXtreamTvCategories(@Header("User-Agent")user_agent: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String = "get_live_categories"): Response<List<XtreamTvCategory>>

    @GET("player_api.php")
    suspend fun getXtreamMovieCategories(@Header("User-Agent")user_agent: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String = "get_vod_categories"): Response<List<XtreamMovieCategory>>

    @GET("player_api.php")
    suspend fun getXtreamSeriesCategories(@Header("User-Agent")user_agent: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String = "get_series_categories"): Response<List<XtreamSeriesCategory>>

    @GET("player_api.php")
    @Headers(
        "Content-Type: application/json",
        "Content-Encoding: gzip"
    )
    suspend fun getXtreamAllMovies(@Header("User-Agent")user_agent: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String = "get_vod_streams"): Response<List<XtreamAllMovies>>

    @GET("player_api.php")
    suspend fun getXtreamAllSeries(@Header("User-Agent")user_agent: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String = "get_series"): Response<List<XtreamAllSeries>>

    @GET("player_api.php")
    suspend fun getXtreamAllChannels(@Header("User-Agent")user_agent: String, @Query("username") username: String, @Query("password") password: String, @Query("action") action: String = "get_live_streams"): Response<List<XtreamAllChannels>>

    @GET("player_api.php")
    suspend fun getXtreamChannelsByCategory(@Header("User-Agent")user_agent: String, @Query("username") username: String, @Query("password") password: String, @Query("category_id") categoryId: String, @Query("action") action: String = "get_live_streams"): Response<List<XtreamChannelsByCategory>>

    @GET("player_api.php")
    suspend fun getXtreamMoviesByCategory(@Header("User-Agent")user_agent: String, @Query("username") username: String, @Query("password") password: String, @Query("category_id") categoryId: String, @Query("action") action: String = "get_vod_streams"): Response<List<XtreamMoviesByCategory>>

    @GET("player_api.php")
    suspend fun getXtreamSeriesByCategory(@Header("User-Agent")user_agent: String, @Query("username") username: String, @Query("password") password: String, @Query("category_id") categoryId: String, @Query("action") action: String = "get_series"): Response<List<XtreamSeriesByCategory>>

    @GET("player_api.php")
    @Headers("Accept: application/json")
    suspend fun getXtreamSeriesInfo(@Header("User-Agent")user_agent: String, @Query("username") username: String, @Query("password") password: String, @Query("series_id") seriesId: String, @Query("action") action: String = "get_series_info"): Response<XtreamSeriesDetails>

    @GET("player_api.php")

    suspend fun getXtreamMovieInfo(@Header("User-Agent")user_agent: String, @Query("username") username: String, @Query("password") password: String, @Query("vod_id") vodId: String, @Query("action") action: String = "get_vod_info"): Response<XtreamMovieDetails>

    @GET("player_api.php")
    suspend fun getXtreamShortEpg(@Header("User-Agent") user_agent: String, @Query("username") username: String, @Query("password") password: String, @Query("stream_id") streamId: String, @Query("action") action: String = "get_short_epg"): Response<XtreamShortEpg>

    @GET("player_api.php")
    suspend fun getXtreamEpgByChannel(@Header("User-Agent") user_agent: String, @Query("username") username: String, @Query("password") password: String, @Query("stream_id") streamId: String, @Query("action") action: String = "get_simple_data_table"): Response<XtreamEpgByChannel>

    @Streaming
    @GET("xmltv.php")
    suspend fun downloadEpgXtream(@Query("username") username: String, @Query("password") password: String): Response<ResponseBody>

    ////PLEX API
    @POST("api/v2/users/signin")
    @Headers(
        "X-Plex-Client-Identifier: LMJ Player",
        "Accept: application/json"
    )
    suspend fun postPlexSignIn(@Query("login") loginMail: String, @Query("password") password: String): Response<PlexSignInResponse>

    @GET("api/v2/resources")
    @Headers(
        "X-Plex-Client-Identifier: LMJ Player",
        "Accept: application/json"
    )
    suspend fun getPlexUserResources(@Header("X-Plex-Token") authToken: String): Response<List<PlexGetUserResources>>

    @GET("library/sections")
    @Headers(
        "X-Plex-Client-Identifier: LMJ Player",
        "Accept: application/json"
    )
    suspend fun getPlexUserLibraries(@Header("X-Plex-Token") authToken: String): Response<PlexGetUserLibraries>

    @GET("library/sections/{sectionKey}/all")
    @Headers(
        "X-Plex-Client-Identifier: LMJ Player",
        "Accept: application/json"
    )
    suspend fun getPlexSectionItems(
        @Path("sectionKey") sectionKey: Int,
        @Query("X-Plex-Token") token: String,
        @Query("X-Plex-Container-Start") offset: Int,
        @Query("X-Plex-Container-Size") limit: Int
    ): Response<PlexGetSectionItems>

    @GET("library/sections/{sectionKey}/all")
    @Headers(
        "X-Plex-Client-Identifier: LMJ Player",
        "Accept: application/json"
    )
    suspend fun getPlexSameItems(
        @Path("sectionKey") sectionKey: Int,
        @Query("X-Plex-Token") token: String,
        @Query("guid") guid: String
    ): Response<PlexGetSectionItems>

    @POST("/:/progress")
    suspend fun updateItemProgress(
        @Query("key") ratingKey: String,
        @Query("time") currentTimeMs: Long,
        @Query("state") state: String, // "playing", "paused", "stopped"
        @Query("identifier") identifier: String = "com.plexapp.plugins.library",
        @Header("X-Plex-Token") token: String
    ): Response<Unit>

    @GET("/:/scrobble")
    suspend fun markItemAsWatched(
        @Query("key") ratingKey: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library",
        @Header("X-Plex-Token") token: String
    ): Response<Unit>

    @GET("/:/unscrobble")
    suspend fun markItemAsNotWatched(
        @Query("key") ratingKey: String,
        @Query("identifier") identifier: String = "com.plexapp.plugins.library",
        @Header("X-Plex-Token") token: String
    ): Response<Unit>

    @GET("library/sections/watchlist/all")
    @Headers(
        "X-Plex-Client-Identifier: LMJ Player",
        "Accept: application/json"
    )
    suspend fun getPlexWatchlist(
        @Header("X-Plex-Token") token: String
    ): Response<PlexGetSectionItems>

    @PUT("actions/addToWatchlist")
    @Headers(
        "X-Plex-Client-Identifier: LMJ Player",
        "Accept: application/json"
    )
    suspend fun addItemToWatchlist(
        @Query("ratingKey") ratingKey: String,
        @Header("X-Plex-Token") token: String
    ): Response<Unit>

    @PUT("actions/removeFromWatchlist")
    @Headers(
        "X-Plex-Client-Identifier: LMJ Player",
        "Accept: application/json"
    )
    suspend fun removeItemFromWatchlist(
        @Query("ratingKey") ratingKey: String,
        @Header("X-Plex-Token") token: String
    ): Response<Unit>
}