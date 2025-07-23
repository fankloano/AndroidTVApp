package com.example.mj_player_tv.ui.settings

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvCategoryOB_
import com.example.mj_player_tv.databinding.FragmentAddchannelscategoryBinding
import com.example.mj_player_tv.databinding.FragmentManageTvcategoryBinding
import com.example.mj_player_tv.ui.adapter.AddChannelCategoryAdapter
import com.example.mj_player_tv.ui.adapter.AddChannelPlaylistAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@UnstableApi
class AddChannelsToUserCategoryCategoriesFragment: Fragment(R.layout.fragment_addchannelscategory) {

    private var _binding: FragmentAddchannelscategoryBinding? = null

    private val binding get() = _binding!!

    private lateinit var manageTvCategoryAdapter: AddChannelCategoryAdapter

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
        _binding = FragmentAddchannelscategoryBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            helpViewModel.addChannelsToUserCategoryFromCategory = null
            helpViewModel.addChannelsToUserCategoryAccount = false
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareRecyclerView()


        val currentAccount = helpViewModel.addChannelsToUserCategoryFromAccount ?: helpViewModel.currentFocusedTvAccount

        if (currentAccount != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val tvCategories =
                    currentAccount.tvcategories.filter { it.favorite && !it.isFavoriteCategory && !it.userCategory }

                if (tvCategories.isNotEmpty()) {
                    manageTvCategoryAdapter.submitList(tvCategories.sortedBy { it.number })
                    binding.rvLayoutTvCategory.requestFocus()
                }
            }

            binding.btnSelectAll.setOnClickListener {
                showOnlySelectedCat = false
                viewLifecycleOwner.lifecycleScope.launch {
                    val query = withContext(Dispatchers.IO) {
                        tvCatBox.query(
                            TvCategoryOB_.playlistId.equal(currentAccount.id)
                                .and(TvCategoryOB_.isFavoriteCategory.notEqual(true))
                                .and(TvCategoryOB_.userCategory.equal(false))
                        ).build()
                    }
                    val tvCategories = query.find()
                    query.close()
                    if (tvCategories.isNotEmpty()) {
                        manageTvCategoryAdapter.submitList(tvCategories.sortedBy { it.number })
                    }
                }
            }

            binding.btnDeselectAll.setOnClickListener {
                showOnlySelectedCat = true
                viewLifecycleOwner.lifecycleScope.launch {
                    val query = withContext(Dispatchers.IO) {
                        tvCatBox.query(
                            TvCategoryOB_.playlistId.equal(currentAccount.id)
                                .and(TvCategoryOB_.favorite.equal(true))
                                .and(TvCategoryOB_.idByAccountData.notEqual("FAVORITE_${currentAccount.id}"))
                                .and(TvCategoryOB_.userCategory.equal(false))
                        ).build()

                    }
                    val tvCategories = query.find()
                    query.close()
                    if (tvCategories.isNotEmpty()) {
                        manageTvCategoryAdapter.submitList(tvCategories.sortedBy { it.number })
                    }
                }
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
        } else {
            Toast.makeText(this@AddChannelsToUserCategoryCategoriesFragment.requireActivity(), "No Categories found", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    private fun prepareRecyclerView() {
        manageTvCategoryAdapter = AddChannelCategoryAdapter(listener)
        binding.rvLayoutTvCategory.apply {
            adapter = manageTvCategoryAdapter
            setFocusOutSideAllowed(false, false)
            setFocusOutAllowed(true, false)
        }
    }

    val listener = AddChannelCategoryAdapter.OnClickListener{
        helpViewModel.addChannelsToUserCategoryFromCategory = it
        changeFragment(AddChannelsToUserCategoryChannelsFragment())
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