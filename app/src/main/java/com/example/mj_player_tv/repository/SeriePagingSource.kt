package com.example.mj_player_tv.repository

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.MovieOB_
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.database.entity.SeriesOB_
import com.example.mj_player_tv.database.help.Movie
import com.example.mj_player_tv.database.help.Serie
import okio.IOException
import retrofit2.HttpException
import com.example.mj_player_tv.network.RetrofitInstance
import com.example.mj_player_tv.network.model.stalker.movies.MovieData
import com.example.mj_player_tv.network.model.stalker.series.SeriesData
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SeriePagingSource(
    private val apiService: RetrofitInstance,
    private val account: Accounts,
    private val categoryId: String,
    private val sortBy: String,
    serieBox: Box<SeriesOB>,
    private val updateTotalSeries: (Int) -> Unit
) : PagingSource<Int, SeriesOB>() { // Ändere den generischen Typ auf SeriesOB

    val url = account.stalkerUrl
    val macAddress = account.macAddress
    val token = account.token ?: ""
    val userAgent = account.userAgent
    val cookie = "mac=${account.macAddress}; stb_lang=de; timezone=${account.timezone};"

    // Lade bereits gespeicherte Serien aus der DB in eine Map
    private val currentSeriesMap: Map<String, SeriesOB> = serieBox.query(
        SeriesOB_.accountId.equal(account.id).and(SeriesOB_.relatedSeriesCategoryId.equal(categoryId))
    ).build().find().associateBy { it.idByAccountData }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SeriesOB> = withContext(Dispatchers.IO) {
        val pageIndex = params.key ?: 1
        return@withContext try {
            val response = apiService.getInstance(url).getSeriesByCategory(categoryId, pageIndex, sortBy, cookie, "Bearer $token", userAgent)
            val seriesDataList = response.body()?.js?.data ?: emptyList()

            val series = seriesDataList.map { seriesData ->
                val idByAccountData = "${seriesData.id}_${account.id}"

                // Falls die Serie bereits in der DB existiert, nutze sie – sonst erstelle eine neue Instanz
                currentSeriesMap[idByAccountData] ?: convertToSeriesOB(seriesData, account.id)
            }

            if (pageIndex == 1) {
                val totalSeries = response.body()?.js?.total_items ?: 0
                updateTotalSeries(totalSeries) // Übergabe der Gesamtanzahl ans ViewModel
            }

            LoadResult.Page(
                data = series,
                prevKey = if (pageIndex == 1) null else pageIndex - 1,
                nextKey = if (series.isEmpty()) null else pageIndex + 1
            )

        } catch (exception: IOException) {
            LoadResult.Error(IOException("Please Check Internet Connection"))
        } catch (exception: HttpException) {
            LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, SeriesOB>): Int? {
        return state.anchorPosition
    }

    private fun convertToSeriesOB(seriesData: SeriesData, accountId: Long): SeriesOB {
        return SeriesOB(
            id = 0, // Erst speichern, wenn notwendig
            idByAccountData = "${seriesData.id}_${accountId}",
            seriesId = seriesData.id ?: "",
            relatedSeriesCategoryId = seriesData.category_id ?: "",
            accountName = account.name,
            accountId = accountId,
            seriesName = seriesData.name ?: "",
            seriesCmd = seriesData.cmd ?: "",
            seriesTime = seriesData.time?.toIntOrNull(),
            seriesYear = seriesData.year ?: "",
            rate = seriesData.rate ?: "",
            rating_imdb = seriesData.rating_imdb ?: "",
            screenshot_uri = seriesData.screenshot_uri ?: "",
            genres_str = seriesData.genres_str ?: "",
            actors = seriesData.actors ?: "",
            added = seriesData.added ?: "",
            age = seriesData.age ?: "",
            description = seriesData.description ?: "",
            director = seriesData.director ?: "",
            tmdb_id = seriesData.tmdb_id ?: "",
            o_name = seriesData.o_name ?: "",
            currentPosition = 0L, // Platzhalter für aktuelle Position
            isFavorite = false,
            isCompletelyWatched = seriesData.isFullyWatched,
            isPartlyWatched = seriesData.isPartlyWatched,
            seriesPercentagePlayed = 0.0, // Platzhalter für Prozentwert
            lastWatchedEpisode = 1,
            lastWatchedSeason = 1,
            totalSeasons = 0,
            totalEpisodes = 0,
            newSeasons = false,
            newEpisodes = false
        )
    }
}

