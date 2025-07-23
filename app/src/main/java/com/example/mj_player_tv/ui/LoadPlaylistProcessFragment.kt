package com.example.mj_player_tv.ui

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.databinding.FragmentHomeBinding
import com.example.mj_player_tv.databinding.FragmentLoadPlaylistProcessBinding
import com.example.mj_player_tv.databinding.FragmentTvChannelsBinding
import com.example.mj_player_tv.repository.PlaylistLoadProcessState
import com.example.mj_player_tv.ui.settings.AddPlaylistFragment
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@UnstableApi
class LoadPlaylistProcessFragment : Fragment(R.layout.fragment_load_playlist_process) {

    private var _binding: FragmentLoadPlaylistProcessBinding? = null

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
        _binding = FragmentLoadPlaylistProcessBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isLoading = true


        binding.constLayoutProcess.requestFocus()


        binding.tvInfo.visibility = View.VISIBLE

        binding.loadingBalken.visibility = View.VISIBLE
        binding.loadingBalken.isIndeterminate = true
        binding.linLayoutPlaylistinfo.visibility = View.VISIBLE
        binding.playlistinfoLoaded.isSelected = true
        binding.playlistinfoLoaded.text = "Check playlist.."

        when (helpViewModel.addAccount) {
            0 -> {
                binding.tvInfo.text = xtreamViewModel.currentProcessName
                setupXtreamObserver()
            }
            1 -> {
                binding.tvInfo.text = stalkerViewModel.currentProcessName
                setupStalkerObserver()
            }
            2 -> {

            }
        }


