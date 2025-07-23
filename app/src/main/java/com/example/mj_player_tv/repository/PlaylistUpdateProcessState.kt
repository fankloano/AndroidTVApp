package com.example.mj_player_tv.repository

sealed class PlaylistUpdateProcessState {
    class CurrentAccount(val message: String) : PlaylistUpdateProcessState()
    object Success : PlaylistUpdateProcessState()
    class NoData(val message: String) : PlaylistUpdateProcessState()
    object Error : PlaylistUpdateProcessState()
}

