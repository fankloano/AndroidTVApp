package com.example.mj_player_tv.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.EpgSourceChannel
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentEpgsourceOptionsBinding
import com.example.mj_player_tv.ui.EditEpgSourceNameFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import kotlinx.coroutines.launch

@UnstableApi
class EpgSourceOptionsFragment : Fragment(R.layout.fragment_epgsource_options) {

    private var _binding: FragmentEpgsourceOptionsBinding? = null

    private val binding get() = _binding!!

    val accountBox = ObjectBox.store.boxFor(Accounts::class.java)

    val tvChannBox = ObjectBox.store.boxFor(TvChannelOB::class.java)

    val epgSourceBox = ObjectBox.store.boxFor(EpgSource::class.java)
    val epgChannelBox = ObjectBox.store.boxFor(EpgSourceChannel::class.java)
    val epgDataBox = ObjectBox.store.boxFor(EpgDataOB::class.java)

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
        _binding = FragmentEpgsourceOptionsBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            helpViewModel.clickedEpgSourceOptions = null
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        val epgSource = epgSourceBox.get(helpViewModel.clickedEpgSourceOptions!!.id)
        if (epgSource != null) {
            if (epgSource.isPlaylistEpg) {
                binding.relLayoutAutoUpdate.visibility = View.GONE
                binding.relLayoutUpdateSource.visibility = View.GONE
                binding.relLayoutDeleteSource.visibility = View.GONE
                binding.relLayoutDays.requestFocus()
                binding.tvChannelName.text = epgSource.name
                binding.relLayoutAutoUpdate.isActivated = true
                binding.relLayoutUpdateSource.isActivated = true
                binding.relLayoutDeleteSource.isActivated = true
                binding.relLayoutAutoUpdate.isFocusable = false
                binding.relLayoutAutoUpdate.isFocusableInTouchMode = false
                binding.relLayoutUpdateSource.isFocusable = false
                binding.relLayoutUpdateSource.isFocusableInTouchMode = false
                binding.relLayoutDeleteSource.isFocusable = false
                binding.relLayoutDeleteSource.isFocusableInTouchMode = false
                binding.relLayoutTimeoffset.nextFocusDownId = R.id.relLayout_timeoffset
                binding.relLayoutDays.nextFocusUpId = R.id.relLayout_days
            } else {
                binding.relLayoutAutoUpdate.visibility = View.VISIBLE
                binding.relLayoutUpdateSource.visibility = View.VISIBLE
                binding.relLayoutDeleteSource.visibility = View.VISIBLE
                binding.relLayoutDays.requestFocus()
                binding.tvChannelName.text = "${epgSource.name} (External)"
            }
            if (epgSource.automaticUpdateDays == 168) {
                binding.tvIsautoUpdatePlaylist.text = "7 days"
            } else if (epgSource.automaticUpdateDays == 144) {
                binding.tvIsautoUpdatePlaylist.text = "6 days"
            } else if (epgSource.automaticUpdateDays == 120) {
                binding.tvIsautoUpdatePlaylist.text = "5 days"
            } else if (epgSource.automaticUpdateDays == 96) {
                binding.tvIsautoUpdatePlaylist.text = "4 days"
            } else if (epgSource.automaticUpdateDays == 72) {
                binding.tvIsautoUpdatePlaylist.text = "3 days"
            } else if (epgSource.automaticUpdateDays == 48) {
                binding.tvIsautoUpdatePlaylist.text = "2 days"
            } else if (epgSource.automaticUpdateDays == 0) {
                binding.tvIsautoUpdatePlaylist.text = "${epgSource.automaticUpdateDays}h (Off)"
            } else {
                binding.tvIsautoUpdatePlaylist.text = "${epgSource.automaticUpdateDays} hours"
            }
        }

        binding.relLayoutDays.setOnFocusChangeListener { _, hasFocus ->
            binding.tvDays.isSelected = hasFocus
        }

        binding.relLayoutAutoUpdate.setOnFocusChangeListener { _, hasFocus ->
            binding.tvAutoUpdatePlaylist.isSelected = hasFocus
            binding.tvIsautoUpdatePlaylist.isSelected = hasFocus
        }

        binding.relLayoutAutoUpdate.setOnClickListener {
            helpViewModel.changeAutoUpdateInterval = 1
            changeFragment(AutoUpdateIntervalFragment())
        }

        binding.relLayoutTimeoffset.setOnFocusChangeListener { _, hasFocus ->
            binding.tvTimeOffset.isSelected = hasFocus
        }

        binding.relLayoutTimeoffset.setOnClickListener {
            changeFragment(EpgSourceTimeOffsetFragment())
        }

        binding.relLayoutEdit.setOnFocusChangeListener { _, hasFocus ->
            binding.tvEditSource.isSelected = hasFocus
        }

        binding.relLayoutEdit.setOnClickListener {
            changeFragment(EditEpgSourceNameFragment())
        }

        binding.relLayoutUpdateSource.setOnFocusChangeListener { _, hasFocus ->
            binding.tvUpdateSource.isSelected = hasFocus
        }

        binding.relLayoutUpdateSource.setOnClickListener {
            if (epgSource != null) {
                helpViewModel.setEpgWorker(epgSource)
            }
        }

        binding.relLayoutDeleteSource.setOnFocusChangeListener { _, hasFocus ->
            binding.tvDeleteSource.isSelected = hasFocus
        }

        binding.relLayoutDeleteSource.setOnClickListener {
            if (epgSource != null) {
                if (epgSource.isExternalEpg) {
                    val alertDialogBuilder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)

                    alertDialogBuilder.setMessage("Delete EPG-source?")

                    alertDialogBuilder.setPositiveButton("Yes") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            helpViewModel.deleteEpgSourceRelatedData(epgSource)
                            parentFragmentManager.popBackStack()
                        }
                    }

                    alertDialogBuilder.setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }

                    val alertDialog = alertDialogBuilder.create()
                    alertDialog.show()
                } else {
                    Toast.makeText(this@EpgSourceOptionsFragment.requireActivity(), "Playlist source can't be deleted!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.settings_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}