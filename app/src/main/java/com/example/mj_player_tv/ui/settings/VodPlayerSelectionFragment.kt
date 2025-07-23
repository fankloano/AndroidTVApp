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
import com.example.mj_player_tv.database.entity.Settings
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistLogosBinding
import com.example.mj_player_tv.databinding.FragmentVodPlayerSelectionBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class VodPlayerSelectionFragment : Fragment(R.layout.fragment_vod_player_selection) {

    private var _binding: FragmentVodPlayerSelectionBinding? = null

    private val binding get() = _binding!!

    private val settingsBox: Box<Settings> = ObjectBox.store.boxFor(Settings::class.java)

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
        _binding = FragmentVodPlayerSelectionBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val settings = helpViewModel.settings
        if (settings != null) {
            if (settings.playMoviesWithVlc) {
                binding.linLayoutUseVLCPlayer.requestFocus()
                binding.cbUseExoPlayer.isChecked = false
                binding.cbUseExoPlayer.isActivated = false
                binding.cbUseVLCPlayer.isChecked = true
                binding.cbUseVLCPlayer.isActivated = true
            } else {
                binding.linLayoutUseExoPlayer.requestFocus()
                binding.cbUseVLCPlayer.isChecked = false
                binding.cbUseVLCPlayer.isActivated = false
                binding.cbUseExoPlayer.isChecked = true
                binding.cbUseExoPlayer.isActivated = true
            }

            binding.linLayoutUseVLCPlayer.setOnClickListener {
                // Aktualisiere currentAccount und Checkboxen nur, wenn die Checkbox nicht bereits ausgewählt ist
                if (!binding.cbUseVLCPlayer.isChecked) {
                    settings.playMoviesWithVlc = true
                    binding.cbUseVLCPlayer.isChecked = true
                    binding.cbUseVLCPlayer.isActivated = true
                    binding.cbUseExoPlayer.isChecked = false
                    binding.cbUseExoPlayer.isActivated = false
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        settingsBox.put(settings)
                        helpViewModel.settings = settings
                    }
                }
            }

            binding.linLayoutUseExoPlayer.setOnClickListener {
                // Aktualisiere currentAccount und Checkboxen nur, wenn die Checkbox nicht bereits ausgewählt ist
                if (!binding.cbUseExoPlayer.isChecked) {
                    settings.playMoviesWithVlc = false
                    binding.cbUseExoPlayer.isChecked = true
                    binding.cbUseExoPlayer.isActivated = true
                    binding.cbUseVLCPlayer.isChecked = false
                    binding.cbUseVLCPlayer.isActivated = false
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        settingsBox.put(settings)
                        helpViewModel.settings = settings
                    }
                }
            }

        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}