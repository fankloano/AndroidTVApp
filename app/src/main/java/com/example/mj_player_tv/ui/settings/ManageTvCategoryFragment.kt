package com.example.mj_player_tv.ui.settings

import android.os.Bundle
import android.view.KeyEvent
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
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvCategoryOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.entity.TvChannelOB_
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentManageTvcategoryBinding
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.ui.adapter.ManageTvCategoryAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@UnstableApi
class ManageTvCategoryFragment: Fragment(R.layout.fragment_manage_tvcategory), View.OnFocusChangeListener {

    private var _binding: FragmentManageTvcategoryBinding? = null

    private val binding get() = _binding!!

    private lateinit var manageTvCategoryAdapter: ManageTvCategoryAdapter

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    private val tvChannBox: Box<TvChannelOB> = ObjectBox.store.boxFor(TvChannelOB::class.java)

    private var isFirstLoad = true

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
        _binding = FragmentManageTvcategoryBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            helpViewModel.selectedAccountData?.let {
                helpViewModel.viewModelScope.launch {
                    helpViewModel.matchChannelsAndEpg(it)
                }
            }
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvLayoutTvCategory.onFocusChangeListener = this
        binding.btnDeselectAll.onFocusChangeListener = this
        binding.btnSelectAll.onFocusChangeListener = this

        prepareRecyclerView()
        binding.btnSelectAll.requestFocus()

        if (helpViewModel.selectedAccountData != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                helpViewModel.selectedAccountData?.let { account ->
                    val categoriesQuery = tvCatBox.query(TvCategoryOB_.playlistId.equal(account.id)).build()
                    val categories = categoriesQuery.find()
                    categoriesQuery.close()
                    if (categories.isNotEmpty()) {
                        manageTvCategoryAdapter.submitList(
                            categories.sortedWith(
                                compareBy<TvCategoryOB> { it.userCategory } // Erst nach `userCategory` (false zuerst)
                                    .thenBy { it.number } // Dann nach `number`
                            )
                        )
                    } else {
                        Toast.makeText(this@ManageTvCategoryFragment.requireActivity(), "No Tv Categories found!", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    }
                }
        }

            binding.btnSelectAll.setOnClickListener {
                helpViewModel.selectedAccountData?.let { account ->
                    val categoriesQuery = tvCatBox.query(TvCategoryOB_.playlistId.equal(account.id)).build()
                    categoriesQuery.find().forEach {
                        it.favorite = true
                        tvCatBox.put(it)
                    }
                    categoriesQuery.close()
                    manageTvCategoryAdapter.currentList.forEach {
                        it.favorite = true
                    }
                    account.tvcategories.reset()
                    manageTvCategoryAdapter.notifyDataSetChanged()
                    helpViewModel.updateTvCategoriesCompleteSuccessful()
                }
            }

            binding.btnDeselectAll.setOnClickListener {
                helpViewModel.selectedAccountData?.let { account ->
                    val categoriesQuery = tvCatBox.query(TvCategoryOB_.playlistId.equal(account.id)).build()
                    categoriesQuery.find().forEach {
                        it.favorite = false
                        tvCatBox.put(it)
                    }
                    categoriesQuery.close()
                    manageTvCategoryAdapter.currentList.forEach {
                        it.favorite = false
                    }
                    account.tvcategories.reset()
                    manageTvCategoryAdapter.notifyDataSetChanged()
                    helpViewModel.updateTvCategoriesCompleteSuccessful()
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

    }

    private fun prepareRecyclerView() {
        manageTvCategoryAdapter = ManageTvCategoryAdapter(listener, helpViewModel)
        binding.rvLayoutTvCategory.apply {
            adapter = manageTvCategoryAdapter
        }
        manageTvCategoryAdapter.addChannelsToUserCategory = false
    }

    val listener = ManageTvCategoryAdapter.OnClickListener {
        tvCatBox.put(it)
        val position = manageTvCategoryAdapter.currentList.indexOf(it)
        manageTvCategoryAdapter.notifyItemChanged(position)
        helpViewModel.changedTvCategoriesAccountId = it.playlistId
        helpViewModel.updateTvCategoriesCompleteSuccessful()
        helpViewModel.selectedAccountData?.let { account ->
            account.tvcategories.reset()
            accountBox.put(account) }
        binding.rvLayoutTvCategory.requestFocus()
    }

    override fun onFocusChange(p0: View?, hasFocus: Boolean) {
        // Hier wird aufgerufen, wenn sich der Fokus auf einem Menüpunkt ändert
        if (hasFocus) {
            // Aktualisiere die visuelle Hervorhebung basierend auf dem aktuellen Fokus
            if (view != null) {
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}