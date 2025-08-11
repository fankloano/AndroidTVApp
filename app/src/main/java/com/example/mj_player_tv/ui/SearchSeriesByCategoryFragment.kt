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
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.SeasonsOB
import com.example.mj_player_tv.databinding.FragmentSearchSeriesBycategoryBinding
import com.example.mj_player_tv.ui.adapter.SearchSeriesAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.SeriesViewModel
import com.example.mj_player_tv.viewmodel.SeriesViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import com.rubensousa.dpadrecyclerview.FocusableDirection
import com.rubensousa.dpadrecyclerview.spacing.DpadGridSpacingDecoration
import kotlinx.coroutines.launch

@UnstableApi
class SearchSeriesByCategoryFragment : Fragment(R.layout.fragment_search_series_bycategory) {

    private var _binding: FragmentSearchSeriesBycategoryBinding? = null

    private lateinit var seriesAdapter: SearchSeriesAdapter

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

    private val seriesViewModel: SeriesViewModel by activityViewModels {
        SeriesViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchSeriesBycategoryBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareSeriesRecyclerView()

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
        if (helpViewModel.currentSeriesAccount!!.isStalker) {
            resetVisibility()
            viewLifecycleOwner.lifecycleScope.launch {
                val searchSeriesList = stalkerViewModel.searchSeriesByCategory(
                    helpViewModel.currentSeriesAccount!!,
                    helpViewModel.currentSeriesCategoryOB!!.seriesCatId,
                    searchTerm).await()
                binding.progressBar.visibility = View.INVISIBLE
                if (searchSeriesList.isNotEmpty()) {
                    binding.tvNodatafound.visibility = View.INVISIBLE
                    binding.rvLayoutSeries.visibility = View.VISIBLE
                    seriesAdapter.submitList(searchSeriesList.sortedBy { it.seriesName })
                    binding.rvLayoutSeries.requestFocus()
                } else {
                    binding.rvLayoutSeries.visibility = View.INVISIBLE
                    binding.tvNodatafound.visibility = View.VISIBLE
                    binding.editTextSearch.requestFocus()
                }
            }
        } else {
            resetVisibility()
            val filteredSeriesList = xtreamViewModel.seriesSearchList.filter { it.seriesName.contains(searchTerm, ignoreCase = true) }
            binding.progressBar.visibility = View.INVISIBLE
            if (filteredSeriesList.isNotEmpty()) {
                binding.tvNodatafound.visibility = View.INVISIBLE
                binding.rvLayoutSeries.visibility = View.VISIBLE
                seriesAdapter.submitList(filteredSeriesList.sortedBy { it.seriesName })
                binding.rvLayoutSeries.requestFocus()
            } else {
                binding.rvLayoutSeries.visibility = View.INVISIBLE
                binding.tvNodatafound.visibility = View.VISIBLE
                binding.editTextSearch.requestFocus()
            }
        }
    }

    private fun resetVisibility() {
        seriesAdapter.submitList(null)
    }

    private fun prepareSeriesRecyclerView() {
        seriesAdapter = SearchSeriesAdapter(onSeriesClickListener,this, helpViewModel)
        binding.rvLayoutSeries.apply {
            adapter = seriesAdapter
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

    private val onSeriesClickListener = SearchSeriesAdapter.OnClickListener { serie ->
        val account = serie.accountId?.let {
            accountBox.get(it)
        }
        if (serie.idByAccountData == helpViewModel.currentFocusedSerie?.idByAccountData) {
            seriesViewModel.openedSameSeries = true
        }
        if (account != null) {
            if (account.isXtream) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val seasons =
                        xtreamViewModel.getXtreamSerieDetails(serie, account)
                    serie.totalSeasons = seasons.size
                    helpViewModel.focusedSeasons = seasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                        .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE }).toMutableList()
                    helpViewModel.focusedEpisodes = xtreamViewModel.episodesList.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })).toMutableList()
                    helpViewModel.currentFocusedSerie = serie
                    openSeriesDetailFragment()
                }
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    stalkerViewModel.getSeriesDetail(serie, account)
                    helpViewModel.currentFocusedSerie = serie
                    openSeriesDetailFragment()
                }
            }
        }
    }

    fun focusToSearchText() {
        binding.editTextSearch.requestFocus()
    }

    fun openSeriesDetailFragment() {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.add(R.id.container_series_info, SeriesDetailFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        helpViewModel.isSearchContainerOpened = true
    }

    private val backStackListener = FragmentManager.OnBackStackChangedListener {
        if (parentFragmentManager.fragments.lastOrNull() == this) {
            // Das Fragment ist wieder sichtbar!
            if (helpViewModel.isSearchContainerOpened) {
                helpViewModel.isSearchContainerOpened = false
                binding.rvLayoutSeries.requestFocus()
            }
        }
    }

    fun closeFragment() {
        seriesViewModel.requestFocusToSeries()
        xtreamViewModel.seriesSearchList = mutableListOf()
        parentFragmentManager.popBackStack()
    }

    override fun onDestroy() {
        super.onDestroy()
        parentFragmentManager.removeOnBackStackChangedListener(backStackListener)
        _binding = null
    }
}