package com.example.mj_player_tv.ui

import android.app.AlertDialog
import android.content.Context
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
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
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
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentSearchGlobalBinding
import com.example.mj_player_tv.ui.adapter.GlobalSearchEpgAdapter
import com.example.mj_player_tv.ui.adapter.GlobalSearchEpgListAdapter
import com.example.mj_player_tv.ui.adapter.GlobalSearchHistoryAdapter
import com.example.mj_player_tv.ui.adapter.GlobalSearchMoviesAdapter
import com.example.mj_player_tv.ui.adapter.GlobalSearchSeriesAdapter
import com.example.mj_player_tv.ui.adapter.GlobalSearchTvChannelsAdapter
import com.example.mj_player_tv.ui.adapter.GlobalSearchPlaylistAdapter
import com.example.mj_player_tv.ui.adapter.MoviesAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.PlexViewModel
import com.example.mj_player_tv.viewmodel.PlexViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import com.rubensousa.dpadrecyclerview.FocusableDirection
import com.rubensousa.dpadrecyclerview.spacing.DpadGridSpacingDecoration
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
class GlobalSearchFragment : Fragment(R.layout.fragment_search_global) {

    private var _binding: FragmentSearchGlobalBinding? = null

    private val settingsBox = ObjectBox.store.boxFor(Settings::class.java)

    private val accountBox = ObjectBox.store.boxFor(Accounts::class.java)

    private lateinit var searchHistoryAdapter: GlobalSearchHistoryAdapter

    private lateinit var playlistAdapter: GlobalSearchPlaylistAdapter
    private var tvChannelsAdapter: GlobalSearchTvChannelsAdapter? = null
    private var moviesAdapter: GlobalSearchMoviesAdapter? = null
    private var seriesAdapter: GlobalSearchSeriesAdapter? = null
    private var epgAdapter: GlobalSearchEpgAdapter? = null
    private var epgListAdapter: GlobalSearchEpgListAdapter? = null

    private var isFirstOpenGlobalSearch = true

    private var lastSearchQuery = ""

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
        prepareTvChannelsRecyclerView()
        prepareMoviesRecyclerView()
        prepareSeriesRecyclerView()
        prepareEpgTvChannelsRecyclerView()
        prepareEpgListRecyclerView()

        showSearchHistory()

        helpViewModel.settings?.let {
            binding.cbFilterSearch.isChecked = helpViewModel.settings!!.globalSearchFilteredCategories
        }

