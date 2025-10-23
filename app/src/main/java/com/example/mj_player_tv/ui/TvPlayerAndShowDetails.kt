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
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.databinding.FragmentHomeBinding
import com.example.mj_player_tv.databinding.FragmentTvChannelsBinding
import com.example.mj_player_tv.databinding.FragmentTvplayerShowdetailsBinding
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
import org.joda.time.DateTime

class TvPlayerAndShowDetails : Fragment(R.layout.fragment_tvplayer_showdetails) {

    private var _binding: FragmentTvplayerShowdetailsBinding? = null

    private val binding get() = _binding!!

    private var currentEpgDataId: String? = null
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
        _binding = FragmentTvplayerShowdetailsBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvGuideViewModel.showProgramDetailsRequest.observe(viewLifecycleOwner) { epgDataOB ->
            if (epgDataOB?.idByAccountData != currentEpgDataId) {
                updateProgramDetails(epgDataOB)
                tvGuideViewModel.clearProgramDetailsRequest()
            }
        }
    }

    private fun updateProgramDetails(epgDataOB: EpgDataOB?) {
        if (epgDataOB != null) {
                binding.tvCurrentProgram.text = epgDataOB.name
                binding.tvCurrentSubtitle.visibility = if (epgDataOB.sub_title.isNotEmpty()) {
                    binding.tvCurrentSubtitle.text = epgDataOB.sub_title
                    View.VISIBLE
                } else {
                    binding.tvCurrentSubtitle.text = ""
                    View.GONE
                }
                binding.tvDescription.text =
                    epgDataOB.descr.ifEmpty { resources.getString(R.string.no_description) }
                binding.tvCurrentStartTime.text =
                    DateTime(epgDataOB.startTimestamp * 1000).toString("HH:mm")
                binding.tvCurrentEndTime.text = DateTime(epgDataOB.stopTimestamp * 1000).toString("HH:mm")
        } else {
            binding.tvCurrentProgram.text = resources.getString(R.string.no_information)
            binding.tvCurrentSubtitle.visibility = View.GONE
            binding.tvDescription.text = resources.getString(R.string.no_description)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}