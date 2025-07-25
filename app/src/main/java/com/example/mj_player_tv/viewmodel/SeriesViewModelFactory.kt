package com.example.mj_player_tv.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SeriesViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SeriesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SeriesViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}