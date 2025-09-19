package com.example.mj_player_tv.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import coil.load
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.help.AccountTvCategory
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.FragmentTvguideBinding
import com.example.mj_player_tv.repository.TvGuideScrollSyncManager
import com.example.mj_player_tv.ui.adapter.ChannelAdapter
import com.example.mj_player_tv.ui.adapter.TvGuideAccountCategoryAdapter
import com.example.mj_player_tv.ui.tvguide.TvGuideRecyclerview
import com.example.mj_player_tv.utils.views.CustomVerticalGridView
import com.example.mj_player_tv.utils.views.TimeMarksRecyclerView
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.TvGuideViewModel
import com.example.mj_player_tv.viewmodel.TvGuideViewModelFactory
import com.volkov.EPGConfig
import com.volkov.epgrecycler.EPGRecyclerView
import com.volkov.epgrecycler.models.epg.ChannelModel
import com.volkov.epgrecycler.models.epg.ShowModel
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.concurrent.TimeUnit

@UnstableApi
class TvGuideFragment : Fragment(R.layout.fragment_tvguide) {

    private var _binding: FragmentTvguideBinding? = null

    private val binding get() = _binding!!

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    private lateinit var tvGuideAccountCategoryAdapter: TvGuideAccountCategoryAdapter

    private val epgListener = object : TvGuideRecyclerview.OnEventListener {
        override fun onShowSelected(channelId: String, showId: String) {

        }

        override fun onShowClick(channelId: String, showId: String) {
            Log.d("click epgview:", "$channelId, $showId")
        }

        override fun onShowExit() {
            binding.btnFocus.post {
                binding.btnFocus.requestFocus()
            }
        }
    }


    private var fullAccountList = listOf<AccountTvCategory>()
    private var expandedAccountId: Long? = null
    private var currentList = listOf<AccountTvCategory>()
    val scrollSyncManager = TvGuideScrollSyncManager()
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

    private val tvGuideViewModel: TvGuideViewModel by activityViewModels {
        TvGuideViewModelFactory(
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

        with(EPGConfig) {
            marginVerticalChannelLogo = 2
        }
        binding.epgView.apply {
            setDayShift(0)
            val startDate = DateTime().minusMinutes(0)
            val endTime = startDate.plusMinutes(15)
            setStartHour(startDate.hourOfDay)
            setEndHour(0)
            listener = epgListener
            initView(
                listOf()
            )
        }

        prepareAccountCategoryRecyclerView()
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

    fun focusEpgRecycler() {
        binding.epgView.requestFocus()
    }

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
                    helpViewModel.clickedTvAccountId = tvGuideViewModel.currentFocusedTvAccount?.id
                    val currAcc = tvGuideAccountCategoryAdapter.currentList.firstOrNull { it is AccountTvCategory.Account && it.id == tvGuideViewModel.currentFocusedTvAccount?.id } as AccountTvCategory.Account
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
                    val focusedCategoryId = tvGuideViewModel.currentFocusedTvCategory?.id ?: 0L
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
                                    tvGuideViewModel.currentFocusedTvCategory?.let {
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



    private fun setupScrollingSync(
        channelsRecyclerView: CustomVerticalGridView,
        timeMarks: TimeMarksRecyclerView
    ) {
        val syncListeners = mutableListOf<RecyclerView.OnScrollListener>()

        for (i in 0 until channelsRecyclerView.adapter!!.itemCount) {
            val holder = channelsRecyclerView.findViewHolderForAdapterPosition(i) as? ChannelAdapter.ChannelViewHolder
            holder?.binding?.rvChannelPrograms?.let { pr ->
                val listener = object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        // Zeitlinie scrollen
                        timeMarks.scrollBy(dx, 0)
                        // Andere ProgramRecyclerViews synchron scrollen
                        for (j in 0 until channelsRecyclerView.adapter!!.itemCount) {
                            val otherHolder = channelsRecyclerView.findViewHolderForAdapterPosition(j) as? ChannelAdapter.ChannelViewHolder
                            if (otherHolder?.binding?.rvChannelPrograms != pr) {
                                otherHolder?.binding?.rvChannelPrograms?.scrollBy(dx, 0)
                            }
                        }
                    }
                }
                pr.addOnScrollListener(listener)
                syncListeners.add(listener)
            }
        }
    }

    private var currentTvChannelsJob: Job? = null

    fun showChannelListInRecyclerview(accountTvCategoryId: Long) {
        if (tvGuideViewModel.currentFocusedTvCategory?.id != accountTvCategoryId) {
            val tvCategory = tvCatBox.get(accountTvCategoryId)
            tvGuideViewModel.currentFocusedTvCategory = tvCategory
            currentTvChannelsJob?.cancel()
            currentTvChannelsJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val isPlaylistActive =
                    tvGuideViewModel.currentFocusedTvAccount?.epgsources?.any { it.isSelected && it.isPlaylistEpg }
                val sortedChannels = getChannelList()
                if (sortedChannels.isNotEmpty()) {
                    binding.epgView.apply {
                        setDayShift(0)
                        val startDate = DateTime().minusMinutes(5)
                        val endTime = startDate.plusMinutes(5)
                        setStartHour(startDate.hourOfDay)
                        setEndHour(0)
                        listener = epgListener
                        val channelsWithEpg = sortedChannels.map { tvChannelPosition ->
                            val tvChannel = tvChannelPosition.tvchannel.target
                            val chEpgId = tvChannel.linkedEpgChannel?.target?.chEpgId
                                ?: if (isPlaylistActive == true) {
                                    tvChannel.epgChannel?.target?.chEpgId
                                } else {
                                    null
                                }
                            TvChannelWithEpg(
                                tvChannel.id,
                                tvChannelPosition,
                                helpViewModel.epgCache[chEpgId] ?: generateDummyEpg(tvChannelPosition.catAndChannelAccount)
                            )

                        }
                        withContext(Dispatchers.Main) {
                            initView(channelsWithEpg)
                        }
                    }
                }
            }
        }
    }

