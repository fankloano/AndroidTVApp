package com.example.mj_player_tv.ui.adapter

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.databinding.RvItemEpgtochannelSourceBinding
import com.example.mj_player_tv.ui.AssignEpgToChannelSourcesFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class EpgToChannelSourcesAdapter(private val onClickListener: EpgToChannelSourcesAdapter.OnClickListener, private val helpViewModel: HelpViewModel, private val fragment: AssignEpgToChannelSourcesFragment) : ListAdapter<EpgSource, EpgToChannelSourcesAdapter.ViewHolder>(
    EPGTOCHANNEL_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemEpgtochannelSourceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(epgSource: EpgSource) {
            binding.apply {
                binding.textEpgsourceName.text = epgSource.name
            }
            binding.rvLinearEpgSource.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                    if (bindingAdapterPosition == 0) {
                        fragment.focusToShowAll()
                        return@setOnKeyListener true
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    fragment.closeFragment()
                }
                return@setOnKeyListener false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemEpgtochannelSourceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)

        if (viewType == 0) {
            // Setze den Fokus auf das erste Element
            viewHolder.itemView.nextFocusUpId = R.id.relLayout_addpl_btn
        }

        return viewHolder
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val epgSource = getItem(position)!!
        holder.bind(epgSource)
        holder.binding.rvLinearEpgSource.setOnClickListener {
            onClickListener.onClick(epgSource)
        }
        holder.binding.rvLinearEpgSource.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.textEpgsourceName.isSelected = hasFocus
        }
    }

    companion object {
        private val EPGTOCHANNEL_COMPERATOR = object : DiffUtil.ItemCallback<EpgSource>() {
            override fun areItemsTheSame(oldItem: EpgSource, newItem: EpgSource) =
                oldItem.id == newItem.id


            override fun areContentsTheSame(oldItem: EpgSource, newItem: EpgSource) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (account: EpgSource) -> Unit) {
        fun onClick(account: EpgSource) = clickListener(account)
    }
}