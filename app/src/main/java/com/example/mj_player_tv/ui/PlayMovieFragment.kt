package com.example.mj_player_tv.ui

import android.app.Dialog
import android.content.Context
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
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
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultDataSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.TransferListener
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DrmSession.DrmSessionException
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView.ProgressUpdateListener
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.MovieOB_
import com.example.mj_player_tv.database.help.Movie
import com.example.mj_player_tv.database.help.TrackInfo
import com.example.mj_player_tv.databinding.FragmentPlayMovieBinding
import com.example.mj_player_tv.ui.adapter.VodSelectionAdapter
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.PlexViewModel
import com.example.mj_player_tv.viewmodel.PlexViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.LibVLC.Event
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import javax.net.ssl.SSLHandshakeException
import kotlin.math.abs
import kotlin.math.round
import androidx.core.view.isVisible
import com.example.mj_player_tv.viewmodel.MoviesViewModel
import com.example.mj_player_tv.viewmodel.MoviesViewModelFactory


@UnstableApi
class PlayMovieFragment : Fragment(R.layout.fragment_play_movie) {

    private lateinit var movieSelectionAdapter: VodSelectionAdapter

    private var _binding: FragmentPlayMovieBinding? = null

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
    val maxIncrement = 50000L
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

    private var currentMovieUrl: String = ""

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)
    private val movieBox: Box<MovieOB> = ObjectBox.store.boxFor(MovieOB::class.java)

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
        _binding = FragmentPlayMovieBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showProgressBar()
        prepareRecyclerview()

        playWithVlc = if (helpViewModel.playMovieSelectionModified != null) {
            if (helpViewModel.playMovieSelectionModified == 0) {
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
                if (helpViewModel.currentMovieAccount?.isPlex == true) {
                    options.add("--http-extra-headers=X-Plex-Client-Identifier:LMJ Player")
                }
                libVLC = LibVLC(this@PlayMovieFragment.requireContext(), options)
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
                    focusForwardBtn()
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
                    focusReplayBtn()
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
                    if (helpViewModel.currentFocusedMovie != null) {
                        helpViewModel.currentFocusedMovie?.let {
                            showDescriptionDialog(it)
                        }
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
                return@setOnKeyListener true
            }

            binding.tvDialogMovieTitle.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    closeMovieDetailDialog()
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
                    var increment = 10000L // 60 Sekunden Schritte
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
    }


    private fun prepareRecyclerview() {
        movieSelectionAdapter = VodSelectionAdapter(onClickListener, this, helpViewModel)
        binding.rvMovieSelection.apply {
            adapter = movieSelectionAdapter
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
        movieSelectionAdapter.submitList(null)
        changeVisibilityOn()
        binding.relLayoutMovieSelection.visibility = View.INVISIBLE
        binding.relLayoutAudioTrack.requestFocus()
    }

    fun focusToAudioDelayFromSelection() {
        changeVisibilityOn()
        binding.relLayoutMovieSelection.visibility = View.INVISIBLE
        binding.relLayoutAudioDelay.requestFocus()
    }

    fun focusToVideoFromSelection() {
        movieSelectionAdapter.submitList(null)
        changeVisibilityOn()
        binding.relLayoutMovieSelection.visibility = View.INVISIBLE
        binding.relLayoutVideoTrack.requestFocus()
    }

    fun focusToSubTitleFromSelection() {
        movieSelectionAdapter.submitList(null)
        changeVisibilityOn()
        binding.relLayoutMovieSelection.visibility = View.INVISIBLE
        binding.relLayoutSubtitleTrack.requestFocus()
    }

    fun focusToAspectRatioFromSelection() {
        movieSelectionAdapter.submitList(null)
        changeVisibilityOn()
        binding.relLayoutMovieSelection.visibility = View.INVISIBLE
        binding.relLayoutAspectratiodetail.requestFocus()
    }

    fun focusToInfoFromSelection() {
        binding.relLayoutMovieSelection.visibility = View.INVISIBLE
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
        binding.playMoviename.text = helpViewModel.currentFocusedMovie!!.movieName

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
            if (mediaPlayer == null && player == null) {
                return
            }
            currentMediaItemDuration = mediaPlayer?.media?.duration ?: player?.duration ?: 0L
            val currentPosition = mediaPlayer?.time ?: player?.currentPosition ?: 0L
            val percentagePlayed =
                if (currentMediaItemDuration > 0) {
                    currentPosition.toDouble() / currentMediaItemDuration
                } else {
                    0.0
                }
            helpViewModel.currentFocusedMovie?.currentPosition = currentPosition
            helpViewModel.currentFocusedMovie?.percentagePlayed = percentagePlayed
            lastUpdateTime = currentPosition
            databaseHandler.postDelayed(this, 10000)
        }
    }

    private fun updateMovieInRV(movie: MovieOB) {
        moviesViewModel.requestUpdateMovieInRV()
    }

    private fun initializeExoPlayer(url: String) {
        binding.moviePlayingError.visibility = View.INVISIBLE

        player = ExoPlayer.Builder(requireContext())
            .setRenderersFactory(
                DefaultRenderersFactory(requireContext())
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                    .setEnableDecoderFallback(true)
            )
            .build()
        currentMovieUrl = url
// Listener NACH dem Build setzen
        player?.addAnalyticsListener(EventLogger())
        player?.addListener(handlePlaybackStateListener)
        player?.addListener(playerErrorListener)
        player?.addAnalyticsListener(analyticsListener)
        player?.setVideoFrameMetadataListener(videoFrameMetadataListener)

        // Player mit der View verbinden
        binding.exoplayerVideoview.player = player
        binding.exoplayerVideoview.useController = false
        stopRunnable = false

        // Erstelle das Media-Objekt
        val mediaSource = createMediaSource(url)

        player?.setMediaSource(mediaSource)
        player?.prepare()
        player?.seekTo(helpViewModel.currentFocusedMovie?.currentPosition ?: 0)
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
                Toast.makeText(this@PlayMovieFragment.requireActivity(), "$e", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createMediaSource(url: String): MediaSource {
        val mediaItem = MediaItem.fromUri(url)

        val dataSourceFactory = if (helpViewModel.currentMovieAccount?.isPlex == true) {
            val factory = DefaultHttpDataSource.Factory()
            factory.setDefaultRequestProperties(
                mapOf(
                    "X-Plex-Client-Identifier" to "LMJ Player"
                )
            )
            factory
        } else {
            DefaultHttpDataSource.Factory()
        }

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
                if (helpViewModel.currentMovieAccount!!.isXtream) {
                    helpViewModel.currentFocusedMovie?.movieTime = (player?.duration?.div((1000)))?.toInt()
                } else {
                    helpViewModel.currentFocusedMovie?.movieTime = (player?.duration?.div((1000 * 60)))?.toInt()
                }
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

                            Log.d("AUDIOTRACKS FOR ${helpViewModel.currentFocusedMovie?.movieName}", "$i = $trackFormat |||||| LABEL: ${trackFormat.label} LANG: ${trackFormat.language}")

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
                            val descr = "${trackFormat.width}x${trackFormat.height} -[${trackFormat.codecs}]"
                            val isSelected = group.isTrackSelected(i)
                            val isSupported = group.isTrackSupported(i)
                            videoTracks.add(TrackInfo(descr, i, isSelected, isSupported, group))
                        }
                    }

                    C.TRACK_TYPE_TEXT -> { // Subtitle Tracks (Text)
                        // "Kein Untertitel"-Option hinzufügen
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
            subTitleTacks.add(TrackInfo("Kein Untertitel", -1, false, true, null))
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
                                binding.tvDialogMovieVideodetailsfps.text = "FPS: $fpsToShow"
                                binding.tvDialogMovieVideodetailsfps.visibility = View.VISIBLE
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
                    binding.moviePlayingError.visibility = View.GONE
                    hideProgressBar()
                    isVideoPlaying = true
                    startDatabaseRunnable()
                    startPeriodicExoPlayerUpdate()
                }
                Player.STATE_BUFFERING -> {
                    showProgressBar()
                }
                Player.STATE_IDLE -> {
                }
                Player.STATE_ENDED -> {
                    stopDatabaseRunnable()
                    stopPeriodicExoPlayerUpdate()
                }
            }
        }

        override fun onRenderedFirstFrame() {
            super.onRenderedFirstFrame()
            if(!ishudContainerVisbile) {
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
            binding.moviePlayingError.text = errorMessage
            binding.moviePlayingError.visibility = View.VISIBLE
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
        binding.moviePlayingError.visibility = View.INVISIBLE
        if (helpViewModel.currentFocusedMovie != null) {
            currentMovieUrl = url
            stopRunnable = false
            // Erstelle das Media-Objekt
            val media = Media(libVLC, Uri.parse(url))
            // Setting up video output
            mediaPlayer?.attachViews(binding.videoView, null, false, false)

            // Setze die Media auf den MediaPlayer
            mediaPlayer?.media = media

            mediaPlayer?.time = helpViewModel.currentFocusedMovie?.currentPosition ?: 0
            // Starte die Wiedergabe
            mediaPlayer?.play()

            vlcDialogCallbacks = object : org.videolan.libvlc.Dialog.Callbacks {
                    override fun onDisplay(dialog: org.videolan.libvlc.Dialog.ErrorMessage) {
                        // Fehlerdialog anzeigen
                        binding.playerProgressBar.visibility = View.GONE
                        stopDatabaseRunnable()
                        binding.moviePlayingError.visibility = View.VISIBLE
                        binding.moviePlayingError.text = dialog.title
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                            delay(4000)
                            parentFragmentManager.popBackStack()
                            val mainFragment =
                                parentFragmentManager.findFragmentById(R.id.container_movie_info)
                            if (mainFragment is MovieDetailFragment) {
                                mainFragment.closeFullScreenMovie()
                            } else {
                                val newmainFragment = parentFragmentManager.findFragmentById(R.id.container_globalsearch_vod_info)
                                if (newmainFragment is MovieDetailFragment) {
                                    newmainFragment.closeFullScreenMovie()
                                }
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
                                if (helpViewModel.currentMovieAccount!!.isXtream) {
                                    helpViewModel.currentFocusedMovie?.movieTime = (totalDuration / (1000)).toInt()
                                } else {
                                    helpViewModel.currentFocusedMovie?.movieTime = (totalDuration / (1000 * 60)).toInt()
                                }
                                helpViewModel.currentFocusedMovie?.let { updateMovieInRV(it) }
                                val formattedMaxDuration = formatTime(totalDuration)
                                binding.tvTotalTime.text = formattedMaxDuration
                                binding.seekBar.setDuration(totalDuration)
                                hideProgressBar()
                                binding.playMoviename.text = helpViewModel.currentFocusedMovie!!.movieName
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
                            val mainFragment = parentFragmentManager.findFragmentById(R.id.container_movie_info)
                            if (mainFragment is MovieDetailFragment) {
                                mainFragment.closeFullScreenMovie()
                            } else {
                                val newmainFragment = parentFragmentManager.findFragmentById(R.id.container_globalsearch_vod_info)
                                if (newmainFragment is MovieDetailFragment) {
                                    newmainFragment.closeFullScreenMovie()
                                }
                            }
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
        binding.moviePlayingError.visibility = View.INVISIBLE
        binding.hudContainer.visibility = View.INVISIBLE
        if (helpViewModel.currentFocusedMovie != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val accountData = withContext(Dispatchers.IO) {
                    accountBox.get(helpViewModel.currentFocusedMovie!!.accountId!!)
                }
                if (accountData.isStalker) {
                    val stalkerUrl = accountData.stalkerUrl
                    val macAddress = accountData.macAddress
                    val userAgent = accountData.userAgent
                    val token = accountData.token
                    val timeZone = accountData.timezone
                    if (!helpViewModel.currentFocusedMovie!!.movieCmd.isNullOrEmpty()) {
                        val movieCmd = if (helpViewModel.currentFocusedMovie!!.movieCmd!!.startsWith("ffmpeg")) {
                            helpViewModel.currentFocusedMovie!!.movieCmd?.removePrefix("ffmpeg ")
                                ?.trim()
                        } else {
                            helpViewModel.currentFocusedMovie!!.movieCmd?.replace(Regex("(?i)^['\"]?ffmpeg['\"]?\\s*"), "")
                                ?.trim()
                        }
                        val response = stalkerViewModel.getMovieLink(
                            stalkerUrl,
                            movieCmd!!,
                            cookie = "mac=${macAddress}; stb_lang=en; timezone=$timeZone;",
                            token = "Bearer $token",
                            userAgent
                        ).await()
                        when (response) {
                            is Resource.Error -> {
                                binding.moviePlayingError.text = response.message
                                binding.moviePlayingError.visibility = View.VISIBLE
                            }

                            is Resource.Success -> {
                                val playToken = response.data.toString()
                                    .replace(Regex("(?i)^['\"]?ffmpeg['\"]?\\s*"), "")
                                    .trim()
                                try {
                                    if (playWithVlc) {
                                        initializePlayer(playToken)
                                    } else {
                                        initializeExoPlayer(playToken)
                                    }
                                } catch (e: IllegalArgumentException) {
                                    // handle the exception, for example:
                                    Toast.makeText(
                                        requireContext(),
                                        "Unable to play the movie: ${
                                            (e.cause?.cause?.cause?.message?.removePrefix("Received "))
                                        }",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }
                            }
                        }
                    }
                } else if (accountData.isXtream) {
                    val url = "${accountData.stalkerUrl}/movie/${accountData.username}/${accountData.macAddress}/${helpViewModel.currentFocusedMovie!!.movieId}.${helpViewModel.currentFocusedMovie!!.xtreamExtension}"
                    if (playWithVlc) {
                        initializePlayer(url)
                    } else {
                        initializeExoPlayer(url)
                    }
                } else {
                    if (playWithVlc) {
                        helpViewModel.currentFocusedMovie?.movieCmd?.let {
                            initializePlayer(it)
                        }
                    } else {
                        helpViewModel.currentFocusedMovie?.movieCmd?.let {
                            initializeExoPlayer(it)
                        }
                    }
                }
            }
        }
    }

    private val onClickListener = VodSelectionAdapter.OnClickListener { selection ->
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
                binding.rvMovieSelection.nextFocusUpId = R.id.tv_aspectratio_default
                binding.tvAspectratioDefault.text = "Reset to default = $defaultAspectRatio"
                binding.tvAspectratioDefault.visibility = View.VISIBLE
            } else {
                binding.rvMovieSelection.nextFocusUpId = R.id.rv_movie_selection
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
            val track = movieSelectionAdapter.currentList.firstOrNull { it.trackName == aspectRatio }
            if (track != null) {
                val pos = movieSelectionAdapter.currentList.indexOf(track)
                movieSelectionAdapter.setSelectedTrackInfo(track, pos)
                binding.rvMovieSelection.setSelectedPosition(pos)
                binding.rvMovieSelection.requestFocus()
            } else {
                binding.rvMovieSelection.requestFocus()
            }
        } else {
            Toast.makeText(this@PlayMovieFragment.requireActivity(), "Can't change aspect ratio!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAudioTrackDialog() {
        binding.tvMovieSelectionSize.text = ""
        binding.tvMovieSelection.text = "Choose audio track:"

        if (audioTracks.isEmpty()) {
            Toast.makeText(this@PlayMovieFragment.requireActivity(), "No audio tracks available", Toast.LENGTH_SHORT).show()
            return // Kein AudioTrack vorhanden, Dialog nicht anzeigen
        } else {
            changeVisibilityOff()
            binding.tvMovieSelectionSize.text = "Total: ${audioTracks.size}"
            val audioList = audioTracks.toList()
            val currentAudioTrack = audioList.firstOrNull { it.isSelected }
            movieSelectionAdapter.submitList(audioList)
            movieSelectionAdapter.currentSelected = currentAudioTrack?.trackName ?: ""
            if (currentAudioTrack != null) {
                val position = movieSelectionAdapter.currentList.indexOf(currentAudioTrack)
                binding.rvMovieSelection.post {
                    binding.rvMovieSelection.setSelectedPosition(position)
                }
            }
            binding.relLayoutMovieSelection.visibility = View.VISIBLE
            binding.rvMovieSelection.requestFocus()
        }
    }

    private fun showSubtitleDialog() {
        binding.tvMovieSelectionSize.text = ""
        binding.tvMovieSelection.text = "Choose subtitle track:"

        if (subTitleTacks.isNotEmpty()) {
            val subtitleList = subTitleTacks.toList()
            changeVisibilityOff()
            binding.tvMovieSelectionSize.text = "Total: ${subtitleList.size}"
            val currentSubtitleTrack = subtitleList.firstOrNull { it.isSelected == true }
            movieSelectionAdapter.submitList(subtitleList)
            movieSelectionAdapter.currentSelected = currentSubtitleTrack?.trackName ?: ""

            if (currentSubtitleTrack != null) {
                val position = movieSelectionAdapter.currentList.indexOf(currentSubtitleTrack)
                binding.rvMovieSelection.post {
                    binding.rvMovieSelection.setSelectedPosition(position)
                }
            }
            binding.relLayoutMovieSelection.visibility = View.VISIBLE
            binding.rvMovieSelection.requestFocus()
        } else {
            Toast.makeText(this@PlayMovieFragment.requireActivity(), "No subtitle tracks available", Toast.LENGTH_SHORT).show()
            return // Kein AudioTrack vorhanden, Dialog nicht anzeigen
        }
    }

    private fun showVideoTrackDialog() {
        binding.tvMovieSelectionSize.text = ""
        binding.tvMovieSelection.text = "Choose video track:"

        if (videoTracks.isEmpty()) {
            Toast.makeText(this@PlayMovieFragment.requireActivity(), "No video tracks available", Toast.LENGTH_SHORT).show()
            return // Kein VideoTrack vorhanden, Dialog nicht anzeigen
        } else {
            val videoTrackList = videoTracks.toList()
            changeVisibilityOff()
            binding.tvMovieSelectionSize.text = "Total: ${videoTrackList.size}"
            val currentVideoTrack = movieSelectionAdapter.currentList.firstOrNull { it.isSelected == true }
            movieSelectionAdapter.submitList(videoTrackList)
            movieSelectionAdapter.currentSelected = currentVideoTrack?.trackName ?: ""

            if (currentVideoTrack != null) {
                val position = movieSelectionAdapter.currentList.indexOf(currentVideoTrack)
                binding.rvMovieSelection.post {
                    binding.rvMovieSelection.setSelectedPosition(position)
                }
            }
            binding.relLayoutMovieSelection.visibility = View.VISIBLE
            binding.rvMovieSelection.requestFocus()
        }
    }


    private fun showAspectRatioDialog() {
        binding.tvMovieSelectionSize.text = ""
        binding.tvMovieSelection.text = "Choose aspect ratio:"
        var currentId = 0

        changeVisibilityOff()
        binding.tvMovieSelectionSize.text = "Total: ${aspectRatioList.size}"
        if (currentAspectRatio != defaultAspectRatio) {
            binding.rvMovieSelection.nextFocusUpId = R.id.tv_aspectratio_default
            binding.tvAspectratioDefault.text = "Reset to default = $defaultAspectRatio"
            binding.tvAspectratioDefault.visibility = View.VISIBLE
        } else {
            binding.rvMovieSelection.nextFocusUpId = R.id.rv_movie_selection
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
        movieSelectionAdapter.submitList(ratioTracks)
        movieSelectionAdapter.currentSelected = currentARinList?.trackName ?: ""
        if (currentARinList != null) {
            val position = movieSelectionAdapter.currentList.indexOf(currentARinList)
            binding.rvMovieSelection.post {
                binding.rvMovieSelection.setSelectedPosition(position)
            }
        }
        binding.relLayoutMovieSelection.visibility = View.VISIBLE
        binding.rvMovieSelection.requestFocus()
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


    private fun showDescriptionDialog(movie: MovieOB) {
        binding.movieDetailInfo.visibility = View.VISIBLE
        binding.linLayoutSeriesInfos.visibility = View.GONE
        val description = movie.description
        val title = movie.movieName
        val image = movie.screenshot_uri

        if (playWithVlc) {
            val videoTrack = mediaPlayer?.currentVideoTrack
            if (videoTrack != null) {
                binding.tvDialogMovieVideodetailscodec.text = "Codec: ${videoTrack.codec}"
            } else {
                binding.tvDialogMovieVideodetailscodec.visibility = View.INVISIBLE
            }
            val audioTrackId = mediaPlayer?.audioTrack
            val audioTrack = audioTrackId?.let { mediaPlayer?.media?.getTrack(it) }
            if (audioTrack != null && audioTrack.type == IMedia.Track.Type.Audio) {
                val audiolanguage = audioTrack.language ?: "Keine Sprache angegeben"
                val codec = audioTrack.codec ?: "Unbekannter Codec"
                val audiodescription = audioTrack.description ?: "Keine Beschreibung"
                binding.tvDialogMovieAudiodetailformat.text = "Format: $audiodescription"
                binding.tvDialogMovieAudiodetailslang.text = "Language: $audiolanguage"
                binding.tvDialogMovieAudiodetailscodec.text = "Codec: $codec"
            } else {
                Toast.makeText(this@PlayMovieFragment.requireActivity(), "KEIN AUDIOTRACK", Toast.LENGTH_SHORT).show()
            }

        } else {
            val videoTrack = player?.videoFormat
            if (videoTrack != null) {
                Log.d("INFODETAIL", "VIDEO: $videoTrack")
                binding.tvDialogMovieVideodetailscodec.text = "Codec: ${videoTrack.codecs}"
            } else {

                binding.tvDialogMovieVideodetailscodec.visibility = View.INVISIBLE
            }
            val audioTrack = player?.audioFormat
            if (audioTrack != null) {
                val audiolanguage = getLanguageName(audioTrack.language) ?: audioTrack.label ?: "Undefined"
                val codec = audioTrack.codecs ?: "Unbekannter Codec"
                val detailAudio = getDetailedAudioDescription(audioTrack.channelCount, codec)
                val audiodescription = "${audioTrack.sampleMimeType?.removePrefix("audio/")} | $detailAudio"
                binding.tvDialogMovieAudiodetailformat.text = "Format: $audiodescription"
                binding.tvDialogMovieAudiodetailslang.text = "Language: $audiolanguage"
                binding.tvDialogMovieAudiodetailscodec.text = "Codec: $codec"
            } else {
                Toast.makeText(this@PlayMovieFragment.requireActivity(), "KEIN AUDIOTRACK", Toast.LENGTH_SHORT).show()
            }
        }
        val fpsFullscreen = binding.fps.text.toString()
        binding.tvDialogMovieVideodetailsfps.text = if (fpsFullscreen.isNotEmpty()) {
            fpsFullscreen
        } else {
            "n/a"
        }
        val resolutionFullscreen = binding.resolution.text.toString()
        binding.tvDialogMovieVideodetailsresol.text = if (resolutionFullscreen.isNotEmpty()) {
            "Resolution: $resolutionFullscreen"
        } else {
            "n/a"
        }

        binding.tvDialogMovieDescription.text = description
        binding.ivMoviedetailPoster.load(image)
        binding.tvDialogMovieTitle.text = title
        binding.tvDialogMovieTitle.requestFocus()
    }

    fun getLanguageName(languageCode: String?): String? {
        return languageCode?.let { Locale(it).displayLanguage }
    }


    fun closeMovieDetailDialog() {
        binding.movieDetailInfo.visibility = View.GONE
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

    private fun updateMovie() {
        val account = helpViewModel.currentMovieAccount
        val percentagePlayed = helpViewModel.currentFocusedMovie?.percentagePlayed ?: 0.0
        val currentPosition = helpViewModel.currentFocusedMovie?.currentPosition ?: 0
        if (percentagePlayed >= 0.05 && percentagePlayed < 0.9) {
                helpViewModel.currentFocusedMovie?.currentPosition = currentPosition
                helpViewModel.currentFocusedMovie?.isPartlyWatched = true
                helpViewModel.currentFocusedMovie?.isCompletelyWatched = false
                helpViewModel.currentFocusedMovie?.percentagePlayed = percentagePlayed
                helpViewModel.currentFocusedMovie?.let {
                    it.movieAccount.target = helpViewModel.currentMovieAccount
                    it.moviecat.target = helpViewModel.currentMovieCategoryOB
                    movieBox.put(it)
                    updateMovieInRV(it)
                    updateMovieDetails()
                    if (account?.isPlex == true) {
                        account.let {
                            helpViewModel.currentFocusedMovie?.plexRatingKey?.let { id ->
                                plexViewModel.updateItemProgress(account, id, currentPosition, "stopped")
                            }
                        }
                    }
                }
        } else if (percentagePlayed >= 0.9) {
            // Update the watched movie record with the current position, percentage watched, and isFullyWatched flag
            helpViewModel.currentFocusedMovie?.currentPosition = 0
            helpViewModel.currentFocusedMovie?.percentagePlayed = 1.0
            helpViewModel.currentFocusedMovie?.isPartlyWatched = false
            helpViewModel.currentFocusedMovie?.isCompletelyWatched = true
            helpViewModel.currentFocusedMovie?.let {
                it.movieAccount.target = helpViewModel.currentMovieAccount
                it.moviecat.target = helpViewModel.currentMovieCategoryOB
                movieBox.put(it)
                updateMovieInRV(it)
                updateMovieDetails()
                if (account?.isPlex == true) {
                    account.let {
                        helpViewModel.currentFocusedMovie?.plexRatingKey?.let { id ->
                            plexViewModel.markItemAsWatched(account, id)
                        }
                    }
                }
            }

        } else {
            helpViewModel.currentFocusedMovie?.currentPosition = 0
            helpViewModel.currentFocusedMovie?.percentagePlayed = 0.0
        }
    }

    private fun updateMovieDetails() {
        val detailFragment = parentFragmentManager.findFragmentById(R.id.container_movie_info)
        if (detailFragment is MovieDetailFragment) {
            detailFragment.updateMovieRunningTime()
        } else {
            val newdetailFragment = parentFragmentManager.findFragmentById(R.id.container_globalsearch_vod_info)
            if (newdetailFragment is MovieDetailFragment) {
                newdetailFragment.updateMovieRunningTime()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentMovieUrl.isNotEmpty()) {
            if (playWithVlc) {
                startDatabaseRunnable()
                initializePlayer(currentMovieUrl)
            } else {
                player?.addListener(playerErrorListener)
                player?.addListener(handlePlaybackStateListener)
                player?.addAnalyticsListener(analyticsListener)
                player?.setVideoFrameMetadataListener(videoFrameMetadataListener)
                startPeriodicExoPlayerUpdate()
                startDatabaseRunnable()
                initializeExoPlayer(currentMovieUrl)
            }
        }
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
        currentMovieUrl = ""
        updateMovie()
        helpViewModel.playMovieSelectionModified = null
        helpViewModel.serieFullScreenOpened = false
        moviesViewModel.requestFocusOnPlayMovie()
        parentFragmentManager.popBackStack()
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
        helpViewModel.movieFullScreenOpened = false
        if (playWithVlc) {
            org.videolan.libvlc.Dialog.setCallbacks(libVLC, null)
            vlcDialogCallbacks = null
            playWithVlc = false
            mediaPlayer?.media?.release()
            mediaPlayer?.release()
            libVLC?.release()
            libVLC = null
            mediaPlayer = null
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