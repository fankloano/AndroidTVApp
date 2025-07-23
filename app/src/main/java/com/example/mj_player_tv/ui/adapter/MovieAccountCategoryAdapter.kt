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
import com.example.mj_player_tv.database.help.Movie
import com.example.mj_player_tv.databinding.RvItemMovieaccountsBinding
import com.example.mj_player_tv.databinding.RvItemMoviecategoryBinding
import com.example.mj_player_tv.databinding.RvItemSeriesaccountsBinding
import com.example.mj_player_tv.databinding.RvItemSeriescategoryBinding
import com.example.mj_player_tv.databinding.RvItemTestBinding
import com.example.mj_player_tv.databinding.RvItemTestCategoryBinding
import com.example.mj_player_tv.ui.MoviesFragment
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.ui.adapter.DiffUtil.AccountMovieCategoryDiffCallback
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class MovieAccountCategoryAdapter(
    private val expandCollapseListener: (accountPosition: Int) -> Unit,
    private val positionHelper: () -> List<AccountMovieCategory>,
    private val helpViewModel: HelpViewModel,
    private val fragment: MoviesFragment,
    private val oncategorylongClickListener: MovieAccountCategoryAdapter.OnLongClickListener
) : ListAdapter<AccountMovieCategory, RecyclerView.ViewHolder>(AccountMovieCategoryDiffCallback()) {

    var selectedMovieCategoryId = 0L

    companion object {
        private const val VIEW_TYPE_ACCOUNT = 0
        private const val VIEW_TYPE_CATEGORY = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AccountMovieCategory.Account -> VIEW_TYPE_ACCOUNT
            is AccountMovieCategory.MovieCategory -> VIEW_TYPE_CATEGORY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ACCOUNT -> {
                val binding = RvItemMovieaccountsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                AccountViewHolder(binding, expandCollapseListener, helpViewModel, fragment)
            }
            VIEW_TYPE_CATEGORY -> {
                val binding = RvItemMoviecategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                MovieCategoryViewHolder(binding, positionHelper, fragment, helpViewModel, { selectedMovieCategoryId })
            }
            else -> throw IllegalArgumentException("Invalid viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AccountViewHolder -> {
                val account = getItem(position)
                holder.bind(account as AccountMovieCategory.Account)
            }
            is MovieCategoryViewHolder -> {
                val movieCategory = getItem(position)
                holder.bind(movieCategory as AccountMovieCategory.MovieCategory)
            }
        }
    }

    class AccountViewHolder(
        private val binding: RvItemMovieaccountsBinding,
        private val expandCollapseListener: (Int) -> Unit,
        private val helpViewModel: HelpViewModel,
        private val fragment: MoviesFragment
    ) : RecyclerView.ViewHolder(binding.root) {


        fun bind(account: AccountMovieCategory.Account) {

            binding.accountName.isSelected = helpViewModel.clickedMovieAccountId == account.id
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
                        helpViewModel.isMovieAccountFocused = true
                        fragment.openMainMenu()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        helpViewModel.isMovieAccountFocused = true
                        fragment.openMainMenu()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        fragment.setFocusToMovies()
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


    class MovieCategoryViewHolder(
        private val binding: RvItemMoviecategoryBinding,
        private val positionHelper: () -> List<AccountMovieCategory>,
        private val fragment: MoviesFragment,
        private val helpViewModel: HelpViewModel,
        private val selectedIdProvider: () -> Long?
    ) : RecyclerView.ViewHolder(binding.root) {

        private var focusHandler = Handler(Looper.getMainLooper())
        private var focusRunnable: Runnable? = null

        fun bind(movieCategory: AccountMovieCategory.MovieCategory) {
            binding.tvCategoryName.text = movieCategory.name

            val isSelected = movieCategory.id == selectedIdProvider()
            binding.rvLinearMovieCategory.isSelected = isSelected

            binding.rvLinearMovieCategory.setOnKeyListener { _, keyCode, event ->
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
                        helpViewModel.isMovieAccountFocused = false
                        fragment.selectLastCategory(bindingAdapterPosition, movieCategory.id)
                        fragment.openMainMenu()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        helpViewModel.isMovieAccountFocused = false
                        fragment.selectLastCategory(bindingAdapterPosition, movieCategory.id)
                        fragment.openMainMenu()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        fragment.setMovieAccountsVisibilityAnimated(false)
                        fragment.setFocusToMovies()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER -> {
                        fragment.setMovieAccountsVisibilityAnimated(false)
                        fragment.setFocusToMovies()
                        true
                    }
                    else -> false
                }
            }

            binding.rvLinearMovieCategory.setOnFocusChangeListener { _, hasFocus ->
                focusRunnable?.let { focusHandler.removeCallbacks(it) } // alten Callback canceln
                binding.tvCategoryName.isSelected = hasFocus
                if (hasFocus) {
                    focusRunnable = Runnable {
                        fragment.loadMoviesForCategory(movieCategory.id)
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
