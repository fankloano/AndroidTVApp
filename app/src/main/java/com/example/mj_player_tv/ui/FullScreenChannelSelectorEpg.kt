package com.example.mj_player_tv.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgDataOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentFullscreenchannelselectorepgBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
class FullScreenChannelSelectorEpg : Fragment(R.layout.fragment_fullscreenchannelselectorepg) {

    private var _binding: FragmentFullscreenchannelselectorepgBinding? = null
    private val binding get() = _binding!!

    private val epgDataBox: Box<EpgDataOB> = ObjectBox.store.boxFor(EpgDataOB::class.java)

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
        _binding = FragmentFullscreenchannelselectorepgBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (helpViewModel.isPlayingCatchup) {
            helpViewModel.catchupPlayingChannelPosition?.let {
                showEpgInfo(it)
            }
        } else {
            if (helpViewModel.currentPlayingChannelPosition != null || helpViewModel.fullScreenFocusedChannel != null) {
                binding.relLayoutPrograminfo.visibility = View.VISIBLE
                showEpgInfo(
                    helpViewModel.fullScreenFocusedChannelPosition ?: helpViewModel.currentPlayingChannelPosition!!
                )
            }
        }
    }

    fun showEpgInfo(tvchannelPos: ChannelPositions) {
        resetInfos()
        binding.relLayoutPrograminfo.visibility = View.VISIBLE
        val tvchannel = tvchannelPos.tvchannel.target
        val currentTime = System.currentTimeMillis()
        if (tvchannel.linkedEpgChannel?.target != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val currentTimeSec = System.currentTimeMillis() / 1000

                val timeOffSetSec = calculateTimeOffsetInSeconds(
                    tvchannel.epgTimeOffSet
                        ?: tvchannelPos.tvcategory.target.epgTimeOffSet
                        ?: tvchannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                        ?: 0
                )

                // Hilfsfunktion für verschobene Zeiten
                fun EpgDataOB.shiftedStart() = (this.startTimestamp ?: 0) + timeOffSetSec
                fun EpgDataOB.shiftedStop()  = (this.stopTimestamp ?: 0) + timeOffSetSec
                val currentProgram = withContext(Dispatchers.IO) {
                    epgDataBox.query(EpgDataOB_.epgChId.equal(tvchannel.linkedEpgChannel!!.target.chEpgId)
                        .and(EpgDataOB_.stopTimestamp.greater(currentTimeSec - timeOffSetSec))
                        .and(EpgDataOB_.startTimestamp.less(currentTimeSec - timeOffSetSec))).build().findFirst()
                }
                if (currentProgram != null) {

                    val startTime = formatUnixTimestampToTime(currentProgram.shiftedStart())
                    val endTime   = formatUnixTimestampToTime(currentProgram.shiftedStop())
                    binding.tvCurrentProgram.text = currentProgram.name
                    binding.tvCurrentProgram.visibility = View.VISIBLE
                    binding.tvCurrentSubtitle.visibility =
                        if (currentProgram.sub_title.isNotEmpty()) {
                            binding.tvCurrentSubtitle.text = currentProgram.sub_title
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    binding.tvCurrentEndTime.visibility = View.VISIBLE
                    binding.tvDescription.visibility = View.VISIBLE
                    binding.tvDescription.text = currentProgram.descr ?: ""
                    binding.tvCurrentStartTime.text = startTime
                    binding.tvCurrentEndTime.text = " - ${endTime}"

                    val duration = currentProgram.shiftedStop() - currentProgram.shiftedStart()
                    val progress = ((currentTimeSec - currentProgram.shiftedStart()) * 100 / duration).toInt()
                    binding.progressBar.isInvisible = false
                    binding.progressBar.max = 100
                    binding.progressBar.progress = progress.coerceIn(0, 100)

                    binding.progressBar.visibility = View.VISIBLE
                        // Berechne die verbleibende Zeit in Sekunden
                    binding.tvRemainingTimeCurrentProgram.visibility = View.VISIBLE

                    val remainingTimeInSeconds = currentProgram.shiftedStop() - currentTimeSec

// Formatiere die verbleibende Zeit
                    val remainingTimeText = if (remainingTimeInSeconds > 3600) {
                        String.format(
                            "%dh %dmin",
                            remainingTimeInSeconds / 3600,
                            (remainingTimeInSeconds % 3600) / 60
                        )
                    } else {
                        String.format("%dmin", remainingTimeInSeconds / 60)
                    }

// Setze den Text im TextView
                    binding.tvRemainingTimeCurrentProgram.text = remainingTimeText

                    binding.tvRemainingTimeCurrentProgram.visibility = View.VISIBLE
                    } else {
                        binding.tvCurrentProgram.text = binding.tvCurrentProgram.context.getString(R.string.no_information)
                        binding.tvCurrentStartTime.text = formatUnixTimestampToTime(currentTimeSec)
                        binding.tvCurrentEndTime.text = " - " + formatUnixTimestampToTime(currentTimeSec + 1800)
                        binding.progressBar.progress = 0
                        binding.tvDescription.text = resources.getString(R.string.no_description)
                        binding.tvCurrentSubtitle.visibility = View.GONE
                        binding.progressBar.visibility = View.INVISIBLE
                        binding.tvRemainingTimeCurrentProgram.visibility = View.INVISIBLE
                }
            }
        } else {
            val currentTimeString =
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime)
            val halfHourLaterTime = currentTime + (30 * 60 * 1000)
            val halfHourLaterTimeString =
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(halfHourLaterTime)
            binding.tvCurrentProgram.text = resources.getString(R.string.no_information)
            binding.tvCurrentStartTime.text = currentTimeString
            binding.tvCurrentEndTime.text = " - $halfHourLaterTimeString"
            binding.tvDescription.text = resources.getString(R.string.no_description)
            binding.tvCurrentSubtitle.visibility = View.GONE
            binding.progressBar.visibility = View.INVISIBLE
            binding.tvRemainingTimeCurrentProgram.visibility = View.INVISIBLE
        }
    }

    fun resetInfos() {
        binding.relLayoutPrograminfo.visibility = View.INVISIBLE
        binding.tvCurrentProgram.text = ""
        binding.tvCurrentStartTime.text = ""
        binding.tvCurrentEndTime.text = ""
        binding.tvDescription.text = ""
        binding.tvCurrentSubtitle.visibility = View.GONE
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvRemainingTimeCurrentProgram.visibility = View.INVISIBLE
    }

    fun formatUnixTimestampToTime(unixTimestamp: Long): String {
        return try {
            val date = Date(unixTimestamp * 1000) // Timestamp in Sekunden → ms
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            timeFormat.format(date)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun calculateTimeOffsetInSeconds(timeOffset: Int): Long {
        return (timeOffset * 3600).toLong()
    }

    fun closeFragment() {
        resetInfos()
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}