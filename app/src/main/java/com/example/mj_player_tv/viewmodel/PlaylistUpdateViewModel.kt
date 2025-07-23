package com.example.mj_player_tv.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.mj_player_tv.database.help.PlaylistUpdate
import com.example.mj_player_tv.repository.EpgUpdateProcessState
import com.example.mj_player_tv.repository.EpgUpdateRepository
import com.example.mj_player_tv.repository.PlaylistUpdateProcessState
import com.example.mj_player_tv.repository.PlaylistUpdateRepository
import com.example.mj_player_tv.repository.XtreamUpdateRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlaylistUpdateViewModel(application: Application): AndroidViewModel(application) {

    private val _playlistUpdateState = MutableLiveData<List<PlaylistUpdate?>>()
    val playlistUpdateState: LiveData<List<PlaylistUpdate?>> get() = _playlistUpdateState

    init {
        viewModelScope.launch {
            PlaylistUpdateRepository.playlistUpdateProcessState.collect { state ->
                // Transformiere die Map in eine Liste
                val uiModels = state.mapNotNull { (accountName, status) ->
                    PlaylistUpdate(accountName, status.playlistId, status.playlistStatus)
                }
                _playlistUpdateState.value = uiModels
            }
        }
    }

    fun removePlaylistAfterDelay(accountName: String, delayMillis: Long = 3000L) {
        viewModelScope.launch {
            delay(delayMillis) // 2-3 Sekunden Verzögerung
            PlaylistUpdateRepository.removePlaylist(accountName)
        }
    }

}

