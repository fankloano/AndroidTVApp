package com.example.mj_player_tv.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.mj_player_tv.MyApplication

class SeriesViewModel(application: Application) : AndroidViewModel(application) {

    var changeEpisodeInfoUi = false

    private val _focusRequest = MutableLiveData<Unit>()
    val focusRequest: LiveData<Unit> = _focusRequest

    fun requestFocusOnNextEpisode() {
        _focusRequest.value = Unit
    }

}
