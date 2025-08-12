package com.example.mj_player_tv.ui.adapter


import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.RvItemManageTvcategoryBinding
import com.example.mj_player_tv.databinding.RvItemManageTvchannelsBinding
import com.example.mj_player_tv.ui.ManageTvCategoriesFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class ManageTvCategoriesEditorAdapter(private val onClickListener: ManageTvCategoriesEditorAdapter.OnClickListener, private val fragment: ManageTvCategoriesFragment) : ListAdapter<TvCategoryOB, ManageTvCategoriesEditorAdapter.ViewHolder>(
    MANAGE_TVCATEGORIES_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemManageTvcategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tvcategory: TvCategoryOB) {
            binding.apply {
                tvTvcategory.text = tvcategory.showingName

                cbTvCategory.isChecked = tvcategory.favorite

                ivNew.visibility = if (tvcategory.newCategory) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                binding.root.setOnClickListener {
                    cbTvCategory.isChecked = !cbTvCategory.isChecked
                    tvTvcategory.isSelected = cbTvCategory.isChecked
                    tvcategory.favorite = cbTvCategory.isChecked
                    onClickListener.onClick(tvcategory)
                }

                binding.root.setOnKeyListener { v, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                        // Rufen Sie die Funktion im Fragment auf
                        fragment.closeFragment()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }
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
    }

    companion object {
        private val MANAGE_TVCATEGORIES_COMPERATOR = object : DiffUtil.ItemCallback<TvCategoryOB>() {
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