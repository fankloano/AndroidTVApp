package com.example.mj_player_tv.ui

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.animation.doOnEnd
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.ChannelPositions_
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgDataOB_
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvCategoryOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.entity.TvChannelOB_
import com.example.mj_player_tv.databinding.FragmentFullscreenSelectorBinding
import com.example.mj_player_tv.ui.adapter.FullscreenChannelAdapter
import com.example.mj_player_tv.ui.adapter.FullscreenTvAccountsAdapter
import com.example.mj_player_tv.ui.adapter.FullscreenTvCategoryAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import com.rubensousa.dpadrecyclerview.ViewHolderTask
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import io.objectbox.Box
import io.objectbox.kotlin.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@UnstableApi
class FullScreenSelectorFragment : Fragment(R.layout.fragment_fullscreen_selector) {

    private var _binding: FragmentFullscreenSelectorBinding? = null

    private val binding get() = _binding!!

    private var fullscreenChannelsAdapter: FullscreenChannelAdapter? = null

    private var fullscreenTvCategoryAdapter: FullscreenTvCategoryAdapter? = null

    private var fullScreenAccountAdapter: FullscreenTvAccountsAdapter? = null

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    private val epgDataBox: Box<EpgDataOB> = ObjectBox.store.boxFor(EpgDataOB::class.java)


    private val stalkerViewModel: StalkerViewModel by activityViewModels {
        StalkerViewModelFactory(
            requireActivity().application
        )
    }

