package com.example.mj_player_tv.ui.adapter


import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.SeasonsOB
import com.example.mj_player_tv.database.help.Season
import com.example.mj_player_tv.databinding.RvItemSeasonsBinding
import com.example.mj_player_tv.ui.SeriesDetailFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class SeasonsAdapter(private val recyclerView: RecyclerView, private val onClickListener: SeasonsAdapter.OnClickListener, private val onLongClickListener: SeasonsAdapter.OnLongClickListener, private val helpViewModel: HelpViewModel, private val fragment: SeriesDetailFragment) : ListAdapter<SeasonsOB, SeasonsAdapter.ViewHolder>(
    ACCOUNT_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemSeasonsBinding, val helpViewModel: HelpViewModel) : RecyclerView.ViewHolder(binding.root) {
        fun bind(season: SeasonsOB) {
            binding.apply {
                tvSeasonName.text = if (season.seasonNumber != "0") {
                    "Season ${season.seasonNumber}"
                } else {
                    season.seasonsName
                }

                Log.d("SEASON ADAPTER", "${season.seasonNumber} = PARTLY: ${season.isSeasonPartlyWatched} FULL: ${season.isSeasonFullyWatched}")

                if (season.isSeasonFullyWatched) {
                    binding.ivSeasonPartlywatched.visibility = View.GONE
                    binding.ivSeasonFullywatched.visibility = View.VISIBLE
                }

                if (season.isSeasonPartlyWatched) {
                    binding.ivSeasonFullywatched.visibility = View.GONE
                    binding.ivSeasonPartlywatched.visibility = View.VISIBLE
                }
                if (!season.isSeasonFullyWatched && !season.isSeasonPartlyWatched) {
                    binding.ivSeasonFullywatched.visibility = View.GONE
                    binding.ivSeasonPartlywatched.visibility = View.GONE
                }
            }
            binding.constSeason.setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    if (bindingAdapterPosition == 0) {
                        fragment.focusToPlayButton()
                        return@setOnKeyListener true
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    // Rufen Sie die Funktion im Fragment auf
                    fragment.closeFragment()
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                    // Rufen Sie die Funktion im Fragment auf
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                    // Rufen Sie die Funktion im Fragment auf
                    onClickListener.onClick(season)
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemSeasonsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val viewHolder = ViewHolder(binding, helpViewModel)
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val season = getItem(position)!!
        holder.bind(season)
        holder.binding.constSeason.isSelected = season.seriesSeasonIdByAccountData == helpViewModel.currentFocusedSeason?.seriesSeasonIdByAccountData
        holder.binding.constSeason.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                fragment.showEpisodesForSeason(season)
            }
        }

        holder.binding.constSeason.setOnClickListener {
            onClickListener.onClick(season)
        }

        holder.binding.constSeason.setOnLongClickListener {
            onLongClickListener.onLongClick(season, holder.binding.constSeason)
            true
        }
    }

    companion object {
        private val ACCOUNT_COMPERATOR = object : DiffUtil.ItemCallback<SeasonsOB>() {
            override fun areItemsTheSame(oldItem: SeasonsOB, newItem: SeasonsOB) =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: SeasonsOB, newItem: SeasonsOB) =
                oldItem.seriesSeasonIdByAccountData == newItem.seriesSeasonIdByAccountData &&
                        oldItem.seasonPercentagePlayed == newItem.seasonPercentagePlayed &&
                        oldItem.isSeasonPartlyWatched == newItem.isSeasonPartlyWatched &&
                        oldItem.isSeasonFullyWatched == newItem.isSeasonFullyWatched
        }
    }

    class OnClickListener(val clickListener: (season: SeasonsOB) -> Unit) {
        fun onClick(season: SeasonsOB) = clickListener(season)
    }

    class OnLongClickListener(val longClickListener: (season: SeasonsOB, view: View) -> Unit) {
        fun onLongClick(season: SeasonsOB, view: View) = longClickListener(season, view)
    }
}