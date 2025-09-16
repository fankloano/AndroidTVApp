package com.example.mj_player_tv.ui.settings


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
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.EpgSourcePositions
import com.example.mj_player_tv.database.entity.EpgSourcePositions_
import com.example.mj_player_tv.database.entity.EpgSource_
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistEpgSettingsBinding
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.ui.adapter.ManagePlaylistEpgAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.rubensousa.dpadrecyclerview.ViewHolderTask
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@UnstableApi
class PlaylistEpgSettingsFragment : Fragment(R.layout.fragment_playlist_epg_settings), View.OnFocusChangeListener {

    private var _binding: FragmentPlaylistEpgSettingsBinding? = null

    private val binding get() = _binding!!

    private lateinit var managePlaylistEpgAdapter: ManagePlaylistEpgAdapter

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val epgSourceBox: Box<EpgSource> = ObjectBox.store.boxFor(EpgSource::class.java)

    private val epgPositonBox: Box<EpgSourcePositions> = ObjectBox.store.boxFor(EpgSourcePositions::class.java)

    private var sortedList = mutableSetOf<EpgSource>()

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
        _binding = FragmentPlaylistEpgSettingsBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            helpViewModel.viewModelScope.launch {
                helpViewModel.matchAndUpdateChannelsWithEpg(accountBox.get(helpViewModel.selectedAccountData!!.id))
                helpViewModel.playlistEpgChanged = false
                parentFragmentManager.popBackStack()
            }
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mainplaylistepg.requestFocus()
        prepareRecyclerView()

