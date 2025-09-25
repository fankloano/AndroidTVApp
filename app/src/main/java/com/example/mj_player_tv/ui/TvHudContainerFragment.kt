package com.example.mj_player_tv.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.mj_player_tv.R
import com.example.mj_player_tv.databinding.FragmentHomeBinding
import com.example.mj_player_tv.databinding.FragmentTvChannelsBinding
import com.example.mj_player_tv.databinding.FragmentTvHudcontainerBinding
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvHudContainerFragment : Fragment(R.layout.fragment_tv_hudcontainer) {

    private var _binding: FragmentTvHudcontainerBinding? = null

    private val binding get() = _binding!!

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
        _binding = FragmentTvHudcontainerBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }


    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}