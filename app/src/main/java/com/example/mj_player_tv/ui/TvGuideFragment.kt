package com.example.mj_player_tv.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import coil.size.Dimension
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.help.AccountTvCategory
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.FragmentHomeBinding
import com.example.mj_player_tv.databinding.FragmentTvChannelsBinding
import com.example.mj_player_tv.databinding.FragmentTvguideBinding
import com.example.mj_player_tv.ui.adapter.TvAccountCategoryAdapter
import com.example.mj_player_tv.ui.adapter.TvChannelsAdapter
import com.example.mj_player_tv.ui.adapter.TvGuideAccountCategoryAdapter
import com.example.mj_player_tv.ui.adapter.TvGuideChannelAdapter
import com.example.mj_player_tv.utils.EpgScrollSyncManager
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class TvGuideFragment : Fragment(R.layout.fragment_tvguide) {

    private var _binding: FragmentTvguideBinding? = null

    private val binding get() = _binding!!

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    private lateinit var tvGuideAccountCategoryAdapter: TvGuideAccountCategoryAdapter

    private lateinit var tvGuideChannelsWithEpgAdapter: TvGuideChannelAdapter

    val scrollSyncManager = EpgScrollSyncManager()

    private var fullAccountList = listOf<AccountTvCategory>()
    private var expandedAccountId: Long? = null
    private var currentList = listOf<AccountTvCategory>()


    private var isFirstOpen = true

    private val stalkerViewModel: StalkerViewModel by activityViewModels {
        StalkerViewModelFactory(
            requireActivity().application
        )
    }

    private val helpViewModel: HelpViewModel by activityViewModels {
        HelpViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTvguideBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scrollSyncManager.register(binding.rvTimeMarks)

        prepareAccountCategoryRecyclerView()

        prepareTvChannelsRecyclerView()

        var accountsList = listOf<AccountTvCategory>()

        helpViewModel.tvAccountsWithCategoriesLiveData.observe(viewLifecycleOwner) { accounts ->
            if (accounts.isEmpty()) {
                binding.tvNoTvAccounts.visibility = View.VISIBLE
                binding.rvLayoutTvAccountsMenu.visibility = View.INVISIBLE
                openMainMenu()
                if (isFirstOpen) {
                    isFirstOpen = false
                }
            } else {
                binding.tvNoTvAccounts.visibility = View.INVISIBLE
                binding.rvLayoutTvAccountsMenu.visibility = VISIBLE
                fullAccountList = accounts

                if (expandedAccountId != null) {
                    // Account ist gerade aufgeklappt – also aufgeklappte Version wieder aufbauen
                    val flatList = mutableListOf<AccountTvCategory>()
                    fullAccountList.forEach { account ->
                        flatList.add(account)
                        if (account is AccountTvCategory.Account && account.id == expandedAccountId) {
                            flatList.addAll(account.categories)
                        }
                    }
                    currentList = flatList
                } else {
                    // Keine Kategorie offen → nur Accounts
                    currentList = fullAccountList
                }
                if (isFirstOpen && accountsList != accounts) {
                    accountsList = accounts
                    submitCollapsedTVList()
                } else {
                    if (accountsList != accounts) {
                        accountsList = accounts
                        tvGuideAccountCategoryAdapter.submitList(currentList)
                    }
                }
            }
        }

    }

    //ACCOUNTS

    private fun prepareAccountCategoryRecyclerView() {
        tvGuideAccountCategoryAdapter = TvGuideAccountCategoryAdapter(
            ::onAccountClicked,
            { currentList },
            helpViewModel,
            this
        )
        binding.rvLayoutTvAccountsMenu.apply {
            adapter = tvGuideAccountCategoryAdapter
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(true, true)
            itemAnimator = DefaultItemAnimator().apply {
                addDuration = 300
                removeDuration = 300
                moveDuration = 300
                changeDuration = 300
            }
        }
    }

    private fun submitCollapsedTVList() {
        currentList = fullAccountList
        tvGuideAccountCategoryAdapter.submitList(currentList)
        binding.rvLayoutTvAccountsMenu.post {
            if (isFirstOpen) {
                if (helpViewModel.channelFromSearchContainer) {
                    helpViewModel.clickedTvAccountId = helpViewModel.currentFocusedTvAccount?.id
                    val currAcc = tvGuideAccountCategoryAdapter.currentList.firstOrNull { it is AccountTvCategory.Account && it.id == helpViewModel.currentFocusedTvAccount?.id } as AccountTvCategory.Account
                    val pos = tvGuideAccountCategoryAdapter.currentList.indexOf(currAcc)
                    helpViewModel.clickedTvAccountPosition = pos
                }
                if (helpViewModel.clickedTvAccountId != 0L && helpViewModel.clickedTvAccountPosition != -1) {
                    onAccountClicked(helpViewModel.clickedTvAccountPosition)
                } else {
                    isFirstOpen = false
                    binding.rvLayoutTvAccountsMenu.requestFocus()
                }
            } else {
                binding.rvLayoutTvAccountsMenu.requestFocus()
            }
        }
    }

    private fun onAccountClicked(position: Int) {

        val list = tvGuideAccountCategoryAdapter.currentList

        if (position !in list.indices) {
            return
        }

        val item = tvGuideAccountCategoryAdapter.currentList[position] as AccountTvCategory.Account


        if (expandedAccountId == item.id) {
            expandedAccountId = null
            helpViewModel.clickedTvAccountId = 0L
            helpViewModel.clickedTvAccountPosition = -1
            tvGuideAccountCategoryAdapter.notifyItemChanged(position)
            submitCollapsedTVList()
            binding.rvLayoutTvAccountsMenu.post {
                binding.rvLayoutTvAccountsMenu.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
            }
            return
        }
        expandedAccountId = item.id
        helpViewModel.currentFocusedTvAccount?.let {
            helpViewModel.epgCache.clear()
            helpViewModel.getEpgForTime(it)
        }
        val oldAccount = tvGuideAccountCategoryAdapter.currentList.firstOrNull {
            it is AccountTvCategory.Account && it.id == helpViewModel.clickedTvAccountId
        } as? AccountTvCategory.Account
        val oldAccountPosition = tvGuideAccountCategoryAdapter.currentList.indexOf(oldAccount)
        val newAccount = tvGuideAccountCategoryAdapter.currentList.firstOrNull {
            it is AccountTvCategory.Account && it.id == item.id
        } as? AccountTvCategory.Account
        val newAccountPosition = tvGuideAccountCategoryAdapter.currentList.indexOf(newAccount)

        tvGuideAccountCategoryAdapter.notifyItemChanged(oldAccountPosition)

        helpViewModel.clickedTvAccountId = item.id
        helpViewModel.clickedTvAccountPosition = position

        tvGuideAccountCategoryAdapter.notifyItemChanged(newAccountPosition)
        if (helpViewModel.channelFromSearchContainer) {
            tvGuideAccountCategoryAdapter.notifyDataSetChanged()
        }
        val flatList = mutableListOf<AccountTvCategory>()
        fullAccountList.forEach { account ->
            if (account is AccountTvCategory.Account) {
                flatList.add(account)
                if (account.id == item.id) {
                    flatList.addAll(account.categories)
                }
            }
        }

        currentList = flatList
        tvGuideAccountCategoryAdapter.submitList(flatList) {
            binding.rvLayoutTvAccountsMenu.post {
                val thisList = tvGuideAccountCategoryAdapter.currentList
                val clickedAccount = tvGuideAccountCategoryAdapter.currentList.firstOrNull {
                    it is AccountTvCategory.Account && it.id == item.id
                } as? AccountTvCategory.Account
                val clickedAccountPosition = tvGuideAccountCategoryAdapter.currentList.indexOf(clickedAccount)
                // Scroll zu Account, falls notwendig
                binding.rvLayoutTvAccountsMenu.scrollToPosition(clickedAccountPosition)

                // WICHTIG: Stelle sicher, dass die Kategorie darunter aufgebaut wird
                if (position + 1 < thisList.size &&
                    thisList[position + 1] is AccountTvCategory.TvCategory
                ) {

                    // Kein requestFocus()! Nur sicherstellen, dass ViewHolder aufgebaut ist.
                    binding.rvLayoutTvAccountsMenu.post {
                        binding.rvLayoutTvAccountsMenu
                            .findViewHolderForAdapterPosition(clickedAccountPosition)
                        // Nichts weiter tun – dadurch ist das Item bereit für Fokus per DPAD_DOWN
                    }
                }
                if (isFirstOpen) {
                    val focusedCategoryId = helpViewModel.currentFocusedTvCategory?.id ?: 0L
                    if (focusedCategoryId != 0L) {

                        val categoryPosition = thisList.indexOfFirst {
                            it is AccountTvCategory.TvCategory && it.id == focusedCategoryId
                        }

                        if (categoryPosition != -1) {
                            binding.rvLayoutTvAccountsMenu.setSelectedPosition(categoryPosition)
                            binding.rvLayoutTvAccountsMenu.post {
                                if (!helpViewModel.channelFromSearchContainer) {
                                    binding.rvLayoutTvAccountsMenu
                                        .findViewHolderForAdapterPosition(categoryPosition)
                                        ?.itemView?.requestFocus()
                                } else {
                                    helpViewModel.currentFocusedChannPosition?.let {
                                        //changingPlayingChannel(it)
                                    }
                                    helpViewModel.currentFocusedTvCategory?.let {
                                        //showChannelList(it.id)
                                    }
                                }
                            }
                        }
                    }
                    isFirstOpen = false
                }
            }
        }

        if (item.categories.isEmpty()) {
            Toast.makeText(
                this@TvGuideFragment.requireActivity(),
                "No categories enabled!",
                Toast.LENGTH_SHORT
            ).show()
            binding.rvLayoutTvAccountsMenu.requestFocus()
        }
    }

    fun updateFocusedTvAccount(accountId: Long) {
        if (!helpViewModel.channelFromSearchContainer) {
            helpViewModel.currentFocusedTvAccount = accountBox.get(accountId)
        }
    }

    fun setFocusToAccountCategoryRV() {
        if (tvGuideAccountCategoryAdapter.currentList.isNotEmpty()) {
            binding.rvLayoutTvAccountsMenu.requestFocus()
        } else {
            return
        }
    }

    fun setTvAccountsVisibilityAnimated(isVisible: Boolean) {
        val accountsRecyclerView = binding.linLayoutTvAccountsMenu
        val constraintSet = ConstraintSet()
        val constTvLayout = binding.constTvguide.findViewById<ConstraintLayout>(R.id.const_tvguide)
        constraintSet.clone(constTvLayout)

        if (isVisible) {
            constraintSet.clear(accountsRecyclerView.id, ConstraintSet.END)
            constraintSet.connect(accountsRecyclerView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            binding.rvLayoutTvAccountsMenu.isFocusable = true
            binding.rvLayoutTvAccountsMenu.isFocusableInTouchMode = true
            showMainMenu()
        } else {
            binding.rvLayoutTvAccountsMenu.isFocusable = false
            binding.rvLayoutTvAccountsMenu.isFocusableInTouchMode = false
            constraintSet.clear(accountsRecyclerView.id, ConstraintSet.START)
            constraintSet.connect(accountsRecyclerView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.START)
        }

        // Wenn channelfromsearchcontainer true → keine Animation, sofort anwenden
        if (helpViewModel.channelFromSearchContainer) {
            constraintSet.applyTo(constTvLayout)
        } else {
            val transition = ChangeBounds()
            transition.duration = 250 // Dauer nach Bedarf
            TransitionManager.beginDelayedTransition(constTvLayout, transition)
            constraintSet.applyTo(constTvLayout)
        }
    }


    //TVCHANNELS

    private fun prepareTvChannelsRecyclerView() {
        tvGuideChannelsWithEpgAdapter = TvGuideChannelAdapter(
            this,
            helpViewModel
        )
        binding.rvChannelsWithEpg.apply {
            adapter = tvGuideChannelsWithEpgAdapter
            addItemDecoration(
                DpadLinearSpacingDecoration.create(
                    itemSpacing = 4,
                    edgeSpacing = 4,
                    perpendicularEdgeSpacing = 4
                )
            )
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(false, false)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private var currentTvChannelsJob: Job? = null

    fun showChannelListInRecyclerview(accountTvCategoryId: Long) {
        if (helpViewModel.currentFocusedTvCategory?.id != accountTvCategoryId) {
            val tvCategory = tvCatBox.get(accountTvCategoryId)
            helpViewModel.currentFocusedTvCategory = tvCategory
            currentTvChannelsJob?.cancel()
            currentTvChannelsJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val isPlaylistActive =
                    helpViewModel.currentFocusedTvAccount?.epgsources?.any { it.isSelected && it.isPlaylistEpg }
                val sortedChannels = getChannelList()
                if (sortedChannels.isNotEmpty()) {
                    val channelsWithEpg = sortedChannels.map {
                        val tvChannel = it.tvchannel.target
                        val chEpgId = tvChannel.linkedEpgChannel?.target?.chEpgId
                            ?: if (isPlaylistActive == true) {
                                tvChannel.epgChannel?.target?.chEpgId
                            } else {
                                null
                            }
                        TvChannelWithEpg(
                            it.id,
                            it,
                            helpViewModel.epgCache[chEpgId]?.toMutableList() ?: emptyList()
                        )
                    }
                    withContext(Dispatchers.Main) {
                        tvGuideChannelsWithEpgAdapter.submitList(channelsWithEpg)
                    }
                }
            }
        }
    }

    private fun getChannelList(): List<ChannelPositions> {
        val sortedChannels = when {
            helpViewModel.currentFocusedTvCategory!!.isAllChannelsCategory -> {
                helpViewModel.currentFocusedTvAccount?.channels?.reset()
                val categories = helpViewModel.currentFocusedTvAccount?.tvcategories
                    ?.filter {
                        it.favorite && !it.isFavoriteCategory && !it.userCategory
                    }
                val channelPositions: MutableList<ChannelPositions> = mutableListOf()
                categories?.forEach {
                    channelPositions.addAll(it.tvChannelLink)
                }
                channelPositions
            }

            else -> {
                helpViewModel.currentFocusedTvCategory!!.tvChannelLink.reset()
                when (helpViewModel.currentFocusedTvCategory!!.orderBy) {
                    0 -> {
                        val categoryLinks =
                            helpViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.originalPosition }.filter {
                                it.isSelected
                            }

                        if (helpViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
                            categoryLinks.filter { it.tvchannel.target.isFavorite }
                        } else {
                            categoryLinks
                        }
                    }

                    1 -> {
                        val categoryLinks =
                            helpViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.tvchannel.target.showingName }.filter {
                                it.isSelected
                            }
                        if (helpViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
                            categoryLinks.filter { it.tvchannel.target.isFavorite }
                        } else {
                            categoryLinks
                        }
                    }

                    else -> {
                        val categoryLinks =
                            helpViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.position }.filter {
                                it.isSelected
                            }
                        if (helpViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
                            categoryLinks.filter { it.tvchannel.target.isFavorite }
                        } else {
                            categoryLinks
                        }
                    }
                }
            }
        }
      return sortedChannels
    }

    fun focusToTvChannelsWithEpg() {
        if (tvGuideChannelsWithEpgAdapter.currentList.isNotEmpty()) {
            hideMainMenu()
            binding.rvChannelsWithEpg.requestFocus()
        } else {
            return
        }
    }

    private fun updateNowMarker() {
        val now = System.currentTimeMillis()
        val diffMinutes = (now - (now - 10000)) / 60000
        val x = (diffMinutes * 7f) - binding.rvChannelsWithEpg.computeHorizontalScrollOffset()
        binding.viewNowMarker.translationX = x.toFloat()
    }

    //MAIN ACTIVITY

    fun showMainMenu() {
        (requireActivity() as? MainActivity)?.showMenu()
    }

    fun hideMainMenu() {
        (requireActivity() as? MainActivity)?.hideMenu()
    }

    fun openMainMenu() {
        (requireActivity() as? MainActivity)?.openMenu()
        (requireActivity() as? MainActivity)?.toggleVisibilityOfMainContainer(false)
        (requireActivity() as? MainActivity)?.lastSelectFocus()
    }


    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}