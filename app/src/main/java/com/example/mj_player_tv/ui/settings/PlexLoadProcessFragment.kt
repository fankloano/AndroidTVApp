package com.example.mj_player_tv.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.Accounts_
import com.example.mj_player_tv.databinding.FragmentAddplexBinding
import com.example.mj_player_tv.databinding.FragmentAddxtreamBinding
import com.example.mj_player_tv.databinding.FragmentLoadPlexProcessBinding
import com.example.mj_player_tv.repository.PlaylistLoadProcessState
import com.example.mj_player_tv.ui.adapter.AccountDataAdapter
import com.example.mj_player_tv.ui.adapter.PlexServersAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.PlexViewModel
import com.example.mj_player_tv.viewmodel.PlexViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import com.rubensousa.dpadrecyclerview.DpadRecyclerView
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@UnstableApi
class PlexLoadProcessFragment : Fragment(R.layout.fragment_load_plex_process) {

    private var _binding: FragmentLoadPlexProcessBinding? = null

    private val binding get() = _binding!!

    private var progressBar: ProgressBar? = null

    private var plexServersAdapter: PlexServersAdapter? = null

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val helpViewModel: HelpViewModel by activityViewModels {
        HelpViewModelFactory(
            requireActivity().application
        )
    }

    private val plexViewModel: PlexViewModel by activityViewModels {
        PlexViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoadPlexProcessBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            val containerFragment = parentFragmentManager.findFragmentById(R.id.settings_container)
            if (containerFragment is AddPlaylistFragment) {
                plexViewModel.serverList.clear()
                containerFragment.setFocusToLastSelectedMenu()
            }
            parentFragmentManager.popBackStack()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val processInfo = binding.tvInfo
        progressBar = binding.loadingBalken

        prepareRecyclerview()

        viewLifecycleOwner.lifecycleScope.launch {
            plexViewModel.playlistProcessState.collect { playlistProcessState ->
                when (playlistProcessState) {
                    is PlaylistLoadProcessState.Loading -> {
                        progressBar!!.isIndeterminate = true
                        processInfo.text = "Check login details"
                    }

                    is PlaylistLoadProcessState.GetToken -> {
                        // Aktualisiere den Ladebalken basierend auf epgProcessState.progress
                        when (playlistProcessState.progress) {
                            100 -> {
                                processInfo.text = "Login successful, load related servers!"
                            }

                            else -> {}
                        }
                    }

                    is PlaylistLoadProcessState.GetTvCategories -> {
                        when (playlistProcessState.progress) {
                            100 -> {
                                processInfo.text = playlistProcessState.message
                                val servers = plexViewModel.serverList
                                binding.loadingBalken.isIndeterminate = false
                                withContext(Dispatchers.Main) {
                                    binding.rvLayoutPlexServers.visibility = View.VISIBLE
                                    plexServersAdapter?.submitList(servers)
                                    binding.rvLayoutPlexServers.requestFocus()
                                }
                            }
                        }
                    }

                    is PlaylistLoadProcessState.GetMovieCategories -> {
                        when (playlistProcessState.progress) {
                            100 -> {
                                processInfo.text = playlistProcessState.message
                            }
                        }
                    }

                    is PlaylistLoadProcessState.MovieError -> {
                                processInfo.text = playlistProcessState.message
                    }

                    is PlaylistLoadProcessState.GetSeriesCategories -> {
                        when (playlistProcessState.progress) {
                            100 -> {
                                processInfo.text = playlistProcessState.message
                            }
                        }
                    }

                    is PlaylistLoadProcessState.SeriesError -> {
                        plexViewModel.serverList.clear()
                        processInfo.text = playlistProcessState.message
                    }

                    is PlaylistLoadProcessState.Success -> {
                        plexViewModel.addedAccount = true
                        plexViewModel.plexAccountAdded += 1
                        processInfo.text = "Plex account succesfully added!"
                        lifecycleScope.launch {
                            delay(2000L) //
                            progressBar!!.isIndeterminate = false
                            progressBar!!.progress = 100
                            plexViewModel.resetPlaylistProcessState()
                            plexViewModel.resetPlexAccountInfos()
                            if (plexViewModel.plexAccountAdded < plexViewModel.serverList.size) {
                                binding.rvLayoutPlexServers.visibility = View.VISIBLE
                                plexServersAdapter?.notifyDataSetChanged()
                                binding.rvLayoutPlexServers.requestFocus()
                            } else {
                                parentFragmentManager.popBackStack()
                            }
                        }
                    }

                    is PlaylistLoadProcessState.Error -> {
                        processInfo.text = "Error fetching Plex data! Account not added!"
                        progressBar!!.isIndeterminate = false
                        progressBar!!.progress = 0
                        lifecycleScope.launch {
                            delay(3000L) //
                            plexViewModel.resetPlaylistProcessState()
                            plexViewModel.resetPlexAccountInfos()
                            parentFragmentManager.popBackStack()
                        }
                    }
                    else -> {}
                }

            }
        }
    }

    private fun prepareRecyclerview() {
        plexServersAdapter = PlexServersAdapter(onClickListener, helpViewModel, accountBox)
        binding.rvLayoutPlexServers.apply {
            adapter = plexServersAdapter
            addItemDecoration(
                DpadLinearSpacingDecoration.create(
                    itemSpacing = 5,
                    edgeSpacing = 5,
                    perpendicularEdgeSpacing = 5
                )
            )
            setFocusOutAllowed(true, false)
            setFocusOutSideAllowed(false, false)
        }
    }

    private val onClickListener = PlexServersAdapter.OnClickListener { it ->
        binding.loadingBalken.isIndeterminate = true
        val resource = it.connections.firstOrNull { it.protocol == "https" }?.uri
        val checkData = checkPlexData(it.clientIdentifier)
        if (checkData) {
            if (resource != null) {
                binding.rvLayoutPlexServers.visibility = View.GONE
                plexViewModel.getPlexUserLibrarySections(
                    resource,
                    it.name,
                    it.accessToken,
                    it.clientIdentifier
                )
            } else {
                val resourceAlternative = it.connections.firstOrNull { it.protocol == "http" }?.uri
                if (resourceAlternative != null) {
                    binding.rvLayoutPlexServers.visibility = View.GONE
                    plexViewModel.getPlexUserLibrarySections(
                        resourceAlternative,
                        it.name,
                        it.accessToken,
                        it.clientIdentifier
                    )
                } else {
                    Toast.makeText(
                        this@PlexLoadProcessFragment.requireActivity(),
                        "No connected server found!",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.rvLayoutPlexServers.requestFocus()
                }
            }
        } else {
            Toast.makeText(this@PlexLoadProcessFragment.requireActivity(), "Server already added!", Toast.LENGTH_SHORT).show()
            binding.rvLayoutPlexServers.requestFocus()
        }
    }

    private fun checkPlexData(identifier: String): Boolean {
        val accounts = accountBox.query(Accounts_.isPlex.equal(true)).build().find().map { it.plexClientIdentifier }
        return if (accounts.contains(identifier)) {
            false
        } else {
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}