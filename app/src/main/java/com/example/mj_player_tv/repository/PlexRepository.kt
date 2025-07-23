package com.example.mj_player_tv.repository

import android.app.Application
import android.util.Log
import com.example.mj_player_tv.network.RetrofitInstance
import com.example.mj_player_tv.network.model.plex.PlexSignInResponse
import com.example.mj_player_tv.network.model.plex.items.PlexGetSectionItems
import com.example.mj_player_tv.network.model.plex.library.PlexGetUserLibraries
import com.example.mj_player_tv.network.model.plex.resources.PlexGetUserResources
import com.example.mj_player_tv.network.model.stalker.token.TokenResponse
import com.example.mj_player_tv.network.model.xtreamcodes.XtreamAuthentication
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
import retrofit2.HttpException
import java.io.IOException

class PlexRepository() {
    private val channelsService = RetrofitInstance

    suspend fun getPlexAuthentication(email: String, password: String): Resource<PlexSignInResponse> {
        return try {
            val request = channelsService.getInstance("https://plex.tv/").postPlexSignIn(email, password)
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

    suspend fun getPlexUserResources(authToken: String): Resource<List<PlexGetUserResources>> {
        return try {
            val request = channelsService.getInstance("https://plex.tv/").getPlexUserResources(authToken)
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

    suspend fun getPlexServerLibrarySections(url: String, authToken: String): Resource<PlexGetUserLibraries> {
        return try {
            val request = channelsService.getInstance(url).getPlexUserLibraries(authToken)
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

    suspend fun getPlexSameItem(url: String, authToken: String, sectionId: Int, guid: String): Resource<PlexGetSectionItems> {
        return try {
            val request =
                channelsService.getInstance(url).getPlexSameItems(sectionId, authToken, guid)
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

        suspend fun updateProgress(
            url: String,
            ratingKey: String,
            currentTimeMs: Long,
            state: String,
            token: String
        ): Resource<Unit> {
            return try {
                val response = channelsService.getInstance(url).updateItemProgress(
                    ratingKey = ratingKey,
                    currentTimeMs = currentTimeMs,
                    state = state,
                    token = token
                )
                if (response.isSuccessful) {
                    Resource.Success(Unit)
                } else {
                    Resource.Error(Exception("API error: ${response.code()}").toString())
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "")
            }
        }

    suspend fun markasWatched(
        url: String,
        ratingKey: String,
        token: String
    ): Resource<Unit> {
        return try {
            val response = channelsService.getInstance(url).markItemAsWatched(
                ratingKey = ratingKey,
                token = token
            )
            if (response.isSuccessful) {
                Resource.Success(Unit)
            } else {
                Resource.Error(Exception("API error: ${response.code()}").toString())
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "")
        }
    }

    suspend fun markasNotWatched(
        url: String,
        ratingKey: String,
        token: String
    ): Resource<Unit> {
        return try {
            val response = channelsService.getInstance(url).markItemAsNotWatched(
                ratingKey = ratingKey,
                token = token
            )
            if (response.isSuccessful) {
                Resource.Success(Unit)
            } else {
                Resource.Error(Exception("API error: ${response.code()}").toString())
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "")
        }
    }

    suspend fun getPlexWatchlist(authToken: String): Resource<PlexGetSectionItems> {
        return try {
            val request =
                channelsService.getInstance("https://metadata.provider.plex.tv").getPlexWatchlist(authToken)
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

    suspend fun addToWatchlist(
        guidKey: String,
        token: String
    ): Resource<Unit> {
        return try {
            val response = channelsService.getInstance("https://metadata.provider.plex.tv/").addItemToWatchlist(
                ratingKey = guidKey,
                token = token
            )
            if (response.isSuccessful) {
                Resource.Success(Unit)
            } else {
                Resource.Error(Exception("API error: ${response.code()}").toString())
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "")
        }
    }

    suspend fun removeFromWatchlist(
        guidKey: String,
        token: String
    ): Resource<Unit> {
        return try {
            val response = channelsService.getInstance("https://metadata.provider.plex.tv/").removeItemFromWatchlist(
                ratingKey = guidKey,
                token = token
            )
            if (response.isSuccessful) {
                Resource.Success(Unit)
            } else {
                Resource.Error(Exception("API error: ${response.code()}").toString())
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "")
        }
    }
}