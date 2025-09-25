package com.example.mj_player_tv.ui

import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.drm.DrmSession.DrmSessionException
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.Accounts_
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgDataOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.FragmentHomeBinding
import com.example.mj_player_tv.databinding.FragmentPlayTvBinding
import com.example.mj_player_tv.databinding.FragmentTvChannelsBinding
import com.example.mj_player_tv.ui.adapter.ScrollTvChannelAdapter
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.TvGuideViewModel
import com.example.mj_player_tv.viewmodel.TvGuideViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

@OptIn(UnstableApi::class)
class PlayTvFragment : Fragment(R.layout.fragment_play_tv) {

    private var _binding: FragmentPlayTvBinding? = null

    private val binding get() = _binding!!

    private var scrolltvChannelAdapter: ScrollTvChannelAdapter? = null
    private var retryCount = 0
    private val maxRetries = 5
    private var tokenRefreshAttempted = false

    private var isSpooling = false
    private var spoolPosition = 0L
    private var spoolRunnable: Runnable? = null
    private val spoolHandler = Handler(Looper.getMainLooper())
    private var incrementChangeDelay: Long = 2000L

    private var maxIncrement = 16000L

    var resumeFromBackground = false

    private var isFirstPlayingChannel = true
    private var player: ExoPlayer? = null

    private val handler = Handler(Looper.getMainLooper())
    private val hideHudRunnable = Runnable {
        hideHudContainer()
    }

    private var isHudContainerOpened: Boolean = false

    private val errorHandler = Handler(Looper.getMainLooper())
    private val channelPositionsBox: Box<ChannelPositions> =
        ObjectBox.store.boxFor(ChannelPositions::class.java)

    private val tvChBox: Box<TvChannelOB> = ObjectBox.store.boxFor(TvChannelOB::class.java)

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val stalkerViewModel: StalkerViewModel by activityViewModels {
        StalkerViewModelFactory(
            requireActivity().application
        )
    }

    private val helpViewModel: HelpViewModel by activityViewModels {
        HelpViewModelFactory(
            requireActivity().application
        )
    }

    private val tvGuideViewModel: TvGuideViewModel by activityViewModels {
        TvGuideViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayTvBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializePlayer()

        helpViewModel.currentPlayingChannelPosition?.let {
            changingPlayingChannel(it)
        }

        tvGuideViewModel.changePlayingChannel.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                changingPlayingChannel(request)
                tvGuideViewModel.clearchangePlayingChannel()
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    fun initializePlayer() {
        if (player == null || resumeFromBackground) {
            resumeFromBackground = false
            player = ExoPlayer.Builder(this@PlayTvFragment.requireActivity())
                .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(50000, 50000, 1500, 2000).build())
                .setRenderersFactory(
                    DefaultRenderersFactory(requireContext())
                        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                        .setEnableDecoderFallback(true)
                )
                .build()

            binding.videoView.player = player
            binding.videoView.useController = false

            player?.addAnalyticsListener(EventLogger())
            // Player-Ereignis-Listener
        }
    }

    fun changingPlayingChannel(channelPosition: ChannelPositions) {
        val channel = channelPosition.tvchannel.target
        player?.stop()
        changedChannelSourceCheck(channelPosition)
        binding.tvPlayingError.visibility = View.GONE
        binding.tvPlayingError.text = ""
        binding.tvAudio.visibility = View.INVISIBLE
        binding.tvAudio.visibility = View.INVISIBLE
        retryCount = 0
        tokenRefreshAttempted = false
        resetChannelQualityInfo(channel)
    }

    fun resetChannelQualityInfo(channel: TvChannelOB) {
        if (!helpViewModel.isTvFullScreen) {
            binding.tvTvchannelname.visibility = VISIBLE
        }
        binding.tvTvchannelname.text = channel.showingName
        binding.tvAudio.visibility = View.INVISIBLE
        binding.tvFps.visibility = View.INVISIBLE
        binding.tvChannelQuality.visibility = View.INVISIBLE
        binding.videoView.visibility = VISIBLE
        binding.relLayoutChannelInfo.visibility = VISIBLE
    }


