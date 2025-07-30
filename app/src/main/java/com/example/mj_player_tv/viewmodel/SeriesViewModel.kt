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

    private val _updateSerieRVRequest = MutableLiveData<Unit>()
    val updateSerieRVRequest: LiveData<Unit> = _updateSerieRVRequest

    fun requestUpdateSerieInRV() {
        _updateSerieRVRequest.value = Unit
    }

    private val _focusToSeriesRequest = MutableLiveData<Unit>()
    val focusToSeriesRequest: LiveData<Unit> = _focusToSeriesRequest

    fun requestFocusToSeries() {
        _focusToSeriesRequest.value = Unit
    }

    private val _focusToEpisode = MutableLiveData<Unit>()
    val focusToEpisode: LiveData<Unit> = _focusToEpisode

    fun requestFocusToEpisode() {
        _focusToEpisode.value = Unit
    }

    private val _updateSeriesDetail = MutableLiveData<Unit>()
    val updateSeriesDetail: LiveData<Unit> = _updateSeriesDetail

    fun updateSeriesDetail() {
        _updateSeriesDetail.value = Unit
    }

}
