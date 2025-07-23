package com.example.mj_player_tv.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentAutoupdateIntervalBinding
import com.example.mj_player_tv.databinding.FragmentEpgsourceTimeoffsetBinding
import com.example.mj_player_tv.ui.adapter.AutoUpdateIntervalAdapter
import com.example.mj_player_tv.ui.adapter.TimeOffSetAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.rubensousa.dpadrecyclerview.ViewHolderTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class AutoUpdateIntervalFragment : Fragment(R.layout.fragment_autoupdate_interval) {

    private var _binding: FragmentAutoupdateIntervalBinding? = null

    private val binding get() = _binding!!

    private lateinit var autoUpdateIntervalAdapter: AutoUpdateIntervalAdapter

    val epgSourceBox = ObjectBox.store.boxFor(EpgSource::class.java)
    val accountBox = ObjectBox.store.boxFor(Accounts::class.java)

    private var currentInterval = -1
    private var newInterval = -1

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
        _binding = FragmentAutoupdateIntervalBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            helpViewModel.changeAutoUpdateInterval = -1
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val account = helpViewModel.selectedAccountData
        val epgSource = helpViewModel.clickedEpgSourceOptions

        if (account != null || epgSource != null && helpViewModel.changeAutoUpdateInterval != -1) {
            currentInterval = if (helpViewModel.changeAutoUpdateInterval == 0) {
                    account!!.autoUpdateHours
                } else {
                    epgSource!!.automaticUpdateDays
                }
            if (currentInterval.toString() == "168") {
                binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n7 days"
            } else if (currentInterval.toString() == "144") {
                binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n6 days"
            } else if (currentInterval.toString() == "120") {
                binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n5 days"
            } else if (currentInterval.toString() == "96") {
                binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n4 days"
            } else if (currentInterval.toString() == "72") {
                binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n 3 days"
            } else if (currentInterval.toString() == "48") {
                binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n2 days"
            } else if (currentInterval.toString() == "0") {
                binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n${currentInterval}h (Off)"
            } else {
                binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n${currentInterval} hours"
            }
            if (helpViewModel.changeAutoUpdateInterval == 0 && account != null) {
                currentInterval = account.autoUpdateHours
                val interval = arrayListOf(
                    "0", "12", "24", "36", "48", "72", "96", "120", "144", "168"
                )
                prepareRecyclerview()
                autoUpdateIntervalAdapter.submitList(interval)
                val currentIntervalPosition =
                    autoUpdateIntervalAdapter.currentList.indexOf(account.autoUpdateHours.toString())
                binding.autoUpdatetListView.requestFocus()
                binding.autoUpdatetListView.setSelectedPosition(
                    currentIntervalPosition,
                    object : ViewHolderTask() {
                        override fun execute(viewHolder: RecyclerView.ViewHolder) {
                            viewHolder.itemView.requestFocus()
                        }
                    })
            } else {
                if (epgSource != null) {
                    currentInterval = epgSource.automaticUpdateDays
                    val interval = arrayListOf(
                        "0", "24", "48", "72", "96", "120", "144", "168"
                    )
                    prepareRecyclerview()
                    autoUpdateIntervalAdapter.submitList(interval)
                    val currentIntervalPosition =
                        autoUpdateIntervalAdapter.currentList.indexOf(epgSource.automaticUpdateDays.toString())
                    binding.autoUpdatetListView.requestFocus()
                    binding.autoUpdatetListView.setSelectedPosition(
                        currentIntervalPosition,
                        object : ViewHolderTask() {
                            override fun execute(viewHolder: RecyclerView.ViewHolder) {
                                viewHolder.itemView.requestFocus()
                            }
                        })
                }
            }
        }
    }

    private fun prepareRecyclerview() {
        autoUpdateIntervalAdapter = AutoUpdateIntervalAdapter(onClickListener, helpViewModel)
        binding.autoUpdatetListView.apply {
            adapter = autoUpdateIntervalAdapter
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(false, false)
        }
    }

    private val onClickListener = AutoUpdateIntervalAdapter.OnClickListener { interval, position ->
        if (currentInterval.toString() == "168") {
            binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n7 days"
        } else if (currentInterval.toString() == "144") {
            binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n6 days"
        } else if (currentInterval.toString() == "120") {
            binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n5 days"
        } else if (currentInterval.toString() == "96") {
            binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n4 days"
        } else if (currentInterval.toString() == "72") {
            binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n3 days"
        } else if (currentInterval.toString() == "48") {
            binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n2 days"
        } else if (currentInterval.toString() == "0") {
            binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n{currentInterval}h (Off)"
        } else {
            binding.tvCurrentAutoUpdate.text = "Current Update Interval:\n${currentInterval} hours"
        }
        if (helpViewModel.changeAutoUpdateInterval == 0) {
            val account = helpViewModel.selectedAccountData
            if (interval != currentInterval.toString() && account != null) {
                    account.autoUpdateHours = interval.toInt()
                    newInterval = interval.toInt()
                    helpViewModel.viewModelScope.launch {
                        withContext(Dispatchers.IO) {
                            account.autoUpdateHours = newInterval
                            accountBox.put(account)
                            helpViewModel.setWorkerWithDelay(account)
                        }
                    }
                    parentFragmentManager.popBackStack()
            }
        } else {
            val epgSource = helpViewModel.clickedEpgSourceOptions
            if (interval != currentInterval.toString() && epgSource != null) {
                epgSource.automaticUpdateDays = interval.toInt()
                newInterval = interval.toInt()
                helpViewModel.viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        epgSource.automaticUpdateDays = newInterval
                        epgSourceBox.put(epgSource)
                        helpViewModel.setEpgWorkerWithDelay(epgSource)
                    }
                }
                parentFragmentManager.popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}