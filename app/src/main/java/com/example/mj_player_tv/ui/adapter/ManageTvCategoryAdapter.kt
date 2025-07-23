package com.example.mj_player_tv.ui.adapter


import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.databinding.RvItemManageTvcategoryBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel

class ManageTvCategoryAdapter(private val onClickListener: ManageTvCategoryAdapter.OnClickListener, private val helpViewModel: HelpViewModel) : ListAdapter<TvCategoryOB, ManageTvCategoryAdapter.ViewHolder>(
    MANAGE_TVCATEGORY_COMPERATOR) {

    var addChannelsToUserCategory = false

    inner class ViewHolder(val binding: RvItemManageTvcategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: TvCategoryOB) {
            binding.apply {
                if (addChannelsToUserCategory) {
                    cbTvCategory.visibility = View.GONE
                } else {
                    cbTvCategory.visibility = View.VISIBLE
                    cbTvCategory.isChecked = category.favorite
                    cbTvCategory.isActivated = category.favorite
                }
                tvTvcategory.text = category.title

                ivNew.visibility = if (category.newCategory) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
            binding.root.setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemManageTvcategoryBinding.inflate(
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
        val tvcategory = getItem(position)!!
        holder.bind(tvcategory)
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = true


        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvTvcategory.isSelected = hasFocus
            holder.binding.cbTvCategory.isSelected = hasFocus
        }

        if (!helpViewModel.addChannelsToUserCategory) {
            val checkbox = holder.binding.cbTvCategory
            holder.itemView.setOnClickListener {
                checkbox.isChecked = !checkbox.isChecked
                if (checkbox.isChecked) {
                    tvcategory.favorite = true
                    onClickListener.onClick(tvcategory)
                }
                if (!checkbox.isChecked) {
                    tvcategory.favorite = false
                    onClickListener.onClick(tvcategory)
                }
            }
        } else {
            holder.itemView.setOnClickListener {
                onClickListener.onClick(tvcategory)
            }
        }
    }

    companion object {
        private val MANAGE_TVCATEGORY_COMPERATOR = object : DiffUtil.ItemCallback<TvCategoryOB>() {
            override fun areItemsTheSame(oldItem: TvCategoryOB, newItem: TvCategoryOB) =
                oldItem.idByAccountData == newItem.idByAccountData


            override fun areContentsTheSame(oldItem: TvCategoryOB, newItem: TvCategoryOB) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (tvcategory: TvCategoryOB) -> Unit) {
        fun onClick(tvcategory: TvCategoryOB) = clickListener(tvcategory)
    }
}