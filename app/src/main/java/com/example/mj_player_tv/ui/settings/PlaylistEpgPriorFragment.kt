package com.example.mj_player_tv.ui.settings


import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.EpgSourcePositions
import com.example.mj_player_tv.databinding.FragmentPlaylistEpgPriorityBinding
import com.example.mj_player_tv.ui.adapter.PriorPlaylistEpgAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistEpgPriorFragment : Fragment(R.layout.fragment_playlist_epg_priority), View.OnFocusChangeListener {

    private var _binding: FragmentPlaylistEpgPriorityBinding? = null

    private val binding get() = _binding!!

    private lateinit var priorPlaylistEpgAdapter: PriorPlaylistEpgAdapter

    private val epgSourcePosBox = ObjectBox.store.boxFor(EpgSourcePositions::class.java)

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

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
        _binding = FragmentPlaylistEpgPriorityBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentAccount = accountBox.get(helpViewModel.selectedAccountData!!.id)

        if (currentAccount != null) {
            priorPlaylistEpgAdapter = PriorPlaylistEpgAdapter(helpViewModel, currentAccount, this)
            binding.rvLayoutPlaylistEpgPriority.apply {
                adapter = priorPlaylistEpgAdapter
                addItemDecoration(
                    DpadLinearSpacingDecoration.create(
                        itemSpacing = 5,
                        edgeSpacing = 5,
                        perpendicularEdgeSpacing = 5
                    )
                )
                setFocusOutAllowed(false, false)
                setFocusOutSideAllowed(false, false)
            }
            currentAccount.epgsources.reset()
            currentAccount.epgsources.forEach {
                Log.d("PLAYLISTEPGPR", "${it.relatedepgsource.target.name} == ${it.isSelected} && ${it.position}")
            }
            val selectedEpgSources = currentAccount.epgsources.filter { it.isSelected }
                .sortedBy { it.position }
            selectedEpgSources.forEach {
                Log.d("PLAYLISTEPGPRIOR OLD", "${it.relatedepgsource.target.name} == ${it.position}")
            }
            priorPlaylistEpgAdapter.submitList(selectedEpgSources)
            binding.rvLayoutPlaylistEpgPriority.requestFocus()
        }
    }

    fun refreshEpgPriority(account: Accounts, epgSourcePosition: EpgSourcePositions, newPosition: Int) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val selectedEpgPositions = account.epgsources.filter { it.isSelected }
            if (epgSourcePosition.position < newPosition) {
                val filteredEpgPos = selectedEpgPositions.filter { it.position > epgSourcePosition.position && it.position <= newPosition }
                for (epgPos in filteredEpgPos) {
                    val oldPosition = epgPos.position
                    epgPos.position = oldPosition - 1
                    epgSourcePosBox.put(epgPos)
                }
                epgSourcePosition.position = newPosition
                epgSourcePosBox.put(epgSourcePosition)
            } else {
                val filteredEpgPos = selectedEpgPositions.filter { it.position < epgSourcePosition.position && it.position >= newPosition }
                for (epgPos in filteredEpgPos) {
                    val oldPosition = epgPos.position
                    epgPos.position = oldPosition + 1
                    epgSourcePosBox.put(epgPos)
                }
                epgSourcePosition.position = newPosition
                epgSourcePosBox.put(epgSourcePosition)
            }
        }
    }

    override fun onFocusChange(p0: View?, p1: Boolean) {
    }

    fun setFocusToRecyclerview() {
        binding.rvLayoutPlaylistEpgPriority.requestFocus()
    }

    fun closeFragment() {
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}