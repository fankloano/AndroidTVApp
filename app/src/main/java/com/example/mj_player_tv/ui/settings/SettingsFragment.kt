package com.example.mj_player_tv.ui.settings

import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.databinding.FragmentPlaylistsBinding
import com.example.mj_player_tv.databinding.FragmentSettingsBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory

@UnstableApi
class SettingsFragment : Fragment(R.layout.fragment_settings), View.OnKeyListener, View.OnClickListener, View.OnFocusChangeListener {

    private var _binding: FragmentSettingsBinding? = null

    private val binding get() = _binding!!

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
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            helpViewModel.isSettingsContainerOpened = false
            helpViewModel.isLogoSettingsOpened = false
            helpViewModel.lastSelectedSettingsMenuViewId = null
            (requireActivity() as? MainActivity)?.makeMainFragmentFullyVisibile()
            (requireActivity() as? MainActivity)?.lastSelectFocus()
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()

        helpViewModel.lastSelectedSettingsMenuViewId?.let {
            view.findViewById<TextView>(it).requestFocus()
        }
            ?: binding.btnPlaylists.requestFocus()

    }

    private fun setupListeners() {
        binding.btnPlaylists.onFocusChangeListener = this
        binding.btnLayout.onFocusChangeListener = this
        binding.btnEpg.onFocusChangeListener = this

        binding.btnPlaylists.setOnClickListener(this)
        binding.btnLayout.setOnClickListener(this)
        binding.btnEpg.setOnClickListener(this)

        binding.btnPlaylists.setOnKeyListener(this)
        binding.btnLayout.setOnKeyListener(this)
        binding.btnEpg.setOnKeyListener(this)
    }

    override fun onFocusChange(view: View?, hasFocus: Boolean) {
        // Hier wird aufgerufen, wenn sich der Fokus auf einem Menüpunkt ändert
        if (hasFocus) {
            // Aktualisiere die visuelle Hervorhebung basierend auf dem aktuellen Fokus
            if (view != null) {
            }
        }
    }

    override fun onKey(view: View?, keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> {

            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
            }
        }
        return false
    }

    override fun onClick(view: View?) {
        if (binding.btnPlaylists.isFocused) {
            helpViewModel.lastSelectedSettingsMenuViewId = binding.btnPlaylists.id
            changeFragment(PlaylistsFragment())
        } else if (binding.btnEpg.isFocused) {
            helpViewModel.lastSelectedSettingsMenuViewId = binding.btnEpg.id
            changeFragment(EpgSourcesFragment())
        } else if (binding.btnLayout.isFocused) {
            helpViewModel.lastSelectedSettingsMenuViewId = binding.btnLayout.id
            changeFragment(LayoutSettingsFragment())
        }
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.settings_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        helpViewModel.lastSelectedSettingsMenuViewId = null
        _binding = null
    }
}