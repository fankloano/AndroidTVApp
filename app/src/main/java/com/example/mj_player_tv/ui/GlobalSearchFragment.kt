package com.example.mj_player_tv.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.SeasonsOB
import com.example.mj_player_tv.database.entity.Settings
import com.example.mj_player_tv.databinding.FragmentSearchGlobalBinding
import com.example.mj_player_tv.ui.adapter.GlobalSearchEpgListAdapter
import com.example.mj_player_tv.ui.adapter.GlobalSearchHistoryAdapter
import com.example.mj_player_tv.ui.adapter.GlobalSearchMoviesAdapter
import com.example.mj_player_tv.ui.adapter.GlobalSearchSeriesAdapter
import com.example.mj_player_tv.ui.adapter.GlobalSearchTvChannelsAdapter
import com.example.mj_player_tv.ui.adapter.GlobalSearchPlaylistAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.MoviesViewModel
import com.example.mj_player_tv.viewmodel.MoviesViewModelFactory
import com.example.mj_player_tv.viewmodel.PlexViewModel
import com.example.mj_player_tv.viewmodel.PlexViewModelFactory
import com.example.mj_player_tv.viewmodel.SeriesViewModel
import com.example.mj_player_tv.viewmodel.SeriesViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import com.rubensousa.dpadrecyclerview.FocusableDirection
import kotlinx.coroutines.launch
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.database.entity.Programme_
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.help.GlobalSearchDisplayItem
import com.example.mj_player_tv.database.help.GlobalSearchItem
import com.example.mj_player_tv.database.help.GlobalSearchMainCategory
import com.example.mj_player_tv.ui.adapter.GlobalSearchItemsAdapter
import com.example.mj_player_tv.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import org.threeten.bp.Duration
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter

@UnstableApi
class GlobalSearchFragment : Fragment(R.layout.fragment_search_global) {

    private var _binding: FragmentSearchGlobalBinding? = null

    private val binding get() = _binding!!

    private val settingsBox = ObjectBox.store.boxFor(Settings::class.java)

    private val accountBox = ObjectBox.store.boxFor(Accounts::class.java)

    private val tvCatBox = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    private val programmeBox = ObjectBox.store.boxFor(Programme::class.java)

    private val epgDataBox = ObjectBox.store.boxFor(EpgDataOB::class.java)

    private lateinit var searchHistoryAdapter: GlobalSearchHistoryAdapter

    private lateinit var globalSearchItemAdapter: GlobalSearchItemsAdapter

    private lateinit var playlistAdapter: GlobalSearchPlaylistAdapter

    private var isFirstOpenGlobalSearch = true

    private var lastSearchQuery = ""

    private var currentfilterOption: Boolean? = null

    private var selectedGlobalSearchCategory: GlobalSearchMainCategory? = null

    private var lastLoadedCategory: GlobalSearchMainCategory? = null

    private var selectedAccount: Accounts? = null

    private var channelsByAccount: Map<Accounts, List<ChannelPositions>>? = null

    private var moviesByAccount: Map<Accounts, List<MovieOB>>? = null

    private var seriesByAccount: Map<Accounts, List<SeriesOB>>? = null

    private var programsByAccount: Map<Accounts, List<Pair<ChannelPositions, List<EpgDataOB>>>>? = null

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

