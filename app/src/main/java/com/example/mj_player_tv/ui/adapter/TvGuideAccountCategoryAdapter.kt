package com.example.mj_player_tv.ui.adapter

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.help.AccountTvCategory
import com.example.mj_player_tv.databinding.RvItemTvaccountsBinding
import com.example.mj_player_tv.databinding.RvItemTvcategoryBinding
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.ui.TvGuideFragment
import com.example.mj_player_tv.ui.adapter.DiffUtil.AccountTvCategoryDiffCallback
import com.example.mj_player_tv.viewmodel.HelpViewModel
import java.security.Key

@UnstableApi
class TvGuideAccountCategoryAdapter(
    private val expandCollapseListener: (accountPosition: Int) -> Unit,
    private val positionHelper: () -> List<AccountTvCategory>,
    private val helpViewModel: HelpViewModel,
    private val fragment: TvGuideFragment
) : ListAdapter<AccountTvCategory, RecyclerView.ViewHolder>(AccountTvCategoryDiffCallback()) {

    private var longPressRunnable: Runnable? = null

    private val longPressHandler = Handler()
    private var isLongPress = false

    var isLongPressBackOnce = true
    var isHandled = false // Flag zur Vermeidung doppelter Aktionen

    private var keyDownStartTime: Long = 0
    private val longPressDuration: Long = 500

    var selectedTvCategoryId = 0L

    companion object {
        private const val VIEW_TYPE_ACCOUNT = 0
        private const val VIEW_TYPE_CATEGORY = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AccountTvCategory.Account -> VIEW_TYPE_ACCOUNT
            is AccountTvCategory.TvCategory -> VIEW_TYPE_CATEGORY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ACCOUNT -> {
                val binding = RvItemTvaccountsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                AccountViewHolder(binding, expandCollapseListener, helpViewModel, fragment)
            }
            VIEW_TYPE_CATEGORY -> {
                val binding = RvItemTvcategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                TvCategoryViewHolder(binding, positionHelper, fragment, helpViewModel, { selectedTvCategoryId })
            }
            else -> throw IllegalArgumentException("Invalid viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AccountViewHolder -> {
                val account = getItem(position)
                holder.bind(account as AccountTvCategory.Account)
            }
            is TvCategoryViewHolder -> {
                val tvCategory = getItem(position)
                holder.bind(tvCategory as AccountTvCategory.TvCategory)
            }
        }
    }

    inner class AccountViewHolder(
        private val binding: RvItemTvaccountsBinding,
        private val expandCollapseListener: (Int) -> Unit,
        private val helpViewModel: HelpViewModel,
        private val fragment: TvGuideFragment
    ) : RecyclerView.ViewHolder(binding.root) {


        fun bind(account: AccountTvCategory.Account) {

            binding.accountName.isSelected = helpViewModel.clickedTvAccountId == account.id
            binding.accountName.text = account.name

            binding.root.setOnKeyListener { _, keyCode, event ->
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        isLongPress = false
                        keyDownStartTime = SystemClock.elapsedRealtime()

                        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    return@setOnKeyListener false
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    return@setOnKeyListener false
                                }
                            }
                        } else {
                            longPressRunnable = Runnable {
                                if (!isLongPress) {
                                    isLongPress = true
                                    when (keyCode) {
                                        KeyEvent.KEYCODE_BACK -> {
                                            handleBackLongPress()
                                            if (longPressRunnable != null) {
                                                longPressHandler.removeCallbacks(longPressRunnable!!)
                                            }
                                        }
                                    }
                                }
                            }
                            longPressHandler.postDelayed(longPressRunnable!!, longPressDuration)
                        }
                    }
                    KeyEvent.ACTION_UP -> {
                        val pressDuration = SystemClock.elapsedRealtime() - keyDownStartTime

                        if (longPressRunnable != null) {
                            longPressHandler.removeCallbacks(longPressRunnable!!)
                        }

                        if (pressDuration < longPressDuration && !isLongPress) {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> handleLeftShortPress()
                                KeyEvent.KEYCODE_DPAD_RIGHT -> handleRightShortPress()
                                KeyEvent.KEYCODE_BACK -> {
                                    if (!helpViewModel.fullScreenFromAbside) {
                                        handleBackShortPress()
                                    }
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER -> handleCenterShortPress()
                            }
                        }
                        if (helpViewModel.fullScreenFromAbside && keyCode == KeyEvent.KEYCODE_BACK && isLongPressBackOnce) {
                            helpViewModel.fullScreenFromAbside = false
                            //fragment.setFocusToVideoView()
                            isLongPressBackOnce = false
                         }
                        isHandled = false
                        isLongPress = false
                    }
                }
                return@setOnKeyListener true
            }

        binding.root.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    fragment.updateFocusedTvAccount(account.id)
                }
            }
        }

        private fun handleCenterShortPress() {
            val adapterPosition = bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                expandCollapseListener(adapterPosition)
            }
        }

        private fun handleBackShortPress() {
            fragment.openMainMenu()
        }

        private fun handleLeftShortPress() {
            fragment.openMainMenu()
        }

        private fun handleRightShortPress() {
            fragment.focusEpgRecycler()
            fragment.setTvAccountsVisibilityAnimated(false)
        }


        private fun handleDownShortPress() {
            // Prüfen, ob es ein nächstes Element gibt
            if (bindingAdapterPosition < itemCount - 1) {
                // Fokussiere das nächste Element automatisch
                itemView.focusSearch(View.FOCUS_DOWN)?.requestFocus()
            }
        }

        private fun handleUpShortPress() {
            // Prüfen, ob es ein nächstes Element gibt
            if (bindingAdapterPosition <= itemCount - 1) {
                itemView.focusSearch(View.FOCUS_UP)?.requestFocus()
            }
        }

        private fun handleBackLongPress() {
            if (helpViewModel.isCurrentlyPlayingTv && isLongPressBackOnce) {
                // Remove the long press callback if the key was released before 500ms
                if (longPressRunnable != null) {
                    longPressHandler.removeCallbacks(longPressRunnable!!)
                }
                //fragment.hideMainMenu()
                //fragment.setTvAccountsVisibilityAnimated(false)
                //fragment.setVideoViewFullScreenWithoutFocus()
                helpViewModel.fullScreenFromAbside = true
            }
        }
    }


    inner class TvCategoryViewHolder(
        private val binding: RvItemTvcategoryBinding,
        private val positionHelper: () -> List<AccountTvCategory>,
        private val fragment: TvGuideFragment,
        private val helpViewModel: HelpViewModel,
        private val selectedIdProvider: () -> Long?
    ) : RecyclerView.ViewHolder(binding.root) {

        private var focusHandler = Handler(Looper.getMainLooper())
        private var focusRunnable: Runnable? = null

        fun bind(tvCategory: AccountTvCategory.TvCategory) {
            binding.tvCategoryName.text = tvCategory.name

            // Sichtbar selektiert = wenn dieses Item das zuletzt ausgewählte ist UND gerade nicht im Fokus
            val isSelected = tvCategory.id == selectedIdProvider()
            binding.rvLinearTvCategory.isSelected = isSelected


            binding.rvLinearTvCategory.setOnKeyListener { _, keyCode, event ->
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (!isHandled) {
                            isLongPress = false
                            keyDownStartTime = SystemClock.elapsedRealtime()

                            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                when (keyCode) {
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        val targetPos = bindingAdapterPosition + 1
                                        if (targetPos < currentList.size) {
                                            (binding.root.parent as RecyclerView)
                                                .findViewHolderForAdapterPosition(targetPos)
                                                ?.itemView?.requestFocus()
                                            return@setOnKeyListener true
                                        } else return@setOnKeyListener false
                                    }

                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        val targetPos = bindingAdapterPosition - 1
                                        if (targetPos >= 0) {
                                            (binding.root.parent as RecyclerView)
                                                .findViewHolderForAdapterPosition(targetPos)
                                                ?.itemView?.requestFocus()
                                            return@setOnKeyListener true
                                        } else return@setOnKeyListener false
                                    }
                                }
                            } else {
                                longPressRunnable = Runnable {
                                    if (!isLongPress) {
                                        isLongPress = true
                                        isHandled = true

                                        when (keyCode) {
                                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                // Long press action for left key
                                                Log.d("TV CAT KEYCODE", "Long press: LEFT")
                                            }

                                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                // Long press action for right key
                                                Log.d("TV CAT KEYCODE", "Long press: RIGHT")
                                            }

                                            KeyEvent.KEYCODE_DPAD_CENTER -> {
                                                handleCenterLongPress()
                                                if (longPressRunnable != null) {
                                                    longPressHandler.removeCallbacks(longPressRunnable!!)
                                                }
                                                Log.d("TV CAT KEYCODE", "Long press: CENTER")
                                            }

                                            KeyEvent.KEYCODE_BACK -> {
                                                handleBackLongPress()
                                                if (longPressRunnable != null) {
                                                    longPressHandler.removeCallbacks(longPressRunnable!!)
                                                }
                                                Log.d("TV CAT KEYCODE", "Long press: BACK")
                                            }
                                        }
                                    }
                                }
                                longPressHandler.postDelayed(longPressRunnable!!, longPressDuration)
                            }
                        } else {
                            Log.d("NOTHANDLED", "NOTHANDLED")
                        }
                    }

                    KeyEvent.ACTION_UP -> {
                        val pressDuration = SystemClock.elapsedRealtime() - keyDownStartTime
                        if (longPressRunnable != null) {
                            longPressHandler.removeCallbacks(longPressRunnable!!)
                        }

                        if (pressDuration < longPressDuration && !isLongPress) {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> handleLeftShortPress()

                                KeyEvent.KEYCODE_DPAD_RIGHT -> handleRightShortPress()

                                KeyEvent.KEYCODE_BACK -> {
                                    if (!helpViewModel.fullScreenFromAbside) {
                                        handleBackShortPress()
                                        Log.d("TV CAT KEYCODE", "Short press: BACK")
                                    }
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER -> handleCenterShortPress()
                            }
                        }
                        if (helpViewModel.fullScreenFromAbside && keyCode == KeyEvent.KEYCODE_BACK && isLongPressBackOnce) {
                            helpViewModel.fullScreenFromAbside = false
                            //fragment.setFocusToVideoView()
                            isLongPressBackOnce = false
                        }
                        isHandled = false
                        isLongPress = false
                    }
                }
                return@setOnKeyListener true
            }

            binding.rvLinearTvCategory.setOnFocusChangeListener { _, hasFocus ->
                focusRunnable?.let { focusHandler.removeCallbacks(it) } // alten Callback canceln
                binding.tvCategoryName.isSelected = hasFocus
                if (hasFocus) {
                    focusRunnable = Runnable {
                        fragment.showChannelListInRecyclerview(tvCategory.id)
                    }
                    focusHandler.postDelayed(focusRunnable!!, 200)
                }
            }
        }

        private fun handleLeftShortPress() {
            helpViewModel.isTvAccountFocused = false
            fragment.openMainMenu()
        }

        private fun handleRightShortPress() {
            fragment.focusEpgRecycler()
            fragment.setTvAccountsVisibilityAnimated(false)
        }

        private fun handleCenterShortPress() {
            fragment.focusEpgRecycler()
            fragment.setTvAccountsVisibilityAnimated(false)
        }

        private fun handleBackShortPress() {
            helpViewModel.isTvAccountFocused = false
            fragment.openMainMenu()
        }

        private fun handleCenterLongPress() {
            if (longPressRunnable != null) {
                longPressHandler.removeCallbacks(longPressRunnable!!)
            }
           //fragment.openTvCatMenu()
        }

        private fun handleBackLongPress() {
            if (helpViewModel.isCurrentlyPlayingTv && isLongPressBackOnce) {
                // Remove the long press callback if the key was released before 500ms
                if (longPressRunnable != null) {
                    longPressHandler.removeCallbacks(longPressRunnable!!)
                }
                //fragment.hideMainMenu()
                //fragment.setTvAccountsVisibilityAnimated(false)
                //fragment.setVideoViewFullScreenWithoutFocus()
                helpViewModel.fullScreenFromAbside = true
            }
        }

        fun cleanup() {
            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
            longPressRunnable = null
        }
    }
}

