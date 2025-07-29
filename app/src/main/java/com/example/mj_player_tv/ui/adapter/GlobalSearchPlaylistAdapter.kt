package com.example.mj_player_tv.ui.adapter


import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.help.GlobalSearchMainCategory
import com.example.mj_player_tv.databinding.RvItemGlobalsearchPlaylistBinding
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class GlobalSearchPlaylistAdapter(private val helpViewModel: HelpViewModel, private val fragment: GlobalSearchFragment) : ListAdapter<Accounts, GlobalSearchPlaylistAdapter.ViewHolder>(
    GLOBALSEARCH_TV_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemGlobalsearchPlaylistBinding, val helpViewModel: HelpViewModel) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: Accounts) {
            binding.apply {
                tvPlaylistname.text = data.name

                binding.constGsPl.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToTextView()
                        return@setOnKeyListener true
                    }

                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToItems()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemGlobalsearchPlaylistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val viewHolder = ViewHolder(binding, helpViewModel)
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val accountdata = getItem(position)!!
        holder.bind(accountdata)

        when (helpViewModel.selectedGlobalSearchCategory) {
            GlobalSearchMainCategory.TV -> holder.binding.constGsPl.isSelected = accountdata.id == helpViewModel.selectedGlobalSearchAccount?.id
            GlobalSearchMainCategory.MOVIES -> holder.binding.constGsPl.isSelected = accountdata.id == helpViewModel.selectedGlobalSearchAccount?.id
            GlobalSearchMainCategory.SERIES -> holder.binding.constGsPl.isSelected = accountdata.id == helpViewModel.selectedGlobalSearchAccount?.id
            GlobalSearchMainCategory.PROGRAMS -> holder.binding.constGsPl.isSelected = accountdata.id == helpViewModel.selectedGlobalSearchAccount?.id
            else -> {} // optional: nichts tun oder Default setzen
        }

        holder.binding.constGsPl.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                fragment.updateItemList(accountdata)
                holder.binding.constGsPl.alpha = 1f
            } else {
                // Nur halbtransparent, wenn es auch nicht selected ist
                holder.binding.constGsPl.alpha = if (accountdata.id == helpViewModel.selectedGlobalSearchAccount?.id) 1f else 0.6f
            }
        }

    }

    companion object {
        private val GLOBALSEARCH_TV_COMPERATOR = object : DiffUtil.ItemCallback<Accounts>() {
            override fun areItemsTheSame(oldItem: Accounts, newItem: Accounts) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Accounts, newItem: Accounts) =
                oldItem == newItem
        }
    }
}