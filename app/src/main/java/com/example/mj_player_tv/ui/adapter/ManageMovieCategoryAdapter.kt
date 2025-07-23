package com.example.mj_player_tv.ui.adapter


import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.MovieCategoryOB
import com.example.mj_player_tv.databinding.RvItemManageMoviecategoryBinding

class ManageMovieCategoryAdapter(private val onClickListener: ManageMovieCategoryAdapter.OnClickListener) : ListAdapter<MovieCategoryOB, ManageMovieCategoryAdapter.ViewHolder>(
    MANAGE_TVCATEGORY_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemManageMoviecategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(moviecategory: MovieCategoryOB) {
            binding.apply {
                tvMoviecategory.text = moviecategory.title
                cbMovieCategory.isChecked = moviecategory.favorite
                cbMovieCategory.isActivated = moviecategory.favorite

                ivNew.visibility = if (moviecategory.newCategory) {
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
        val binding = RvItemManageMoviecategoryBinding.inflate(
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
        val moviecategory = getItem(position)!!
        holder.bind(moviecategory)
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = true

        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvMoviecategory.isSelected = hasFocus
            holder.binding.cbMovieCategory.isSelected = hasFocus
        }

        val checkbox = holder.binding.cbMovieCategory
        holder.itemView.setOnClickListener {
            checkbox.isChecked = !checkbox.isChecked
            if (checkbox.isChecked) {
                moviecategory.favorite = true
                onClickListener.onClick(moviecategory)
            }
            if (!checkbox.isChecked)
                moviecategory.favorite = false
                onClickListener.onClick(moviecategory)
        }
    }

    companion object {
        private val MANAGE_TVCATEGORY_COMPERATOR = object : DiffUtil.ItemCallback<MovieCategoryOB>() {
            override fun areItemsTheSame(oldItem: MovieCategoryOB, newItem: MovieCategoryOB) =
                oldItem.idByAccountData == newItem.idByAccountData


            override fun areContentsTheSame(oldItem: MovieCategoryOB, newItem: MovieCategoryOB) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (moviecategory: MovieCategoryOB) -> Unit) {
        fun onClick(moviecategory: MovieCategoryOB) = clickListener(moviecategory)
    }
}