        binding.constLayoutProcess.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                if (!isLoading) {
                    return@setOnKeyListener true
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                if (!isLoading) {
                    return@setOnKeyListener true
                }
            }
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                if (!isLoading) {
                    return@setOnKeyListener true
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                if (!isLoading) {
                    return@setOnKeyListener true
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                if (!isLoading) {
                    return@setOnKeyListener true
                }
            }
            return@setOnKeyListener false
        }
    }

    fun setupXtreamObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            xtreamViewModel.playlistProcessState.collect { playlistProcessState ->
                when (playlistProcessState) {
                    is PlaylistLoadProcessState.Loading -> {
                        withContext(Dispatchers.Main) {
                            binding.loadingBalken.visibility = View.VISIBLE
                            binding.loadingBalken.isIndeterminate = true
                            binding.linLayoutPlaylistinfo.visibility = View.VISIBLE
                            binding.playlistinfoLoaded.isSelected = true
                            binding.playlistinfoLoaded.text = "Check playlist.."
                        }
                    }

                    is PlaylistLoadProcessState.GetToken -> {
                        // Aktualisiere den Ladebalken basierend auf epgProcessState.progress
                        when (playlistProcessState.progress) {
                            100 -> {
                                binding.ivPlaylistinfoCheck.isSelected = true
                                binding.playlistinfoLoaded.text = resources.getString(R.string.token_ok)
                                binding.linLayoutTvcat.visibility = View.VISIBLE
                                binding.linLayoutMovies.visibility = View.VISIBLE
                                binding.linLayoutChannels.visibility = View.VISIBLE
                                binding.linLayoutSeries.visibility = View.VISIBLE
                            }
                            else -> {}
                        }
                    }

                    is PlaylistLoadProcessState.TokenError -> {
                        binding.linLayoutPlaylistinfo.visibility = View.VISIBLE
                        val tokenString = resources.getString(R.string.token_error)
                        binding.playlistinfoLoaded.isSelected = true
                        binding.playlistinfoLoaded.text = "$tokenString \n ${playlistProcessState.message} "
                        binding.loadingBalken.isIndeterminate = false
                        binding.loadingBalken.progress = 0
                        (requireActivity() as? MainActivity)?.addPlaylistError(playlistProcessState.message)
                        lifecycleScope.launch {
                            delay(3000L) //
                            stalkerViewModel.resetPlaylistProcessState()
                            (requireActivity() as? MainActivity)?.closePlaylistError()
                            parentFragmentManager.popBackStack()
                        }
                    }

                    is PlaylistLoadProcessState.GetTvCategories -> {
                        when (playlistProcessState.progress) {
                            1 -> {
                                binding.linLayoutTvcat.visibility = View.VISIBLE
                                binding.tvcatLoaded.text = playlistProcessState.message
                            }
                            100 -> {
                                binding.tvcatLoaded.isSelected = true
                                binding.ivTvcatCheck.isSelected = true
                                binding.tvcatLoaded.text = "Tv Categories: ${playlistProcessState.message}"
                            }
                        }
                    }

                    is PlaylistLoadProcessState.TvError -> {
                        binding.linLayoutTvcat.visibility = View.VISIBLE
                        binding.tvcatLoaded.isSelected = true
                        binding.ivTvcatCheck.isSelected = false
                        binding.tvcatLoaded.text = resources.getString(R.string.tv_error)
                    }

                    is PlaylistLoadProcessState.GetChannels -> {
                        when (playlistProcessState.progress) {
                            1 -> {
                                binding.linLayoutChannels.visibility = View.VISIBLE
                                binding.channelsLoaded.text = playlistProcessState.message
                            }
                            100 -> {
                                binding.channelsLoaded.isSelected = true
                                binding.ivChannelsCheck.isSelected = true
                                binding.channelsLoaded.text = "${resources.getString(R.string.channels_ok)} ${playlistProcessState.message}"
                            }
                        }
                    }
                    is PlaylistLoadProcessState.ChannelsError -> {
                        binding.linLayoutChannels.visibility = View.VISIBLE
                        binding.channelsLoaded.isSelected = true
                        binding.ivChannelsCheck.isSelected = false
                        binding.tvcatLoaded.text = resources.getString(R.string.channels_error)
                    }

                    is PlaylistLoadProcessState.GetMovieCategories -> {
                        when (playlistProcessState.progress) {
                            1 -> {
                                binding.linLayoutMovies.visibility = View.VISIBLE
                                binding.movieLoaded.text = playlistProcessState.message
                            }
                            100 -> {
                                binding.movieLoaded.isSelected = true
                                binding.totalmovieLoaded.isSelected = true
                                binding.ivMovieCheck.isSelected = true
                                binding.movieLoaded.text = "Movie Categories: ${playlistProcessState.message}"
                                binding.totalmovieLoaded.text = "Movies: ${playlistProcessState.totalMovies}"
                            }
                        }
                    }

                    is PlaylistLoadProcessState.MovieError -> {
                        binding.linLayoutMovies.visibility = View.VISIBLE
                        binding.movieLoaded.isSelected = true
                        binding.totalmovieLoaded.text = ""
                        binding.ivMovieCheck.isSelected = false
                        binding.movieLoaded.text = resources.getString(R.string.movies_error)
                    }

                    is PlaylistLoadProcessState.GetSeriesCategories -> {
                        when (playlistProcessState.progress) {
                            1 -> {
                                binding.linLayoutSeries.visibility = View.VISIBLE
                                binding.seriesLoaded.text = playlistProcessState.message
                            }
                            100 -> {
                                binding.seriesLoaded.isSelected = true
                                binding.totalseriesLoaded.isSelected = true
                                binding.ivSeriesCheck.isSelected = true
                                binding.seriesLoaded.text = "Series Categories: ${playlistProcessState.message}"
                                binding.totalseriesLoaded.text = "Series: ${playlistProcessState.totalSeries}"
                            }
                        }
                    }

                    is PlaylistLoadProcessState.SeriesError -> {
                        binding.linLayoutSeries.visibility = View.VISIBLE
                        binding.seriesLoaded.isSelected = true
                        binding.totalseriesLoaded.text = ""
                        binding.ivSeriesCheck.isSelected = false
                        binding.seriesLoaded.text = resources.getString(R.string.series_error)
                    }

                    is PlaylistLoadProcessState.Success -> {
                        helpViewModel.playlistSuccessFullyAdded = true
                        withContext(Dispatchers.Main) {
                            binding.playlistSuccess.visibility = View.VISIBLE
                            binding.playlistSuccess.text =
                                resources.getString(R.string.playlist_added)
                        }
                        lifecycleScope.launch {
                            delay(5000L) //
                            stalkerViewModel.currentProcessName = ""
                            binding.loadingBalken.isIndeterminate = false
                            binding.loadingBalken.progress = 100
                            stalkerViewModel.resetPlaylistProcessState()
                            val containerFragment = parentFragmentManager.findFragmentById(R.id.settings_container)
                            if (containerFragment is AddPlaylistFragment) {
                                containerFragment.setFocusToLastSelectedMenu()
                            }
                            parentFragmentManager.popBackStack()
                        }
                    }
                    is PlaylistLoadProcessState.Error -> {
                        stalkerViewModel.currentProcessName = ""
                        binding.linLayoutPlaylistinfo.visibility = View.VISIBLE
                        binding.playlistinfoLoaded.text = resources.getString(R.string.playlist_error)
                        binding.loadingBalken.isIndeterminate = false
                        binding.loadingBalken.progress = 0
                        (requireActivity() as? MainActivity)?.addPlaylistError(playlistProcessState.message)
                        lifecycleScope.launch {
                            delay(3000L) //
                            stalkerViewModel.resetPlaylistProcessState()
                            (requireActivity() as? MainActivity)?.closePlaylistError()
                            parentFragmentManager.popBackStack()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun setupStalkerObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            stalkerViewModel.playlistProcessState.collect { playlistProcessState ->
                when (playlistProcessState) {
                    is PlaylistLoadProcessState.Loading -> {
                        withContext(Dispatchers.Main) {
                            binding.loadingBalken.visibility = View.VISIBLE
                            binding.loadingBalken.isIndeterminate = true
                            binding.linLayoutPlaylistinfo.visibility = View.VISIBLE
                            binding.playlistinfoLoaded.isSelected = true
                            binding.playlistinfoLoaded.text = "Check playlist.."
                        }
                    }

                    is PlaylistLoadProcessState.GetToken -> {
                        // Aktualisiere den Ladebalken basierend auf epgProcessState.progress
                        when (playlistProcessState.progress) {
                            100 -> {
                                binding.ivPlaylistinfoCheck.isSelected = true
                                binding.playlistinfoLoaded.text = resources.getString(R.string.token_ok)
                                binding.linLayoutTvcat.visibility = View.VISIBLE
                                binding.linLayoutMovies.visibility = View.VISIBLE
                                binding.linLayoutChannels.visibility = View.VISIBLE
                                binding.linLayoutSeries.visibility = View.VISIBLE
                            }
                            else -> {}
                        }
                    }

                    is PlaylistLoadProcessState.TokenError -> {
                        binding.linLayoutPlaylistinfo.visibility = View.VISIBLE
                        val tokenString = resources.getString(R.string.token_error)
                        binding.playlistinfoLoaded.isSelected = true
                        binding.playlistinfoLoaded.text = "$tokenString \n ${playlistProcessState.message} "
                        binding.loadingBalken.isIndeterminate = false
                        binding.loadingBalken.progress = 0
                        (requireActivity() as? MainActivity)?.addPlaylistError(playlistProcessState.message)
                        lifecycleScope.launch {
                            delay(3000L) //
                            stalkerViewModel.resetPlaylistProcessState()
                            (requireActivity() as? MainActivity)?.closePlaylistError()
                            parentFragmentManager.popBackStack()
                        }
                    }

                    is PlaylistLoadProcessState.GetTvCategories -> {
                        when (playlistProcessState.progress) {
                            1 -> {
                                binding.linLayoutTvcat.visibility = View.VISIBLE
                                binding.tvcatLoaded.text = playlistProcessState.message
                            }
                            100 -> {
                                binding.tvcatLoaded.isSelected = true
                                binding.ivTvcatCheck.isSelected = true
                                binding.tvcatLoaded.text = "Tv Categories: ${playlistProcessState.message}"
                            }
                        }
                    }

                    is PlaylistLoadProcessState.TvError -> {
                        binding.linLayoutTvcat.visibility = View.VISIBLE
                        binding.tvcatLoaded.isSelected = true
                        binding.ivTvcatCheck.isSelected = false
                        binding.tvcatLoaded.text = resources.getString(R.string.tv_error)
                    }

                    is PlaylistLoadProcessState.GetChannels -> {
                        when (playlistProcessState.progress) {
                            1 -> {
                                binding.linLayoutChannels.visibility = View.VISIBLE
                                binding.channelsLoaded.text = playlistProcessState.message
                            }
                            100 -> {
                                binding.channelsLoaded.isSelected = true
                                binding.ivChannelsCheck.isSelected = true
                                binding.channelsLoaded.text = "${resources.getString(R.string.channels_ok)} ${playlistProcessState.message}"
                            }
                        }
                    }
                    is PlaylistLoadProcessState.ChannelsError -> {
                        binding.linLayoutChannels.visibility = View.VISIBLE
                        binding.channelsLoaded.isSelected = true
                        binding.ivChannelsCheck.isSelected = false
                        binding.tvcatLoaded.text = resources.getString(R.string.channels_error)
                    }

                    is PlaylistLoadProcessState.GetMovieCategories -> {
                        when (playlistProcessState.progress) {
                            1 -> {
                                binding.linLayoutMovies.visibility = View.VISIBLE
                                binding.movieLoaded.text = playlistProcessState.message
                            }
                            100 -> {
                                binding.movieLoaded.isSelected = true
                                binding.totalmovieLoaded.isSelected = true
                                binding.ivMovieCheck.isSelected = true
                                binding.movieLoaded.text = "Movie Categories: ${playlistProcessState.message}"
                                binding.totalmovieLoaded.text = "Movies: ${playlistProcessState.totalMovies}"
                            }
                        }
                    }

                    is PlaylistLoadProcessState.MovieError -> {
                        binding.linLayoutMovies.visibility = View.VISIBLE
                        binding.movieLoaded.isSelected = true
                        binding.totalmovieLoaded.text = ""
                        binding.ivMovieCheck.isSelected = false
                        binding.movieLoaded.text = resources.getString(R.string.movies_error)
                    }

                    is PlaylistLoadProcessState.GetSeriesCategories -> {
                        when (playlistProcessState.progress) {
                            1 -> {
                                binding.linLayoutSeries.visibility = View.VISIBLE
                                binding.seriesLoaded.text = playlistProcessState.message
                            }
                            100 -> {
                                binding.seriesLoaded.isSelected = true
                                binding.totalseriesLoaded.isSelected = true
                                binding.ivSeriesCheck.isSelected = true
                                binding.seriesLoaded.text = "Series Categories: ${playlistProcessState.message}"
                                binding.totalseriesLoaded.text = "Series: ${playlistProcessState.totalSeries}"
                            }
                        }
                    }

                    is PlaylistLoadProcessState.SeriesError -> {
                        binding.linLayoutSeries.visibility = View.VISIBLE
                        binding.seriesLoaded.isSelected = true
                        binding.totalseriesLoaded.text = ""
                        binding.ivSeriesCheck.isSelected = false
                        binding.seriesLoaded.text = resources.getString(R.string.series_error)
                    }

                    is PlaylistLoadProcessState.Success -> {
                        helpViewModel.playlistSuccessFullyAdded = true
                        withContext(Dispatchers.Main) {
                            binding.playlistSuccess.visibility = View.VISIBLE
                            binding.playlistSuccess.text =
                                resources.getString(R.string.playlist_added)
                        }
                        lifecycleScope.launch {
                            delay(5000L) //
                            stalkerViewModel.currentProcessName = ""
                            binding.loadingBalken.isIndeterminate = false
                            binding.loadingBalken.progress = 100
                            stalkerViewModel.resetPlaylistProcessState()
                            val containerFragment = parentFragmentManager.findFragmentById(R.id.settings_container)
                            if (containerFragment is AddPlaylistFragment) {
                                containerFragment.setFocusToLastSelectedMenu()
                            }
                            parentFragmentManager.popBackStack()
                        }
                    }
                    is PlaylistLoadProcessState.Error -> {
                        stalkerViewModel.currentProcessName = ""
                        binding.linLayoutPlaylistinfo.visibility = View.VISIBLE
                        binding.playlistinfoLoaded.text = resources.getString(R.string.playlist_error)
                        binding.loadingBalken.isIndeterminate = false
                        binding.loadingBalken.progress = 0
                        (requireActivity() as? MainActivity)?.addPlaylistError(playlistProcessState.message)
                        lifecycleScope.launch {
                            delay(3000L) //
                            stalkerViewModel.resetPlaylistProcessState()
                            (requireActivity() as? MainActivity)?.closePlaylistError()
                            parentFragmentManager.popBackStack()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}