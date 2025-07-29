package com.example.mj_player_tv.ui

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.activity.addCallback
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.databinding.FragmentDetailepgBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
class DetailEpgFragment : Fragment(R.layout.fragment_detailepg) {

    private var _binding: FragmentDetailepgBinding? = null

    private val binding get() = _binding!!

    val epgSourceBox = ObjectBox.store.boxFor(EpgSource::class.java)

    private var isTimeShiftProgram = 0

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailepgBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            closeFragmentContainer()
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val epgData = helpViewModel.currentSelectedEpgForSelectedChannel


        if (epgData != null && helpViewModel.currentFocusedChannel != null) {
            if (binding.detailepgcontainerview.isGone) {
                binding.detailepgcontainerview.visibility = View.VISIBLE
            }
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
                    val categoryString = epgData.category!!.joinToString(", ")
                    binding.tvCurrentCategory.text = categoryString
                }

                if (epgData.country.isNullOrEmpty()) {
                    binding.tvCurrentCountry.visibility = View.GONE
                } else {
                    val countryString = epgData.country!!.joinToString(", ")
                    binding.tvCurrentCountry.text = countryString
                }

                if (epgData.date.isEmpty()) {
                    binding.tvCurrentDate.visibility = View.GONE
                } else {
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
        } else {
                resetData()
        }

        if (helpViewModel.epgPreviewEpgDetail) {
            binding.tvDescription.requestFocus()
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
                val animation = AnimationUtils.loadAnimation(this@DetailEpgFragment.requireActivity(), R.anim.blinked_border)
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
                                        parentFragmentManager.findFragmentById(R.id.rv_layout_FullEpg)
                                    if (fullepgFragment is FullEpgFragment) {
                                        fullepgFragment.setFocusToEpgDataFromDescr()
                                    }
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
                    val fullepgFragment = parentFragmentManager.findFragmentById(R.id.rv_layout_FullEpg)
                    if (fullepgFragment is FullEpgFragment) {
                        fullepgFragment.setFocusToEpgData()
                    }
                }
            }
        }
    }


    fun calculateTimeOffsetInSeconds(timeOffset: Int): Long {
        return (timeOffset * 3600).toLong()
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


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun showNewEpgData(epgData: EpgDataOB) {
        if (helpViewModel.currentFocusedChannPosition != null) {
            binding.detailepgcontainerview.visibility = View.VISIBLE
            val timeOffSet = helpViewModel.currentFocusedChannel?.epgTimeOffSet ?: helpViewModel.currentFocusedChannPosition?.tvcategory?.target?.epgTimeOffSet ?: helpViewModel.currentFocusedChannel?.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0
            val timeOffSetSeconds = calculateTimeOffsetInSeconds(timeOffSet)
            val startTime = formatUnixTimestampToTime(epgData.startTimestamp ?: 0, timeOffSet)
            val endTime = formatUnixTimestampToTime(epgData.stopTimestamp ?: 0, timeOffSet)
            binding.tvCurrentStartTime.text = startTime
            binding.tvCurrentEndTime.text = " - ${endTime}"

            if (epgData.name.isNullOrEmpty()) {
                binding.tvCurrentProgram.text = resources.getString(R.string.no_information)
                binding.tvCurrentSubtitle.isSelected = false
            } else {
                binding.tvCurrentProgram.text = epgData.name
                binding.tvCurrentProgram.isSelected = true
            }

            if (epgData.sub_title.isNullOrEmpty()) {
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

            if (epgData.date.isNullOrEmpty()) {
                binding.tvCurrentDate.visibility = View.GONE
            } else {
                binding.tvCurrentDate.visibility = View.VISIBLE
                binding.tvCurrentDate.text = epgData.date
            }

            if (epgData.descr.isNullOrEmpty()) {
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
    }

    fun resetData() {
        isTimeShiftProgram = 0
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

    fun hideParentLayout() {
        binding.detailepgcontainerview.visibility = View.GONE
    }

    fun closeFragment() {
        closeFragmentContainer()
        parentFragmentManager.popBackStack()
    }

    private fun closeFragmentContainer() {
        val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (mainFragment is TvChannelsFragment) {
            mainFragment.closeDetailEpgContainer()
        }
    }
}