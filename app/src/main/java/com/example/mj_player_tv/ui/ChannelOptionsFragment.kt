package com.example.mj_player_tv.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgSourcePositions
import com.example.mj_player_tv.database.entity.EpgSourcePositions_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentChannelOptionsBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.launch

@UnstableApi
class ChannelOptionsFragment : Fragment(R.layout.fragment_channel_options) {

    private var _binding: FragmentChannelOptionsBinding? = null

    private val binding get() = _binding!!

    private val tvChannBox: Box<TvChannelOB> = ObjectBox.store.boxFor(TvChannelOB::class.java)

    private val epgSourcePosBox = ObjectBox.store.boxFor(EpgSourcePositions::class.java)

    private val accountBox = ObjectBox.store.boxFor(Accounts::class.java)

    private val chPosBox = ObjectBox.store.boxFor(ChannelPositions::class.java)

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
        _binding = FragmentChannelOptionsBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (containerFragment is TvChannelsFragment) {
                containerFragment.closeChannelOptionsContainer()
                containerFragment.setFocusToTvChannels()
            }
            helpViewModel.isChannelOptionsContainerOpened = false
            helpViewModel.modifiedChannelList = false
            helpViewModel.lastSelectedChannelOptionsMenuView = null
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        helpViewModel.lastSelectedChannelOptionsMenuView?.let {
            view.findViewById<RelativeLayout>(it)?.requestFocus() ?: binding.relLayoutFavorites.requestFocus()
        } ?: binding.relLayoutFavorites.requestFocus()

        if (helpViewModel.currentFocusedTvCategory != null) {
            binding.tvVisibility.text = if (helpViewModel.currentFocusedTvCategory!!.userCategory) {
                "Remove Channel"
            } else {
                "Hide Channel"
            }
        }

        helpViewModel.currentFocusedTvAccount = helpViewModel.currentFocusedChannPosition?.playlistId?.let { accountBox.get(it) }
        if (helpViewModel.currentFocusedChannPosition != null) {
            binding.tvChannelName.text = helpViewModel.currentFocusedChannel?.showingName
            if (helpViewModel.currentFocusedChannel!!.isFavorite) {
                binding.tvFavorite.text = "Remove from Favorites"
            } else {
                binding.tvFavorite.text = "Add to Favorites"
            }
        }

        binding.relLayoutFavorites.setOnFocusChangeListener { _, hasFocus ->
            binding.tvFavorite.isSelected = hasFocus
        }

