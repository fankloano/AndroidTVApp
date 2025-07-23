package com.example.mj_player_tv.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.databinding.RvItemEpgsourceTimeoffsetBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel

class TimeOffSetAdapter(private val onClickListener: TimeOffSetAdapter.OnClickListener, private val helpViewModel: HelpViewModel) : ListAdapter<String, TimeOffSetAdapter.ViewHolder>(
    ACCOUNT_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemEpgsourceTimeoffsetBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(timeOffSet: String) {
            binding.apply {
                tvTimeOffSet.text = timeOffSet
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemEpgsourceTimeoffsetBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)

        return viewHolder
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val timeOffSet = getItem(position)!!
        holder.bind(timeOffSet)

        holder.binding.tvTimeOffSet.setOnClickListener {
            onClickListener.onClick(timeOffSet, position)
        }
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->

        }
    }

    companion object {
        private val ACCOUNT_COMPERATOR = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String) =
                oldItem == newItem


            override fun areContentsTheSame(oldItem: String, newItem: String) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (timeOffSet: String, position: Int) -> Unit) {
        fun onClick(timeOffSet: String, position: Int) = clickListener(timeOffSet, position)
    }
}