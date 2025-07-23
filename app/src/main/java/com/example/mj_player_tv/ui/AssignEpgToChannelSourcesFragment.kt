package com.example.mj_player_tv.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpgSourcePositions
import com.example.mj_player_tv.databinding.FragmentAssignEpgtochannelSourcesBinding
import com.example.mj_player_tv.ui.adapter.EpgToChannelSourcesAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory


@UnstableApi
class AssignEpgToChannelSourcesFragment : Fragment(R.layout.fragment_assign_epgtochannel_sources) {

    private var _binding: FragmentAssignEpgtochannelSourcesBinding? = null

    private val binding get() = _binding!!

    private lateinit var epgChannelsSourceAdapter: EpgToChannelSourcesAdapter

    private val accountBox = ObjectBox.store.boxFor(Accounts::class.java)

    private val epgSourcePosBox = ObjectBox.store.boxFor(EpgSourcePositions::class.java)

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
        _binding = FragmentAssignEpgtochannelSourcesBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            val containerFragment = parentFragmentManager.findFragmentById(R.id.container_AssignChannelToEpg)
            if (containerFragment is AssingChannelToEpgFragment) {
                containerFragment.removeOverlay()
                containerFragment.setFocusToRightMenu()
                parentFragmentManager.popBackStack()
            }
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareRecyclerView()


        if (helpViewModel.currentFocusedChannPosition != null && helpViewModel.currentFocusedTvAccount != null) {
            helpViewModel.currentFocusedTvAccount!!.epgsources.reset()
            val epgSources = helpViewModel.currentFocusedTvAccount!!.epgsources.filter { it.isSelected }.sortedBy { it.position }.map {
                it.relatedepgsource.target
            }
            if (epgSources.isNotEmpty()) {
                epgChannelsSourceAdapter.submitList(epgSources)
                if (!helpViewModel.showAllEpgChannelSources && helpViewModel.currentSelectedEpgChannelSource != null) {
                    val sourcePos =
                        epgChannelsSourceAdapter.currentList.indexOf(helpViewModel.currentSelectedEpgChannelSource)
                    binding.rvEpgSources.setSelectedPosition(sourcePos)
                    binding.rvEpgSources.requestFocus()
                } else {
                    binding.textAllEpgSources.requestFocus()
                }
            } else {
                val assignEpgContainer = parentFragmentManager.findFragmentById(R.id.container_AssignChannelToEpg)
                if (assignEpgContainer is AssingChannelToEpgFragment) {
                    assignEpgContainer.removeOverlay()
                }
                parentFragmentManager.popBackStack()
            }
        }

        binding.textAllEpgSources.setOnClickListener {
            val containerFragment = parentFragmentManager.findFragmentById(R.id.container_AssignChannelToEpg)
            if (containerFragment is AssingChannelToEpgFragment) {
                parentFragmentManager.popBackStack()
                helpViewModel.showAllEpgChannelSources = true
                containerFragment.showAllEpgSources()
                containerFragment.removeOverlay()
            }
        }
    }

    private val onClickListener = EpgToChannelSourcesAdapter.OnClickListener {
        helpViewModel.currentSelectedEpgChannelSource = it
        val containerFragment = parentFragmentManager.findFragmentById(R.id.container_AssignChannelToEpg)
        if (containerFragment is AssingChannelToEpgFragment) {
            parentFragmentManager.popBackStack()
            helpViewModel.showAllEpgChannelSources = false
            containerFragment.refreshEpgChannels(it)
            containerFragment.removeOverlay()
        }
    }

    private fun prepareRecyclerView() {
        epgChannelsSourceAdapter = EpgToChannelSourcesAdapter(onClickListener, helpViewModel, this)
        binding.rvEpgSources.apply {
            adapter = epgChannelsSourceAdapter
            setFocusOutSideAllowed(false, false)
            setFocusOutAllowed(false, false)
        }
    }

    fun focusToShowAll() {
        binding.textAllEpgSources.requestFocus()
    }

    fun closeFragment() {
        val containerFragment = parentFragmentManager.findFragmentById(R.id.container_AssignChannelToEpg)
        if (containerFragment is AssingChannelToEpgFragment) {
            containerFragment.removeOverlay()
            containerFragment.setFocusToRightMenu()
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}