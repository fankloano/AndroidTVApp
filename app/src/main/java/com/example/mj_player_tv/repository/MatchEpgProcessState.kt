package com.example.mj_player_tv.repository

sealed class MatchEpgProcessState {
    object Loading : MatchEpgProcessState()
    class Matching(val totalEpgChannels: Int, val checkedEpgChannels: Int) : MatchEpgProcessState()
    class okMatching(val tvChannel: String, val EpgChannel: String) : MatchEpgProcessState()
    object Success : MatchEpgProcessState()
    class Error(val message: String) : MatchEpgProcessState()
}
