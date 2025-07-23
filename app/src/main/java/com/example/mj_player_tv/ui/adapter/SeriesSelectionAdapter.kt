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
import com.example.mj_player_tv.database.help.TrackInfo
import com.example.mj_player_tv.databinding.RvItemMovieSelectionBinding
import com.example.mj_player_tv.ui.PlayMovieFragment
import com.example.mj_player_tv.ui.PlaySeriesFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class SeriesSelectionAdapter(private val onClickListener: SeriesSelectionAdapter.OnClickListener, private val fragment: PlaySeriesFragment, private val helpViewModel: HelpViewModel) : ListAdapter<TrackInfo, SeriesSelectionAdapter.ViewHolder>(
    ACCOUNT_COMPERATOR) {

    var currentSelected = ""

    inner class ViewHolder(val binding: RvItemMovieSelectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(selection: TrackInfo) {
            binding.apply {
                tvSelection.text = selection.trackName
            }

            binding.relLayoutMovieSelector.setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    if (helpViewModel.movieSelectionOption == 0) {
                        fragment.focusToAudioFromSelection()
                        return@setOnKeyListener true
                    } else if (helpViewModel.movieSelectionOption == 1) {
                        fragment.focusToSubTitleFromSelection()
                        return@setOnKeyListener true
                    } else if (helpViewModel.movieSelectionOption == 2) {
                        fragment.focusToVideoFromSelection()
                        return@setOnKeyListener true
                    } else {
                        fragment.focusToAspectRatioFromSelection()
                        return@setOnKeyListener true
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN && helpViewModel.movieSelectionOption == 3) {
                    if (bindingAdapterPosition == 0) {
                        fragment.setFocusToResetAspectRatio()
                        return@setOnKeyListener true
                    } else {
                        return@setOnKeyListener false
                    }
                }
                return@setOnKeyListener false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemMovieSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)

        return viewHolder
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val selection = getItem(position)!!
        holder.bind(selection)

        holder.binding.cbVodSelection.isActivated = selection.isSelected
        holder.binding.cbVodSelection.isChecked = selection.isSelected

        holder.binding.ivSupport.visibility = if (selection.isSupported) {
            View.INVISIBLE
        } else {
            View.VISIBLE
        }

        holder.binding.relLayoutMovieSelector.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvSelection.isSelected = hasFocus
            holder.binding.cbVodSelection.isSelected = hasFocus
        }

        holder.binding.relLayoutMovieSelector.setOnClickListener {
            if (!selection.isSelected) {
                selection.isSelected = true
                setSelectedTrackInfo(selection, position)
                onClickListener.onClick(selection)
            } else {
                return@setOnClickListener
            }
        }
    }

    companion object {
        private val ACCOUNT_COMPERATOR = object : DiffUtil.ItemCallback<TrackInfo>() {
            override fun areItemsTheSame(oldItem: TrackInfo, newItem: TrackInfo) =
                oldItem == newItem


            override fun areContentsTheSame(oldItem: TrackInfo, newItem: TrackInfo) =
                oldItem.isSelected == newItem.isSelected
        }
    }

    class OnClickListener(val clickListener: (selection: TrackInfo) -> Unit) {
        fun onClick(selection: TrackInfo) = clickListener(selection)
    }

    fun setSelectedTrackInfo(newSelection: TrackInfo, position: Int) {
        val oldTrack = currentList.firstOrNull { it.trackName == currentSelected }
        oldTrack?.isSelected = false
        currentSelected = newSelection.trackName
        newSelection.isSelected = true
        val oldTrackPosition = currentList.indexOf(oldTrack)
        notifyItemChanged(oldTrackPosition)
        notifyItemChanged(position)
    }
}