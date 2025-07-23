package com.example.mj_player_tv.ui.adapter



import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.databinding.RvItemFullscreenTvcategoryBinding
import com.example.mj_player_tv.ui.FullScreenSelectorFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel


@UnstableApi
class FullscreenTvCategoryAdapter(private val fragment: FullScreenSelectorFragment, private val onClickListener: FullscreenTvCategoryAdapter.OnClickListener, private val helpViewModel: HelpViewModel) : ListAdapter<TvCategoryOB, FullscreenTvCategoryAdapter.ViewHolder>(
    TVCATEGORY_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemFullscreenTvcategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        @UnstableApi
        fun bind(tvcategory: TvCategoryOB) {
            binding.apply {
                tvTvcategory.text = tvcategory.title

            }
            binding.rvLinearTvCategory.setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    // Rufen Sie die Funktion im Fragment auf
                    fragment.setTvCategoriesVisibilityAnimated(false)
                    fragment.setTvChannelsVisibilityAnimated(true)
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                    // Rufen Sie die Funktion im Fragment auf
                    fragment.setTvCategoriesVisibilityAnimated(false)
                    fragment.setTvChannelsVisibilityAnimated(true)
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    // Rufen Sie die Funktion im Fragment auf
                    fragment.setTvCategoriesVisibilityAnimated(false)
                    fragment.setTvAccountsVisibilityAnimated(true)
                    fragment.focusToAccountFromTvCategory(tvcategory.playlistId!!)
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    // Rufen Sie die Funktion im Fragment auf
                    fragment.closeFragment()
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemFullscreenTvcategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)
        return viewHolder
    }

    @UnstableApi
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tvCategory = getItem(position)!!
        holder.bind(tvCategory)

        holder.binding.rvLinearTvCategory.setOnClickListener {
            onClickListener.onClick(tvCategory)
        }
        holder.binding.rvLinearTvCategory.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvTvcategory.isSelected = hasFocus
            if (hasFocus) {
                fragment.showChannelList(tvCategory)
                holder.binding.tvTvcategory.alpha = 1F
                helpViewModel.fullScreenFocusedTvCategory = tvCategory
            } else {
                holder.binding.tvTvcategory.alpha = 0.7F
            }
        }
    }

    class OnClickListener(val clickListener: (tvcategory: TvCategoryOB) -> Unit) {
        fun onClick(tvcategory: TvCategoryOB) = clickListener(tvcategory)
    }

    companion object {
        private val TVCATEGORY_COMPERATOR = object : DiffUtil.ItemCallback<TvCategoryOB>() {
            override fun areItemsTheSame(oldItem: TvCategoryOB, newItem: TvCategoryOB) =
                oldItem.idByAccountData == newItem.idByAccountData


            override fun areContentsTheSame(oldItem: TvCategoryOB, newItem: TvCategoryOB) =
                oldItem == newItem
        }
    }
}