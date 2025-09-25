package com.example.mj_player_tv.viewmodel

import android.app.Application
import android.util.TypedValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.TvCategoryOB
import java.util.Calendar

class TvGuideViewModel(application: Application) : AndroidViewModel(application) {

    var currentFocusedTvCategory: TvCategoryOB? = null

    var currentFocusedTvAccount: Accounts? = null

    var timeLineStartSec = 0L

    private val _changePlayingChannel = MutableLiveData<ChannelPositions?>()
    val changePlayingChannel: LiveData<ChannelPositions?> = _changePlayingChannel

    fun requestchangePlayingChannel(channelPositions: ChannelPositions) {
        _changePlayingChannel.value = channelPositions
    }

    fun clearchangePlayingChannel() {
        _changePlayingChannel.value = null
    }
}
