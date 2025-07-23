package com.example.mj_player_tv.ui.adapter


import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgSourceChannel
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.RvItemAssignChannelToEpgBinding
import com.example.mj_player_tv.databinding.RvItemCopychannelTocategoryBinding
import com.example.mj_player_tv.ui.AssingChannelToEpgFragment
import com.example.mj_player_tv.ui.CopyChannelToCategoryFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel


@UnstableApi
class CopySingleChannelToCategoryAdapter(private val onClickListener: CopySingleChannelToCategoryAdapter.OnClickListener, private val helpViewModel: HelpViewModel, private val fragment: CopyChannelToCategoryFragment) : ListAdapter<TvCategoryOB, CopySingleChannelToCategoryAdapter.ViewHolder>(
    MANAGE_EPGLIST_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemCopychannelTocategoryBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tvcategory: TvCategoryOB) {
            binding.apply {
                tvTvcategory.text = tvcategory.showingName

                binding.relLayoutItemManage.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                        return@setOnKeyListener true
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                        return@setOnKeyListener true
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        fragment.closeFragment()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemCopychannelTocategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)
        return viewHolder
    }

    @UnstableApi
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tvcategory = getItem(position)!!
        holder.bind(tvcategory)

        val cbTvCat = holder.binding.cbTvcategory

        // Klickereignis für die CheckBox behandeln
        holder.itemView.setOnClickListener {
            if (cbTvCat.isChecked) {
                cbTvCat.isChecked = false
                onClickListener.onClick(tvcategory, false)
            } else {
                cbTvCat.isChecked = true
                onClickListener.onClick(tvcategory, true)
            }
        }
    }

    companion object {
        private val MANAGE_EPGLIST_COMPERATOR = object : DiffUtil.ItemCallback<TvCategoryOB>() {
            override fun areItemsTheSame(oldItem: TvCategoryOB, newItem: TvCategoryOB) =
                oldItem.id == newItem.id


            override fun areContentsTheSame(oldItem: TvCategoryOB, newItem: TvCategoryOB) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (tvcategory: TvCategoryOB, isChecked: Boolean) -> Unit) {
        fun onClick(tvcategory: TvCategoryOB, isChecked: Boolean) = clickListener(tvcategory, isChecked)
    }

}