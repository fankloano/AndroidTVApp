package com.example.mj_player_tv.repository

sealed class ExternEpgProcessState {
    object Loading : ExternEpgProcessState()
    object Parsing : ExternEpgProcessState()
    class ParsingFinished(val message: String) : ExternEpgProcessState()
    object EpgDataToDatabase : ExternEpgProcessState()
    class EpgDataToDatabaseFinished(val message: String) : ExternEpgProcessState()
    object Success : ExternEpgProcessState()
    class Error(val message: String) : ExternEpgProcessState()
}