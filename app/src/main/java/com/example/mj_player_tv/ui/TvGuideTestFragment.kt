package com.example.mj_player_tv.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
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
import com.example.mj_player_tv.databinding.FragmentTvguideTestBinding
import com.example.mj_player_tv.repository.TvGuideScrollSyncManager
import com.example.mj_player_tv.ui.adapter.TvGuideAccountCategoryAdapter
import com.example.mj_player_tv.ui.adapter.TvGuideAccountCategoryTestAdapter
import com.example.mj_player_tv.ui.tvguide.TvGuideRecyclerview
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.TvGuideViewModel
import com.example.mj_player_tv.viewmodel.TvGuideViewModelFactory
import com.volkov.EPGConfig
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

@UnstableApi
class TvGuideTestFragment : Fragment(R.layout.fragment_tvguide_test) {

    private var _binding: FragmentTvguideTestBinding? = null

    private val binding get() = _binding!!

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    private lateinit var tvGuideAccountCategoryAdapter: TvGuideAccountCategoryTestAdapter

    var firstChannelId = ""

    private var fullAccountList = listOf<AccountTvCategory>()
    private var expandedAccountId: Long? = null
    private var currentList = listOf<AccountTvCategory>()
    val scrollSyncManager = TvGuideScrollSyncManager()

    var currentDetailChannelPosition: ChannelPositions? = null

    private var currentDetailEpgData: EpgDataOB? = null
    private var isFirstOpen = true
    private val currentShowDetailHandler = Handler(Looper.getMainLooper())

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
        _binding = FragmentTvguideTestBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(EPGConfig) {
            marginVerticalChannelLogo = 2
        }
        binding.epgView.apply {

        }

        prepareAccountCategoryRecyclerView()
        var accountsList = listOf<AccountTvCategory>()

