package com.example.mj_player_tv.repository

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.MovieOB_
import com.example.mj_player_tv.database.help.Movie
import okio.IOException
import retrofit2.HttpException
import com.example.mj_player_tv.network.RetrofitInstance
import com.example.mj_player_tv.network.model.stalker.movies.MovieData
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MoviePagingSource(
    private val apiService: RetrofitInstance,
    private val account: Accounts,
    private val categoryId: String,
    private val sortBy: String,
    movieBox: Box<MovieOB>,
    private val updateTotalMovies: (Int) -> Unit
) : PagingSource<Int, MovieOB>() {

    val url = account.stalkerUrl
    val macAddress = account.macAddress
    val token = account.token ?: ""
    val userAgent = account.userAgent
    val cookie = "mac=${account.macAddress}; stb_lang=de; timezone=${account.timezone};"

    private val currentMoviesMap: Map<String, MovieOB> = movieBox.query(
        MovieOB_.accountId.equal(account.id).and(MovieOB_.relatedMovieCategoryId.equal(categoryId))
    ).build().find().associateBy { it.idByAccountData }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MovieOB> = withContext(Dispatchers.IO) {
        Log.d("UPDATE STALKER ACCOUNTS", "MOVIES TOKEN: $token && CAT_ID: $categoryId")
        val pageIndex = params.key ?: 1 // Startseite ist 1 oder wie auch immer deine API funktioniert

        return@withContext try {
            val response = apiService.getInstance(url).getMoviesByCategory(categoryId, pageIndex, sortBy, cookie, "Bearer $token", userAgent)

            val movieDataList = response.body()?.js?.data ?: emptyList() // Passe dies an die Struktur deiner API-Antwort an

            val movies = movieDataList.map { movieData ->
                val idByAccountData = "${movieData.id}_${account.id}"
                currentMoviesMap[idByAccountData] ?: convertToMovie(movieData, account.id)
            }
            if (pageIndex == 1) {
                val totalMovies = response.body()?.js?.total_items ?: 0
                updateTotalMovies(totalMovies) // Übergabe der Gesamtanzahl ans ViewModel
            }

            LoadResult.Page(
                data = movies,
                prevKey = if (pageIndex == 1) null else pageIndex - 1,
                nextKey = if (movies.isEmpty()) null else pageIndex + 1
            )

        } catch (exception: IOException) {
            val error = IOException("Please Check Internet Connection")
            LoadResult.Error(error)
        } catch (exception: HttpException) {
            LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MovieOB>): Int? {
        return state.anchorPosition
    }

    fun convertToMovie(movieData: MovieData, accountId: Long): MovieOB {
        return MovieOB(
            id = 0,
            idByAccountData = "${movieData.id}_${accountId}",
            movieId = movieData.id ?: "",
            relatedMovieCategoryId = movieData.category_id ?: "",
            accountName = account.name,
            accountId = accountId,
            movieName = movieData.name ?: "",
            movieCmd = movieData.cmd ?: "",
            movieTime = movieData.time,
            movieYear = movieData.year ?: "",
            rate = movieData.rate ?: "",
            rating_imdb = movieData.rating_imdb ?: "",
            screenshot_uri = movieData.screenshot_uri ?: "",
            genres_str = movieData.genres_str ?: "",
            actors = movieData.actors ?: "",
            added = movieData.added ?: "",
            age = movieData.age ?: "",
            description = movieData.description ?: "",
            director = movieData.director ?: "",
            tmdb_id = movieData.tmdb_id,
            o_name = movieData.o_name ?: "",
            currentPosition = 0L, // Platzhalter für aktuelle Position
            isFavorite = false,
            isCompletelyWatched = movieData.isFullyWatched,
            isPartlyWatched = movieData.isPartlyWatched,
            percentagePlayed = 0.0 // Platzhalter für Prozentwert
        )
    }
}
