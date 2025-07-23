package com.example.mj_player_tv.ui.adapter


import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.SeriesCategoryOB
import com.example.mj_player_tv.databinding.RvItemManageSeriescategoryBinding

class ManageSeriesCategoryAdapter(private val onClickListener: ManageSeriesCategoryAdapter.OnClickListener) : ListAdapter<SeriesCategoryOB, ManageSeriesCategoryAdapter.ViewHolder>(
    MANAGE_TVCATEGORY_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemManageSeriescategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(seriesCategory: SeriesCategoryOB) {
            binding.apply {
                tvSeriescategory.text = seriesCategory.title
                cbSeriesCategory.isChecked = seriesCategory.favorite
                cbSeriesCategory.isActivated = seriesCategory.favorite

                ivNew.visibility = if (seriesCategory.newCategory) {
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
        val binding = RvItemManageSeriescategoryBinding.inflate(
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
        val seriesCategory = getItem(position)!!
        holder.bind(seriesCategory)
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = true

        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvSeriescategory.isSelected = hasFocus
            holder.binding.cbSeriesCategory.isSelected = hasFocus
        }

        val checkbox = holder.binding.cbSeriesCategory
        holder.itemView.setOnClickListener {
            checkbox.isChecked = !checkbox.isChecked
            if (checkbox.isChecked) {
                seriesCategory.favorite = true
                onClickListener.onClick(seriesCategory)
            }
            if (!checkbox.isChecked)
                seriesCategory.favorite = false
                onClickListener.onClick(seriesCategory)
        }
    }

    companion object {
        private val MANAGE_TVCATEGORY_COMPERATOR = object : DiffUtil.ItemCallback<SeriesCategoryOB>() {
            override fun areItemsTheSame(oldItem: SeriesCategoryOB, newItem: SeriesCategoryOB) =
                oldItem.idByAccountData == newItem.idByAccountData


            override fun areContentsTheSame(oldItem: SeriesCategoryOB, newItem: SeriesCategoryOB) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (seriesCategory: SeriesCategoryOB) -> Unit) {
        fun onClick(seriesCategory: SeriesCategoryOB) = clickListener(seriesCategory)
    }
}