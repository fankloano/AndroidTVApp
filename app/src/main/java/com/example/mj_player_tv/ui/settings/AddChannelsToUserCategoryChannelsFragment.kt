package com.example.mj_player_tv.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.example.mj_player_tv.database.entity.ChannelPositions_
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.entity.TvChannelOB_
import com.example.mj_player_tv.databinding.FragmentAddchannelschannelsBinding
import com.example.mj_player_tv.databinding.FragmentManageTvchannelsBinding
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.ui.adapter.AddTvChannelsToCategoryAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess


@UnstableApi
class AddChannelsToUserCategoryChannelsFragment: Fragment(R.layout.fragment_addchannelschannels) {

    private var _binding: FragmentAddchannelschannelsBinding? = null

    private val binding get() = _binding!!

    private lateinit var addTvChannelsToCategoryAdapter: AddTvChannelsToCategoryAdapter

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    private val manualPositionBox: Box<ChannelPositions> = ObjectBox.store.boxFor(ChannelPositions::class.java)

    private val tvChannBox: Box<TvChannelOB> = ObjectBox.store.boxFor(TvChannelOB::class.java)

    private val helpViewModel: HelpViewModel by activityViewModels {
        HelpViewModelFactory(
            requireActivity().application
        )
    }

    private val stalkerViewModel: StalkerViewModel by activityViewModels {
        StalkerViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddchannelschannelsBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareRecyclerView()

        if (helpViewModel.addChannelsToUserCategoryFromCategory != null) {

            binding.btnSelectAll.visibility = View.VISIBLE
            viewLifecycleOwner.lifecycleScope.launch {
                val tvChannels =
                    helpViewModel.addChannelsToUserCategoryFromCategory!!.tvChannelLink
                if (tvChannels.isNotEmpty()) {

                    val sortedChannels = when {
                        helpViewModel.addChannelsToUserCategoryFromCategory!!.isAllChannelsCategory -> {
                            helpViewModel.addChannelsToUserCategoryFromAccount?.channels?.reset()
                            val categories =
                                helpViewModel.addChannelsToUserCategoryFromAccount?.tvcategories
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
                            helpViewModel.addChannelsToUserCategoryFromCategory!!.tvChannelLink.reset()
                            when (helpViewModel.addChannelsToUserCategoryFromCategory!!.orderBy) {
                                0 -> {
                                    val categoryLinks =
                                        helpViewModel.addChannelsToUserCategoryFromCategory!!.tvChannelLink.filter { it.isSelected }.sortedBy { it.originalPosition }
                                    if (helpViewModel.addChannelsToUserCategoryFromCategory!!.isFavoriteCategory) {
                                        categoryLinks.filter { it.tvchannel.target.isFavorite }
                                    } else {
                                        categoryLinks
                                    }
                                }

                                1 -> {
                                    val categoryLinks =
                                        helpViewModel.addChannelsToUserCategoryFromCategory!!.tvChannelLink.filter { it.isSelected }.sortedBy { it.tvchannel.target.showingName }
                                    if (helpViewModel.addChannelsToUserCategoryFromCategory!!.isFavoriteCategory) {
                                        categoryLinks.filter { it.tvchannel.target.isFavorite }
                                    } else {
                                        categoryLinks
                                    }
                                }

                                else -> {
                                    val categoryLinks =
                                        helpViewModel.addChannelsToUserCategoryFromCategory!!.tvChannelLink.filter { it.isSelected }.sortedBy { it.position }
                                    if (helpViewModel.addChannelsToUserCategoryFromCategory!!.isFavoriteCategory) {
                                        categoryLinks.filter { it.tvchannel.target.isFavorite }
                                    } else {
                                        categoryLinks
                                    }
                                }
                            }
                        }
                    }
                    passChannelList()
                    addTvChannelsToCategoryAdapter.submitList(sortedChannels)
                    binding.rvLayoutTvChannels.requestFocus()
                }
            }
        }

            binding.btnSelectAll.setOnClickListener {
                binding.btnSelectAll.visibility = View.GONE
                binding.btnDeselectAll.visibility = View.VISIBLE
                binding.btnDeselectAll.requestFocus()

                viewLifecycleOwner.lifecycleScope.launch {
                    helpViewModel.addChannelsToUserCategoryFromCategory!!.tvChannelLink.reset()
                    val tvChannels =
                        helpViewModel.addChannelsToUserCategoryFromCategory!!.tvChannelLink
                    addTvChannelsToCategoryAdapter.submitList(tvChannels)
                    binding.rvLayoutTvChannels.requestFocus()
                }
            }

            binding.btnDeselectAll.setOnClickListener {
                binding.btnDeselectAll.visibility = View.GONE
                binding.btnSelectAll.visibility = View.VISIBLE
                binding.btnSelectAll.requestFocus()
                viewLifecycleOwner.lifecycleScope.launch {
                    helpViewModel.addChannelsToUserCategoryFromCategory!!.tvChannelLink.reset()
                    val tvChannels =
                        helpViewModel.addChannelsToUserCategoryFromCategory!!.tvChannelLink.filter { it.isSelected }
                    if (tvChannels.isNotEmpty()) {
                        addTvChannelsToCategoryAdapter.submitList(tvChannels)
                        binding.rvLayoutTvChannels.requestFocus()
                    }
                }
            }

        binding.rvLayoutTvChannels.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tvContentDescription.visibility = View.INVISIBLE
                binding.tvContentDescription.text = ""
            }
        }

        binding.btnCopy.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tvContentDescription.visibility = View.VISIBLE
                binding.tvContentDescription.text = "Copy channels"
            } else {
                binding.tvContentDescription.visibility = View.INVISIBLE
            }
        }

        binding.btnSelectAll.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tvContentDescription.visibility = View.VISIBLE
                binding.tvContentDescription.text = "Show only visible channels"
            } else {
                binding.tvContentDescription.visibility = View.INVISIBLE
            }
        }

        binding.btnDeselectAll.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tvContentDescription.visibility = View.VISIBLE
                binding.tvContentDescription.text = "Show all channels"
            } else {
                binding.tvContentDescription.visibility = View.INVISIBLE
            }
        }

        binding.btnCopy.setOnClickListener {
            val channelList = addTvChannelsToCategoryAdapter.retrieveChannelListToAdd()
            if (!channelList.isNullOrEmpty()) {
                copyChannelsToCategory(channelList)
            }
        }

            view.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    // Hier fügst du die Logik für die Zurück-Navigation im settings_container hinzu
                    // Zum Beispiel:
                    val fragmentManager = parentFragmentManager
                    if (fragmentManager.backStackEntryCount > 0) {
                        fragmentManager.popBackStack()
                    } else {
                        // Wenn es keine vorherigen Einträge gibt, kannst du das Fragment schließen
                        // oder andere Aktionen durchführen.
                    }
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }

    }


    fun showAlreadyInCategoryToast() {
        Toast.makeText(this@AddChannelsToUserCategoryChannelsFragment.requireActivity(), "Channel already part of the group", Toast.LENGTH_SHORT).show()
    }

    fun focusToMenu() {
        binding.btnCopy.requestFocus()
    }

    fun copyChannelsToCategory(channelList: List<TvChannelOB>) {
        var positionToAdd = -1
        val newChannelPosition = channelList.map {
            positionToAdd++
            val channelPosition = ChannelPositions(
                id = 0,
                it.idByAccountData,
                helpViewModel.categoryToAddChannelsInto!!.playlistId,
                helpViewModel.categoryToAddChannelsInto!!.idByAccountData,
                positionToAdd,
                positionToAdd,
               "${helpViewModel.categoryToAddChannelsInto!!.idByAccountData}_${it.idByAccountData}",
                false,
                true
            )
            channelPosition.tvchannel.target = it
            channelPosition.tvcategory.target = helpViewModel.categoryToAddChannelsInto
            channelPosition
        }
        val channelQuantity = channelList.size
        helpViewModel.categoryToAddChannelsInto?.tvChannelLink?.reset()
        val modifyCurrentPositions = helpViewModel.categoryToAddChannelsInto?.tvChannelLink
        val modifiedPositions = modifyCurrentPositions?.map {
            val oldPosition = it.position
            val oldOriginalPosition = it.originalPosition
            it.position = oldPosition + channelQuantity
            it.originalPosition = oldOriginalPosition + channelQuantity
            it
        }
        manualPositionBox.put(newChannelPosition)
        manualPositionBox.put(modifiedPositions)
        channelList.forEach {
            Toast.makeText(this@AddChannelsToUserCategoryChannelsFragment.requireActivity(), "CHANNEL: ${it.showingName} copied to Category: ${helpViewModel.categoryToAddChannelsInto?.showingName}", Toast.LENGTH_LONG)
                .show()
        }
        val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (mainFragment is TvChannelsFragment) {
            mainFragment.updateChannelList()
        }
        parentFragmentManager.popBackStack()
    }

    private fun prepareRecyclerView() {
        addTvChannelsToCategoryAdapter = AddTvChannelsToCategoryAdapter(listener, helpViewModel, this, manualPositionBox)
        binding.rvLayoutTvChannels.apply {
            adapter = addTvChannelsToCategoryAdapter
            setFocusOutSideAllowed(false, false)
            setFocusOutAllowed(false, false)
        }
    }

    val listener = AddTvChannelsToCategoryAdapter.OnClickListener{

    }

    private fun passChannelList() {
            helpViewModel.categoryToAddChannelsInto?.tvChannelLink?.reset()
            val channels = helpViewModel.categoryToAddChannelsInto?.tvChannelLink?.map {
                it.tvchannel.target
            }?.sortedBy { it.number }

            if (channels != null) {
                addTvChannelsToCategoryAdapter.channelListAlreadyInCategory?.addAll(channels)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateChannelListToAdd() {
        viewLifecycleOwner.lifecycleScope.launch {
            val tvChannels =
                helpViewModel.addChannelsToUserCategoryFromCategory!!.tvChannelLink
            if (tvChannels.isNotEmpty()) {

                val sortedChannels = when {
                    helpViewModel.addChannelsToUserCategoryFromCategory!!.isAllChannelsCategory -> {
                        helpViewModel.addChannelsToUserCategoryFromAccount?.channels?.reset()
                        val categories =
                            helpViewModel.addChannelsToUserCategoryFromAccount?.tvcategories
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
                        helpViewModel.addChannelsToUserCategoryFromCategory!!.tvChannelLink.reset()
                        when (helpViewModel.addChannelsToUserCategoryFromCategory!!.orderBy) {
                            0 -> {
                                val categoryLinks =
                                    helpViewModel.addChannelsToUserCategoryFromCategory!!.tvChannelLink.sortedBy { it.originalPosition }
                                if (helpViewModel.addChannelsToUserCategoryFromCategory!!.isFavoriteCategory) {
                                    categoryLinks.filter { it.tvchannel.target.isFavorite }
                                } else {
                                    categoryLinks
                                }
                            }

                            1 -> {
                                val categoryLinks =
                                    helpViewModel.addChannelsToUserCategoryFromCategory!!.tvChannelLink.sortedBy { it.tvchannel.target.showingName }
                                if (helpViewModel.addChannelsToUserCategoryFromCategory!!.isFavoriteCategory) {
                                    categoryLinks.filter { it.tvchannel.target.isFavorite }
                                } else {
                                    categoryLinks
                                }
                            }

                            else -> {
                                val categoryLinks =
                                    helpViewModel.addChannelsToUserCategoryFromCategory!!.tvChannelLink.sortedBy { it.position }
                                if (helpViewModel.addChannelsToUserCategoryFromCategory!!.isFavoriteCategory) {
                                    categoryLinks.filter { it.tvchannel.target.isFavorite }
                                } else {
                                    categoryLinks
                                }
                            }
                        }
                    }
                }
                passChannelList()
                addTvChannelsToCategoryAdapter.submitList(sortedChannels)
                binding.rvLayoutTvChannels.requestFocus()
            }
        }
    }

    fun updateChannelList() {
        val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (containerFragment is TvChannelsFragment) {
            containerFragment.updateChannelList()
        }
    }
}