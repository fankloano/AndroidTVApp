package com.example.mj_player_tv.ui.settings


import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.Accounts_
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.ChannelPositions_
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.EpgSourcePositions
import com.example.mj_player_tv.database.entity.EpgSourcePositions_
import com.example.mj_player_tv.database.entity.EpgSource_
import com.example.mj_player_tv.database.entity.MovieCategoryOB
import com.example.mj_player_tv.database.entity.MovieCategoryOB_
import com.example.mj_player_tv.database.entity.SeriesCategoryOB
import com.example.mj_player_tv.database.entity.SeriesCategoryOB_
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvCategoryOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.entity.TvChannelOB_
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistSettingsBinding
import com.example.mj_player_tv.repository.PlaylistUpdateProcessState
import com.example.mj_player_tv.ui.ManageCategoriesFragment
import com.example.mj_player_tv.ui.PlaylistInfoFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.PlaylistUpdateViewModel
import com.example.mj_player_tv.viewmodel.PlaylistUpdateViewModelFactory
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


@UnstableApi
class PlaylistSettingsFragment : Fragment(R.layout.fragment_playlist_settings), View.OnFocusChangeListener {

    private var _binding: FragmentPlaylistSettingsBinding? = null

    private val binding get() = _binding!!

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

    private val playlistUpdateViewModel: PlaylistUpdateViewModel by activityViewModels {
        PlaylistUpdateViewModelFactory(
            requireActivity().application
        )
    }

    val accountBox = ObjectBox.store.boxFor(Accounts::class.java)
    val epgSourceBox = ObjectBox.store.boxFor(EpgSource::class.java)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistSettingsBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            helpViewModel.selectedAccountData = null
            helpViewModel.lastSelectedPlaylistOptionsId = null
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        helpViewModel.lastSelectedPlaylistOptionsId?.let { view.findViewById<RelativeLayout>(it).requestFocus() }
            ?: binding.relLayoutActive.requestFocus()

        setupListeners()

        val accountData = accountBox.get(helpViewModel.selectedAccountData!!.id)

        if (accountData != null) {
            if (accountData.isPlex) {
                binding.relLayoutGroups.visibility = View.GONE
                binding.relLayoutEpg.visibility = View.GONE
                binding.relLayoutLogos.visibility = View.GONE
                binding.relLayoutChannelOrder.visibility = View.GONE
                binding.relLayoutShowtv.visibility = View.GONE
                binding.relLayoutShowvod.visibility = View.GONE
            }
            binding.titleSetting.text = accountData.name
            binding.tvEpgSources.text = "${accountData.epgsources.filter { it.isSelected }.count()} EPG-sources"
            binding.switchUpdateonappstart.isChecked = accountData.updateOnAppStart
            binding.switchActive.isChecked = accountData.isSelected
            binding.switchShowTv.isChecked = accountData.showTv
            binding.switchShowvod.isChecked = accountData.showVod
            if (accountData.autoUpdateHours.toString() == "168") {
                binding.tvIsautoUpdatePlaylist.text = "7 days"
            } else if (accountData.autoUpdateHours.toString() == "144") {
                binding.tvIsautoUpdatePlaylist.text = "6 days"
            } else if (accountData.autoUpdateHours.toString() == "120") {
                binding.tvIsautoUpdatePlaylist.text = "5 days"
            } else if (accountData.autoUpdateHours.toString() == "96") {
                binding.tvIsautoUpdatePlaylist.text = "4 days"
            } else if (accountData.autoUpdateHours.toString() == "72") {
                binding.tvIsautoUpdatePlaylist.text = "3 days"
            } else if (accountData.autoUpdateHours.toString() == "48") {
                binding.tvIsautoUpdatePlaylist.text = "2 days"
            } else if (accountData.autoUpdateHours.toString() == "0") {
                binding.tvIsautoUpdatePlaylist.text = "${accountData.autoUpdateHours}h (Off)"
            } else {
                binding.tvIsautoUpdatePlaylist.text = "${accountData.autoUpdateHours} hours"
            }
            binding.tvIsChannelOrder.text = if (accountData.orderBy == 0) {
                "Sort by Playlist"
            } else if (accountData.orderBy == 1) {
                "Sort by Name"
            } else {
                "Sort manually"
            }

            binding.tvIsLogoPreference.text = if (accountData.useEpgLogos) {
                "Prefer EPG Logos"
            } else {
                "Prefer Playlist Logos"
            }
        }


        binding.relLayoutActive.setOnClickListener {
            if (accountData != null) {
                                // Aktualisiere den Zustand der Schaltfläche
                binding.switchActive.isChecked = !binding.switchActive.isChecked

                accountData.isSelected = binding.switchActive.isChecked

                // Speichere die Änderungen im Datenbank und aktualisiere ViewModel
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        accountBox.put(accountData)
                    }
                }

