package com.example.mj_player_tv.ui.epg

import android.content.res.Resources
import android.os.Bundle
import android.os.Looper
import android.text.format.DateUtils.HOUR_IN_MILLIS
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
import com.example.mj_player_tv.ui.epg.util.EpgUtil
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

class EpgFragment : Fragment(R.layout.fragment_epg), EpgManager.Listener {

    private var _binding: FragmentEpgBinding? = null
    private val binding get() = _binding!!


    // Adapter-Instanzen
    private lateinit var timeLineAdapter: EpgTimeLineAdapter
    private lateinit var channelAdapter: EpgChannelAdapter

    private val epgManager = EpgManager()

    private var disableScrollSyncUntilOffset: Int? = null
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

        binding.epgGridView.initialize(epgManager)
        setupAdapters()
        setupComponents()

        tvGuideViewModel.loadChannelsForCategory.observe(viewLifecycleOwner) { tvCategory ->
            if (tvCategory != null) {
                getChannelsForTvCategory(tvCategory.id)
                tvGuideViewModel.clearloadChannelsForCategory()
            }
        }

        tvGuideViewModel.focusToTvGuideRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                tvGuideViewModel.clearFocusOnTvGuide()
            }
        }

        binding.timelineHeaderView.addOnScrollListener(onTimeLineScrollListener)
    }

    private val onTimeLineScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val timeRow = binding.timelineHeaderView
            if (disableScrollSyncUntilOffset != null) {
                if (timeRow.currentScrollOffset == disableScrollSyncUntilOffset) {
                    disableScrollSyncUntilOffset = null
                } else {
                    return
                }
            }
            onHorizontalScrolled(dx)
        }
    }

    // --- Setup Methoden ---

    private fun setupAdapters() {
        // 1. Initialisiere Adapter
        timeLineAdapter = EpgTimeLineAdapter()
        binding.timelineHeaderView.adapter = timeLineAdapter
        // Verwende den korrekten ChannelPositions Typ
        channelAdapter = EpgChannelAdapter(epgManager)
        binding.epgGridView.adapter = channelAdapter
    }

    // ---- Lade Channels & Epg Daten ---

    private var channelLoadJob: Job? = null

    private fun getChannelsForTvCategory(accountTvCategoryId: Long) {
        channelLoadJob?.cancel()
        channelAdapter.submitList(null)
        channelLoadJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val channelsWithEpg = tvGuideViewModel.getChannelsForCategory(accountTvCategoryId)
            epgManager.setData(channelsWithEpg, binding.timelineHeaderView)
            channelAdapter.submitList(channelsWithEpg)
        }
        val startMs = tvGuideViewModel.getTimelineStartMs(DateTime.now())
// Rufe deine neue update-Methode auf
        timeLineAdapter.updateStartTime(startMs)
        val endMs = startMs + getInitialEndTime()
        epgManager.updateInitialTimeRange(startMs, endMs)
    }

    private fun getInitialEndTime(): Long {
        val displayWidth = Resources.getSystem().displayMetrics.widthPixels
        val gridWidth =
            (displayWidth - 180)
        return gridWidth * HOUR_IN_MILLIS / 300
    }

    override fun onTimeRangeUpdated() {
        val scrollOffset =
            (300 * epgManager.getShiftedTime() / HOUR_IN_MILLIS).toInt()

        // 1️⃣ Timeline scrollen
        binding.timelineHeaderView.scrollTo(scrollOffset, true)
    }

    override fun onSchedulesUpdated() {
        TODO("Not yet implemented")
    }


    private fun onHorizontalScrolled(dx: Int) {
        if (dx == 0) {
            return
        }

        var i = 0

        binding.epgGridView.let { grid ->
            val n = grid.childCount
            while (i < n) {
                grid.getChildAt(i).findViewById<View>(R.id.rv_channel_programs).scrollBy(dx, 0)
                ++i
            }
        }
    }

    private fun setupComponents() {
        epgManager.listeners.add(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}