package com.example.mj_player_tv.ui

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentManageTvchannelsBinding
import com.example.mj_player_tv.ui.adapter.ManageTvChannelsAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.launch

@UnstableApi
class ManageTvChannelsFragment: Fragment(R.layout.fragment_manage_tvchannels), View.OnFocusChangeListener {

    private var _binding: FragmentManageTvchannelsBinding? = null

    private val binding get() = _binding!!

    private lateinit var manageTvChannelsAdapter: ManageTvChannelsAdapter

    private val tvChannBox: Box<TvChannelOB> = ObjectBox.store.boxFor(TvChannelOB::class.java)

    private val manualPositionsBox: Box<ChannelPositions> = ObjectBox.store.boxFor(ChannelPositions::class.java)

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
        _binding = FragmentManageTvchannelsBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack

            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.parentview.isSelected = false
        binding.titleSetting.isSelected = false
        binding.parentview.requestLayout()
        binding.titleSetting.requestLayout()

        binding.rvLayoutTvChannels.onFocusChangeListener = this

        prepareRecyclerView()

        binding.linLayoutMenu.visibility = View.GONE

        binding.titleSetting.text = resources.getString(R.string.channel_editor)
        binding.titleSetting.gravity = (Gravity.CENTER_HORIZONTAL)
        binding.btnSelectAll.visibility = View.GONE


        if (helpViewModel.currentFocusedTvAccount != null && helpViewModel.currentFocusedChannPosition != null) {
            viewLifecycleOwner.lifecycleScope.launch {
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
                                    helpViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.originalPosition }
                                if (helpViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
                                    categoryLinks.filter { it.tvchannel.target.isFavorite }
                                } else {
                                    categoryLinks
                                }
                            }

                            1 -> {
                                val categoryLinks =
                                    helpViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.tvchannel.target.showingName }
                                if (helpViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
                                    categoryLinks.filter { it.tvchannel.target.isFavorite }
                                } else {
                                    categoryLinks
                                }
                            }

                            else -> {
                                val categoryLinks =
                                    helpViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.position }
                                if (helpViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
                                    categoryLinks.filter { it.tvchannel.target.isFavorite }
                                } else {
                                    categoryLinks
                                }
                            }
                        }
                    }
                }
                if (sortedChannels.isNotEmpty()) {
                    manageTvChannelsAdapter.submitList(sortedChannels)
                    binding.rvLayoutTvChannels.requestFocus()
                }
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

    private fun prepareRecyclerView() {
        manageTvChannelsAdapter = ManageTvChannelsAdapter(listener, helpViewModel)
        binding.rvLayoutTvChannels.apply {
            adapter = manageTvChannelsAdapter
            setFocusOutSideAllowed(false, false)
            setFocusOutAllowed(false, false)
        }
    }

    val listener = ManageTvChannelsAdapter.OnClickListener { channel ->
        manualPositionsBox.put(channel)
        updateChannelList()
        val chPos = manageTvChannelsAdapter.currentList.indexOf(channel)
        manageTvChannelsAdapter.notifyItemChanged(chPos)
        binding.rvLayoutTvChannels.requestFocus()
    }

    override fun onFocusChange(p0: View?, hasFocus: Boolean) {
        // Hier wird aufgerufen, wenn sich der Fokus auf einem Menüpunkt ändert
        if (hasFocus) {
            // Aktualisiere die visuelle Hervorhebung basierend auf dem aktuellen Fokus
            if (view != null) {
            }
        }
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
}