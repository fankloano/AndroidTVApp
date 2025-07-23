package com.example.mj_player_tv.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.EpgSourceChannel
import com.example.mj_player_tv.database.entity.EpgSourcePositions
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentAssignChannelToEpgBinding
import com.example.mj_player_tv.ui.adapter.EpgChannelListAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.rubensousa.dpadrecyclerview.ViewHolderTask
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.commons.text.similarity.LevenshteinDistance
import kotlin.system.exitProcess


@UnstableApi
class AssingChannelToEpgFragment: Fragment(R.layout.fragment_assign_channel_to_epg), View.OnFocusChangeListener {

    private var _binding: FragmentAssignChannelToEpgBinding? = null

    private val binding get() = _binding!!

    private lateinit var epgChannelListAdapter: EpgChannelListAdapter

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val tvChannBox: Box<TvChannelOB> = ObjectBox.store.boxFor(TvChannelOB::class.java)

    private val epgChannelBox: Box<EpgSourceChannel> = ObjectBox.store.boxFor(EpgSourceChannel::class.java)

    private val epgSourceBox: Box<EpgSource> = ObjectBox.store.boxFor(EpgSource::class.java)

    private val epgSourcePosBox = ObjectBox.store.boxFor(EpgSourcePositions::class.java)

    private var sortedBy = 1

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
        _binding = FragmentAssignChannelToEpgBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            closeFragment()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvEpgList.onFocusChangeListener = this

        binding.btnSearch.onFocusChangeListener = this
        binding.btnEpglist.onFocusChangeListener = this
        binding.btnCancelEpg.onFocusChangeListener = this
        binding.editTextSearch.onFocusChangeListener = this


