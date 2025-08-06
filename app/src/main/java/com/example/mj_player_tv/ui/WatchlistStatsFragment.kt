package com.example.mj_player_tv.ui

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.databinding.FragmentHomeBinding
import com.example.mj_player_tv.databinding.FragmentTvChannelsBinding
import com.example.mj_player_tv.databinding.FragmentWatchlistStatsBinding
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class WatchlistStatsFragment : Fragment(R.layout.fragment_watchlist_stats) {

    private var _binding: FragmentWatchlistStatsBinding? = null

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
        _binding = FragmentWatchlistStatsBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.linLayoutCardWatchlist.requestFocus()

        binding.linLayoutCardWatchlist.setOnFocusChangeListener { _, hasFocus ->
            binding.tvCardWatchlist.isSelected = hasFocus
            binding.ivCardWatchlist.isSelected = hasFocus
        }

        binding.linLayoutCardWatchlist.setOnKeyListener { _, keyCode, event ->
            if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) && event.action == KeyEvent.ACTION_DOWN) {
                (requireActivity() as? MainActivity)?.openMenu()
                (requireActivity() as? MainActivity)?.toggleVisibilityOfMainContainer(false)
                (requireActivity() as? MainActivity)?.lastSelectFocus()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.linLayoutCardWatchlist.setOnClickListener {
            changeFragment(WatchListFragment())
        }

        binding.linLayoutCardStats.setOnFocusChangeListener { _, hasFocus ->
            binding.tvCardStats.isSelected = hasFocus
            binding.ivCardStats.isSelected = hasFocus
        }

        binding.linLayoutCardStats.setOnKeyListener { _, keyCode, event ->
           if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                (requireActivity() as? MainActivity)?.openMenu()
                (requireActivity() as? MainActivity)?.toggleVisibilityOfMainContainer(false)
                (requireActivity() as? MainActivity)?.lastSelectFocus()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.linLayoutCardStats.setOnClickListener {
            changeFragment(WatchHistoryFragment())
        }

        helpViewModel.focusToWatchlistCard.observe(viewLifecycleOwner) { focusOnWatchList ->
            if (focusOnWatchList != null) {
                showCards()
                if (focusOnWatchList) {
                    binding.linLayoutCardWatchlist.requestFocus()
                } else {
                    binding.linLayoutCardStats.requestFocus()
                }
                helpViewModel.clearFocusOnWatchlListCard()
            }
        }
    }

    fun focusToLast() {
        if (!helpViewModel.watchstatsContainerOpened) {
            binding.linLayoutCardWatchlist.requestFocus()
        }
    }

    private fun showCards() {
        binding.cardWatchlist.visibility = View.VISIBLE
        binding.cardStats.visibility = View.VISIBLE
    }

    private fun hideCards() {
        binding.cardWatchlist.visibility = View.GONE
        binding.cardStats.visibility = View.GONE
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_watchlist_stats, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
        hideCards()
        binding.containerWatchlistStats.visibility = View.VISIBLE
        helpViewModel.watchstatsContainerOpened = true
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}