        binding.rvLayoutPlaylistEpgSources.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                helpViewModel.selectedAccountData?.let {
                    helpViewModel.matchAndUpdateChannelsWithEpg(it)
                }
                helpViewModel.playlistEpgChanged = false
                parentFragmentManager.popBackStack()
                return@setOnKeyListener true
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }
        binding.tvEpgPriority.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                helpViewModel.selectedAccountData?.let {
                    helpViewModel.matchAndUpdateChannelsWithEpg(it)
                }
                helpViewModel.playlistEpgChanged = false
                parentFragmentManager.popBackStack()
                return@setOnKeyListener true
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }
        if (helpViewModel.selectedAccountData != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    // Führe den Query aus und erhalte das Ergebnis
                    helpViewModel.selectedAccountData?.epgsources?.reset()

                    val allSources = helpViewModel.selectedAccountData?.epgsources?.sortedWith(compareBy<EpgSourcePositions>(
                        { !it.isSelected },  // ISSELECTED true vor false
                        { if (it.isSelected) it.position else null },  // Position für isSelected = true
                        { if (!it.isSelected && it.isPlaylistEpg) "" else it.relatedepgsource.target?.name ?: "Unbekannt" }  // Playlist zuerst, dann Name
                    ))
                    if (!allSources.isNullOrEmpty()) {
                        // Move UI-related code to the UI thread
                        withContext(Dispatchers.Main) {
                            managePlaylistEpgAdapter.submitList(allSources)
                            binding.rvLayoutPlaylistEpgSources.requestFocus()
                        }
                    } else {
                        Toast.makeText(this@PlaylistEpgSettingsFragment.requireActivity(), "NO EPG SOURCES FOUND", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    }
                }
            }
        } else {
            Toast.makeText(this@PlaylistEpgSettingsFragment.requireActivity(), "NO ACCOUNT SELECTED", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }

        binding.tvEpgPriority.setOnClickListener {
            helpViewModel.selectedAccountData?.epgsources?.reset()
            val selectedEpg = helpViewModel.selectedAccountData?.epgsources?.any { it.isSelected }
            if (selectedEpg == true) {
                changeFragment(PlaylistEpgPriorFragment())
            } else {
                Toast.makeText(this@PlaylistEpgSettingsFragment.requireActivity(), "No activated EPG-sources!", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private val listener = ManagePlaylistEpgAdapter.OnClickListener { epgSources, isChecked ->
        helpViewModel.selectedAccountData?.let { currentAccount ->
            helpViewModel.playlistEpgChanged = true
            if (isChecked) {
                val totalSelectedSource = currentAccount.epgsources.count { it.isSelected }
                val thisEpgPosition = currentAccount.epgsources.firstOrNull { it.id == epgSources.id }
                if (thisEpgPosition != null) {
                    thisEpgPosition.isSelected = true
                    thisEpgPosition.position = totalSelectedSource
                    epgPositonBox.put(thisEpgPosition)
                }
                currentAccount.epgsources.reset()
                val allSources = currentAccount.epgsources.sortedWith(compareBy<EpgSourcePositions>(
                    { !it.isSelected },  // ISSELECTED true vor false
                    { if (it.isSelected) it.position else null },  // Position für isSelected = true
                    { if (!it.isSelected && it.isPlaylistEpg) "" else it.relatedepgsource.target.name }  // Playlist zuerst, dann Name
                ))
                managePlaylistEpgAdapter.submitList(allSources)
                binding.rvLayoutPlaylistEpgSources.requestFocus()
                if (epgSources.isPlaylistEpg) {
                    currentAccount.usePlaylistEpg = true
                    accountBox.put(currentAccount)
                    helpViewModel.epgSourceChangeCompleteSuccessful()
                }
            } else {
                val currentIndex = managePlaylistEpgAdapter.currentList.indexOf(epgSources)
                val thisEpgPosition = currentAccount.epgsources.firstOrNull { it.id == epgSources.id }
                if (thisEpgPosition != null) {
                    val filteredEpgPositions = currentAccount.epgsources.filter { it.isSelected && it.position > epgSources.position }
                    for (epgSource in filteredEpgPositions) {
                        val oldposition = epgSource.position
                        epgSource.position = oldposition - 1
                        epgPositonBox.put(epgSource)
                    }
                    epgSources.isSelected = false
                    epgSources.position = -1
                    epgPositonBox.put(epgSources)
                }
                currentAccount.epgsources.reset()
                val allSources = currentAccount.epgsources.sortedWith(compareBy<EpgSourcePositions>(
                    { !it.isSelected },  // ISSELECTED true vor false
                    { if (it.isSelected) it.position else null },  // Position für isSelected = true
                    { if (!it.isSelected && it.isPlaylistEpg) "" else it.relatedepgsource.target.name }  // Playlist zuerst, dann Name
                ))
                managePlaylistEpgAdapter.submitList(allSources)
                binding.rvLayoutPlaylistEpgSources.post {
                    binding.rvLayoutPlaylistEpgSources.setSelectedPosition(currentIndex)
                    binding.rvLayoutPlaylistEpgSources.requestFocus()
                }
                if (epgSources.isPlaylistEpg) {
                    helpViewModel.updateChannelsNotUseEpgSource(
                        epgSources.id,
                        currentAccount.id,
                        false
                    )
                } else {
                    helpViewModel.updateChannelsNotUseEpgSource(
                        epgSources.id,
                        currentAccount.id,
                        true
                    )
                }
                binding.rvLayoutPlaylistEpgSources.requestFocus()
            }
        }
    }

    private fun prepareRecyclerView() {
            managePlaylistEpgAdapter =
                ManagePlaylistEpgAdapter(
                    listener,
                    helpViewModel
                )
            binding.rvLayoutPlaylistEpgSources.apply {
                adapter = managePlaylistEpgAdapter
                addItemDecoration(
                    DpadLinearSpacingDecoration.create(
                        itemSpacing = 4,
                        edgeSpacing = 4,
                        perpendicularEdgeSpacing = 4
                    )
                )
                setFocusOutAllowed(throughFront = true, throughBack = false)
                setFocusOutSideAllowed(throughFront = false, throughBack = false)
                visibility = View.VISIBLE
        }
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.settings_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    override fun onFocusChange(p0: View?, p1: Boolean) {

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}