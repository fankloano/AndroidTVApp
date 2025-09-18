package com.example.mj_player_tv.ui.tvguide.viewholders

import android.annotation.SuppressLint
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.databinding.RvItemTvguideEpgBinding
import dev.androidbroadcast.vbpd.viewBinding

class TvGuideEpgViewholder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val binding by viewBinding(RvItemTvguideEpgBinding::bind)

    @SuppressLint("SetTextI18n")
    fun bind(item: EpgDataOB) {

        EPGConfig.showBackgroundColorStateList?.let {
            binding.background.backgroundTintList = it
        }

        binding.background.isActivated = item.isLiveShow

        binding.root.updateLayoutParams<RecyclerView.LayoutParams> {
            marginEnd = EPGConfig.marginEnd.dpToPx
        }

        binding.ivShowImage.isVisible = EPGConfig.displayPreviewForLiveShow && item.isLiveShow

        val progressMax = Seconds.secondsBetween(item.startDate, item.endDate).seconds
        val progress = Seconds.secondsBetween(item.startDate, DateTime.now()).seconds
        binding.pbLine.isVisible = EPGConfig.isProgressVisible && binding.ivShowImage.isVisible
        binding.pbLine.max = progressMax
        binding.pbLine.progress = progress

        if (binding.ivShowImage.isVisible) {
            val options = RequestOptions().apply {
                EPGConfig.transform?.let { list ->
                    list.map {
                        transform(it)
                    }
                }
            }
            Glide.with(context)
                .load(item.showPreviewImage)
                .apply(options)
                .into(binding.ivShowImage)
        }

        val realStartDate = DateTime(item.startDate)
        val realEndDate = DateTime(item.endDate)
        val start = if (item.startDate.isBefore(startTime)) startTime else item.startDate
        val end = if (item.endDate.isAfter(endTime)) endTime else item.endDate

        binding.tvTitle.text = item.name
        binding.tvSubTitle.text =
            "${realStartDate.toString(SHOW_TIME_PATTERN)} - ${realEndDate.toString(SHOW_TIME_PATTERN)}"

        binding.showParent.updateLayoutParams<RecyclerView.LayoutParams> {
            width = getCellWidth(start, end)
        }

        binding.root.setOnClickListenerDebounce {
            item.onClick?.invoke()
        }
    }

    private val DataModel.ShowDataModel.isLiveShow: Boolean
        get() = startDate.isBeforeNow && endDate.isAfterNow
}