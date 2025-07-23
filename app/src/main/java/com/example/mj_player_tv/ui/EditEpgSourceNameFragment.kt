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
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.databinding.FragmentEditEpgsourceNameBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory

class EditEpgSourceNameFragment : Fragment(R.layout.fragment_edit_epgsource_name) {

    private var _binding: FragmentEditEpgsourceNameBinding? = null

    private val binding get() = _binding!!

    val epgSourceBox = ObjectBox.store.boxFor(EpgSource::class.java)

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
        _binding = FragmentEditEpgsourceNameBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val epgSource = epgSourceBox.get(helpViewModel.clickedEpgSourceOptions!!.id)

        if (epgSource != null) {
            binding.tvCurrentEpgSourceName.text = epgSource.name
            binding.etEditedChannelName.setText(epgSource.name)
            binding.etEditedChannelName.requestFocus()
            val imm =
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etEditedChannelName, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.btnEpgSourceSave.setOnClickListener {
            if (binding.etEditedChannelName.text.toString().isNotEmpty()) {
                epgSource.name = binding.etEditedChannelName.text.toString()
                epgSourceBox.put(epgSource)
                parentFragmentManager.popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}