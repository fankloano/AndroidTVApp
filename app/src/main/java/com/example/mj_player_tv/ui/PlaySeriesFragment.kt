package com.example.mj_player_tv.ui

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DrmSession.DrmSessionException
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.extractor.DefaultExtractorsFactory
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpisodesOB
import com.example.mj_player_tv.database.entity.SeasonsOB
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.database.help.TrackInfo
import com.example.mj_player_tv.databinding.FragmentPlaySeriesBinding
import com.example.mj_player_tv.ui.adapter.SeriesSelectionAdapter
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLHandshakeException
import kotlin.math.abs
import kotlin.math.round
import androidx.core.view.isVisible
import com.example.mj_player_tv.viewmodel.SeriesViewModel
import com.example.mj_player_tv.viewmodel.SeriesViewModelFactory


@UnstableApi
class PlaySeriesFragment : Fragment(R.layout.fragment_play_series) {

    private lateinit var seriesSelectionAdapter: SeriesSelectionAdapter

    private var _binding: FragmentPlaySeriesBinding? = null

    private val binding get() = _binding!!

    private var mediaPlayer: MediaPlayer? = null
    private var libVLC: LibVLC? = null

    private var player: ExoPlayer? = null

    private var isFirstOpen = true

    private var playWithVlc = false

    private var stopRunnable = false
    private var currentMediaItemDuration = 0L

    private var ishudContainerVisbile = false

    private var isVideoPlaying = true

    var changedAudioTrack = false
    var changedSubtitleTrack = false
    var changedVideoTrack = false

    private var totalDuration = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val hideHudRunnable = Runnable {
        hideHudContainer()
    }

    val spoolHandler = Handler(Looper.getMainLooper())
    var isSpooling = false
    var spoolRunnable: Runnable? = null
    var spoolPosition: Long = 0 // Temporäre Position fürs Spulen
    val maxIncrement = 30000L
    var incrementChangeDelay: Long = 2000L

    private val databaseHandler = Handler(Looper.getMainLooper())

    private var audioTracks: MutableList<TrackInfo> = mutableListOf()
    private var videoTracks: MutableList<TrackInfo> = mutableListOf()
    private var subTitleTacks: MutableList<TrackInfo> = mutableListOf()
    private var aspectRatioList = mutableListOf(
        "1:1",
        "4:3",
        "16:9",
        "1.85:1",
        "2.40:1",
        "2.35:1",
        "3:2",
        "9:16",
        "21:9",
        "2.00:1",
        "2.39:1",
        "2.76:1",
        "14:9",
        "16:10",
        "2.21:1"
    )
    private var currentAspectRatio: String = ""
    private var defaultAspectRatio: String = ""

