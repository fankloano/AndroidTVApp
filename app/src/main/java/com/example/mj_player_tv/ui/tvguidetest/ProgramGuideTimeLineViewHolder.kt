package com.example.mj_player_tv.ui.tvguidetest

import android.util.TypedValue
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.databinding.RvItemTvguideTimelineBinding

class ProgramGuideTimeLineViewHolder(
    private val binding: RvItemTvguideTimelineBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(timeData: TimeLineData) {
        binding.tvTime.text = timeData.time
        binding.llTimeLineParent.updateLayoutParams<RecyclerView.LayoutParams> {
            width = timeData.width
        }
        binding.tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, timeData.textSizeSp)
    }
}
