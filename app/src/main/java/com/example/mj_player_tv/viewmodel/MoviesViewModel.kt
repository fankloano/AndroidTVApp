package com.example.mj_player_tv.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class MoviesViewModel(application: Application) : AndroidViewModel(application) {

    private val _focusRequest = MutableLiveData<Unit?>()
    val focusRequest: LiveData<Unit?> = _focusRequest

    fun requestFocusOnPlayMovie() {
        _focusRequest.value = Unit
    }

    fun clearFocusOnPlayMovie() {
        _focusRequest.value = null
    }

    private val _updateMovieRVRequest = MutableLiveData<Unit?>()
    val updateMovieRVRequest: LiveData<Unit?> = _updateMovieRVRequest

    fun requestUpdateMovieInRV() {
        _updateMovieRVRequest.value = Unit
    }

    fun clearFocusOnMovieRV() {
        _updateMovieRVRequest.value = null
    }

    private val _focusToMoviesRequest = MutableLiveData<Unit?>()
    val focusToMoviesRequest: LiveData<Unit?> = _focusToMoviesRequest

    fun requestFocusToMovies() {
        _focusToMoviesRequest.value = Unit
    }

    fun clearFocusToMovies() {
        _focusToMoviesRequest.value = null
    }

}