    private val xtreamViewModel: XtreamViewModel by activityViewModels {
        XtreamViewModelFactory(
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
        _binding = FragmentFullscreenSelectorBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            fullscreenChannelsAdapter?.submitList(null)
            fullscreenTvCategoryAdapter?.submitList(null)
            fullScreenAccountAdapter?.submitList(null)
            setTvAccountsVisibilityAnimated(false)
            setTvCategoriesVisibilityAnimated(false)
            setTvChannelsVisibilityAnimated(true)
            val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (containerFragment is TvChannelsFragment) {
                containerFragment.setFocusToVideoView()
            }
            helpViewModel.isChannelOptionsContainerOpened = false
            helpViewModel.modifiedChannelList = false
            val fsChannelEpgFragment = parentFragmentManager.findFragmentById(R.id.container_fullscreen_epgInfo)
            if (fsChannelEpgFragment is FullScreenChannelSelectorEpg) {
                fsChannelEpgFragment.closeFragment()
            }
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareTvChannelsRecyclerView()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val sortedChannels = when {
                helpViewModel.fullScreenFocusedTvCategory?.isAllChannelsCategory == true -> {
                    helpViewModel.fullScreenFocusedAccount?.channels?.reset()
                    val categories = helpViewModel.fullScreenFocusedAccount?.tvcategories
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
                    helpViewModel.fullScreenFocusedTvCategory?.tvChannelLink?.reset()
                    when (helpViewModel.fullScreenFocusedTvCategory?.orderBy) {
                        0 -> {
                            val categoryLinks =
                                helpViewModel.fullScreenFocusedTvCategory?.tvChannelLink?.sortedBy { it.originalPosition }?.filter {
                                    it.isSelected
                                }

                            if (helpViewModel.fullScreenFocusedTvCategory?.isFavoriteCategory == true) {
                                categoryLinks?.filter { it.tvchannel.target.isFavorite }
                            } else {
                                categoryLinks
                            }
                        }

                        1 -> {
                            val categoryLinks =
                                helpViewModel.fullScreenFocusedTvCategory?.tvChannelLink?.sortedBy { it.tvchannel.target.showingName }?.filter {
                                    it.isSelected
                                }
                            if (helpViewModel.fullScreenFocusedTvCategory?.isFavoriteCategory == true) {
                                categoryLinks?.filter { it.tvchannel.target.isFavorite }
                            } else {
                                categoryLinks
                            }
                        }

                        else -> {
                            val categoryLinks =
                                helpViewModel.fullScreenFocusedTvCategory?.tvChannelLink?.sortedBy { it.position }?.filter { it.isSelected }
                            if (helpViewModel.fullScreenFocusedTvCategory?.isFavoriteCategory == true) {
                                categoryLinks?.filter { it.tvchannel.target.isFavorite }
                            } else {
                                categoryLinks
                            }
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                fullscreenChannelsAdapter?.submitList(sortedChannels)
                val currentPlayingPosition =
                    fullscreenChannelsAdapter?.currentList!!.indexOf(helpViewModel.currentPlayingChannelPosition)
                binding.rvLayoutChangeTvChannels.post {
                    binding.rvLayoutChangeTvChannels.setSelectedPosition(currentPlayingPosition)
                    binding.rvLayoutChangeTvChannels.requestFocus()
                    showChannelEpgContainer()
                }

            }
            getCategoriesAndAccounts()
        }
    }

    private fun getCategoriesAndAccounts() {
        prepareTvCategoryRecyclerView()
        prepareTvAccountsRecyclerView()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            if (helpViewModel.fullScreenFocusedAccount != null) {
                val categoriesQuery = tvCatBox.query(
                    TvCategoryOB_.playlistId.equal(helpViewModel.fullScreenFocusedAccount!!.id)
                        .and(TvCategoryOB_.favorite.equal(true))
                ).build()
                val categories = categoriesQuery.find()
                categoriesQuery.close()
                withContext(Dispatchers.Main) {
                    fullscreenTvCategoryAdapter?.submitList(categories)
                    val position =
                        fullscreenTvCategoryAdapter?.currentList!!.indexOf(helpViewModel.fullScreenFocusedTvCategory)
                    binding.rvLayoutChangeTvCategory.post {
                        binding.rvLayoutChangeTvCategory.setSelectedPosition(position)
                    }
                }
            }

            val accounts = accountBox.all.filter { it.isSelected && it.showTv }
            withContext(Dispatchers.Main) {
                fullScreenAccountAdapter?.submitList(accounts)
                val position =
                    fullScreenAccountAdapter?.currentList!!.indexOf(helpViewModel.fullScreenFocusedAccount)
                binding.rvLayoutChangeTvAccounts.post {
                    binding.rvLayoutChangeTvAccounts.setSelectedPosition(position)
                }
            }
        }
    }

    private fun prepareTvChannelsRecyclerView() {
        fullscreenChannelsAdapter = FullscreenChannelAdapter(this, onChannelclickListener, helpViewModel, epgDataBox)
        binding.rvLayoutChangeTvChannels.apply {
            adapter = fullscreenChannelsAdapter
            addItemDecoration(
                DpadLinearSpacingDecoration.create(
                    itemSpacing = 3,
                    edgeSpacing = 3,
                    perpendicularEdgeSpacing = 3
                )
            )
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(false, false)
        }
    }

    private val onChannelclickListener = FullscreenChannelAdapter.OnClickListener {
        if (helpViewModel.currentPlayingChannel?.idByAccountData != it.tvchannel.target.idByAccountData) {
            val currentPosition = fullscreenChannelsAdapter?.currentList!!.indexOf(helpViewModel.currentPlayingChannelPosition)
            val newPosition = fullscreenChannelsAdapter?.currentList!!.indexOf(it)
            val parentFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (parentFragment is TvChannelsFragment) {
                parentFragment.changingPlayingChannel(it)
                parentFragment.refreshLists()
            }
            fullscreenChannelsAdapter?.notifyItemChanged(currentPosition)
            fullscreenChannelsAdapter?.notifyItemChanged(newPosition)
            binding.rvLayoutChangeTvChannels.requestFocus()
        } else {
            helpViewModel.fullScreenFocusedChannel = null
            helpViewModel.fullScreenFocusedTvCategory = null
            helpViewModel.fullScreenFocusedAccount = null
            helpViewModel.isChannelOptionsContainerOpened = false
            helpViewModel.modifiedChannelList = false
            closeFragment()
        }
    }


    private fun prepareTvCategoryRecyclerView() {
        fullscreenTvCategoryAdapter = FullscreenTvCategoryAdapter(this, onCategoryClickListener, helpViewModel)
        binding.rvLayoutChangeTvCategory.apply {
            adapter = fullscreenTvCategoryAdapter
            addItemDecoration(
                DpadLinearSpacingDecoration.create(
                    itemSpacing = 3,
                    edgeSpacing = 3,
                    perpendicularEdgeSpacing = 3
                )
            )
            visibility = View.VISIBLE
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(false, false)
        }
    }

    private val onCategoryClickListener = FullscreenTvCategoryAdapter.OnClickListener {

    }

    private fun prepareTvAccountsRecyclerView() {
        fullScreenAccountAdapter = FullscreenTvAccountsAdapter(this, onAccountClickListener, helpViewModel)
        binding.rvLayoutChangeTvAccounts.apply {
            adapter = fullScreenAccountAdapter
            addItemDecoration(
                DpadLinearSpacingDecoration.create(
                    itemSpacing = 3,
                    edgeSpacing = 3,
                    perpendicularEdgeSpacing = 3
                )
            )
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(false, false)
        }
    }

    private val onAccountClickListener = FullscreenTvAccountsAdapter.OnClickListener {

    }

    fun setTvChannelsVisibilityAnimated(isVisible: Boolean) {
        val channelsRecyclerView = binding.rvLayoutChangeTvChannels
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.constTvFullscreen.findViewById<ConstraintLayout>(R.id.const_tv_fullscreen))
        if (isVisible) {
            constraintSet.clear(channelsRecyclerView.id, ConstraintSet.START)
            constraintSet.connect(channelsRecyclerView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            binding.rvLayoutChangeTvChannels.isFocusable = true
            binding.rvLayoutChangeTvChannels.isFocusableInTouchMode = true
            binding.rvLayoutChangeTvChannels.requestFocus()
            val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (containerFragment is TvChannelsFragment) {
                containerFragment.showFullScreenChannelEpg()
            }
        } else {
            binding.rvLayoutChangeTvChannels.isFocusable = false
            binding.rvLayoutChangeTvChannels.isFocusableInTouchMode = false
            constraintSet.clear(channelsRecyclerView.id, ConstraintSet.START)
            constraintSet.connect(channelsRecyclerView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.END)
        }
        val targetX = if (isVisible) 0f else binding.constTvFullscreen.width.toFloat()

        ObjectAnimator.ofFloat(binding.rvLayoutChangeTvChannels, "translationX", targetX).apply {
            duration = 150 // Animation dauert 300 ms
            doOnEnd {
                if (helpViewModel.isFullScreenFullEpg) {
                    (parentFragmentManager.findFragmentById(R.id.navHostFragment) as? TvChannelsFragment)
                        ?.showFullScreenFullEpg()
                }
            }
            start()
        }


    }

    fun setTvAccountsVisibilityAnimated(isVisible: Boolean) {
        val accountsRecyclerView = binding.rvLayoutChangeTvAccounts
        val tvcategoriesRecyclerview = binding.rvLayoutChangeTvCategory
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.constTvFullscreen.findViewById<ConstraintLayout>(R.id.const_tv_fullscreen))
        if (isVisible) {
            constraintSet.clear(accountsRecyclerView.id, ConstraintSet.START)
            constraintSet.clear(accountsRecyclerView.id, ConstraintSet.END)
            constraintSet.connect(accountsRecyclerView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            binding.rvLayoutChangeTvAccounts.isFocusable = true
            binding.rvLayoutChangeTvAccounts.isFocusableInTouchMode = true
        } else {
            binding.rvLayoutChangeTvAccounts.isFocusable = false
            binding.rvLayoutChangeTvAccounts.isFocusableInTouchMode = false
            constraintSet.clear(accountsRecyclerView.id, ConstraintSet.START)
            constraintSet.clear(accountsRecyclerView.id, ConstraintSet.END)
            constraintSet.connect(accountsRecyclerView.id, ConstraintSet.END, tvcategoriesRecyclerview.id, ConstraintSet.START)
        }

        val transition = ChangeBounds()

        TransitionManager.beginDelayedTransition(binding.constTvFullscreen.findViewById(R.id.const_tv_fullscreen), transition)
        constraintSet.applyTo(binding.constTvFullscreen.findViewById(R.id.const_tv_fullscreen))
    }

    fun setTvCategoriesVisibilityAnimated(isVisible: Boolean) {
        val tvCategoriesRecyclerView = binding.rvLayoutChangeTvCategory
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.constTvFullscreen.findViewById<ConstraintLayout>(R.id.const_tv_fullscreen))
        if (isVisible) {
            binding.rvLayoutChangeTvCategory.isFocusable = true
            binding.rvLayoutChangeTvCategory.isFocusableInTouchMode = true
            constraintSet.clear(tvCategoriesRecyclerView.id, ConstraintSet.START)
            constraintSet.clear(tvCategoriesRecyclerView.id, ConstraintSet.END)
            constraintSet.connect(tvCategoriesRecyclerView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        } else {
            binding.rvLayoutChangeTvCategory.isFocusable = false
            binding.rvLayoutChangeTvCategory.isFocusableInTouchMode = false
            constraintSet.clear(tvCategoriesRecyclerView.id, ConstraintSet.START)
            constraintSet.clear(tvCategoriesRecyclerView.id, ConstraintSet.END)
            constraintSet.connect(tvCategoriesRecyclerView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.START)
        }

        val transition = ChangeBounds()

        TransitionManager.beginDelayedTransition(binding.constTvFullscreen.findViewById(R.id.const_tv_fullscreen), transition)
        constraintSet.applyTo(binding.constTvFullscreen.findViewById(R.id.const_tv_fullscreen))
    }

    fun focusToTvCategoryFromChannel() {
        val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (containerFragment is TvChannelsFragment) {
            containerFragment.hideFullScreenChannelEpg()
        }
        binding.rvLayoutChangeTvCategory.requestFocus()
    }

    fun focusToTvCategoryFromAccount() {
        binding.rvLayoutChangeTvCategory.requestFocus()
    }

    fun focusToAccountFromTvCategory(accountId: Long) {
        val account = fullScreenAccountAdapter?.currentList!!.firstOrNull { it.id == accountId }
        val position = fullScreenAccountAdapter?.currentList!!.indexOf(account)
        binding.rvLayoutChangeTvAccounts.requestFocus()
        binding.rvLayoutChangeTvAccounts.setSelectedPosition(
            position,
            object : ViewHolderTask() {
                override fun execute(viewHolder: RecyclerView.ViewHolder) {
                    viewHolder.itemView.requestFocus()
                }
            })
    }

    fun closeFragment() {
        helpViewModel.fullScreenFocusedChannel = null
        helpViewModel.fullScreenFocusedTvCategory = null
        helpViewModel.fullScreenFocusedAccount = null
        fullscreenChannelsAdapter?.submitList(null)
        fullscreenTvCategoryAdapter?.submitList(null)
        fullScreenAccountAdapter?.submitList(null)
        setTvAccountsVisibilityAnimated(false)
        setTvCategoriesVisibilityAnimated(false)
        setTvChannelsVisibilityAnimated(true)
        helpViewModel.isChannelOptionsContainerOpened = false
        helpViewModel.modifiedChannelList = false
        val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (containerFragment is TvChannelsFragment) {
            containerFragment.closeFullScreenChannelSelectorEpg()
            containerFragment.setFocusToVideoView()
        }
        parentFragmentManager.popBackStack()
    }

    fun showChannelEpgContainer() {
        val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (containerFragment is TvChannelsFragment) {
            containerFragment.showFullScreenChannelSelectorEpg()
        }
    }


    fun showFullScreenEpg() {
        helpViewModel.isFullScreenFullEpg = true
        setTvChannelsVisibilityAnimated(false)
        setTvCategoriesVisibilityAnimated(false)
        setTvAccountsVisibilityAnimated(false)
    }

    fun setFocusToTvChannels() {
        val currentPositon = fullscreenChannelsAdapter?.currentList?.indexOf(helpViewModel.fullScreenFocusedChannelPosition)
        if (currentPositon != null) {
            binding.rvLayoutChangeTvChannels.post {
                binding.rvLayoutChangeTvChannels.setSelectedPosition(currentPositon)
                binding.rvLayoutChangeTvChannels.requestFocus()
            }
        } else {
            binding.rvLayoutChangeTvChannels.requestFocus()
        }
    }

    fun showChannelEpg(tvchannelPos: ChannelPositions) {
        if (helpViewModel.fullScreenFocusedChannel != tvchannelPos.tvchannel.target) {
            helpViewModel.fullScreenFocusedChannel = tvchannelPos.tvchannel.target
            val epgContainerFragment =
                parentFragmentManager.findFragmentById(R.id.container_fullscreen_epgInfo)
            if (epgContainerFragment is FullScreenChannelSelectorEpg) {
                epgContainerFragment.showEpgInfo(tvchannelPos)
            }
        }
    }

    private var currentEpgJob: Job? = null
    private var currentTvChannelsJob: Job? = null

    private var firstOpenTvCategory = true

    fun showChannelList(tvCategory: TvCategoryOB) {
        if (helpViewModel.fullScreenFocusedTvCategory?.idByAccountData != tvCategory.idByAccountData) {
            fullscreenChannelsAdapter?.submitList(null)
            firstOpenTvCategory = true
            currentEpgJob?.cancel()
            currentTvChannelsJob?.cancel()
            helpViewModel.fullScreenFocusedTvCategory = tvCategory
            currentTvChannelsJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val sortedChannels = when {
                    helpViewModel.fullScreenFocusedTvCategory!!.isAllChannelsCategory -> {
                        helpViewModel.fullScreenFocusedAccount?.channels?.reset()
                        val categories = helpViewModel.fullScreenFocusedAccount?.tvcategories
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
                        helpViewModel.fullScreenFocusedTvCategory!!.tvChannelLink.reset()
                        when (helpViewModel.fullScreenFocusedTvCategory!!.orderBy) {
                            0 -> {
                                val categoryLinks =
                                    helpViewModel.fullScreenFocusedTvCategory!!.tvChannelLink.sortedBy { it.originalPosition }.filter {
                                        it.isSelected
                                    }

                                if (helpViewModel.fullScreenFocusedTvCategory!!.isFavoriteCategory) {
                                    categoryLinks.filter { it.tvchannel.target.isFavorite }
                                } else {
                                    categoryLinks
                                }
                            }

                            1 -> {
                                val categoryLinks =
                                    helpViewModel.fullScreenFocusedTvCategory!!.tvChannelLink.sortedBy { it.tvchannel.target.showingName }.filter {
                                        it.isSelected
                                    }
                                if (helpViewModel.fullScreenFocusedTvCategory!!.isFavoriteCategory) {
                                    categoryLinks.filter { it.tvchannel.target.isFavorite }
                                } else {
                                    categoryLinks
                                }
                            }

                            else -> {
                                val categoryLinks =
                                    helpViewModel.fullScreenFocusedTvCategory!!.tvChannelLink.sortedBy { it.position }.filter { it.isSelected }
                                if (helpViewModel.fullScreenFocusedTvCategory!!.isFavoriteCategory) {
                                    categoryLinks.filter { it.tvchannel.target.isFavorite }
                                } else {
                                    categoryLinks
                                }
                            }
                        }
                    }
                }
                if (sortedChannels.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        fullscreenChannelsAdapter?.submitList(sortedChannels)
                    }
                    if (firstOpenTvCategory) {
                        checkChannelEpg(sortedChannels)
                        firstOpenTvCategory = false
                    }
                }
            }
        }
    }

