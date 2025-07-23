package com.example.mj_player_tv.ui.adapter


import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.EpgSourcePositions
import com.example.mj_player_tv.databinding.RvItemManagePlaylistEpgBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel

class ManagePlaylistEpgAdapter(private val onClickListener: ManagePlaylistEpgAdapter.OnClickListener, private val helpViewModel: HelpViewModel) : ListAdapter<EpgSourcePositions, ManagePlaylistEpgAdapter.ViewHolder>(
    MANAGE_EPG_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemManagePlaylistEpgBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(epgSourcePos: EpgSourcePositions) {
            binding.apply {
                val epgSource = epgSourcePos.relatedepgsource.target
                Log.d("ERROREPGSOUIRCES", "$epgSource")
                if (epgSource.isPlaylistEpg) {
                    rvItemEpgsourcesName.text = epgSource.name
                    rvItemEpgsourcesUrl.text = epgSource.name
                } else {
                    if (epgSource.name.isNotEmpty()) {
                        rvItemEpgsourcesName.text = epgSource.name
                        rvItemEpgsourcesUrl.text = epgSource.url
                    } else {
                        rvItemEpgsourcesName.text = epgSource.url
                    }
                }
                binding.cbEpgSource.isChecked = epgSourcePos.isSelected
                binding.cbEpgSource.isActivated = epgSourcePos.isSelected
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemManagePlaylistEpgBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)

        if (viewType == 0) {
            // Setze den Fokus auf das erste Element
            viewHolder.itemView.nextFocusUpId = R.id.btn_selectAll
        }

        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val epgSource = getItem(position)!!
        holder.bind(epgSource)

        val checkbox = holder.binding.cbEpgSource
        // Set the initial checkbox status based on selectedEpgSources
        holder.binding.cbEpgSource.isChecked = epgSource.isSelected
        holder.binding.cbEpgSource.isActivated = epgSource.isSelected

        holder.binding.relLayoutPlaylistepg.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.cbEpgSource.isSelected = hasFocus
        }
        // Set the click listener
        holder.binding.relLayoutPlaylistepg.setOnClickListener {
            checkbox.isChecked = !checkbox.isChecked
            holder.binding.cbEpgSource.isActivated = checkbox.isChecked
            onClickListener.onClick(epgSource, checkbox.isChecked)
        }
    }

    companion object {
        private val MANAGE_EPG_COMPERATOR = object : DiffUtil.ItemCallback<EpgSourcePositions>() {
            override fun areItemsTheSame(oldItem: EpgSourcePositions, newItem: EpgSourcePositions) =
                oldItem.id == newItem.id


            override fun areContentsTheSame(oldItem: EpgSourcePositions, newItem: EpgSourcePositions) =
                oldItem == newItem &&
                        oldItem.isSelected == newItem.isSelected &&
                        oldItem.position == newItem.position
        }
    }

    class OnClickListener(val clickListener: (epgSources: EpgSourcePositions, isChecked: Boolean) -> Unit) {
        fun onClick(epgSources: EpgSourcePositions, isChecked: Boolean) =
            clickListener(epgSources, isChecked)
    }
}