package com.example.mj_player_tv.ui.adapter


import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpgSourcePositions
import com.example.mj_player_tv.databinding.RvItemPriorPlaylistEpgBinding
import com.example.mj_player_tv.ui.settings.PlaylistEpgPriorFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

class PriorPlaylistEpgAdapter(
    private val helpViewModel: HelpViewModel,
    private val currentAccount: Accounts,
    private val fragment: PlaylistEpgPriorFragment
) : ListAdapter<EpgSourcePositions, PriorPlaylistEpgAdapter.ViewHolder>(
    PRIOR_EPG_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemPriorPlaylistEpgBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(epgSourcePos: EpgSourcePositions) {
            val epgSource = epgSourcePos.relatedepgsource.target
            var startedPosition: Int? = null

            binding.apply {
                if (epgSource.name.isNotEmpty()) {
                    rvItemEpgsourcesName.text = epgSource.name
                    rvItemEpgsourcesUrl.text = if (epgSource.isPlaylistEpg) {
                        epgSource.name
                    } else {
                        epgSource.url
                    }
                } else {
                    rvItemEpgsourcesName.text = epgSource.url
                    rvItemEpgsourcesUrl.visibility = View.INVISIBLE
                }
            }

            binding.relLayoutPlaylistepg.setOnKeyListener {  _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                    if (binding.relLayoutPlaylistepg.isSelected) {
                        moveItemUp(bindingAdapterPosition)
                        return@setOnKeyListener true
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                    if (binding.root.isSelected && bindingAdapterPosition != (itemCount - 1)) {
                        moveItemDown(bindingAdapterPosition)
                        return@setOnKeyListener true
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                    if (binding.relLayoutPlaylistepg.isSelected) {
                        if (bindingAdapterPosition != startedPosition) {
                            binding.relLayoutPlaylistepg.isSelected = false
                            fragment.refreshEpgPriority(currentAccount, epgSourcePos, bindingAdapterPosition)
                            return@setOnKeyListener true
                        } else {
                            binding.relLayoutPlaylistepg.isSelected = false
                            startedPosition = null
                            fragment.setFocusToRecyclerview()
                            return@setOnKeyListener true
                        }
                    } else {
                        startedPosition = null
                        startedPosition = bindingAdapterPosition
                        binding.relLayoutPlaylistepg.isSelected = true
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    if (binding.relLayoutPlaylistepg.isSelected) {
                        binding.relLayoutPlaylistepg.isSelected = false
                        startedPosition = null
                        fragment.setFocusToRecyclerview()
                        return@setOnKeyListener true
                    } else {
                        fragment.closeFragment()
                        return@setOnKeyListener true
                    }
                }
                return@setOnKeyListener false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemPriorPlaylistEpgBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)


        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val epgSource = getItem(position)!!
        holder.bind(epgSource)
    }

    companion object {
        private val PRIOR_EPG_COMPERATOR = object : DiffUtil.ItemCallback<EpgSourcePositions>() {
            override fun areItemsTheSame(oldItem: EpgSourcePositions, newItem: EpgSourcePositions) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: EpgSourcePositions, newItem: EpgSourcePositions) =
                oldItem.id == newItem.id &&
                        oldItem.position == newItem.position
        }
    }

    fun moveItemUp(selectedItemPosition: Int) {
        if (selectedItemPosition > 0) {
            notifyItemMoved(selectedItemPosition, selectedItemPosition - 1)
        }
    }

    fun moveItemDown(selectedItemPosition: Int) {
        if (selectedItemPosition < itemCount - 1) {
            notifyItemMoved(selectedItemPosition, selectedItemPosition + 1)
        }
    }
}