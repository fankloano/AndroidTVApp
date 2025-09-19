package com.example.mj_player_tv.ui.tvguide.viewholders

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.databinding.RvItemTvguideEpgBinding
import com.volkov.epg_recycler.databinding.ItemShowEmptyBinding
import dev.androidbroadcast.vbpd.viewBinding

class TvGuideEmptyShowViewholder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val binding by viewBinding(RvItemTvguideEpgBinding::bind)

    @SuppressLint("SetTextI18n")
    fun bind(item: EpgDataOB) {

    }
}