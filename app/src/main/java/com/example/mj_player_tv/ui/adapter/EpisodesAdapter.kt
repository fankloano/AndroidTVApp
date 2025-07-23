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
import coil.load
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpisodesOB
import com.example.mj_player_tv.database.entity.SeasonsOB
import com.example.mj_player_tv.database.help.Episode
import com.example.mj_player_tv.database.help.Season
import com.example.mj_player_tv.database.help.Serie
import com.example.mj_player_tv.databinding.RvItemActivatedAccountsBinding
import com.example.mj_player_tv.databinding.RvItemEpisodeBinding
import com.example.mj_player_tv.databinding.RvItemSeasonsBinding
import com.example.mj_player_tv.ui.MoviesFragment
import com.example.mj_player_tv.ui.SeriesDetailFragment
import com.example.mj_player_tv.ui.SeriesFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class EpisodesAdapter(private val onClickListener: EpisodesAdapter.OnClickListener, private val onLongClickListener: EpisodesAdapter.OnLongClickListener, private val helpViewModel: HelpViewModel, private val fragment: SeriesDetailFragment) : ListAdapter<EpisodesOB, EpisodesAdapter.ViewHolder>(
    ACCOUNT_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemEpisodeBinding, val helpViewModel: HelpViewModel) : RecyclerView.ViewHolder(binding.root) {
        fun bind(episode: EpisodesOB) {
            binding.apply {
                Log.d("THIS EPISODE INFO", "$episode")
                rvItemOrginalnameEpisodes.text = episode.episodeName
                rvItemDurationEpisode.text = episode.episodeTime
                rvItemLogoEpisodes.load(episode.episodeImg)
                rvItemNameEpisodes.text = "Episode ${episode.episodeNumber}"
                // Fortschritt berechnen
                val progressPercentage = (episode.episodePercentagePlayed * 100).toInt()

                // Fortschritt setzen
                if (progressPercentage != 100) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = progressPercentage
                } else {
                    binding.progressBar.visibility = View.INVISIBLE
                }
                if (episode.episodePercentagePlayed > 0.0) {
                    if (episode.episodePercentagePlayed == 1.0) {
                        binding.tvIsFullyWatched.visibility = View.VISIBLE
                        binding.tvPercentagePlayed.text = ""
                        binding.tvPercentagePlayed.visibility = View.INVISIBLE
                    } else {
                        binding.tvIsFullyWatched.visibility = View.INVISIBLE
                        binding.tvPercentagePlayed.visibility = View.VISIBLE
                        binding.tvPercentagePlayed.text = "$progressPercentage%"
                    }
                } else {
                    binding.tvPercentagePlayed.visibility = View.INVISIBLE
                    binding.tvIsFullyWatched.visibility = View.INVISIBLE
                }
                if (episode.episodeTime.isNullOrEmpty() || episode.episodeTime == "0") {
                    binding.rvItemDurationEpisode.text = "N/A"
                } else {
                    binding.rvItemDurationEpisode.text = "${episode.episodeTime}min"
                }
            }
            binding.cardEpisode.setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    // Rufen Sie die Funktion im Fragment auf
                    fragment.focusToPreviousSeason()
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    // Rufen Sie die Funktion im Fragment auf
                    fragment.closeFragment()
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    fragment.focusToNextSeason()
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                    if (bindingAdapterPosition == 0) {
                        fragment.focusToSeasons()
                        return@setOnKeyListener true
                    }
                }
                return@setOnKeyListener false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemEpisodeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val viewHolder = ViewHolder(binding, helpViewModel)
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val episode = getItem(position)!!
        holder.bind(episode)

        holder.binding.cardEpisode.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.rvItemOrginalnameEpisodes.isSelected = hasFocus
            if (hasFocus) {
                holder.binding.alphaView.visibility = View.INVISIBLE
                fragment.showFocusedEpisodeInfos(episode)
            } else {
                holder.binding.alphaView.visibility = View.VISIBLE
            }
        }

        holder.binding.cardEpisode.setOnClickListener {
            holder.binding.alphaView.visibility = View.INVISIBLE
            onClickListener.onClick(episode)
        }

        holder.binding.cardEpisode.setOnLongClickListener {
            onLongClickListener.onLongClick(episode, holder.binding.cardEpisode, position)
            true
        }

    }

    companion object {
        private val ACCOUNT_COMPERATOR = object : DiffUtil.ItemCallback<EpisodesOB>() {
            override fun areItemsTheSame(oldItem: EpisodesOB, newItem: EpisodesOB) =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: EpisodesOB, newItem: EpisodesOB) =
                oldItem.seriesSeasonEpisodeIdByAccountData == newItem.seriesSeasonEpisodeIdByAccountData &&
                        oldItem.isEpisodeFullyWatched == newItem.isEpisodeFullyWatched &&
                        oldItem.isEpisodePartlyWatched == newItem.isEpisodePartlyWatched &&
                        oldItem.currentPosition == newItem.currentPosition &&
                        oldItem.episodePercentagePlayed == newItem.episodePercentagePlayed
        }
    }

    class OnClickListener(val clickListener: (episode: EpisodesOB) -> Unit) {
        fun onClick(episode: EpisodesOB) = clickListener(episode)
    }

    class OnLongClickListener(val longClickListener: (episode: EpisodesOB, view: View, position: Int) -> Unit) {
        fun onLongClick(episode: EpisodesOB, view: View, position: Int) = longClickListener(episode, view, position)
    }
}