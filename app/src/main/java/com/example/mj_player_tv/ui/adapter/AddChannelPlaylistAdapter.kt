package com.example.mj_player_tv.ui.adapter


import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.RvItemActivatedAccountsBinding
import com.example.mj_player_tv.databinding.RvItemAddcategoryPlaylistsBinding
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class AddChannelPlaylistAdapter(private val onClickListener: AddChannelPlaylistAdapter.OnClickListener) : ListAdapter<Accounts, AddChannelPlaylistAdapter.ViewHolder>(
    ACCOUNT_COMPERATOR) {


    inner class ViewHolder(val binding: RvItemAddcategoryPlaylistsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: Accounts) {
            binding.apply {
                tvTvaccount.text = data.name
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemAddcategoryPlaylistsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val viewHolder = ViewHolder(binding)
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val accountdata = getItem(position)!!
        holder.bind(accountdata)
        holder.binding.rvLinearTvAccount.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvTvaccount.isSelected = hasFocus
        }

        holder.binding.rvLinearTvAccount.setOnClickListener {
            onClickListener.onClick(accountdata)
        }
    }

    companion object {
        private val ACCOUNT_COMPERATOR = object : DiffUtil.ItemCallback<Accounts>() {
            override fun areItemsTheSame(oldItem: Accounts, newItem: Accounts) =
                oldItem.totalAccountData == newItem.totalAccountData

            override fun areContentsTheSame(oldItem: Accounts, newItem: Accounts) =
                oldItem == newItem &&
                        oldItem.id == newItem.id

        }
    }

    class OnClickListener(val clickListener: (account: Accounts) -> Unit) {
        fun onClick(account: Accounts) = clickListener(account)
    }
}