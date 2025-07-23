package com.example.mj_player_tv.repository

import android.app.Application
import android.util.Log
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgDataOB_
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.EpgSourceChannel
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.entity.TvChannelOB_
import com.example.mj_player_tv.network.RetrofitInstance
import com.example.mj_player_tv.network.model.MainInfoResponse
import com.example.mj_player_tv.network.model.ProfileResponse
import com.example.mj_player_tv.network.model.stalker.seriesdetails.SeriesData
import com.example.mj_player_tv.network.model.stalker.alltvchannels.AllTvChannelsResponse
import com.example.mj_player_tv.network.model.stalker.epgforday.EpgByChannelByDayResponse
import com.example.mj_player_tv.network.model.stalker.moviecategory.MovieCategoryResponse
import com.example.mj_player_tv.network.model.stalker.movies.MoviesByCategoryResponse
import com.example.mj_player_tv.network.model.stalker.movieurl.MovieLinkResponse
import com.example.mj_player_tv.network.model.stalker.series.SeriesByCategoryResponse
import com.example.mj_player_tv.network.model.stalker.seriescategory.SeriesCategoryResponse
import com.example.mj_player_tv.network.model.stalker.seriesurl.SeriesLinkUrlResponse
import com.example.mj_player_tv.network.model.stalker.shortepg.ShortEpgResponse
import com.example.mj_player_tv.network.model.stalker.sortedtvchannels.TvChannelsResponse
import com.example.mj_player_tv.network.model.stalker.token.TokenResponse
import com.example.mj_player_tv.network.model.stalker.tvcatchuplink.GetTvCatchupResponse
import com.example.mj_player_tv.network.model.stalker.tvcategory.TvCategoryResponse
import com.example.mj_player_tv.network.model.stalker.tvchannellink.TvChannelLinkResponse
import com.example.mj_player_tv.utils.Resource
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import retrofit2.HttpException
import java.io.IOException

class StalkerRepository() {
    private val channelsService = RetrofitInstance

