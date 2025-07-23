package com.example.mj_player_tv.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.databinding.RvItemAutoupdateIntervalBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel

class AutoUpdateIntervalAdapter(private val onClickListener: AutoUpdateIntervalAdapter.OnClickListener, private val helpViewModel: HelpViewModel) : ListAdapter<String, AutoUpdateIntervalAdapter.ViewHolder>(
    ACCOUNT_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemAutoupdateIntervalBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(interval: String) {
            binding.apply {
                if (interval == "168") {
                    tvAutoUpdateInterval.text = "7 days"
                } else if (interval == "144") {
                    tvAutoUpdateInterval.text = "6 days"
                } else if (interval == "120") {
                    tvAutoUpdateInterval.text = "5 days"
                } else if (interval == "96") {
                    tvAutoUpdateInterval.text = "4 days"
                } else if (interval == "72") {
                    tvAutoUpdateInterval.text = "3 days"
                } else if (interval == "48") {
                    tvAutoUpdateInterval.text = "2 days"
                } else if (interval == "0") {
                    tvAutoUpdateInterval.text = "${interval}h (Off)"
                } else {
                        tvAutoUpdateInterval.text = "${interval} hours"
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemAutoupdateIntervalBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)

        return viewHolder
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val interval = getItem(position)!!
        holder.bind(interval)

        holder.binding.tvAutoUpdateInterval.setOnClickListener {
            onClickListener.onClick(interval, position)
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

    class OnClickListener(val clickListener: (interval: String, position: Int) -> Unit) {
        fun onClick(interval: String, position: Int) = clickListener(interval, position)
    }
}