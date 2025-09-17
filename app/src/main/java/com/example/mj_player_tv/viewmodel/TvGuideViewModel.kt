package com.example.mj_player_tv.viewmodel

import android.app.Application
import android.util.TypedValue
import androidx.lifecycle.AndroidViewModel
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.TvCategoryOB
import java.util.Calendar

class TvGuideViewModel(application: Application) : AndroidViewModel(application) {

    var currentFocusedTvCategory: TvCategoryOB? = null

    var currentFocusedTvAccount: Accounts? = null

    var timeLineStartSec = 0L
}
