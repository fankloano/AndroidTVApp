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
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.MovieCategoryOB
import com.example.mj_player_tv.database.entity.MovieCategoryOB_
import com.example.mj_player_tv.database.entity.SeriesCategoryOB
import com.example.mj_player_tv.database.entity.SeriesCategoryOB_
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvCategoryOB_
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentManageMoviecategoryBinding
import com.example.mj_player_tv.databinding.FragmentManageSeriescategoryBinding
import com.example.mj_player_tv.databinding.FragmentManageTvcategoryBinding
import com.example.mj_player_tv.ui.adapter.ManageMovieCategoryAdapter
import com.example.mj_player_tv.ui.adapter.ManageSeriesCategoryAdapter
import com.example.mj_player_tv.ui.adapter.ManageTvCategoryAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.launch

class ManageSeriesCategoryFragment: Fragment(R.layout.fragment_manage_seriescategory), View.OnFocusChangeListener {

    private var _binding: FragmentManageSeriescategoryBinding? = null

    private val binding get() = _binding!!

    private lateinit var manageSeriesCategoryAdapter: ManageSeriesCategoryAdapter

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val seriesCatBox: Box<SeriesCategoryOB> = ObjectBox.store.boxFor(SeriesCategoryOB::class.java)

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
        _binding = FragmentManageSeriescategoryBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvLayoutSeriesCategory.onFocusChangeListener = this
        binding.btnDeselectAll.onFocusChangeListener = this
        binding.btnSelectAll.onFocusChangeListener = this

        prepareRecyclerView()
        binding.btnSelectAll.requestFocus()

        val currentAccount = helpViewModel.selectedAccountData
        if (currentAccount != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val movieCategories = seriesCatBox.query(SeriesCategoryOB_.playlistId.equal(currentAccount.id)).build().find()
                if (movieCategories.isNotEmpty()) {
                    manageSeriesCategoryAdapter.submitList(movieCategories)
                } else {
                    Toast.makeText(this@ManageSeriesCategoryFragment.requireActivity(), "No Series Categories found!", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
            }

            binding.btnSelectAll.setOnClickListener {
                val movieCategories = seriesCatBox.query(SeriesCategoryOB_.playlistId.equal(currentAccount.id)).build().find()
                movieCategories.forEach {
                    it.favorite = true
                    seriesCatBox.put(it)
                }
                manageSeriesCategoryAdapter.currentList.forEach {
                    it.favorite = true
                }
                manageSeriesCategoryAdapter.notifyDataSetChanged()
            }

            binding.btnDeselectAll.setOnClickListener {
                val movieCategories = seriesCatBox.query(SeriesCategoryOB_.playlistId.equal(currentAccount.id)).build().find()
                movieCategories.forEach {
                    it.favorite = false
                    seriesCatBox.put(it)
                }
                manageSeriesCategoryAdapter.currentList.forEach {
                    it.favorite = false
                }
                manageSeriesCategoryAdapter.notifyDataSetChanged()
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
        manageSeriesCategoryAdapter = ManageSeriesCategoryAdapter(listener)
        binding.rvLayoutSeriesCategory.apply {
            adapter = manageSeriesCategoryAdapter
        }
    }

    val listener = ManageSeriesCategoryAdapter.OnClickListener{
        seriesCatBox.put(it)
        val position = manageSeriesCategoryAdapter.currentList.indexOf(it)
        manageSeriesCategoryAdapter.notifyItemChanged(position)
        helpViewModel.updateSeriesCategoriesCompleteSuccessful()
        binding.rvLayoutSeriesCategory.requestFocus()
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