package com.example.mj_player_tv.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class TvGuideViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TvGuideViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TvGuideViewModel(application) as T   // ✅ richtig: ViewModel zurückgeben
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