    fun switchChannel(url: String) {
        if (helpViewModel.isPlayingCatchup) {
            player?.stop()
            //binding.seekBar.setPosition(0)
            binding.tvTvchannelname.text = "${helpViewModel.catchupPlayingChannelPosition?.tvchannel?.target?.showingName} [REPLAY]"
            //val currentPos = tvChannelsAdapter?.currentList?.indexOfFirst { it.id == helpViewModel.currentPlayingChannelPosition?.id }
            helpViewModel.currentPlayingChannel = null
            helpViewModel.currentPlayingChannelPosition = null
            //if (currentPos != null) {
                //tvChannelsAdapter?.notifyItemChanged(currentPos)
            //}
        }
        lastFpsToShow = 0
        helpViewModel.currentlyPlayingUrl = url
        binding.videoView.visibility = VISIBLE
        // MediaSource für den neuen Sender erstellen
        val mediaSource: MediaSource =
            createMediaSource(url)
        player?.setMediaSource(mediaSource)
        player?.playWhenReady = true
        player?.prepare()
        player?.removeListener(playerErrorListener)
        player?.removeListener(handlePlaybackStateListener)
        player?.removeAnalyticsListener(analyticsListener)
        player?.addListener(handlePlaybackStateListener)
        player?.addListener(playerErrorListener)
        player?.addAnalyticsListener(analyticsListener)
        player?.clearVideoFrameMetadataListener(videoFrameMetadataListener)
        player?.setVideoFrameMetadataListener(videoFrameMetadataListener)
        // Qualität und Audio-Infos ermitteln
    }

