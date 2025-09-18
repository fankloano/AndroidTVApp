package com.example.mj_player_tv.ui.tvguide.adapters

import android.R.attr.width
import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.EpgDataOB
import dev.androidbroadcast.vbpd.viewBinding

class TvGuideEmptyShowViewholder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val binding by viewBinding(ItemShowEmptyBinding::bind)

    @SuppressLint("SetTextI18n")
    fun bind(item: EpgDataOB) {
        binding.llEmpty.updateLayoutParams<RecyclerView.LayoutParams> {
            width = item.width
        }
    }
}