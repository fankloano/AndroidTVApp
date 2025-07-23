package com.example.mj_player_tv.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Settings
import com.example.mj_player_tv.databinding.FragmentAddTmdbKeyBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory

class AddTmdbApiKeyFragment : Fragment(R.layout.fragment_add_tmdb_key) {

    private var _binding: FragmentAddTmdbKeyBinding? = null

    private val binding get() = _binding!!

    val settingsBox = ObjectBox.store.boxFor(Settings::class.java)

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
        _binding = FragmentAddTmdbKeyBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val settings = helpViewModel.settings

        if (settings != null) {
            binding.etTmdbkey.setText(settings.tmdbApiKey)
            binding.etTmdbkey.requestFocus()
            val imm =
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etTmdbkey, InputMethodManager.SHOW_IMPLICIT)

            binding.btnSaveEpg.setOnClickListener {
                if (binding.etTmdbkey.text.toString().isNotEmpty()) {
                    settings.tmdbApiKey = binding.etTmdbkey.text.toString()
                    settingsBox.put(settings)
                    parentFragmentManager.popBackStack()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}