package com.example.mj_player_tv.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentEditChannelNameBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box


@UnstableApi
class EditChannelNameFragment : Fragment(R.layout.fragment_edit_channel_name) {

    private var _binding: FragmentEditChannelNameBinding? = null

    private val binding get() = _binding!!

    private val tvChannBox: Box<TvChannelOB> = ObjectBox.store.boxFor(TvChannelOB::class.java)

    private val channPosBox: Box<ChannelPositions> = ObjectBox.store.boxFor(ChannelPositions::class.java)

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
        _binding = FragmentEditChannelNameBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (helpViewModel.currentFocusedChannel != null) {
            binding.tvOriginalName.text = helpViewModel.currentFocusedChannel!!.name
            binding.etEditedChannelName.setText(helpViewModel.currentFocusedChannel!!.showingName)
            binding.etEditedChannelName.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etEditedChannelName, InputMethodManager.SHOW_IMPLICIT)

            binding.etEditedChannelName.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    // Speichern Sie den neuen Namen hier
                    saveEditedChannelName(helpViewModel.currentFocusedChannel!!)
                    val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.hideSoftInputFromWindow(binding.etEditedChannelName.windowToken, 0)
                    // true zurückgeben, um zu signalisieren, dass das Ereignis verarbeitet wurde
                    true
                } else {
                    // Wenn der Benutzer die Zurücktaste drückt, ohne auf "Done" zu tippen,
                    // setzen Sie den Namen zurück
                    binding.etEditedChannelName.setText(helpViewModel.currentFocusedChannel!!.showingName)
                    // false zurückgeben, um zu signalisieren, dass das Ereignis nicht verarbeitet wurde
                    false
                }
            }


            binding.btnResetChannelName.setOnClickListener {
                val alertDialogBuilder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)

                alertDialogBuilder.setMessage("Reset current channel name to original name?")

                alertDialogBuilder.setPositiveButton("Yes") { _, _ ->
                    resetChannelName(helpViewModel.currentFocusedChannel!!)
                }

                alertDialogBuilder.setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }

                val alertDialog = alertDialogBuilder.create()
                alertDialog.show()
            }
        }
    }

    private fun saveEditedChannelName(channelOB: TvChannelOB) {
        channelOB.showingName = binding.etEditedChannelName.text.toString()
        tvChannBox.put(channelOB)
        val channelPos = channPosBox.get(helpViewModel.currentFocusedChannPosition!!.id)
        helpViewModel.updateFocusedChannel(channelPos)
        updateChannelList()
    }

    private fun resetChannelName(channelOB: TvChannelOB) {
        channelOB.showingName = channelOB.name
        tvChannBox.put(channelOB)
        val channelPos = channPosBox.get(helpViewModel.currentFocusedChannPosition!!.id)
        helpViewModel.updateFocusedChannel(channelPos)
        helpViewModel.updateFocusedChannel(channelPos)
        updateChannelList()
        binding.etEditedChannelName.setText(channelOB.showingName)
        binding.etEditedChannelName.requestFocus()
        binding.etEditedChannelName.setSelection(binding.etEditedChannelName.text.length)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun updateChannelList() {
        val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (containerFragment is TvChannelsFragment) {
            containerFragment.updateChannelList()
        }
    }
}