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
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvCategoryOB_
import com.example.mj_player_tv.databinding.FragmentAddplaylistBinding
import com.example.mj_player_tv.databinding.FragmentManageMoviecategoryBinding
import com.example.mj_player_tv.databinding.FragmentManageTvcategoryBinding
import com.example.mj_player_tv.ui.adapter.ManageMovieCategoryAdapter
import com.example.mj_player_tv.ui.adapter.ManageTvCategoryAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.launch

class ManageMovieCategoryFragment: Fragment(R.layout.fragment_manage_moviecategory), View.OnFocusChangeListener {

    private var _binding: FragmentManageMoviecategoryBinding? = null

    private val binding get() = _binding!!

    private lateinit var manageMovieCategoryAdapter: ManageMovieCategoryAdapter

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val movieCatBox: Box<MovieCategoryOB> = ObjectBox.store.boxFor(MovieCategoryOB::class.java)

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
        _binding = FragmentManageMoviecategoryBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvLayoutMovieCategory.onFocusChangeListener = this
        binding.btnDeselectAll.onFocusChangeListener = this
        binding.btnSelectAll.onFocusChangeListener = this

        prepareRecyclerView()
        binding.btnSelectAll.requestFocus()

        val currentAccount = helpViewModel.selectedAccountData
        if (currentAccount != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val movieCategories = movieCatBox.query(MovieCategoryOB_.playlistId.equal(currentAccount.id)).build().find()
                if (movieCategories.isNotEmpty()) {
                    manageMovieCategoryAdapter.submitList(movieCategories)
                } else {
                    Toast.makeText(this@ManageMovieCategoryFragment.requireActivity(), "No Movie Categories found!", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
            }

            binding.btnSelectAll.setOnClickListener {
                val movieCategories = movieCatBox.query(MovieCategoryOB_.playlistId.equal(currentAccount.id)).build().find()
                movieCategories.forEach {
                    it.favorite = true
                    movieCatBox.put(it)
                }
                manageMovieCategoryAdapter.currentList.forEach {
                    it.favorite = true
                }
                manageMovieCategoryAdapter.notifyDataSetChanged()
            }

            binding.btnDeselectAll.setOnClickListener {
                val movieCategories = movieCatBox.query(MovieCategoryOB_.playlistId.equal(currentAccount.id)).build().find()
                movieCategories.forEach {
                    it.favorite = false
                    movieCatBox.put(it)
                }
                manageMovieCategoryAdapter.currentList.forEach {
                    it.favorite = false
                }
                manageMovieCategoryAdapter.notifyDataSetChanged()
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
        manageMovieCategoryAdapter = ManageMovieCategoryAdapter(listener)
        binding.rvLayoutMovieCategory.apply {
            adapter = manageMovieCategoryAdapter
        }
    }

    val listener = ManageMovieCategoryAdapter.OnClickListener{
        movieCatBox.put(it)
        val position = manageMovieCategoryAdapter.currentList.indexOf(it)
        manageMovieCategoryAdapter.notifyItemChanged(position)
        helpViewModel.updateMovieCategoriesCompleteSuccessful()
        binding.rvLayoutMovieCategory.requestFocus()
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