        parentFragmentManager.addOnBackStackChangedListener(backStackListener)
        binding.editTextSearch.requestFocus()
        (requireActivity() as? MainActivity)?.hideMenu()
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.editTextSearch, InputMethodManager.SHOW_IMPLICIT)

        binding.editTextSearch.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                closeFragment()
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

                val currentText = binding.editTextSearch.text.toString()
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
               binding.ivSettings.visibility = View.VISIBLE
               binding.backgroundDarker.visibility = View.VISIBLE
           }
        }

        binding.ivSettings.setOnClickListener {
            binding.linLayoutSearchoptionsMenu.visibility = View.VISIBLE
            binding.relLayoutFiltersearch.requestFocus()
        }

        binding.ivSettings.setOnKeyListener { _, keyCode, event ->
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
                binding.editTextSearch.requestFocus()
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
                binding.editTextSearch.requestFocus()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }


        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.playlistsWithTvChannels.collect {
                if (it.isNotEmpty()) {
                    binding.tvCatTv.visibility = View.VISIBLE
                    if (helpViewModel.currentSelectedGlobalSearchCategory == "TV") {
                        playlistAdapter.submitList(it.keys.sortedBy { it.name }.toMutableList())
                        if (binding.tvCatTv.isFocused ||
                            binding.editTextSearch.isFocused ||
                            binding.ivSettings.isFocused ||
                            binding.relLayoutFiltersearch.isFocused ||
                            binding.relLayoutClearhistory.isFocused) {
                            if (playlistAdapter.currentList.isNotEmpty()) {
                                binding.recyclerItems.post {
                                    binding.recyclerItems.setSelectedPosition(0)
                                    val firstAccount = playlistAdapter.currentList.firstOrNull()
                                    Log.d("GS SELECT TV ACCOUNT", "${firstAccount?.name}")
                                    if (firstAccount != null) {
                                        showFocusedTvChannels(playlistAdapter.currentList.first())
                                    }
                                }
                            }
                        }
                    }
                    if (isFirstOpenGlobalSearch) {
                        binding.ivShowSearch.visibility = View.VISIBLE
                        if (binding.recyclerItems.adapter != tvChannelsAdapter) {
                            binding.recyclerItems.adapter = tvChannelsAdapter
                        }
                        isFirstOpenGlobalSearch = false
                        helpViewModel.currentSelectedGlobalSearchCategory = "TV"
                        binding.recyclerPlaylists.visibility = View.VISIBLE
                        focusToTextView()
                        showFocusedTvChannels(it.keys.first())
                        playlistAdapter.submitList(it.keys.sortedBy { it.name }.toMutableList())
                    }
                } else {
                    binding.tvCatTv.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.playlistsWithPrograms.collect {
                if (it.isNotEmpty()) {
                    if (helpViewModel.currentSelectedGlobalSearchCategory == "EPG") {
                        playlistAdapter.submitList(it.keys.sortedBy { it.name }.toMutableList())
                    }
                    binding.tvCatEpg.visibility = View.VISIBLE
                    if (isFirstOpenGlobalSearch) {
                        binding.ivShowSearch.visibility = View.VISIBLE
                        isFirstOpenGlobalSearch = false
                        helpViewModel.currentSelectedGlobalSearchCategory = "EPG"
                        binding.recyclerPlaylists.visibility = View.VISIBLE
                        focusToTextView()
                        showTvChannelsWithEpg(it.keys.first())
                        playlistAdapter.submitList(it.keys.sortedBy { it.name }.toMutableList())
                    }
                } else {
                    binding.tvCatEpg.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.playlistsWithMovies.collect {
                if (it.isNotEmpty()) {
                    if (helpViewModel.currentSelectedGlobalSearchCategory == "MOVIE") {
                        playlistAdapter.submitList(it.keys.sortedBy { it.name }.toMutableList())
                    }
                    binding.tvCatMovies.visibility = View.VISIBLE
                    if (isFirstOpenGlobalSearch) {
                        binding.ivShowSearch.visibility = View.VISIBLE
                        if (binding.recyclerItems.adapter != moviesAdapter) {
                            binding.recyclerItems.adapter = moviesAdapter
                        }
                        isFirstOpenGlobalSearch = false
                        helpViewModel.currentSelectedGlobalSearchCategory = "MOVIE"
                        binding.recyclerPlaylists.visibility = View.VISIBLE
                        focusToTextView()
                        showFocusedMovies(it.keys.first())
                        playlistAdapter.submitList(it.keys.sortedBy { it.name }.toMutableList())
                    }
                } else {
                    binding.tvCatMovies.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.playlistsWithSeries.collect {
                if (it.isNotEmpty()) {
                    if (helpViewModel.currentSelectedGlobalSearchCategory == "SERIE") {
                        playlistAdapter.submitList(it.keys.sortedBy { it.name }.toMutableList())
                    }
                    binding.tvCatSeries.visibility = View.VISIBLE
                    if (isFirstOpenGlobalSearch) {
                        binding.ivShowSearch.visibility = View.VISIBLE
                        if (binding.recyclerItems.adapter != seriesAdapter) {
                            binding.recyclerItems.adapter = seriesAdapter
                        }
                        isFirstOpenGlobalSearch = false
                        helpViewModel.currentSelectedGlobalSearchCategory = "SERIE"
                        binding.recyclerPlaylists.visibility = View.VISIBLE
                        focusToTextView()
                        showFocusedSeries(it.keys.first())
                        playlistAdapter.submitList(it.keys.sortedBy { it.name }.toMutableList())
                    }
                } else {
                    binding.tvCatSeries.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.isSearching.collect { searching ->
                if (searching) {
                    binding.tvNodatafound.visibility = View.GONE
                    binding.progressBar.visibility = View.VISIBLE
                } else {
                    binding.progressBar.visibility = View.GONE
                    if (helpViewModel.playlistsWithTvChannels.value.isEmpty() &&
                        helpViewModel.playlistsWithMovies.value.isEmpty() &&
                        helpViewModel.playlistsWithSeries.value.isEmpty() &&
                        helpViewModel.playlistsWithPrograms.value.isEmpty() && !isFirstOpenGlobalSearch) {
                        binding.tvNodatafound.visibility = View.VISIBLE
                    }
                }
            }
        }


        binding.tvCatTv.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && helpViewModel.currentSelectedGlobalSearchCategory != "TV" && !helpViewModel.isSearchContainerOpened) {
                viewLifecycleOwner.lifecycleScope.launch {
                    helpViewModel.currentSelectedGlobalSearchCategory = "TV"
                    playlistAdapter.submitList(listOf())
                    binding.tvCatTv.isSelected = true
                    binding.tvCatMovies.isSelected = false
                    binding.tvCatEpg.isSelected = false
                    binding.tvCatSeries.isSelected = false
                    delay(500)
                    playlistAdapter.submitList(helpViewModel.playlistsWithTvChannels.value.keys.sortedBy { it.name }
                        .toMutableList())
                    if (binding.recyclerItems.adapter != tvChannelsAdapter) {
                        binding.recyclerItems.adapter = tvChannelsAdapter
                    }
                    if (binding.constEpg.visibility == View.VISIBLE) {
                        binding.recyclerItems.visibility = View.VISIBLE
                        binding.constEpg.visibility = View.GONE
                    }
                    binding.recyclerPlaylists.post {
                        binding.recyclerPlaylists.setSelectedPosition(0)
                        val firstAccount = playlistAdapter.currentList.firstOrNull()
                        if (firstAccount != null) {
                            showFocusedTvChannels(firstAccount)
                        }
                    }
                    helpViewModel.currentGlobalSearchProgramPlaylist = null
                    helpViewModel.currentGlobalSearchSeriePlaylist = null
                    helpViewModel.currentGlobalSearchMoviePlaylist = null
                    binding.tvSelectedChannel.visibility = View.INVISIBLE
                    binding.tvSelectedChannel.text = ""
                }
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
            return@setOnKeyListener false
        }

        binding.tvCatMovies.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && helpViewModel.currentSelectedGlobalSearchCategory != "MOVIE" && !helpViewModel.isSearchContainerOpened) {
                viewLifecycleOwner.lifecycleScope.launch {
                    playlistAdapter.submitList(listOf())
                    helpViewModel.currentSelectedGlobalSearchCategory = "MOVIE"
                    binding.tvCatTv.isSelected = false
                    binding.tvCatMovies.isSelected = true
                    binding.tvCatEpg.isSelected = false
                    binding.tvCatSeries.isSelected = false
                    delay(500)
                    playlistAdapter.submitList(helpViewModel.playlistsWithMovies.value.keys.sortedBy { it.name }
                        .toMutableList())
                    // Hier prüfen, ob der Adapter bereits gesetzt ist
                    if (binding.recyclerItems.adapter != moviesAdapter) {
                        binding.recyclerItems.adapter = moviesAdapter
                    }
                    if (binding.constEpg.visibility == View.VISIBLE) {
                        binding.recyclerItems.visibility = View.VISIBLE
                        binding.constEpg.visibility = View.GONE
                    }
                    binding.recyclerPlaylists.post {
                        binding.recyclerPlaylists.setSelectedPosition(0)
                        val firstAccount = playlistAdapter.currentList.firstOrNull()
                        if (firstAccount != null) {
                            showFocusedMovies(firstAccount)
                        }
                    }
                    helpViewModel.currentGlobalSearchTvPlaylist = null
                    helpViewModel.currentGlobalSearchSeriePlaylist = null
                    helpViewModel.currentGlobalSearchProgramPlaylist = null
                    binding.tvSelectedChannel.visibility = View.INVISIBLE
                    binding.tvSelectedChannel.text = ""
                }
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
            return@setOnKeyListener false
        }

        binding.tvCatSeries.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && helpViewModel.currentSelectedGlobalSearchCategory != "SERIE" && !helpViewModel.isSearchContainerOpened) {
                viewLifecycleOwner.lifecycleScope.launch {
                    Log.d("EPG VISIBILITY", "visi: ${binding.constEpg.visibility}")
                    helpViewModel.currentSelectedGlobalSearchCategory = "SERIE"
                    playlistAdapter.submitList(listOf())
                    binding.tvCatTv.isSelected = false
                    binding.tvCatMovies.isSelected = false
                    binding.tvCatEpg.isSelected = false
                    binding.tvCatSeries.isSelected = true
                    delay(500)
                    playlistAdapter.submitList(helpViewModel.playlistsWithSeries.value.keys.sortedBy { it.name }.toMutableList())
                    binding.recyclerItems.visibility = View.VISIBLE
                    binding.recyclerPlaylists.post {
                        binding.recyclerPlaylists.setSelectedPosition(0)
                        val firstAccount = playlistAdapter.currentList.firstOrNull()
                        if (firstAccount != null) {
                            showFocusedSeries(firstAccount)
                        }
                    }
                    if (binding.recyclerItems.adapter != seriesAdapter) {
                        binding.recyclerItems.adapter = seriesAdapter
                    }
                    if (binding.constEpg.visibility == View.VISIBLE) {
                        binding.constEpg.visibility = View.GONE
                    }
                    helpViewModel.currentGlobalSearchTvPlaylist = null
                    helpViewModel.currentGlobalSearchMoviePlaylist = null
                    helpViewModel.currentGlobalSearchProgramPlaylist = null
                    binding.tvSelectedChannel.visibility = View.INVISIBLE
                    binding.tvSelectedChannel.text = ""
                }
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
            return@setOnKeyListener false
        }

        binding.tvCatEpg.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && helpViewModel.currentSelectedGlobalSearchCategory != "EPG" && !helpViewModel.isSearchContainerOpened) {
                viewLifecycleOwner.lifecycleScope.launch {
                    binding.recyclerItems.visibility = View.GONE
                    Log.d("EPG VISIBILITY", "visi 2: ${binding.constEpg.visibility}")
                    helpViewModel.currentSelectedGlobalSearchCategory = "EPG"
                    playlistAdapter.submitList(listOf())
                    binding.tvCatTv.isSelected = false
                    binding.tvCatMovies.isSelected = false
                    binding.tvCatSeries.isSelected = false
                    binding.tvCatEpg.isSelected = true
                    delay(500)
                    playlistAdapter.submitList(helpViewModel.playlistsWithPrograms.value.keys.sortedBy { it.name }
                        .toMutableList())
                    binding.constEpg.visibility = View.VISIBLE
                    binding.recyclerPlaylists.post {
                        binding.recyclerPlaylists.setSelectedPosition(0)
                        val firstAccount = playlistAdapter.currentList.firstOrNull()
                        if (firstAccount != null) {
                            showTvChannelsWithEpg(firstAccount)
                        }
                    }
                    helpViewModel.currentGlobalSearchTvPlaylist = null
                    helpViewModel.currentGlobalSearchMoviePlaylist = null
                    helpViewModel.currentGlobalSearchSeriePlaylist = null
                }
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
            return@setOnKeyListener false
        }
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
        binding.ivSettings.visibility = View.GONE
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
        binding.ivSettings.requestFocus()
    }

    fun showFocusedTvChannels(tvPlaylistAccount: Accounts) {
        if (tvPlaylistAccount.id != helpViewModel.currentGlobalSearchTvPlaylist?.id) {
            val oldPosition = playlistAdapter.currentList.indexOf(helpViewModel.currentGlobalSearchTvPlaylist)
            val newPosition = playlistAdapter.currentList.indexOf(tvPlaylistAccount)
            helpViewModel.currentGlobalSearchTvPlaylist = tvPlaylistAccount
            playlistAdapter.notifyItemChanged(oldPosition)
            playlistAdapter.notifyItemChanged(newPosition)
            viewLifecycleOwner.lifecycleScope.launch {
                // Die passende List<TvChannels> aus dem StateFlow holen
                val channels = helpViewModel.playlistsWithTvChannels.value[tvPlaylistAccount] ?: emptyList()
                tvChannelsAdapter?.submitList(listOf())
                if (channels.isNotEmpty()) {

                    binding.recyclerItems.visibility = View.VISIBLE
                    // Die Channels in den zweiten RecyclerView laden
                    tvChannelsAdapter?.submitList(channels)
                } else {
                    binding.recyclerItems.visibility = View.GONE
                }
            }
        }
    }

    fun showFocusedMovies(moviesPlaylistAccount: Accounts) {
        if (moviesPlaylistAccount.id != helpViewModel.currentGlobalSearchMoviePlaylist?.id) {
            val oldPosition = playlistAdapter.currentList.indexOf(helpViewModel.currentGlobalSearchMoviePlaylist)
            val newPosition = playlistAdapter.currentList.indexOf(moviesPlaylistAccount)
            helpViewModel.currentGlobalSearchMoviePlaylist = moviesPlaylistAccount
            playlistAdapter.notifyItemChanged(oldPosition)
            playlistAdapter.notifyItemChanged(newPosition)
            viewLifecycleOwner.lifecycleScope.launch {
                // Die passende List<TvChannels> aus dem StateFlow holen
                val movies = helpViewModel.playlistsWithMovies.value[moviesPlaylistAccount] ?: emptyList()
                moviesAdapter?.submitList(listOf())
                if (movies.isNotEmpty()) {
                    movies.forEach { channel ->
                        Log.d("GLOBAL SEARCH MOVIES", "PL: ${moviesPlaylistAccount.name} CHANNELS: ${channel.movieName}")
                    }
                    binding.recyclerItems.visibility = View.VISIBLE
                    // Die Channels in den zweiten RecyclerView laden
                    moviesAdapter?.submitList(movies)
                } else {
                    binding.recyclerItems.visibility = View.GONE
                }
            }
        }
    }

    fun showFocusedSeries(seriesPlaylistAccount: Accounts) {
        if (seriesPlaylistAccount.id != helpViewModel.currentGlobalSearchSeriePlaylist?.id) {
            val oldPosition = playlistAdapter.currentList.indexOf(helpViewModel.currentGlobalSearchSeriePlaylist)
            val newPosition = playlistAdapter.currentList.indexOf(seriesPlaylistAccount)
            helpViewModel.currentGlobalSearchSeriePlaylist = seriesPlaylistAccount
            playlistAdapter.notifyItemChanged(oldPosition)
            playlistAdapter.notifyItemChanged(newPosition)
            viewLifecycleOwner.lifecycleScope.launch {
                // Die passende List<TvChannels> aus dem StateFlow holen
                val series = helpViewModel.playlistsWithSeries.value[seriesPlaylistAccount] ?: emptyList()
                seriesAdapter?.submitList(listOf())
                if (series.isNotEmpty()) {
                    series.forEach { channel ->
                        Log.d("GLOBAL SEARCH SERIES", "PL: ${seriesPlaylistAccount.name} CHANNELS: ${channel.seriesName}")
                    }
                    binding.recyclerItems.visibility = View.VISIBLE
                    // Die Channels in den zweiten RecyclerView laden
                    seriesAdapter?.submitList(series)
                } else {
                    binding.recyclerItems.visibility = View.GONE
                }
            }
        }
    }

    fun showTvChannelsWithEpg(programPlaylistAccount: Accounts) {
        if (programPlaylistAccount.id != helpViewModel.currentGlobalSearchProgramPlaylist?.id) {
            val oldPosition = playlistAdapter.currentList.indexOf(helpViewModel.currentGlobalSearchProgramPlaylist)
            val newPosition = playlistAdapter.currentList.indexOf(programPlaylistAccount)
            helpViewModel.currentGlobalSearchProgramPlaylist = programPlaylistAccount
            playlistAdapter.notifyItemChanged(oldPosition)
            playlistAdapter.notifyItemChanged(newPosition)
            viewLifecycleOwner.lifecycleScope.launch {
                // Die passende List<TvChannels> aus dem StateFlow holen
                val tvchannelsWithEpg = helpViewModel.playlistsWithPrograms.value[programPlaylistAccount]?.keys ?: emptyList()
                epgAdapter?.submitList(listOf())
                if (tvchannelsWithEpg.isNotEmpty()) {

                    binding.recyclerItems.visibility = View.GONE
                    binding.recyclerEpg.visibility = View.VISIBLE
                    binding.constEpg.visibility = View.VISIBLE
                    // Die Channels in den zweiten RecyclerView laden
                    epgAdapter?.submitList(tvchannelsWithEpg.toMutableList())
                    showEpgList(tvchannelsWithEpg.first())
                } else {
                    binding.recyclerEpg.visibility = View.GONE
                }
            }
        }
    }

    fun showEpgList(tvchannelPos: ChannelPositions) {
        val relatedShowsForChannel = helpViewModel.playlistsWithPrograms.value[helpViewModel.currentGlobalSearchProgramPlaylist]?.get(tvchannelPos)?.sortedBy { it.startTimestamp } ?: emptyList()
        if (relatedShowsForChannel.isNotEmpty()) {
            binding.tvSelectedChannel.visibility = View.VISIBLE
            binding.tvSelectedChannel.text = tvchannelPos.tvchannel.target.showingName
            binding.recyclerEpglist.visibility = View.VISIBLE
            epgListAdapter?.selectedChannel = tvchannelPos
            epgListAdapter?.submitList(relatedShowsForChannel)
            showDetailEpg(relatedShowsForChannel.first())
        } else {
            binding.tvSelectedChannel.visibility = View.INVISIBLE
            binding.tvSelectedChannel.text = ""
        }
    }

    fun showDetailEpg(epgDataOB: EpgDataOB) {
        if (binding.relLayoutEpgDetail.visibility == View.GONE) {
            binding.relLayoutEpgDetail.visibility = View.VISIBLE
        }
        binding.tvDetailepgName.text = epgDataOB.name
        if (epgDataOB.sub_title.isNotEmpty()) {
            binding.tvDetailepgSubtitle.visibility = View.VISIBLE
            binding.tvDetailepgSubtitle.text = epgDataOB.sub_title
        } else {
            binding.tvDetailepgSubtitle.visibility = View.GONE
        }
        binding.tvDetailepgDescription.text = epgDataOB.descr
    }

    private fun searchFor(searchTerm: String) {
        viewLifecycleOwner.lifecycleScope.launch {
                playlistAdapter.submitList(listOf())
                tvChannelsAdapter?.submitList(listOf())
                moviesAdapter?.submitList(listOf())
                seriesAdapter?.submitList(listOf())
                epgAdapter?.submitList(listOf())
                epgListAdapter?.submitList(listOf())
                binding.tvCatTv.visibility = View.GONE
                binding.tvCatMovies.visibility = View.GONE
                binding.tvCatSeries.visibility = View.GONE
                binding.tvCatEpg.visibility = View.GONE

                binding.backgroundDarker.visibility = View.GONE
                hideSearchBarShowArrow()
                saveSearchTerm(searchTerm)
                binding.progressBar.visibility = View.VISIBLE
                helpViewModel.resetGlobalSearchData()

                helpViewModel.currentGlobalSearchTvPlaylist = null
                helpViewModel.currentGlobalSearchMoviePlaylist = null
                helpViewModel.currentGlobalSearchSeriePlaylist = null
                helpViewModel.currentGlobalSearchProgramPlaylist = null
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
                searchHistoryAdapter.submitList(it.searchString)
            }
        } else {
            helpViewModel.settings?.searchString?.add(0, searchTerm)
            helpViewModel.settings?.let {
                settingsBox.put(it)
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

    private fun prepareTvChannelsRecyclerView() {
        tvChannelsAdapter = GlobalSearchTvChannelsAdapter(onChannelClickListener, this, helpViewModel)
        binding.recyclerItems.apply {
            adapter = tvChannelsAdapter
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private fun prepareMoviesRecyclerView() {
        moviesAdapter = GlobalSearchMoviesAdapter(onMovieClickListener, this, helpViewModel)
        binding.recyclerItems.apply {
            adapter = moviesAdapter
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private fun prepareSeriesRecyclerView() {
        seriesAdapter = GlobalSearchSeriesAdapter(onSeriesClickListener, this, helpViewModel)
        binding.recyclerItems.apply {
            adapter = seriesAdapter
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private fun prepareEpgTvChannelsRecyclerView() {
        epgAdapter = GlobalSearchEpgAdapter(this, helpViewModel)
        binding.recyclerEpg.apply {
            adapter = epgAdapter
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private fun prepareEpgListRecyclerView() {
        epgListAdapter = GlobalSearchEpgListAdapter(onEpgDataListener, this, helpViewModel)
        binding.recyclerEpglist.apply {
            adapter = epgListAdapter
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private val onSearchHistoryClickListener = GlobalSearchHistoryAdapter.OnClickListener {
        if (it.isNotEmpty()) {
            lastSearchQuery = it
            Log.d("EDITTEXTNAME","START SEARCH HISTORY: $lastSearchQuery")
            searchFor(it)
        }
    }

    private val onSearchHistoryLongClickListener = GlobalSearchHistoryAdapter.OnLongClickListener { searchTerm ->
        AlertDialog.Builder(requireContext())
            .setMessage("Delete search term?")
            .setPositiveButton("Yes") { dialog, _ ->
                helpViewModel.settings?.let {
                    Log.d("HISTORYTERMS", "OLD: ${it.searchString}")
                    it.searchString.remove(searchTerm)
                    settingsBox.put(it)
                    Log.d("HISTORYTERMS", "NEW: ${it.searchString}")
                    val newList = it.searchString.toList()
                    Log.d("HISTORYTERMS", "NEW 2: ${newList}")
                    searchHistoryAdapter.submitList(newList)
                }

                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
                binding.relLayoutClearhistory.requestFocus()
            }
            .show()
    }

    private val onChannelClickListener = GlobalSearchTvChannelsAdapter.OnClickListener {
        parentFragmentManager.popBackStack()
        (requireActivity() as? MainActivity)?.openTvChannelsFragmentFromGlobalSearch(it)
    }

    private val onMovieClickListener = GlobalSearchMoviesAdapter.OnClickListener { movie ->
        val account = movie.accountId?.let {
            accountBox.get(it)
        }
        if (account != null) {
            if (account.isXtream) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val xtreamMovie = xtreamViewModel.getXtreamMovieDetails(movie, account)
                    helpViewModel.currentFocusedMovie = xtreamMovie
                    helpViewModel.currentMovieAccount = account
                    openMovieDetailFragment()
                }
            } else {
                helpViewModel.currentMovieAccount = account
                helpViewModel.currentFocusedMovie = movie
                openMovieDetailFragment()
            }
        }
    }

    private val onSeriesClickListener = GlobalSearchSeriesAdapter.OnClickListener { serie ->
        val account = serie.accountId?.let {
            accountBox.get(it)
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
                    helpViewModel.currentSeriesAccount = account
                    openSeriesDetailFragment()
                }
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    stalkerViewModel.seriesDetailData.postValue(mutableListOf())
                    stalkerViewModel.getSeriesDetail(serie, account)
                    helpViewModel.currentFocusedSerie = serie
                    stalkerViewModel.seriesDetailData.observe(viewLifecycleOwner) { seasons ->
                        serie.totalSeasons = seasons.size
                        helpViewModel.focusedSeasons = seasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                            .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE }).toMutableList()
                        helpViewModel.focusedEpisodes = stalkerViewModel.episodesList.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })).toMutableList()
                    }
                    helpViewModel.currentSeriesAccount = account
                    openSeriesDetailFragment()
                }
            }
        }
    }

    private val onEpgDataListener = GlobalSearchEpgListAdapter.OnClickListener {

    }

    fun openMovieDetailFragment() {
        helpViewModel.isSearchContainerOpened = true
        val transaction = parentFragmentManager.beginTransaction()
        transaction.add(R.id.container_globalsearch_vod_info, MovieDetailFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.containerGlobalsearchVodInfo.visibility = View.VISIBLE
    }

    fun openSeriesDetailFragment() {
        helpViewModel.isSearchContainerOpened = true
        val transaction = parentFragmentManager.beginTransaction()
        transaction.add(R.id.container_globalsearch_vod_info, SeriesDetailFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.containerGlobalsearchVodInfo.visibility = View.VISIBLE
    }


    fun focusToEpgList() {
        if (!epgListAdapter?.currentList.isNullOrEmpty()) {
            binding.recyclerEpglist.requestFocus()
        } else {
            return
        }
    }

    fun focusToEpgChannels() {
        if (!epgAdapter?.currentList.isNullOrEmpty()) {
            binding.recyclerEpg.requestFocus()
        } else {
            return
        }
    }

    fun focusToTextView() {
        if (helpViewModel.currentSelectedGlobalSearchCategory == "TV") {
            binding.tvCatTv.requestFocus()
        } else if (helpViewModel.currentSelectedGlobalSearchCategory == "MOVIE") {
            binding.tvCatMovies.requestFocus()
        } else if (helpViewModel.currentSelectedGlobalSearchCategory == "SERIE") {
            binding.tvCatSeries.requestFocus()
        } else if (helpViewModel.currentSelectedGlobalSearchCategory == "EPG") {
            binding.tvCatEpg.requestFocus()
        } else {
            return
        }
    }

    fun focusToPlaylist() {
        if (playlistAdapter.currentList.isNotEmpty()) {
            binding.recyclerPlaylists.requestFocus()
        } else {
            return
        }
    }

    fun focusToItems() {
        if (helpViewModel.currentSelectedGlobalSearchCategory == "TV") {
            if (!tvChannelsAdapter?.currentList.isNullOrEmpty()) {
                binding.recyclerItems.requestFocus()
            }
        } else if (helpViewModel.currentSelectedGlobalSearchCategory == "MOVIE") {
            if (!moviesAdapter?.currentList.isNullOrEmpty()) {
                binding.recyclerItems.requestFocus()
            }
        } else if (helpViewModel.currentSelectedGlobalSearchCategory == "SERIE") {
            if (!seriesAdapter?.currentList.isNullOrEmpty()) {
                binding.recyclerItems.requestFocus()
            }
        } else {
            if (!epgAdapter?.currentList.isNullOrEmpty()) {
                binding.recyclerEpg.requestFocus()
            } else {
                return
            }
        }
    }

    fun closeFragment() {
        moviesAdapter = null
        seriesAdapter = null
        tvChannelsAdapter = null
        epgAdapter = null
        playlistAdapter.submitList(listOf())
        helpViewModel.resetGlobalSearchData()
        parentFragmentManager.popBackStack()
        (requireActivity() as? MainActivity)?.openMenu()
    }

    private val backStackListener = FragmentManager.OnBackStackChangedListener {
        if (isAdded && parentFragmentManager.fragments.lastOrNull() == this && helpViewModel.isSearchContainerOpened) {
            if (helpViewModel.currentSelectedGlobalSearchCategory == "EPG") {
                binding.recyclerEpglist.requestFocus()
            } else {
                focusToItems()
            }
            helpViewModel.isSearchContainerOpened = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        parentFragmentManager.removeOnBackStackChangedListener(backStackListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        moviesAdapter = null
        seriesAdapter = null
        tvChannelsAdapter = null
        epgAdapter = null
        playlistAdapter.submitList(listOf())
        helpViewModel.resetGlobalSearchData()
        _binding = null
    }
}