        helpViewModel.tvAccountsWithCategoriesLiveData.observe(viewLifecycleOwner) { accounts ->
            if (accounts.isEmpty()) {
                binding.tvNoTvAccounts.visibility = VISIBLE
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
        binding.epgView.focusEpgData()
    }

    private fun prepareAccountCategoryRecyclerView() {
        tvGuideAccountCategoryAdapter = TvGuideAccountCategoryTestAdapter(
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
                this@TvGuideTestFragment.requireActivity(),
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
            if (tvGuideAccountCategoryAdapter.currentList.isNotEmpty()) {
                binding.rvLayoutTvAccountsMenu.requestFocus()
            }
        } else {
            hideMainMenu()
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
                        val channelsWithEpg = sortedChannels.map { tvChannelPosition ->
                            val tvChannel = tvChannelPosition.tvchannel.target
                            val chEpgId = tvChannel.linkedEpgChannel?.target?.chEpgId
                                ?: if (isPlaylistActive == true) {
                                    tvChannel.epgChannel?.target?.chEpgId
                                } else {
                                    null
                                }
                            val originalEpg = helpViewModel.epgCache[chEpgId] ?: generateDummyEpg(tvChannelPosition.catAndChannelAccount)
                            val sortedEpg = originalEpg.sortedBy { it.startTimestamp }

                            val cleanedEpg = mutableListOf<EpgDataOB>()
                            var lastShowStopTimestamp = 0L // Startet bei 0, um die erste Sendung zu behandeln
                            if (sortedEpg.isNotEmpty()) {
                                lastShowStopTimestamp = sortedEpg.first().stopTimestamp
                                cleanedEpg.add(sortedEpg.first())

                                for (i in 1 until sortedEpg.size) {
                                    val currentShow = sortedEpg[i]

                                    // Überprüfe auf Lücken
                                    if (currentShow.startTimestamp > lastShowStopTimestamp) {
                                        val gapDurationSeconds = currentShow.startTimestamp - lastShowStopTimestamp
                                        val gapDurationMinutes = gapDurationSeconds / 60

                                        // Füge eine Lücken-Sendung hinzu, wenn die Lücke mindestens 5 Minuten beträgt
                                        if (gapDurationMinutes >= 5) {
                                            cleanedEpg.add(
                                                EpgDataOB(
                                                    id = 0,
                                                    startTimestamp = lastShowStopTimestamp,
                                                    stopTimestamp = currentShow.startTimestamp,
                                                    name = "No Information",
                                                    idByAccountData = "gap_${tvChannelPosition.catAndChannelAccount}_${lastShowStopTimestamp}"
                                                )
                                            )
                                        }
                                    }

                                    // Überprüfe auf Überlappungen
                                    // Wenn die Sendung nach der letzten Sendung beginnt, füge sie hinzu
                                    if (currentShow.startTimestamp >= lastShowStopTimestamp) {
                                        cleanedEpg.add(currentShow)
                                        lastShowStopTimestamp = currentShow.stopTimestamp
                                    }
                                    // Andernfalls (bei Überlappung) überspringen wir die Sendung
                                    // und `lastShowStopTimestamp` bleibt unverändert, um die nächste Sendung zu überprüfen
                                }
                            }
                            TvChannelWithEpg(
                                tvChannel.id,
                                tvChannelPosition,
                                cleanedEpg // Verwende die bereinigten Daten
                            )

                        }.sortedBy { it.tvChannelPosition.position }
                        firstChannelId = channelsWithEpg.firstOrNull()?.tvChannelPosition?.catAndChannelAccount ?: ""
                        withContext(Dispatchers.Main) {
                            binding.epgView.initFirstView(channelsWithEpg)

                        }
                    }
                }
            }
        }
    }

    fun generateDummyEpg(channelId: String): List<EpgDataOB> {
        val dummyEpgList = mutableListOf<EpgDataOB>()

        // Holen der aktuellen Zeit in der System-Zeitzone
        val now = DateTime.now(DateTimeZone.getDefault())

        // Auf die letzte volle Stunde runden und dann 30 Minuten abziehen
        var currentTime = now.withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0).minusMinutes(30)

        // Das Ende ist das Ende des heutigen Tages
        val dayEnd = now.plusDays(1).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)

        // Dauer der Dummy-Sendung ist eine Stunde
        val showDurationInSeconds = 60 * 60 // 60 Minuten = 3600 Sekunden

        while (currentTime.isBefore(dayEnd)) {
            val endTime = currentTime.plusSeconds(showDurationInSeconds)

            dummyEpgList.add(
                EpgDataOB(
                    id = 0,
                    startTimestamp = currentTime.millis / 1000,
                    stopTimestamp = endTime.millis / 1000,
                    name = "No Information",
                    idByAccountData = "dummy_${channelId}_${currentTime.millis}"
                )
            )
            currentTime = endTime
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

    private fun showDetailEpg(epgData: EpgDataOB) {
        // Stop any existing update process first
        stopLiveUpdate()

        // Store the EPG data in a member variable
        currentDetailEpgData = epgData

        val endTime = DateTime(epgData.stopTimestamp * 1000).toString("HH:mm")
        binding.tvCurrentStartTime.text = DateTime(epgData.startTimestamp * 1000).toString("HH:mm")
        binding.tvCurrentEndTime.text = " - $endTime"
        binding.tvCurrentProgram.text = epgData.name

        binding.tvCurrentSubtitle.visibility = if (epgData.sub_title.isEmpty()) View.GONE else VISIBLE
        binding.tvCurrentSubtitle.text = epgData.sub_title

        binding.tvDescription.text = epgData.descr.ifEmpty {
            resources.getString(R.string.no_description)
        }

        // Check if it's a live show
        val isLiveShow = epgData.startTimestamp < System.currentTimeMillis() / 1000 && epgData.stopTimestamp > System.currentTimeMillis() / 1000

        if (isLiveShow && !epgData.idByAccountData.startsWith("dummy_")) {
            // Show the ProgressBar and start the update
            binding.progressBar.visibility = VISIBLE
            currentShowDetailHandler.post(currentShowDetailRunnable)
        } else {
            // Hide the ProgressBar and stop any updates
            binding.tvRemainingTime.visibility = View.INVISIBLE
            binding.progressBar.visibility = View.INVISIBLE
        }
    }

    // Runnable to update the progress bar
    private val currentShowDetailRunnable = object : Runnable {
        override fun run() {
            val item = currentDetailEpgData ?: return

            // Get the current time in seconds
            val nowInSeconds = System.currentTimeMillis() / 1000
            val remainingSeconds = item.stopTimestamp - nowInSeconds
            val remainingMinutes = remainingSeconds / 60
            val remainingHours = remainingMinutes / 60
            val remainingMinutesInHour = remainingMinutes % 60

            // Erstelle den Text für die Anzeige
            val remainingText = when {
                remainingHours > 0 -> "${remainingHours}h ${remainingMinutesInHour}min remaining"
                remainingMinutes > 0 -> "${remainingMinutes}min remaining"
                remainingSeconds > 0 -> "${remainingSeconds}s remaining"
                else -> "ending now"
            }
            binding.tvRemainingTime.text = remainingText
            // Calculate progress based on pre-shifted times
            val progress = nowInSeconds - item.startTimestamp
            val progressMax = item.stopTimestamp - item.startTimestamp

            // Update the ProgressBar
            if (progressMax > 0) {
                binding.progressBar.max = progressMax.toInt()
                binding.progressBar.progress = progress.toInt()
            }

            // Post the next update after a small delay (e.g., 1 second)
            // A 1-second update is more responsive than 60 seconds
            currentShowDetailHandler.postDelayed(this, 1000)
        }
    }

    private fun stopLiveUpdate() {
        currentShowDetailHandler.removeCallbacksAndMessages(null)
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

    private fun playChannel(channelPositions: ChannelPositions) {
        if (binding.playerTv.isInvisible) {
            openPlayerFragment()
        } else {
            if (helpViewModel.currentPlayingChannelPosition?.id != channelPositions.id) {
                tvGuideViewModel.requestchangePlayingChannel(channelPositions)
            } else {
                return
            }
        }
    }

    private fun openPlayerFragment() {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.add(R.id.player_tv, PlayTvFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.playerTv.visibility = VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}