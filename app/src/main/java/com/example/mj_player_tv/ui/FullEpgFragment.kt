package com.example.mj_player_tv.ui

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgDataOB_
import com.example.mj_player_tv.database.entity.EpgSourceChannel
import com.example.mj_player_tv.database.entity.PortalEpgAndDate
import com.example.mj_player_tv.database.entity.PortalEpgAndDate_
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.database.entity.Programme_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentFullepgBinding
import com.example.mj_player_tv.ui.adapter.DateTabAdapter
import com.example.mj_player_tv.ui.adapter.FullEpgAdapter
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.Duration
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

@UnstableApi
class FullEpgFragment : Fragment(R.layout.fragment_fullepg) {

    private var _binding: FragmentFullepgBinding? = null

    private val binding get() = _binding!!

    private var dateTabAdapter: DateTabAdapter? = null

    private var fullEpgAdapter: FullEpgAdapter? = null

    private val epgDataBox = ObjectBox.store.boxFor(EpgDataOB::class.java)

    private val programmeBox = ObjectBox.store.boxFor(Programme::class.java)

    private var clickedEpgData: EpgDataOB? = null

    private val portalEpgAndDateBox = ObjectBox.store.boxFor(PortalEpgAndDate::class.java)

    private var isFirstOpen: Boolean = true

    private val helpViewModel: HelpViewModel by activityViewModels {
        HelpViewModelFactory(
            requireActivity().application
        )
    }

    private val stalkerViewModel: StalkerViewModel by activityViewModels {
        StalkerViewModelFactory(
            requireActivity().application
        )
    }

    private val xtreamViewModel: XtreamViewModel by activityViewModels {
        XtreamViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFullepgBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            closeFragment()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isFirstOpen = true
        prepareTabRecyclerview()
        prepareFullEpgRecyclerview()

        val tvChannel = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedChannel
        } else {
            helpViewModel.currentFocusedChannel
        }