    private val plexViewModel: PlexViewModel by activityViewModels {
        PlexViewModelFactory(
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

    private val moviesViewModel: MoviesViewModel by activityViewModels {
        MoviesViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchGlobalBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            closeFragment()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareSearchHistoryRecyclerView()
        preparePlaylistRecyclerView()
        prepareItemsRecyclerView()

        showSearchHistory()

        helpViewModel.settings?.let {
            binding.cbFilterSearch.isChecked = helpViewModel.settings!!.globalSearchFilteredCategories
        }

        parentFragmentManager.addOnBackStackChangedListener(backStackListener)
        (requireActivity() as? MainActivity)?.hideMenu()
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        binding.editTextSearch.requestFocus()
        binding.editTextSearch.isCursorVisible = true
        binding.editTextSearch.setSelection(binding.editTextSearch.length())
        imm.showSoftInput(binding.editTextSearch, InputMethodManager.SHOW_IMPLICIT)

        binding.editTextSearch.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                goToMainMenu()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                if (binding.editTextSearch.text.isEmpty()) {
                    binding.recyclerSearchhistory.requestFocus()
                    return@setOnKeyListener true
                } else {
                    if (playlistAdapter.currentList.isNotEmpty()) {
                        focusToTextView()
                        hideSearchBarShowArrow()
                        return@setOnKeyListener true
                    }
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                focusSettings()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                val editText = v as EditText
                val cursorPosition = editText.selectionStart
                val textLength = editText.text.length

                if (cursorPosition == textLength) {
                    // Cursor ist am Ende, führe hier die gewünschte Aktion aus
                    focusSettings()
                    return@setOnKeyListener true // Die Taste wurde verarbeitet
                }
            }
            false
        }

        binding.editTextSearch.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) {

                val currentText = binding.editTextSearch.text.toString().trim()
                // Deine Funktion aufrufen
                if (currentText != lastSearchQuery && currentText.isNotEmpty()) {
                    lastSearchQuery = currentText
                    Log.d("EDITTEXTNAME","START SEARCH: $lastSearchQuery")
                    searchFor(binding.editTextSearch.text.toString())

                    // Virtuelle Tastatur ausblenden
                    val imm =
                        v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)

                    true // Ereignis wurde behandelt
                } else {
                    return@setOnEditorActionListener true
                }
            } else {
                false // Weiterverarbeiten lassen
            }
        }

        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val isEmpty = s.isNullOrEmpty()
                binding.recyclerSearchhistory.visibility = if (isEmpty) View.VISIBLE else View.GONE
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.editTextSearch.setOnFocusChangeListener { _, hasFocus ->
           if (hasFocus) {
               binding.relLayoutSettings.visibility = View.VISIBLE
               binding.backgroundDarker.visibility = View.VISIBLE
           }
        }

        binding.relLayoutSettings.setOnClickListener {
            currentfilterOption = helpViewModel.settings?.globalSearchFilteredCategories
            binding.linLayoutSearchoptionsMenu.visibility = View.VISIBLE
            binding.relLayoutFiltersearch.requestFocus()
        }

        binding.relLayoutSettings.setOnKeyListener { _, keyCode, event ->
            if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                binding.editTextSearch.requestFocus()
                return@setOnKeyListener true
            }
            if ((keyCode == KeyEvent.KEYCODE_DPAD_DOWN) && event.action == KeyEvent.ACTION_DOWN) {
                focusToTextView()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }


        binding.relLayoutFiltersearch.setOnClickListener {
            binding.cbFilterSearch.isChecked = !binding.cbFilterSearch.isChecked
            if (helpViewModel.settings?.globalSearchFilteredCategories == false) {
                helpViewModel.settings?.globalSearchFilteredCategories = true
            } else {
                helpViewModel.settings?.globalSearchFilteredCategories = false
            }
            helpViewModel.settings?.let { settingsBox.put(it) }
        }

        binding.relLayoutFiltersearch.setOnKeyListener { _, keyCode, event ->
            if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                binding.linLayoutSearchoptionsMenu.visibility = View.GONE
                if (currentfilterOption != helpViewModel.settings?.globalSearchFilteredCategories) {
                    lastSearchQuery = ""
                }
                binding.relLayoutSettings.requestFocus()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.relLayoutClearhistory.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage("Are you sure you want to clear your search history?")
                .setPositiveButton("Yes") { dialog, _ ->
                    helpViewModel.settings?.searchString = mutableListOf()
                    helpViewModel.settings?.let {
                        Log.d("ClearHistory", "Search String is now: ${it.searchString}")
                        settingsBox.put(it)
                        searchHistoryAdapter.submitList(listOf())
                    }

                    dialog.dismiss()
                }
                .setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                    binding.relLayoutClearhistory.requestFocus()
                }
                .show()
        }

        binding.relLayoutClearhistory.setOnKeyListener { _, keyCode, event ->
            if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                binding.linLayoutSearchoptionsMenu.visibility = View.GONE
                binding.relLayoutSettings.requestFocus()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.searchResults.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED).collectLatest { results ->
                val tvResults = results.filterIsInstance<GlobalSearchItem.TvChannels>()
                channelsByAccount  = tvResults.groupBy(
                    keySelector = {it.account},
                    valueTransform = {it.channels}
                ).mapValues { it.value.flatten() }
                binding.tvCatTv.visibility =
                    if (channelsByAccount?.values?.flatten().isNullOrEmpty()) View.GONE else View.VISIBLE
                val movieResults = results.filterIsInstance<GlobalSearchItem.Movies>()
                moviesByAccount  = movieResults.groupBy(
                    keySelector = {it.account},
                    valueTransform = {it.movies}
                ).mapValues { it.value.flatten() }
                binding.tvCatMovies.visibility =
                    if (moviesByAccount?.values?.flatten().isNullOrEmpty()) View.GONE else View.VISIBLE
                val seriesResults = results.filterIsInstance<GlobalSearchItem.Series>()
                seriesByAccount = seriesResults.groupBy(
                    keySelector = { it.account },
                    valueTransform = { it.series }
                ).mapValues { it.value.flatten() } // Alle Listen zu einer Liste zusammenfassen
                binding.tvCatSeries.visibility =
                    if (seriesByAccount?.values?.flatten().isNullOrEmpty()) View.GONE else View.VISIBLE
                val programResults = results.filterIsInstance<GlobalSearchItem.Programs>()
                programsByAccount = programResults.groupBy(
                    keySelector = { it.account },
                    valueTransform = { it.programs }
                ).mapValues { it.value.flatten() }
                binding.tvCatEpg.visibility =
                    if (programsByAccount?.values?.flatten().isNullOrEmpty()) View.GONE else View.VISIBLE
                if (isFirstOpenGlobalSearch) {
                    val (firstCategory, firstMap) = listOf(
                        GlobalSearchMainCategory.TV to channelsByAccount,
                        GlobalSearchMainCategory.MOVIES to moviesByAccount,
                        GlobalSearchMainCategory.SERIES to seriesByAccount,
                        GlobalSearchMainCategory.PROGRAMS to programsByAccount
                    ).firstOrNull { it.second?.isNotEmpty() == true }
                        ?: return@collectLatest // keine Ergebnisse

                    val firstAccount = firstMap?.keys?.minByOrNull { it.name } ?: return@collectLatest

                    helpViewModel.selectedGlobalSearchCategory = firstCategory
                    selectedGlobalSearchCategory = firstCategory
                    helpViewModel.selectedGlobalSearchAccount = firstAccount
                    selectedAccount = firstAccount

                    // 👉 Playlists setzen
                    val initialPlaylists = firstMap.keys.toList().sortedBy { it.name }
                    playlistAdapter.submitList(initialPlaylists)


                    // 👉 Fokus-Setzen verzögert nach UI-Update
                    binding.root.post {
                        when (firstCategory) {
                            GlobalSearchMainCategory.TV -> binding.tvCatTv.requestFocus()
                            GlobalSearchMainCategory.MOVIES -> binding.tvCatMovies.requestFocus()
                            GlobalSearchMainCategory.SERIES -> binding.tvCatSeries.requestFocus()
                            GlobalSearchMainCategory.PROGRAMS -> binding.tvCatEpg.requestFocus()
                        }
                    }

                    // **Nur einmal setzen**
                    isFirstOpenGlobalSearch = false
                }
                val newPlaylists = when (selectedGlobalSearchCategory) {
                    GlobalSearchMainCategory.TV -> channelsByAccount?.keys
                    GlobalSearchMainCategory.MOVIES -> moviesByAccount?.keys
                    GlobalSearchMainCategory.SERIES -> seriesByAccount?.keys
                    GlobalSearchMainCategory.PROGRAMS -> programsByAccount?.keys
                    else -> null
                }
                if ((newPlaylists?.sortedBy { it.name }
                        ?: emptyList()) != playlistAdapter.currentList) {
                    playlistAdapter.submitList(newPlaylists?.sortedBy { it.name })
                }
                selectedAccount?.let { account ->
                    selectedGlobalSearchCategory?.let {
                        val items = getDisplayableItemsFor(account, it)  // Neue Items vom aktuellen Account & Kategorie
// Liste an Adapter übergeben (am besten als Kopie, da Adapter intern immutable Liste erwartet)
                        globalSearchItemAdapter.submitList(items)

                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.isSearching.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED).collectLatest { searching ->
                if (searching) {
                    // Zeige z.B. ProgressBar, lade Spinner etc.
                    binding.tvNodatafound.visibility = View.GONE
                    binding.progressBar.visibility = View.VISIBLE
                } else {
                    if (!helpViewModel.hasSearched) {
                        // Noch keine Suche gestartet -> kein "No Data" anzeigen
                        binding.tvNodatafound.visibility = View.GONE
                        return@collectLatest
                    }

                    // Verberge ProgressBar, zeige UI mit Ergebnissen
                    binding.progressBar.visibility = View.GONE
                    val hasResults = !(channelsByAccount?.values?.flatten().isNullOrEmpty()
                            && moviesByAccount?.values?.flatten().isNullOrEmpty()
                            && seriesByAccount?.values?.flatten().isNullOrEmpty()
                            && programsByAccount?.values?.flatten().isNullOrEmpty())

                    if (hasResults) {
                        binding.tvNodatafound.visibility = View.GONE
                    } else {
                        showSearchBarHideArrow()
                        binding.tvNodatafound.visibility = View.VISIBLE
                    }
                }
            }
        }


        binding.tvCatTv.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tvCatTv.isSelected = true
                binding.tvCatMovies.isSelected = false
                binding.tvCatSeries.isSelected = false
                binding.tvCatEpg.isSelected = false
                onCategorySelected(GlobalSearchMainCategory.TV)
            }
        }

        binding.tvCatTv.setOnKeyListener { _, keyCode, event ->
            if ((keyCode) == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                showSearchBarHideArrow()
                return@setOnKeyListener true
            }
            if ((keyCode) == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                if (playlistAdapter.currentList.isNotEmpty()) {
                    binding.recyclerPlaylists.requestFocus()
                    return@setOnKeyListener true
                } else {
                    binding.tvCatTv.requestFocus()
                    return@setOnKeyListener true
                }
            }
            if ((keyCode) == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                showSearchBarHideArrow()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.tvCatMovies.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tvCatTv.isSelected = false
                binding.tvCatMovies.isSelected = true
                binding.tvCatSeries.isSelected = false
                binding.tvCatEpg.isSelected = false
                onCategorySelected(GlobalSearchMainCategory.MOVIES)
            }
        }

        binding.tvCatMovies.setOnKeyListener { _, keyCode, event ->
            if ((keyCode) == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                showSearchBarHideArrow()
                return@setOnKeyListener true
            }
            if ((keyCode) == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                if (playlistAdapter.currentList.isNotEmpty()) {
                    binding.recyclerPlaylists.requestFocus()
                    return@setOnKeyListener true
                } else {
                    binding.tvCatMovies.requestFocus()
                    return@setOnKeyListener true
                }
            }
            if ((keyCode) == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                showSearchBarHideArrow()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.tvCatSeries.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tvCatTv.isSelected = false
                binding.tvCatMovies.isSelected = false
                binding.tvCatSeries.isSelected = true
                binding.tvCatEpg.isSelected = false
                onCategorySelected(GlobalSearchMainCategory.SERIES)
            }
        }

        binding.tvCatSeries.setOnKeyListener { _, keyCode, event ->
            if ((keyCode) == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                showSearchBarHideArrow()
                return@setOnKeyListener true
            }
            if ((keyCode) == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                if (playlistAdapter.currentList.isNotEmpty()) {
                    binding.recyclerPlaylists.requestFocus()
                    return@setOnKeyListener true
                } else {
                    binding.tvCatSeries.requestFocus()
                    return@setOnKeyListener true
                }
            }
            if ((keyCode) == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                showSearchBarHideArrow()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.tvCatEpg.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tvCatTv.isSelected = false
                binding.tvCatMovies.isSelected = false
                binding.tvCatSeries.isSelected = false
                binding.tvCatEpg.isSelected = true
                onCategorySelected(GlobalSearchMainCategory.PROGRAMS)
            }
        }

        binding.tvCatEpg.setOnKeyListener { _, keyCode, event ->
            if ((keyCode) == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                showSearchBarHideArrow()
                return@setOnKeyListener true
            }
            if ((keyCode) == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                if (playlistAdapter.currentList.isNotEmpty()) {
                    binding.recyclerPlaylists.requestFocus()
                    return@setOnKeyListener true
                } else {
                    return@setOnKeyListener true
                }
            }
            if ((keyCode) == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                showSearchBarHideArrow()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        seriesViewModel.focusToSeriesRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                binding.recyclerItems.requestFocus()
                seriesViewModel.clearFocusToSeries()
            }
        }

        moviesViewModel.focusToMoviesRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                binding.recyclerItems.requestFocus()
                moviesViewModel.clearFocusToMovies()
            }
        }

        seriesViewModel.updateSerieRVRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                val thisSerie = globalSearchItemAdapter.currentList.firstOrNull { it is GlobalSearchDisplayItem.SeriesItem && it.series.idByAccountData == helpViewModel.currentFocusedSerie?.idByAccountData }
                val position = globalSearchItemAdapter.currentList.indexOf(thisSerie)
                globalSearchItemAdapter.notifyItemChanged(position)
                Log.d("UPDATEGLOBALSEARCHITEM", "SERIE: $position")
                seriesViewModel.clearUpdateSerieInRV()
            }
        }

        moviesViewModel.updateMovieRVRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                val thisMovie = globalSearchItemAdapter.currentList.firstOrNull { it is GlobalSearchDisplayItem.MovieItem && it.movie.idByAccountData == helpViewModel.currentFocusedMovie?.idByAccountData }
                val position = globalSearchItemAdapter.currentList.indexOf(thisMovie)
                globalSearchItemAdapter.notifyItemChanged(position)
                Log.d("UPDATEGLOBALSEARCHITEM", "MOVIE: $position")
                moviesViewModel.clearUpdateOnMovieRV()
            }
        }
    }

    private fun getDisplayableItemsFor(account: Accounts, category: GlobalSearchMainCategory): List<GlobalSearchDisplayItem> {
        return when (category) {
            GlobalSearchMainCategory.TV -> channelsByAccount?.get(account)?.map { GlobalSearchDisplayItem.ChannelItem(it) } ?: emptyList()
            GlobalSearchMainCategory.MOVIES -> moviesByAccount?.get(account)?.map { GlobalSearchDisplayItem.MovieItem(it) } ?: emptyList()
            GlobalSearchMainCategory.SERIES -> seriesByAccount?.get(account)?.map { GlobalSearchDisplayItem.SeriesItem(it) } ?: emptyList()
            GlobalSearchMainCategory.PROGRAMS -> {
                programsByAccount?.get(account)?.let { programs ->
                    listOf(GlobalSearchDisplayItem.ProgramItem(programs))
                } ?: emptyList()
            }
        }
    }

    fun updateItemList(account: Accounts) {
        if (account == selectedAccount && selectedGlobalSearchCategory == lastLoadedCategory) {
            return
        }
        if (selectedGlobalSearchCategory != null) {
            globalSearchItemAdapter.submitList(null)
            when (selectedGlobalSearchCategory) {
                GlobalSearchMainCategory.PROGRAMS -> binding.recyclerItems.setSpanCount(1)
                else -> {
                    binding.recyclerItems.setSpanCount(7)
                } // optional: nichts tun oder Default setzen
            }
            val oldPosition = playlistAdapter.currentList.indexOf(helpViewModel.selectedGlobalSearchAccount)
            val newPosition = playlistAdapter.currentList.indexOf(account)
            helpViewModel.selectedGlobalSearchAccount = account
            selectedAccount = account
            lastLoadedCategory = selectedGlobalSearchCategory
            playlistAdapter.notifyItemChanged(oldPosition)
            playlistAdapter.notifyItemChanged(newPosition)
            val items = getDisplayableItemsFor(account, selectedGlobalSearchCategory!!)
            globalSearchItemAdapter.submitList(items)
        }
    }

    fun onCategorySelected(category: GlobalSearchMainCategory) {
        if (selectedGlobalSearchCategory == category) {
            // Kategorie ist schon aktiv → nichts tun
            return
        }
        playlistAdapter.submitList(null)
        helpViewModel.selectedGlobalSearchCategory = category
        selectedGlobalSearchCategory = category

        val accounts = when (category) {
            GlobalSearchMainCategory.TV -> channelsByAccount?.keys?.toList()
            GlobalSearchMainCategory.MOVIES -> moviesByAccount?.keys?.toList()
            GlobalSearchMainCategory.SERIES -> seriesByAccount?.keys?.toList()
            GlobalSearchMainCategory.PROGRAMS -> programsByAccount?.keys?.toList()
        }?.sortedBy { it.name }

        playlistAdapter.submitList(accounts)
        helpViewModel.selectedGlobalSearchAccount = accounts?.firstOrNull()
        selectedAccount = accounts?.firstOrNull()
        selectedAccount?.let { updateItemList(it) }
    }

    fun focusToItems() {
        if (globalSearchItemAdapter.currentList.isNotEmpty()) {
            binding.recyclerItems.requestFocus()
        } else {
            return
        }
    }

    fun focusToSearchBar() {
        if (binding.recyclerSearchhistory.isInvisible) {
            binding.recyclerSearchhistory.visibility = View.VISIBLE
        }
        binding.editTextSearch.requestFocus()
    }

    private fun showSearchHistory() {
        if (helpViewModel.settings != null) {
            val searchHistory = helpViewModel.settings!!.searchString
            if (searchHistory.isNotEmpty()) {
                binding.backgroundDarker.visibility = View.VISIBLE
                searchHistoryAdapter.submitList(listOf())
                binding.recyclerSearchhistory.visibility = View.VISIBLE
                searchHistoryAdapter.submitList(searchHistory)
            } else {
                binding.recyclerSearchhistory.visibility = View.INVISIBLE
                binding.backgroundDarker.visibility = View.INVISIBLE
            }
        }
    }

    private fun hideSearchBarShowArrow() {
        binding.recyclerSearchhistory.visibility = View.GONE
        binding.progressBar.visibility = View.INVISIBLE
        binding.relLayoutSettings.visibility = View.GONE
        binding.backgroundDarker.visibility = View.GONE
        binding.relLayoutSearchMovie.visibility = View.GONE
        binding.ivShowSearch.visibility = View.VISIBLE
    }

    private fun showSearchBarHideArrow() {
        binding.ivShowSearch.visibility = View.GONE
        binding.relLayoutSearchMovie.visibility = View.VISIBLE
        Log.d("EDITTEXTNAME", "IS: $lastSearchQuery")
        binding.editTextSearch.setText(lastSearchQuery)
        Log.d("EDITTEXTNAME", "NOW: ${binding.editTextSearch.text.toString()}")
        binding.backgroundDarker.visibility = View.VISIBLE
        // Fokus setzen
        binding.editTextSearch.post { // Sicherstellen, dass die UI bereit ist
            binding.editTextSearch.setSelection(binding.editTextSearch.text.length)
            binding.editTextSearch.requestFocus()
            val imm =
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.editTextSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun focusSettings() {
        binding.relLayoutSettings.requestFocus()
    }


    private fun searchFor(searchTerm: String) {
        viewLifecycleOwner.lifecycleScope.launch {
                playlistAdapter.submitList(null)
                globalSearchItemAdapter.submitList(null)
                binding.tvCatTv.visibility = View.GONE
                binding.tvCatMovies.visibility = View.GONE
                binding.tvCatSeries.visibility = View.GONE
                binding.tvCatEpg.visibility = View.GONE
                binding.backgroundDarker.visibility = View.GONE
                hideSearchBarShowArrow()
                saveSearchTerm(searchTerm)
                binding.progressBar.visibility = View.VISIBLE
                helpViewModel.resetGlobalSearchData()

                isFirstOpenGlobalSearch = true
                val settings = helpViewModel.settings
                if (settings != null) {
                   helpViewModel.makeGlobalSearch(settings.globalSearchFilteredCategories, searchTerm)
            }
        }
    }

    private fun saveSearchTerm(searchTerm: String) {
        if (helpViewModel.settings?.searchString?.contains(searchTerm) == true) {
            helpViewModel.settings?.searchString?.remove(searchTerm)
            helpViewModel.settings?.searchString?.add(0, searchTerm)
            helpViewModel.settings?.let {
                settingsBox.put(it)
                searchHistoryAdapter.submitList(null)
                searchHistoryAdapter.submitList(it.searchString)
            }
        } else {
            helpViewModel.settings?.searchString?.add(0, searchTerm)
            helpViewModel.settings?.let {
                settingsBox.put(it)
                searchHistoryAdapter.submitList(null)
                searchHistoryAdapter.submitList(it.searchString)
            }
        }
    }

    private fun prepareSearchHistoryRecyclerView() {
        searchHistoryAdapter = GlobalSearchHistoryAdapter(onSearchHistoryClickListener, onSearchHistoryLongClickListener, this, helpViewModel)
        binding.recyclerSearchhistory.apply {
            adapter = searchHistoryAdapter
            setFocusOutAllowed(throughFront = true, throughBack = false)
            setFocusOutSideAllowed(throughFront = false, throughBack = false)
            setSmoothFocusChangesEnabled(false)
        }
    }


    private fun preparePlaylistRecyclerView() {
        playlistAdapter = GlobalSearchPlaylistAdapter(helpViewModel, this)
        binding.recyclerPlaylists.apply {
            adapter = playlistAdapter
            setFocusOutAllowed(throughFront = false, throughBack = false)
            setFocusOutSideAllowed(throughFront = true, throughBack = true)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private fun prepareItemsRecyclerView() {
        globalSearchItemAdapter = GlobalSearchItemsAdapter(helpViewModel, this, programmeBox, epgDataBox) { clickedItem ->
            when (clickedItem) {
                is GlobalSearchDisplayItem.ChannelItem -> {
                    playChannel(clickedItem.channel)
                }
                is GlobalSearchDisplayItem.MovieItem -> {
                    helpViewModel.currentMovieAccount = selectedAccount
                    helpViewModel.currentFocusedMovie = clickedItem.movie
                    openMovieDetailFragment()
                }
                is GlobalSearchDisplayItem.SeriesItem -> {
                    if (selectedAccount != null) {
                        if (clickedItem.series.idByAccountData == helpViewModel.currentFocusedSerie?.idByAccountData) {
                            seriesViewModel.openedSameSeries = true
                        }
                        if (selectedAccount!!.isXtream) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val seasons =
                                    xtreamViewModel.getXtreamSerieDetails(clickedItem.series, selectedAccount!!)
                                clickedItem.series.totalSeasons = seasons.size
                                helpViewModel.focusedSeasons =
                                    seasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                                        .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE })
                                        .toMutableList()
                                helpViewModel.focusedEpisodes =
                                    xtreamViewModel.episodesList.sortedWith(
                                        compareBy(
                                            { it.seasonNumber },
                                            { it.episodeNumber })
                                    ).toMutableList()
                                helpViewModel.currentFocusedSerie = clickedItem.series
                                helpViewModel.currentSeriesAccount = selectedAccount
                                openSeriesDetailFragment()
                            }
                        } else {
                            viewLifecycleOwner.lifecycleScope.launch {
                                stalkerViewModel.seriesDetailData.postValue(mutableListOf())
                                stalkerViewModel.getSeriesDetail(clickedItem.series, selectedAccount!!)
                                helpViewModel.currentFocusedSerie = clickedItem.series
                                stalkerViewModel.seriesDetailData.observe(viewLifecycleOwner) { seasons ->
                                    clickedItem.series.totalSeasons = seasons.size
                                    helpViewModel.focusedSeasons =
                                        seasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                                            .thenBy {
                                                it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE
                                            }).toMutableList()
                                    helpViewModel.focusedEpisodes =
                                        stalkerViewModel.episodesList.sortedWith(
                                            compareBy(
                                                { it.seasonNumber },
                                                { it.episodeNumber })
                                        ).toMutableList()
                                }
                                helpViewModel.currentSeriesAccount = selectedAccount
                                openSeriesDetailFragment()
                            }
                        }
                    }
                }

                is GlobalSearchDisplayItem.ProgramItem -> {

                }
            }
        }
        binding.recyclerItems.apply {
            adapter = globalSearchItemAdapter
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setFocusOutAllowed(true, false)
            setFocusOutSideAllowed(false, false)
            setSmoothFocusChangesEnabled(false)
        }
    }

    fun playChannel(tvChannelPos: ChannelPositions) {
        helpViewModel.currentFocusedChannPosition = tvChannelPos
        helpViewModel.channelFromSearchContainer = true
        helpViewModel.currentFocusedTvAccount = tvChannelPos.tvcategory.target.tvaccount.target
        helpViewModel.currentFocusedTvCategory = tvChannelPos.tvcategory.target
        helpViewModel.checkCategoryActivated(tvChannelPos.tvcategory.target)
        Log.d("CLICKEDFROMGLOBALSEARCH", "${tvChannelPos.tvchannel.target.showingName} IN ${tvChannelPos.tvcategory.target.showingName}")
        (requireActivity() as? MainActivity)?.checkTvChannelsFragmentFromGlobalSearch()
    }


    fun replayProgram(tvChannelPos: ChannelPositions, clickedEpgData: EpgDataOB) {
        val tvCategory = tvChannelPos.tvcategory.target
        val tvChannel = tvChannelPos.tvchannel.target
        helpViewModel.currentFocusedChannPosition = tvChannelPos
        helpViewModel.currentFocusedTvCategory = tvCategory
        if (tvChannel.linkedEpgChannel?.target?.isExternalEpg == true) {
            if (tvChannel.account.target.isXtream) {
                clickedEpgData.let { epgData ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val thisEpg = xtreamViewModel.findEpgMatch(
                            epgData,
                            tvChannel,
                            tvCategory!!
                        )
                        when (thisEpg) {
                            is Resource.Success -> {
                                if (thisEpg.data != null) {
                                    val startTime = thisEpg.data.start
                                    val endTime = thisEpg.data.end
                                    getXtreamCatchup(
                                        tvChannelPos,
                                        startTime,
                                        endTime,
                                        clickedEpgData
                                    )
                                }
                            }
                            is Resource.Error -> {
                                Toast.makeText(
                                    this@GlobalSearchFragment.requireActivity(),
                                    "Error fetching Catchup Link!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }
                        }
                    }
                }
            } else {
                clickedEpgData.let { epgData ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val thisEpg = stalkerViewModel.findEpgMatch(
                            epgData,
                            tvChannel,
                            epgData.datum,
                            tvCategory!!
                        )
                        Log.d("CATCHUP STALKER", "NOT EXTERN: ${epgData.name}")
                        when (thisEpg) {
                            is Resource.Success -> {
                                if (thisEpg.data != null) {
                                    val epgId = thisEpg.data.id
                                    getStalkerCatchupLink(
                                        tvChannelPos,
                                        epgId,
                                        epgData
                                    )
                                }
                            }

                            is Resource.Error -> {
                                Toast.makeText(
                                    this@GlobalSearchFragment.requireActivity(),
                                    "Error fetching Catchup Link!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }
                        }
                    }
                }
            }
        } else {
            if (tvChannel.linkedEpgChannel?.target?.epgsource?.target?.isXtreamEpg == true) {
                clickedEpgData.let { epgData ->
                    getXtreamCatchup(tvChannelPos, epgData.startTime, epgData.endTime, clickedEpgData)
                }
            } else {
                clickedEpgData.let { epgData ->
                    getStalkerCatchupLink(tvChannelPos, epgData.epgId, clickedEpgData)
                }
            }
        }
    }

    fun getXtreamCatchup(tvChannelPos: ChannelPositions, startTime: String, endTime: String, clickedEpgData: EpgDataOB) {
        val account = tvChannelPos.tvchannel.target.account.target
        if (account != null) {
            val accountUrl = account.stalkerUrl
            val accountUserName = account.username
            val accountPassword = account.macAddress
            val epgStart = startTime.substring(0, 10) + ":" + startTime.substring(
                11,
                13
            ) + "-" + startTime.substring(14, 16)
            val duration = calculateDurationInMinutes(startTime, endTime)
            val url =
                "$accountUrl/streaming/timeshift.php?username=$accountUserName&password=$accountPassword&stream=${tvChannelPos.tvchannel.target.channelId}&start=$epgStart&duration=$duration"
            helpViewModel.globalSearchCatchupUrl = url
            helpViewModel.isPlayingCatchup = true
            helpViewModel.catchupEpgData = clickedEpgData
            helpViewModel.currentFocusedTvAccount = account
            helpViewModel.currentFocusedTvCategory = tvChannelPos.tvcategory.target
            helpViewModel.channelFromSearchContainer = true
            (requireActivity() as MainActivity).checkTvChannelsFragmentFromGlobalSearch()
        }
    }

    fun calculateDurationInMinutes(startString: String, endString: String): Long {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val startDateTime = LocalDateTime.parse(startString, formatter)
        val endDateTime = LocalDateTime.parse(endString, formatter)
        return Duration.between(startDateTime, endDateTime).toMinutes()
    }

    fun getStalkerCatchupLink(tvChannelPos: ChannelPositions, epgId: String, clickedEpgData: EpgDataOB) {
        viewLifecycleOwner.lifecycleScope.launch {
            val account = tvChannelPos.tvchannel.target.account.target
            if (account != null) {
                val catchUp = stalkerViewModel.getTvCatchupLink(
                    account.stalkerUrl,
                    cmd = "/media/$epgId.mpg",
                    cookie = "mac=${account.macAddress}; stb_lang=en; timezone=${account.timezone};",
                    token = "Bearer ${account.token}",
                    account.userAgent
                ).await()
                when (catchUp) {
                    is Resource.Success -> {
                        Log.d("CATCHUP STALKER", "CATCHUPDATA: ${catchUp.data}")
                        helpViewModel.isPlayingCatchup = true
                        helpViewModel.catchupEpgData = clickedEpgData
                        helpViewModel.currentFocusedTvAccount = account
                        helpViewModel.currentFocusedTvCategory = tvChannelPos.tvcategory.target
                        helpViewModel.channelFromSearchContainer = true
                        helpViewModel.globalSearchCatchupUrl = catchUp.data?.removePrefix("ffmpeg")?.trim() ?: ""
                        (requireActivity() as MainActivity).checkTvChannelsFragmentFromGlobalSearch()
                    }
                    is Resource.Error -> {
                        Toast.makeText(
                            this@GlobalSearchFragment.requireActivity(),
                            "Error fetching Catchup Link!\n${catchUp.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.recyclerItems.requestFocus()
                    }
                }
            }
        }
    }


    private val onSearchHistoryClickListener = GlobalSearchHistoryAdapter.OnClickListener {
        if (it.isNotEmpty()) {
            lastSearchQuery = it
            searchFor(it)
        }
    }

    private val onSearchHistoryLongClickListener = GlobalSearchHistoryAdapter.OnLongClickListener { searchTerm ->
        AlertDialog.Builder(requireContext())
            .setMessage("Delete search term?")
            .setPositiveButton("Yes") { dialog, _ ->
                helpViewModel.settings?.let { settings ->
                    // 1) NEUE Liste aus der aktuellen bauen (immutabel für DiffUtil)

                    val updated = searchHistoryAdapter.currentList
                        .toMutableList()                // kopieren
                        .apply {
                            // ENTWEDER nur erstes Vorkommen entfernen:
                            val idx = indexOfFirst { it.equals(searchTerm, ignoreCase = true) }
                            if (idx != -1) removeAt(idx)

                            // ODER alle Vorkommen entfernen:
                            // removeAll { it.equals(term, ignoreCase = true) }
                        }
                        .toList()                        // wieder immutable machen
                    // 2) Neue Liste submitten (Main-Thread; Click-Listener ist bereits Main)
                    searchHistoryAdapter.submitList(updated)

                    // 3) Optional: Persistieren (z. B. in Settings/DB) – im IO-Thread
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        helpViewModel.settings?.let { settings ->
                            val persisted = settings.searchString
                                .filterNot { it.equals(searchTerm, ignoreCase = true) }
                            settings.searchString.clear()
                            settings.searchString.addAll(persisted)
                            settingsBox.put(settings)
                        }
                    }
                }

                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
                binding.relLayoutClearhistory.requestFocus()
            }
            .show()
    }

    fun openMovieDetailFragment() {
        helpViewModel.isSearchContainerOpened = true
        val transaction = parentFragmentManager.beginTransaction()
        transaction.add(R.id.container_globalsearch_vod_info, MovieDetailFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.focusBlocker.requestFocus()
        binding.containerGlobalsearchVodInfo.visibility = View.VISIBLE
    }

    fun openSeriesDetailFragment() {
        helpViewModel.isSearchContainerOpened = true
        val transaction = parentFragmentManager.beginTransaction()
        transaction.add(R.id.container_globalsearch_vod_info, SeriesDetailFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.focusBlocker.requestFocus()
        binding.containerGlobalsearchVodInfo.visibility = View.VISIBLE
    }

    fun focusToTextView() {
        when (selectedGlobalSearchCategory) {
            GlobalSearchMainCategory.TV -> binding.tvCatTv.requestFocus()
            GlobalSearchMainCategory.MOVIES -> binding.tvCatMovies.requestFocus()
            GlobalSearchMainCategory.SERIES -> binding.tvCatSeries.requestFocus()
            GlobalSearchMainCategory.PROGRAMS -> binding.tvCatEpg.requestFocus()
            else -> {} // optional: nichts tun oder Default setzen
        }
    }


    fun focusToPlaylist() {
        if (playlistAdapter.currentList.isNotEmpty()) {
            binding.recyclerPlaylists.requestFocus()
        } else {
            return
        }
    }

    fun goToMainMenu() {
        if (binding.recyclerSearchhistory.isVisible) {
            binding.recyclerSearchhistory.visibility = View.INVISIBLE
        }
        (requireActivity() as? MainActivity)?.openMenu()
        (requireActivity() as? MainActivity)?.lastSelectFocus()
    }

    fun closeFragment() {
        playlistAdapter.submitList(listOf())
        helpViewModel.resetGlobalSearchData()
        parentFragmentManager.popBackStack()
        (requireActivity() as? MainActivity)?.openMenu()
    }

    private val backStackListener = FragmentManager.OnBackStackChangedListener {
        if (isAdded && parentFragmentManager.fragments.lastOrNull() == this && helpViewModel.isSearchContainerOpened) {
            if (globalSearchItemAdapter.currentList.isNotEmpty()) {
                helpViewModel.isSearchContainerOpened = false
                binding.recyclerItems.requestFocus()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        parentFragmentManager.removeOnBackStackChangedListener(backStackListener)
        _binding = null  // 👉 View wird hier freigegeben
    }

    override fun onDestroy() {
        super.onDestroy()
        playlistAdapter.submitList(emptyList())
        helpViewModel.resetGlobalSearchData()
        helpViewModel.cancelGlobalSearchJob()
        helpViewModel.selectedGlobalSearchCategory = null
        // kein _binding = null hier!
    }

    override fun onLowMemory() {
        super.onLowMemory()
        // Speicher knapp: abbrechen, aufräumen etc.
        helpViewModel.cancelGlobalSearchJob()
    }

}