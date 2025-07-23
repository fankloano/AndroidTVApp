package com.example.mj_player_tv.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.databinding.FragmentEditPlaylistDataBinding
import com.example.mj_player_tv.databinding.FragmentHomeBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistsBinding
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditPlaylistDataFragment : Fragment(R.layout.fragment_edit_playlist_data) {

    private var _binding: FragmentEditPlaylistDataBinding? = null

    private val binding get() = _binding!!

    val accountBox = ObjectBox.store.boxFor(Accounts::class.java)

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
        _binding = FragmentEditPlaylistDataBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            helpViewModel.dataToModifyPlaylist = -1
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val account = accountBox.get(helpViewModel.selectedAccountData!!.id)

        if (account != null && helpViewModel.dataToModifyPlaylist != -1) {
            if (helpViewModel.dataToModifyPlaylist == 0) {
                binding.tvModifyAccountData.text = "Modify Playlist Name"
                binding.tvCurrentAccountData.text =  "Current Playlist Name"
                binding.tvCurrentAccountDataName.text = account.name
                binding.etEditedChannelName.setText(account.name)
            } else if (helpViewModel.dataToModifyPlaylist == 1) {
                binding.tvModifyAccountData.text =  "Modify Server Url"
                binding.tvCurrentAccountData.text = "Current Server Url"
                binding.tvCurrentAccountDataName.text = account.stalkerUrl
                binding.etEditedChannelName.setText(account.stalkerUrl)
            } else if (helpViewModel.dataToModifyPlaylist == 2) {
                binding.tvModifyAccountData.text = "Modify Username"
                binding.tvCurrentAccountData.text =  "Current Username"
                binding.tvCurrentAccountDataName.text =  account.username
                binding.etEditedChannelName.setText(account.username)
            } else {
                if (account.isStalker) {
                    binding.tvModifyAccountData.text = "Modify Mac-Address"
                    binding.tvCurrentAccountData.text =   "Current Mac-Address"
                } else if (account.isXtream) {
                    binding.tvModifyAccountData.text = "Modify Password"
                    binding.tvCurrentAccountData.text = "Current Password"
                } else {

                }
                binding.tvCurrentAccountDataName.text = account.macAddress
                binding.etEditedChannelName.setText(account.macAddress)
            }
            binding.etEditedChannelName.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etEditedChannelName, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.btnPlaylistSave.setOnClickListener {
            if (helpViewModel.dataToModifyPlaylist != -1 && helpViewModel.selectedAccountData != null) {
                if (helpViewModel.dataToModifyPlaylist == 0) {
                    if (binding.etEditedChannelName.text.toString() != account!!.name) {
                        helpViewModel.selectedAccountData!!.name = binding.etEditedChannelName.text.toString()
                        helpViewModel.dataToModifyPlaylist = -1
                        helpViewModel.wasPlaylistChanged = true
                        parentFragmentManager.popBackStack()
                    }
                } else if (helpViewModel.dataToModifyPlaylist == 1) {
                    if (binding.etEditedChannelName.text.toString() != helpViewModel.selectedAccountData!!.stalkerUrl) {
                        helpViewModel.selectedAccountData!!.stalkerUrl = binding.etEditedChannelName.text.toString()
                        helpViewModel.dataToModifyPlaylist = -1
                        helpViewModel.wasPlaylistChanged = true
                        parentFragmentManager.popBackStack()
                    }
                } else if (helpViewModel.dataToModifyPlaylist == 2) {
                    if (binding.etEditedChannelName.text.toString() != helpViewModel.selectedAccountData!!.username) {
                        helpViewModel.selectedAccountData!!.username =
                            binding.etEditedChannelName.text.toString()
                        helpViewModel.dataToModifyPlaylist = -1
                        helpViewModel.wasPlaylistChanged = true
                        parentFragmentManager.popBackStack()
                    }
                } else {
                    if (binding.etEditedChannelName.text.toString() != helpViewModel.selectedAccountData!!.macAddress) {
                        helpViewModel.selectedAccountData!!.macAddress =
                            binding.etEditedChannelName.text.toString()
                        helpViewModel.dataToModifyPlaylist = -1
                        helpViewModel.wasPlaylistChanged = true
                        parentFragmentManager.popBackStack()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}