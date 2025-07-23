package com.example.mj_player_tv.ui.adapter


import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.help.AccountPlexCategory
import com.example.mj_player_tv.databinding.RvItemPlexHeaderBinding
import com.example.mj_player_tv.databinding.RvItemPlexcategoriesBinding
import com.example.mj_player_tv.ui.PlexFragment
import com.example.mj_player_tv.ui.adapter.DiffUtil.AccountPlexCategoryDiffCallback

@UnstableApi
class PlexAccountCategoryAdapter(
    private val fragment: PlexFragment
) : ListAdapter<AccountPlexCategory, RecyclerView.ViewHolder>(AccountPlexCategoryDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CATEGORY = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AccountPlexCategory.Header -> VIEW_TYPE_HEADER
            is AccountPlexCategory.PlexCategory -> VIEW_TYPE_CATEGORY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = RvItemPlexHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SectionHeaderViewHolder(binding, fragment)
            }
            VIEW_TYPE_CATEGORY -> {
                val binding = RvItemPlexcategoriesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                CategoryViewHolder(binding, fragment)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is AccountPlexCategory.Header -> (holder as SectionHeaderViewHolder).bind(item)
            is AccountPlexCategory.PlexCategory -> (holder as CategoryViewHolder).bind(item)
        }
    }

    class SectionHeaderViewHolder(
        private val binding: RvItemPlexHeaderBinding,
        private val fragment: PlexFragment
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: AccountPlexCategory.Header) {
            binding.tvPlexaccount.text = header.name
        }
    }

    class CategoryViewHolder(
        private val binding: RvItemPlexcategoriesBinding,
        private val fragment: PlexFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        private var focusHandler = Handler(Looper.getMainLooper())
        private var focusRunnable: Runnable? = null

        fun bind(category: AccountPlexCategory.PlexCategory) {
            binding.accountName.text = category.name

            binding.root.setOnFocusChangeListener { _, hasFocus ->

                focusRunnable?.let { focusHandler.removeCallbacks(it) } // alten Callback canceln
                binding.accountName.isSelected = hasFocus

                if (hasFocus) {
                    focusRunnable = Runnable {
                        fragment.loadPlexCategoryItems(category)
                    }
                    focusHandler.postDelayed(focusRunnable!!, 200)
                }
            }

            binding.root.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

                when (keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        fragment.showAccountMenu()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        fragment.showAccountMenu()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        fragment.hideCategories()
                        fragment.setFocusToPlexItems()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER -> {
                        fragment.hideCategories()
                        fragment.setFocusToPlexItems()
                        true
                    }
                    else -> false
                }
            }
        }
    }
}