        binding.relLayoutFavorites.setOnClickListener {
            if (helpViewModel.currentFocusedChannel != null) {
                if (helpViewModel.currentFocusedChannel!!.isFavorite) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        helpViewModel.currentFocusedChannel!!.isFavorite = false
                        helpViewModel.currentFocusedChannel?.let { it1 -> tvChannBox.put(it1) }
                        val chPosFav = helpViewModel.currentFocusedChannel!!.tvcategoryLink.find { it.tvcategory.target.isFavoriteCategory }
                        val thisChannelPosition = chPosFav?.position
                        if (thisChannelPosition != null) {
                            helpViewModel.currentFocusedTvCategory?.tvChannelLink?.filter { it.position > thisChannelPosition }?.forEach { channPos ->
                                val oldPosition = channPos.position
                                channPos.position = oldPosition - 1
                                val oldOriginalPos = channPos.originalPosition
                                channPos.originalPosition = oldOriginalPos - 1
                            }
                            chPosBox.put(helpViewModel.currentFocusedTvCategory?.tvChannelLink)
                        }
                        if (chPosFav != null) {
                            chPosBox.remove(chPosFav)
                        }
                        updateSingleChannel()
                        helpViewModel.updateFocusedChannel(helpViewModel.currentFocusedChannPosition!!)
                        if (helpViewModel.currentFocusedTvCategory?.isFavoriteCategory == true) {
                            updateChannelList()
                        }
                        binding.tvFavorite.text = "Add to Favorites"
                        helpViewModel.currentFocusedChannel?.let {
                            helpViewModel.checkifFirstFavoriteChannel(it.account.target, false)
                        }
                        val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                        if (mainFragment is TvChannelsFragment) {
                            mainFragment.closeChannelOptionsContainer()
                            mainFragment.setFocusToTvChannels()
                            helpViewModel.isChannelOptionsContainerOpened = false
                            helpViewModel.modifiedChannelList = false
                            parentFragmentManager.popBackStack()
                        }
                    }
                } else {
                    viewLifecycleOwner.lifecycleScope.launch {
                        helpViewModel.currentFocusedChannel!!.isFavorite = true
                        val favCategory = helpViewModel.currentFocusedTvAccount!!.tvcategories.find { it.isFavoriteCategory }
                        val favoriteNumber = favCategory?.tvChannelLink?.count() ?: 0
                        helpViewModel.currentFocusedChannel?.let { it1 -> tvChannBox.put(it1) }
                        val chPosition = ChannelPositions(
                            id = 0,
                            helpViewModel.currentFocusedChannel!!.idByAccountData,
                            helpViewModel.currentFocusedTvAccount!!.id,
                            helpViewModel.currentFocusedTvCategory!!.idByAccountData,
                            favoriteNumber,
                            favoriteNumber,
                            "${favCategory!!.idByAccountData}_${helpViewModel.currentFocusedChannel!!.idByAccountData}_${helpViewModel.currentFocusedChannel!!.account.target.id}"
                        )
                        chPosition.tvcategory.target = favCategory
                        chPosition.tvchannel.target = helpViewModel.currentFocusedChannel
                        chPosBox.put(chPosition)
                        updateSingleChannel()
                        helpViewModel.updateFocusedChannel(helpViewModel.currentFocusedChannPosition!!)
                        if (helpViewModel.currentFocusedTvCategory?.isFavoriteCategory == true) {
                            updateChannelList()
                        }
                        binding.tvFavorite.text = "Remove from Favorites"
                        helpViewModel.currentFocusedChannel?.let {
                            helpViewModel.checkifFirstFavoriteChannel(it.account.target, true)
                        }
                        val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                        if (mainFragment is TvChannelsFragment) {
                            mainFragment.closeChannelOptionsContainer()
                            mainFragment.setFocusToTvChannels()
                            helpViewModel.isChannelOptionsContainerOpened = false
                            helpViewModel.modifiedChannelList = false
                            parentFragmentManager.popBackStack()
                        }
                    }
                }
            }
        }

        binding.relLayoutVisibility.setOnFocusChangeListener { _, hasFocus ->
            binding.tvVisibility.isSelected = hasFocus
        }

        binding.relLayoutVisibility.setOnClickListener {
            if (helpViewModel.currentFocusedTvCategory != null) {
                if (helpViewModel.currentFocusedTvCategory!!.userCategory) {
                    helpViewModel.currentFocusedChannPosition?.let { chPosBox.remove(it)  }
                    helpViewModel.isChannelHide = true
                    updateChannelList()
                    val containerFragment =
                        parentFragmentManager.findFragmentById(R.id.navHostFragment)
                    if (containerFragment is TvChannelsFragment) {
                        containerFragment.resetLayoutAlpha()
                        containerFragment.setFocusToTvChannels()
                    }
                    parentFragmentManager.popBackStack()
                } else {
                    if (helpViewModel.currentFocusedChannPosition != null) {
                        helpViewModel.currentFocusedChannPosition!!.isSelected = false
                        viewLifecycleOwner.lifecycleScope.launch {
                            helpViewModel.currentFocusedChannPosition!!.isSelected = false
                            helpViewModel.currentFocusedChannPosition?.let { it1 -> chPosBox.put(it1) }
                            helpViewModel.isChannelHide = true
                            updateChannelList()
                            helpViewModel.updateFocusedChannel(helpViewModel.currentFocusedChannPosition!!)
                            val containerFragment =
                                parentFragmentManager.findFragmentById(R.id.navHostFragment)
                            if (containerFragment is TvChannelsFragment) {
                                containerFragment.resetLayoutAlpha()
                                containerFragment.setFocusToTvChannels()
                                }
                            parentFragmentManager.popBackStack()
                            }
                        }
                }
            }
        }

        binding.relLayoutEdit.setOnFocusChangeListener { _, hasFocus ->
            binding.tvEditChannel.isSelected = hasFocus
        }

        binding.relLayoutEdit.setOnClickListener {
            helpViewModel.lastSelectedChannelOptionsMenuView = binding.relLayoutEdit.id
            changeFragment(EditChannelNameFragment())
        }

        binding.relLayoutCopyChannel.setOnFocusChangeListener { _, hasFocus ->
            binding.tvCopyChannel.isSelected = hasFocus
        }

        binding.relLayoutCopyChannel.setOnClickListener {
            helpViewModel.lastSelectedChannelOptionsMenuView = binding.relLayoutCopyChannel.id
            changeFragment(CopyChannelToCategoryFragment())
        }

        binding.relLayoutAssignEpg.setOnFocusChangeListener { _, hasFocus ->
            binding.tvAssignEpg.isSelected = hasFocus
        }

        binding.relLayoutAssignEpg.setOnClickListener {
            helpViewModel.lastSelectedChannelOptionsMenuView = binding.relLayoutAssignEpg.id
            helpViewModel.currentFocusedTvAccount?.epgsources?.reset()
            if (helpViewModel.currentFocusedTvAccount != null && helpViewModel.currentFocusedTvAccount!!.epgsources.isNotEmpty()) {
                helpViewModel.currentAssignEpgChannel = helpViewModel.currentFocusedChannel
                helpViewModel.assignChannelToEpgActive = true
                val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                if (containerFragment is TvChannelsFragment) {
                    containerFragment.setMinimalAlpha()
                    containerFragment.makeChannelOptionsContainerInvisible()
                    containerFragment.notifyChannelAdapterAssignEpgActive()
                    containerFragment.setVisibilityAssignChannelEpg(true)
                }
            } else {
                Toast.makeText(this@ChannelOptionsFragment.requireActivity(), "No EPG-Sources for this playlist! Assign them in settings!", Toast.LENGTH_LONG).show()
            }
        }


        binding.relLayoutOrderChannels.setOnFocusChangeListener { _, hasFocus ->
            binding.tvOrderChannels.isSelected = hasFocus
        }

        binding.relLayoutOrderChannels.setOnClickListener {
            val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (containerFragment is TvChannelsFragment) {
                containerFragment.closeChannelOptionsContainer()
                if (helpViewModel.currentFocusedChannPosition != null) {
                    containerFragment.notifyChannelAdapterOrderChannels(helpViewModel.currentFocusedChannPosition!!)
                }
            }
            parentFragmentManager.popBackStack()
        }

        binding.relLayoutEpgTimeOffset.setOnFocusChangeListener { _, hasFocus ->
            binding.tvTimeOffSet.isSelected = hasFocus
        }

        binding.relLayoutChannelEditor.setOnFocusChangeListener { _, hasFocus ->
            binding.tvChannelEditor.isSelected = hasFocus
        }

        binding.relLayoutChannelEditor.setOnClickListener {
            helpViewModel.lastSelectedChannelOptionsMenuView = binding.relLayoutChannelEditor.id
            changeFragment(ManageTvChannelsFragment())
        }
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_ChannelOptions, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    fun focusLastSelectedItem() {
        val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (containerFragment is TvChannelsFragment) {
            containerFragment.resetLayoutAlpha()
            containerFragment.setVisibilityAssignChannelEpg(false)
        }
        helpViewModel.lastSelectedChannelOptionsMenuView?.let {
            view?.findViewById<RelativeLayout>(it)?.requestFocus() ?: binding.relLayoutFavorites.requestFocus()
        } ?: binding.relLayoutFavorites.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun updateChannelList() {
        val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (containerFragment is TvChannelsFragment) {
            containerFragment.updateChannelList()
        }
    }

    fun updateSingleChannel() {
        val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (containerFragment is TvChannelsFragment) {
            containerFragment.updateSingleChannel()
        }
    }


}