package com.example.mj_player_tv.ui.adapter


import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.databinding.RvItemActivatedAccountsBinding
import com.example.mj_player_tv.databinding.RvItemMovieaccountsBinding
import com.example.mj_player_tv.databinding.RvItemPlexAccountsBinding
import com.example.mj_player_tv.ui.MoviesFragment
import com.example.mj_player_tv.ui.PlexFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class PlexAccountsAdapter(private val helpViewModel: HelpViewModel, private val fragment: PlexFragment) : ListAdapter<Accounts, PlexAccountsAdapter.ViewHolder>(
    ACCOUNT_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemPlexAccountsBinding, val helpViewModel: HelpViewModel) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: Accounts) {
            binding.apply {
                val playlistName = data.name
                tvPlexaccount.text = playlistName
            }
            binding.rvLinearPlexAccount.setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    // Rufen Sie die Funktion im Fragment auf
                    fragment.openMainMenu()
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    // Rufen Sie die Funktion im Fragment auf
                    fragment.openMainMenu()
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    fragment.hideAccountMenu()
                    fragment.showAccountName()
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemPlexAccountsBinding.inflate(
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
        holder.binding.rvLinearPlexAccount.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvPlexaccount.isSelected = hasFocus
            if (hasFocus) {
                fragment.showCategories(accountdata)
            }
        }
    }

    companion object {
        private val ACCOUNT_COMPERATOR = object : DiffUtil.ItemCallback<Accounts>() {
            override fun areItemsTheSame(oldItem: Accounts, newItem: Accounts) =
                oldItem.totalAccountData == newItem.totalAccountData

            override fun areContentsTheSame(oldItem: Accounts, newItem: Accounts) =
                oldItem == newItem
        }
    }
}