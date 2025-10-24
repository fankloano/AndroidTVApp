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

class EpgProgramAdapter(
    private val epgManager: EpgManager,
    private var channelName: String
) : RecyclerView.Adapter<EpgProgramAdapter.ProgramViewHolder>() {

    private val programs = mutableListOf<EpgDataOB>()

    fun setPrograms(newPrograms: List<EpgDataOB>) {
        programs.clear()
        programs.addAll(newPrograms)
        notifyDataSetChanged() // synchron
    }

    inner class ProgramViewHolder(private val binding: VgItemEpgProgramBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var epgProgramItemView: EpgProgramItemView? = null

        fun bind(item: EpgDataOB) {
            epgProgramItemView = itemView as EpgProgramItemView
            epgProgramItemView?.bind(item, epgManager.getTimeLineStart())
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgramViewHolder {
        val binding = VgItemEpgProgramBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProgramViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProgramViewHolder, position: Int) {
        val item = programs[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = programs.size
}
