package com.example.mj_player_tv.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class XtreamViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(XtreamViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return XtreamViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}