    private fun createMediaSource(url: String): MediaSource {
        val mediaItem = MediaItem.fromUri(url)
        val dataSourceFactory = DefaultHttpDataSource.Factory()
        val customLoadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy() {
            override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                return 1
            }
        }
        return when {
            url.endsWith(".m3u8") -> {
                // HLS MediaSource
                HlsMediaSource.Factory(dataSourceFactory)
                    .setLoadErrorHandlingPolicy(customLoadErrorHandlingPolicy)
                    .createMediaSource(mediaItem)
            }
            url.endsWith(".ts") -> {
                // Progressive MediaSource mit Flags
                val extractorsFactory = DefaultExtractorsFactory()
                    .setTsExtractorFlags(
                        DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                    )
                ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                    .setLoadErrorHandlingPolicy(customLoadErrorHandlingPolicy)
                    .createMediaSource(mediaItem)
            }
            else -> {
                // Standard Progressive MediaSource
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .setLoadErrorHandlingPolicy(customLoadErrorHandlingPolicy)
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

            binding.tvChannelQuality.text = resolution
            binding.tvChannelQuality.visibility = VISIBLE
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            val audioFormat = getAudioFormatDescription(format.channelCount)
            binding.tvAudio.text = audioFormat
            binding.tvAudio.visibility = VISIBLE
        }
        override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
            hideProgressBar()
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                delay(500) // 1,5 Sekunden warten
                showProgressBar()
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
                binding.playerProgressBar.visibility = View.GONE
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.playerProgressBar.visibility =
                        VISIBLE // Zurücksetzen der Sichtbarkeit
                }, 250) // 500 Millisekunden = 0,5 Sekunden
            }
        }
    }

    var lastFrameTimeNs: Long? = null // Zeitstempel des letzten Frames
    var frameCount = 0               // Anzahl der gerenderten Frames
    var totalFrameIntervalNs = 0L    // Summe der Zeitdifferenzen zwischen Frames
    var totalFrameCount = 0          // Anzahl der berechneten FPS-Werte
    var lastFpsToShow = 0

    val videoFrameMetadataListener = object : VideoFrameMetadataListener {
        override fun onVideoFrameAboutToBeRendered(
            presentationTimeUs: Long,
            releaseTimeNs: Long,
            format: Format,
            mediaFormat: MediaFormat?
        ) {
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
                                _binding.let {
                                    binding.tvFps.text = "FPS: $fpsToShow"
                                    val channelIdByAccountData = if (helpViewModel.isPlayingCatchup) {
                                        helpViewModel.catchupPlayingChannelPosition?.tvchannel?.target?.idByAccountData
                                    } else {
                                        helpViewModel.currentPlayingChannel?.idByAccountData
                                    }
                                    if (helpViewModel.currentHudFocusedChannel?.idByAccountData == channelIdByAccountData) {
                                        binding.tvFps.visibility = VISIBLE
                                    }
                                }
                            }
                        }
                        totalFrameCount = 0
                    }
                }
            }
            // Setze den aktuellen Frame als letzten Frame
            lastFrameTimeNs = releaseTimeNs
        }
    }

    val handlePlaybackStateListener = object : Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    tokenRefreshAttempted = false
                    binding.tvPlayingError.text = ""
                    binding.tvPlayingError.visibility = View.GONE
                    helpViewModel.isCurrentlyPlayingTv = true
                    retryCount = 0
                    hideProgressBar()
                    if (isFirstPlayingChannel && helpViewModel.isTvFullScreen && binding.containerFullscreenChannelchange.visibility != VISIBLE) {
                        showHudContainer()
                        isFirstPlayingChannel = false
                    }
                    if (helpViewModel.isPlayingCatchup && helpViewModel.catchupEpgData != null) {
                        val start = helpViewModel.catchupEpgData?.startTimestamp ?: 0L
                        val end = helpViewModel.catchupEpgData?.stopTimestamp ?: 0L
                        val duration = (end - start) * 1000
                        val exoduration = player?.duration ?: duration
                        startPeriodicExoPlayerUpdate()
                    }
                }
                Player.STATE_BUFFERING -> {
                    showProgressBar()
                }
                Player.STATE_IDLE -> {
                    Log.d("CATCHUP NOCHMAL", "IDLE")
                }
                Player.STATE_ENDED -> {
                    Log.d("CATCHUP NOCHMAL", "ENDED")
                    helpViewModel.isCurrentlyPlayingTv = false
                    switchChannel(helpViewModel.currentlyPlayingUrl)
                    if (helpViewModel.isPlayingCatchup) {
                        stopPeriodicExoPlayerUpdate()
                    }
                }
            }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                startWatchTimer()
            }
            // Nicht sofort stoppen, sondern nur bei echten Pausen/Fehlern
            else if (player?.playbackState == Player.STATE_ENDED || player?.playbackState == Player.STATE_IDLE) {
                stopWatchTimer()
            }
        }
    }

    private var watchTimeJob: Job? = null

    fun startWatchTimer() {
        watchTimeJob?.cancel() // vorherigen Timer beenden
        watchTimeJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            delay(100)
            var lastUpdateTime = System.currentTimeMillis()
            while (isActive) {
                delay(1000L)
                val currentTime = System.currentTimeMillis()
                val elapsedSeconds = maxOf(0, (currentTime - lastUpdateTime) / 1000)

                if (elapsedSeconds > 0) {
                    val channelPos = if (helpViewModel.isPlayingCatchup) {
                        helpViewModel.catchupPlayingChannelPosition
                    } else {
                        helpViewModel.currentPlayingChannelPosition
                    }
                    channelPos?.tvchannel?.target?.let {
                        it.timeWatched += elapsedSeconds
                    }
                    lastUpdateTime = currentTime
                }
            }
        }
    }


    fun stopWatchTimer() {
        watchTimeJob?.cancel()
        watchTimeJob = null
        val channelPos = if (helpViewModel.isPlayingCatchup) {
            helpViewModel.catchupPlayingChannelPosition
        } else {
            helpViewModel.currentPlayingChannelPosition
        }
        channelPos?.let {
            channelPositionsBox.put(it) // In Room/Firebase/Repo
            it.tvchannel.target?.let { ch ->
                tvChBox.put(ch)
            }
        }
    }

    val playerErrorListener = object : Listener {
        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause
            if (helpViewModel.isPlayingCatchup) {
                Log.d("CATCHUP STALKER", "ERROR: $cause")
            }
            val errorMessage = if (cause is HttpDataSource.HttpDataSourceException) {
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
            } else {
                "Unknown error occurred: ${cause?.message ?: "Please try again later.."}"
            }
            binding.tvPlayingError.text = errorMessage
            binding.tvPlayingError.visibility = VISIBLE
            binding.videoView.useController = false
            if (!helpViewModel.isPlayingCatchup) {
                if (retryCount < maxRetries && helpViewModel.currentPlayingChannelPosition != null) {
                    retryCount++
                    val currentErrorText = binding.tvPlayingError.text.toString()
                    binding.tvPlayingError.text =
                        "$currentErrorText - Retry: ($retryCount/$maxRetries)"
                    // Wartezeit vor dem nächsten Versuch
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                        delay(1500) // 1,5 Sekunden warten
                        helpViewModel.currentPlayingChannelPosition?.let {
                            changedChannelSourceCheck(it)
                        }
                    }
                } else if (!tokenRefreshAttempted && helpViewModel.currentPlayingChannel?.account?.target?.isStalker == true) {
                    val currentChannel =
                        helpViewModel.currentPlayingChannelPosition!!.tvchannel.target
                    // Nur einmal Token-Refresh versuchen
                    tokenRefreshAttempted = true
                    binding.tvPlayingError.text =
                        "Maximum retries reached. Attempting to refresh token..."
                    getNewChannelLinkData(
                        currentChannel.cmd,
                        currentChannel.account.target.id,
                        helpViewModel.currentPlayingChannelPosition!!
                    )
                } else {
                    retryCount = 0
                    tokenRefreshAttempted = false
                    // Wenn auch Token-Refresh fehlschlägt
                    player?.stop()
                    binding.tvPlayingError.text =
                        "Stream could not be started. Please try again later."
                    errorHandler.postDelayed({
                        if (binding.tvPlayingError.isVisible) {
                            clearPlayingChannel()
                            binding.tvPlayingError.visibility = View.GONE // TextView ausblenden
                            if (helpViewModel.isTvFullScreen) {
                                if (!binding.rvLayoutScrollChannels.isVisible) {
                                    //setVideoViewNotFullScreen()
                                    //binding.rvLayoutTvChannels.requestFocus()
                                }
                            } else {
                                if (helpViewModel.isFullEpgContainerOpened) {
                                    //binding.rvLayoutFullEpg.requestFocus()
                                } else {
                                    //binding.rvLayoutTvChannels.requestFocus()
                                }
                            }
                        }
                    }, 5000) // 5000 Millisekunden = 5 Sekunden
                }
            } else {
                Toast.makeText(this@PlayTvFragment.requireActivity(), "Can't play catchup link!", Toast.LENGTH_SHORT).show()
                binding.tvPlayingError.text = "${cause?.message}"
            }
        }
    }

    private val updateCatchupHandler = Handler(Looper.getMainLooper())
    private val updateCatchupRunnable = object : Runnable {
        override fun run() {
            player?.let {
                if (!isSpooling) {
                    //binding.tvCurrentTimeCatchup.text = formatTime(it.currentPosition)
                    //binding.seekBar.setPosition(it.currentPosition)
                }
            }
            updateCatchupHandler.postDelayed(this, 500) // alle 500ms aktualisieren
        }
    }

    private fun startPeriodicExoPlayerUpdate() {
        updateCatchupRunnable.run()
    }

    private fun stopPeriodicExoPlayerUpdate() {
        updateCatchupHandler.removeCallbacks(updateCatchupRunnable)
    }

    fun releasePlayer() {
        helpViewModel.isCurrentlyPlayingTv = false
        player?.release()
        player = null
    }

    fun roundToNearestStandardFps(calculatedFps: Double): Int {
        // Liste der typischen FPS-Werte
        val standardFpsValues = listOf(25, 30, 50, 60, 120)

        // Suche den nächsten Wert
        return standardFpsValues.minByOrNull { kotlin.math.abs(it - calculatedFps) } ?: calculatedFps.toInt()
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

    fun getAudioFormatDescription(channels: Int?): String {
        return when {
            channels == 1 -> "Mono"
            channels == 2 -> "Stereo"
            channels == 6 -> "5.1"
            channels == 8 -> "7.1"
            else -> "N/A"
        }
    }

    fun getChannelLinkData(cmd: String, accountId: Long, tvChannelPos: ChannelPositions) {
        viewLifecycleOwner.lifecycleScope.launch {
            val tvChannel = tvChannelPos.tvchannel.target
            val accountData = accountBox.query(Accounts_.id.equal(accountId)).build().findFirst()
            if (cmd.isNotEmpty() && accountData != null) {
                if (cmd.startsWith("ffmpeg http://localhost")) {
                    val stalkerUrl = accountData.stalkerUrl
                    val macAddress = accountData.macAddress
                    val userAgent = accountData.userAgent
                    val timeZone = accountData.timezone
                    val token = accountData.token
                    val response = stalkerViewModel.getTvChannelLink(
                        stalkerUrl,
                        cmd = cmd,
                        cookie = "mac=${macAddress}; stb_lang=en; timezone=$timeZone;",
                        token = "Bearer $token",
                        userAgent
                    ).await()
                    when (response) {
                        is Resource.Error -> {
                            getNewChannelLinkData(cmd, accountData.id, tvChannelPos)
                        }
                        is Resource.Success -> {
                            if (!response.data.isNullOrEmpty()) {
                                val playToken =
                                    response.data.toString().replace(Regex("(?i)^['\"]?ffmpeg['\"]?\\s*"), "")
                                binding.tvTvchannelname.text = tvChannel.showingName
                                switchChannel(playToken)
                            } else {
                            }
                        }
                    }
                } else {
                    val playToken = cmd.replace(Regex("(?i)^['\"]?ffmpeg['\"]?\\s*"), "")
                    switchChannel(playToken)
                }
            }
        }
    }

    var tryNewChannelLinkData = false
    private fun getNewChannelLinkData(cmd: String, accountId: Long, tvChannPos: ChannelPositions) {
        viewLifecycleOwner.lifecycleScope.launch {
            val accountData = accountBox.query(Accounts_.id.equal(accountId)).build().findFirst()
            val tvChannelOB = tvChannPos.tvchannel.target
            if (cmd.isNotEmpty() && accountData != null) {
                tryNewChannelLinkData = true
                val newToken = stalkerViewModel.getToken(accountData.stalkerUrl, accountData.macAddress, accountData.userAgent, accountData.name).await()
                when (newToken) {
                    is Resource.Success -> {
                        if (cmd.startsWith("ffmpeg http://localhost")) {
                            val stalkerUrl = accountData.stalkerUrl
                            val macAddress = accountData.macAddress
                            val userAgent = accountData.userAgent
                            val timeZone = accountData.timezone
                            val token = newToken.data
                            val response = stalkerViewModel.getTvChannelLink(
                                stalkerUrl,
                                cmd = cmd,
                                cookie = "mac=${macAddress}; stb_lang=en; timezone=$timeZone;",
                                token = "Bearer $token",
                                userAgent
                            ).await()
                            when (response) {
                                is Resource.Error -> {
                                    tryNewChannelLinkData = false
                                    binding.tvPlayingError.text = response.message
                                    val handler = Handler(Looper.getMainLooper())
                                    handler.postDelayed({
                                        if (binding.tvPlayingError.visibility == VISIBLE) {
                                            clearPlayingChannel()
                                            binding.tvPlayingError.visibility = View.GONE // TextView ausblenden
                                        }
                                    }, 5000) // 5000 Millisekunden = 5 Sekunden
                                }
                                is Resource.Success -> {
                                    if (!response.data.isNullOrEmpty()) {
                                        val playToken =
                                            response.data.toString().replace(Regex("(?i)^['\"]?ffmpeg['\"]?\\s*"), "")
                                        switchChannel(
                                            playToken
                                        )
                                    }
                                }

                            }
                        } else {
                            val playToken = cmd.replace(Regex("(?i)^['\"]?ffmpeg['\"]?\\s*"), "")
                            switchChannel(playToken)
                        }
                    }

                    is Resource.Error -> {
                        tryNewChannelLinkData = false
                        binding.tvPlayingError.text = "Can't get new token!"
                        val handler = Handler(Looper.getMainLooper())
                        handler.postDelayed({
                            if (binding.tvPlayingError.isVisible) {
                                clearPlayingChannel()
                                binding.tvPlayingError.visibility = View.GONE // TextView ausblenden
                            }
                        }, 5000) // 5000 Millisekunden = 5 Sekunden
                    }
                }
            }
        }
    }

    fun clearPlayingChannel() {
        binding.tvTvchannelname.text = ""
        stopWatchTimer()
        helpViewModel.currentPlayingChannelPosition = null
        helpViewModel.currentPlayingChannel = null
        helpViewModel.catchupPlayingChannelPosition = null
        helpViewModel.catchupEpgData = null
        helpViewModel.currentlyPlayingUrl = ""
        player?.stop()
        helpViewModel.isCurrentlyPlayingTv = false
        hideProgressBar()
    }

    private var channelLoadJob: Job? = null

    fun changedChannelSourceCheck(channelPos: ChannelPositions) {
        showProgressBar()
        channelLoadJob?.cancel()
        channelLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            if (helpViewModel.channelFromSearchContainer && helpViewModel.isPlayingCatchup) {
                val url = helpViewModel.globalSearchCatchupUrl
                if (url.isNotEmpty()) {
                    //binding.seekBar.setPosition(0L)
                    switchChannel(url)
                    helpViewModel.globalSearchCatchupUrl = ""
                } else {
                    Toast.makeText(this@PlayTvFragment.requireActivity(), "Can't play catchup program!", Toast.LENGTH_SHORT).show()
                    delay(1000)
                    //setVideoViewNotFullScreen()
                }
            } else {
                val channel = channelPos.tvchannel.target
                val channelAccount = channel.account.target
                if (channelAccount.isStalker) {
                    getChannelLinkData(channel.cmd, channel.playlistId!!, channelPos)
                } else if (channelAccount.isXtream) {
                    if (channelAccount.xtreamUseDefaultType) {
                        val url =
                            "${channelAccount.stalkerUrl}/live/${channelAccount.username}/${channelAccount.macAddress}/${channel.channelId}.ts"
                        switchChannel(url)
                    } else {
                        val url =
                            "${channelAccount.stalkerUrl}/live/${channelAccount.username}/${channelAccount.macAddress}/${channel.channelId}.${channelAccount.xtreamOtherStreamType}"

                        switchChannel(url)
                    }
                }
            }
        }
    }

    private fun hideProgressBar() {
        binding.playerProgressBar.visibility = View.INVISIBLE
    }

    private fun showProgressBar() {
        binding.playerProgressBar.visibility = VISIBLE
    }

    private fun showHudContainer() {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.add(R.id.hud_container, TvHudContainerFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        val slideIn = AnimationUtils.loadAnimation(this@PlayTvFragment.requireActivity(), R.anim.slide_in_right)
        binding.hudcontainer.startAnimation(slideIn)
        binding.hudcontainer.visibility = View.VISIBLE
    }

    private fun hideHudContainer() {
        val slideOut = AnimationUtils.loadAnimation(this@PlayTvFragment.requireActivity(), R.anim.slide_out_to_right)
        binding.hudcontainer.startAnimation(slideOut)
        binding.hudcontainer.visibility = View.GONE
        binding.videoView.requestFocus()
    }

    fun calculateTimeOffsetInSeconds(timeOffset: Int): Long {
        return (timeOffset * 3600).toLong()
    }


}