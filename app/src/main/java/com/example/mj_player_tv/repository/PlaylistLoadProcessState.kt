package com.example.mj_player_tv.repository

import com.example.mj_player_tv.database.entity.Accounts

sealed class PlaylistLoadProcessState {
    object Loading : PlaylistLoadProcessState()
    class GetToken(val progress: Int) : PlaylistLoadProcessState()
    class GetTvCategories(val progress: Int, val message: String) : PlaylistLoadProcessState()
    class GetMovieCategories(val progress: Int, val message: String, val totalMovies: String) : PlaylistLoadProcessState()
    class GetSeriesCategories(val progress: Int, val message: String, val totalSeries: String) : PlaylistLoadProcessState()
    class GetChannels(val progress: Int, val message: String) : PlaylistLoadProcessState()
    class GetEpg(val progress: Int, val message: String) : PlaylistLoadProcessState()
    class Success(val accountData: Accounts) : PlaylistLoadProcessState()
    class TokenError(val message: String) : PlaylistLoadProcessState()
    class ChannelsError(val message: String) : PlaylistLoadProcessState()
    class TvError(val message: String) : PlaylistLoadProcessState()
    class MovieError(val message: String) : PlaylistLoadProcessState()
    class SeriesError(val message: String) : PlaylistLoadProcessState()
    class EpgError(val message: String) : PlaylistLoadProcessState()
    class Error(val message: String) : PlaylistLoadProcessState()
}

