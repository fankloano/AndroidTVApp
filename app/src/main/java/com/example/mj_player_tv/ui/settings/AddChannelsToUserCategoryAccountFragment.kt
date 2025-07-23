package com.example.mj_player_tv.ui.settings

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.Accounts_
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvCategoryOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.entity.TvChannelOB_
import com.example.mj_player_tv.databinding.FragmentAddchannelsplaylistBinding
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentManagePlaylistBinding
import com.example.mj_player_tv.databinding.FragmentManageTvcategoryBinding
import com.example.mj_player_tv.databinding.FragmentPlaylistsBinding
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.ui.adapter.AddChannelPlaylistAdapter
import com.example.mj_player_tv.ui.adapter.ManageTvCategoryAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import io.objectbox.kotlin.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@UnstableApi
class AddChannelsToUserCategoryAccountFragment: Fragment(R.layout.fragment_addchannelsplaylist) {

    private var _binding: FragmentAddchannelsplaylistBinding? = null

    private val binding get() = _binding!!
    private lateinit var addChannelPlaylistAdapter: AddChannelPlaylistAdapter

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    private var showOnlySelectedCat = true

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
        _binding = FragmentAddchannelsplaylistBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        prepareRecyclerView()

        val accountsQuery = accountBox.query(Accounts_.isUserCategories.equal(false)).build()
        val accounts = accountsQuery.find()
        accountsQuery.close()
        if (accounts.isNotEmpty()) {
            addChannelPlaylistAdapter.submitList(accounts)
            binding.rvLayoutAccounts.requestFocus()
        } else {
            Toast.makeText(this@AddChannelsToUserCategoryAccountFragment.requireActivity(), "No activated accounts found!", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }

            view.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    // Hier fügst du die Logik für die Zurück-Navigation im settings_container hinzu
                    // Zum Beispiel:
                    val fragmentManager = parentFragmentManager
                    if (fragmentManager.backStackEntryCount > 0) {
                        fragmentManager.popBackStack()
                    } else {
                        // Wenn es keine vorherigen Einträge gibt, kannst du das Fragment schließen
                        // oder andere Aktionen durchführen.
                    }
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
    }

    private fun prepareRecyclerView() {
        addChannelPlaylistAdapter = AddChannelPlaylistAdapter(listener)
        binding.rvLayoutAccounts.apply {
            adapter = addChannelPlaylistAdapter
            setFocusOutSideAllowed(false, false)
            setFocusOutAllowed(true, false)
        }
    }

    val listener = AddChannelPlaylistAdapter.OnClickListener{
        helpViewModel.addChannelsToUserCategoryAccount = true
        helpViewModel.addChannelsToUserCategoryFromAccount = it
        changeFragment(AddChannelsToUserCategoryCategoriesFragment())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_AssignChannelToEpg, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }
}