package com.example.mj_player_tv.ui.adapter



import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.databinding.RvItemFullscreenAccountBinding
import com.example.mj_player_tv.ui.FullScreenSelectorFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel


@UnstableApi
class FullscreenTvAccountsAdapter(private val fragment: FullScreenSelectorFragment, private val onClickListener: FullscreenTvAccountsAdapter.OnClickListener, private val helpViewModel: HelpViewModel) : ListAdapter<Accounts, FullscreenTvAccountsAdapter.ViewHolder>(
    TVCATEGORY_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemFullscreenAccountBinding) : RecyclerView.ViewHolder(binding.root) {
        @UnstableApi
        fun bind(account: Accounts) {
            binding.apply {
                tvTvaccount.text = account.name
            }
            binding.rvLinearTvAccount.setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    // Rufen Sie die Funktion im Fragment auf
                    fragment.setTvAccountsVisibilityAnimated(false)
                    fragment.setTvCategoriesVisibilityAnimated(true)
                    fragment.focusToTvCategoryFromAccount()
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                    // Rufen Sie die Funktion im Fragment auf
                    fragment.setTvAccountsVisibilityAnimated(false)
                    fragment.setTvCategoriesVisibilityAnimated(true)
                    fragment.focusToTvCategoryFromAccount()
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
        val binding = RvItemFullscreenAccountBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)
        return viewHolder
    }

    @UnstableApi
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val account = getItem(position)!!
        holder.bind(account)

        holder.binding.rvLinearTvAccount.setOnClickListener {
            onClickListener.onClick(account)
        }
        holder.binding.rvLinearTvAccount.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvTvaccount.isSelected = hasFocus
            if (hasFocus) {
                fragment.showTvCategories(account)
                holder.binding.tvTvaccount.alpha = 1F
                helpViewModel.fullScreenFocusedAccount = account
            } else {
                holder.binding.tvTvaccount.alpha = 0.7F
            }
        }
    }

    class OnClickListener(val clickListener: (account: Accounts) -> Unit) {
        fun onClick(account: Accounts) = clickListener(account)
    }

    companion object {
        private val TVCATEGORY_COMPERATOR = object : DiffUtil.ItemCallback<Accounts>() {
            override fun areItemsTheSame(oldItem: Accounts, newItem: Accounts) =
                oldItem.id == newItem.id


            override fun areContentsTheSame(oldItem: Accounts, newItem: Accounts) =
                oldItem == newItem
        }
    }
}