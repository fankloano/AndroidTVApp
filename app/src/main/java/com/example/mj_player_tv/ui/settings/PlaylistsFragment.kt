package com.example.mj_player_tv.ui.settings

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.Accounts_
import com.example.mj_player_tv.database.entity.Settings
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistsBinding
import com.example.mj_player_tv.ui.adapter.AccountDataAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import kotlinx.coroutines.launch

@UnstableApi
class PlaylistsFragment : Fragment(R.layout.fragment_playlists), View.OnFocusChangeListener {

    private var _binding: FragmentPlaylistsBinding? = null

    private val binding get() = _binding!!

    private var accountDataAdapter: AccountDataAdapter? = null

    val accountBox = ObjectBox.store.boxFor(Accounts::class.java)

    val settingsBox = ObjectBox.store.boxFor(Settings::class.java)

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
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            helpViewModel.lastSelectedPlaylistSettingId = null
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareRecyclerview()
        // Hier kannst du deinen Adapter mit Daten füllen oder aktualisieren

        binding.relLayoutAddplBtn.onFocusChangeListener = this
        binding.rvLayoutAccounts.onFocusChangeListener = this
        binding.relLayoutAddplBtn.onFocusChangeListener = this

        val settings = helpViewModel.settings
        val accountsQuery = accountBox.query(Accounts_.isUserCategories.equal(false)).build()
        val accounts = accountsQuery.find()
        accountsQuery.close()

        binding.relLayoutSortBtn.text = if (settings?.playlistSorting == 0) {
            "Sorted by: Date added"
        } else if (settings?.playlistSorting == 1) {
            "Sorted by: Name"
        } else {
            "Sorted by: Last Update Date"
        }

        if (accounts.isEmpty()) {
            binding.rvLayoutAccounts.isFocusable = false
            binding.rvLayoutAccounts.isFocusableInTouchMode = false
            binding.relLayoutAddplBtn.nextFocusDownId = R.id.relLayout_addpl_btn
            binding.relLayoutAddplBtn.nextFocusUpId = R.id.relLayout_addpl_btn
            binding.relLayoutAddplBtn.requestFocus()
        } else {
            if (settings != null) {
                binding.rvLayoutAccounts.isFocusable = true
                binding.rvLayoutAccounts.isFocusableInTouchMode = true
                val sortedAccounts = if (settings.playlistSorting == 0) {
                    accounts.sortedBy { it.id }
                } else if (settings.playlistSorting == 1) {
                           accounts.sortedBy { it.name }
                } else {
                    accounts.sortedBy { it.lastUpdatedDate }
                }
                accountDataAdapter?.submitList(sortedAccounts)
                if (helpViewModel.lastSelectedPlaylistSettingId == binding.rvLayoutAccounts.id) {
                    binding.rvLayoutAccounts.post {
                        val account = accountDataAdapter?.currentList?.firstOrNull { it.id == helpViewModel.selectedAccountData?.id }
                        val pos = accountDataAdapter?.currentList?.indexOf(account)
                        Log.d("CLICKED PLAYLIST", "${account?.name} & POS: $pos")
                        if (pos != null) {
                            binding.rvLayoutAccounts.post {
                                binding.rvLayoutAccounts.setSelectedPosition(pos)
                                binding.rvLayoutAccounts.requestFocus()
                            }
                        } else {
                            binding.rvLayoutAccounts.requestFocus()
                        }
                    }
                } else {
                    binding.relLayoutAddplBtn.requestFocus()
                }
            }
        }

        binding.relLayoutSortBtn.setOnClickListener {
            if (settings != null) {
                if (settings.playlistSorting == 0) {
                    settings.playlistSorting = 1
                    binding.relLayoutSortBtn.text = "Sorted by: Name"
                    settingsBox.put(settings)
                    helpViewModel.getSettings()
                    accountDataAdapter?.submitList(accounts.sortedBy { it.name })

                } else if (settings.playlistSorting == 1) {
                    settings.playlistSorting = 2
                    binding.relLayoutSortBtn.text = "Sorted by: Last Update Date"
                    settingsBox.put(settings)
                    helpViewModel.getSettings()
                    accountDataAdapter?.submitList(accounts.sortedBy { it.lastUpdatedDate })
                } else {
                    settings.playlistSorting = 0
                    binding.relLayoutSortBtn.text = "Sorted by: Date added"
                    settingsBox.put(settings)
                    helpViewModel.getSettings()
                    accountDataAdapter?.submitList(accounts.sortedBy { it.id })
                }
            }
        }

        binding.relLayoutAddplBtn.setOnClickListener {
                helpViewModel.lastSelectedPlaylistSettingId = it.id
                // Hier wird aufgerufen, wenn die OK-Taste auf der Fernbedienung gedrückt wird
                changeFragment(AddPlaylistFragment())
        }
    }

    private val onClickListener = AccountDataAdapter.OnClickListener { accountdata ->
        helpViewModel.lastSelectedPlaylistSettingId = binding.rvLayoutAccounts.id
        helpViewModel.selectedAccountData = accountdata
        changeFragment(PlaylistSettingsFragment())
    }


    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.settings_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun prepareRecyclerview() {
        accountDataAdapter = AccountDataAdapter(onClickListener, helpViewModel)
        binding.rvLayoutAccounts.apply {
            adapter = accountDataAdapter
            addItemDecoration(
                DpadLinearSpacingDecoration.create(
                    itemSpacing = 5,
                    edgeSpacing = 5,
                    perpendicularEdgeSpacing = 5
                )
            )
            setFocusOutAllowed(true, false)
            setFocusOutSideAllowed(false, false)
            nextFocusUpId = R.id.relLayout_addpl_btn
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (accountDataAdapter != null) {
            accountDataAdapter?.submitList(null)
            accountDataAdapter = null
        }
        _binding = null
    }

    override fun onFocusChange(v: View?, hasFocus: Boolean) {
    }
}