                helpViewModel.isPlaylistEnableChanged = true

                // Umgekehrten Wert von isSelected setzen
                helpViewModel.selectedAccountData?.isSelected = accountData.isSelected
            }
        }


        binding.relLayoutActive.setOnFocusChangeListener { _, hasFocus ->
            binding.tvActive.isSelected = hasFocus
            binding.switchActive.isActivated = hasFocus
        }

        binding.switchActive.setOnCheckedChangeListener { _, isChecked ->

            val account = accountBox.query(Accounts_.id.equal(helpViewModel.selectedAccountData!!.id)).build().findFirst()
            if (account != null) {
                account.isSelected = isChecked
                accountBox.put(account)
                helpViewModel.selectedAccountData = account
            }
        }

        binding.relLayoutEpg.setOnClickListener {
            helpViewModel.lastSelectedPlaylistOptionsId = it.id
            changeFragment(PlaylistEpgSettingsFragment())
        }

        binding.relLayoutEpg.setOnFocusChangeListener { _, hasFocus ->
            binding.tvEpgSources.isSelected = hasFocus
            binding.tvManageEpg.isSelected = hasFocus
        }

        binding.relLayoutLogos.setOnClickListener {
            helpViewModel.isLogoSettingsOpened = true
            helpViewModel.lastSelectedPlaylistOptionsId = it.id
            changeFragment(PlaylistLogosFragment())
        }

        binding.relLayoutLogos.setOnFocusChangeListener { _, hasFocus ->
            binding.tvLogos.isSelected = hasFocus
            binding.tvIsLogoPreference.isSelected = hasFocus
        }

        binding.relLayoutGroups.setOnClickListener {
            helpViewModel.lastSelectedPlaylistOptionsId = it.id
            helpViewModel.isCategoryManagementOpened = true
            changeFragment(ManageCategoriesFragment())
        }

        binding.relLayoutGroups.setOnFocusChangeListener { _, hasFocus ->
            binding.tvManageGroups.isSelected = hasFocus
        }

        binding.relLayoutShowtv.setOnFocusChangeListener { _, hasFocus ->
            binding.tvShowtv.isSelected = hasFocus
        }

        binding.relLayoutShowtv.setOnClickListener {
            if (accountData != null) {
                helpViewModel.lastSelectedPlaylistOptionsId = it.id
                binding.switchShowTv.isChecked = !binding.switchShowTv.isChecked
                accountData.showTv = binding.switchShowTv.isChecked

                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        accountBox.put(accountData)
                    }
                }

                helpViewModel.isPlaylistEnableChanged = true

                helpViewModel.selectedAccountData?.showTv = accountData.showTv
                helpViewModel.updateTvAccountsCompleteSuccessful()
            }
        }

        binding.relLayoutShowvod.setOnFocusChangeListener { _, hasFocus ->
            binding.tvShowvod.isSelected = hasFocus
        }

        binding.relLayoutShowvod.setOnClickListener {
            if (accountData != null) {
                helpViewModel.lastSelectedPlaylistOptionsId = it.id
                binding.switchShowvod.isChecked = !binding.switchShowvod.isChecked
                accountData.showVod = binding.switchShowvod.isChecked

                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        accountBox.put(accountData)
                    }
                }

                helpViewModel.isPlaylistEnableChanged = true

                helpViewModel.selectedAccountData?.showVod = accountData.showVod
                helpViewModel.updateTvAccountsCompleteSuccessful()
            }
        }

        binding.relLayoutDelete.setOnClickListener {
            helpViewModel.lastSelectedPlaylistOptionsId = it.id
            val alertDialogBuilder = AlertDialog.Builder(requireContext(),R.style.CustomAlertDialog)

            alertDialogBuilder.setMessage("Delete Playlist?")

            alertDialogBuilder.setPositiveButton("Yes") { _, _ ->
                deletePlaylist(accountData)
            }

            alertDialogBuilder.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }

            val alertDialog = alertDialogBuilder.create()
            alertDialog.setOnShowListener {
                val negative = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                negative.requestFocus()
            }
            alertDialog.show()
        }

        binding.relLayoutDelete.setOnFocusChangeListener { _, hasFocus ->
            binding.tvDeletePlaylist.isSelected = hasFocus
        }

        binding.relLayoutInfo.setOnFocusChangeListener { _, hasFocus ->
            binding.tvPlaylistInfo.isSelected = hasFocus
        }

        binding.relLayoutInfo.setOnClickListener {
            helpViewModel.lastSelectedPlaylistOptionsId = it.id
            changeFragment(PlaylistInfoFragment())
        }

        binding.relLayoutAutoUpdate.setOnFocusChangeListener { _, hasFocus ->
            binding.tvAutoUpdatePlaylist.isSelected = hasFocus
            binding.tvIsautoUpdatePlaylist.isSelected = hasFocus
        }

        binding.relLayoutAutoUpdate.setOnClickListener {
            helpViewModel.lastSelectedPlaylistOptionsId = it.id
            helpViewModel.changeAutoUpdateInterval = 0
            changeFragment(AutoUpdateIntervalFragment())
        }

        binding.relLayoutUpdateonappstart.setOnFocusChangeListener { _, hasFocus ->
            binding.tvUpdateonappstart.isSelected = hasFocus
        }

        binding.relLayoutUpdateonappstart.setOnClickListener {
            if (accountData != null) {
                helpViewModel.lastSelectedPlaylistOptionsId = it.id
                binding.switchUpdateonappstart.isChecked = !binding.switchUpdateonappstart.isChecked
                accountData.updateOnAppStart = binding.switchUpdateonappstart.isChecked

                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        accountBox.put(accountData)
                    }
                }
                helpViewModel.isPlaylistEnableChanged = true

                helpViewModel.selectedAccountData?.updateOnAppStart = accountData.updateOnAppStart
                helpViewModel.updateTvAccountsCompleteSuccessful()
            }
        }

        binding.relLayoutUpdate.setOnFocusChangeListener { _, hasFocus ->
            binding.tvUpdatePlaylist.isSelected = hasFocus
            binding.tvLastUpdatePlaylist.isSelected = hasFocus
        }

        binding.relLayoutUpdate.setOnClickListener {
            if (accountData != null) {
                helpViewModel.setWorker(accountData)
            }
        }

        binding.relLayoutChannelOrder.setOnFocusChangeListener { _, hasFocus ->
            binding.tvOrder.isSelected = hasFocus
            binding.tvIsChannelOrder.isSelected = hasFocus
        }

        binding.relLayoutChannelOrder.setOnClickListener {
            helpViewModel.lastSelectedPlaylistOptionsId = it.id
            changeFragment(PlaylistChannelSortFragment())
        }

        playlistUpdateViewModel.playlistUpdateState.observe(viewLifecycleOwner) { updateProcessState ->
            val thisAccountUpdating = updateProcessState.firstOrNull { it?.playlistId == accountData.id }
            if (thisAccountUpdating != null) {
                binding.loadingBalken.visibility = View.VISIBLE
                binding.tvLastUpdatePlaylist.visibility = View.INVISIBLE
                binding.tvUpdatePlaylist.text = "Updating Playlist..."
                binding.relLayoutUpdate.isClickable = false
            } else {
                binding.loadingBalken.visibility = View.GONE
                binding.tvUpdatePlaylist.text = "Update Playlist"
                binding.relLayoutUpdate.isClickable = true
                binding.tvLastUpdatePlaylist.visibility = View.VISIBLE
                binding.tvLastUpdatePlaylist.text = if (accountData.lastUpdateStatus == 0) {
                    "${convertUnixTimestampToDateTime(accountData.lastUpdatedDate)} -> Failed!"
                } else if (accountData.lastUpdateStatus == 1) {
                    "${convertUnixTimestampToDateTime(accountData.lastUpdatedDate)} -> Successful!"
                } else {
                    "${convertUnixTimestampToDateTime(accountData.lastUpdatedDate)} -> Incomplete, check playlist info!"
                }
            }
        }

    }


    fun convertUnixTimestampToDateTime(unixTimestamp: Long): String {
        val date = Date(unixTimestamp * 1000) // Unix-Timestamp in Millisekunden umwandeln
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) // Format festlegen
        return dateFormat.format(date) // Datum in String umwandeln
    }


    private fun setupListeners() {
        binding.relLayoutActive.onFocusChangeListener = this
        binding.relLayoutInfo.onFocusChangeListener = this
        binding.relLayoutGroups.onFocusChangeListener = this
        binding.relLayoutEpg.onFocusChangeListener = this
        binding.relLayoutDelete.onFocusChangeListener = this
        binding.relLayoutShowtv.onFocusChangeListener = this
        binding.relLayoutShowvod.onFocusChangeListener = this
    }


    override fun onFocusChange(view: View?, hasFocus: Boolean) {
        if (view != null) {
            // Hier wird aufgerufen, wenn sich der Fokus auf einem Menüpunkt ändert
            if (hasFocus) {
            }
        }
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.settings_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }


    private fun deletePlaylist(accountData: Accounts) {
        accountData.isUserCategories = true
        accountBox.put(accountData)
        helpViewModel.deletePlaylist(accountData)
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}