    fun checkChannelEpg(channelPositions: List<ChannelPositions>) {
        val currentTime = System.currentTimeMillis() / 1000
        currentEpgJob = viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                channelPositions.filter { channelPos ->
                    val tvChannel = channelPos.tvchannel.target
                    val currentTimeMillis = (System.currentTimeMillis() / 1000).plus(calculateTimeOffsetInSeconds(
                        tvChannel.epgTimeOffSet ?: channelPos.tvcategory.target.epgTimeOffSet ?: tvChannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0)
                    )
                    val epgChId = tvChannel.linkedEpgChannel?.target?.chEpgId
                    (tvChannel.linkedEpgChannel?.target == null) ||
                            !tvChannel.usesExternalEpg
                }.forEach {
                    val tvChannel = it.tvchannel.target
                    val newChannel = if (tvChannel.account.target.isStalker) {
                        stalkerViewModel.checkChannelsAndShortEpg(tvChannel)
                    } else if (tvChannel.account.target.isXtream) {
                        xtreamViewModel.checkChannelsAndShortEpg(tvChannel)
                    } else {
                        null
                    }
                    if (newChannel?.linkedEpgChannel?.target == null ||
                        newChannel.linkedEpgChannel?.target?.chEpgId?.let { chEpgId ->
                            EpgDataOB_.epgChId.equal(
                                chEpgId
                            )
                        }?.let { it1 -> epgDataBox.query(it1).build().find() } == null) {
                        val matchedChannel = helpViewModel.matchSingleChannelWithEpgChannels(
                            tvChannel,
                            helpViewModel.currentFocusedTvAccount!!
                        )
                        if (matchedChannel.linkedEpgChannel?.target != null) {
                            val position = fullscreenChannelsAdapter?.currentList?.indexOf(it)
                            if (position != null) {
                                withContext(Dispatchers.Main) {
                                    if (matchedChannel == helpViewModel.currentFocusedChannPosition) {
                                        helpViewModel.currentFocusedChannPosition?.let {
                                            showChannelEpg(it)
                                        }
                                    }
                                    fullscreenChannelsAdapter?.notifyItemChanged(position)
                                }
                            }
                        }
                    } else {
                        val position = fullscreenChannelsAdapter?.currentList?.indexOf(it)
                        if (position != null) {
                            withContext(Dispatchers.Main) {
                                if (newChannel == helpViewModel.currentFocusedChannPosition) {
                                    helpViewModel.currentFocusedChannPosition?.let {
                                        showChannelEpg(it)
                                    }
                                }
                                fullscreenChannelsAdapter?.notifyItemChanged(position)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun calculateTimeOffsetInSeconds(timeOffset: Int): Long {
        return (timeOffset * 3600).toLong()
    }

    fun showTvCategories(accounts: Accounts) {
        val categoriesQuery = tvCatBox.query(TvCategoryOB_.playlistId.equal(accounts.id)
            .and(TvCategoryOB_.favorite.equal(true))).build()
        val categories = categoriesQuery.find()
        categoriesQuery.close()
        fullscreenTvCategoryAdapter?.submitList(categories)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fullscreenChannelsAdapter?.stopRunnable()
        binding.rvLayoutChangeTvChannels.adapter = null
        binding.rvLayoutChangeTvCategory.adapter = null
        binding.rvLayoutChangeTvAccounts.adapter = null
        fullscreenTvCategoryAdapter = null
        fullScreenAccountAdapter = null
        fullscreenChannelsAdapter = null
        fullscreenChannelsAdapter?.handler?.removeCallbacksAndMessages(null)
        _binding = null
    }
}