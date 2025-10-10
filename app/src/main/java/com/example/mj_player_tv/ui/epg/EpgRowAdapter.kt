package com.example.mj_player_tv.ui.epg

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.leanback.widget.HorizontalGridView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.VgItemEpgRowBinding

// ProgramRowAdapter.kt

class EpgRowAdapter(
    private val onProgramClick: (EpgDataOB) -> Unit,
    private val onRowViewAttached: (HorizontalGridView) -> Unit,
    private val synchronizer: EpgScrollSynchronizer // ⬅️ NEU
) : ListAdapter<TvChannelWithEpg, EpgRowAdapter.RowViewHolder>(RowDiffCallback()) {

    class RowViewHolder(private val binding: VgItemEpgRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        val horizontalGridView: HorizontalGridView = binding.innerProgramRow

        init {
            // Die gesamte Zeile ist NICHT fokussierbar
            binding.root.isFocusable = false
        }

        fun bind(row: TvChannelWithEpg, onProgramClick: (EpgDataOB) -> Unit) {
            // Falls Adapter bereits gesetzt, Programme aktualisieren
            val adapter = horizontalGridView.adapter as? EpgProgramAdapter
            if (adapter == null) {
                // Erstelle den inneren Adapter nur einmal
                val newAdapter = EpgProgramAdapter(onProgramClick)
                horizontalGridView.adapter = newAdapter
                newAdapter.submitList(row.epgList)

            } else {
                adapter.submitList(row.epgList)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val binding = VgItemEpgRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        val holder = RowViewHolder(binding)

        // Melde die HorizontalGridView dem Fragment zur Scroll-Synchronisation
        onRowViewAttached(holder.horizontalGridView)

        return holder
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onProgramClick)

        synchronizer.setInitialHorizontalOffset(holder.horizontalGridView) // ⬅️ KRITISCH

        holder.itemView.tag = item.tvChannelPosition.tvchannel.target.showingName
    }

    class RowDiffCallback : DiffUtil.ItemCallback<TvChannelWithEpg>() {
        override fun areItemsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg): Boolean =
            oldItem.tvChannelPosition.catAndChannelAccount == newItem.tvChannelPosition.catAndChannelAccount

        override fun areContentsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg): Boolean =
            oldItem == newItem
    }
}