    suspend fun getToken(url: String, cookie: String, userAgent: String): Resource<TokenResponse> {
        return try {
            val request = channelsService.getInstance(url).getToken(cookie, userAgent)
            if (request.isSuccessful) {
                Log.d("TOKEN FETCHED:", "OK: ${request.body().toString()}")
                if (request.body() != null) {
                    Resource.Success(request.body()!!)
                } else {
                    Log.d("TOKEN FETCHED:", "OK BUT EMPTY")

                    Resource.Error("Empty body")
                }
            } else {
                Log.d("TOKEN FETCHED:", "NOT OK AND EMPTY")
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message} + ${e.cause} + ${e.stackTrace}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: IllegalStateException) {
            Resource.Error("Illegal State Exc.: ${e.message}")
        } catch (e: JsonSyntaxException) {
            Resource.Error("Json Syntax error: ${e.message}")
        } catch (e: JsonParseException) {
            Resource.Error("Json Parsing error: ${e.message}")
        } catch (e: NullPointerException) {
            Resource.Error("Nullpointer error: ${e.message}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getProfile(url: String, cookie: String, token: String, userAgent: String): Resource<ProfileResponse> {
        return try {
            val request = channelsService.getInstance(url).getProfile(cookie, token, userAgent)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getMainInfo(url: String, cookie: String, token: String, userAgent: String): Resource<MainInfoResponse> {
        return try {
            val request = channelsService.getInstance(url).getMainInfo(cookie, token, userAgent)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getTvCategory(url: String, cookie: String, token: String, userAgent: String): Resource<TvCategoryResponse> {
        return try {
            val request = channelsService.getInstance(url).getTvCategory(cookie, token, userAgent)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getOrderedTvChannels(url: String, cookie: String, token: String, userAgent: String, genre: String, page: Int): Resource<TvChannelsResponse> {
        return try {
            val request = channelsService.getInstance(url).getOrderedTvChannels(genre, page, cookie, token, userAgent)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getAllTvChannels(url: String, cookie: String, token: String, userAgent: String): Resource<AllTvChannelsResponse>? {
        return try {
            val request = channelsService.getInstance(url).getAllChannels(cookie, token, userAgent)
            if (request.isSuccessful) {
                if (request.body() != null) {
                    Resource.Success(request.body()!!)
                } else {
                    Resource.Error("Body is empty")
                }
            } else {
                Resource.Error("HTTP error: ${request.code()} - ${request.message()}")            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: IllegalStateException) {
            Resource.Error("Illegal State Exc.: ${e.message}")
        } catch (e: JsonSyntaxException) {
            Resource.Error("Json Syntax error: ${e.message}")
        } catch (e: JsonParseException) {
            Resource.Error("Json Parsing error: ${e.message}")
        } catch (e: NullPointerException) {
            Resource.Error("Nullpointer error: ${e.message}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getTvChannelLink(url: String, cmd: String, cookie: String, token: String, userAgent: String): Resource<TvChannelLinkResponse?> {
        return try {
            val request = channelsService.getInstance(url).getTvChannelLink(cmd, cookie, token, userAgent)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getTvCatchupLink(url: String, cmd: String, cookie: String, token: String, userAgent: String): Resource<GetTvCatchupResponse?> {
        return try {
            val request = channelsService.getInstance(url).getTvCatchup(cmd, cookie, token, userAgent)
            if (request.isSuccessful) {
                Log.d("CATCHUPREQUEST", request.body().toString())
                Resource.Success(request.body()!!)
            } else {
                Log.d("CATCHUPREQUEST", "UNKNOWN: ${request.errorBody().toString()}")
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Log.d("CATCHUPREQUEST", "IOEXC: ${e.message}")
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Log.d("CATCHUPREQUEST", "HTTPE: ${e.message}")
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Log.d("CATCHUPREQUEST", "UNKNOWN: ${e.message}")
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getAllEpg(url: String, cookie: String, token: String, userAgent: String, account: Accounts, isUpdating: Boolean, thisEpgSource: EpgSource): Resource<Boolean>? {
        val epgSourceBox = ObjectBox.store.boxFor(EpgSource::class.java)
        val epgChannelBox = ObjectBox.store.boxFor(EpgSourceChannel::class.java)
        val epgDataBox = ObjectBox.store.boxFor(EpgDataOB::class.java)
        val tvChannelBox = ObjectBox.store.boxFor(TvChannelOB::class.java)
        val epgChannelsToPut: MutableList<EpgSourceChannel> = mutableListOf()
        val channelsToPut: MutableList<TvChannelOB> = mutableListOf()
        return try {
            val request = channelsService.getInstance(url).getAllEpg(cookie, token, userAgent)
            if (request.isSuccessful) {
                var epgDataForChannel: MutableList<EpgDataOB>
                request.body()?.js?.data?.forEach { (channelId, epgItems) ->
                    Log.d("STALKER EPG", "CURRENTCHID: $channelId")
                    epgDataForChannel = mutableListOf()
                    val tvChannelQuery = tvChannelBox.query(
                        TvChannelOB_.playlistId.equal(account.id).and(
                            TvChannelOB_.channelId.equal(channelId.toInt()))).build()
                    val thisTvChannel = tvChannelQuery.findFirst()
                    Log.d("STALKER EPG", "THISCHANNEL: $thisTvChannel")
                    tvChannelQuery.close()
                    if (thisTvChannel != null) {
                        val epgChannel = EpgSourceChannel(
                            id = 0,
                            "${thisEpgSource.id}_${thisTvChannel.channelId}",
                            channelId,
                            mutableListOf(thisTvChannel.logo),
                            thisTvChannel.name,
                            thisEpgSource.id,
                            mutableListOf(
                                thisTvChannel.name,
                                thisTvChannel.editedName,
                                thisTvChannel.showingName
                            ),
                            false,
                        )
                        epgChannel.epgsource.target = thisEpgSource
                        thisTvChannel.epgChannel?.target = epgChannel
                        epgDataForChannel = epgItems.map {
                            val dateTimeString = it.time
                            val datum = extractDate(dateTimeString)
                            val epgData = EpgDataOB(
                                0,
                                "${account.id}_${it.ch_id}_${it.start_timestamp}",
                                it.id,
                                it.ch_id,
                                datum,
                                it.name,
                                "",
                                it.descr,
                                mutableListOf(),
                                mutableListOf(),
                                mutableListOf(),
                                "",
                                mutableListOf(),
                                "",
                                "",
                                "",
                                it.t_time,
                                it.t_time_to,
                                it.start_timestamp,
                                it.stop_timestamp,
                                it.mark_archive,
                                account.id.toString(),
                                thisEpgSource.id.toInt(),
                                "${thisEpgSource.id}_${it.ch_id}"
                            )
                            epgData
                        }.toMutableList()
                        if (isUpdating) {
                            val existingDataQuery = epgDataBox.query(EpgDataOB_.epgSourceId.equal(thisEpgSource.id)).build()
                            val existingDataIds = existingDataQuery.find()
                                .map { it.idByAccountData }.toSet()
                            existingDataQuery.close()

                            val iterator = epgDataForChannel.iterator()
                            while (iterator.hasNext()) {
                                val epg = iterator.next()
                                if (existingDataIds.contains(epg.idByAccountData)) {
                                    iterator.remove()
                                }
                            }
                        }
                        epgDataBox.put(epgDataForChannel)
                        epgChannelsToPut.add(epgChannel)
                        channelsToPut.add(thisTvChannel)
                    }
                }
                epgChannelBox.put(epgChannelsToPut)
                tvChannelBox.put(channelsToPut)
                Resource.Success(true)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    fun extractDate(timeString: String): String {
        // Definiere das Format des Eingabestrings
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        // Definiere das Ausgabeformat
        val outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        // Parse den Zeitstring in ein LocalDateTime Objekt
        val dateTime = LocalDateTime.parse(timeString, inputFormatter)

        // Extrahiere das LocalDate aus LocalDateTime
        val date = dateTime.toLocalDate()

        // Formatiere das LocalDate Objekt als Datum
        return date.format(outputFormatter)
    }

    suspend fun getShortEpgByChannel(url: String, channelId: String, cookie: String, token: String, userAgent: String): Resource<ShortEpgResponse?> {
        return try {
            val request = channelsService.getInstance(url).getShortEpgByChannel(channelId, cookie, token, userAgent)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getEpgByChannelByDay(url: String, cookie: String, token: String, channelId: String, date: String, page: Int, userAgent: String): Resource<EpgByChannelByDayResponse> {
        return try {
            val request = channelsService.getInstance(url).getEpgByChannelByDay(channelId, date, page, cookie, token, userAgent)

            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getMovieCategory(url: String, cookie: String, token: String, userAgent: String): Resource<MovieCategoryResponse> {
        return try {
            val request = channelsService.getInstance(url).getMovieCategory(cookie, token, userAgent)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getMoviesByCategory(url: String, cookie: String, token: String, category: String, page: Int, userAgent: String): Resource<MoviesByCategoryResponse> {
        return try {
            val request = channelsService.getInstance(url).getMoviesByCategory(category, page, "added", cookie, token, userAgent, )
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: IllegalStateException) {
            Resource.Error("Illegal State Exc.: ${e.message}")
        } catch (e: JsonSyntaxException) {
            Resource.Error("Json Syntax error: ${e.message}")
        } catch (e: JsonParseException) {
            Resource.Error("Json Parsing error: ${e.message}")
        } catch (e: NullPointerException) {
            Resource.Error("Nullpointer error: ${e.message}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun searchMoviesByCategory(url: String, cookie: String, token: String, category: String, page: Int, userAgent: String, searchTerm: String): Resource<MoviesByCategoryResponse> {
        return try {
            val request = channelsService.getInstance(url).searchMoviesByCategory(category, page, searchTerm, cookie, token, userAgent)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: IllegalStateException) {
            Resource.Error("Illegal State Exc.: ${e.message}")
        } catch (e: JsonSyntaxException) {
            Resource.Error("Json Syntax error: ${e.message}")
        } catch (e: JsonParseException) {
            Resource.Error("Json Parsing error: ${e.message}")
        } catch (e: NullPointerException) {
            Resource.Error("Nullpointer error: ${e.message}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getMovieLink(url: String, cmd: String, cookie: String, token: String, userAgent: String): Resource<MovieLinkResponse?> {
        return try {
            val request = channelsService.getInstance(url).getMovieLink(cmd, cookie, token, userAgent)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getSeriesCategory(url: String, cookie: String, token: String, userAgent: String): Resource<SeriesCategoryResponse> {
        return try {
            val request = channelsService.getInstance(url).getSeriesCategory(cookie, token, userAgent)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getSeriesByCategory(url: String, cookie: String, token: String, category: String, page: Int, userAgent: String): Resource<SeriesByCategoryResponse> {
        return try {
            val request = channelsService.getInstance(url).getSeriesByCategory(category, page, "added", cookie, token, userAgent)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: IllegalStateException) {
            Resource.Error("Illegal State Exc.: ${e.message}")
        } catch (e: JsonSyntaxException) {
            Resource.Error("Json Syntax error: ${e.message}")
        } catch (e: JsonParseException) {
            Resource.Error("Json Parsing error: ${e.message}")
        } catch (e: NullPointerException) {
            Resource.Error("Nullpointer error: ${e.message}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }


    suspend fun getSeriesDetails(url: String, cookie: String, token: String, seriesId: String, userAgent: String): Resource<List<SeriesData>> {
        return try {
            var page = 1 // start with page 1
            var totalData: List<SeriesData> = emptyList() // initialize an empty list
            while (true) { // loop until we have received all the necessary data
                val request = channelsService.getInstance(url).getSeriesDetail(seriesId, page, cookie, token, userAgent)
                if (request.isSuccessful) {
                    val responseData = request.body()?.js?.data?.toList() ?: emptyList()
                    if (responseData.isEmpty()) {
                        break // exit the loop if we have received all the data
                    }
                    totalData = totalData + responseData// add the Data objects to the existing list
                    page++ // increment the page number
                } else {
                    delay(100) // wait for 1 second before making another request
                }
            }
            Resource.Success(totalData)
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    // Erweiterungsfunktion, um parallele Verarbeitung zu ermöglichen
    suspend fun <T> Iterable<T>.forEachParallel(action: suspend (T) -> Unit) {
        coroutineScope {
            map { async { action(it) } }.awaitAll()
        }
    }

    suspend fun searchSeriesByCategory(url: String, cookie: String, token: String, category: String, page: Int, userAgent: String, searchTerm: String): Resource<SeriesByCategoryResponse> {
        return try {
            val request = channelsService.getInstance(url).searchSeriesByCategory(category, page, searchTerm, cookie, token, userAgent)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: IllegalStateException) {
            Resource.Error("Illegal State Exc.: ${e.message}")
        } catch (e: JsonSyntaxException) {
            Resource.Error("Json Syntax error: ${e.message}")
        } catch (e: JsonParseException) {
            Resource.Error("Json Parsing error: ${e.message}")
        } catch (e: NullPointerException) {
            Resource.Error("Nullpointer error: ${e.message}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

    suspend fun getSeriesLink(url: String, cmd: String, series: String, cookie: String, token: String, userAgent: String): Resource<SeriesLinkUrlResponse?> {
        return try {
            val request = channelsService.getInstance(url).getSeriesLink(cmd, series, cookie, token, userAgent)
            if (request.isSuccessful) {
                Resource.Success(request.body()!!)
            } else {
                Resource.Error(HttpException(request).message ?: "Unknown error")
            }
        } catch (e: IOException) {
            Resource.Error("Network error: ${e.message}")
        } catch (e: HttpException) {
            Resource.Error("Server error: ${e.code()} ${e.message()}")
        } catch (e: Exception) {
            Resource.Error("Unknown error: ${e.message}")
        }
    }

}