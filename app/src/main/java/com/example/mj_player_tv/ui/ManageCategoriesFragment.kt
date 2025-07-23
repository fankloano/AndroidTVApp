package com.example.mj_player_tv.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.databinding.FragmentManageCategoriesBinding
import com.example.mj_player_tv.ui.settings.ManageMovieCategoryFragment
import com.example.mj_player_tv.ui.settings.ManageSeriesCategoryFragment
import com.example.mj_player_tv.ui.settings.ManageTvCategoryFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory

@UnstableApi
class ManageCategoriesFragment : Fragment(R.layout.fragment_manage_categories) {

    private var _binding: FragmentManageCategoriesBinding? = null

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
        _binding = FragmentManageCategoriesBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            helpViewModel.isCategoryManagementOpened = false
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        helpViewModel.lastSelectedManageCategoryId?.let {
            view.findViewById<TextView>(it).requestFocus()
        }
            ?: binding.btnManageTv.requestFocus()

        binding.btnManageTv.setOnClickListener {
            helpViewModel.lastSelectedManageCategoryId = binding.btnManageTv.id
            changeFragment(ManageTvCategoryFragment())
        }

        binding.btnManageMovies.setOnClickListener {
            helpViewModel.lastSelectedManageCategoryId = binding.btnManageMovies.id
            changeFragment(ManageMovieCategoryFragment())
        }

        binding.btnManageSeries.setOnClickListener {
            helpViewModel.lastSelectedManageCategoryId = binding.btnManageSeries.id
            changeFragment(ManageSeriesCategoryFragment())
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
        _binding = null
    }
}