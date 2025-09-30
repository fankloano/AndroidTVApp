package com.example.mj_player_tv.ui.tvguide.viewholders

import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.databinding.RvItemTvguideTimelineBinding
import com.volkov.epgrecycler.EPGUtils
import dev.androidbroadcast.vbpd.viewBinding

class TimeLineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val binding by viewBinding(RvItemTvguideTimelineBinding::bind)

    fun bind(item: TimeLineData) {

        binding.tvTime.text = item.time
        // Setzt die Breite des gesamten ViewHolders
        binding.llTimeLineParent.updateLayoutParams<RecyclerView.LayoutParams> {
            width = item.width
        }
        binding.line.updateLayoutParams<LinearLayout.LayoutParams> { gravity = item.gravity }
        binding.tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, item.textSizeSp)
    }
}