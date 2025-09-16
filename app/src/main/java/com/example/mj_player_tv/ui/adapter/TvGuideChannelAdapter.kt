package com.example.mj_player_tv.ui.adapter

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgDataOB_
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.RvItemFullepgBinding
import com.example.mj_player_tv.databinding.RvItemScrollTvchannelBinding
import com.example.mj_player_tv.databinding.RvItemTvguideTvepgBinding
import com.example.mj_player_tv.ui.FullEpgFragment
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.ui.TvGuideFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.rubensousa.dpadrecyclerview.layoutmanager.PivotLayoutManager
import io.objectbox.Box
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
class TvGuideChannelAdapter(
    private val fragment: TvGuideFragment,
    private val helpViewModel: HelpViewModel
) : ListAdapter<TvChannelWithEpg, TvGuideChannelAdapter.ViewHolder>(
    SCROLLTVCHANNEL_COMPERATOR) {

    var timelineStartSec: Long = 0

    inner class ViewHolder(val binding: RvItemTvguideTvepgBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tvchannelwithepg: TvChannelWithEpg) {
            val tvChannel = tvchannelwithepg.tvChannelPosition.tvchannel.target
            val linkedEpgChannel = tvChannel.linkedEpgChannel?.target
            val epgChId = linkedEpgChannel?.chEpgId
            val image = tvChannel.logo
            val epgLogo = linkedEpgChannel?.icon?.firstOrNull()

            binding.tvChannelname.text = tvChannel.showingName

            if (tvChannel.account.target!!.useEpgLogos) {
                if (!epgLogo.isNullOrEmpty() && (linkedEpgChannel.isExternalEpg || tvChannel.alwaysUsesExternalEpg)) {
                    binding.ivChannelLogo.visibility = View.VISIBLE
                    binding.ivChannelLogo.load(epgLogo)
                } else {
                    if (image.isNotEmpty()) {
                        binding.ivChannelLogo.visibility = View.VISIBLE
                        binding.ivChannelLogo.load(image)
                    } else {
                        binding.ivChannelLogo.visibility = View.INVISIBLE
                    }
                }
            } else {
                if (image.isNotEmpty()) {
                    binding.ivChannelLogo.visibility = View.VISIBLE
                    binding.ivChannelLogo.load(image)
                } else {
                    binding.ivChannelLogo.visibility = View.INVISIBLE
                }
            }

            binding.ivCatchup.visibility = if (tvChannel.enable_tv_archive == 1) {
                View.VISIBLE
            } else {
                View.GONE
            }

            binding.ivFavorite.visibility = if (tvChannel.isFavorite) {
                View.VISIBLE
            } else {
                View.GONE
            }

            val epgAdapter = TvGuideEpgAdapter(fragment, tvchannelwithepg.tvChannelPosition, helpViewModel)
            binding.rvChannelPrograms.adapter = epgAdapter
            binding.rvChannelPrograms.layoutManager = LinearLayoutManager(binding.rvChannelPrograms.context,
                RecyclerView.HORIZONTAL, false)

            // idempotente Registrierung
            if (binding.rvChannelPrograms.getTag(R.id.tag_sync_registered) != true) {
                fragment.scrollSyncManager.register(binding.rvChannelPrograms)
            }
            val programs = tvchannelwithepg.epgList.ifEmpty {
                generateFakeEpg(
                    tvChannelPosId = tvChannel.id,
                    timelineStartSec = timelineStartSec,
                    timelineEndSec = (System.currentTimeMillis() / 1000) + 1800
                )
            }
            epgAdapter.timelineStartSec = timelineStartSec
            epgAdapter.submitList(programs)

            binding.constTvchannel.setOnKeyListener { _, keyCode, event ->
                if ((keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && event.action == KeyEvent.ACTION_DOWN) {
                    binding.rvChannelPrograms.requestFocus()
                    return@setOnKeyListener true
                }

                if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                    fragment.setFocusToAccountCategoryRV()
                    return@setOnKeyListener true
                }
                if ((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) {
                    if (bindingAdapterPosition > 0) {
                        itemView.focusSearch(View.FOCUS_UP)?.requestFocus()
                        return@setOnKeyListener true
                    }
                }
                if ((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) && event.action == KeyEvent.ACTION_DOWN) {
                    if (bindingAdapterPosition < itemCount - 1) {
                        itemView.focusSearch(View.FOCUS_DOWN)?.requestFocus()
                        return@setOnKeyListener true
                    }
                }
                return@setOnKeyListener false
            }

        }

        fun generateFakeEpg(
            timelineStartSec: Long,
            timelineEndSec: Long,
            tvChannelPosId: Long
        ): List<EpgDataOB> {
            val fakeList = mutableListOf<EpgDataOB>()
            val halfHourSec = 30 * 60
            var t = timelineStartSec - (timelineStartSec % halfHourSec) // abrunden auf halbe Stunde

            while (t < timelineEndSec) {
                val start = t
                val end = (t + halfHourSec).coerceAtMost(timelineEndSec)

                fakeList.add(
                    EpgDataOB(
                        id = tvChannelPosId,
                        idByAccountData = "$tvChannelPosId$start",
                        name = "No information",
                        startTimestamp = start,
                        stopTimestamp = end
                    )
                )

                t += halfHourSec
            }
            return fakeList
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemTvguideTvepgBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tvChannPos = getItem(position)
        holder.bind(tvChannPos)

        holder.binding.constTvchannel.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvChannelname.isSelected = hasFocus

        }
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        fragment.scrollSyncManager.register(holder.binding.rvChannelPrograms)
    }
    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        fragment.scrollSyncManager.unregister(holder.binding.rvChannelPrograms)
    }


    companion object {
        private val SCROLLTVCHANNEL_COMPERATOR = object : DiffUtil.ItemCallback<TvChannelWithEpg>() {
            override fun areItemsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg) =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg) =
                oldItem == newItem
        }
    }
}
