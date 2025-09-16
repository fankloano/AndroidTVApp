package com.example.mj_player_tv.ui.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.utils.views.ProgramTextView

class ProgramsAdapter(
    private val onProgramClick: (EpgDataOB) -> Unit
) : ListAdapter<EpgDataOB, ProgramsAdapter.ProgramViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<EpgDataOB>() {
            override fun areItemsTheSame(oldItem: EpgDataOB, newItem: EpgDataOB): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: EpgDataOB, newItem: EpgDataOB): Boolean =
                oldItem == newItem
        }
    }

    inner class ProgramViewHolder(itemView: ProgramTextView) : RecyclerView.ViewHolder(itemView) {
        fun bind(epg: EpgDataOB) {
            (itemView as ProgramTextView).apply {
                text = epg.name
                setTextSizeSp(12f)
                setPaddingRelative(12, 0, 12, 0) // kleiner Padding
                setOnClickListener { onProgramClick(epg) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgramViewHolder {
        val textView = ProgramTextView(parent.context)
        val params = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.WRAP_CONTENT,
            RecyclerView.LayoutParams.MATCH_PARENT
        )
        textView.layoutParams = params
        return ProgramViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ProgramViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}


