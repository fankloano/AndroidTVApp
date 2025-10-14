package com.example.mj_player_tv.ui.epg

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.leanback.widget.HorizontalGridView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.VgItemEpgRowBinding
import com.example.mj_player_tv.ui.tvguide.EpgItemDecoration
import com.google.api.Distribution
import com.volkov.epgrecycler.context

// ProgramRowAdapter.kt

class EpgRowAdapter(
    private val onProgramClick: (EpgDataOB) -> Unit,
    private val synchronizer: EpgScrollSynchronizer // <-- direkt hier, kein extra Callback nötig
) : ListAdapter<TvChannelWithEpg, EpgRowAdapter.RowViewHolder>(RowDiffCallback()) {

    class RowViewHolder(private val binding: VgItemEpgRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        val horizontalGridView: CustomEpgHorizontalGridView = binding.innerProgramRow


        fun bind(row: TvChannelWithEpg, onProgramClick: (EpgDataOB) -> Unit) {
            val adapter = horizontalGridView.adapter as? EpgProgramAdapter
            if (adapter == null) {
                val newAdapter = EpgProgramAdapter(onProgramClick)
                horizontalGridView.adapter = newAdapter
                horizontalGridView.addItemDecoration(
                    EpgItemDecoration(color = binding.root.context.getColor(R.color.background_dark))
                )
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
        return RowViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onProgramClick)

        val hgv = holder.horizontalGridView
        synchronizer.registerHorizontalView(hgv)
        // Wende Offset an (synchronizer macht intern post wenn View width == 0)
        hgv.post { synchronizer.setInitialHorizontalOffset(hgv) }


        holder.itemView.tag = item.tvChannelPosition.tvchannel.target.showingName
    }

    class RowDiffCallback : DiffUtil.ItemCallback<TvChannelWithEpg>() {
        override fun areItemsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg): Boolean =
            oldItem.tvChannelPosition.catAndChannelAccount == newItem.tvChannelPosition.catAndChannelAccount

        override fun areContentsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg): Boolean =
            oldItem == newItem
    }
}