    private var currentSeriesUrl: String = ""

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)
    private val episodeBox: Box<EpisodesOB> = ObjectBox.store.boxFor(EpisodesOB::class.java)
    private val seasonBox: Box<SeasonsOB> = ObjectBox.store.boxFor(SeasonsOB::class.java)
    private val seriesBox: Box<SeriesOB> = ObjectBox.store.boxFor(SeriesOB::class.java)

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
        _binding = FragmentPlaySeriesBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showProgressBar()
        prepareRecyclerview()

        playWithVlc = if (helpViewModel.playSeriesSelectionModified != null) {
            if (helpViewModel.playSeriesSelectionModified == 0) {
                false
            } else {
                true
            }
        } else {
            val settings = helpViewModel.settings
            if (settings != null) {
                if (settings.playMoviesWithVlc) {
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }

        if (playWithVlc) {
            binding.exoplayerVideoview.visibility = View.GONE
            binding.videoView.visibility = View.VISIBLE

            val options = ArrayList<String>()
            options.add("--http-reconnect")
            options.add("--network-caching=1000")
            options.add("--ffmpeg-hw")
            libVLC = LibVLC(this@PlaySeriesFragment.requireContext(), options)
            // Initialisiere den MediaPlayer
            mediaPlayer = MediaPlayer(libVLC)

            binding.videoView.requestFocus()
            binding.videoView.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    if (!ishudContainerVisbile) {
                        closeFragment()
                        return@setOnKeyListener true
                    } else {
                        hideHudContainer()
                        return@setOnKeyListener true
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                    if (!ishudContainerVisbile && mediaPlayer?.isPlaying == true) {
                        showHudContainer()
                    } else {
                        hideHudContainer()
                    }
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    if (!ishudContainerVisbile && isVideoPlaying) {
                        showHudContainer()
                    } else {
                        hideHudContainer()
                    }
                }
                return@setOnKeyListener true
            }
        } else {
            binding.videoView.visibility = View.GONE

            binding.exoplayerVideoview.visibility = View.VISIBLE

            binding.exoplayerVideoview.requestFocus()

            binding.exoplayerVideoview.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    if (!ishudContainerVisbile) {
                        closeFragment()
                        return@setOnKeyListener true
                    } else {
                        hideHudContainer()
                        return@setOnKeyListener true
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                    if (!ishudContainerVisbile && player?.isPlaying == true) {
                        showHudContainer()
                        return@setOnKeyListener true
                    } else {
                        hideHudContainer()
                        return@setOnKeyListener true
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    if (!ishudContainerVisbile && isVideoPlaying) {
                        showHudContainer()
                        return@setOnKeyListener true
                    } else {
                        hideHudContainer()
                        return@setOnKeyListener true
                    }
                }
                return@setOnKeyListener true
            }
        }

        getData()


        binding.ivPauseVideo.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                hideHudContainer()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                isVideoPlaying = false
                if (playWithVlc) {
                    mediaPlayer?.pause()
                } else {
                    player?.pause()
                }
                handler.removeCallbacks(hideHudRunnable)
                binding.ivPauseVideo.visibility = View.GONE
                binding.ivPlayVideo.visibility = View.VISIBLE
                binding.ivReplayVideo.nextFocusRightId = R.id.iv_playVideo
                binding.ivForwardVideo.nextFocusLeftId = R.id.iv_playVideo
                binding.ivPlayVideo.requestFocus()
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                showOptions()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusSeekBar()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusReplayBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusForwardBtn()
                return@setOnKeyListener true
            }
            return@setOnKeyListener true
        }


        binding.ivPlayVideo.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                hideHudContainer()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                isVideoPlaying = true
                if (playWithVlc) {
                    mediaPlayer?.play()
                } else {
                    player?.play()
                }
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                binding.ivPlayVideo.visibility = View.GONE
                binding.ivPauseVideo.visibility = View.VISIBLE
                binding.ivReplayVideo.nextFocusRightId = R.id.iv_pauseVideo
                binding.ivForwardVideo.nextFocusLeftId = R.id.iv_pauseVideo
                binding.ivPauseVideo.requestFocus()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                showOptions()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusSeekBar()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusReplayBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusForwardBtn()
                return@setOnKeyListener true
            }
            return@setOnKeyListener true
        }

        binding.ivForwardVideo.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                hideHudContainer()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                seekForward()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                showOptions()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusSeekBar()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                if (isVideoPlaying) {
                    focusPauseBtn()
                } else {
                    focusPlayBtn()
                }
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusNextEpisodeBtn()
                return@setOnKeyListener true
            }
            return@setOnKeyListener true
        }

        binding.ivReplayVideo.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                hideHudContainer()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                seekBackward()
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                showOptions()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusSeekBar()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusPreviousEpisodeBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                if (isVideoPlaying) {
                    focusPauseBtn()
                } else {
                    focusPlayBtn()
                }
                return@setOnKeyListener true
            }
            return@setOnKeyListener true
        }

        binding.ivPreviousEpisode.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                hideHudContainer()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                playPreviousEpisode()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                showOptions()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusSeekBar()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusPreviousEpisodeBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusReplayBtn()
                return@setOnKeyListener true
            }
            return@setOnKeyListener true
        }

        binding.ivNextEpisode.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                hideHudContainer()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                playNextEpisode()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                showOptions()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusSeekBar()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusForwardBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                handler.postDelayed(hideHudRunnable, 8000)
                focusNextEpisodeBtn()
                return@setOnKeyListener true
            }
            return@setOnKeyListener true
        }


        binding.relLayoutAudioTrack.setOnFocusChangeListener { _, hasFocus ->
            binding.tvAudioinfo.isSelected = hasFocus
            binding.ivAudio.isSelected = hasFocus
            binding.relLayoutAudioTrack.isSelected = hasFocus
        }

        binding.relLayoutAudioTrack.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                hideOptions()
                handler.postDelayed(hideHudRunnable, 8000)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                helpViewModel.movieSelectionOption = 0
                showAudioTrackDialog()
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                focusAudioBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                hideOptions()
                handler.postDelayed(hideHudRunnable, 8000)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                focusSubtitleBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                focusAudioDelayBtn()
                return@setOnKeyListener true
            }
            return@setOnKeyListener true
        }

        binding.relLayoutSubtitleTrack.setOnFocusChangeListener { _, hasFocus ->
            binding.tvSubtitleinfo.isSelected = hasFocus
            binding.ivSubtitle.isSelected = hasFocus
            binding.relLayoutSubtitleTrack.isSelected = hasFocus
        }

        binding.relLayoutSubtitleTrack.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                hideOptions()
                handler.postDelayed(hideHudRunnable, 8000)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                helpViewModel.movieSelectionOption = 1
                showSubtitleDialog()
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                focusSubtitleBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                hideOptions()
                handler.postDelayed(hideHudRunnable, 8000)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                focusVideoBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                focusAudioBtn()
                return@setOnKeyListener true
            }
            return@setOnKeyListener true
        }

        binding.relLayoutVideoTrack.setOnFocusChangeListener { _, hasFocus ->
            binding.tvVideotrackinfo.isSelected = hasFocus
            binding.ivVideoTrack.isSelected = hasFocus
            binding.relLayoutVideoTrack.isSelected = hasFocus
        }

        binding.relLayoutVideoTrack.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                hideOptions()
                handler.postDelayed(hideHudRunnable, 8000)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                helpViewModel.movieSelectionOption = 2
                showVideoTrackDialog()
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                focusVideoBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                hideOptions()
                handler.postDelayed(hideHudRunnable, 8000)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                focusAspectRationBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                focusSubtitleBtn()
                return@setOnKeyListener true
            }
            return@setOnKeyListener true
        }

        binding.relLayoutAspectratiodetail.setOnFocusChangeListener { _, hasFocus ->
            binding.tvAspectratio.isSelected = hasFocus
            binding.tvRatioinfo.isSelected = hasFocus
            binding.relLayoutAspectratiodetail.isSelected = hasFocus
        }

        binding.relLayoutAspectratiodetail.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                hideOptions()
                handler.postDelayed(hideHudRunnable, 8000)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                helpViewModel.movieSelectionOption = 3
                showAspectRatioDialog()
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                focusAspectRationBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                hideOptions()
                handler.postDelayed(hideHudRunnable, 8000)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                focusInfoBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                focusVideoBtn()
                return@setOnKeyListener true
            }
            return@setOnKeyListener true
        }

        binding.relLayoutInfodetail.setOnFocusChangeListener { _, hasFocus ->
            binding.tvInfoinfo.isSelected = hasFocus
            binding.tvInfo.isSelected = hasFocus
            binding.relLayoutInfodetail.isSelected = hasFocus
        }

        binding.relLayoutInfodetail.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                hideOptions()
                handler.postDelayed(hideHudRunnable, 8000)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                if (helpViewModel.currentFocusedSerie != null) {
                    showDescriptionDialog(helpViewModel.currentFocusedSerie, helpViewModel.currentFocusedEpisode)
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                focusInfoBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                hideOptions()
                handler.postDelayed(hideHudRunnable, 8000)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                focusInfoBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                focusAspectRationBtn()
                return@setOnKeyListener true
            }
            return@setOnKeyListener true
        }

        binding.relLayoutAudioDelay.setOnFocusChangeListener { _, hasFocus ->
            binding.tvAudiodelayinfo.isSelected = hasFocus
            binding.ivAudioDelay.isSelected = hasFocus
            binding.relLayoutAudioDelay.isSelected = hasFocus
        }

        binding.relLayoutAudioDelay.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                hideOptions()
                handler.postDelayed(hideHudRunnable, 8000)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                helpViewModel.movieSelectionOption = 4
                showAudioTrackDialog()
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                focusAudioDelayBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                hideOptions()
                handler.postDelayed(hideHudRunnable, 8000)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                focusAudioBtn()
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                focusAudioDelayBtn()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        binding.tvDialogSeriesTitle.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                closeSeriesDetailDialog()
                return@setOnKeyListener true
            }
            return@setOnKeyListener true
        }

        binding.seekBar.setOnFocusChangeListener { seekbar, hasFocus ->
            if (hasFocus) {
                handler.postDelayed(hideHudRunnable, 8000)
                binding.seekBar.setUnplayedColor(resources.getColor(R.color.white))
                binding.seekBar.setPlayedColor(resources.getColor(R.color.light_blue))
                binding.seekBar.setScrubberColor(resources.getColor(R.color.light_blue))
            } else {
                binding.seekBar.setUnplayedColor(resources.getColor(R.color.light_mid_grey))
                binding.seekBar.setPlayedColor(resources.getColor(R.color.light_blue_darker))
                binding.seekBar.setScrubberColor(resources.getColor(R.color.light_blue_darker))
            }
        }


        binding.seekBar.setOnKeyListener { _, keyCode, event ->
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val isForward = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    var increment = 10000L // 30 Sekunden Schritte
                    val maxPosition = if (playWithVlc) mediaPlayer?.length ?: 0 else player?.duration ?: 0

                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> {
                            if (isSpooling) return@setOnKeyListener true // Verhindere doppelte ACTION_DOWN-Ereignisse

                            handler.removeCallbacks(hideHudRunnable)
                            isSpooling = true

                            // Initiale Position setzen
                            spoolPosition = if (playWithVlc) mediaPlayer?.time ?: 0 else player?.currentPosition ?: 0

                            // Vorherige Callbacks entfernen (wenn vorhanden)
                            spoolHandler.removeCallbacksAndMessages(null)

                            spoolRunnable = object : Runnable {
                                override fun run() {
                                    if (isSpooling) {
                                        // Simuliertes Spulen durch Änderung des SeekBar-Werts
                                        spoolPosition = if (isForward) {
                                            (spoolPosition + increment).coerceAtMost(maxPosition)
                                        } else {
                                            (spoolPosition - increment).coerceAtLeast(0)
                                        }

                                        binding.seekBar.setPosition(spoolPosition)
                                        binding.tvCurrentTime.text = formatTime(spoolPosition)
                                        spoolHandler.postDelayed({
                                            increment = (increment + 10000L).coerceAtMost(maxIncrement)
                                        }, incrementChangeDelay)
                                        // Wiederhole den Vorgang alle 100ms für eine weichere Animation
                                        spoolHandler.postDelayed(this, 10)
                                    }
                                }
                            }
                            spoolHandler.post(spoolRunnable!!)
                            return@setOnKeyListener true
                        }

                        KeyEvent.ACTION_UP -> {
                            isSpooling = false
                            spoolHandler.removeCallbacksAndMessages(null)

                            // Nach dem Spulen zur neuen Position springen
                            if (playWithVlc) {
                                mediaPlayer?.time = spoolPosition
                            } else {
                                player?.seekTo(spoolPosition)
                            }

                            // UI aktualisieren
                            updateUI()
                            handler.postDelayed(hideHudRunnable, 8000)
                            return@setOnKeyListener true
                        }
                    }
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        hideHudContainer()
                        return@setOnKeyListener  true
                    }
                }
            }
            false
        }

        binding.tvAspectratioDefault.setOnClickListener {
            if (playWithVlc) {
                val formattedAspectRatio = if (defaultAspectRatio.contains(":")) {
                    defaultAspectRatio.substringBefore(":")
                } else {
                    defaultAspectRatio
                }
                mediaPlayer?.aspectRatio = formattedAspectRatio
            } else {
                setExoPlayerAspectRatio(defaultAspectRatio)
            }
            binding.tvAspectratio.text = defaultAspectRatio
            currentAspectRatio = defaultAspectRatio
            binding.tvAspectratioDefault.visibility = View.GONE
        }
    }

    // Aktualisiere UI nach dem Spulen
    fun updateUI() {
        binding.tvCurrentTime.text = formatTime(player?.currentPosition ?: mediaPlayer?.time ?: 0)
        binding.seekBar.requestFocus()
    }

    private fun prepareRecyclerview() {
        seriesSelectionAdapter = SeriesSelectionAdapter(onClickListener, this, helpViewModel)
        binding.rvSeriesSelection.apply {
            adapter = seriesSelectionAdapter
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(false, false)
        }
    }

    private fun showOptions() {
        binding.arrowShowoptions.visibility = View.INVISIBLE
        binding.arrowHideoptions.visibility = View.VISIBLE
        binding.relLayoutPlaybackControls.visibility = View.INVISIBLE
        binding.relLayoutOptions.visibility = View.VISIBLE
        binding.tvInfo.isSelected = true
        binding.tvInfoinfo.isSelected = true
        binding.relLayoutInfodetail.isSelected = true
        binding.relLayoutInfodetail.requestFocus()
    }

    private fun hideOptions() {
        binding.relLayoutOptions.visibility = View.INVISIBLE
        binding.relLayoutPlaybackControls.visibility = View.VISIBLE
        binding.arrowHideoptions.visibility = View.INVISIBLE
        binding.arrowShowoptions.visibility = View.VISIBLE
        if (isVideoPlaying) {
            binding.ivPauseVideo.requestFocus()
        } else {
            binding.ivPlayVideo.requestFocus()
        }
    }

    fun focusToAudioFromSelection() {
        seriesSelectionAdapter.submitList(null)
        changeVisibilityOn()
        binding.relLayoutSeriesSelection.visibility = View.INVISIBLE
        binding.relLayoutAudioTrack.requestFocus()
    }

    fun focusToAudioDelayFromSelection() {
        changeVisibilityOn()
        binding.relLayoutSeriesSelection.visibility = View.INVISIBLE
        binding.relLayoutAudioDelay.requestFocus()
    }

    fun focusToVideoFromSelection() {
        seriesSelectionAdapter.submitList(null)
        changeVisibilityOn()
        binding.relLayoutSeriesSelection.visibility = View.INVISIBLE
        binding.relLayoutVideoTrack.requestFocus()
    }

    fun focusToSubTitleFromSelection() {
        seriesSelectionAdapter.submitList(null)
        changeVisibilityOn()
        binding.relLayoutSeriesSelection.visibility = View.INVISIBLE
        binding.relLayoutSubtitleTrack.requestFocus()
    }

    fun focusToAspectRatioFromSelection() {
        seriesSelectionAdapter.submitList(null)
        changeVisibilityOn()
        binding.relLayoutSeriesSelection.visibility = View.INVISIBLE
        binding.relLayoutAspectratiodetail.requestFocus()
    }

    fun focusToInfoFromSelection() {
        binding.relLayoutSeriesSelection.visibility = View.INVISIBLE
        binding.relLayoutInfodetail.requestFocus()
    }

    private fun focusAudioBtn() {
        binding.seekBar.isSelected = false
        binding.relLayoutAudioTrack.requestFocus()
    }

    private fun focusAudioDelayBtn() {
        binding.seekBar.isSelected = false
        binding.relLayoutAudioDelay.requestFocus()
    }

    private fun focusSubtitleBtn() {
        binding.seekBar.isSelected = false
        binding.relLayoutSubtitleTrack.requestFocus()
    }
    
    private fun focusVideoBtn() {
        binding.seekBar.isSelected = false
        binding.relLayoutVideoTrack.requestFocus()
    }
    
    private fun focusAspectRationBtn() {
        binding.seekBar.isSelected = false
        binding.relLayoutAspectratiodetail.requestFocus()
    }

    private fun focusInfoBtn() {
        binding.seekBar.isSelected = false
        binding.relLayoutInfodetail.requestFocus()
    }

    private fun focusPreviousEpisodeBtn() {
        binding.seekBar.isSelected = false
        binding.ivPreviousEpisode.requestFocus()
    }

    private fun focusNextEpisodeBtn() {
        binding.seekBar.isSelected = false
        binding.ivNextEpisode.requestFocus()
    }

    private fun focusForwardBtn() {
        binding.seekBar.isSelected = false
        binding.ivForwardVideo.requestFocus()
    }

    private fun focusReplayBtn() {
        binding.seekBar.isSelected = false
        binding.ivReplayVideo.requestFocus()
    }

    private fun focusPauseBtn() {
        binding.seekBar.isSelected = false
        binding.ivPauseVideo.requestFocus()
    }

    private fun focusPlayBtn() {
        binding.seekBar.isSelected = false
        binding.ivPlayVideo.requestFocus()
    }

    private fun focusSeekBar() {
        binding.seekBar.requestFocus()
    }

    private fun playPreviousEpisode() {
        val currentEpisodeIndex = helpViewModel.focusedEpisodes?.indexOf(helpViewModel.currentFocusedEpisode)
        val currentSeasonIndex = helpViewModel.focusedSeasons?.indexOf(helpViewModel.currentFocusedSeason)
        updateSerieSeasonEpisode(helpViewModel.currentFocusedEpisode, helpViewModel.currentFocusedSeason, helpViewModel.currentFocusedSerie)
        if (currentEpisodeIndex != null && currentEpisodeIndex > 0) {
            player?.stop() ?: mediaPlayer?.stop()
            val currentSeasonNumber = helpViewModel.currentFocusedEpisode?.seasonNumber
            val newEpisode = helpViewModel.focusedEpisodes?.get(currentEpisodeIndex - 1)
            helpViewModel.currentFocusedEpisode = newEpisode
            seriesViewModel.changeEpisodeInfoUi = true
            if (currentSeasonNumber != newEpisode?.seasonNumber && currentSeasonIndex != null) {
                helpViewModel.currentFocusedSeason = helpViewModel.focusedSeasons?.get(currentSeasonIndex - 1)
                seriesViewModel.requestFocusOnNextEpisode()
                getData()
            } else {
                seriesViewModel.requestFocusOnNextEpisode()
                getData()
            }
        }
    }

    private fun playNextEpisode() {
        val currentEpisodeIndex = helpViewModel.focusedEpisodes?.indexOf(helpViewModel.currentFocusedEpisode)
        val currentSeasonIndex = helpViewModel.focusedSeasons?.indexOf(helpViewModel.currentFocusedSeason)
        val episodeIndexes = (helpViewModel.focusedEpisodes?.size?.minus(1)) ?: 0
        val seasonIndexes = (helpViewModel.focusedSeasons?.size?.minus(1)) ?: 0
        updateSerieSeasonEpisode(helpViewModel.currentFocusedEpisode, helpViewModel.currentFocusedSeason, helpViewModel.currentFocusedSerie)
        if (currentEpisodeIndex != null && currentEpisodeIndex < episodeIndexes) {
            player?.stop() ?: mediaPlayer?.stop()
            val currentSeasonNumber = helpViewModel.currentFocusedEpisode?.seasonNumber
            val newEpisode = helpViewModel.focusedEpisodes?.get(currentEpisodeIndex + 1)
            helpViewModel.currentFocusedEpisode = newEpisode
            seriesViewModel.changeEpisodeInfoUi = true
            if (currentSeasonNumber != newEpisode?.seasonNumber && currentSeasonIndex != null) {
                helpViewModel.currentFocusedSeason = helpViewModel.focusedSeasons?.get(currentSeasonIndex + 1)
                seriesViewModel.requestFocusOnNextEpisode()
                getData()
            } else {
                seriesViewModel.requestFocusOnNextEpisode()
                getData()
            }
        }
    }


    private fun seekForward() {
        // Hier wird die Wiedergabeposition vorwärts verschoben
        if (playWithVlc) {
            val newPosition = mediaPlayer?.time?.plus(10000)?.coerceAtMost(mediaPlayer?.length ?: 0) ?: 0
            mediaPlayer?.time = newPosition
        } else {
            val newPosition = (player?.currentPosition?.plus(10000))?.coerceAtMost(player?.duration ?: 0) ?: 0
            player?.seekTo(newPosition)
        }
    }

    private fun seekBackward() {
        // Hier wird die Wiedergabeposition rückwärts verschoben
        if (playWithVlc) {
            val newPosition = mediaPlayer?.time?.minus(10000)?.coerceAtLeast(0) ?: 0
            mediaPlayer?.time = newPosition
        } else {
            val newPosition = (player?.currentPosition?.minus(10000))?.coerceAtLeast(0) ?: 0
            player?.seekTo(newPosition)
        }
    }

    private fun showHudContainer() {
        handler.removeCallbacks(hideHudRunnable)
        ishudContainerVisbile = true
        binding.hudContainer.visibility = View.VISIBLE
        binding.seekBar.visibility = View.VISIBLE
        binding.arrowShowoptions.visibility = View.VISIBLE
        binding.relLayoutPlaybackControls.visibility = View.VISIBLE
        if (isVideoPlaying) {
            binding.ivPlayVideo.visibility = View.INVISIBLE
            binding.ivPauseVideo.visibility = View.VISIBLE
            binding.ivPauseVideo.requestFocus()
        } else {
            binding.ivPauseVideo.visibility = View.INVISIBLE
            binding.ivPlayVideo.visibility = View.VISIBLE
            binding.ivPlayVideo.requestFocus()
        }
        val thisSerie = helpViewModel.currentFocusedSerie
        val thisSeason = helpViewModel.currentFocusedSeason
        val thisEpisode = helpViewModel.currentFocusedEpisode
        binding.playSeriesname.text = thisSerie?.seriesName
        binding.playSeasonepisode.text = "S${thisSeason?.seasonNumber} E${thisEpisode?.episodeNumber}  -  ${thisEpisode?.episodeName}"
        handler.postDelayed(hideHudRunnable, 7000)
    }

    private fun hideHudContainer() {
        // Entferne das Runnable, falls es noch nicht ausgeführt wurde
        handler.removeCallbacks(hideHudRunnable)
        ishudContainerVisbile = false
        binding.hudContainer.visibility = View.GONE
        if (playWithVlc) {
            binding.videoView.requestFocus()
        } else {
            binding.exoplayerVideoview.requestFocus()
        }
    }

    private fun startDatabaseRunnable() {
        databaseHandler.post(updateDatabaseRunnable) // Startet das regelmäßige Update der SeekBar
    }

    private fun stopDatabaseRunnable() {
        databaseHandler.removeCallbacks(updateDatabaseRunnable) // Stoppt das Update der SeekBar
    }


    private val updateDatabaseRunnable = object: Runnable {
        private var lastUpdateTime = 0L
        override fun run() {
            currentMediaItemDuration = mediaPlayer?.media?.duration ?: player?.duration ?: 0L
            val currentPosition = mediaPlayer?.time ?: player?.currentPosition ?: 0L
            val percentagePlayed =
                if (currentMediaItemDuration > 0) {
                    currentPosition.toDouble() / currentMediaItemDuration
                } else {
                    0.0
                }
            helpViewModel.currentFocusedEpisode?.episodePercentagePlayed = percentagePlayed
            helpViewModel.currentFocusedEpisode?.currentPosition = currentPosition
            lastUpdateTime = currentPosition
            databaseHandler.postDelayed(this, 10000)
        }
    }



    private fun updateSeriesInRV() {
        seriesViewModel.requestUpdateSerieInRV()
    }

    private fun initializeExoPlayer(url: String) {
        binding.seriesPlayingError.visibility = View.INVISIBLE
        player = ExoPlayer.Builder(requireContext())
            .setRenderersFactory(
                DefaultRenderersFactory(requireContext())
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            )
            .build()
        currentSeriesUrl = url
// Listener NACH dem Build setzen
        player?.addListener(handlePlaybackStateListener)
        player?.addListener(playerErrorListener)
        player?.addAnalyticsListener(analyticsListener)
        player?.setVideoFrameMetadataListener(videoFrameMetadataListener)

        // Player mit der View verbinden
        binding.exoplayerVideoview.player = player
        binding.exoplayerVideoview.useController = false
        val mediaSource = createMediaSource(url)

        player?.setMediaSource(mediaSource)
        player?.prepare()
        player?.seekTo(helpViewModel.currentFocusedEpisode?.currentPosition ?: 0)
        player?.playWhenReady = true
    }

    fun changeExoTrack(trackInfo: TrackInfo) {
        if (player != null && trackInfo.group != null) {
            try {
                player!!.trackSelectionParameters =
                    player!!.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(
                            TrackSelectionOverride(trackInfo.group.mediaTrackGroup, trackInfo.trackId)
                        )
                        .build()
            } catch (e: Exception) {
                Toast.makeText(this@PlaySeriesFragment.requireActivity(), "$e", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createMediaSource(url: String): MediaSource {
        val mediaItem = MediaItem.fromUri(url)
        val dataSourceFactory = DefaultHttpDataSource.Factory()
        val extractorsFactory = DefaultExtractorsFactory()
        return when {
            url.endsWith(".mpd") -> {
                DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }

            url.endsWith(".m3u8") -> {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }

            else -> {
                ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                    .createMediaSource(mediaItem)
            }
        }
    }


    val analyticsListener = object : AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            val resolution = "${format.width}x${format.height}"
            binding.resolution.text = resolution
            binding.resolution.visibility = View.VISIBLE
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            val audioFormat = getAudioFormatDescription(format.channelCount)
            binding.audio.text = audioFormat
            binding.audio.visibility = View.VISIBLE
        }

        override fun onVideoSizeChanged(
            eventTime: AnalyticsListener.EventTime,
            videoSize: VideoSize
        ) {
            val width = videoSize.width
            val height = videoSize.height
            val pixelAspectRatio = videoSize.pixelWidthHeightRatio

            // Seitenverhältnis berechnen
            val aspectRatio = if (height != 0) (width * pixelAspectRatio) / height else 0f
            currentAspectRatio = matchAspectRatio(aspectRatio)
            binding.tvAspectratio.text = currentAspectRatio
            if (isFirstOpen) {
                defaultAspectRatio = currentAspectRatio
                isFirstOpen = false
            }
            Log.d("AspectRatio", "Aspect Ratio: $aspectRatio")
        }

        override fun onTracksChanged(eventTime: AnalyticsListener.EventTime, tracks: Tracks) {
            audioTracks.clear()
            videoTracks.clear()
            subTitleTacks.clear()
            for (groupIndex in 0 until tracks.groups.size) {
                val group = tracks.groups[groupIndex]

                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> { // Audio Tracks
                        for (i in 0 until group.mediaTrackGroup.length) {
                            val trackFormat = group.mediaTrackGroup.getFormat(i)
                            val language = when {
                                trackFormat.label != null && trackFormat.label != "und" -> trackFormat.label
                                trackFormat.language != null && trackFormat.language != "und" -> getLanguageName(trackFormat.language)
                                else -> "Undefined"
                            }
                            val codec = trackFormat.sampleMimeType?.removePrefix("audio/") ?: "Undefined"
                            val audioFormat = getAudioFormatDescription(trackFormat.channelCount)
                            val formattedFreq = formatFrequency(trackFormat.sampleRate)
                            val sampleRate = "$audioFormat / $formattedFreq"
                            val descr = "$language - $codec - [$sampleRate]"
                            val isSelected = group.isTrackSelected(i)
                            val isSupported = group.isTrackSupported(i)
                            audioTracks.add(TrackInfo(descr, i, isSelected, isSupported, group))
                        }
                    }

                    C.TRACK_TYPE_VIDEO -> { // Video Tracks
                        for (i in 0 until group.mediaTrackGroup.length) {
                            val trackFormat = group.mediaTrackGroup.getFormat(i)
                            val descr = "${trackFormat.width}x${trackFormat.height} - [${trackFormat.codecs}]"
                            val isSelected = group.isTrackSelected(i)
                            val isSupported = group.isTrackSupported(i)
                            videoTracks.add(TrackInfo(descr, i, isSelected, isSupported, group))
                        }
                    }

                    C.TRACK_TYPE_TEXT -> { // Subtitle Tracks (Text)
                        for (i in 0 until group.mediaTrackGroup.length) {
                            val trackFormat = group.mediaTrackGroup.getFormat(i)
                            val language = when {
                                trackFormat.label != null && trackFormat.label != "und" -> trackFormat.label
                                trackFormat.language != null -> getLanguageName(trackFormat.language)
                                else -> "Undefined"
                            } ?: "Undefined"
                            val isSelected = group.isTrackSelected(i)
                            val isSupported = group.isTrackSupported(i)
                            subTitleTacks.add(TrackInfo(language, i, isSelected, isSupported, group))
                        }
                    }
                }
            }

        }
        override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {

            binding.playerProgressBar.visibility = View.GONE
            stopDatabaseRunnable()
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                delay(500) // 1,5 Sekunden warten
                binding.playerProgressBar.visibility = View.VISIBLE
            }

            val cause = error.cause
            val errorMessage = when (cause) {
                is IllegalArgumentException -> {
                    if (cause.message?.contains("MalformedURLException") == true) {
                        "The specified URL is invalid!"
                    } else {
                        "Can't play stream! The file may be incompatible or corrupt."
                    }
                }

                is SocketTimeoutException -> "Timeout error, can't connect to server!"
                is DrmSessionException -> "DRM error: ${cause.message ?: "Unable to play protected content."}"
                is UnknownHostException -> {
                    val host = cause.message?.substringAfter("Unable to resolve host ")?.substringBefore(":") ?: "Unknown Host"
                    "Can't connect to $host.."
                }
                is SSLHandshakeException -> "SSL/TLS error: Unable to establish a secure connection."
                else -> "Unknown error occurred: ${cause?.message} Please try again later.."
            }
            Log.d("EXOPLAYER INTERN ERROR", "$errorMessage")
        }
        override fun onEvents(player: Player, events: AnalyticsListener.Events) {
            if (events.contains(AnalyticsListener.EVENT_DRM_SESSION_MANAGER_ERROR) ||
                events.contains(AnalyticsListener.EVENT_VIDEO_CODEC_ERROR) ||
                events.contains(AnalyticsListener.EVENT_AUDIO_CODEC_ERROR)
            ) {
                stopDatabaseRunnable()
                binding.playerProgressBar.visibility = View.GONE
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.playerProgressBar.visibility =
                        View.VISIBLE // Zurücksetzen der Sichtbarkeit
                }, 250) // 500 Millisekunden = 0,5 Sekunden

            }
        }
    }



    var lastFrameTimeNs: Long? = null // Zeitstempel des letzten Frames
    var frameCount = 0               // Anzahl der gerenderten Frames
    var totalFrameIntervalNs = 0L    // Summe der Zeitdifferenzen zwischen Frames
    var totalFrameCount = 0          // Anzahl der berechneten FPS-Werte
    var lastFpsToShow = 0

    val videoFrameMetadataListener =
        VideoFrameMetadataListener { presentationTimeUs, releaseTimeNs, format, mediaFormat ->
            if (lastFrameTimeNs != null) {
                // Berechne die Zeitdifferenz zum letzten Frame
                val intervalNs = releaseTimeNs - lastFrameTimeNs!!
                totalFrameIntervalNs += intervalNs
                frameCount++
                // FPS berechnen, wenn genügend Frames gesammelt wurden (z.B. 30)
                if (frameCount >= 30) {
                    val avgFrameIntervalSeconds = totalFrameIntervalNs / 1_000_000_000.0
                    val fps = frameCount / avgFrameIntervalSeconds
                    Log.d("ExoPlayer FPS", "Calculated FPS: $fps")
                    var fps2 = 0.0
                    totalFrameCount++
                    // Zurücksetzen
                    frameCount = 0
                    totalFrameIntervalNs = 0L
                    if (totalFrameCount == 3) {
                        fps2 = frameCount / avgFrameIntervalSeconds
                    }
                    if (totalFrameCount == 5) {
                        val fpsToCalculate = (fps + fps2) / 2
                        val fpsToShow = roundToNearestStandardFps(fpsToCalculate)
                        if (fpsToShow != lastFpsToShow) {
                            lastFpsToShow = fpsToShow
                            activity?.runOnUiThread {
                                binding.fps.text = "FPS: $fpsToShow"
                                binding.fps.visibility = View.VISIBLE
                                binding.tvDialogSeriesVideodetailsfps.text = "FPS: $fpsToShow"
                                binding.tvDialogSeriesVideodetailsfps.visibility = View.VISIBLE
                            }
                        }
                        totalFrameCount = 0
                    }
                }
            }
            // Setze den aktuellen Frame als letzten Frame
            lastFrameTimeNs = releaseTimeNs
        }

    fun roundToNearestStandardFps(calculatedFps: Double): Int {
        // Liste der typischen FPS-Werte
        val standardFpsValues = listOf(24, 25, 30, 50, 60, 120)

        // Suche den nächsten Wert
        return standardFpsValues.minByOrNull { kotlin.math.abs(it - calculatedFps) } ?: calculatedFps.toInt()
    }



    val handlePlaybackStateListener = object : Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    totalDuration = player?.duration ?: 0L
                    binding.seekBar.setDuration(totalDuration)
                    val formattedMaxDuration = formatTime(totalDuration)
                    binding.tvTotalTime.text = formattedMaxDuration
                    binding.seriesPlayingError.visibility = View.GONE
                    hideProgressBar()
                    isVideoPlaying = true
                    startDatabaseRunnable()
                    startPeriodicExoPlayerUpdate()
                }
                Player.STATE_BUFFERING -> {
                    showProgressBar()
                }
                Player.STATE_IDLE -> {
                    "IDLE"
                }
                Player.STATE_ENDED -> {
                    stopDatabaseRunnable()
                    stopPeriodicExoPlayerUpdate()
                }
            }
        }

        override fun onRenderedFirstFrame() {
            super.onRenderedFirstFrame()
            if (!ishudContainerVisbile) {
                showHudContainer()
            }
        }
    }

    val playerErrorListener = object : Listener {
        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause
            stopDatabaseRunnable()
            val errorMessage = if (cause is HttpDataSource.HttpDataSourceException) {
                Log.d("EXOPLAYERMOVIE", "ERROR: $cause")
                if (cause is InvalidResponseCodeException) {
                    when (cause.responseCode) {
                        401 -> {
                            "401: Unauthorized - Check your credentials!"
                        }
                        403 -> {
                            "403: Forbidden - No permission to access the requested content!"
                        }
                        404 -> {
                            "404: Not found - Requested content not available!"
                        }
                        502 ->  {
                            "502: Bad Gateway - Try again later!"
                        }
                        503 -> {
                            "503: Service Unavailable - Try again later!"
                        }
                        504 -> {
                            "504: Gateway Timeout - Try again later!"
                        } else -> {
                        "${cause.responseCode}: ${cause.responseMessage}"
                    }
                    }
                } else {
                    if (cause.message?.contains("SocketTimeoutException") == true) {
                        "Failed to connect: Timeout Exception"
                    } else {
                        "${cause.cause}"
                    }
                }
            } else if (cause is ParserException) {
                if (helpViewModel.currentlyPlayingMovieUrl.isNotEmpty()) {
                    initializeExoPlayer(helpViewModel.currentlyPlayingMovieUrl)
                    ""
                } else {
                    "Cant'play content. Retry!"
                }
            } else {
                "Unknown error occurred: ${cause?.message ?: "Please try again later.."}"
            }
            binding.playerProgressBar.visibility = View.GONE
            binding.seriesPlayingError.text = errorMessage
            binding.seriesPlayingError.visibility = View.VISIBLE
            binding.exoplayerVideoview.useController = false
        }
    }

    private val updateExoPlayerHandler = Handler(Looper.getMainLooper())
    private val updateExoPlayerRunnable = object : Runnable {
        override fun run() {
            player?.let {
                if (!isSpooling) {
                    binding.tvCurrentTime.text = formatTime(it.currentPosition)
                    binding.seekBar.setPosition(it.currentPosition)
                }
            }
            updateExoPlayerHandler.postDelayed(this, 500) // alle 500ms aktualisieren
        }
    }

    private fun startPeriodicExoPlayerUpdate() {
        updateExoPlayerRunnable.run()
    }

    private fun stopPeriodicExoPlayerUpdate() {
        updateExoPlayerHandler.removeCallbacks(updateExoPlayerRunnable)
    }

    private var vlcDialogCallbacks: org.videolan.libvlc.Dialog.Callbacks? = null

    private fun initializePlayer(url: String) {
        binding.seriesPlayingError.visibility = View.INVISIBLE
        if (helpViewModel.currentFocusedSerie != null) {
            currentSeriesUrl = url
            stopRunnable = false

            // Erstelle das Media-Objekt
            val media = Media(libVLC, Uri.parse(url))

            // Setting up video output
            mediaPlayer?.attachViews(binding.videoView, null, false, false)
            // Setze die Media auf den MediaPlayer
            mediaPlayer?.media = media

            mediaPlayer?.play()
            vlcDialogCallbacks = object : org.videolan.libvlc.Dialog.Callbacks {
                    override fun onDisplay(dialog: org.videolan.libvlc.Dialog.ErrorMessage) {
                        // Fehlerdialog anzeigen
                        binding.playerProgressBar.visibility = View.GONE
                        stopDatabaseRunnable()
                        binding.seriesPlayingError.visibility = View.VISIBLE
                        binding.seriesPlayingError.text = dialog.title
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                            delay(4000)
                            parentFragmentManager.popBackStack()
                            val mainFragment =
                                parentFragmentManager.findFragmentById(R.id.navHostFragment)
                            if (mainFragment is SeriesFragment) {
                                mainFragment.setFocusToSeries()
                            }
                        }
                    }

                    override fun onDisplay(dialog: org.videolan.libvlc.Dialog.LoginDialog) {
                        Log.d("VLC ERROR", "LOGIN: ${dialog.title}")

                    }

                    override fun onDisplay(dialog: org.videolan.libvlc.Dialog.QuestionDialog) {
                        Log.d("VLC ERROR", "QUESTION: ${dialog.title}")

                    }

                    override fun onDisplay(dialog: org.videolan.libvlc.Dialog.ProgressDialog) {
                        // Fortschrittsdialog anzeigen
                        Log.d("VLC ERROR", "PROGRESS: ${dialog.title}")

                    }

                    override fun onCanceled(dialog: org.videolan.libvlc.Dialog) {
                        when (dialog) {
                            is org.videolan.libvlc.Dialog.ErrorMessage -> {
                                Log.d("VLC ERROR", "CANCEL: ${dialog.title}")
                            }
                        }
                    }
                    override fun onProgressUpdate(dialog: org.videolan.libvlc.Dialog.ProgressDialog) {
                        // Fortschritt aktualisieren, falls nötig
                        Log.d("VLC ERROR", "PROGRESSUPDATE: ${dialog.title}")
                    }
                }

                mediaPlayer?.setEventListener { event ->
                    when (event.type) {
                        MediaPlayer.Event.Opening -> {
                            // Der Player öffnet das Medium
                            // Hier kannst du Aufgaben vor dem Start der Wiedergabe ausführen
                            binding.hudContainer.visibility = View.INVISIBLE
                            binding.fps.visibility = View.INVISIBLE
                            binding.resolution.visibility = View.INVISIBLE
                            binding.relLayoutPlaybackControls.visibility = View.INVISIBLE
                        }
                        MediaPlayer.Event.Playing -> {

                            isVideoPlaying = true
                            binding.ivPlayVideo.visibility = View.INVISIBLE
                            binding.ivPauseVideo.visibility = View.VISIBLE
                            if (isFirstOpen) {
                                mediaPlayer?.time = helpViewModel.currentFocusedMovie?.currentPosition ?: 0
                                totalDuration = mediaPlayer?.media?.duration ?: 0L
                                if (helpViewModel.currentSeriesAccount!!.isXtream) {
                                    helpViewModel.currentFocusedSerie?.seriesTime = (player?.duration?.div(
                                        (1000)
                                    ))?.toInt()
                                } else {
                                    helpViewModel.currentFocusedSerie?.seriesTime = (player?.duration?.div(
                                        (1000 * 60)
                                    ))?.toInt()
                                }
                                helpViewModel.currentFocusedSerie?.let { updateSeriesInRV() }
                                val formattedMaxDuration = formatTime(totalDuration)
                                binding.tvTotalTime.text = formattedMaxDuration
                                binding.seekBar.setDuration(totalDuration)
                                hideProgressBar()
                                binding.playSeriesname.text = helpViewModel.currentFocusedSerie?.seriesName ?: "NO NAME"
                                getVLCQualityInfo()
                                isFirstOpen = false
                                getVlcTracks()
                            }
                            startDatabaseRunnable()
                        }
                        MediaPlayer.Event.Paused -> {
                            // Die Wiedergabe wurde pausiert
                            // Hier kannst du auf das Pausieren der Wiedergabe reagieren
                            binding.ivPauseVideo.visibility = View.INVISIBLE
                            binding.ivPlayVideo.visibility = View.VISIBLE
                            binding.relLayoutPlaybackControls.visibility = View.VISIBLE
                            stopDatabaseRunnable()
                        }
                        MediaPlayer.Event.Stopped -> {
                            showProgressBar()
                            isVideoPlaying = false
                            // Die Wiedergabe wurde gestoppt
                            // Hier kannst du auf das Stoppen der Wiedergabe reagieren
                            stopDatabaseRunnable()
                        }
                        MediaPlayer.Event.EndReached -> {
                            isVideoPlaying = false
                            stopDatabaseRunnable()
                            mediaPlayer!!.release()
                            libVLC!!.release()
                            parentFragmentManager.popBackStack()
                            // Das Video wurde bis zum Ende abgespielt
                            // Hier kannst du auf das Erreichen des Endes der Wiedergabe reagieren
                        }
                        MediaPlayer.Event.Buffering -> {
                        }
                        MediaPlayer.Event.TimeChanged -> {
                            if (!isSpooling) {
                                mediaPlayer?.let {
                                    binding.seekBar.setPosition(it.time)
                                    binding.tvCurrentTime.text = formatTime(it.time)
                                }
                            }
                        }
                        MediaPlayer.Event.ESSelected -> {
                            audioTracks.clear()
                            videoTracks.clear()
                            subTitleTacks.clear()
                            getVlcTracks()
                        }
                        // Weitere Ereignisse können hier behandelt werden
                        else -> {
                            // Anderes Ereignis
                    }
                }
            }
        }
    }



    private fun getVLCQualityInfo() {
        val videoTrack = mediaPlayer?.currentVideoTrack
        videoTrack?.let {
            val fps = (it.frameRateNum.toDouble() / it.frameRateDen.toDouble()).toInt()
            if (fps != 0) {
                binding.fps.visibility = View.VISIBLE
                binding.fps.text = "FPS: $fps"
            } else {
                binding.fps.visibility = View.INVISIBLE
            }
            binding.resolution.visibility = View.VISIBLE
            binding.resolution.text = "${it.width}x${it.height}"
            val ar = mediaPlayer?.aspectRatio
            if (ar.isNullOrEmpty()) {
                val aspectRatio = it.width.toFloat() / it.height.toFloat()
                val format = matchAspectRatio(aspectRatio)
                binding.tvAspectratio.text = format
                currentAspectRatio = format
                defaultAspectRatio = format
            } else {
                binding.tvAspectratio.text = ar
                currentAspectRatio = ar
                defaultAspectRatio = ar
            }
        }
        val tracks = mediaPlayer!!.media!!.trackCount
        for (i in 0..tracks) {
            val track = mediaPlayer!!.media!!.getTrack(i)
            if (track != null) {
                when (track.type) {
                    IMedia.Track.Type.Audio -> {
                        (track as? IMedia.AudioTrack)?.let {
                            val audio = getAudioFormatDescription(it.channels)
                            binding.audio.visibility = View.VISIBLE
                            binding.audio.text = audio
                        }
                    }
                }
            }
        }
        binding.hudContainer.visibility = View.VISIBLE
        binding.relLayoutPlaybackControls.visibility = View.VISIBLE
        showHudContainer()
    }

    private fun getVlcTracks() {
        val currentVideoTrack = mediaPlayer?.currentVideoTrack
        videoTracks = mediaPlayer?.videoTracks?.map {
            val isSelected = it.id == currentVideoTrack?.id
            val trackInfo = TrackInfo(
                it.name,
                it.id,
                isSelected,
                true,
                null
            )
            trackInfo
        }?.toMutableList() ?: mutableListOf()

        val currentAudioTrackId = mediaPlayer?.audioTrack
        audioTracks = mediaPlayer?.audioTracks?.map {
            val isSelected = it.id == currentAudioTrackId
            val trackInfo = TrackInfo(
                it.name,
                it.id,
                isSelected,
                true,
                null
            )
            trackInfo
        }?.toMutableList() ?: mutableListOf()

        val currentSubtitleTrack = mediaPlayer?.spuTrack
        subTitleTacks = mediaPlayer?.spuTracks?.map {
            val isSelected = it.id == currentSubtitleTrack
            val trackInfo = TrackInfo(
                it.name,
                it.id,
                isSelected,
                true,
                null
            )
            trackInfo
        }?.toMutableList() ?: mutableListOf()

    }

    private fun hideProgressBar() {
        binding.playerProgressBar.visibility = View.INVISIBLE
    }

    private fun showProgressBar() {
        binding.playerProgressBar.visibility = View.VISIBLE
    }

    fun formatFrequency(hz: Int): String {
        return if (hz >= 1000) {
            "${hz / 1000} kHz"
        } else {
            "$hz Hz"
        }
    }

    fun formatTime(duration: Long): String {
        val hours = duration / 3600000
        val minutes = (duration % 3600000) / 60000
        val seconds = (duration % 60000) / 1000

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun getData() {
        binding.seriesPlayingError.visibility = View.INVISIBLE
        hideHudContainer()
        binding.hudContainer.visibility = View.INVISIBLE
        showProgressBar()
        helpViewModel.focusedSeasons?.forEachIndexed { index, seasonsOB ->
            Log.d("LOG DIE SEASONS", "INDEX: $index, SEASON: ${seasonsOB.seasonNumber}")
        }
        if (helpViewModel.currentFocusedSerie != null && helpViewModel.currentFocusedEpisode != null && helpViewModel.currentFocusedSeason != null) {
            Log.d("PLAYING NUMBERS", "SEASON: ${helpViewModel.currentFocusedSeason?.seasonNumber} EPISODE: ${helpViewModel.currentFocusedEpisode?.episodeNumber} FROM SEASON ${helpViewModel.currentFocusedEpisode?.seasonNumber}")
            viewLifecycleOwner.lifecycleScope.launch {
                val accountData = helpViewModel.currentSeriesAccount
                if (accountData != null) {
                    if (accountData.isStalker) {
                        val stalkerUrl = accountData.stalkerUrl
                        val macAddress = accountData.macAddress
                        val userAgent = accountData.userAgent
                        val token = accountData.token
                        val timeZone = accountData.timezone
                        if (!helpViewModel.currentFocusedEpisode!!.episodeCmd.isNullOrEmpty()) {
                            val response = stalkerViewModel.getSeriesLink(
                                stalkerUrl,
                                helpViewModel.currentFocusedEpisode!!.episodeCmd!!.removePrefix("ffmpeg ").trim(),
                                helpViewModel.currentFocusedEpisode!!.episodeNumber.toString(),
                                cookie = "mac=${macAddress}; stb_lang=en; timezone=$timeZone;",
                                token = "Bearer $token",
                                userAgent
                            ).await()
                            when (response) {
                                is Resource.Error -> {
                                    binding.seriesPlayingError.text = response.message
                                    binding.seriesPlayingError.visibility = View.VISIBLE
                                }

                                is Resource.Success -> {
                                    val playToken =
                                        response.data.toString().removePrefix("ffmpeg ").trim()
                                    try {
                                        hideProgressBar()
                                        if (playWithVlc) {
                                            initializePlayer(playToken)
                                        } else {
                                            initializeExoPlayer(playToken)
                                        }
                                    } catch (e: IllegalArgumentException) {
                                        // handle the exception, for example:
                                        Toast.makeText(
                                            requireContext(),
                                            "Unable to play the episode: ${
                                                (e.cause?.cause?.cause?.message?.removePrefix("Received "))
                                            }",
                                            Toast.LENGTH_SHORT
                                        )
                                            .show()
                                    }
                                }
                            }
                        }
                    } else {
                        val url = "${accountData.stalkerUrl}/series/${accountData.username}/${accountData.macAddress}/${helpViewModel.currentFocusedEpisode!!.episodeCmd}.${helpViewModel.currentFocusedEpisode!!.containerExtension}"
                        if (playWithVlc) {
                            initializePlayer(url)
                        } else {
                            initializeExoPlayer(url)
                        }
                    }
                }
            }
        }
    }

    private val onClickListener = SeriesSelectionAdapter.OnClickListener { selection ->
        if (helpViewModel.movieSelectionOption == 0) {
            changedAudioTrack = true
            if (playWithVlc) {
                mediaPlayer?.setAudioTrack(selection.trackId)
            } else {
                changeExoTrack(selection)
            }
        } else if (helpViewModel.movieSelectionOption == 1) {
            changedSubtitleTrack = true
            if (playWithVlc) {
                mediaPlayer?.setSpuTrack(selection.trackId)
            } else {
                changeExoTrack(selection)
            }
        } else if (helpViewModel.movieSelectionOption == 2) {
            changedVideoTrack = true
            if (playWithVlc) {
                mediaPlayer?.setVideoTrack(selection.trackId)
            } else {
                changeExoTrack(selection)
            }
        } else if (helpViewModel.movieSelectionOption == 3) {
            if (selection.trackName != defaultAspectRatio) {
                binding.rvSeriesSelection.nextFocusUpId = R.id.tv_aspectratio_default
                binding.tvAspectratioDefault.text = "Reset to default = $defaultAspectRatio"
                binding.tvAspectratioDefault.visibility = View.VISIBLE
            } else {
                binding.rvSeriesSelection.nextFocusUpId = R.id.rv_movie_selection
                binding.tvAspectratioDefault.text = ""
                binding.tvAspectratioDefault.visibility = View.GONE
            }
            if (playWithVlc) {
                val formattedAspectRatio = if (selection.trackName.contains(":")) {
                    selection.trackName.substringBefore(":")
                } else {
                    selection.trackName
                }
                mediaPlayer?.aspectRatio = formattedAspectRatio
            } else {
                setExoPlayerAspectRatio(selection.trackName)
            }
            binding.tvAspectratio.text = selection.trackName
            currentAspectRatio = selection.trackName
        } else {

        }
    }

    fun setExoPlayerAspectRatio(aspectRatio: String) {
        // Parsen des Seitenverhältnisses (z.B. "3:4" in Breite und Höhe)
        val (widthRatio, heightRatio) = aspectRatio.split(":").map { it.toFloat() }

        // Berechnen des Seitenverhältnisses
        val aspect = widthRatio / heightRatio

        // Zugriff auf PlayerView und SurfaceView
        val playerView = binding.exoplayerVideoview
        val surfaceView = playerView.videoSurfaceView

        // Berechne die neue Breite basierend auf der gewünschten Aspect Ratio
        if (surfaceView != null) {
            val layoutParams = surfaceView.layoutParams
            layoutParams.width = (surfaceView.height * aspect).toInt() // Breite anpassen
            surfaceView.layoutParams = layoutParams
            val track = seriesSelectionAdapter.currentList.firstOrNull { it.trackName == aspectRatio }
            if (track != null) {
                val pos = seriesSelectionAdapter.currentList.indexOf(track)
                seriesSelectionAdapter.setSelectedTrackInfo(track, pos)
                binding.rvSeriesSelection.setSelectedPosition(pos)
                binding.rvSeriesSelection.requestFocus()
            } else {
                binding.rvSeriesSelection.requestFocus()
            }
        } else {
            Toast.makeText(this@PlaySeriesFragment.requireActivity(), "Can't change aspect ratio!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAudioTrackDialog() {
        binding.tvSerieSelectionSize.text = ""
        binding.tvSerieSelection.text = "Choose audio track:"

        if (audioTracks.isEmpty()) {
            Toast.makeText(this@PlaySeriesFragment.requireActivity(), "No audio tracks available", Toast.LENGTH_SHORT).show()
            return // Kein AudioTrack vorhanden, Dialog nicht anzeigen
        } else {
            changeVisibilityOff()
            binding.tvSerieSelectionSize.text = "Total: ${audioTracks.size}"
            val audioList = audioTracks.toList()
            val currentAudioTrack = audioList.firstOrNull { it.isSelected }
            seriesSelectionAdapter.submitList(audioList)
            seriesSelectionAdapter.currentSelected = currentAudioTrack?.trackName ?: ""
            if (currentAudioTrack != null) {
                val position = seriesSelectionAdapter.currentList.indexOf(currentAudioTrack)
                binding.rvSeriesSelection.post {
                    binding.rvSeriesSelection.setSelectedPosition(position)
                }
            }
            binding.relLayoutSeriesSelection.visibility = View.VISIBLE
            binding.rvSeriesSelection.requestFocus()
        }
    }

    private fun showSubtitleDialog() {
        binding.tvSerieSelectionSize.text = ""
        binding.tvSerieSelection.text = "Choose subtitle track:"

        if (subTitleTacks.isNotEmpty()) {
            val subtitleList = subTitleTacks.toList()
            changeVisibilityOff()
            binding.tvSerieSelectionSize.text = "Total: ${subtitleList.size}"
            val currentSubtitleTrack = subtitleList.firstOrNull { it.isSelected == true }
            seriesSelectionAdapter.submitList(subtitleList)
            seriesSelectionAdapter.currentSelected = currentSubtitleTrack?.trackName ?: ""

            if (currentSubtitleTrack != null) {
                val position = seriesSelectionAdapter.currentList.indexOf(currentSubtitleTrack)
                binding.rvSeriesSelection.post {
                    binding.rvSeriesSelection.setSelectedPosition(position)
                }
            }
            binding.relLayoutSeriesSelection.visibility = View.VISIBLE
            binding.rvSeriesSelection.requestFocus()
        } else {
            Toast.makeText(this@PlaySeriesFragment.requireActivity(), "No subtitle tracks available", Toast.LENGTH_SHORT).show()
            return // Kein AudioTrack vorhanden, Dialog nicht anzeigen
        }
    }

    private fun showVideoTrackDialog() {
        binding.tvSerieSelectionSize.text = ""
        binding.tvSerieSelection.text = "Choose video track:"

        if (videoTracks.isEmpty()) {
            Toast.makeText(this@PlaySeriesFragment.requireActivity(), "No video tracks available", Toast.LENGTH_SHORT).show()
            return // Kein VideoTrack vorhanden, Dialog nicht anzeigen
        } else {
            val videoTrackList = videoTracks.toList()
            changeVisibilityOff()
            binding.tvSerieSelectionSize.text = "Total: ${videoTrackList.size}"
            val currentVideoTrack = seriesSelectionAdapter.currentList.firstOrNull { it.isSelected == true }
            seriesSelectionAdapter.submitList(videoTrackList)
            seriesSelectionAdapter.currentSelected = currentVideoTrack?.trackName ?: ""

            if (currentVideoTrack != null) {
                val position = seriesSelectionAdapter.currentList.indexOf(currentVideoTrack)
                binding.rvSeriesSelection.post {
                    binding.rvSeriesSelection.setSelectedPosition(position)
                }
            }
            binding.relLayoutSeriesSelection.visibility = View.VISIBLE
            binding.rvSeriesSelection.requestFocus()
        }
    }


    private fun showAspectRatioDialog() {
        binding.tvSerieSelectionSize.text = ""
        binding.tvSerieSelection.text = "Choose aspect ratio:"
        var currentId = 0

        changeVisibilityOff()
        binding.tvSerieSelectionSize.text = "Total: ${aspectRatioList.size}"
        if (currentAspectRatio != defaultAspectRatio) {
            binding.rvSeriesSelection.nextFocusUpId = R.id.tv_aspectratio_default
            binding.tvAspectratioDefault.text = "Reset to default = $defaultAspectRatio"
            binding.tvAspectratioDefault.visibility = View.VISIBLE
        } else {
            binding.rvSeriesSelection.nextFocusUpId = R.id.rv_movie_selection
            binding.tvAspectratioDefault.text = ""
            binding.tvAspectratioDefault.visibility = View.GONE
        }
        val ratioTracks = aspectRatioList.map {
            val isSelected = it == currentAspectRatio
            val isDefault = it == defaultAspectRatio
            TrackInfo(
                it,
                currentId++,
                isSelected,
                isDefault = isDefault
            )
        }
        val currentARinList = ratioTracks.firstOrNull { it.trackName == currentAspectRatio }
        seriesSelectionAdapter.submitList(ratioTracks)
        seriesSelectionAdapter.currentSelected = currentARinList?.trackName ?: ""
        if (currentARinList != null) {
            val position = seriesSelectionAdapter.currentList.indexOf(currentARinList)
            binding.rvSeriesSelection.post {
                binding.rvSeriesSelection.setSelectedPosition(position)
            }
        }
        binding.relLayoutSeriesSelection.visibility = View.VISIBLE
        binding.rvSeriesSelection.requestFocus()
    }

    private fun matchAspectRatio(aspectRatio: Float): String {
        val tolerance = 0.05f
        return when {
            abs(aspectRatio - 1.0f) < tolerance -> "1:1"
            abs(aspectRatio - 4.0f / 3.0f) < tolerance -> "4:3"
            abs(aspectRatio - 16.0f / 9.0f) < tolerance -> "16:9"
            abs(aspectRatio - 3.0f / 2.0f) < tolerance -> "3:2"
            abs(aspectRatio - 21.0f / 9.0f) < tolerance -> "21:9"
            abs(aspectRatio - 9.0f / 16.0f) < tolerance -> "9:16"
            abs(aspectRatio - 16.0f / 10.0f) < tolerance -> "16:10"
            abs(aspectRatio - 1.85f) < tolerance -> "1.85:1"  // Neues Seitenverhältnis
            abs(aspectRatio - 2.40f) < tolerance -> "2.40:1"  // Neues Seitenverhältnis
            abs(aspectRatio - 2.35f) < tolerance -> "2.35:1"  // Neues Seitenverhältnis
            else -> {
                val roundedAspectRatio = (round(aspectRatio * 10) / 10.0) // Runde auf eine Dezimalstelle
                String.format("%.1f:1", roundedAspectRatio)  // Formatierte Ausgabe
            }
        }
    }


    private fun showDescriptionDialog(serie: SeriesOB?, episode: EpisodesOB?) {
        if (serie != null) {
            binding.seriesDetailInfo.visibility = View.VISIBLE
            binding.linLayoutSeriesInfos.visibility = View.GONE
            val seriesdescription = serie.description
            val episodedescription = episode?.episodeDescription
            val title = serie.seriesName
            val image = serie.screenshot_uri
            binding.tvDialogSeriesSeasonepisode.text = "Season ${episode?.seasonNumber}  |  Episode ${episode?.episodeNumber}"

        if (playWithVlc) {
            val videoTrack = mediaPlayer?.currentVideoTrack
            if (videoTrack != null) {
                binding.tvDialogSeriesVideodetailscodec.text = "Codec: ${videoTrack.codec}"
            } else {
                binding.tvDialogSeriesVideodetailscodec.visibility = View.INVISIBLE
            }
            val audioTrackId = mediaPlayer?.audioTrack
            val audioTrack = audioTrackId?.let { mediaPlayer?.media?.getTrack(it) }
            if (audioTrack != null && audioTrack.type == IMedia.Track.Type.Audio) {
                val audiolanguage = audioTrack.language ?: "Keine Sprache angegeben"
                val codec = audioTrack.codec ?: "Unbekannter Codec"
                val audiodescription = audioTrack.description ?: "Keine Beschreibung"
                binding.tvDialogSeriesAudiodetailformat.text = audiodescription
                binding.tvDialogSeriesAudiodetailslang.text = audiolanguage
                binding.tvDialogSeriesAudiodetailscodec.text = codec
            } else {
                Toast.makeText(this@PlaySeriesFragment.requireActivity(), "KEIN AUDIOTRACK", Toast.LENGTH_SHORT).show()
            }

        } else {
            val videoTrack = player?.videoFormat
            if (videoTrack != null) {
                Log.d("INFODETAIL", "VIDEO: $videoTrack")
                binding.tvDialogSeriesVideodetailscodec.text = "Codec: ${videoTrack.codecs}"
            } else {

                binding.tvDialogSeriesVideodetailscodec.visibility = View.INVISIBLE
            }
            val audioTrack = player?.audioFormat
            if (audioTrack != null) {
                val audiolanguage = getLanguageName(audioTrack.language) ?: audioTrack.label ?: "Undefined"
                val codec = audioTrack.codecs ?: "Unbekannter Codec"
                val detailAudio = getDetailedAudioDescription(audioTrack.channelCount, codec)
                val audiodescription = "${audioTrack.sampleMimeType?.removePrefix("audio/")} | $detailAudio"
                binding.tvDialogSeriesAudiodetailformat.text = "Format: $audiodescription"
                binding.tvDialogSeriesAudiodetailslang.text = "Language: $audiolanguage"
                binding.tvDialogSeriesAudiodetailscodec.text = "Codec: $codec"
            } else {
                Toast.makeText(this@PlaySeriesFragment.requireActivity(), "KEIN AUDIOTRACK", Toast.LENGTH_SHORT).show()
            }
        }
            val fpsFullscreen = binding.fps.text.toString()
            binding.tvDialogSeriesVideodetailsfps.text = if (fpsFullscreen.isNotEmpty()) {
                "FPS: $fpsFullscreen"
            } else {
                "n/a"
            }
            val resolutionFullscreen = binding.resolution.text.toString()
            binding.tvDialogSeriesVideodetailsresol.text = if (resolutionFullscreen.isNotEmpty()) {
                "Resolution: $resolutionFullscreen"
            } else {
                "n/a"
            }

            binding.tvDialogSeriesDescription.text = if (episodedescription.isNullOrEmpty()) {
                seriesdescription
            } else {
                episodedescription
            }
            binding.tvDialogSeriesSeasonepisode.text = "Season ${helpViewModel.currentFocusedSeason?.seasonNumber} Episode ${helpViewModel.currentFocusedEpisode?.episodeNumber}"
            binding.ivSeriesdetailPoster.load(image)
            binding.tvDialogSeriesTitle.text = title
            binding.tvDialogSeriesTitle.requestFocus()
        }
    }

    fun getLanguageName(languageCode: String?): String? {
        return languageCode?.let { Locale(it).displayLanguage }
    }


    fun closeSeriesDetailDialog() {
        binding.seriesDetailInfo.visibility = View.GONE
        binding.relLayoutInfodetail.requestFocus()
    }

    fun getAudioFormatDescription(channels: Int?): String {
        return when {
            channels == 1 -> "Mono"
            channels == 2 -> "Stereo"
            channels == 6 -> "5.1"
            channels == 8 -> "7.1"
            else -> "N/A"
        }
    }

    fun getDetailedAudioDescription(channelCount: Int, codec: String?): String {
        val channelDescription = when (channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            4 -> "Quadrophonie"
            5 -> "5.0 Surround"
            6 -> "5.1 Surround"
            7 -> "6.1 Surround"
            8 -> "7.1 Surround"
            else -> "${channelCount} Channels"
        }

        val audioCodecDescription = when {
            codec == null -> "Unbekannter Codec"
            codec.contains("mp4a", ignoreCase = true) -> {
                when {
                    codec.contains("40.2") -> "AAC-LC"
                    codec.contains("40.5") -> "HE-AAC"
                    codec.contains("40.29") -> "HE-AAC v2"
                    else -> "MPEG-4 Audio"
                }
            }
            codec.contains("ac3", ignoreCase = true) -> "Dolby Digital (AC-3)"
            codec.contains("eac3", ignoreCase = true) -> "Dolby Digital Plus (E-AC-3)"
            codec.contains("opus", ignoreCase = true) -> "Opus"
            codec.contains("flac", ignoreCase = true) -> "FLAC (Free Lossless Audio Codec)"
            codec.contains("alac", ignoreCase = true) -> "Apple Lossless Audio Codec (ALAC)"
            codec.contains("dts", ignoreCase = true) -> "DTS (Digital Theater Systems)"
            codec.contains("dtshd", ignoreCase = true) -> "DTS-HD Master Audio"
            codec.contains("truehd", ignoreCase = true) -> "Dolby TrueHD"
            codec.contains("mp3", ignoreCase = true) -> "MP3 (MPEG Audio Layer III)"
            codec.contains("pcm", ignoreCase = true) -> "Unkomprimiertes PCM Audio"
            codec.contains("wav", ignoreCase = true) -> "WAV (Waveform Audio Format)"
            codec.contains("aiff", ignoreCase = true) -> "AIFF (Audio Interchange File Format)"
            codec.contains("vorbis", ignoreCase = true) -> "Vorbis (OGG)"
            codec.contains("atmos", ignoreCase = true) -> "Dolby Atmos"
            else -> codec.uppercase()
        }
        return "$channelDescription | $audioCodecDescription"
    }


    private fun changeVisibilityOff() {
        binding.hudContainer.alpha = 0.4F
    }

    private fun changeVisibilityOn() {
        binding.hudContainer.alpha = 1F
    }

    fun calculateSeriesPercentagePlayed(): Double {
        val episodes = helpViewModel.focusedEpisodes
        if (episodes.isNullOrEmpty()) return 0.0

        val totalPercentage = episodes.sumOf { it.episodePercentagePlayed }
        val totalEpisodes = episodes.size.toDouble()

        return (totalPercentage / totalEpisodes)  // In Prozent umwandeln
    }

    fun calculateSeasonPercentagePlayed(): Double {
        val episodes = helpViewModel.focusedEpisodes?.filter { it.seasonNumber == helpViewModel.currentFocusedSeason?.seasonNumber }
        if (episodes.isNullOrEmpty()) return 0.0

        val totalPercentage = episodes.sumOf { it.episodePercentagePlayed }
        val totalEpisodes = episodes.size.toDouble() // Anzahl der Episoden als Double für die Division

        return totalPercentage / totalEpisodes
    }


    private fun updateSerieSeasonEpisode(episode: EpisodesOB?, season: SeasonsOB?, serie: SeriesOB?) {
        if (episode == null) return
        if (season == null) return
        if (serie == null) return
        val percentage = episode.episodePercentagePlayed

        if (percentage in 0.05..0.94) {
            markEpisodePartlyWatched(serie, season, episode)
        } else if (percentage >= 0.95) {
            markEpisodeFullyWatched(serie, season, episode)
        } else {
            episode.currentPosition = 0
            episode.episodePercentagePlayed = 0.0
        }

        updateCache(serie, helpViewModel.focusedSeasons, helpViewModel.focusedEpisodes)

        updateSeriesInRV()
    }


    private fun markEpisodePartlyWatched(serie: SeriesOB, season: SeasonsOB, episode: EpisodesOB) {
        if (!serie.isPartlyWatched) serie.isPartlyWatched = true
        if (!season.isSeasonPartlyWatched) season.isSeasonPartlyWatched = true

        serie.seriesPercentagePlayed = calculateSeriesPercentagePlayed()
        season.seasonPercentagePlayed = calculateSeasonPercentagePlayed()

        serie.lastWatchedSeason = season.seasonNumber.toIntOrNull() ?: 1
        serie.lastWatchedEpisode = episode.episodeNumber

        episode.isEpisodePartlyWatched = true
        episode.isEpisodeFullyWatched = false

        persistAll(serie, season, episode)
    }

    private fun markEpisodeFullyWatched(serie: SeriesOB, season: SeasonsOB, episode: EpisodesOB) {
        episode.isEpisodePartlyWatched = false
        episode.isEpisodeFullyWatched = true
        episode.episodePercentagePlayed = 1.0
        episode.currentPosition = 0L
        val episodesOfSeason = helpViewModel.focusedEpisodes?.filter { it.seasonNumber == season.seasonNumber }
        if (episodesOfSeason?.all { it.isEpisodeFullyWatched} == true) {
            season.isSeasonFullyWatched = true
            season.isSeasonPartlyWatched = false
            season.seasonPercentagePlayed = 1.0
            if (helpViewModel.focusedSeasons?.all { it.isSeasonFullyWatched } == true) {
                serie.seriesPercentagePlayed = 1.0
                serie.isCompletelyWatched = true
                serie.isPartlyWatched = false
                serie.currentPosition = 0L
                helpViewModel.currentFocusedSeason = helpViewModel.focusedSeasons?.firstOrNull()
                helpViewModel.currentFocusedEpisode = helpViewModel.focusedEpisodes?.firstOrNull()
                serie.lastWatchedEpisode = helpViewModel.focusedEpisodes?.first()?.episodeNumber ?: 0
                serie.lastWatchedSeason = helpViewModel.focusedSeasons?.first()?.seasonNumber?.toIntOrNull() ?: 0
            } else {
                val nextSeason = helpViewModel.focusedSeasons?.firstOrNull { it.seasonNumber > season.seasonNumber }
                if (nextSeason != null) {
                    helpViewModel.currentFocusedSeason = nextSeason
                    serie.lastWatchedSeason = nextSeason.seasonNumber.toIntOrNull() ?: 0
                    val firstEpisodeToWatch =
                        helpViewModel.focusedEpisodes?.firstOrNull { it.seasonNumber == nextSeason.seasonNumber }
                    helpViewModel.currentFocusedEpisode = firstEpisodeToWatch
                    serie.lastWatchedEpisode = firstEpisodeToWatch?.episodeNumber ?: 0
                } else {
                    val firstUnseenEpisode = helpViewModel.focusedEpisodes?.firstOrNull { !it.isEpisodeFullyWatched || it.isEpisodePartlyWatched }
                    serie.lastWatchedSeason = firstUnseenEpisode?.seasonNumber?.toIntOrNull() ?: 0
                    serie.lastWatchedEpisode = firstUnseenEpisode?.episodeNumber ?: 0
                    helpViewModel.currentFocusedEpisode = firstUnseenEpisode
                    helpViewModel.currentFocusedSeason = helpViewModel.focusedSeasons?.firstOrNull { it.seasonNumber == firstUnseenEpisode?.seasonNumber }
                }
            }
        } else {
            season.isSeasonPartlyWatched = true
            season.isSeasonFullyWatched = false
            season.seasonPercentagePlayed = calculateSeasonPercentagePlayed()
            serie.seriesPercentagePlayed = calculateSeriesPercentagePlayed()
            val lastEpisodeOfSeason = episodesOfSeason?.none { it.episodeNumber > episode.episodeNumber }
            if (lastEpisodeOfSeason != null && !lastEpisodeOfSeason) {
                val nextEpisode = episodesOfSeason.firstOrNull { it.episodeNumber > episode.episodeNumber }
                if (nextEpisode != null) {
                    serie.lastWatchedEpisode = nextEpisode.episodeNumber
                    serie.lastWatchedSeason = nextEpisode.seasonNumber?.toIntOrNull() ?: 0
                    helpViewModel.currentFocusedEpisode = nextEpisode
                }
            } else {
                val nextSeason = helpViewModel.focusedSeasons?.firstOrNull { it.seasonNumber > season.seasonNumber }
                if (nextSeason != null) {
                    helpViewModel.currentFocusedSeason = nextSeason
                    serie.lastWatchedSeason = nextSeason.seasonNumber.toIntOrNull() ?: 0
                    val firstEpisodeToWatch = helpViewModel.focusedEpisodes?.firstOrNull { it.seasonNumber == nextSeason.seasonNumber }
                    serie.lastWatchedEpisode = firstEpisodeToWatch?.episodeNumber ?: 0
                    helpViewModel.currentFocusedEpisode = firstEpisodeToWatch
                } else {
                    val firstUnseenEpisode = helpViewModel.focusedEpisodes?.firstOrNull { !it.isEpisodeFullyWatched || it.isEpisodePartlyWatched }
                    serie.lastWatchedSeason = firstUnseenEpisode?.seasonNumber?.toIntOrNull() ?: 0
                    serie.lastWatchedEpisode = firstUnseenEpisode?.episodeNumber ?: 0
                    helpViewModel.currentFocusedSeason = helpViewModel.focusedSeasons?.firstOrNull { it.seasonNumber == firstUnseenEpisode?.seasonNumber }
                    helpViewModel.currentFocusedEpisode = firstUnseenEpisode
                }
            }
        }
        persistAll(serie, season, episode)
    }

    private fun updateCache(serie: SeriesOB?, seasons: MutableList<SeasonsOB>?, episodes: MutableList<EpisodesOB>?) {
        if (serie == null) return
        if (seasons == null) return
        if (episodes == null) return

        if (helpViewModel.currentSeriesAccount?.isStalker == true) {
            val currentCache = stalkerViewModel.seriesCacheLive.value ?: mutableMapOf()
            currentCache[serie.idByAccountData] = seasons to episodes
            stalkerViewModel.seriesCacheLive.postValue(currentCache)
        } else {
            xtreamViewModel.seriesCache[serie.idByAccountData] = seasons to episodes
        }
    }

    private fun persistAll(serie: SeriesOB, season: SeasonsOB?, episode: EpisodesOB?) {
        seriesViewModel.updateSeriesDetail()
        serie.seriesAccount.target = helpViewModel.currentSeriesAccount
        serie.seriescat.target = helpViewModel.currentSeriesCategoryOB

        seriesBox.put(serie)
        season?.let { seasonBox.put(it) }
        episode?.let { episodeBox.put(it) }
    }

    fun setFocusToResetAspectRatio() {
        if (binding.tvAspectratioDefault.isVisible) {
            binding.tvAspectratioDefault.requestFocus()
        } else {
            return
        }
    }

    private fun closeFragment() {
        stopRunnable = true
        player?.release()
        mediaPlayer?.release()
        isVideoPlaying = false
        currentSeriesUrl = ""
        updateSerieSeasonEpisode(helpViewModel.currentFocusedEpisode, helpViewModel.currentFocusedSeason, helpViewModel.currentFocusedSerie)
        helpViewModel.playMovieSelectionModified = null
        helpViewModel.serieFullScreenOpened = false
        seriesViewModel.requestFocusOnNextEpisode()
        parentFragmentManager.popBackStack()
    }

    override fun onResume() {
        super.onResume()
        if (currentSeriesUrl.isNotEmpty()) {
            if (playWithVlc) {
                startDatabaseRunnable()
                initializePlayer(currentSeriesUrl)
            } else {
                player?.addListener(playerErrorListener)
                player?.addListener(handlePlaybackStateListener)
                player?.addAnalyticsListener(analyticsListener)
                player?.setVideoFrameMetadataListener(videoFrameMetadataListener)
                startPeriodicExoPlayerUpdate()
                startDatabaseRunnable()
                initializeExoPlayer(currentSeriesUrl)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (playWithVlc) {
            stopDatabaseRunnable()
            mediaPlayer?.release()
            mediaPlayer = null
            binding.ivPauseVideo.visibility = View.GONE
            binding.ivPlayVideo.visibility = View.VISIBLE
        } else {
            player?.removeListener(playerErrorListener)
            player?.removeListener(handlePlaybackStateListener)
            player?.removeAnalyticsListener(analyticsListener)
            player?.clearVideoFrameMetadataListener(videoFrameMetadataListener)
            stopPeriodicExoPlayerUpdate()
            stopDatabaseRunnable()
            player?.release()
            player = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDatabaseRunnable()
        stopPeriodicExoPlayerUpdate()
        if (playWithVlc) {
            org.videolan.libvlc.Dialog.setCallbacks(libVLC, null)
            vlcDialogCallbacks = null
            playWithVlc = false
            mediaPlayer?.media?.release()
            mediaPlayer?.release()
            libVLC?.release()
            mediaPlayer = null
            libVLC = null
        } else {
            player?.removeListener(playerErrorListener)
            player?.removeListener(handlePlaybackStateListener)
            player?.removeAnalyticsListener(analyticsListener)
            player?.clearVideoFrameMetadataListener(videoFrameMetadataListener)
            stopPeriodicExoPlayerUpdate()
            player?.release()
            player = null
        }
        handler.removeCallbacks(hideHudRunnable)
        _binding = null
    }
}