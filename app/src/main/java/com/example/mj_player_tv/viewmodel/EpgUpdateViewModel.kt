package com.example.mj_player_tv.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.mj_player_tv.database.help.EpgUpdate
import com.example.mj_player_tv.database.help.PlaylistUpdate
import com.example.mj_player_tv.repository.EpgUpdateProcessState
import com.example.mj_player_tv.repository.EpgUpdateRepository
import com.example.mj_player_tv.repository.PlaylistUpdateProcessState
import com.example.mj_player_tv.repository.PlaylistUpdateRepository
import com.example.mj_player_tv.repository.XtreamUpdateRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EpgUpdateViewModel(application: Application): AndroidViewModel(application) {

    private val _epgUpdateState = MutableLiveData<List<EpgUpdate?>>()
    val epgUpdateState: LiveData<List<EpgUpdate?>> get() = _epgUpdateState

    init {
        viewModelScope.launch {
            EpgUpdateRepository.epgUpdateProcessState.collect { state ->
                val uiModels = state.mapNotNull { (accountName, status) ->
                    EpgUpdate(accountName, status)
                }
                _epgUpdateState.value = uiModels
            }
        }
    }

    fun removeEpgAfterDelay(epgName: String, delayMillis: Long = 3000L) {
        viewModelScope.launch {
            delay(delayMillis) // 2-3 Sekunden Verzögerung
            EpgUpdateRepository.removeEpgSource(epgName)
        }
    }

}

