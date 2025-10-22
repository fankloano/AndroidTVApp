package com.example.mj_player_tv.ui.epg

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.databinding.VgItemEpgProgramBinding
import com.example.mj_player_tv.ui.epg.util.EpgUtil

// ProgramBlockAdapter.kt

class EpgProgramAdapter(private val epgManager: EpgManager, private var channelName: String) :
    ListAdapter<EpgDataOB, EpgProgramAdapter.ProgramViewHolder>(ProgramDiffCallback()) {

    fun updateChannelName(newChannelName: String) {
        this.channelName = newChannelName
    }
    inner class ProgramViewHolder(private val binding: VgItemEpgProgramBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var epgProgramItemView: EpgProgramItemView? = null

        fun bind(item: EpgDataOB) {

            epgProgramItemView = itemView as EpgProgramItemView
            epgProgramItemView?.bind(item)
        }

        private val EpgDataOB.isLiveShow: Boolean
            get() = startTimestamp < System.currentTimeMillis() / 1000 && stopTimestamp > System.currentTimeMillis() / 1000

    }


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ProgramViewHolder {
        val binding = VgItemEpgProgramBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val viewHolder = ProgramViewHolder(binding)
        return viewHolder
    }

    override fun onBindViewHolder(
        holder: ProgramViewHolder,
        position: Int,
    ) {
        val show = getItem(position)
        holder.bind(show)
        holder.itemView.tag = "${show.name} + ${show.sub_title} I: ${holder.bindingAdapterPosition}"
        holder.itemView.setTag(R.id.program_data_tag, show)

    }

    class ProgramDiffCallback : DiffUtil.ItemCallback<EpgDataOB>() {
        override fun areItemsTheSame(oldItem: EpgDataOB, newItem: EpgDataOB): Boolean =
            oldItem.idByAccountData == newItem.idByAccountData

        override fun areContentsTheSame(oldItem: EpgDataOB, newItem: EpgDataOB): Boolean =
            oldItem == newItem
    }
}