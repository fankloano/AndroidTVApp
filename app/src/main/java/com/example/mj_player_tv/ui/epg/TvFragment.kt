package com.example.mj_player_tv.ui.epg

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.databinding.FragmentHomeBinding
import com.example.mj_player_tv.databinding.FragmentTvBinding
import com.example.mj_player_tv.databinding.FragmentTvChannelsBinding
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.TvGuideViewModel
import com.example.mj_player_tv.viewmodel.TvGuideViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class TvFragment : Fragment(R.layout.fragment_tv) {

    private var _binding: FragmentTvBinding? = null

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

    private val tvGuideViewModel: TvGuideViewModel by activityViewModels {
        TvGuideViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTvBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setAccountsWithCategoriesFragment()

        tvGuideViewModel.loadChannelsForCategory.observe(viewLifecycleOwner) {
            val epgFragment = childFragmentManager.findFragmentById(R.id.container_epggrid)
            if (epgFragment == null) {
                setEpgFragment()
            }
        }

        tvGuideViewModel.showMenuAndAccountsRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                showMenuAndAccounts()
                tvGuideViewModel.clearShowMenuAndAccounts()
            }
        }

        tvGuideViewModel.hideMenuAndAccountsRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                hideMenuAndAccounts()
                tvGuideViewModel.clearHideMenuAndAccounts()
            }
        }
    }

    private fun setAccountsWithCategoriesFragment() {
        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(R.id.container_accountwithcategories, AccountTvCategoriesFragment())
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun setPlayerAndShowDetailsFragment() {

    }

    private fun setEpgFragment() {
        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(R.id.container_epggrid, EpgFragment())
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun showMenuAndAccounts() {
        binding.motionLayout.transitionToStart {
            showMainMenu()
        }
    }

    private fun hideMenuAndAccounts() {
        binding.motionLayout.transitionToEnd {
            hideMainMenu()
        }
    }

    private fun showMainMenu() {
        (requireActivity() as? MainActivity)?.showMenu()
    }

    private fun hideMainMenu() {
        (requireActivity() as? MainActivity)?.hideMenu()
    }


    fun closeFragment() {
        parentFragmentManager.popBackStack()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}