    fun generateDummyEpg(channelId: String): List<EpgDataOB> {
        val dummyEpgList = mutableListOf<EpgDataOB>()

        // Aktuelle Zeit als Unix-Zeitstempel in Sekunden
        // Die Berechnung muss direkt auf Sekundenbasis erfolgen
        var currentTimeInSeconds = System.currentTimeMillis() / 1000

        // Die Startzeit 2 Stunden vor der aktuellen Zeit
        currentTimeInSeconds -= TimeUnit.HOURS.toSeconds(2)

        // Das Ende ist das Ende des heutigen Tages
        val dayEndInSeconds = LocalDateTime.now().plusDays(1)
            .truncatedTo(ChronoUnit.DAYS)
            .atZone(ZoneId.systemDefault())
            .toEpochSecond()

        val showDurationInSeconds = TimeUnit.MINUTES.toSeconds(30) // 30-Minuten-Blöcke

        while (currentTimeInSeconds < dayEndInSeconds) {
            val endTimeInSeconds = currentTimeInSeconds + showDurationInSeconds

            dummyEpgList.add(
                EpgDataOB(
                    id = 0,
                    startTimestamp = currentTimeInSeconds,
                    stopTimestamp = endTimeInSeconds,
                    // Setzen Sie hier alle anderen notwendigen Dummy-Werte
                    name = "No Information",
                    idByAccountData = "dummy_${channelId}_$currentTimeInSeconds"
                )
            )
            currentTimeInSeconds = endTimeInSeconds
        }
        return dummyEpgList
    }

    private fun getChannelList(): List<ChannelPositions> {
        val sortedChannels = when {
            tvGuideViewModel.currentFocusedTvCategory!!.isAllChannelsCategory -> {
                tvGuideViewModel.currentFocusedTvAccount?.channels?.reset()
                val categories = tvGuideViewModel.currentFocusedTvAccount?.tvcategories
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
                tvGuideViewModel.currentFocusedTvCategory!!.tvChannelLink.reset()
                when (tvGuideViewModel.currentFocusedTvCategory!!.orderBy) {
                    0 -> {
                        val categoryLinks =
                            tvGuideViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.originalPosition }.filter {
                                it.isSelected
                            }

                        if (tvGuideViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
                            categoryLinks.filter { it.tvchannel.target.isFavorite }
                        } else {
                            categoryLinks
                        }
                    }

                    1 -> {
                        val categoryLinks =
                            tvGuideViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.tvchannel.target.showingName }.filter {
                                it.isSelected
                            }
                        if (tvGuideViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
                            categoryLinks.filter { it.tvchannel.target.isFavorite }
                        } else {
                            categoryLinks
                        }
                    }

                    else -> {
                        val categoryLinks =
                            tvGuideViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.position }.filter {
                                it.isSelected
                            }
                        if (tvGuideViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
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