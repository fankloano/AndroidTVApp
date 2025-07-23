package com.example.mj_player_tv.ui.settings

import android.os.Bundle
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
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistLogosBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class PlaylistLogosFragment : Fragment(R.layout.fragment_playlist_logos) {

    private var _binding: FragmentPlaylistLogosBinding? = null

    private val binding get() = _binding!!

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val tvChBox: Box<TvChannelOB> = ObjectBox.store.boxFor(TvChannelOB::class.java)

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
        _binding = FragmentPlaylistLogosBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentAccount = helpViewModel.selectedAccountData
        if (currentAccount != null) {
            if (currentAccount.usePlaylistLogos) {
                binding.linLayoutUsePlaylistLogos.requestFocus()
                binding.cbUseEpgLogos.isChecked = false
                binding.cbUseEpgLogos.isActivated = false
                binding.cbUsePlaylistLogos.isChecked = true
                binding.cbUsePlaylistLogos.isActivated = true
            } else {
                binding.linLayoutUseEpgLogos.requestFocus()
                binding.cbUsePlaylistLogos.isChecked = false
                binding.cbUsePlaylistLogos.isActivated = false
                binding.cbUseEpgLogos.isChecked = true
                binding.cbUseEpgLogos.isActivated = true
            }

            binding.linLayoutUsePlaylistLogos.setOnClickListener {
                // Aktualisiere currentAccount und Checkboxen nur, wenn die Checkbox nicht bereits ausgewählt ist
                if (!binding.cbUsePlaylistLogos.isChecked) {
                    currentAccount.usePlaylistLogos = false
                    currentAccount.useEpgLogos = true
                    binding.cbUsePlaylistLogos.isChecked = true
                    binding.cbUsePlaylistLogos.isActivated = true
                    binding.cbUseEpgLogos.isChecked = false
                    binding.cbUseEpgLogos.isActivated = false
                    viewLifecycleOwner.lifecycleScope.launch {
                        updateForPlaylistLogos(currentAccount)
                    }
                }
            }

            binding.linLayoutUseEpgLogos.setOnClickListener {
                // Aktualisiere currentAccount und Checkboxen nur, wenn die Checkbox nicht bereits ausgewählt ist
                if (!binding.cbUseEpgLogos.isChecked) {
                    currentAccount.useEpgLogos = false
                    currentAccount.usePlaylistLogos = true
                    binding.cbUseEpgLogos.isChecked = true
                    binding.cbUseEpgLogos.isActivated = true
                    binding.cbUsePlaylistLogos.isChecked = false
                    binding.cbUsePlaylistLogos.isActivated = false
                    viewLifecycleOwner.lifecycleScope.launch {
                        updateForEpgLogos(currentAccount)
                    }
                }
            }

        }
    }

    private fun updateForEpgLogos(currentAccount: Accounts) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                currentAccount.usePlaylistLogos = false
                currentAccount.useEpgLogos = true
                accountBox.put(currentAccount)
                helpViewModel.updateTvAccountsCompleteSuccessful()
                helpViewModel.updateLogoSourceCompleteSuccessful()
                binding.linLayoutUseEpgLogos.requestFocus()
            }
        }
    }

    private fun updateForPlaylistLogos(currentAccount: Accounts) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                currentAccount.usePlaylistLogos = true
                currentAccount.useEpgLogos = false
                accountBox.put(currentAccount)
                helpViewModel.updateTvAccountsCompleteSuccessful()
                helpViewModel.updateLogoSourceCompleteSuccessful()
                binding.linLayoutUsePlaylistLogos.requestFocus()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}