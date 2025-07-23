package com.example.mj_player_tv.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentHomeBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistInfosBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistsBinding
import com.example.mj_player_tv.ui.settings.PlaylistSettingsFragment
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlaylistInfoFragment : Fragment(R.layout.fragment_playlist_infos) {

    private var _binding: FragmentPlaylistInfosBinding? = null

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
        _binding = FragmentPlaylistInfosBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            helpViewModel.currentAccountName = null
            helpViewModel.currentAccountUrl = null
            helpViewModel.currentAccountUsername = null
            helpViewModel.currentAccountPassword = null
            helpViewModel.wasPlaylistChanged = false
            helpViewModel.lastSelectedPlaylistInfoId = null
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val account = helpViewModel.selectedAccountData

        helpViewModel.lastSelectedPlaylistInfoId?.let { view.findViewById<RelativeLayout>(it).requestFocus() }
            ?: binding.relLayoutUrl.requestFocus()

        if (account != null) {
            if (account.isStalker) {
                binding.relLayoutUsername.visibility = View.GONE
                binding.tvChangePasswordPlaylist.text = "Mac-Address:"
            } else {
                binding.relLayoutUsername.visibility = View.VISIBLE
                binding.tvIsUsernamePlaylist.text = account.username
                binding.tvChangePasswordPlaylist.text = "Password:"
            }

            if (helpViewModel.wasPlaylistChanged) {
                binding.relLayoutSave.isActivated = false
                binding.relLayoutSave.isFocusable = true
                binding.relLayoutSave.isFocusableInTouchMode = true
            } else {
                binding.relLayoutSave.isActivated = true
                binding.relLayoutSave.isFocusable = false
                binding.relLayoutSave.isFocusableInTouchMode = false
            }

            binding.tvIsUrlPlaylist.text = account.stalkerUrl
            binding.tvIsPasswordPlaylist.text = account.macAddress
            binding.tvIsLastUpdatePlaylist.text = convertUnixTimestampToDateTime(account.lastUpdatedDate)
            binding.tvIsLastUpdateStatusTv.text =
                if (account.tvCategoryLoadingOK == 1 && account.tvchannelLoadingOK == 1) {
                    "Categories: Ok / Channels: Ok"
                } else if (account.tvCategoryLoadingOK == 1 && account.tvchannelLoadingOK == 0) {
                    "Categories: Ok / Channels: failed!"
                } else if (account.tvCategoryLoadingOK == 0 && account.tvchannelLoadingOK == 1) {
                    "Categories: failed! / Channels: Ok"
                } else {
                    "Categories: failed! / Channels: failed!"
                }
            binding.tvIsLastUpdateStatusmovie.text = if (account.movieCategoryLoadingOK == 1) {
                "Ok"
            } else {
                "Failed!"
            }
            binding.tvIsLastUpdateStatusSeries.text = if (account.seriesCategoryLoadingOK == 1) {
                "Ok"
            } else {
                "Failed!"
            }

            binding.tvIsNamePlaylist.text = account.name

            binding.tvIsLastUpdatePlaylist.text = convertUnixTimestampToDateTime(account.lastUpdatedDate)

            binding.relLayoutName.setOnFocusChangeListener { _, hasFocus ->
                binding.tvIsNamePlaylist.isSelected = hasFocus
                binding.tvChangeNamePlaylist.isSelected = hasFocus
            }

            binding.relLayoutName.setOnClickListener {
                helpViewModel.lastSelectedPlaylistInfoId = it.id
                changeToModifyFragment(0)
            }

            binding.relLayoutUrl.setOnFocusChangeListener { _, hasFocus ->
                binding.tvChangeUrlPlaylist.isSelected = hasFocus
                binding.tvIsUrlPlaylist.isSelected = hasFocus
            }

            binding.relLayoutUrl.setOnClickListener {
                helpViewModel.lastSelectedPlaylistInfoId = it.id
                changeToModifyFragment(1)
            }


            binding.relLayoutUsername.setOnFocusChangeListener { _, hasFocus ->
                binding.tvChangeUsernamePlaylist.isSelected = hasFocus
                binding.tvIsUsernamePlaylist.isSelected = hasFocus
            }

            binding.relLayoutUsername.setOnClickListener {
                helpViewModel.lastSelectedPlaylistInfoId = it.id
                changeToModifyFragment(2)
            }

            binding.relLayoutPassword.setOnFocusChangeListener { _, hasFocus ->
                binding.tvChangePasswordPlaylist.isSelected = hasFocus
                binding.tvIsPasswordPlaylist.isSelected = hasFocus
            }

            binding.relLayoutPassword.setOnClickListener {
                val cause = if (account.isStalker) {
                    "Mac Address"
                } else {
                    "Password"
                }
                changeToModifyFragment(3)
            }

            binding.relLayoutSave.setOnFocusChangeListener { _, hasFocus ->
                binding.tvSavePlaylist.isSelected = hasFocus
            }

            binding.relLayoutLastupdate.setOnFocusChangeListener { _, hasFocus ->
                binding.tvLastUpdatePlaylist.isSelected = hasFocus
                binding.tvIsLastUpdatePlaylist.isSelected = hasFocus
            }

            binding.relLayoutTvupdatestatus.setOnFocusChangeListener { _, hasFocus ->
                binding.tvLastUpdateStatusTv.isSelected = hasFocus
                binding.tvIsLastUpdateStatusTv.isSelected = hasFocus
            }

            binding.relLayoutMovieupdatestatus.setOnFocusChangeListener { _, hasFocus ->
                binding.tvLastUpdateStatusmovies.isSelected = hasFocus
                binding.tvIsLastUpdateStatusmovie.isSelected = hasFocus
            }

            binding.relLayoutSeriesupdatestatus.setOnFocusChangeListener { _, hasFocus ->
                binding.tvLastUpdateStatusSeries.isSelected = hasFocus
                binding.tvIsLastUpdateStatusSeries.isSelected = hasFocus
            }

            binding.relLayoutSave.setOnClickListener {
                val currentAccount = accountBox.get(helpViewModel.selectedAccountData!!.id)
                if (currentAccount.name != helpViewModel.selectedAccountData!!.name) {
                    currentAccount.name = helpViewModel.selectedAccountData!!.name
                    accountBox.put(currentAccount)
                    helpViewModel.selectedAccountData = currentAccount
                }
                if (currentAccount.isStalker) {
                    if (currentAccount.stalkerUrl != helpViewModel.selectedAccountData!!.stalkerUrl ||
                        currentAccount.username != helpViewModel.selectedAccountData!!.username ||
                        currentAccount.macAddress != helpViewModel.selectedAccountData!!.macAddress
                    ) {
                        currentAccount.stalkerUrl = helpViewModel.selectedAccountData!!.stalkerUrl
                        currentAccount.username = helpViewModel.selectedAccountData!!.username
                        currentAccount.macAddress = helpViewModel.selectedAccountData!!.macAddress
                        accountBox.put(currentAccount)
                        helpViewModel.setWorker(currentAccount)
                    }
                    helpViewModel.wasPlaylistChanged = false
                    helpViewModel.selectedAccountData = currentAccount
                    parentFragmentManager.popBackStack()
                } else if (currentAccount.isXtream) {
                    if (currentAccount.stalkerUrl != helpViewModel.selectedAccountData!!.stalkerUrl ||
                        currentAccount.username != helpViewModel.selectedAccountData!!.username ||
                        currentAccount.macAddress != helpViewModel.selectedAccountData!!.macAddress
                    ) {
                        currentAccount.stalkerUrl = helpViewModel.selectedAccountData!!.stalkerUrl
                        currentAccount.username = helpViewModel.selectedAccountData!!.username
                        currentAccount.macAddress = helpViewModel.selectedAccountData!!.macAddress
                        accountBox.put(currentAccount)
                        helpViewModel.setWorker(currentAccount)
                    }
                    helpViewModel.wasPlaylistChanged = false
                    helpViewModel.selectedAccountData = currentAccount
                    parentFragmentManager.popBackStack()
                }
            }
        }
    }

    fun convertUnixTimestampToDateTime(unixTimestamp: Long): String {
        val date = Date(unixTimestamp * 1000) // Unix-Timestamp in Millisekunden umwandeln
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) // Format festlegen
        return dateFormat.format(date) // Datum in String umwandeln
    }

    private fun changeToModifyFragment(datatomodify: Int) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.settings_container, EditPlaylistDataFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        helpViewModel.dataToModifyPlaylist = datatomodify
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}