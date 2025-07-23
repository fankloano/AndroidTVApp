package com.example.mj_player_tv.repository

sealed class EpgUpdateProcessState {
    object Loading : EpgUpdateProcessState()
    class CurrentAccount(val message: String) : EpgUpdateProcessState()
    object Success : EpgUpdateProcessState()
    class NoData(val message: String) : EpgUpdateProcessState()
    object Error : EpgUpdateProcessState()
}

