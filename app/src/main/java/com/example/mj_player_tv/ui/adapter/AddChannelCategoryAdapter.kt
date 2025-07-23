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
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.RvItemActivatedAccountsBinding
import com.example.mj_player_tv.databinding.RvItemAddcategoryPlaylistsBinding
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class AddChannelCategoryAdapter(private val onClickListener: AddChannelCategoryAdapter.OnClickListener) : ListAdapter<TvCategoryOB, AddChannelCategoryAdapter.ViewHolder>(
    CATEGORY_COMPERATOR) {


    inner class ViewHolder(val binding: RvItemAddcategoryPlaylistsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tvcategory: TvCategoryOB) {
            binding.apply {
                tvTvaccount.text = tvcategory.showingName
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
        val tvcategory = getItem(position)!!
        holder.bind(tvcategory)
        holder.binding.rvLinearTvAccount.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvTvaccount.isSelected = hasFocus
        }

        holder.binding.rvLinearTvAccount.setOnClickListener {
            onClickListener.onClick(tvcategory)
        }
    }

    companion object {
        private val CATEGORY_COMPERATOR = object : DiffUtil.ItemCallback<TvCategoryOB>() {
            override fun areItemsTheSame(oldItem: TvCategoryOB, newItem: TvCategoryOB) =
                oldItem.idByAccountData == newItem.idByAccountData

            override fun areContentsTheSame(oldItem: TvCategoryOB, newItem: TvCategoryOB) =
                oldItem == newItem &&
                        oldItem.id == newItem.id

        }
    }

    class OnClickListener(val clickListener: (tvcategory: TvCategoryOB) -> Unit) {
        fun onClick(tvcategory: TvCategoryOB) = clickListener(tvcategory)
    }
}