package com.example.mj_player_tv.ui.epg

import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.databinding.FragmentEpgBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.TvGuideViewModel
import com.example.mj_player_tv.viewmodel.TvGuideViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import java.util.logging.Handler

class EpgFragment : Fragment(R.layout.fragment_epg) {

    private var _binding: FragmentEpgBinding? = null
    private val binding get() = _binding!!

    // Helferklasse für die Scroll-Synchronisation
    private val synchronizer = EpgScrollSynchronizer()

    // Adapter-Instanzen
    private lateinit var timeLineAdapter: EpgTimeLineAdapter
    private lateinit var channelAdapter: EpgChannelAdapter
    private lateinit var programRowAdapter: EpgRowAdapter

    private val tvGuideViewModel: TvGuideViewModel by activityViewModels {
        TvGuideViewModelFactory(
            requireActivity().application
        )
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEpgBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupSynchronizer()

        tvGuideViewModel.loadChannelsForCategory.observe(viewLifecycleOwner) { tvCategory ->
            if (tvCategory != null) {
                getChannelsForTvCategory(tvCategory.id)
                tvGuideViewModel.clearloadChannelsForCategory()
            }
        }

        tvGuideViewModel.focusToTvGuideRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                focusToTvGuide()
                tvGuideViewModel.clearFocusOnTvGuide()
            }
        }
    }


    // --- Setup Methoden ---

    private fun setupAdapters() {
        // 1. Initialisiere Adapter
        timeLineAdapter = EpgTimeLineAdapter()
        // Verwende den korrekten ChannelPositions Typ
        channelAdapter = EpgChannelAdapter()
        programRowAdapter = EpgRowAdapter(
            onProgramClick = { programData ->
                // Handle Klick
            },
            synchronizer = synchronizer // ⬅️ NEU: Synchronizer übergeben
        )

        // 2. Verbinde Adapter
        binding.channelListView.apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            adapter = channelAdapter
        }
        binding.programGridView.apply {
            adapter = programRowAdapter
        }
        binding.timelineHeaderView.apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = timeLineAdapter
        }
    }

    private fun setupSynchronizer() {
        // 1. Vertikale Synchronisation einrichten
        synchronizer.setupVerticalSync(binding.channelListView, binding.programGridView)

        // 2. Timeline zur horizontalen Synchronisation registrieren
        synchronizer.registerHorizontalView(binding.timelineHeaderView)
        binding.programGridView.synchronizer = synchronizer
    }

    // --- Fokus & Time Indicator Logik ---



    private fun focusToTvGuide() {
        // Beispiel: fokussiere erste Zeile, erste Sendung
        binding.programGridView.scrollToPosition(0)
        binding.programGridView.post {
            binding.programGridView.requestFocus()

            // Optional: Fokus auf erstes ProgramItem in der Zeile
            val rowHolder = binding.programGridView.findViewHolderForAdapterPosition(0)
                    as? EpgRowAdapter.RowViewHolder
            rowHolder?.horizontalGridView?.post {
                rowHolder.horizontalGridView.getChildAt(0)?.requestFocus()
            }
        }
    }


    // ---- Lade Channels & Epg Daten ---

    private var channelLoadJob: Job? = null

    private fun getChannelsForTvCategory(accountTvCategoryId: Long) {
        channelLoadJob?.cancel()
        channelAdapter.submitList(null)
        programRowAdapter.submitList(emptyList())
        channelLoadJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val channelsWithEpg = tvGuideViewModel.getChannelsForCategory(accountTvCategoryId)
            val channelPositions = channelsWithEpg.map { it.tvChannelPosition }
            withContext(Dispatchers.Main) {
                channelAdapter.submitList(channelPositions)
                programRowAdapter.submitList(channelsWithEpg)
            }
        }
        val timeMarks = tvGuideViewModel.getCurrentTimeMarks(DateTime.now())
        timeLineAdapter.submitList(timeMarks)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        synchronizer.release()
        _binding = null
    }
}