        val tvChannelPos = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedChannelPosition
        } else {
            helpViewModel.currentFocusedChannPosition
        }

        if (tvChannel != null && tvChannel.linkedEpgChannel?.target != null) {
            val epgChId = tvChannel.linkedEpgChannel?.target?.chEpgId
            if (epgChId != null) {
                binding.tvTvchannelname.text = tvChannel.showingName
                val image = tvChannel.logo
                if (tvChannel.account.target.useEpgLogos) {
                    val epgLogo = tvChannel.epgLogo
                    if (epgLogo.isNotEmpty()) {
                        binding.ivChannellogoImage.load(epgLogo)
                    } else {
                        if (image.isNotEmpty()) {
                            binding.ivChannellogoImage.load(image)
                        } else {
                            binding.ivChannellogoImage.visibility = View.INVISIBLE
                        }
                    }
                } else {
                    if (image.isNotEmpty()) {
                        binding.ivChannellogoImage.load(image)
                    } else {
                        binding.ivChannellogoImage.visibility = View.INVISIBLE
                    }
                }
                if (tvChannel.enable_tv_archive == 1) {
                    binding.ivCatchup.visibility = View.VISIBLE
                    binding.tvArchiveTime.visibility = View.VISIBLE
                    binding.tvArchiveTime.text =
                        "${tvChannel.tv_archive_duration}h"
                } else {
                    binding.ivCatchup.visibility = View.INVISIBLE
                    binding.tvArchiveTime.visibility = View.INVISIBLE
                }

                val timeOffSet = tvChannel.epgTimeOffSet
                    ?: tvChannelPos?.tvcategory?.target?.epgTimeOffSet
                    ?: tvChannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                    ?: 0
                val timeOffSetSeconds = calculateTimeOffsetInSeconds(timeOffSet)
                if (tvChannel.linkedEpgChannel?.target?.isExternalEpg == true) {
                    val dates = epgDataBox.query(EpgDataOB_.epgChId.equal(epgChId)
                        ).build().property(EpgDataOB_.datum).distinct().findStrings().sorted().toMutableList()
                    if (dates.isNotEmpty()) {
                        val today = getTodayFormatted()
                        val todayPosition = dates.indexOf(today)
                        dateTabAdapter?.submitList(dates) {
                            binding.tablayoutEpg.setSelectedPosition(todayPosition)
                            binding.tablayoutEpg.findViewHolderForAdapterPosition(todayPosition)?.itemView?.isSelected =
                                true
                        }
                        dateTabAdapter?.updateSelectedPosition(today)
                        helpViewModel.currentEpgTab = today
                        val currentTime = System.currentTimeMillis() / 1000
                        val epg = filterEpgDataListForHorizontal(today)
                        val currentEpg =
                            epg.find { (it.startTimestamp!! + timeOffSetSeconds) <= currentTime && (it.stopTimestamp!! + timeOffSetSeconds) >= currentTime }
                        if (currentEpg != null) {
                            helpViewModel.currentFullEpgProgramId = currentEpg.idByAccountData
                        }
                        fullEpgAdapter?.submitList(epg)
                        fullEpgAdapter?.setCurrentChannelId(tvChannel.idByAccountData)
                        val currentProgramPosition =
                            fullEpgAdapter?.currentList!!.indexOf(currentEpg)
                        binding.rvFullepg.setSelectedPosition(currentProgramPosition)
                        binding.rvFullepg.post {
                            if (currentEpg != null) {
                                binding.rvFullepg.requestFocus()
                            }
                        }
                    }
                } else {
                    if (tvChannel.account.target?.isStalker == true) {
                        val uniqueDates = getFormattedDateList()
                        val today = getTodayFormatted()
                        val todayPosition = uniqueDates.indexOf(today)
                        dateTabAdapter?.submitList(uniqueDates)
                        binding.tablayoutEpg.post {
                            binding.tablayoutEpg.setSelectedPosition(todayPosition)
                            binding.tablayoutEpg.findViewHolderForAdapterPosition(todayPosition)?.itemView?.isSelected =
                                true
                            binding.tablayoutEpg.requestFocus()
                        }
                    } else {
                        viewLifecycleOwner.lifecycleScope.launch {
                            val epg = xtreamViewModel.getFullEpgByChannel(
                                tvChannel
                            ).await()
                            if (epg.isNotEmpty()) {
                                val dates = epgDataBox.query(EpgDataOB_.epgChId.equal(epgChId)
                                ).build().property(EpgDataOB_.datum).distinct().findStrings().sorted().toMutableList()
                                if (dates.isNotEmpty()) {
                                    val today = getTodayFormatted()
                                    val todayPosition = dates.indexOf(today)
                                    dateTabAdapter?.submitList(dates) {
                                        binding.tablayoutEpg.setSelectedPosition(todayPosition)
                                        binding.tablayoutEpg.findViewHolderForAdapterPosition(
                                            todayPosition
                                        )?.itemView?.isSelected = true
                                    }
                                    dateTabAdapter?.updateSelectedPosition(today)
                                    helpViewModel.currentEpgTab = today
                                    val currentTime = System.currentTimeMillis() / 1000
                                    val thisEpg = filterEpgDataListForHorizontal(today)
                                    val currentEpg =
                                        epg.find { (it.startTimestamp!! + timeOffSetSeconds) <= currentTime && (it.stopTimestamp!! + timeOffSetSeconds) >= currentTime }
                                    if (currentEpg != null) {
                                        helpViewModel.currentFullEpgProgramId =
                                            currentEpg.idByAccountData
                                    }
                                    fullEpgAdapter?.submitList(thisEpg)
                                    fullEpgAdapter?.setCurrentChannelId(tvChannel.idByAccountData)
                                    binding.rvFullepg.post {
                                        binding.rvFullepg.requestFocus()
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@FullEpgFragment.requireActivity(),
                                        "No EPG data received!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    delay(2000)
                                    helpViewModel.isFullScreenFullEpg = false
                                    parentFragmentManager.popBackStack()
                                }
                            }
                        }
                    }
                }
                if (helpViewModel.isTvFullScreen) {
                    val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                    if (mainFragment is TvChannelsFragment) {
                        mainFragment.setVideoViewNotFullScreen()
                        mainFragment.makeFullEpgVisible()
                        binding.rvFullepg.requestFocus()
                    }
                }
            }
        }

        stalkerViewModel.portalEpgFetchedComplete.observe(viewLifecycleOwner) {portalEpg ->
            when (portalEpg) {
                1 ->    {
                        binding.loadepgProgressBar.visibility = View.INVISIBLE
                        binding.tvNoepgdata.visibility = View.INVISIBLE
                        portalEpgFetched()
                        stalkerViewModel.portalEpgFetchedCompleteCompleteReset()
                    }
                2 -> {

                }
            }
        }

        binding.rvFullepg.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                Log.d("FULLEPGFOKUS", "RV FULL EPG HAS FOCUS")
            }
        }

        binding.tablayoutEpg.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                Log.d("FULLEPGFOKUS", "RV TAB LAYOUT HAS FOCUS")
            }
        }


        binding.playProgram.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                closeEpgOptions()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.playProgram.setOnClickListener {
            if (tvChannelPos == helpViewModel.currentPlayingChannelPosition) {
                makeFullScreen()
            } else {
                changeChannel()
            }
        }

        binding.replayProgram.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                closeEpgOptions()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.replayProgram.setOnClickListener {
            if (tvChannel != null) {
                val tvCategory = if (helpViewModel.isFullScreenFullEpg) {
                    helpViewModel.fullScreenFocusedTvCategory
                } else {
                    helpViewModel.currentFocusedTvCategory
                }
                stopCurrentStreamAndShowProgressBar()
                if (tvChannel.linkedEpgChannel?.target?.isExternalEpg == true) {
                    if (tvChannel.account.target.isXtream) {
                        clickedEpgData?.let { epgData ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val thisEpg = xtreamViewModel.findEpgMatch(
                                    epgData,
                                    tvChannel,
                                    tvCategory!!
                                )
                                Log.d("CATCHUP XTREAM", "NOT EXTERN: ${epgData.name}")
                                when (thisEpg) {
                                    is Resource.Success -> {
                                        if (thisEpg.data != null) {
                                            val startTime = thisEpg.data.start
                                            val endTime = thisEpg.data.end
                                            getXtreamCatchup(
                                                tvChannel,
                                                startTime,
                                                endTime
                                            )
                                        }
                                    }
                                    is Resource.Error -> {
                                        closeEpgOptions()
                                        Toast.makeText(
                                            this@FullEpgFragment.requireActivity(),
                                            "Error fetching Catchup Link!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        binding.rvFullepg.requestFocus()
                                    }
                                }
                            }
                        }
                    } else {
                        clickedEpgData?.let { epgData ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val thisEpg = stalkerViewModel.findEpgMatch(
                                    epgData,
                                    tvChannel,
                                    epgData.datum,
                                    tvCategory!!
                                )
                                Log.d("CATCHUP STALKER", "NOT EXTERN: ${epgData.name}")
                                when (thisEpg) {
                                    is Resource.Success -> {
                                        if (thisEpg.data != null) {
                                            val epgId = thisEpg.data.id
                                            getStalkerCatchupLink(
                                                epgId
                                            )
                                        }
                                    }
                                    is Resource.Error -> {
                                        closeEpgOptions()
                                        Toast.makeText(
                                            this@FullEpgFragment.requireActivity(),
                                            "Error fetching Catchup Link!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        binding.rvFullepg.requestFocus()
                                    }
                                }
                            }
                        }
                    }
                } else {
                        if (tvChannel.linkedEpgChannel?.target?.epgsource?.target?.isXtreamEpg == true) {
                            clickedEpgData?.let { epgData ->
                                getXtreamCatchup(tvChannel, epgData.startTime, epgData.endTime)
                            }
                        } else {
                            clickedEpgData?.let { epgData ->
                                getStalkerCatchupLink(epgData.epgId)
                            }
                        }
                    }
            }
        }

        binding.remindProgram.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                closeEpgOptions()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.remindProgram.setOnClickListener {
            helpViewModel.currentSelectedEpgForSelectedChannel?.let { epg ->
                helpViewModel.currentFocusedChannel?.let { channel ->
                    val isProgramme = programmeBox.query(Programme_.epgForCh.equal("${epg.idByAccountData}_${channel.idByAccountData}")).build().findFirst()
                    if (isProgramme != null) {
                        programmeBox.remove(isProgramme)
                        epg.isRemembered = false
                        epgDataBox.put(epg)
                        val currentEpgPos = fullEpgAdapter?.currentList?.indexOf(epg)
                        if (currentEpgPos != null) {
                            fullEpgAdapter?.notifyItemChanged(currentEpgPos)
                        }
                        closeEpgOptions()
                    } else {
                        val timeOffSet =
                            tvChannel?.epgTimeOffSet ?: tvChannelPos?.tvcategory?.target?.epgTimeOffSet
                            ?: tvChannel?.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                            ?: 0
                        val thisProgramme = Programme(
                            0,
                            "${epg.idByAccountData}_${channel.idByAccountData}",
                            epg.startTimestamp ?: 0L,
                            epg.stopTimestamp ?: 0L,
                            helpViewModel.settings?.tvReminderTime ?: 10L
                        )
                        programmeBox.put(thisProgramme)
                        thisProgramme.apply {
                            epgData.target = epg
                            tvchannels.target = tvChannel
                        }
                        programmeBox.put(thisProgramme)
                        epg.isRemembered = true
                        epgDataBox.put(epg)
                        val currentEpgPos = fullEpgAdapter?.currentList?.indexOf(epg)
                        if (currentEpgPos != null) {
                            fullEpgAdapter?.notifyItemChanged(currentEpgPos)
                        }
                        if (!Settings.canDrawOverlays(requireContext())) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + requireContext().packageName)
                            )
                            startActivity(intent)
                        }
                        Log.d("REMIND INTERN EPG", "$epg")
                        helpViewModel.setReminder(requireContext(), thisProgramme, timeOffSet)
                        Toast.makeText(this@FullEpgFragment.requireActivity(), "Reminder added: ${thisProgramme.epgData.target.name}", Toast.LENGTH_SHORT).show()
                        closeEpgOptions()
                    }
                }
            }
        }

        binding.searchProgram.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                closeEpgOptions()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        helpViewModel.reminderAdded.observe(viewLifecycleOwner) { reminder ->
            when (reminder) {
                1 -> {
                    Toast.makeText(this.requireActivity(), "Erinnerung hinzugefügt!", Toast.LENGTH_SHORT).show()
                    helpViewModel.updateReminderAddedReset()
                }
            }
        }

        binding.tvDescription.post {
            val layout = binding.tvDescription.layout
            Log.d("TEXTVIEW_HEIGHT", "Height of TextView: ${binding.tvDescription.height}")
            if (layout != null) {
                val isTextTruncated = layout.height - binding.tvDescription.height > 0
                if (isTextTruncated) {
                    binding.tvDescription.isVerticalScrollBarEnabled = true
                    binding.tvDescription.movementMethod = ScrollingMovementMethod()
                    binding.ivMoretext.visibility = View.VISIBLE
                } else {
                    binding.tvDescription.isVerticalScrollBarEnabled = false
                    binding.tvDescription.movementMethod = null
                    binding.tvDescription.scrollTo(0, 0) // Zurück zur Ausgangsposition
                    binding.ivMoretext.visibility = View.GONE
                }
            }
        }

        binding.tvDescription.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val animation = AnimationUtils.loadAnimation(this@FullEpgFragment.requireActivity(), R.anim.blinked_border)
                binding.descriptionborder.visibility = View.VISIBLE
                binding.descriptionborder.startAnimation(animation)
            } else {
                binding.descriptionborder.visibility = View.GONE
                binding.descriptionborder.clearAnimation()
            }
        }

        binding.tvDescription.setOnKeyListener { _, keyCode, event ->
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    val layout = binding.tvDescription.layout

                    // Überprüfen, ob der Text abgeschnitten ist (abgeschnitten, wenn die letzte Zeile mehr als die Höhe des TextViews hinausgeht)
                    val isTextTruncated = layout.height - binding.tvDescription.height > 0

                    // Wenn der Text abgeschnitten ist, Scrollen zulassen
                    if (isTextTruncated) {

                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (helpViewModel.isFullEpgContainerOpened) {
                                    binding.tvDescription.scrollTo(0, 0)
                                    binding.gradientView.visibility = View.VISIBLE
                                    binding.ivMoretext.visibility = View.VISIBLE
                                    val fullepgFragment =
                                    setFocusToEpgDataFromDescr()
                                    return@setOnKeyListener true
                                } else {
                                    closeFragment()
                                    return@setOnKeyListener true
                                }
                            }

                            KeyEvent.KEYCODE_BACK -> {
                                if (helpViewModel.isFullEpgContainerOpened) {
                                    binding.tvDescription.scrollTo(0, 0)
                                    val fullepgFragment =
                                        parentFragmentManager.findFragmentById(R.id.rv_layout_FullEpg)
                                    if (fullepgFragment is FullEpgFragment) {
                                        fullepgFragment.setFocusToEpgDataFromDescr()
                                    }
                                    return@setOnKeyListener true
                                } else {
                                    closeFragment()
                                    val tvChannelsFragment =
                                        parentFragmentManager.findFragmentById(R.id.navHostFragment)
                                    if (tvChannelsFragment is TvChannelsFragment) {
                                        tvChannelsFragment.closeDetailEpgContainer()
                                    }
                                    return@setOnKeyListener true
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                // Scroll nach oben
                                val newScrollY = (binding.tvDescription.scrollY - 50).coerceAtLeast(0)
                                binding.tvDescription.scrollTo(0, newScrollY)
                                return@setOnKeyListener true // Verhindern, dass der Rest ausgeführt wird
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                // Berechne den maximalen Scrollbereich
                                val maxScrollY = binding.tvDescription.layout.height - binding.tvDescription.height
                                val newScrollY = (binding.tvDescription.scrollY + 50).coerceAtMost(maxScrollY + (2 * binding.tvDescription.lineHeight))  // 2 Zeilen Puffer
                                binding.tvDescription.scrollTo(0, newScrollY)
                                return@setOnKeyListener true
                            }
                            else -> return@setOnKeyListener false // Falls keine relevante Taste gedrückt wird
                        }
                    } else {
                        // Wenn der Text nicht abgeschnitten ist, führ die Rückkehr zur ursprünglichen Aktion aus
                        if (helpViewModel.epgPreviewEpgDetail) {
                            val tvChannelsFragment =
                                parentFragmentManager.findFragmentById(R.id.navHostFragment)
                            if (tvChannelsFragment is TvChannelsFragment) {
                                tvChannelsFragment.closeDetailEpgContainer()
                                parentFragmentManager.popBackStack()
                            }
                            return@setOnKeyListener true
                        } else {
                            return@setOnKeyListener false
                        }
                    }
                }

                else -> {false}
            }
        }
    }


    fun getFormattedDateList(): List<String> {
        val dateList = mutableListOf<String>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")

        val calendar = Calendar.getInstance()

        for (i in -2..2) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, i)

            val date = calendar.time
            val formattedDate = dateFormat.format(date)
            dateList.add(formattedDate)
        }
        return dateList
    }

    private fun portalEpgFetched() {
        val tvChannel = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedChannel
        } else {
            helpViewModel.currentFocusedChannel
        }

        val tvChannelPos = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedChannelPosition
        } else {
            helpViewModel.currentFocusedChannPosition
        }
        if (tvChannel != null && tvChannelPos != null) {
            resetData()
            val timeOffSet =
                tvChannel.epgTimeOffSet ?: tvChannelPos.tvcategory.target?.epgTimeOffSet
                ?: tvChannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                ?: 0
            val timeOffSetSeconds = calculateTimeOffsetInSeconds(timeOffSet)
            val epg = filterEpgDataListForHorizontal(helpViewModel.currentEpgTab)
            if (epg.isNotEmpty()) {
                val currentTime = System.currentTimeMillis() / 1000
                val currentEpg =
                    epg.find { (it.startTimestamp!! + timeOffSetSeconds) <= currentTime && (it.stopTimestamp!! + timeOffSetSeconds) >= currentTime }
                helpViewModel.currentFullEpgProgramId = currentEpg?.idByAccountData ?: ""
                fullEpgAdapter?.setCurrentChannelId(tvChannel.idByAccountData)
                fullEpgAdapter?.submitList(epg)
                val today = getTodayFormatted()
                if (helpViewModel.currentEpgTab == today) {
                    binding.rvFullepg.post {
                        if (currentEpg != null) {
                            val currentProgramPosition =
                                fullEpgAdapter?.currentList!!.indexOf(currentEpg)
                            binding.rvFullepg.setSelectedPosition(currentProgramPosition)
                            if (isFirstOpen) {
                                binding.rvFullepg.requestFocus()
                                showDetailEpg(currentEpg)
                                isFirstOpen = false
                            }
                        }
                    }
                } else {
                    val firstEpg = epg.firstOrNull()
                    if (firstEpg != null) {
                        showDetailEpg(firstEpg)
                    }
                    binding.tablayoutEpg.requestFocus()
                }
                addChannelAndDateToDatabase(tvChannel)
            } else {
                binding.loadepgProgressBar.visibility = View.INVISIBLE
                binding.tvNoepgdata.visibility = View.VISIBLE
            }
        } else {
            binding.loadepgProgressBar.visibility = View.INVISIBLE
            binding.tvNoepgdata.visibility = View.VISIBLE
        }
    }

    private fun addChannelAndDateToDatabase(tvChannelOB: TvChannelOB) {
        stalkerViewModel.viewModelScope.launch {
            withContext(Dispatchers.IO) {
                portalEpgAndDateBox.put(
                    PortalEpgAndDate(
                        0,
                        tvChannelOB.idByAccountData,
                        helpViewModel.currentEpgTab
                    )
                )
            }
        }
    }

    fun makeFullScreen() {
        closeEpgOptions()
        helpViewModel.currentEpgTab = ""
        helpViewModel.currentFullEpgProgramId = ""
        helpViewModel.currentSelectedEpgForSelectedChannel = null
        helpViewModel.isFullScreenFullEpg = false
        val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (mainFragment is TvChannelsFragment) {
            mainFragment.setVideoViewFullScreen()
        }
        parentFragmentManager.popBackStack()
    }

    fun changeChannel() {
        val tvChannel = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedChannel
        } else {
            helpViewModel.currentFocusedChannel
        }

        val tvChannelPos = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedChannelPosition
        } else {
            helpViewModel.currentFocusedChannPosition
        }

        val tvCategory = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedTvCategory
        } else {
            helpViewModel.currentFocusedTvCategory
        }
        val account = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedAccount
        } else {
            helpViewModel.currentFocusedTvAccount
        }

        if (tvChannel != null && tvChannelPos != null) {
            val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (mainFragment is TvChannelsFragment) {
                mainFragment.resetPlayingChannel(tvChannelPos)
                helpViewModel.currentPlayingChannelPosition =
                    tvChannelPos
                helpViewModel.currentPlayingChannel = tvChannel
                helpViewModel.currentPlayingTvCategory = tvCategory
                helpViewModel.currentPlayingTvAccount = account
                if (helpViewModel.isPlayingCatchup) {

                } else {
                    closeEpgOptions()
                    val channelAccount = tvChannel.account.target
                    mainFragment.changingPlayingChannel(tvChannelPos)
                }
                resetFullEpg()
            }
        }
    }

    fun resetFullEpg() {
        val currentEpgPosition = fullEpgAdapter?.currentList?.indexOf(helpViewModel.currentSelectedEpgForSelectedChannel)
        if (currentEpgPosition != null) {
            fullEpgAdapter?.notifyItemChanged(currentEpgPosition)
        }
    }

    fun getTodayFormatted(): String {
        // Erzeuge einen DateTimeFormatter für das Format 'yyyy-MM-dd'
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        // Hole das heutige Datum
        val today = LocalDate.now()

        // Formatieren des Datums in das gewünschte Format
        return today.format(formatter)
    }


    private fun prepareTabRecyclerview() {
        dateTabAdapter = DateTabAdapter(this, helpViewModel)
        binding.tablayoutEpg.apply {
            adapter = dateTabAdapter
            addItemDecoration(
                DpadLinearSpacingDecoration.create(
                    itemSpacing = 10,
                    edgeSpacing = 10,
                    perpendicularEdgeSpacing = 5
                )
            )
            setSmoothScrollMaxPendingAlignments(2)
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(false, false)
            setHasFixedSize(true)
        }
    }

    private fun prepareFullEpgRecyclerview() {
        fullEpgAdapter = FullEpgAdapter(onClickListener,this, helpViewModel)
        binding.rvFullepg.apply {
            adapter = fullEpgAdapter
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(false, false)
            addItemDecoration(
                DpadLinearSpacingDecoration.create(
                    itemSpacing = 10,
                    edgeSpacing = 10,
                    perpendicularEdgeSpacing = 5
                )
            )
        }
    }

    fun calculateTimeOffsetInSeconds(timeOffset: Int): Long {
        return (timeOffset * 3600).toLong()
    }

    private val onClickListener = FullEpgAdapter.OnClickListener { epgData, view ->
        val slideIn = AnimationUtils.loadAnimation(this@FullEpgFragment.requireActivity(), R.anim.slide_in_right)
        binding.menuEpgOptions.visibility = View.VISIBLE
        binding.menuEpgOptions.startAnimation(slideIn)
        binding.overlayLayout.visibility = View.VISIBLE
        binding.menuEpgOptions.requestFocus()
        clickedEpgData = epgData
        setEpgOptions(epgData)
    }

    fun showDetailEpg(epgData: EpgDataOB) {
        if (helpViewModel.currentSelectedEpgForSelectedChannel?.idByAccountData != epgData.idByAccountData) {
            val oldPos = fullEpgAdapter?.currentList?.indexOf(helpViewModel.currentSelectedEpgForSelectedChannel)
            helpViewModel.currentSelectedEpgForSelectedChannel = epgData
            val newPos = fullEpgAdapter?.currentList?.indexOf(epgData)
            binding.rvFullepg.post {
                if (oldPos != null) {
                    fullEpgAdapter?.notifyItemChanged(oldPos)
                }
                if (newPos != null) {
                    fullEpgAdapter?.notifyItemChanged(newPos)
                }
            }
            resetData()
            showNewEpgData(epgData)
        }
    }

    fun setEpgOptions(epgData: EpgDataOB) {
        val tvChannel = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedChannel
        } else {
            helpViewModel.currentFocusedChannel
        }

        val tvChannelPos = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedChannelPosition
        } else {
            helpViewModel.currentFocusedChannPosition
        }


        if (tvChannel != null && tvChannelPos != null) {

        val currentTime = System.currentTimeMillis() / 1000
        val timeOffSet = tvChannel.epgTimeOffSet ?: tvChannelPos.tvcategory.target?.epgTimeOffSet ?: helpViewModel.currentFocusedChannel?.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0
        val timeOffSetSeconds = calculateTimeOffsetInSeconds(timeOffSet)
        val startTime = epgData.startTimestamp?.plus(timeOffSetSeconds)
        val endTime = epgData.stopTimestamp?.plus(timeOffSetSeconds)

            binding.playProgram.visibility =
                if (helpViewModel.currentFullEpgProgramId == epgData.idByAccountData) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            binding.playProgram.text =
                if (tvChannelPos == helpViewModel.currentPlayingChannelPosition) {
                    "Show Fullscreen!"
                } else {
                    "Play Program"
                }

            if (startTime != null) {
                binding.replayProgram.visibility =
                    if (tvChannel.enable_tv_archive == 1 && startTime <= currentTime) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }

                binding.replayProgram.text = if (startTime <= currentTime) {
                    if (endTime!! >= currentTime) {
                        "Watch from beginning"
                    } else {
                        "Rewatch"
                    }
                } else {
                    "Watch Program"
                }

                binding.remindProgram.visibility = if (startTime > currentTime) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                if (startTime > currentTime) {
                    val isProgrammReminded = programmeBox.query(
                        Programme_.epgForCh.equal("${epgData.idByAccountData}_${tvChannel.idByAccountData}")
                    ).build().findFirst()
                    if (isProgrammReminded != null) {
                        binding.remindProgram.text = "Remove reminder"
                    } else {
                        binding.remindProgram.text = "Set reminder"
                    }
                }

                if (helpViewModel.currentFullEpgProgramId == epgData.idByAccountData) {
                    binding.playProgram.requestFocus()
                } else {
                    if (tvChannel.enable_tv_archive == 1 && startTime <= currentTime) {
                        binding.replayProgram.requestFocus()
                    } else {
                        if (startTime > currentTime) {
                            binding.remindProgram.requestFocus()
                        } else {
                            binding.searchProgram.requestFocus()
                        }
                    }
                }
            }
            setContainerSelected()
        }
    }

    fun closeEpgOptions() {
        val slideOut = AnimationUtils.loadAnimation(this@FullEpgFragment.requireActivity(), R.anim.slide_out_to_right)
        binding.menuEpgOptions.visibility = View.GONE
        binding.menuEpgOptions.startAnimation(slideOut)
        binding.overlayLayout.visibility = View.INVISIBLE
        setContainerDeSelected()
        binding.rvFullepg.requestFocus()
    }


    fun checkIfExternStalkerOrXtream(datum: String) {
        binding.tvNoepgdata.visibility = View.INVISIBLE
        val tvChannel = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedChannel
        } else {
            helpViewModel.currentFocusedChannel
        }

        val tvChannelPos = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedChannelPosition
        } else {
            helpViewModel.currentFocusedChannPosition
        }
        if (helpViewModel.currentEpgTab != datum && tvChannel != null && tvChannelPos != null) {
            val today = getTodayFormatted()
            val timeOffSet = tvChannel.epgTimeOffSet ?: tvChannelPos.tvcategory.target?.epgTimeOffSet ?: helpViewModel.currentFocusedChannel?.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0
            val timeOffSetSeconds = calculateTimeOffsetInSeconds(timeOffSet)
            resetData()
        if (tvChannel.linkedEpgChannel?.target?.isExternalEpg == true || tvChannel.account.target?.isXtream == true) {
                dateTabAdapter?.updateSelectedPosition(datum)
                fullEpgAdapter?.submitList(null)
                helpViewModel.currentEpgTab = datum

                val epg = filterEpgDataListForHorizontal(datum)

                fullEpgAdapter?.submitList(epg)
                if (datum == today) {
                    val currentTime = System.currentTimeMillis() / 1000
                    val currentEpg = epg.find { (it.startTimestamp!! + timeOffSetSeconds) <= currentTime && (it.stopTimestamp!! + timeOffSetSeconds) >= currentTime }
                    if (currentEpg != null) {
                        helpViewModel.currentFullEpgProgramId = currentEpg.idByAccountData
                    }
                    val currentEpgPosition = fullEpgAdapter?.currentList?.indexOf(currentEpg)
                    binding.rvFullepg.post {
                        val safeBinding = _binding ?: return@post  // binding ist evtl. schon null

                        currentEpgPosition?.let { pos ->
                            safeBinding.rvFullepg.setSelectedPosition(pos)
                            currentEpg?.let { epg ->
                                showDetailEpg(epg)
                            }
                        }
                    }
                } else {
                    fullEpgAdapter?.submitList(epg)
                    val firstEpg = epg.firstOrNull()
                    if (firstEpg != null) {
                        showDetailEpg(firstEpg)
                    }
            }
        } else {
                dateTabAdapter?.updateSelectedPosition(datum)
                helpViewModel.currentEpgTab = datum

                fullEpgAdapter?.submitList(null)
                val portalEpgAndDate =
                    portalEpgAndDateBox.query(
                        PortalEpgAndDate_.channelIdByAccount.equal(tvChannel.idByAccountData)
                            .and(PortalEpgAndDate_.datum.equal(datum))
                    ).build().findFirst()
                if (portalEpgAndDate != null) {
                    val epg = filterEpgDataListForHorizontal(datum)
                    fullEpgAdapter?.submitList(epg)
                    if (datum == today) {
                        val currentTime = System.currentTimeMillis() / 1000
                        val currentEpg = epg.find { (it.startTimestamp!! + timeOffSetSeconds) <= currentTime && (it.stopTimestamp!! + timeOffSetSeconds) >= currentTime }
                        if (currentEpg != null) {
                            helpViewModel.currentFullEpgProgramId = currentEpg.idByAccountData
                        }
                        val currentEpgPosition = fullEpgAdapter?.currentList?.indexOf(currentEpg)
                        binding.rvFullepg.post {
                            if (currentEpgPosition != null) {
                                binding.rvFullepg.setSelectedPosition(currentEpgPosition)
                                if (currentEpg != null) {
                                    showDetailEpg(currentEpg)
                                }
                            }
                                if (isFirstOpen) {
                                    binding.rvFullepg.requestFocus()
                                    isFirstOpen = false
                                } else {
                                    binding.tablayoutEpg.requestFocus()
                            }
                        }
                    } else {
                        val firstEpg = epg.firstOrNull()
                        if (firstEpg != null) {
                            showDetailEpg(firstEpg)
                        }
                        binding.tablayoutEpg.requestFocus()
                    }
                } else {
                    val account = if (helpViewModel.isFullScreenFullEpg) {
                        helpViewModel.fullScreenFocusedAccount
                    } else {
                        helpViewModel.currentFocusedTvAccount
                    }
                    if (account != null && account.epgsources.any { it.isPlaylistEpg }) {
                        stalkerViewModel.epgLoadJob?.cancel()
                        stalkerViewModel.fetchEpgDataForDay = true
                        binding.loadepgProgressBar.visibility = View.VISIBLE
                        Log.d("CHECKFULLSCREENFULLEPG","API STALKER CALL")
                        stalkerViewModel.getEpgByChannelByDay(tvChannel, datum)

                    } else {
                        binding.tvNoepgdata.visibility = View.VISIBLE
                    }
                }
            }
        } else {
            binding.tablayoutEpg.requestFocus()
        }
    }

    private fun filterEpgDataListForHorizontal(datum: String): List<EpgDataOB> {
        val tvChannel = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedChannel
        } else {
            helpViewModel.currentFocusedChannel
        }
        val epgChId = tvChannel?.linkedEpgChannel?.target?.chEpgId
        if (epgChId != null) {
            return epgDataBox.query(EpgDataOB_.epgChId.equal(epgChId)
                .and(EpgDataOB_.datum.equal(datum))).order(EpgDataOB_.startTimestamp).build().find()
        } else {
            return emptyList()
        }
    }

    fun setFocusToEpgData() {
        if (fullEpgAdapter?.currentList!!.isNotEmpty()) {
            binding.rvFullepg.requestFocus()
        } else {
            binding.tablayoutEpg.requestFocus()
        }
    }

    fun setFocusToEpgDataFromDescr() {
        val pos = fullEpgAdapter?.currentList?.indexOf(helpViewModel.currentSelectedEpgForSelectedChannel)
        if (pos != null) {
            fullEpgAdapter?.notifyItemChanged(pos)
        }
        binding.rvFullepg.requestFocus()
    }

    fun setFocusToTab() {
        binding.tablayoutEpg.requestFocus()
    }

    fun stopCurrentStreamAndShowProgressBar() {
        val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (mainFragment is TvChannelsFragment) {
            mainFragment.stopPlayer()
            mainFragment.showProgressBar()
            closeEpgOptions()
        }
    }

    fun setFocusToDescription(epgData: EpgDataOB) {
        val position = fullEpgAdapter?.currentList?.indexOf(epgData)
        if (position != null) {
            fullEpgAdapter?.notifyItemChanged(position)
        }
        if (epgData.descr.isNotEmpty()) {
            focusDescription()
        }
    }

    ///CATCHUP

    fun getStalkerCatchupLink(epgId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val tvChannel = if (helpViewModel.isFullScreenFullEpg) {
                helpViewModel.fullScreenFocusedChannel
            } else {
                helpViewModel.currentFocusedChannel
            }

            val tvChannelPos = if (helpViewModel.isFullScreenFullEpg) {
                helpViewModel.fullScreenFocusedChannelPosition
            } else {
                helpViewModel.currentFocusedChannPosition
            }

            val tvCategory = if (helpViewModel.isFullScreenFullEpg) {
                helpViewModel.fullScreenFocusedTvCategory
            } else {
                helpViewModel.currentFocusedTvCategory
            }
            val account = if (helpViewModel.isFullScreenFullEpg) {
                helpViewModel.fullScreenFocusedAccount
            } else {
                helpViewModel.currentFocusedTvAccount
            }
            if (account != null && clickedEpgData != null) {
                val catchUp = stalkerViewModel.getTvCatchupLink(
                    account.stalkerUrl,
                    cmd = "/media/$epgId.mpg",
                    cookie = "mac=${account.macAddress}; stb_lang=en; timezone=${account.timezone};",
                    token = "Bearer ${account.token}",
                    account.userAgent
                ).await()
                when (catchUp) {
                    is Resource.Success -> {
                        Log.d("CATCHUP STALKER", "CATCHUPDATA: ${catchUp.data}")
                        helpViewModel.isPlayingCatchup = true
                        helpViewModel.catchupEpgData = clickedEpgData
                        helpViewModel.currentPlayingChannel = tvChannel
                        helpViewModel.currentPlayingChannelPosition = tvChannelPos
                        helpViewModel.currentPlayingTvCategory = tvCategory
                        helpViewModel.currentPlayingTvAccount = account
                        helpViewModel.isFullScreenFullEpg = false
                        val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                        if (mainFragment is TvChannelsFragment) {
                            mainFragment.hideFullEpgAndDetailEpgContainer()
                            helpViewModel.currentPlayingChannelPosition = tvChannelPos
                            val url =  catchUp.data?.removePrefix("ffmpeg ")?.trim() ?: ""
                            Log.d("CATCHUP STALKER", "CATCHUPURL: $url")
                            mainFragment.cancelChannelLoadJob()
                            mainFragment.switchChannel(url)
                            mainFragment.setVideoViewFullScreen()
                        }
                    }
                    is Resource.Error -> {
                        Toast.makeText(
                            this@FullEpgFragment.requireActivity(),
                            "Error fetching Catchup Link!\n${catchUp.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.rvFullepg.requestFocus()
                    }
                }
            }
        }
    }

    fun getXtreamCatchup(channelOB: TvChannelOB, startTime: String, endTime: String) {
        val tvChannel = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedChannel
        } else {
            helpViewModel.currentFocusedChannel
        }

        val tvChannelPos = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedChannelPosition
        } else {
            helpViewModel.currentFocusedChannPosition
        }

        val tvCategory = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedTvCategory
        } else {
            helpViewModel.currentFocusedTvCategory
        }
        val account = if (helpViewModel.isFullScreenFullEpg) {
            helpViewModel.fullScreenFocusedAccount
        } else {
            helpViewModel.currentFocusedTvAccount
        }
        if (account != null && tvCategory != null && tvChannel != null && tvChannelPos != null) {
            val accountUrl = account.stalkerUrl
            val accountUserName = account.username
            val accountPassword = account.macAddress
            val epgStart = startTime.substring(0, 10) + ":" + startTime.substring(
                11,
                13
            ) + "-" + startTime.substring(14, 16)
            val duration = calculateDurationInMinutes(startTime, endTime)
            val url =
                "$accountUrl/streaming/timeshift.php?username=$accountUserName&password=$accountPassword&stream=${channelOB.channelId}&start=$epgStart&duration=$duration"
            Log.d("CATCHUP XTREAM", "NOT EXTERN: CATCHUP URL: $url")
            helpViewModel.isPlayingCatchup = true
            helpViewModel.catchupEpgData = clickedEpgData
            helpViewModel.currentPlayingChannel = tvChannel
            helpViewModel.currentPlayingChannelPosition = tvChannelPos
            helpViewModel.currentPlayingTvCategory = tvCategory
            helpViewModel.currentPlayingTvAccount = account
            closeEpgOptions()

            helpViewModel.isFullScreenFullEpg = false
            val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (mainFragment is TvChannelsFragment) {
                mainFragment.hideFullEpgAndDetailEpgContainer()
                helpViewModel.currentPlayingChannelPosition =
                    tvChannelPos
                mainFragment.cancelChannelLoadJob()
                mainFragment.switchChannel(
                    url
                )
                mainFragment.setVideoViewFullScreen()
            }
            parentFragmentManager.popBackStack()
        }
    }

    fun calculateDurationInMinutes(startString: String, endString: String): Long {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val startDateTime = LocalDateTime.parse(startString, formatter)
        val endDateTime = LocalDateTime.parse(endString, formatter)
        return Duration.between(startDateTime, endDateTime).toMinutes()
    }

    fun setContainerSelected() {
        binding.detailepgcontainerview.isSelected = true
        binding.detailepgcontainerview.requestLayout()
    }

    fun setContainerDeSelected() {
        binding.detailepgcontainerview.isSelected = false
        binding.detailepgcontainerview.requestLayout()
    }

    fun focusDescription() {
        binding.tvDescription.post {
            val layout = binding.tvDescription.layout
            if (layout != null) {
                val totalLines = layout.lineCount
                val lastLineBottom = layout.getLineBottom(totalLines - 1)

                // Wenn der Text abgeschnitten ist oder 1-2 Zeilen weniger angezeigt werden, dann als abgeschnitten betrachten
                val tolerance = 4 // Toleranz für 1-2 Zeilen weniger
                val isTextTruncated = lastLineBottom > (binding.tvDescription.height - tolerance)

                if (isTextTruncated) {
                    binding.gradientView.visibility = View.GONE
                    binding.ivMoretext.visibility = View.GONE
                    binding.tvDescription.requestFocus()
                } else {
                    setFocusToEpgData()
                }
            }
        }
    }


    fun formatUnixTimestampToTime(unixTimestamp: Long, timeOffset: Int): String {
        try {
            // Konvertiere den Unix-Zeitstempel in ein Date-Objekt
            val date = Date(unixTimestamp * 1000)

            // Erstelle ein SimpleDateFormat-Objekt für das gewünschte Zeitformat
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            // Berechne den Zeitversatz in Stunden (positiv oder negativ)
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.add(Calendar.HOUR_OF_DAY, timeOffset)

            // Gib das formatierte Datum und die Uhrzeit zurück
            return timeFormat.format(calendar.time)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    fun showNewEpgData(epgData: EpgDataOB) {
        if (helpViewModel.currentFocusedChannPosition != null) {
            Log.d("FOCUSEDFULLEPGDATA", "$epgData")
            binding.detailepgcontainerview.visibility = View.VISIBLE
            val timeOffSet = helpViewModel.currentFocusedChannel?.epgTimeOffSet ?: helpViewModel.currentFocusedChannPosition?.tvcategory?.target?.epgTimeOffSet ?: helpViewModel.currentFocusedChannel?.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0
            val timeOffSetSeconds = calculateTimeOffsetInSeconds(timeOffSet)
            val startTime = formatUnixTimestampToTime(epgData.startTimestamp ?: 0, timeOffSet)
            val endTime = formatUnixTimestampToTime(epgData.stopTimestamp ?: 0, timeOffSet)
            binding.tvCurrentStartTime.text = startTime
            binding.tvCurrentEndTime.text = " - ${endTime}"

            if (epgData.name.isEmpty()) {
                binding.tvCurrentProgram.text = resources.getString(R.string.no_information)
                binding.tvCurrentSubtitle.isSelected = false
            } else {
                binding.tvCurrentProgram.text = epgData.name
                binding.tvCurrentProgram.isSelected = true
            }

            if (epgData.sub_title.isEmpty()) {
                binding.tvCurrentSubtitle.visibility = View.GONE
                binding.tvCurrentSubtitle.isSelected = false
            } else {
                binding.tvCurrentSubtitle.visibility = View.VISIBLE
                binding.tvCurrentSubtitle.text = epgData.sub_title
                binding.tvCurrentSubtitle.isSelected = true
            }

            if (epgData.category.isNullOrEmpty()) {
                binding.tvCurrentCategory.visibility = View.GONE
            } else {
                binding.tvCurrentCategory.visibility = View.VISIBLE
                val categoryString = epgData.category!!.joinToString(", ")
                binding.tvCurrentCategory.text = categoryString
            }

            if (epgData.country.isNullOrEmpty()) {
                binding.tvCurrentCountry.visibility = View.GONE
            } else {
                binding.tvCurrentCountry.visibility = View.VISIBLE
                val countryString = epgData.country!!.joinToString(", ")
                binding.tvCurrentCountry.text = countryString
            }

            if (epgData.date.isEmpty()) {
                binding.tvCurrentDate.visibility = View.GONE
            } else {
                binding.tvCurrentDate.visibility = View.VISIBLE
                binding.tvCurrentDate.text = epgData.date
            }

            if (epgData.descr.isEmpty()) {
                binding.tvDescription.text = resources.getString(R.string.no_description)
            } else {
                binding.tvDescription.text = epgData.descr
            }
            val currentTimeMillis = System.currentTimeMillis() / 1000
            if ((epgData.startTimestamp!! + timeOffSetSeconds) <= currentTimeMillis && (epgData.stopTimestamp!! + timeOffSetSeconds) >= currentTimeMillis) {
                val duration =
                    ((epgData.stopTimestamp!! + timeOffSetSeconds).minus(
                        epgData.startTimestamp!! + timeOffSetSeconds
                    ))
                binding.progressBar.max = 100
                binding.progressBar.max = 100
                val progress =
                    ((currentTimeMillis - (epgData.startTimestamp!! + timeOffSetSeconds)) * 100 / duration).toInt()
                binding.progressBar.progress = progress
                binding.progressBar.visibility = View.VISIBLE
                // Berechne die verbleibende Zeit in Sekunden
                val remainingTimeInSeconds = (epgData.stopTimestamp!! + timeOffSetSeconds) - currentTimeMillis
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
                binding.tvRemainingTimeCurrentProgram.text = "$remainingTimeText remaining"
                binding.tvRemainingTimeCurrentProgram.visibility = View.VISIBLE

            } else {
                binding.tvRemainingTimeCurrentProgram.visibility = View.INVISIBLE
                binding.progressBar.visibility = View.INVISIBLE
            }

            binding.tvDescription.post {
                val layout = binding.tvDescription.layout
                Log.d("TEXTVIEW_HEIGHT", "Height of TextView: ${binding.tvDescription.height}")
                if (layout != null) {
                    val isTextTruncated = layout.height - binding.tvDescription.height > 0
                    if (isTextTruncated) {
                        binding.tvDescription.isVerticalScrollBarEnabled = true
                        binding.tvDescription.movementMethod = ScrollingMovementMethod()
                        binding.gradientView.visibility = View.VISIBLE
                        binding.ivMoretext.visibility = View.VISIBLE
                    } else {
                        binding.tvDescription.isVerticalScrollBarEnabled = false
                        binding.tvDescription.movementMethod = null
                        binding.tvDescription.scrollTo(0, 0) // Zurück zur Ausgangsposition
                        binding.gradientView.visibility = View.GONE
                        binding.ivMoretext.visibility = View.GONE
                    }
                }
            }

        } else {
            resetData()
        }
        if (helpViewModel.epgPreviewEpgDetail) {
            binding.tvDescription.requestFocus()
        }
    }


    fun resetData() {
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvRemainingTimeCurrentProgram.visibility = View.INVISIBLE
        binding.tvCurrentDate.visibility = View.INVISIBLE
        binding.tvCurrentCategory.visibility = View.INVISIBLE
        binding.tvCurrentCountry.visibility = View.INVISIBLE
        binding.tvDescription.text = ""
        binding.tvCurrentProgram.text = ""
        binding.tvCurrentEndTime.text = ""
        binding.tvCurrentStartTime.text = ""
        binding.tvCurrentSubtitle.visibility = View.INVISIBLE
    }


    fun closeFragment() {
        if (helpViewModel.isFullScreenFullEpg) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            helpViewModel.currentEpgTab = ""
            helpViewModel.currentFullEpgProgramId = ""
            helpViewModel.currentSelectedEpgForSelectedChannel = null
            dateTabAdapter?.submitList(null)
            fullEpgAdapter?.submitList(null)

            parentFragmentManager.popBackStack()
            val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (containerFragment is TvChannelsFragment) {
                containerFragment.setVideoViewFullScreen()
            }
            val fullScreenChannelChangeContainer = parentFragmentManager.findFragmentById(R.id.container_fullscreen_channelchange)
            if (fullScreenChannelChangeContainer is FullScreenSelectorFragment) {
                helpViewModel.isFullScreenFullEpg = false
                fullScreenChannelChangeContainer.setTvChannelsVisibilityAnimated(true)
                fullScreenChannelChangeContainer.setFocusToTvChannels()
            }
        } else {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            helpViewModel.currentEpgTab = ""
            helpViewModel.currentFullEpgProgramId = ""
            helpViewModel.currentSelectedEpgForSelectedChannel = null
            dateTabAdapter?.submitList(null)
            fullEpgAdapter?.submitList(null)
            val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (containerFragment is TvChannelsFragment) {
                containerFragment.setFocusToTvChannels()
            }
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvFullepg.setOnClickListener(null)
        binding.rvFullepg.adapter = null
        binding.tablayoutEpg.adapter = null
        fullEpgAdapter = null
        dateTabAdapter = null
        _binding = null
    }

}
