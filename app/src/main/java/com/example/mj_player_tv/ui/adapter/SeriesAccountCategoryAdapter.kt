package com.example.mj_player_tv.ui.adapter

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.help.AccountMovieCategory
import com.example.mj_player_tv.database.help.AccountSeriesCategory
import com.example.mj_player_tv.databinding.RvItemSeriesaccountsBinding
import com.example.mj_player_tv.databinding.RvItemSeriescategoryBinding
import com.example.mj_player_tv.databinding.RvItemTestBinding
import com.example.mj_player_tv.databinding.RvItemTestCategoryBinding
import com.example.mj_player_tv.ui.SeriesFragment
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.ui.adapter.DiffUtil.AccountMovieCategoryDiffCallback
import com.example.mj_player_tv.ui.adapter.DiffUtil.AccountSeriesCategoryDiffCallback
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class SeriesAccountCategoryAdapter(
    private val expandCollapseListener: (accountPosition: Int) -> Unit,
    private val positionHelper: () -> List<AccountSeriesCategory>,
    private val helpViewModel: HelpViewModel,
    private val fragment: SeriesFragment,
    private val oncategorylongClickListener: SeriesAccountCategoryAdapter.OnLongClickListener
) : ListAdapter<AccountSeriesCategory, RecyclerView.ViewHolder>(AccountSeriesCategoryDiffCallback()) {

    var selectedSeriesCategoryId = 0L

    companion object {
        private const val VIEW_TYPE_ACCOUNT = 0
        private const val VIEW_TYPE_CATEGORY = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AccountSeriesCategory.Account -> VIEW_TYPE_ACCOUNT
            is AccountSeriesCategory.SeriesCategory -> VIEW_TYPE_CATEGORY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ACCOUNT -> {
                val binding = RvItemSeriesaccountsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                AccountViewHolder(binding, expandCollapseListener, helpViewModel, fragment)
            }
            VIEW_TYPE_CATEGORY -> {
                val binding = RvItemSeriescategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SeriesCategoryViewHolder(binding, positionHelper, fragment, helpViewModel, { selectedSeriesCategoryId })
            }
            else -> throw IllegalArgumentException("Invalid viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AccountViewHolder -> {
                val account = getItem(position)
                holder.bind(account as AccountSeriesCategory.Account)
            }
            is SeriesCategoryViewHolder -> {
                val seriesCategory = getItem(position)
                holder.bind(seriesCategory as AccountSeriesCategory.SeriesCategory)
            }
        }
    }

    class AccountViewHolder(
        private val binding: RvItemSeriesaccountsBinding,
        private val expandCollapseListener: (Int) -> Unit,
        private val helpViewModel: HelpViewModel,
        private val fragment: SeriesFragment
    ) : RecyclerView.ViewHolder(binding.root) {


        fun bind(account: AccountSeriesCategory.Account) {

            binding.accountName.isSelected = helpViewModel.clickedSeriesAccountId == account.id
            binding.accountName.text = account.name
            binding.root.setOnClickListener {
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    expandCollapseListener(adapterPosition)
                }
            }

            binding.root.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

                when (keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        helpViewModel.isSeriesAccountFocused = true
                        fragment.openMainMenu()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        helpViewModel.isSeriesAccountFocused = true
                        fragment.openMainMenu()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        fragment.setFocusToSeries()
                        true
                    }
                    else -> false
                }
            }

            binding.root.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    fragment.updateAccount(account.id)
                }
            }
        }
    }


    class SeriesCategoryViewHolder(
        private val binding: RvItemSeriescategoryBinding,
        private val positionHelper: () -> List<AccountSeriesCategory>,
        private val fragment: SeriesFragment,
        private val helpViewModel: HelpViewModel,
        private val selectedIdProvider: () -> Long?
    ) : RecyclerView.ViewHolder(binding.root) {

        private var focusHandler = Handler(Looper.getMainLooper())
        private var focusRunnable: Runnable? = null

        fun bind(seriesCategory: AccountSeriesCategory.SeriesCategory) {
            binding.tvCategoryName.text = seriesCategory.name

            val isSelected = seriesCategory.id == selectedIdProvider()
            binding.rvLinearSeriesCategory.isSelected = isSelected

            binding.rvLinearSeriesCategory.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

                val currentList = positionHelper()

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        val targetPos = bindingAdapterPosition - 1
                        if (targetPos >= 0) {
                            (binding.root.parent as RecyclerView)
                                .findViewHolderForAdapterPosition(targetPos)
                                ?.itemView?.requestFocus()
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val targetPos = bindingAdapterPosition + 1
                        if (targetPos < currentList.size) {
                            (binding.root.parent as RecyclerView)
                                .findViewHolderForAdapterPosition(targetPos)
                                ?.itemView?.requestFocus()
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        helpViewModel.isSeriesAccountFocused = false
                        fragment.selectLastCategory(bindingAdapterPosition, seriesCategory.id)
                        fragment.openMainMenu()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        helpViewModel.isSeriesAccountFocused = false
                        fragment.selectLastCategory(bindingAdapterPosition, seriesCategory.id)
                        fragment.openMainMenu()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        fragment.setSeriesAccountsVisibilityAnimated(false)
                        fragment.setFocusToSeries()
                        true
                    }
                    else -> false
                }
            }

            binding.rvLinearSeriesCategory.setOnClickListener {
                fragment.setSeriesAccountsVisibilityAnimated(false)
                fragment.setFocusToSeries()
            }

            binding.rvLinearSeriesCategory.setOnLongClickListener {
                fragment.showSeriesCategoryHideDialog(seriesCategory.id)
                true
            }

            binding.rvLinearSeriesCategory.setOnFocusChangeListener { _, hasFocus ->
                focusRunnable?.let { focusHandler.removeCallbacks(it) } // alten Callback canceln
                binding.tvCategoryName.isSelected = hasFocus
                if (hasFocus) {
                    focusRunnable = Runnable {
                        fragment.loadSeriesForCategory(seriesCategory.id)
                    }
                    focusHandler.postDelayed(focusRunnable!!, 200)
                }
            }
        }
    }

    class OnLongClickListener(val oncategorylongClickListener: (view: View, position: Int) -> Unit) {
        fun onLongClick(view: View, position: Int) = oncategorylongClickListener(view, position)
    }
}