        if (helpViewModel.currentAssignEpgChannel != null && helpViewModel.currentFocusedTvAccount != null) {
            helpViewModel.currentAssignChannelPosition = helpViewModel.currentFocusedChannPosition
            binding.assignepgProgressBar.visibility = View.VISIBLE
            binding.rvEpgList.visibility = View.INVISIBLE
            binding.playlistEpgSourcesContainer.visibility = View.GONE
            val epgChannelList: MutableList<EpgSourceChannel> = mutableListOf()
            val currentAccount = accountBox.get(helpViewModel.currentFocusedTvAccount!!.id)
            viewLifecycleOwner.lifecycleScope.launch {
                currentAccount.epgsources.filter { it.isSelected }.sortedBy { it.position }.forEach {
                    epgChannelList.addAll(it.relatedepgsource.target.epgchs.sortedBy { it.name.lowercase() })
                }
                if (epgChannelList.isNotEmpty() && helpViewModel.currentAssignEpgChannel != null) {
                    helpViewModel.showAllEpgChannelSources = true
                    if (epgChannelList.any {
                            it.chEpgId == helpViewModel.currentAssignEpgChannel!!.linkedEpgChannel?.target?.chEpgId
                        }) {
                        prepareRecyclerView(helpViewModel.currentAssignEpgChannel!!)
                        epgChannelListAdapter.submitList(epgChannelList.sortedBy { it.name.lowercase() })
                        val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                        if (mainFragment is TvChannelsFragment) {
                            mainFragment.setFocusedAssignEpgChannel()
                        }
                        val epgChannel =
                            epgChannelListAdapter.currentList.find { it.chEpgId == helpViewModel.currentAssignEpgChannel!!.linkedEpgChannel?.target?.chEpgId }
                        val epgChPosition =
                            epgChannelListAdapter.currentList.indexOf(epgChannel)
                        binding.rvEpgList.post {
                            binding.rvEpgList.setSelectedPosition(epgChPosition)
                        }
                        binding.assignepgProgressBar.visibility = View.INVISIBLE
                        binding.rvEpgList.visibility = View.VISIBLE
                        binding.rvEpgList.requestFocus()
                    } else {
                        val matchedEpgChannel = findBestMatchEpgChannel(
                            helpViewModel.currentAssignEpgChannel!!.showingName,
                            epgChannelList
                        )
                        if (matchedEpgChannel != null) {
                            prepareRecyclerView(helpViewModel.currentAssignEpgChannel!!)
                            epgChannelListAdapter.submitList(epgChannelList)
                            val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                            if (mainFragment is TvChannelsFragment) {
                                mainFragment.setFocusedAssignEpgChannel()
                            }
                            val epgChPosition =
                                epgChannelListAdapter.currentList.indexOf(matchedEpgChannel)
                            binding.rvEpgList.post {
                                binding.rvEpgList.setSelectedPosition(epgChPosition)
                            }
                            binding.assignepgProgressBar.visibility = View.INVISIBLE
                            binding.rvEpgList.visibility = View.VISIBLE
                            binding.rvEpgList.requestFocus()
                        } else {
                            prepareRecyclerView(helpViewModel.currentAssignEpgChannel!!)
                            epgChannelListAdapter.submitList(epgChannelList)
                            val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                            if (mainFragment is TvChannelsFragment) {
                                mainFragment.setFocusedAssignEpgChannel()
                            }
                            val firstMatchingChannel =
                                epgChannelListAdapter.currentList.firstOrNull {
                                    it.name.startsWith(
                                        helpViewModel.currentAssignEpgChannel!!.showingName.first(),
                                        ignoreCase = true
                                    )
                                }
                            if (firstMatchingChannel != null) {
                                val position = epgChannelListAdapter.currentList.indexOf(
                                    firstMatchingChannel
                                )
                                binding.rvEpgList.post {
                                    binding.rvEpgList.setSelectedPosition(position)
                                }
                                binding.assignepgProgressBar.visibility = View.INVISIBLE
                                binding.rvEpgList.visibility = View.VISIBLE
                                binding.rvEpgList.requestFocus()
                            } else {
                                binding.rvEpgList.post {
                                    binding.rvEpgList.setSelectedPosition(0)
                                }
                                binding.assignepgProgressBar.visibility = View.INVISIBLE
                                binding.rvEpgList.visibility = View.VISIBLE
                                binding.rvEpgList.requestFocus()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this@AssingChannelToEpgFragment.requireActivity(), "No EPG-Channels found! Check Playlist settings", Toast.LENGTH_SHORT).show()
                    closeFragment()
                }
            }


            binding.editTextSearch.onFocusChangeListener =
                View.OnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        // EditText ist nicht im Fokus
                        binding.editTextSearch.visibility = View.INVISIBLE
                        binding.tvSourceName.visibility = View.VISIBLE
                        binding.tvEpgsourceName.visibility = View.VISIBLE
                    } else {
                        binding.editTextSearch.visibility = View.VISIBLE
                        binding.tvSourceName.visibility = View.INVISIBLE
                        binding.tvEpgsourceName.visibility = View.INVISIBLE
                        binding.tvContentDescription.visibility = View.INVISIBLE
                        val imm =
                            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(binding.editTextSearch, InputMethodManager.SHOW_IMPLICIT)
                    }
                }

            binding.rvEpgList.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    binding.tvContentDescription.visibility = View.INVISIBLE
                }
            }

            binding.btnSearch.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    binding.tvContentDescription.visibility = View.VISIBLE
                    binding.tvContentDescription.text = "Search Epg Channel"
                } else {
                    binding.tvContentDescription.visibility = View.INVISIBLE
                }
            }

            binding.btnSearch.setOnClickListener {
                binding.tvSourceName.visibility = View.INVISIBLE
                binding.tvEpgsourceName.visibility = View.INVISIBLE
                binding.tvContentDescription.visibility = View.INVISIBLE
                binding.editTextSearch.visibility = View.VISIBLE
                binding.editTextSearch.requestFocus()
                // Fülle das EditText-Feld mit dem aktuellen Sendername
                binding.editTextSearch.setText(helpViewModel.currentAssignEpgChannel!!.showingName)
                // Setze den Cursor ans Ende des Textes
                // Sicherstellen, dass die Cursorposition innerhalb der Textlänge liegt
                binding.editTextSearch.post {
                    val cursorPosition = helpViewModel.currentAssignEpgChannel?.showingName?.length
                    val safeCursorPosition = cursorPosition?.coerceIn(0, helpViewModel.currentAssignEpgChannel?.showingName?.length)
                    if (safeCursorPosition != null) {
                        binding.editTextSearch.setSelection(safeCursorPosition)
                    }
                }

                binding.editTextSearch.addTextChangedListener(object : TextWatcher {
                    private val DELAY: Long = 600 // Zeitverzögerung in Millisekunden
                    private var searchRunnable: Runnable? = null
                    private val handler = Handler(Looper.getMainLooper())

                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }

                    override fun afterTextChanged(s: Editable?) {

                        if (s == null || s.length < 1) return
                        // Entferne das vorherige Suchvorgang Runnable
                        searchRunnable?.let { handler.removeCallbacks(it) }

                        // Erstelle ein neues Runnable, das den Suchvorgang startet
                        searchRunnable = Runnable {
                            val enteredText = s.toString()
                            // Starte den Suchvorgang
                            searchForChannel(enteredText)
                        }

                        // Starte das Runnable nach einer Verzögerung
                        searchRunnable?.let { handler.postDelayed(it, DELAY) }
                    }
                })
            }

            binding.editTextSearch.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                    binding.tvContentDescription.visibility = View.INVISIBLE
                    binding.rvEpgList.requestFocus()
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }

            binding.btnEpglist.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    binding.tvContentDescription.visibility = View.VISIBLE
                    binding.tvContentDescription.text = "Change EPG-Source"
                } else {
                    binding.tvContentDescription.visibility = View.INVISIBLE
                }
            }

            binding.btnEpglist.setOnClickListener {
                binding.overlayLayout.visibility = View.VISIBLE
                changeFragment(AssignEpgToChannelSourcesFragment())
            }

            binding.btnSortEpg.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    binding.tvContentDescription.visibility = View.VISIBLE
                    binding.tvContentDescription.text = "Sort Epg Channel-List"
                }
            }

            binding.btnSortEpg.setOnClickListener {
                val slideIn = AnimationUtils.loadAnimation(this@AssingChannelToEpgFragment.requireActivity(), R.anim.slide_in_right)
                binding.menuAssignEpgOptions.visibility = View.VISIBLE
                binding.menuAssignEpgOptions.startAnimation(slideIn)
                binding.overlayLayout.visibility = View.VISIBLE
                if (sortedBy == 1) {
                    binding.sortbySource.isSelected = false
                    binding.sortbyName.isSelected = true
                    binding.sortbyName.requestFocus()
                } else {
                    binding.sortbyName.isSelected = false
                    binding.sortbySource.isSelected = true
                    binding.sortbySource.requestFocus()
                }
            }

            binding.sortbyName.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    val slideOut = AnimationUtils.loadAnimation(this@AssingChannelToEpgFragment.requireActivity(), R.anim.slide_out_to_right)
                    binding.menuAssignEpgOptions.visibility = View.GONE
                    binding.overlayLayout.visibility = View.GONE
                    binding.menuAssignEpgOptions.startAnimation(slideOut)
                    binding.btnSortEpg.requestFocus()
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }

            binding.sortbyName.setOnClickListener {
                if (sortedBy != 1) {
                    sortedBy = 1
                    binding.sortbySource.isSelected = false
                    binding.sortbyName.isSelected = true
                    // Neue sortierte Liste erstellen
                    val sortedList = epgChannelListAdapter.currentList.toList()
                        .sortedBy { it.name.lowercase() }
                    epgChannelListAdapter.submitList(sortedList)
                    val slideOut = AnimationUtils.loadAnimation(this@AssingChannelToEpgFragment.requireActivity(), R.anim.slide_out_to_right)
                    binding.menuAssignEpgOptions.visibility = View.GONE
                    binding.overlayLayout.visibility = View.GONE
                    binding.menuAssignEpgOptions.startAnimation(slideOut)
                    binding.btnSortEpg.requestFocus()
                }
            }

            binding.sortbySource.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    val slideOut = AnimationUtils.loadAnimation(this@AssingChannelToEpgFragment.requireActivity(), R.anim.slide_out_to_right)
                    binding.menuAssignEpgOptions.visibility = View.GONE
                    binding.overlayLayout.visibility = View.GONE
                    binding.menuAssignEpgOptions.startAnimation(slideOut)
                    binding.btnSortEpg.requestFocus()
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }

            binding.sortbySource.setOnClickListener {
                if (sortedBy != 2) { // Verhindert unnötige Operationen
                    sortedBy = 2
                    binding.sortbyName.isSelected = false
                    binding.sortbySource.isSelected = true
                    // Neue sortierte Liste erstellen
                    val sortedList = epgChannelListAdapter.currentList.toList()
                        .sortedWith(compareBy({ it.epgsource.target.id }, { it.name.lowercase() }))
                    epgChannelListAdapter.submitList(sortedList)
                    val slideOut = AnimationUtils.loadAnimation(this@AssingChannelToEpgFragment.requireActivity(), R.anim.slide_out_to_right)
                    binding.menuAssignEpgOptions.visibility = View.GONE
                    binding.overlayLayout.visibility = View.GONE
                    binding.menuAssignEpgOptions.startAnimation(slideOut)
                    binding.btnSortEpg.requestFocus()
                }
            }


            binding.btnCancelEpg.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    binding.tvContentDescription.visibility = View.VISIBLE
                    binding.tvContentDescription.text = "Reset / use Playlist EPG"
                } else {
                    binding.tvContentDescription.visibility = View.INVISIBLE
                }
            }

            binding.btnCancelEpg.setOnClickListener {
                if (helpViewModel.currentAssignEpgChannel!!.usesExternalEpg) {
                    helpViewModel.currentAssignEpgChannel!!.epgLogo = ""
                    helpViewModel.currentAssignEpgChannel!!.usesExternalEpg = false
                    helpViewModel.currentAssignEpgChannel!!.alwaysUsesExternalEpg = false
                    helpViewModel.currentAssignEpgChannel!!.linkedEpgChannel?.target = null
                } else {
                    if (helpViewModel.currentFocusedTvAccount!!.epgsources.any { it!!.isPlaylistEpg }) {
                        helpViewModel.currentAssignEpgChannel!!.epgChannel?.target = null
                    }
                }
                tvChannBox.put(helpViewModel.currentAssignEpgChannel!!)
                updateChannelList()
                val mainContainer = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                if (mainContainer is TvChannelsFragment) {
                    mainContainer.showEpgPreview(helpViewModel.currentAssignEpgChannel!!)
                }
                epgChannelListAdapter.updateChannel(helpViewModel.currentAssignEpgChannel!!)
                binding.rvEpgList.requestFocus()
            }
        } else {
            if (helpViewModel.currentFocusedChannPosition == null) {
                Toast.makeText(this@AssingChannelToEpgFragment.requireActivity(), "No focused channel!", Toast.LENGTH_LONG).show()
            }
            if (helpViewModel.currentFocusedTvAccount == null) {
                Toast.makeText(this@AssingChannelToEpgFragment.requireActivity(), "No focused account!", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnFilterChannelList.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tvContentDescription.visibility = View.VISIBLE
                binding.tvContentDescription.text = "Filter Channels with no related Epg"
            } else {
                binding.tvContentDescription.visibility = View.INVISIBLE
            }
        }

        binding.btnFilterChannelList.setOnClickListener {
            helpViewModel.assignEpgChannelListFiltered = true
            binding.btnFilterChannelList.visibility = View.GONE
            binding.btnFilterOffChannelList.visibility = View.VISIBLE
            binding.btnFilterOffChannelList.requestFocus()
            val mainContainer = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (mainContainer is TvChannelsFragment) {
                mainContainer.showChannelsWithNoEpg()
            }
        }

        binding.btnFilterOffChannelList.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tvContentDescription.visibility = View.VISIBLE
                binding.tvContentDescription.text = "Show all channels"
            } else {
                binding.tvContentDescription.visibility = View.INVISIBLE
            }
        }

        binding.btnFilterOffChannelList.setOnClickListener {
            helpViewModel.assignEpgChannelListFiltered = false
            binding.btnFilterOffChannelList.visibility = View.GONE
            binding.btnFilterChannelList.visibility = View.VISIBLE
            binding.btnFilterChannelList.requestFocus()
            val mainContainer = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (mainContainer is TvChannelsFragment) {
                mainContainer.showChannelsWithAndWithoutEpg()
            }
        }
    }

    private fun prepareRecyclerView(selectedChannel: TvChannelOB) {
        epgChannelListAdapter = EpgChannelListAdapter(onClickListener, helpViewModel, selectedChannel, this)
        binding.rvEpgList.apply {
            adapter = epgChannelListAdapter
            setFocusOutSideAllowed(false, false)
            setFocusOutAllowed(false, false)
        }
    }

    override fun onFocusChange(p0: View?, hasFocus: Boolean) {
        // Hier wird aufgerufen, wenn sich der Fokus auf einem Menüpunkt ändert
        if (hasFocus) {
            // Aktualisiere die visuelle Hervorhebung basierend auf dem aktuellen Fokus
            if (view != null) {

            }
        }
    }

    private val onClickListener = EpgChannelListAdapter.OnClickListener { epgChannel, _, isChecked ->
        if (isChecked) {
            if (helpViewModel.currentAssignEpgChannel != null) {
                if (epgChannel.isExternalEpg) {
                    helpViewModel.currentAssignEpgChannel?.alwaysUsesExternalEpg = true
                    helpViewModel.currentAssignEpgChannel?.usesExternalEpg = true
                    helpViewModel.currentAssignEpgChannel?.epgSourceId = epgChannel.relatedepgSourceId
                    helpViewModel.currentAssignEpgChannel?.linkedEpgChannel?.target = epgChannel
                } else {
                    helpViewModel.currentAssignEpgChannel?.epgSourceId = epgChannel.relatedepgSourceId
                    helpViewModel.currentAssignEpgChannel?.usesPlaylistEpg = true
                    helpViewModel.currentAssignEpgChannel?.usesExternalEpg = false
                    helpViewModel.currentAssignEpgChannel?.alwaysUsesExternalEpg = false
                    helpViewModel.currentAssignEpgChannel?.linkedEpgChannel?.target = epgChannel
                }
                helpViewModel.currentAssignEpgChannel?.let { tvChannBox.put(it) }
                val mainContainer = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                if (mainContainer is TvChannelsFragment) {
                    mainContainer.updateSingleChannel()
                    helpViewModel.currentAssignEpgChannel?.let { mainContainer.showEpgPreview(it) }
                }
            }
        } else {
            if (epgChannel.isExternalEpg) {
                helpViewModel.currentAssignEpgChannel?.epgSourceId = null
                helpViewModel.currentAssignEpgChannel?.alwaysUsesExternalEpg = false
                helpViewModel.currentAssignEpgChannel?.usesExternalEpg = false
                if ((helpViewModel.currentAssignEpgChannel?.account?.target?.epgsources?.filter { it.isSelected }?.any { it.isPlaylistEpg } == true) &&
                    helpViewModel.currentAssignEpgChannel?.epgChannel?.target != null){
                    openResetEpgDialog()
                } else {
                    helpViewModel.currentAssignEpgChannel?.linkedEpgChannel?.target = null
                    helpViewModel.currentAssignEpgChannel?.let { tvChannBox.put(it) }
                    val mainContainer = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                    if (mainContainer is TvChannelsFragment) {
                        mainContainer.updateSingleChannel()
                        helpViewModel.currentAssignEpgChannel?.let { mainContainer.showEpgPreview(it) }
                    }
                }
            } else {
                helpViewModel.currentAssignEpgChannel?.epgSourceId = null
                helpViewModel.currentAssignEpgChannel?.usesPlaylistEpg = false
                helpViewModel.currentAssignEpgChannel?.linkedEpgChannel?.target = null
                updateChannelList()
                helpViewModel.currentAssignEpgChannel?.let { tvChannBox.put(it) }
                val mainContainer = parentFragmentManager.findFragmentById(R.id.navHostFragment)
                if (mainContainer is TvChannelsFragment) {
                    helpViewModel.currentAssignEpgChannel?.let { mainContainer.showEpgPreview(it) }
                }
            }
        }
    }

    fun openResetEpgDialog() {
        val alertDialogBuilder = AlertDialog.Builder(this@AssingChannelToEpgFragment.requireActivity())

        alertDialogBuilder.setMessage("Reset to Playlist-Epg?")

        alertDialogBuilder.setPositiveButton("Yes") { _, _ ->
            helpViewModel.currentAssignEpgChannel?.linkedEpgChannel?.target = helpViewModel.currentAssignEpgChannel?.epgChannel?.target
            helpViewModel.currentAssignEpgChannel?.let { tvChannBox.put(it) }
            val epgChannel =
                epgChannelListAdapter.currentList.find { it.chEpgId == helpViewModel.currentAssignEpgChannel!!.linkedEpgChannel?.target?.chEpgId }
            val epgChPosition =
                epgChannelListAdapter.currentList.indexOf(epgChannel)
            binding.rvEpgList.requestFocus()
            binding.rvEpgList.post {
                binding.rvEpgList.setSelectedPosition(epgChPosition)
            }
            updateChannelList()
            val mainContainer = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (mainContainer is TvChannelsFragment) {
                helpViewModel.currentAssignEpgChannel?.let { mainContainer.showEpgPreview(it) }
            }
            epgChannelListAdapter.notifyDataSetChanged()
        }

        alertDialogBuilder.setNegativeButton("No") { dialog, _ ->
            helpViewModel.currentAssignEpgChannel?.linkedEpgChannel?.target = null
            helpViewModel.currentAssignEpgChannel?.let { tvChannBox.put(it) }
            epgChannelListAdapter.updateChannel(helpViewModel.currentAssignEpgChannel!!)
            updateChannelList()
            val mainContainer = parentFragmentManager.findFragmentById(R.id.navHostFragment)
            if (mainContainer is TvChannelsFragment) {
                helpViewModel.currentAssignEpgChannel?.let { mainContainer.showEpgPreview(it) }
            }
            dialog.dismiss()
            val epgChannel =
                epgChannelListAdapter.currentList.find { it.chEpgId == helpViewModel.currentAssignEpgChannel!!.linkedEpgChannel?.target?.chEpgId }
            val epgChPosition =
                epgChannelListAdapter.currentList.indexOf(epgChannel)
            epgChannelListAdapter.notifyItemChanged(epgChPosition)
        }

        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    fun findBestMatchEpgChannel(tvChannelName: String, epgChannels: List<EpgSourceChannel>): EpgSourceChannel? {
        val levenshteinDistance = LevenshteinDistance()

        // 1. Trenne externe EPG-Sources und die Playlist-EPG-Source
        val allEpgSources = helpViewModel.currentAssignEpgChannel?.account?.target?.epgsources
        val externalEpgSources = allEpgSources?.filter { !it.isPlaylistEpg }?.sortedBy { it.position }
        val playlistEpgSource = allEpgSources?.find { it.isPlaylistEpg }

        // 2. Suche in den externen EPG-Sources nacheinander nach einem Match
        if (externalEpgSources != null) {
            for (epgSource in externalEpgSources) {
                val channelsForSource = epgChannels.filter { it.epgsource.target.id == epgSource.id }
                val bestMatch = findClosestMatch(tvChannelName, channelsForSource, levenshteinDistance)
                if (bestMatch != null) {
                    return bestMatch // Match gefunden, Vorgang beendet
                }
            }
        }

        // 3. Wenn kein Match in externen EPG-Sources, prüfe die Playlist-EPG-Source
        if (playlistEpgSource != null) {
            val channelsForPlaylist = epgChannels.filter { it.epgsource.target.id == playlistEpgSource.id }
            return findClosestMatch(tvChannelName, channelsForPlaylist, levenshteinDistance)
        }

        // 4. Kein Match gefunden
        return null
    }

    // Hilfsfunktion: Finde den besten Match basierend auf der Levenshtein-Distanz
    private fun findClosestMatch(
        tvChannelName: String,
        channels: List<EpgSourceChannel>,
        levenshteinDistance: LevenshteinDistance
    ): EpgSourceChannel? {
        var bestMatch: EpgSourceChannel? = null
        var minDistance = tvChannelName.length

        for (channel in channels) {
            val distance = levenshteinDistance.apply(tvChannelName.lowercase(), channel.name.lowercase())
            if (distance < minDistance) {
                minDistance = distance
                bestMatch = channel
            }
        }

        return bestMatch
    }


    private fun searchForChannel(searchQuery: String) {
        var bestMatchedChannel: EpgSourceChannel? = null
        var minDistance = Int.MAX_VALUE
        val epgChannels = epgChannelListAdapter.currentList
        val levenshteinDistance = LevenshteinDistance()
        val searchQueryLowerCase = searchQuery.lowercase()

        // Durchlaufe alle Kanäle parallel
        runBlocking {
            epgChannels.forEachParallel { channel ->
                // Suche die beste Übereinstimmung für jedes `display_name` des Kanals
                val bestMatchForChannel = channel.display_name
                    .map { displayName ->
                        val displayNameLowerCase = displayName.lowercase()
                        val distance = levenshteinDistance.apply(searchQueryLowerCase, displayNameLowerCase)
                        // Gib das Paar aus Distanz und Kanal zurück
                        distance to channel
                    }
                    // Finde die kleinste Distanz (beste Übereinstimmung)
                    .minByOrNull { it.first }

                // Wenn wir einen besten Treffer gefunden haben, überprüfe, ob es der beste insgesamt ist
                bestMatchForChannel?.let { (distance, channel) ->
                    if (distance < minDistance) {
                        minDistance = distance
                        bestMatchedChannel = channel
                    }
                }
            }
        }

        // Wenn ein bester Kanal gefunden wurde, scrolle zur Position des Kanals in der RecyclerView
        if (bestMatchedChannel != null) {
            val position = epgChannelListAdapter.currentList.indexOfFirst { it.chEpgId == bestMatchedChannel?.chEpgId }
            if (position != -1) {
                binding.rvEpgList.setSelectedPosition(position, object : ViewHolderTask() {
                    override fun execute(viewHolder: RecyclerView.ViewHolder) {
                        viewHolder.itemView.requestFocus()
                    }
                })
            }
        }
    }

    // Erweiterungsfunktion, um parallele Verarbeitung zu ermöglichen
    suspend fun <T> Iterable<T>.forEachParallel(action: suspend (T) -> Unit) {
        coroutineScope {
            map { async { action(it) } }.awaitAll()
        }
    }



    fun setFocusToRightMenu() {
        binding.btnEpglist.requestFocus()
    }

    fun setFocusToTvChannels() {
        val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (mainFragment is TvChannelsFragment) {
            mainFragment.setFocusToTvChannels()
        }
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.add(R.id.playlist_epg_sources_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
        binding.playlistEpgSourcesContainer.visibility = View.VISIBLE
        binding.playlistEpgSourcesContainer.requestFocus()
    }

    fun refreshEpgChannels(epgSource: EpgSource) {
        binding.overlayLayout.visibility = View.GONE
        binding.sortbySource.visibility = View.GONE
        binding.playlistEpgSourcesContainer.visibility = View.GONE
        binding.tvEpgsourceName.text = epgSource.name
        viewLifecycleOwner.lifecycleScope.launch {
            epgSource.epgchs.reset()
            val epgChannelList = epgSource.epgchs
            val sortedEpgChannelList = epgChannelList.sortedBy { it.name.lowercase() }
            if (sortedEpgChannelList.isNotEmpty() && helpViewModel.currentAssignEpgChannel != null) {
                if (sortedEpgChannelList.any { it.chEpgId == helpViewModel.currentAssignEpgChannel!!.linkedEpgChannel?.target?.chEpgId }) {
                    prepareRecyclerView(helpViewModel.currentAssignEpgChannel!!)
                    epgChannelListAdapter.submitList(sortedEpgChannelList)
                    val epgChannel = epgChannelListAdapter.currentList.find { it.chEpgId == helpViewModel.currentAssignEpgChannel!!.linkedEpgChannel?.target?.chEpgId }
                    val epgChPosition =
                        epgChannelListAdapter.currentList.indexOf(epgChannel)
                    binding.rvEpgList.requestFocus()
                    binding.rvEpgList.post {
                        binding.rvEpgList.setSelectedPosition(
                            epgChPosition,
                            object : ViewHolderTask() {
                                override fun execute(viewHolder: RecyclerView.ViewHolder) {
                                    viewHolder.itemView.requestFocus()
                                }
                            })
                    }
                } else {
                    val matchedEpgChannel = findBestMatchEpgChannel(
                        helpViewModel.currentAssignEpgChannel!!.showingName,
                        sortedEpgChannelList
                    )
                    if (matchedEpgChannel != null) {
                        // Wenn ein übereinstimmender EPG-Kanal gefunden wurde, setze die Position
                        prepareRecyclerView(helpViewModel.currentAssignEpgChannel!!)
                        epgChannelListAdapter.submitList(sortedEpgChannelList)
                        val epgChPosition =
                            epgChannelListAdapter.currentList.indexOf(matchedEpgChannel)
                        binding.rvEpgList.requestFocus()
                        binding.rvEpgList.post {
                            binding.rvEpgList.setSelectedPosition(
                                epgChPosition,
                                object : ViewHolderTask() {
                                    override fun execute(viewHolder: RecyclerView.ViewHolder) {
                                        viewHolder.itemView.requestFocus()
                                    }
                                })
                        }
                    } else {
                        prepareRecyclerView(helpViewModel.currentAssignEpgChannel!!)
                        epgChannelListAdapter.submitList(sortedEpgChannelList)
                        val firstMatchingChannel =
                            epgChannelListAdapter.currentList.firstOrNull {
                                it.name.startsWith(
                                    helpViewModel.currentAssignEpgChannel!!.showingName.first(),
                                    ignoreCase = true
                                )
                            }
                        if (firstMatchingChannel != null) {
                            val position = epgChannelListAdapter.currentList.indexOf(
                                firstMatchingChannel
                            )
                            binding.rvEpgList.requestFocus()
                            binding.rvEpgList.post {
                                binding.rvEpgList.setSelectedPosition(
                                    position,
                                    object : ViewHolderTask() {
                                        override fun execute(viewHolder: RecyclerView.ViewHolder) {
                                            viewHolder.itemView.requestFocus()
                                        }
                                    })
                            }
                        } else {
                            binding.rvEpgList.requestFocus()
                            binding.rvEpgList.post {
                                binding.rvEpgList.setSelectedPosition(
                                    0,
                                    object : ViewHolderTask() {
                                        override fun execute(viewHolder: RecyclerView.ViewHolder) {
                                            viewHolder.itemView.requestFocus()
                                        }
                                    })
                            }
                        }
                    }
                }
            }
        }
    }

    fun showAllEpgSources() {
        binding.playlistEpgSourcesContainer.visibility = View.GONE
        binding.overlayLayout.visibility = View.GONE
        binding.sortbySource.visibility = View.VISIBLE
        val epgChannelList: MutableList<EpgSourceChannel> = mutableListOf()
        val currentAccount = accountBox.get(helpViewModel.currentFocusedTvAccount!!.id)
        viewLifecycleOwner.lifecycleScope.launch {
            currentAccount.epgsources.filter { it.isSelected }.sortedBy { it.position }.forEach {
                epgChannelList.addAll(it.relatedepgsource.target.epgchs.sortedBy { it.name.lowercase() })
            }
            if (epgChannelList.isNotEmpty() && helpViewModel.currentAssignEpgChannel != null) {
                if (epgChannelList.any {
                    it.chEpgId == helpViewModel.currentAssignEpgChannel!!.linkedEpgChannel?.target?.chEpgId
                }) {
                    prepareRecyclerView(helpViewModel.currentAssignEpgChannel!!)
                    epgChannelListAdapter.submitList(epgChannelList)
                    val epgChannel =
                        epgChannelListAdapter.currentList.find { it.chEpgId == helpViewModel.currentAssignEpgChannel!!.linkedEpgChannel?.target?.chEpgId }
                    val epgChPosition =
                        epgChannelListAdapter.currentList.indexOf(epgChannel)
                    binding.rvEpgList.requestFocus()
                    binding.rvEpgList.post {
                        binding.rvEpgList.setSelectedPosition(
                            epgChPosition,
                            object : ViewHolderTask() {
                                override fun execute(viewHolder: RecyclerView.ViewHolder) {
                                    viewHolder.itemView.requestFocus()
                                }
                            })
                    }
                } else {
                    val matchedEpgChannel = findBestMatchEpgChannel(
                        helpViewModel.currentAssignEpgChannel!!.showingName,
                        epgChannelList
                    )
                    if (matchedEpgChannel != null) {
                        prepareRecyclerView(helpViewModel.currentAssignEpgChannel!!)
                        epgChannelListAdapter.submitList(epgChannelList)
                        val epgChPosition =
                            epgChannelListAdapter.currentList.indexOf(matchedEpgChannel)
                        binding.rvEpgList.requestFocus()
                        binding.rvEpgList.post {
                            binding.rvEpgList.setSelectedPosition(
                                epgChPosition,
                                object : ViewHolderTask() {
                                    override fun execute(viewHolder: RecyclerView.ViewHolder) {
                                        viewHolder.itemView.requestFocus()
                                    }
                                })
                        }
                    } else {
                        prepareRecyclerView(helpViewModel.currentAssignEpgChannel!!)
                        epgChannelListAdapter.submitList(epgChannelList)
                        val firstMatchingChannel =
                            epgChannelListAdapter.currentList.firstOrNull {
                                it.name.startsWith(
                                    helpViewModel.currentAssignEpgChannel!!.showingName.first(),
                                    ignoreCase = true
                                )
                            }
                        if (firstMatchingChannel != null) {
                            val position = epgChannelListAdapter.currentList.indexOf(
                                firstMatchingChannel
                            )
                            binding.rvEpgList.requestFocus()
                            binding.rvEpgList.post {
                                binding.rvEpgList.setSelectedPosition(
                                    position,
                                    object : ViewHolderTask() {
                                        override fun execute(viewHolder: RecyclerView.ViewHolder) {
                                            viewHolder.itemView.requestFocus()
                                        }
                                    })
                            }
                        } else {
                            binding.rvEpgList.requestFocus()
                            binding.rvEpgList.post {
                                binding.rvEpgList.setSelectedPosition(
                                    0,
                                    object : ViewHolderTask() {
                                        override fun execute(viewHolder: RecyclerView.ViewHolder) {
                                            viewHolder.itemView.requestFocus()
                                        }
                                    })
                            }
                        }
                    }
                }
            }
        }
    }

    fun resetEpgListCheck() {
        checkEpgCannelJob?.cancel()
        binding.rvEpgList.visibility = View.INVISIBLE
        binding.assignepgProgressBar.visibility = View.VISIBLE
    }

    var checkEpgCannelJob: Job? = null

    fun cancelCheckChannelJob() {
        checkEpgCannelJob?.cancel()
    }

    fun checkNewChannel() {
        if (isAdded && view != null) {
            checkThisNewChannel()
        }
    }
    fun checkThisNewChannel() {
        val safeBinding = _binding ?: return
        if (viewLifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        checkEpgCannelJob?.cancel()
        checkEpgCannelJob = viewLifecycleOwner.lifecycleScope.launch {
            safeBinding.rvEpgList.visibility = View.INVISIBLE
            safeBinding.assignepgProgressBar.visibility = View.VISIBLE
        val relatedEpgSource =
            if (helpViewModel.currentAssignEpgChannel?.linkedEpgChannel?.target != null) {
                helpViewModel.currentAssignEpgChannel?.linkedEpgChannel?.target?.epgsource?.target
            } else {
                helpViewModel.currentFocusedTvAccount!!.epgsources.filter { it.isSelected }.sortedBy { it.position }
                    .firstOrNull()?.relatedepgsource?.target
            }
        if (relatedEpgSource != null) {
            helpViewModel.currentSelectedEpgChannelSource = relatedEpgSource
            safeBinding.tvEpgsourceName.text = relatedEpgSource.name
                val epgChannelList = withContext(Dispatchers.IO) {
                    relatedEpgSource.epgchs
                }
                val sortedEpgChannelList = epgChannelList.sortedBy { it.name.lowercase() }
                if (sortedEpgChannelList.isNotEmpty()) {
                    if (sortedEpgChannelList.any { it.chEpgId == helpViewModel.currentAssignEpgChannel!!.linkedEpgChannel?.target?.chEpgId }) {
                        prepareRecyclerView(helpViewModel.currentAssignEpgChannel!!)
                        epgChannelListAdapter.submitList(sortedEpgChannelList)
                        val epgChannel =
                            epgChannelListAdapter.currentList.find { it.chEpgId == helpViewModel.currentAssignEpgChannel!!.linkedEpgChannel?.target?.chEpgId }
                        val epgChPosition =
                            epgChannelListAdapter.currentList.indexOf(epgChannel)
                        safeBinding.rvEpgList.setSelectedPosition(epgChPosition)
                        safeBinding.assignepgProgressBar.visibility = View.INVISIBLE
                        safeBinding.rvEpgList.visibility = View.VISIBLE
                    } else {
                        val matchedEpgChannel = findBestMatchEpgChannel(
                            helpViewModel.currentAssignEpgChannel!!.showingName,
                            sortedEpgChannelList
                        )
                        if (matchedEpgChannel != null) {
                            // Wenn ein übereinstimmender EPG-Kanal gefunden wurde, setze die Position
                            prepareRecyclerView(helpViewModel.currentAssignEpgChannel!!)
                            epgChannelListAdapter.submitList(sortedEpgChannelList)
                            val epgChPosition =
                                epgChannelListAdapter.currentList.indexOf(matchedEpgChannel)
                            safeBinding.rvEpgList.post {
                                binding.rvEpgList.setSelectedPosition(epgChPosition)
                            }
                            safeBinding.assignepgProgressBar.visibility = View.INVISIBLE
                            safeBinding.rvEpgList.visibility = View.VISIBLE
                        } else {
                            prepareRecyclerView(helpViewModel.currentAssignEpgChannel!!)
                            epgChannelListAdapter.submitList(sortedEpgChannelList)
                            val firstMatchingChannel =
                                epgChannelListAdapter.currentList.firstOrNull {
                                    it.name.startsWith(
                                        helpViewModel.currentAssignEpgChannel!!.showingName.first(),
                                        ignoreCase = true
                                    )
                                }
                            if (firstMatchingChannel != null) {
                                val position = epgChannelListAdapter.currentList.indexOf(
                                    firstMatchingChannel
                                )
                                safeBinding.rvEpgList.post {
                                    binding.rvEpgList.setSelectedPosition(position)
                                }
                                safeBinding.assignepgProgressBar.visibility = View.INVISIBLE
                                safeBinding.rvEpgList.visibility = View.VISIBLE
                            } else {
                                safeBinding.rvEpgList.post {
                                    safeBinding.rvEpgList.setSelectedPosition(0)
                                }
                                safeBinding.assignepgProgressBar.visibility = View.INVISIBLE
                                safeBinding.rvEpgList.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
        }
    }

    fun showEpgSourceNameWhenAll(epgSource: EpgSource) {
        binding.tvEpgsourceName.text = epgSource.name
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun updateChannelList() {
        val containerFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (containerFragment is TvChannelsFragment) {
            containerFragment.updateSingleChannel()
        }
    }

    fun setFocusToEpgChannels() {
        if (epgChannelListAdapter.currentList.isNotEmpty()) {
            binding.rvEpgList.requestFocus()
        } else {
            return
        }
    }


    fun removeOverlay() {
        binding.overlayLayout.visibility = View.GONE
    }

    fun closeFragment() {
        cancelCheckChannelJob()
        helpViewModel.assignChannelToEpgActive = false
        helpViewModel.currentAssignChannelPosition = null
        helpViewModel.modifiedChannelList = false
        helpViewModel.currentSelectedEpgChannelSource = null
        helpViewModel.showAllEpgChannelSources = false
        parentFragmentManager.popBackStack()
        val mainFragment = parentFragmentManager.findFragmentById(R.id.navHostFragment)
        if (mainFragment is TvChannelsFragment) {
            mainFragment.updateLastFocusedAssignChannel()
            mainFragment.resetFocusedAssignEpgChannel()
            mainFragment.makeChannelOptionsContainerVisible()
            if (helpViewModel.assignEpgChannelListFiltered) {
                mainFragment.updateChannelList()
                helpViewModel.assignEpgChannelListFiltered = false
            }
            val containerFragment = parentFragmentManager.findFragmentById(R.id.container_ChannelOptions)
            if (containerFragment is ChannelOptionsFragment) {
                containerFragment.focusLastSelectedItem()
            }
        }
    }
}