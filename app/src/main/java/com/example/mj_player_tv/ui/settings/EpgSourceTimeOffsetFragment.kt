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
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentEpgsourceTimeoffsetBinding
import com.example.mj_player_tv.ui.TvChannelsFragment
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
class EpgSourceTimeOffsetFragment : Fragment(R.layout.fragment_epgsource_timeoffset) {

    private var _binding: FragmentEpgsourceTimeoffsetBinding? = null

    private val binding get() = _binding!!

    private lateinit var timeOffSetAdapter: TimeOffSetAdapter

    val epgSourceBox = ObjectBox.store.boxFor(EpgSource::class.java)

    private var currentTimeOffSet = 0
    private var newTimeOffSet = 0

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
        _binding = FragmentEpgsourceTimeoffsetBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val epgSource = helpViewModel.clickedEpgSourceOptions

        if (epgSource != null) {
            currentTimeOffSet = epgSource.timeOffSet
            // Zeige das aktuelle TimeOffset im gewünschten Format an
            binding.tvCurrentTimeOffset.text = "Current Timeoffset: ${formatTimeOffset(epgSource.timeOffSet)}"

            // Erstelle eine Liste von timeOffsets von -24 bis +24 Stunden
            val timeOffsets = mutableListOf<String>()
            for (hour in -24..24) {
                val formattedHour = formatTimeOffset(hour)
                timeOffsets.add(formattedHour)
            }

            // Bereite die RecyclerView vor und übergebe die Liste
            prepareRecyclerview()
            timeOffSetAdapter.submitList(timeOffsets)

            // Aktuelle Position des timeOffsets in der Liste finden
            val currentTimeOffSetFormatted = formatTimeOffset(epgSource.timeOffSet)
            val currentTimeOffSetPosition = timeOffSetAdapter.currentList.indexOf(currentTimeOffSetFormatted)

            // Fokussiere das aktuelle TimeOffset in der Liste
            binding.timeOffsetListView.requestFocus()
            binding.timeOffsetListView.setSelectedPosition(
                currentTimeOffSetPosition,
                object : ViewHolderTask() {
                    override fun execute(viewHolder: RecyclerView.ViewHolder) {
                        viewHolder.itemView.requestFocus()
                    }
                }
            )
        }
    }

    private fun formatTimeOffset(timeOffset: Int): String {
        val sign = if (timeOffset >= 0) "+" else "-"
        val hours = Math.abs(timeOffset).toString().padStart(2, '0')
        return "$sign$hours:00"
    }


    private fun prepareRecyclerview() {
        timeOffSetAdapter = TimeOffSetAdapter(onClickListener, helpViewModel)
        binding.timeOffsetListView.apply {
            adapter = timeOffSetAdapter
            setFocusOutAllowed(true, false)
            setFocusOutSideAllowed(false, false)
        }
    }

    private val onClickListener = TimeOffSetAdapter.OnClickListener { timeOffSet, position ->
        val epgSource = helpViewModel.clickedEpgSourceOptions
        binding.tvCurrentTimeOffset.text = "Current TimeOffset: $timeOffSet"
        val timeOffSetInt = parseTimeOffset(timeOffSet)
        if (timeOffSetInt != currentTimeOffSet && epgSource != null) {
            epgSource.timeOffSet = timeOffSetInt
            newTimeOffSet = timeOffSetInt
            helpViewModel.viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    epgSource.timeOffSet = newTimeOffSet
                    epgSource.previousTimeOffSet = currentTimeOffSet
                    epgSourceBox.put(epgSource)
                    val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                    if (mainFragment is TvChannelsFragment) {
                        mainFragment.updateChannelList()
                    }
                }
            }
            parentFragmentManager.popBackStack()
        }
    }

    fun parseTimeOffset(timeOffsetString: String): Int {
        // Entferne den Doppelpunkt und die Minuten (immer ":00")
        val trimmedString = timeOffsetString.substring(0, 3) // Z.B. "+02" oder "-03"

        // Konvertiere den String in eine Ganzzahl
        return trimmedString.toInt()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}