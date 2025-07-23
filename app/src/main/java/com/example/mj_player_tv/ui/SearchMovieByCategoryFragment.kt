package com.example.mj_player_tv.ui

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager.OnBackStackChangedListener
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.databinding.FragmentSearchMovieBycategoryBinding
import com.example.mj_player_tv.ui.adapter.SearchMoviesAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import com.rubensousa.dpadrecyclerview.FocusableDirection
import com.rubensousa.dpadrecyclerview.spacing.DpadGridSpacingDecoration
import kotlinx.coroutines.launch

@UnstableApi
class SearchMovieByCategoryFragment : Fragment(R.layout.fragment_search_movie_bycategory) {

    private var _binding: FragmentSearchMovieBycategoryBinding? = null

    private lateinit var moviesAdapter: SearchMoviesAdapter

    private val accountBox = ObjectBox.store.boxFor(Accounts::class.java)

    private val binding get() = _binding!!

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
        _binding = FragmentSearchMovieBycategoryBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareMoviesRecyclerView()

        parentFragmentManager.addOnBackStackChangedListener(backStackListener)
        binding.editTextSearch.requestFocus()
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.editTextSearch, InputMethodManager.SHOW_IMPLICIT)


        binding.editTextSearch.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                closeFragment()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                if (moviesAdapter.currentList.isNotEmpty()) {
                    binding.rvLayoutMovies.requestFocus()
                    return@setOnKeyListener true
                } else {
                    binding.editTextSearch.requestFocus()
                }
            }
            false
        }

        binding.editTextSearch.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) {

                // Deine Funktion aufrufen
                searchFor(binding.editTextSearch.text.toString())

                // Virtuelle Tastatur ausblenden
                val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)

                true // Ereignis wurde behandelt
            } else {
                false // Weiterverarbeiten lassen
            }
        }
    }

    private fun searchFor(searchTerm: String) {
        binding.progressBar.visibility = View.VISIBLE
        if (helpViewModel.currentMovieAccount!!.isStalker) {
            resetVisibility()
            viewLifecycleOwner.lifecycleScope.launch {
                val searchMoviesList = stalkerViewModel.searchMoviesByCategory(
                    helpViewModel.currentMovieAccount!!,
                    helpViewModel.currentMovieCategoryOB!!.movieCatId,
                    searchTerm).await()
                binding.progressBar.visibility = View.INVISIBLE
                if (searchMoviesList.isNotEmpty()) {
                    binding.tvNodatafound.visibility = View.INVISIBLE
                    binding.rvLayoutMovies.visibility = View.VISIBLE
                    moviesAdapter.submitList(searchMoviesList.sortedBy { it.movieName })
                    binding.rvLayoutMovies.requestFocus()
                } else {
                    binding.rvLayoutMovies.visibility = View.INVISIBLE
                    binding.tvNodatafound.visibility = View.VISIBLE
                    binding.editTextSearch.requestFocus()
                }
            }
        } else {
            resetVisibility()
            val filteredMoviesList = xtreamViewModel.movieSearchList.filter { it.movieName?.contains(searchTerm, ignoreCase = true) == true }
            binding.progressBar.visibility = View.INVISIBLE
            if (filteredMoviesList.isNotEmpty()) {
                binding.tvNodatafound.visibility = View.INVISIBLE
                binding.rvLayoutMovies.visibility = View.VISIBLE
                moviesAdapter.submitList(filteredMoviesList.sortedBy { it.movieName })
                binding.rvLayoutMovies.requestFocus()
            } else {
                binding.rvLayoutMovies.visibility = View.INVISIBLE
                binding.tvNodatafound.visibility = View.VISIBLE
                binding.editTextSearch.requestFocus()
            }
        }
    }

    private fun resetVisibility() {
        moviesAdapter.submitList(null)
    }

    private fun prepareMoviesRecyclerView() {
        moviesAdapter = SearchMoviesAdapter(onMovieClickListener,this, helpViewModel)
        binding.rvLayoutMovies.apply {
            adapter = moviesAdapter
            addItemDecoration(
                DpadGridSpacingDecoration.create(
                    itemSpacing = 16,
                    edgeSpacing = 7,
                    perpendicularItemSpacing = 14
                )
            )
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private val onMovieClickListener = SearchMoviesAdapter.OnClickListener { movie ->
        val account = movie.accountId?.let {
            accountBox.get(it)
        }
        if (account != null) {
            if (account.isXtream) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val xtreamMovie = xtreamViewModel.getXtreamMovieDetails(movie, account)
                    helpViewModel.currentFocusedMovie = xtreamMovie
                    openMovieDetailFragment()
                }
            } else {
                helpViewModel.currentFocusedMovie = movie
                openMovieDetailFragment()
            }
        }
    }

    fun focusToSearchText() {
        binding.editTextSearch.requestFocus()
    }

    fun openMovieDetailFragment() {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.add(R.id.container_movie_info, MovieDetailFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        helpViewModel.isSearchContainerOpened = true
    }

    fun closeFragment() {
        val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (mainFragment is MoviesFragment) {
            mainFragment.setFocusToMovies()
        }
        xtreamViewModel.movieSearchList = mutableListOf()
        parentFragmentManager.popBackStack()
    }

    private val backStackListener = OnBackStackChangedListener {
        if (parentFragmentManager.fragments.lastOrNull() == this) {
            // Das Fragment ist wieder sichtbar!
            if (helpViewModel.isSearchContainerOpened) {
                helpViewModel.isSearchContainerOpened = false
                binding.rvLayoutMovies.requestFocus()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        parentFragmentManager.removeOnBackStackChangedListener(backStackListener)
        _binding = null
    }
}