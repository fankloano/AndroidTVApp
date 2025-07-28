package com.example.mj_player_tv.ui

import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder

import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.text.bold
import androidx.core.view.isInvisible
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
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import coil.load
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.Accounts_
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgDataOB_
import com.example.mj_player_tv.database.entity.TvCategoryOB
import com.example.mj_player_tv.database.entity.TvCategoryOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.database.help.AccountTvCategory
import com.example.mj_player_tv.databinding.FragmentTvChannelsBinding
import com.example.mj_player_tv.ui.adapter.TvAccountCategoryAdapter
import com.example.mj_player_tv.ui.adapter.TvChannelsAdapter
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import io.objectbox.Box
import io.objectbox.reactive.DataSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLHandshakeException


@UnstableApi
class TvChannelsFragment: Fragment(R.layout.fragment_tv_channels) {

    private var _binding: FragmentTvChannelsBinding? = null

    private val binding: FragmentTvChannelsBinding
        get() {
            if (_binding == null) {
                val stackTrace = Throwable().stackTrace // Holt den aktuellen Stacktrace
                Log.e(
                    "TvChannelsFragment",
                    "binding is NULL but accessed! Called from: ${stackTrace[1]}"
                )
            }
            return _binding!!
        }

    private lateinit var tvAccountCategoryAdapter: TvAccountCategoryAdapter

    private var tvChannelsAdapter: TvChannelsAdapter? = null

    val constraintSet = ConstraintSet()

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val tvChBox: Box<TvChannelOB> = ObjectBox.store.boxFor(TvChannelOB::class.java)

    private val tvCatBox: Box<TvCategoryOB> = ObjectBox.store.boxFor(TvCategoryOB::class.java)

    private val epgDataBox: Box<EpgDataOB> = ObjectBox.store.boxFor(EpgDataOB::class.java)

    private val manualPositionsBox: Box<ChannelPositions> =
        ObjectBox.store.boxFor(ChannelPositions::class.java)

    private var isHudContainerOpened: Boolean = false

    private var progressBar: ProgressBar? = null

    private var fullScreenProgressBar: ProgressBar? = null

    private var accountSubscription: DataSubscription? = null

    private var tvCatSubscription: DataSubscription? = null

    private var retryCount = 0
    private val maxRetries = 5
    private var tokenRefreshAttempted = false

    private var catchupDuration = 0L

    private var focusedChannelFirstEpgIdByAccountdata: String = ""

    private var focusedChannelLastEpgIdByAccountData: String = ""

    private var player: ExoPlayer? = null
    private var playbackPosition = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val hideHudRunnable = Runnable {
        hideHudContainer()
    }

    private val errorHandler = Handler(Looper.getMainLooper())

    private var isFullScreenEpgInfo = false

    private var fullAccountList = listOf<AccountTvCategory>()
    private var expandedAccountId: Long? = null
    private var currentList = listOf<AccountTvCategory>()

    private var isFirstOpen = true

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

    private val xtreamViewModel: XtreamViewModel by activityViewModels {
        XtreamViewModelFactory(
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTvChannelsBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Gehe zurück zum vorherigen Fragment im Back Stack
            releasePlayer()
            helpViewModel.isTvFullScreen = false
            helpViewModel.isTvAccountMenuOpened = false
            helpViewModel.isTvAccountsMenuFocused = false
            helpViewModel.isTvCategoryMenuFocused = false
            helpViewModel.isTvChannelsMenuFocused = false
            helpViewModel.currentlyPlayingUrl = ""
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializePlayer()
        progressBar = binding.playerProgressBar
        fullScreenProgressBar = binding.fullScreenProgressBar

        prepareRecyclerView()
        prepareTvChannelsRecyclerView()

        var accountsList = listOf<AccountTvCategory>()

        helpViewModel.tvAccountsWithCategoriesLiveData.observe(viewLifecycleOwner) { accounts ->
            if (accounts.isEmpty()) {
                if (isFirstOpen) {
                    binding.tvNoTvAccounts.visibility = View.VISIBLE
                    binding.rvLayoutTvAccountsMenu.visibility = View.INVISIBLE
                    openMainMenu()
                    if (isFirstOpen) {
                        isFirstOpen = false
                    }
                }
            } else {
                binding.tvNoTvAccounts.visibility = View.INVISIBLE
                binding.rvLayoutTvAccountsMenu.visibility = View.VISIBLE
                binding.tvNoTvAccounts.visibility = View.INVISIBLE
                binding.rvLayoutTvAccountsMenu.visibility = View.VISIBLE
                fullAccountList = accounts
                // ✅ Liste immer neu bauen!

                if (expandedAccountId != null) {
                    // Account ist gerade aufgeklappt – also aufgeklappte Version wieder aufbauen
                    val flatList = mutableListOf<AccountTvCategory>()
                    fullAccountList.forEach { account ->
                        flatList.add(account)
                        if (account is AccountTvCategory.Account && account.id == expandedAccountId) {
                            flatList.addAll(account.categories)
                        }
                    }
                    currentList = flatList
                } else {
                    // Keine Kategorie offen → nur Accounts
                    currentList = fullAccountList
                }

                if (isFirstOpen && accountsList != accounts) {
                    accountsList = accounts
                    submitCollapsedTVList()
                } else {
                    if (accountsList != accounts) {
                        accountsList = accounts
                        tvAccountCategoryAdapter.submitList(currentList)
                    }
                }
                if (helpViewModel.isTvAccountsMenuFocused || helpViewModel.wasTvSectionOpened) {
                    if (helpViewModel.wasTvSectionOpened && helpViewModel.currentPlayingTvAccount != null) {
                        val lastAccount =
                            tvAccountCategoryAdapter.currentList.firstOrNull { it is AccountTvCategory.Account && it.id == helpViewModel.currentPlayingTvAccount?.id }
                        if (lastAccount != null) {
                            val lastAccountPosition =
                                tvAccountCategoryAdapter.currentList.indexOf(lastAccount)
                            binding.rvLayoutTvAccountsMenu.setSelectedPosition(lastAccountPosition)
                            binding.rvLayoutTvAccountsMenu.requestFocus()
                        }
                    }
                }
            }
        }

        binding.videoView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                if (helpViewModel.isTvFullScreen) {
                    if (isHudContainerOpened) {
                        hideHudContainer()
                        return@setOnKeyListener true
                    } else {
                        if (isFullScreenEpgInfo) {
                            closeFullScreenChannelSelectorEpg()
                            isFullScreenEpgInfo = false
                            return@setOnKeyListener true
                        } else {
                            setVideoViewNotFullScreen()
                            return@setOnKeyListener true
                        }
                    }
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                if (!isHudContainerOpened) {
                    showHudContainer()
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                if (helpViewModel.isTvFullScreen) {
                    if (isFullScreenEpgInfo) {
                        closeFullScreenChannelSelectorEpg()
                        isFullScreenEpgInfo = false
                    }
                    hideHudContainer()
                    helpViewModel.fullScreenFocusedAccount = helpViewModel.currentPlayingTvAccount
                    helpViewModel.fullScreenFocusedTvCategory =
                        helpViewModel.currentPlayingTvCategory
                    helpViewModel.fullScreenClickedChannel = helpViewModel.currentPlayingChannel
                    showChangeFragmentInFullscreen(FullScreenSelectorFragment())
                    isFirstOpenDetailEpgFullScreen = true
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                isFullScreenEpgInfo = true
                showFullScreenChannelSelectorEpg()
                binding.videoView.requestFocus()
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                if (!helpViewModel.isPlayingCatchup) {
                    val totalChannels = (tvChannelsAdapter?.currentList?.size?.minus(1)) ?: 0
                    val currentPlayingChannelIndex =
                        tvChannelsAdapter?.currentList?.indexOf(helpViewModel.currentPlayingChannelPosition)
                    if (currentPlayingChannelIndex != null && currentPlayingChannelIndex < totalChannels) {
                        val nextChannelPosition =
                            tvChannelsAdapter?.currentList?.get(currentPlayingChannelIndex + 1)
                        if (nextChannelPosition != null) {
                            changingPlayingChannel(nextChannelPosition)
                        }
                    }
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                if (!helpViewModel.isPlayingCatchup) {
                    val currentPlayingChannelIndex =
                        tvChannelsAdapter?.currentList?.indexOf(helpViewModel.currentPlayingChannelPosition)
                    if (currentPlayingChannelIndex != null && currentPlayingChannelIndex > 0) {
                        val previousChannelPosition =
                            tvChannelsAdapter?.currentList?.get(currentPlayingChannelIndex - 1)
                        if (previousChannelPosition != null) {
                            changingPlayingChannel(previousChannelPosition)
                        }
                    }
                }
            } else {
                // Ignoriere alle anderen Tastenereignisse während des Vollbildmodus
                return@setOnKeyListener true
            }
            // Lasse die normale Verarbeitung für andere Tasten zu
            false
        }

        binding.hudLayout.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                hideHudContainer()
                return@setOnKeyListener true
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                if (helpViewModel.currentHudFocusedChannel != helpViewModel.currentPlayingChannel) {
                    if (helpViewModel.currentHudFocusedChannelPosition != null) {
                        changingPlayingChannel(helpViewModel.currentHudFocusedChannelPosition!!)
                    }
                    return@setOnKeyListener true
                } else {
                    return@setOnKeyListener true
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                if (!helpViewModel.isPlayingCatchup) {
                    val hudtvchannel = helpViewModel.currentHudFocusedChannel
                    val timeOffSet = hudtvchannel?.epgTimeOffSet
                        ?: helpViewModel.currentHudFocusedChannelPosition?.tvcategory?.target?.epgTimeOffSet
                        ?: hudtvchannel?.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                        ?: 0
                    val currentNextEpgStartTime = hudcurrentNextEpg?.startTimestamp?.plus(
                        calculateTimeOffsetInSeconds(timeOffSet)
                    )
                    if (currentNextEpgStartTime != null) {
                        if (hudtvchannel != null) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val thisEpg =
                                    hudtvchannel.linkedEpgChannel?.target?.chEpgId?.let { chEpgId ->
                                        withContext(Dispatchers.IO) {
                                            if (hudcurrentEpg != null) {
                                                epgDataBox.query(
                                                    EpgDataOB_.epgChId.equal(chEpgId) // Filter für den EPG-Channel
                                                        .and(
                                                            EpgDataOB_.stopTimestamp.lessOrEqual(
                                                                currentNextEpgStartTime
                                                            )
                                                        )
                                                        .and(
                                                            EpgDataOB_.idByAccountData.notEqual(
                                                                hudcurrentEpg!!.idByAccountData
                                                            )
                                                        )// Filter für Stopzeit < currentNextEpgStartTime
                                                )
                                                    .orderDesc(EpgDataOB_.startTimestamp) // Sortiere absteigend nach Startzeit (neueste zuerst)
                                                    .build()
                                                    .findFirst() // Hole nur den ersten Eintrag (die letzte passende Sendung)
                                            } else {
                                                epgDataBox.query(
                                                    EpgDataOB_.epgChId.equal(chEpgId) // Filter für den EPG-Channel
                                                        .and(
                                                            EpgDataOB_.stopTimestamp.lessOrEqual(
                                                                currentNextEpgStartTime
                                                            )
                                                        ) // Filter für Stopzeit < currentNextEpgStartTime
                                                )
                                                    .orderDesc(EpgDataOB_.startTimestamp) // Sortiere absteigend nach Startzeit (neueste zuerst)
                                                    .build()
                                                    .findFirst() // Hole nur den ersten Eintrag (die letzte passende Sendung)
                                            }
                                        }
                                    }
                                withContext(Dispatchers.Main) {
                                    if (thisEpg?.idByAccountData == focusedChannelFirstEpgIdByAccountdata) {
                                        Log.d("EPGHUD", "LINKS EXTERN: LETZTE")
                                        binding.fullscreenChannelpreviousepg.visibility =
                                            View.INVISIBLE
                                    } else {
                                        Log.d("EPGHUD", "LINKS EXTERN: NICHT LETZTE")
                                        binding.fullscreenChannelnextepg.visibility = View.VISIBLE
                                    }
                                    if (thisEpg != null) {
                                        binding.fullscreenChannelpreviousepg.visibility =
                                            View.VISIBLE
                                        changeHudNextEpg(thisEpg)
                                    } else {
                                        binding.fullscreenChannelpreviousepg.visibility =
                                            View.INVISIBLE
                                    }
                                }
                            }
                        }
                        binding.hudLayout.requestFocus()
                        handler.postDelayed(hideHudRunnable, 8000)
                        return@setOnKeyListener true
                    } else {
                        handler.postDelayed(hideHudRunnable, 8000)
                        return@setOnKeyListener true
                    }
                } else {
                    handler.postDelayed(hideHudRunnable, 8000)
                    return@setOnKeyListener true
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                if (!helpViewModel.isPlayingCatchup) {
                    val hudtvchannel = helpViewModel.currentHudFocusedChannel
                    val timeOffSet = hudtvchannel?.epgTimeOffSet
                        ?: helpViewModel.currentHudFocusedChannelPosition?.tvcategory?.target?.epgTimeOffSet
                        ?: hudtvchannel?.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                        ?: 0

                    val currentNextEpgEndTimeStamp = hudcurrentNextEpg?.stopTimestamp?.plus(
                        calculateTimeOffsetInSeconds(timeOffSet)
                    )

                    if (currentNextEpgEndTimeStamp != null) {
                        if (hudtvchannel != null) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val thisEpg =
                                    hudtvchannel.linkedEpgChannel?.target?.chEpgId?.let { chEpgId ->
                                        withContext(Dispatchers.IO) {
                                            if (hudcurrentEpg != null) {
                                                epgDataBox.query(
                                                    EpgDataOB_.epgChId.equal(chEpgId) // Filter für den EPG-Channel
                                                        .and(
                                                            EpgDataOB_.startTimestamp.greaterOrEqual(
                                                                currentNextEpgEndTimeStamp
                                                            )
                                                                .and(
                                                                    EpgDataOB_.idByAccountData.notEqual(
                                                                        hudcurrentEpg!!.idByAccountData
                                                                    )
                                                                )
                                                        ) // Filter für Stopzeit < currentNextEpgStartTime
                                                )
                                                    .order(EpgDataOB_.startTimestamp) // Sortiere absteigend nach Startzeit (neueste zuerst)
                                                    .build()
                                                    .findFirst() // Hole nur den ersten Eintrag (die letzte passende Sendung)
                                            } else {
                                                epgDataBox.query(
                                                    EpgDataOB_.epgChId.equal(chEpgId) // Filter für den EPG-Channel
                                                        .and(
                                                            EpgDataOB_.startTimestamp.greaterOrEqual(
                                                                currentNextEpgEndTimeStamp
                                                            )
                                                        ) // Filter für Stopzeit < currentNextEpgStartTime
                                                )
                                                    .order(EpgDataOB_.startTimestamp) // Sortiere absteigend nach Startzeit (neueste zuerst)
                                                    .build()
                                                    .findFirst() // Hole nur den ersten Eintrag (die letzte passende Sendung)
                                            }
                                        }
                                    }
                                withContext(Dispatchers.Main) {
                                    if (thisEpg?.idByAccountData == focusedChannelFirstEpgIdByAccountdata) {
                                        binding.fullscreenChannelpreviousepg.visibility =
                                            View.INVISIBLE
                                    } else {
                                        binding.fullscreenChannelnextepg.visibility = View.VISIBLE
                                    }
                                    if (thisEpg?.idByAccountData == focusedChannelLastEpgIdByAccountData) {
                                        binding.fullscreenChannelnextepg.visibility = View.INVISIBLE
                                    } else {
                                        binding.fullscreenChannelpreviousepg.visibility =
                                            View.VISIBLE
                                    }
                                    if (thisEpg != null) {
                                        binding.fullscreenChannelpreviousepg.visibility =
                                            View.VISIBLE
                                        changeHudNextEpg(thisEpg)
                                    } else {
                                        binding.fullscreenChannelnextepg.visibility = View.INVISIBLE
                                    }
                                }
                            }
                        }
                        binding.hudLayout.requestFocus()
                        handler.postDelayed(hideHudRunnable, 8000)
                        return@setOnKeyListener true
                    } else {
                        handler.postDelayed(hideHudRunnable, 8000)
                        return@setOnKeyListener true
                    }
                } else {
                    handler.postDelayed(hideHudRunnable, 8000)
                    return@setOnKeyListener true
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                if (!helpViewModel.isPlayingCatchup) {
                    val currentPlayingChannelIndex =
                        tvChannelsAdapter?.currentList?.indexOf(helpViewModel.currentHudFocusedChannelPosition)
                    if (currentPlayingChannelIndex != null && currentPlayingChannelIndex > 0) {
                        val previousChannelPosition =
                            tvChannelsAdapter?.currentList?.get(currentPlayingChannelIndex - 1)
                        if (previousChannelPosition != null) {
                            val previousChannel = previousChannelPosition.tvchannel.target
                            if (previousChannelPosition.id == tvChannelsAdapter?.currentList?.firstOrNull()?.id) {
                                binding.fullscreenChannelprevious.visibility = View.INVISIBLE
                            } else {
                                binding.fullscreenChannelpreviousepg.visibility = View.VISIBLE
                            }
                            binding.fullscreenChannelnext.visibility = View.VISIBLE
                            viewLifecycleOwner.lifecycleScope.launch {
                                val epgChannelId = previousChannel.linkedEpgChannel?.target?.chEpgId
                                val currentTimeMillis = (System.currentTimeMillis() / 1000).plus(
                                    calculateTimeOffsetInSeconds(
                                        previousChannel.epgTimeOffSet
                                            ?: previousChannelPosition.tvcategory.target.epgTimeOffSet
                                            ?: previousChannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                                            ?: 0
                                    )
                                )
                                val currentAndNextEpg = epgChannelId?.let {
                                    withContext(Dispatchers.IO) {
                                        epgDataBox.query(
                                            EpgDataOB_.epgChId.equal(it)
                                                .and(
                                                    EpgDataOB_.stopTimestamp.greater(
                                                        currentTimeMillis
                                                    )
                                                )
                                        ).order(EpgDataOB_.startTimestamp).build().find(0, 2)
                                    }
                                }
                                updateFullScreenChannel(
                                    previousChannelPosition,
                                    currentAndNextEpg?.firstOrNull(),
                                    if (currentAndNextEpg != null && currentAndNextEpg.size > 1) {
                                        currentAndNextEpg[1]
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                        binding.hudLayout.requestFocus()
                        handler.postDelayed(hideHudRunnable, 8000)
                        return@setOnKeyListener true
                    } else {
                        binding.fullscreenChannelprevious.visibility = View.INVISIBLE
                        handler.postDelayed(hideHudRunnable, 8000)
                        return@setOnKeyListener true
                    }
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                handler.removeCallbacks(hideHudRunnable)
                if (!helpViewModel.isPlayingCatchup) {
                    val totalChannels = tvChannelsAdapter?.currentList?.size
                    val currentPlayingChannelIndex =
                        tvChannelsAdapter?.currentList?.indexOf(helpViewModel.currentHudFocusedChannelPosition)
                    if (currentPlayingChannelIndex != null && totalChannels != null && currentPlayingChannelIndex < (totalChannels - 1)) {
                        binding.fullscreenChannelprevious.visibility = View.VISIBLE
                        val nextChannelPosition =
                            tvChannelsAdapter?.currentList?.get(currentPlayingChannelIndex + 1)
                        if (nextChannelPosition != null) {
                            val nextChannel = nextChannelPosition.tvchannel.target
                            if (nextChannelPosition.id == tvChannelsAdapter?.currentList?.lastOrNull()?.id) {
                                binding.fullscreenChannelnext.visibility = View.INVISIBLE
                            } else {
                                binding.fullscreenChannelnext.visibility = View.VISIBLE
                            }
                            binding.fullscreenChannelprevious.visibility = View.VISIBLE
                            viewLifecycleOwner.lifecycleScope.launch {
                                val epgChannelId = nextChannel.linkedEpgChannel?.target?.chEpgId
                                val currentTimeMillis = (System.currentTimeMillis() / 1000).plus(
                                    calculateTimeOffsetInSeconds(
                                        nextChannel.epgTimeOffSet
                                            ?: nextChannelPosition.tvcategory.target.epgTimeOffSet
                                            ?: nextChannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                                            ?: 0
                                    )
                                )
                                val currentAndNextEpg = epgChannelId?.let {
                                    withContext(Dispatchers.IO) {
                                        epgDataBox.query(
                                            EpgDataOB_.epgChId.equal(it)
                                                .and(
                                                    EpgDataOB_.stopTimestamp.greater(
                                                        currentTimeMillis
                                                    )
                                                )
                                        ).order(EpgDataOB_.startTimestamp).build().find(0, 2)
                                    }
                                }
                                updateFullScreenChannel(
                                    nextChannelPosition,
                                    currentAndNextEpg?.firstOrNull(),
                                    if (currentAndNextEpg != null && currentAndNextEpg.size > 1) {
                                        currentAndNextEpg[1]
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                        binding.hudLayout.requestFocus()
                        handler.postDelayed(hideHudRunnable, 8000)
                        return@setOnKeyListener true
                    } else {
                        binding.fullscreenChannelnext.visibility = View.INVISIBLE
                        handler.postDelayed(hideHudRunnable, 8000)
                        return@setOnKeyListener true
                    }
                }
            } else {
                // Ignoriere alle anderen Tastenereignisse während des Vollbildmodus
                return@setOnKeyListener true
            }
            // Lasse die normale Verarbeitung für andere Tasten zu
            false
        }


        binding.tvShowFullEpg.setOnClickListener {
            if (helpViewModel.currentFocusedChannPosition != null) {
                helpViewModel.currentSelectedEpgForSelectedChannel = null
                showFullEpgContainer()
            }
        }

        binding.tvShowFullEpg.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                binding.tvDescription.requestFocus()
                binding.borderAnimated.visibility = View.VISIBLE
                val animation = AnimationUtils.loadAnimation(
                    this@TvChannelsFragment.requireActivity(),
                    R.anim.blinked_border
                )
                binding.borderAnimated.startAnimation(animation)
                return@setOnKeyListener true

            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                setFocusToTvChannels()
                return@setOnKeyListener true
            } else {
                false
            }
        }

        binding.tvDescription.setOnClickListener {
            if (helpViewModel.currentSelectedEpgForSelectedChannel != null) {
                helpViewModel.epgPreviewEpgDetail = true
                showDetailEpgContainer()
            }
        }

        binding.tvDescription.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.borderAnimated.visibility = View.VISIBLE
                val animation = AnimationUtils.loadAnimation(
                    this@TvChannelsFragment.requireActivity(),
                    R.anim.blinked_border
                )
                binding.borderAnimated.startAnimation(animation)
            } else {
                binding.borderAnimated.clearAnimation()
                binding.borderAnimated.visibility = View.INVISIBLE
            }
        }

        binding.tvDescription.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                setFocusToTvChannels()
                return@setOnKeyListener true
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                binding.tvShowFullEpg.requestFocus()
                return@setOnKeyListener true
            } else {
                false
            }
        }

        helpViewModel.epgTimeOffsetComplete.observe(viewLifecycleOwner) { epgTimeOffset ->
            when (epgTimeOffset) {
                1 -> {
                    updateChannelList()
                }
            }
        }

        helpViewModel.matchAndUpdateComplete.observe(viewLifecycleOwner) { matchAndUpdate ->
            when (matchAndUpdate) {
                1 -> {
                    updateChannelList()
                    helpViewModel.matchAndUpdateCompleteReset()
                }
            }
        }

        helpViewModel.updateLogoSourceComplete.observe(viewLifecycleOwner) { logoUpdate ->
            when (logoUpdate) {
                1 -> {
                    val accountId = helpViewModel.currentPlayingTvAccount?.id
                    if (accountId != null) {
                        helpViewModel.currentPlayingTvAccount = accountBox.get(accountId)
                    }
                    updateChannelList()
                    helpViewModel.updateLogoSourceCompleteReset()
                }
            }
        }

        helpViewModel.epgSourceChangeComplete.observe(viewLifecycleOwner) { epgSourceUpdate ->
            when (epgSourceUpdate) {
                1 -> {
                    tvChannelsAdapter?.epgForChannelCache?.clear()
                    updateChannelList()
                    helpViewModel.epgSourceChangeCompleteReset()
                }
            }
        }

        binding.seekBar.setOnFocusChangeListener { seekbar, hasFocus ->
            if (hasFocus) {
                binding.seekBar.setUnplayedColor(resources.getColor(R.color.white))
            } else {
                binding.seekBar.setUnplayedColor(resources.getColor(R.color.light_mid_grey))
            }
        }

        val spoolHandler = Handler(Looper.getMainLooper())
        var isSpoolingFast = false
        var spoolRunnable: Runnable? = null

        binding.seekBar.setOnKeyListener { _, keyCode, event ->
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        isSpoolingFast = true
                        spoolRunnable = object : Runnable {
                            override fun run() {
                                if (isSpoolingFast) {
                                    val newPosition =
                                        (player?.currentPosition?.minus(10000))?.coerceAtLeast(0)
                                            ?: 0
                                    player?.seekTo(newPosition)

                                    // Wiederholen nach 100ms
                                    spoolHandler.postDelayed(this, 100)
                                }
                            }
                        }
                        spoolHandler.post(spoolRunnable!!)
                        return@setOnKeyListener true
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        isSpoolingFast = false
                        spoolHandler.removeCallbacks(spoolRunnable!!)
                        updateSeekbarUI()
                        return@setOnKeyListener true
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        isSpoolingFast = true
                        spoolRunnable = object : Runnable {
                            override fun run() {
                                if (isSpoolingFast) {
                                    val newPosition =
                                        (player?.currentPosition?.plus(10000))?.coerceAtMost(
                                            catchupDuration ?: 0
                                        ) ?: 0
                                    player?.seekTo(newPosition)

                                    // Wiederholen nach 100ms
                                    spoolHandler.postDelayed(this, 100)
                                }
                            }
                        }
                        spoolHandler.post(spoolRunnable!!)
                        return@setOnKeyListener true
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        isSpoolingFast = false
                        spoolHandler.removeCallbacks(spoolRunnable!!)
                        updateSeekbarUI()
                        return@setOnKeyListener true
                    }
                }

                KeyEvent.KEYCODE_BACK -> {
                    hideHudContainer()
                }

                else -> return@setOnKeyListener false
            }
            false
        }
    }

    // Aktualisiere UI nach dem Spulen
    fun updateSeekbarUI() {
        binding.seekBar.requestFocus()
        binding.tvCurrentTimeCatchup.text = formatTime(player?.currentPosition ?: 0)
    }


    //PREPARE RECYCLERVIEWS

    private fun prepareRecyclerView() {
        tvAccountCategoryAdapter = TvAccountCategoryAdapter(
            ::onAccountClicked,
            { currentList },
            helpViewModel,
            this,
            oncategorylongClickListener
        )
        binding.rvLayoutTvAccountsMenu.apply {
            adapter = tvAccountCategoryAdapter
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(true, true)
            itemAnimator = DefaultItemAnimator().apply {
                addDuration = 300
                removeDuration = 300
                moveDuration = 300
                changeDuration = 300
            }
        }
    }

    private val oncategorylongClickListener =
        TvAccountCategoryAdapter.OnLongClickListener { view, position ->

        }

    private fun prepareTvChannelsRecyclerView() {
        tvChannelsAdapter = TvChannelsAdapter(
            onChannelClickListener,
            onChannelLongClickListener,
            this,
            helpViewModel,
            epgDataBox
        )
        binding.rvLayoutTvChannels.apply {
            adapter = tvChannelsAdapter
            addItemDecoration(
                DpadLinearSpacingDecoration.create(
                    itemSpacing = 4,
                    edgeSpacing = 4,
                    perpendicularEdgeSpacing = 4
                )
            )
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(false, false)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private fun submitCollapsedTVList() {
        currentList = fullAccountList
        tvAccountCategoryAdapter.submitList(currentList)
        binding.rvLayoutTvAccountsMenu.post {
            if (isFirstOpen) {
                if (helpViewModel.clickedTvAccountId != 0L && helpViewModel.clickedTvAccountPosition != -1) {
                    onAccountClicked(helpViewModel.clickedTvAccountPosition)
                } else {
                    isFirstOpen = false
                    binding.rvLayoutTvAccountsMenu.requestFocus()
                }
            } else {
                binding.rvLayoutTvAccountsMenu.requestFocus()
            }
        }
    }

    private fun onAccountClicked(position: Int) {
        val item = tvAccountCategoryAdapter.currentList[position] as AccountTvCategory.Account

        if (expandedAccountId == item.id) {
            expandedAccountId = null
            helpViewModel.clickedTvAccountId = 0L
            helpViewModel.clickedTvAccountPosition = -1
            tvAccountCategoryAdapter.notifyItemChanged(position)
            submitCollapsedTVList()
            binding.rvLayoutTvAccountsMenu.post {
                binding.rvLayoutTvAccountsMenu.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
            }
            return
        }
        expandedAccountId = item.id

        val oldAccount = tvAccountCategoryAdapter.currentList.firstOrNull {
            it is AccountTvCategory.Account && it.id == helpViewModel.clickedTvAccountId
        } as? AccountTvCategory.Account
        val oldAccountPosition = tvAccountCategoryAdapter.currentList.indexOf(oldAccount)
        val newAccount = tvAccountCategoryAdapter.currentList.firstOrNull {
            it is AccountTvCategory.Account && it.id == item.id
        } as? AccountTvCategory.Account
        val newAccountPosition = tvAccountCategoryAdapter.currentList.indexOf(newAccount)

        tvAccountCategoryAdapter.notifyItemChanged(oldAccountPosition)

        helpViewModel.clickedTvAccountId = item.id
        helpViewModel.clickedTvAccountPosition = position

        tvAccountCategoryAdapter.notifyItemChanged(newAccountPosition)

        val flatList = mutableListOf<AccountTvCategory>()
        fullAccountList.forEach { account ->
            if (account is AccountTvCategory.Account) {
                flatList.add(account)
                if (account.id == item.id) {
                    flatList.addAll(account.categories)
                }
            }
        }

        currentList = flatList
        tvAccountCategoryAdapter.submitList(flatList) {
            binding.rvLayoutTvAccountsMenu.post {
                val list = tvAccountCategoryAdapter.currentList
                val clickedAccount = tvAccountCategoryAdapter.currentList.firstOrNull {
                    it is AccountTvCategory.Account && it.id == item.id
                } as? AccountTvCategory.Account
                val clickedAccountPosition = tvAccountCategoryAdapter.currentList.indexOf(clickedAccount)
                // Scroll zu Account, falls notwendig
                binding.rvLayoutTvAccountsMenu.scrollToPosition(clickedAccountPosition)

                // WICHTIG: Stelle sicher, dass die Kategorie darunter aufgebaut wird
                if (position + 1 < list.size &&
                    list[position + 1] is AccountTvCategory.TvCategory
                ) {

                    // Kein requestFocus()! Nur sicherstellen, dass ViewHolder aufgebaut ist.
                    binding.rvLayoutTvAccountsMenu.post {
                        binding.rvLayoutTvAccountsMenu
                            .findViewHolderForAdapterPosition(clickedAccountPosition)
                        // Nichts weiter tun – dadurch ist das Item bereit für Fokus per DPAD_DOWN
                    }
                }
                if (isFirstOpen) {
                    val focusedCategoryId = helpViewModel.currentFocusedTvCategory?.id ?: 0L
                    if (focusedCategoryId != 0L) {
                        val categoryPosition = list.indexOfFirst {
                            it is AccountTvCategory.TvCategory && it.id == focusedCategoryId
                        }

                        if (categoryPosition != -1) {
                            binding.rvLayoutTvAccountsMenu.setSelectedPosition(categoryPosition)
                            binding.rvLayoutTvAccountsMenu.post {
                                binding.rvLayoutTvAccountsMenu
                                    .findViewHolderForAdapterPosition(categoryPosition)
                                    ?.itemView?.requestFocus()
                            }
                        }
                    }
                    isFirstOpen = false
                }
            }
        }

        if (item.categories.isEmpty()) {
            Toast.makeText(
                this@TvChannelsFragment.requireActivity(),
                "No categories enabled!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun saveCurrentList(tvChannelPos: ChannelPositions) {
        val current = tvChannelsAdapter?.currentList

        manualPositionsBox.put(current) // oder viewModel.persistList()
        val tvCategory = tvChannelPos.tvcategory.target
        if (tvCategory.orderBy != 2) {
            tvCategory.orderBy = 2
            tvCatBox.put(tvCategory)
        }
    }


    fun updateTvChannels() {
        if (!tvChannelsAdapter?.currentList.isNullOrEmpty()) {
            helpViewModel.currentFocusedTvAccount?.epgsources?.reset()
            tvChannelsAdapter?.notifyDataSetChanged()
        }
    }

    //FILL RECYCLERVIEWS WITH DATA

    fun openTvCatMenu() {
        setLayoutAlphaExcludingFragments()
        showCategoryOptionsContainer()
    }

    var firstOpenTvCategory = true

    fun updateChannelList() {
        if (helpViewModel.currentFocusedTvCategory != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val sortedChannels = when {
                        helpViewModel.currentFocusedTvCategory!!.isAllChannelsCategory -> {
                            helpViewModel.currentFocusedTvAccount?.channels?.reset()
                            val categories = helpViewModel.currentFocusedTvAccount?.tvcategories
                                ?.filter {
                                    it.favorite && !it.isFavoriteCategory && !it.userCategory
                                }
                            val channelPositions: MutableList<ChannelPositions> = mutableListOf()
                            categories?.forEach {
                                channelPositions.addAll(it.tvChannelLink)
                            }
                            channelPositions
                        }

                        else -> {
                            helpViewModel.currentFocusedTvCategory!!.tvChannelLink.reset()
                            when (helpViewModel.currentFocusedTvCategory!!.orderBy) {
                                0 -> {
                                    val categoryLinks =
                                        helpViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.originalPosition }.filter {
                                            it.isSelected
                                        }

                                    if (helpViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
                                        categoryLinks.filter { it.tvchannel.target.isFavorite }
                                    } else {
                                        categoryLinks
                                    }
                                }

                                1 -> {
                                    val categoryLinks =
                                        helpViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.tvchannel.target.showingName }.filter {
                                            it.isSelected
                                        }
                                    if (helpViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
                                        categoryLinks.filter { it.tvchannel.target.isFavorite }
                                    } else {
                                        categoryLinks
                                    }
                                }

                                else -> {
                                    val categoryLinks =
                                        helpViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.position }.filter {
                                            it.isSelected
                                        }
                                    if (helpViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
                                        categoryLinks.filter { it.tvchannel.target.isFavorite }
                                    } else {
                                        categoryLinks
                                    }
                                }
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        if (!tvChannelsAdapter?.currentList.isNullOrEmpty()) {
                            tvChannelsAdapter?.submitList(sortedChannels)
                            tvChannelsAdapter?.submitListToUse(sortedChannels)
                            if (helpViewModel.isChannelHide) {
                                val position =
                                    tvChannelsAdapter?.currentList?.indexOf(helpViewModel.currentFocusedChannPosition)
                                if (position != null) {
                                    binding.rvLayoutTvChannels.post {
                                        binding.rvLayoutTvChannels.setSelectedPosition(position)
                                        binding.rvLayoutTvChannels.requestFocus()
                                    }
                                }
                                helpViewModel.isChannelHide = false
                            }
                            if (sortedChannels.isEmpty()) {
                                setTvAccountsVisibilityAnimated(true)
                                resetEpgPreview()
                                focusToTvAccountFromChannel()
                                Toast.makeText(
                                    this@TvChannelsFragment.requireActivity(),
                                    "No channels found!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateSingleChannel() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (helpViewModel.currentFocusedChannPosition != null && helpViewModel.currentFocusedChannPosition?.tvchannel?.target?.linkedEpgChannel?.target?.isExternalEpg == false) {
                if (helpViewModel.currentFocusedChannPosition?.tvchannel?.target?.account?.target?.isStalker == true) {
                    stalkerViewModel.checkChannelsAndShortEpg(helpViewModel.currentFocusedChannPosition!!.tvchannel.target)
                } else {
                    xtreamViewModel.checkChannelsAndShortEpg(helpViewModel.currentFocusedChannPosition!!.tvchannel.target)
                }
            }
            if (helpViewModel.assignChannelToEpgActive) {
                tvChannelsAdapter?.epgForChannelCache?.set(helpViewModel.currentFocusedChannel!!.id,
                    mutableListOf()
                )
            }
            val position = if(!helpViewModel.assignChannelToEpgActive) {
                tvChannelsAdapter?.currentList?.indexOf(helpViewModel.currentFocusedChannPosition)
            } else {
                Log.d("ASSIGNSINGLEEPG", "update: ${helpViewModel.currentAssignChannelPosition?.tvchannel?.target?.showingName}")
                tvChannelsAdapter?.currentList?.indexOf(helpViewModel.currentAssignChannelPosition)
            }
            if (position != null) {
                tvChannelsAdapter?.notifyItemChanged(position)
            }
        }
    }

    fun updateLastFocusedAssignChannel() {
        if (helpViewModel.currentAssignEpgChannel != null && helpViewModel.currentAssignEpgChannel?.id != helpViewModel.currentFocusedChannel?.id) {
            val lastAssignChannel = tvChannelsAdapter?.currentList?.firstOrNull { it.tvchannel.target.id == helpViewModel.currentAssignEpgChannel!!.id }
            val lastAssignChannelPosition = tvChannelsAdapter?.currentList?.indexOf(lastAssignChannel)
            if (lastAssignChannelPosition != null) {
                tvChannelsAdapter?.notifyItemChanged(lastAssignChannelPosition)
            }
            helpViewModel.currentAssignEpgChannel = null
        }
    }

    private var currentEpgJob: Job? = null
    private var currentDatabaseJob: Job? = null
    private var currentTvChannelsJob: Job? = null
    private var firstOpenTvChannels = true

    fun selectLastCategory(position: Int, catId: Long) {
        if (!helpViewModel.isTvAccountFocused) {
            tvAccountCategoryAdapter.selectedTvCategoryId = catId
            tvAccountCategoryAdapter.notifyItemChanged(position)
        }
    }

    fun resetSelectedTvCategory(position: Int) {
        tvAccountCategoryAdapter.selectedTvCategoryId = 0L
        tvAccountCategoryAdapter.notifyItemChanged(position)
    }

    fun showChannelList(accounttvCategoryId: Long) {
        if (firstOpenTvChannels || helpViewModel.wasTvSectionOpened) {
            binding.rvLayoutTvChannels.visibility = View.VISIBLE
            binding.relLayoutEpg.visibility = View.VISIBLE
            binding.videoViewPreview.visibility = View.VISIBLE
            firstOpenTvChannels = false
        }
        if (helpViewModel.currentFocusedTvCategory?.id != accounttvCategoryId || helpViewModel.wasTvSectionOpened) {
            tvChannelsAdapter?.submitList(null)
            val tvCategory = tvCatBox.get(accounttvCategoryId)
            firstOpenTvCategory = true
            helpViewModel.currentFocusedTvCategory = tvCategory
            binding.tvSelectedTvCategory.text = tvCategory.showingName
            currentEpgJob?.cancel()
            currentDatabaseJob?.cancel()
            currentTvChannelsJob?.cancel()
            currentTvChannelsJob = viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val sortedChannels = when {
                        helpViewModel.currentFocusedTvCategory!!.isAllChannelsCategory -> {
                            helpViewModel.currentFocusedTvAccount?.channels?.reset()
                            val categories = helpViewModel.currentFocusedTvAccount?.tvcategories
                                ?.filter {
                                    it.favorite && !it.isFavoriteCategory && !it.userCategory
                                }
                            val channelPositions: MutableList<ChannelPositions> = mutableListOf()
                            categories?.forEach {
                                channelPositions.addAll(it.tvChannelLink)
                            }
                            channelPositions
                        }

                        else -> {
                            helpViewModel.currentFocusedTvCategory!!.tvChannelLink.reset()
                            when (helpViewModel.currentFocusedTvCategory!!.orderBy) {
                                0 -> {
                                    val categoryLinks =
                                        helpViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.originalPosition }.filter {
                                            it.isSelected
                                        }

                                    if (helpViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
                                        categoryLinks.filter { it.tvchannel.target.isFavorite }
                                    } else {
                                        categoryLinks
                                    }
                                }

                                1 -> {
                                    val categoryLinks =
                                        helpViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.tvchannel.target.showingName }.filter {
                                            it.isSelected
                                        }
                                    if (helpViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
                                        categoryLinks.filter { it.tvchannel.target.isFavorite }
                                    } else {
                                        categoryLinks
                                    }
                                }

                                else -> {
                                    val categoryLinks =
                                        helpViewModel.currentFocusedTvCategory!!.tvChannelLink.sortedBy { it.position }.filter {
                                            it.isSelected
                                        }
                                    if (helpViewModel.currentFocusedTvCategory!!.isFavoriteCategory) {
                                        categoryLinks.filter { it.tvchannel.target.isFavorite }
                                    } else {
                                        categoryLinks
                                    }
                                }
                            }
                        }
                    }
                    if (sortedChannels.isNotEmpty()) {

                        withContext(Dispatchers.Main) {
                            binding.tvCatChannelSize.visibility = View.VISIBLE
                            binding.tvCatChannelSize.text = "${sortedChannels.size}"
                            tvChannelsAdapter?.submitList(sortedChannels)
                            tvChannelsAdapter?.submitListToUse(sortedChannels)
                            val firstChannel = sortedChannels.firstOrNull()
                            if (firstChannel != null) {
                                showEpgPreview(firstChannel.tvchannel.target)
                            }
                        }
                        if (firstOpenTvCategory || helpViewModel.wasTvSectionOpened) {
                            if (tvCategory.tvaccount.target.isUserCategories) {
                                val filteredchannels = sortedChannels.filter { it.tvchannel.target.account.target.epgsources.filter { it.isSelected }.any { it.isPlaylistEpg } }
                                checkChannelEpg(filteredchannels)
                            } else {
                                if (helpViewModel.currentFocusedTvAccount!!.epgsources.filter { it.isSelected }.any { it.isPlaylistEpg }) {
                                    checkChannelEpg(sortedChannels)
                                }
                            }
                            firstOpenTvCategory = false
                            if (helpViewModel.wasTvSectionOpened) {
                                val lastChannel = tvChannelsAdapter?.currentList?.firstOrNull { it.catAndChannelAccount == helpViewModel.currentPlayingChannelPosition?.catAndChannelAccount }
                                if (lastChannel != null) {
                                    withContext(Dispatchers.Main) {
                                        showEpgPreview(lastChannel.tvchannel.target)
                                        val lastChannelPosition =
                                            tvChannelsAdapter?.currentList?.indexOf(lastChannel)
                                        if (lastChannelPosition != null) {
                                            binding.rvLayoutTvChannels.post {
                                                binding.rvLayoutTvChannels.setSelectedPosition(
                                                    lastChannelPosition
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    val firstChannel = tvChannelsAdapter?.currentList?.firstOrNull()
                                    if (firstChannel != null) {
                                        withContext(Dispatchers.Main) {
                                            showEpgPreview(firstChannel.tvchannel.target)
                                        }
                                    }
                                }
                                helpViewModel.wasTvSectionOpened = false
                            }
                        }
                        withContext(Dispatchers.Main) {
                            binding.tvNoTvCategories.visibility = View.INVISIBLE
                        }
                    } else {
                        binding.tvLoadEpg.visibility = View.INVISIBLE
                        binding.relLayoutEpg.visibility = View.INVISIBLE
                        binding.tvCatChannelSize.visibility = View.INVISIBLE
                        tvChannelsAdapter?.submitList(null)
                        tvChannelsAdapter?.submitListToUse(null)
                        withContext(Dispatchers.Main) {
                            resetEpgPreview()
                            binding.rvLayoutTvAccountsMenu.requestFocus()
                        }
                    }
                }
            }
        }
    }

    fun updateAccount(accountId: Long) {
        helpViewModel.currentFocusedTvAccount = accountBox.get(accountId)
    }

    fun checkSingleChannelEpg(channelPos: ChannelPositions) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val tvChannel = channelPos.tvchannel.target ?: return@withContext
                val account = tvChannel.account.target ?: return@withContext
                val epgChannel = tvChannel.epgChannel?.target
                val epgSource = epgChannel?.epgsource?.target

                val selectedEpgSources = account.epgsources.filter { it.isSelected }

                if (epgSource?.isPlaylistEpg == true && selectedEpgSources.any { it.isPlaylistEpg }) {
                    val newChannel = if (account.isStalker) {
                        stalkerViewModel.checkChannelsAndShortEpg(tvChannel)
                    } else {
                        xtreamViewModel.checkChannelsAndShortEpg(tvChannel)
                    }

                    val position = tvChannelsAdapter?.currentList?.indexOf(channelPos) ?: -1
                    if (position != -1) {
                        // Vor dem Zugriff sicherstellen, dass das Fragment noch existiert und die Referenzen gültig sind
                        withContext(Dispatchers.Main) {
                            // Prüfen, ob das Fragment noch existiert und der Adapter nicht null ist
                            if (!isAdded || _binding == null || tvChannelsAdapter == null) {
                                return@withContext
                            }

                            // Sicher auf die UI zugreifen
                            if (newChannel.idByAccountData == helpViewModel.currentFocusedChannPosition?.tvchannel?.target?.idByAccountData) {
                                showEpgPreview(newChannel)
                            }
                            tvChannelsAdapter?.notifyItemChanged(position)
                        }
                    }
                }
            }
        }
    }

    fun checkChannelEpg(channelPositions: List<ChannelPositions>) {
        val currentTime = System.currentTimeMillis() / 1000
        currentEpgJob?.cancel()
        currentEpgJob = viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    binding.tvLoadEpg.visibility = View.VISIBLE
                }
                channelPositions.filter { channelPos ->
                    val tvChannel = channelPos.tvchannel.target
                    val currentTimeMillis = (System.currentTimeMillis() / 1000).plus(calculateTimeOffsetInSeconds(
                        tvChannel.epgTimeOffSet ?: channelPos.tvcategory.target.epgTimeOffSet ?: tvChannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0)
                    )
                    val epgChId = tvChannel.linkedEpgChannel?.target?.chEpgId
                    (tvChannel.linkedEpgChannel?.target == null) ||
                            tvChannel.linkedEpgChannel?.target?.isExternalEpg == false
                }.forEach {
                    val tvChannel = it.tvchannel.target
                    Log.d("GETEPGFORCHANNELS", "${tvChannel.showingName}")
                    val newChannel = if (tvChannel.account.target.isStalker) {
                        stalkerViewModel.checkChannelsAndShortEpg(tvChannel)
                    } else if (tvChannel.account.target.isXtream) {
                        xtreamViewModel.checkChannelsAndShortEpg(tvChannel)
                    } else {
                        null
                    }
                    if (newChannel?.linkedEpgChannel?.target == null ||
                        newChannel.linkedEpgChannel?.target?.chEpgId?.let { chEpgId ->
                            EpgDataOB_.epgChId.equal(
                                chEpgId
                            )
                        }?.let { it1 -> epgDataBox.query(it1).build().find() } == null) {
                        val matchedChannel = helpViewModel.matchSingleChannelWithEpgChannels(
                            tvChannel,
                            helpViewModel.currentFocusedTvAccount!!
                        )
                        if (matchedChannel.linkedEpgChannel?.target != null) {
                            val position = tvChannelsAdapter?.currentList?.indexOf(it)
                            if (position != null) {
                                withContext(Dispatchers.Main) {
                                    if (matchedChannel == helpViewModel.currentFocusedChannPosition?.tvchannel?.target) {
                                        showEpgPreview(matchedChannel)
                                    }
                                    tvChannelsAdapter?.notifyItemChanged(position)
                                }
                            }
                        }
                    } else {
                        val position = tvChannelsAdapter?.currentList?.indexOf(it)
                        if (position != null) {
                            withContext(Dispatchers.Main) {
                                if (newChannel == helpViewModel.currentFocusedChannPosition?.tvchannel?.target) {
                                    showEpgPreview(newChannel)
                                }
                                tvChannelsAdapter?.notifyItemChanged(position)
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    binding.tvLoadEpg.visibility = View.GONE
                }
            }
        }
    }

    fun notifyChannelAdapterOrderChannels(channPos: ChannelPositions) {
        binding.containerChannelOptions.visibility = View.GONE
        binding.relLayoutChOrderInfo.visibility = View.VISIBLE
        val stringOk = SpannableStringBuilder()
            .bold {
                append("OK:\n") }
            .append("Save Channel Pos.")
        binding.tvOrderChInfoOK.text = stringOk

        val stringReturn = SpannableStringBuilder()
            .bold {
                append("BACK:\n") }
            .append("Change channel")
        binding.tvOrderChInfoReturn.text = stringReturn
        binding.tvOrderChInfoUpDown.visibility = View.VISIBLE

        val stringUpDown = SpannableStringBuilder()
            .bold {
                append("UP/DOWN:\n") }
            .append("Move Channel")
        binding.tvOrderChInfoUpDown.text = stringUpDown
        tvChannelsAdapter?.setCurrentChanneldId(channPos)
        val position = tvChannelsAdapter?.currentList?.indexOf(channPos)
        if (position != null) {
            tvChannelsAdapter?.notifyItemChanged(position)
        }
        helpViewModel.changeChannelOrder = true
        binding.rvLayoutTvChannels.requestFocus()
    }

    fun closeChOrder() {
        binding.relLayoutChOrderInfo.visibility = View.GONE
        binding.rvLayoutTvChannels.requestFocus()
    }

    fun changeChOrderInfoMoving() {
        binding.tvOrderChInfoUpDown.visibility = View.VISIBLE
        val stringOk = SpannableStringBuilder()
            .bold {
                append("OK:\n") }
            .append("Save Channel Pos.")
        binding.tvOrderChInfoOK.text = stringOk

        val stringReturn = SpannableStringBuilder()
            .bold {
                append("BACK:\n") }
            .append("Change channel")
        binding.tvOrderChInfoReturn.text = stringReturn

        binding.tvOrderChInfoUpDown.visibility = View.VISIBLE
        val stringUpDown = SpannableStringBuilder()
            .bold {
                append("UP/DOWN:\n") }
            .append("Move Channel")
        binding.tvOrderChInfoUpDown.text = stringUpDown
    }

    fun changeChOrderInformation() {
        binding.tvOrderChInfoUpDown.visibility = View.GONE
        val stringOk = SpannableStringBuilder()
            .bold {
                append("OK:\n") }
            .append("Select Channel")
        binding.tvOrderChInfoOK.text = stringOk

        val stringReturn = SpannableStringBuilder()
            .bold {
                append("BACK:\n") }
            .append("Stop channel sorting")
        binding.tvOrderChInfoReturn.text = stringReturn
    }

    //HANDLE RECYCLERVIEW FOCUSES

    fun focusToTvAccountFromChannel() {
        binding.tvActualChNr.visibility = View.INVISIBLE
        val currentCat = tvAccountCategoryAdapter.currentList.firstOrNull { it is AccountTvCategory.TvCategory && it.id == helpViewModel.currentFocusedTvCategory?.id }
        val pos = tvAccountCategoryAdapter.currentList.indexOf(currentCat)
        binding.rvLayoutTvAccountsMenu.setSelectedPosition(pos)
        binding.relLayoutEpg.requestLayout()
        binding.rvLayoutTvAccountsMenu.requestFocus()
    }

    fun setTvAccountsVisibilityAnimated(isVisible: Boolean) {
        val accountsRecyclerView = binding.linLayoutTvAccountsMenu
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.constTv.findViewById<ConstraintLayout>(R.id.const_tv))
        if (isVisible) {
            constraintSet.clear(accountsRecyclerView.id, ConstraintSet.END)
            constraintSet.connect(accountsRecyclerView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            helpViewModel.isTvAccountMenuOpened = true
            helpViewModel.isTvAccountsMenuFocused = true
            binding.rvLayoutTvAccountsMenu.isFocusable = true
            binding.rvLayoutTvAccountsMenu.isFocusableInTouchMode = true
            helpViewModel.isTvCategoryMenuFocused = false
            showMainMenu()
        } else {
            helpViewModel.isTvAccountsMenuFocused = false
            binding.rvLayoutTvAccountsMenu.isFocusable = false
            binding.rvLayoutTvAccountsMenu.isFocusableInTouchMode = false
            constraintSet.clear(accountsRecyclerView.id, ConstraintSet.START)
            constraintSet.connect(accountsRecyclerView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.START)
        }

        val transition = ChangeBounds()
        transition.duration = 250 // Ändere die Dauer nach Bedarf

        TransitionManager.beginDelayedTransition(binding.constTv.findViewById(R.id.const_tv), transition)
        constraintSet.applyTo(binding.constTv.findViewById(R.id.const_tv))
    }


    fun calculateTimeOffsetInSeconds(timeOffset: Int): Long {
        return (timeOffset * 3600).toLong()
    }

    private var hudcurrentNextEpg: EpgDataOB? = null
    private var hudcurrentEpg: EpgDataOB? = null

    private fun changeHudNextEpg(epgDataOB: EpgDataOB?) {
        binding.tvHudNextProgramTime.text = ""
        binding.tvHudNextProgramName.text = ""
        binding.tvHudNextProgramSubtitle.text = ""
        binding.tvHudNextProgramSubtitle.visibility = View.INVISIBLE
        val timeOffSet = helpViewModel.currentHudFocusedChannel?.epgTimeOffSet ?: helpViewModel.currentFocusedChannPosition?.tvcategory?.target?.epgTimeOffSet ?: helpViewModel.currentHudFocusedChannel?.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0

        if (epgDataOB != null) {
            val datum = reformatDate(epgDataOB.datum)
            val currentTime = (System.currentTimeMillis() / 1000).plus(calculateTimeOffsetInSeconds(timeOffSet))
            if (epgDataOB.startTimestamp!! >= currentTime) {
                binding.tvHudPrograminfo.text = "$datum"
            } else {
                if (epgDataOB.stopTimestamp!! <= currentTime) {
                    binding.tvHudPrograminfo.text = "$datum"
                }
            }
            hudcurrentNextEpg = epgDataOB
            binding.tvHudNextProgramName.text = epgDataOB.name
            if (epgDataOB.sub_title.isNotEmpty()) {
                binding.tvHudNextProgramSubtitle.visibility = View.VISIBLE
                binding.tvHudNextProgramSubtitle.text = epgDataOB.sub_title
            } else {
                binding.tvHudNextProgramSubtitle.visibility = View.INVISIBLE
                binding.tvHudNextProgramSubtitle.text = ""
            }
            val startTime = formatUnixTimestampToTime(epgDataOB.startTimestamp!!, timeOffSet)

            val endTime = formatUnixTimestampToTime(epgDataOB.stopTimestamp!!, timeOffSet)

            binding.tvHudNextProgramTime.text =
                "${startTime} - ${endTime}"
        } else {
            binding.tvHudNextProgramTime.text = ""
            binding.tvHudNextProgramName.text = "No Information"
            binding.tvHudNextProgramSubtitle.text = ""
            if (hudcurrentNextEpg != null) {
                val startTime = formatUnixTimestampToTime(hudcurrentNextEpg?.startTimestamp ?: 0, timeOffSet)

                binding.tvHudNextProgramTime.text = "$startTime - "
            } else {
                binding.tvHudNextProgramTime.text = "00:00 - 00:00"
            }
            binding.tvHudNextProgramSubtitle.visibility = View.INVISIBLE
            binding.tvHudPrograminfo.text = ""
        }
        binding.hudLayout.requestFocus()
    }

    fun reformatDate(inputDate: String): String {
        // Eingabeformat definieren (yyyy-MM-dd)
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        // Ausgabeformat definieren (dd/MM/yyyy)
        val outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        // Eingabe-Datum parsen
        val date = LocalDate.parse(inputDate, inputFormatter)

        // Datum ins gewünschte Format umwandeln
        return date.format(outputFormatter)
    }

    private fun resetFullScreenChannel() {
        hudcurrentNextEpg = null
        hudcurrentEpg = null
        helpViewModel.currentHudFocusedChannel = null
        helpViewModel.currentHudFocusedChannelPosition = null
        binding.tvHudCurrentProgramTime.text = ""
        binding.tvHudChannelname.text = ""
        binding.tvHudCurrenProgramName.text = ""
        binding.tvHudCurrenProgramSubtitle.text = ""
        binding.tvHudNextProgramTime.text = ""
        binding.tvHudNextProgramName.text = ""
        binding.tvHudNextProgramSubtitle.text = ""
        binding.tvHudNextProgramSubtitle.visibility = View.INVISIBLE
        binding.tvHudCurrenProgramSubtitle.visibility = View.INVISIBLE
    }


    private fun updateFullScreenChannel(channelPos: ChannelPositions, firstEpgDataOB: EpgDataOB?, nextEpgDataOB: EpgDataOB?) {
        hudcurrentNextEpg = null
        hudcurrentEpg = null
        helpViewModel.currentHudFocusedChannel = channelPos.tvchannel.target
        helpViewModel.currentHudFocusedChannelPosition = channelPos
        binding.tvHudCurrentProgramTime.text = ""
        binding.tvHudChannelname.text = ""
        binding.tvHudCurrenProgramName.text = ""
        binding.tvHudCurrenProgramSubtitle.text = ""
        binding.tvHudNextProgramTime.text = ""
        binding.tvHudNextProgramName.text = ""
        binding.tvHudNextProgramSubtitle.text = ""
        binding.tvHudNextProgramSubtitle.visibility = View.INVISIBLE
        binding.tvHudCurrenProgramSubtitle.visibility = View.INVISIBLE

        val tvchannel = channelPos.tvchannel.target

        if (tvchannel.idByAccountData == helpViewModel.currentPlayingChannel?.idByAccountData) {
            binding.channelQualityInfoFullscreen.visibility = View.VISIBLE
        } else {
            binding.channelQualityInfoFullscreen.visibility = View.GONE
        }

        val timeOffSet = tvchannel.epgTimeOffSet ?: channelPos.tvcategory.target?.epgTimeOffSet ?: tvchannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0

        val currentAccount = helpViewModel.currentPlayingTvAccount
        binding.tvHudChannelname.text = tvchannel.showingName
        binding.tvHudChannelname.isSelected = true
        val image = tvchannel.logo

        if (currentAccount!!.useEpgLogos) {
            val epgLogo = tvchannel.linkedEpgChannel?.target?.icon?.firstOrNull()
            if (!epgLogo.isNullOrEmpty()) {
                binding.hudChannelLogo.load(epgLogo)
            } else {
                if (image.isNotEmpty()) {
                    binding.hudChannelLogo.load(image)
                } else {
                    binding.hudChannelLogo.visibility = View.INVISIBLE
                }
            }
        } else {
            if (image.isNotEmpty()) {
                binding.hudChannelLogo.load(image)
            } else {
                binding.hudChannelLogo.visibility = View.INVISIBLE
            }
        }
        if (!helpViewModel.isPlayingCatchup) {
            binding.relLayoutSeekbar.visibility = View.GONE
            if (channelPos.catAndChannelAccount == tvChannelsAdapter?.currentList?.first()?.catAndChannelAccount) {
                binding.fullscreenChannelprevious.visibility = View.INVISIBLE
            } else {
                binding.fullscreenChannelprevious.visibility = View.VISIBLE
            }

            if (channelPos.catAndChannelAccount == tvChannelsAdapter?.currentList?.last()?.catAndChannelAccount) {
                binding.fullscreenChannelnext.visibility = View.INVISIBLE
            } else {
                binding.fullscreenChannelnext.visibility = View.VISIBLE
            }

            val currentTime = System.currentTimeMillis()
            val currentTimeString =
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime)
            val halfHourLaterTime = currentTime + (30 * 60 * 1000)
            val halfHourLaterTimeString =
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(halfHourLaterTime)

            if (firstEpgDataOB != null) {
                hudcurrentEpg = firstEpgDataOB
                binding.tvHudCurrenProgramName.text = firstEpgDataOB.name
                if (firstEpgDataOB.sub_title.isEmpty()) {
                    binding.tvHudCurrenProgramSubtitle.visibility = View.INVISIBLE
                } else {
                    binding.tvHudCurrenProgramSubtitle.visibility = View.VISIBLE
                    binding.tvHudCurrenProgramSubtitle.text = firstEpgDataOB.sub_title
                }
                val startTime = formatUnixTimestampToTime(firstEpgDataOB.startTimestamp ?: 0, timeOffSet)
                val endTime = formatUnixTimestampToTime(firstEpgDataOB.stopTimestamp ?: 0, timeOffSet)
                binding.tvHudCurrentProgramTime.text =
                    "${startTime} - ${endTime}"
                val duration =
                    ((firstEpgDataOB.stopTimestamp!! + calculateTimeOffsetInSeconds(
                        timeOffSet
                    )).minus(firstEpgDataOB.startTimestamp!! + calculateTimeOffsetInSeconds(timeOffSet)))
                binding.hudprogressBar.max = 100
                val progress =
                    ((System.currentTimeMillis() / 1000 - (firstEpgDataOB.startTimestamp!!+ calculateTimeOffsetInSeconds(
                        timeOffSet
                    ))) * 100 / duration).toInt()
                binding.hudprogressBar.progress = progress
                val timeOffSetSeconds = calculateTimeOffsetInSeconds(timeOffSet)
                val remainingTimeInSeconds = (firstEpgDataOB.stopTimestamp!! + timeOffSetSeconds) - (System.currentTimeMillis() / 1000)
// Formatiere die verbleibende Zeit
                val remainingTimeText = if (remainingTimeInSeconds > 3600) {
                    String.format(
                        "%dh %dmin",
                        remainingTimeInSeconds / 3600,
                        (remainingTimeInSeconds % 3600) / 60
                    )
                } else {
                    String.format("%dmin", remainingTimeInSeconds / 60)
                }

                // Setze den Text im TextView
                binding.tvRemainingTimeCurrentProgram.text = "$remainingTimeText remaining.."
                binding.tvRemainingTimeCurrentProgram.visibility = View.VISIBLE
            } else {
                binding.hudprogressBar.progress = 0
                binding.tvHudCurrentProgramTime.text =
                    "$currentTimeString - $halfHourLaterTimeString"
                binding.tvHudCurrenProgramName.text =
                    resources.getString(R.string.no_information)
                binding.tvHudCurrenProgramSubtitle.visibility = View.INVISIBLE
                binding.tvRemainingTimeCurrentProgram.visibility = View.INVISIBLE
            }
            if (nextEpgDataOB != null) {
                binding.tvHudPrograminfo.visibility = View.VISIBLE
                val datum =  reformatDate(nextEpgDataOB.datum)
                binding.tvHudPrograminfo.text = "$datum"
                binding.tvHudNextProgramTime.visibility = View.VISIBLE
                binding.tvHudNextProgramSubtitle.visibility = View.VISIBLE
                binding.fullscreenChannelpreviousepg.visibility = View.VISIBLE
                binding.fullscreenChannelnextepg.visibility = View.VISIBLE
                hudcurrentNextEpg = nextEpgDataOB
                val nextStartTime = formatUnixTimestampToTime(nextEpgDataOB.startTimestamp ?: 0, timeOffSet)
                val nextEndTime = formatUnixTimestampToTime(nextEpgDataOB.stopTimestamp ?: 0, timeOffSet)
                binding.tvHudNextProgramName.text = nextEpgDataOB.name
                binding.tvHudNextProgramName.visibility = View.VISIBLE
                binding.tvHudNextProgramTime.text =
                    "${nextStartTime} - ${nextEndTime}"
                if (nextEpgDataOB.sub_title.isNotEmpty()) {
                    binding.tvHudNextProgramSubtitle.visibility = View.VISIBLE
                    binding.tvHudNextProgramSubtitle.text = nextEpgDataOB.sub_title
                } else {
                    binding.tvHudNextProgramSubtitle.visibility = View.INVISIBLE
                }
            } else {
                binding.tvHudNextProgramTime.visibility = View.GONE
                binding.tvHudNextProgramSubtitle.visibility = View.INVISIBLE
                binding.fullscreenChannelpreviousepg.visibility = View.GONE
                binding.fullscreenChannelnextepg.visibility = View.GONE
                binding.tvHudPrograminfo.text = "No upcoming program info"
                binding.tvHudNextProgramName.text =
                    resources.getString(R.string.no_information)
            }
        } else {
            binding.tvHudPrograminfo.visibility = View.INVISIBLE
            binding.relLayoutSeekbar.visibility = View.VISIBLE
            val startTimestamp = helpViewModel.catchupEpgData?.startTimestamp ?: 0
            val stopTimestamp = helpViewModel.catchupEpgData?.stopTimestamp ?: 0
            val datum = helpViewModel.catchupEpgData?.datum?.let {
                reformatDate(it)
            }
            val catchupStartTime = formatUnixTimestampToTime(startTimestamp, timeOffSet)
            val catchupEndTime = formatUnixTimestampToTime(stopTimestamp, timeOffSet)
            binding.tvHudCurrenProgramName.text = helpViewModel.catchupEpgData?.name ?: "No Information"
            if (helpViewModel.catchupEpgData?.sub_title != null) {
                binding.tvHudCurrenProgramSubtitle.visibility = View.VISIBLE
                binding.tvHudCurrenProgramSubtitle.text = helpViewModel.catchupEpgData?.sub_title
            } else {
                binding.tvHudCurrenProgramSubtitle.visibility = View.INVISIBLE
            }
            if (startTimestamp in 1..<stopTimestamp) {
                val duration = stopTimestamp - startTimestamp
                val progress = if (duration > 0) (playbackPosition * 100) / duration else 0
                binding.tvHudCurrentProgramTime.text = "$catchupStartTime - $catchupEndTime  /  $datum"
                binding.hudprogressBar.progress = progress.toInt()
                val remainingTimeInSeconds = maxOf(0, duration - playbackPosition)

// Formatiere die verbleibende Zeit
                val remainingTimeText = if (remainingTimeInSeconds > 3600) {
                    String.format(
                        "%dh %dmin",
                        remainingTimeInSeconds / 3600,
                        (remainingTimeInSeconds % 3600) / 60
                    )
                } else {
                    String.format("%dmin", remainingTimeInSeconds / 60)
                }

                // Setze den Text im TextView
                binding.tvRemainingTimeCurrentProgram.text = "$remainingTimeText remaining.."
                binding.tvRemainingTimeCurrentProgram.visibility = View.VISIBLE
            } else {
                binding.tvHudCurrentProgramTime.text = "00:00 - 00:00"
                binding.hudprogressBar.progress = 0
            }

            binding.tvHudNextProgramSubtitle.visibility = View.INVISIBLE
            binding.fullscreenChannelpreviousepg.visibility = View.INVISIBLE
            binding.fullscreenChannelnext.visibility = View.INVISIBLE
            binding.fullscreenChannelnextepg.visibility = View.INVISIBLE
            binding.fullscreenChannelprevious.visibility = View.INVISIBLE
            binding.tvHudNextProgramName.visibility = View.INVISIBLE
            binding.tvHudNextProgramTime.visibility = View.INVISIBLE
            binding.seekBar.requestFocus()
        }
        if (tvchannel.idByAccountData == helpViewModel.currentPlayingChannel?.idByAccountData) {
            if (binding.tvFullscreenChannelQuality.text.toString().isNotEmpty()) {
                binding.tvFullscreenChannelQuality.visibility = View.VISIBLE
            } else {
                binding.tvFullscreenChannelQuality.visibility = View.GONE
            }
            if (binding.tvFullscreenFps.text.toString().isNotEmpty()) {
                binding.tvFullscreenFps.visibility = View.VISIBLE
            } else {
                binding.tvFullscreenFps.visibility = View.GONE
            }
            if (binding.tvFullscreenAudio.text.toString().isNotEmpty()) {
                binding.tvFullscreenAudio.visibility = View.VISIBLE
            } else {
                binding.tvFullscreenAudio.visibility = View.GONE
            }
        } else {
            binding.tvFullscreenAudio.visibility = View.INVISIBLE
            binding.tvFullscreenFps.visibility = View.INVISIBLE
            binding.tvFullscreenChannelQuality.visibility = View.INVISIBLE
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

    fun getAudioFormatDescription(channels: Int?): String {
        return when {
            channels == 1 -> "Mono"
            channels == 2 -> "Stereo"
            channels == 6 -> "5.1"
            channels == 8 -> "7.1"
            else -> "N/A"
        }
    }

    private fun showHudContainer() {
        binding.hudLayout.visibility = View.VISIBLE
        // Entferne vorherige geplante Ausführungen des Runnables, falls vorhanden
        handler.removeCallbacks(hideHudRunnable)
        isHudContainerOpened = true
        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.currentPlayingChannelPosition?.let { channPos ->
                val tvchannel = channPos.tvchannel.target
                val timeOffSet = helpViewModel.currentFocusedChannel?.epgTimeOffSet
                    ?: channPos.tvcategory.target?.epgTimeOffSet
                    ?: tvchannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0
                val currentTimeMillis =
                    (System.currentTimeMillis() / 1000).plus(calculateTimeOffsetInSeconds(timeOffSet))
                val epgChId = tvchannel.linkedEpgChannel?.target?.chEpgId
                if (epgChId != null) {
                    val currentAndNextEpg =
                        withContext(Dispatchers.IO) {
                            epgDataBox.query(
                                EpgDataOB_.epgChId.equal(epgChId)
                                    .and(EpgDataOB_.stopTimestamp.greater(currentTimeMillis))
                            )
                                .order(EpgDataOB_.startTimestamp)
                                .build().find(0, 2)
                        }
                    val currentProgram =
                        currentAndNextEpg.firstOrNull()

                    val nextEpgData = if (currentAndNextEpg.size > 1) {
                        currentAndNextEpg[1]
                    } else {
                        null
                    }
                    updateFullScreenChannel(channPos, currentProgram, nextEpgData)
                } else {
                    updateFullScreenChannel(channPos, null, null)
                }
            }
            if (helpViewModel.isPlayingCatchup) {
                binding.seekBar.requestFocus()
            } else {
                binding.hudLayout.requestFocus()
            }
            // Erstelle einen Handler, um das HUD nach 5 Sekunden auszublenden
            handler.postDelayed(hideHudRunnable, 8000) // 5000 Millisekunden = 5 Sekunden
        }
    }

    private fun hideHudContainer() {
        // Entferne das Runnable, falls es noch nicht ausgeführt wurde
        handler.removeCallbacks(hideHudRunnable)
        resetFullScreenChannel()
        isHudContainerOpened = false
        binding.hudLayout.visibility = View.GONE
        hudcurrentEpg = null
        hudcurrentNextEpg = null
        helpViewModel.currentHudFocusedChannel = null
        helpViewModel.currentHudFocusedChannelPosition = null
        binding.videoView.requestFocus()
    }

    fun setVideoViewFullScreenWithoutFocus() {
        binding.tvTvchannelname.visibility = View.INVISIBLE
        binding.relLayoutEpg.visibility = View.INVISIBLE
        binding.rvPreviewFullEpg.visibility = View.INVISIBLE
        binding.relLayoutChannelInfo.visibility = View.INVISIBLE
        // Wenn im Vollbildmodus, setze die Größe zurück

        helpViewModel.isTvFullScreen = true
        helpViewModel.fullScreenFocusedChannel = null
        val layoutParams = binding.videoViewPreview.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.startToEnd = ConstraintLayout.LayoutParams.UNSET
        layoutParams.topToTop = ConstraintLayout.LayoutParams.UNSET
        layoutParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
        layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        binding.videoViewPreview.layoutParams = layoutParams
        binding.videoViewPreview.requestLayout()
    }


    fun setVideoViewFullScreen() {
        // Alle anderen Views unsichtbar machen
        binding.tvTvchannelname.visibility = View.INVISIBLE
        binding.relLayoutEpg.visibility = View.INVISIBLE
        binding.rvPreviewFullEpg.visibility = View.INVISIBLE
        binding.relLayoutChannelInfo.visibility = View.INVISIBLE

        // Status auf Vollbildmodus setzen
        helpViewModel.isTvFullScreen = true

        // ConstraintLayout-Parameter entfernen (um volle Freiheit zu geben)
        val layoutParams = binding.videoViewPreview.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.startToEnd = ConstraintLayout.LayoutParams.UNSET
        layoutParams.topToTop = ConstraintLayout.LayoutParams.UNSET
        layoutParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
        layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET

        // Neue Layout-Parameter setzen und View aktualisieren
        binding.videoViewPreview.layoutParams = layoutParams
        binding.videoViewPreview.requestLayout()

        // Fokus und Sichtbarkeit des Players
        binding.videoView.visibility = View.VISIBLE
        binding.videoView.isFocusable = true
        binding.videoView.isFocusableInTouchMode = true
        binding.videoView.requestFocus()
        if (isFirstPlayingChannel) {
            showHudContainer()
            isFirstPlayingChannel = false
        }
    }

    fun setVideoViewNotFullScreen() {
        binding.fullScreenProgressBar.visibility = View.INVISIBLE
        binding.fullScreenPlayingError.visibility = View.INVISIBLE
        // Andere Views wieder sichtbar machen
        binding.tvTvchannelname.visibility = View.VISIBLE
        binding.relLayoutEpg.visibility = View.VISIBLE
        binding.relLayoutChannelInfo.visibility = View.VISIBLE
        binding.tvAudio.visibility = View.VISIBLE
        // Status auf Nicht-Vollbildmodus setzen
        helpViewModel.isTvFullScreen = false

        tvChannelsAdapter?.isLongPressBackOnce = true
        tvAccountCategoryAdapter.isLongPressBackOnce = true

        // Ursprüngliche Constraint-Parameter wiederherstellen
        val layoutParams = binding.videoViewPreview.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.width = 0 // "match_constraint" entspricht 0dp in XML
        layoutParams.height = 0
        layoutParams.startToEnd = R.id.relLayout_tvchannels
        layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        layoutParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
        layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        layoutParams.matchConstraintPercentWidth = 0.48f // Ursprüngliche Breite
        layoutParams.matchConstraintPercentHeight = 0.48f // Ursprüngliche Höhe
        // Neue Layout-Parameter setzen und View aktualisieren
        binding.videoViewPreview.layoutParams = layoutParams
        binding.videoViewPreview.requestLayout()

        // Fokus und Sichtbarkeit des Players
        binding.videoView.visibility = View.VISIBLE
        binding.videoView.isFocusable = false
        binding.videoView.isFocusableInTouchMode = false
        if (!helpViewModel.isFullScreenFullEpg) {
            if (!helpViewModel.isPlayingCatchup) {
                binding.relLayoutEpg.visibility = View.VISIBLE
                binding.relLayoutChannelInfo.visibility = View.VISIBLE

                helpViewModel.currentPlayingChannelPosition?.let { currentChannel ->
                    val clickedPosition =
                        tvChannelsAdapter?.currentList?.indexOf(currentChannel) ?: -1
                    if (clickedPosition >= 0) {
                        binding.rvLayoutTvChannels.setSelectedPosition(clickedPosition)
                        binding.rvLayoutTvChannels.post {
                            binding.rvLayoutTvChannels.requestFocus()
                        }
                    }
                }
            } else {
                stopPeriodicExoPlayerUpdate()
                helpViewModel.currentPlayingChannelPosition?.let { currentChannel ->
                    val clickedPosition =
                        tvChannelsAdapter?.currentList?.indexOf(currentChannel) ?: -1
                    if (clickedPosition >= 0) {
                        tvChannelsAdapter?.notifyItemChanged(clickedPosition)
                        binding.rvLayoutTvChannels.setSelectedPosition(clickedPosition)
                    }
                }
                visibleFullEpgAndDetailEpgContainer()
            }
        }
    }

    fun changingPlayingChannel(channelPosition: ChannelPositions) {
        val channel = channelPosition.tvchannel.target
        player?.stop()
        changedChannelSourceCheck(channelPosition)
        binding.tvErrorChannelPlay.visibility = View.GONE
        binding.tvErrorChannelPlay.text = ""
        focusedChannelFirstEpgIdByAccountdata = ""
        focusedChannelLastEpgIdByAccountData = ""
        binding.tvFullscreenAudio.visibility = View.INVISIBLE
        binding.tvAudio.visibility = View.INVISIBLE
        retryCount = 0
        tokenRefreshAttempted = false
        updateChannelRecyclerview(channelPosition)
        resetChannelQualityInfo(channel)
    }

    private val onChannelClickListener = TvChannelsAdapter.OnClickListener { channelPosition, position ->
        val channel = channelPosition.tvchannel.target
        if (helpViewModel.isPlayingCatchup) {
            helpViewModel.lastPlayingCatchupEpgId = ""
            helpViewModel.isPlayingCatchup = false
        }
        if (channel.idByAccountData == helpViewModel.currentPlayingChannel?.idByAccountData) {
            getFirstAndLastEpgForChannel()
            isFirstPlayingChannel = true
            setVideoViewFullScreen()
        } else {
            changingPlayingChannel(channelPosition)
        }
    }

    fun getFirstAndLastEpgForChannel() {
        val epgChannelId = helpViewModel.currentPlayingChannel?.linkedEpgChannel?.target?.chEpgId
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            focusedChannelFirstEpgIdByAccountdata =
                epgChannelId?.let {
                    epgDataBox.query(EpgDataOB_.epgChId.equal(it))
                        .order(EpgDataOB_.startTimestamp)
                        .build()
                        .findFirst()?.idByAccountData ?: ""
                }.toString()

            focusedChannelLastEpgIdByAccountData =
                epgChannelId?.let {
                    epgDataBox.query(EpgDataOB_.epgChId.equal(it))
                        .orderDesc(EpgDataOB_.startTimestamp)
                        .build()
                        .findFirst()?.idByAccountData ?: ""
                }.toString()
        }
    }

    fun setCurrentFocusedChannel(channPos: ChannelPositions) {
        val chPos = (tvChannelsAdapter?.currentList?.indexOf(channPos)?.plus(1)) ?: -1
        helpViewModel.currentFocusedChannPosition = channPos
        helpViewModel.currentFocusedChannel = channPos.tvchannel.target
        if (chPos != -1) {
            binding.tvActualChNr.visibility = View.VISIBLE
            binding.tvActualChNr.text = "$chPos / "
        } else {
            binding.tvActualChNr.visibility = View.INVISIBLE
        }
    }

    fun updateChannelRecyclerview(channelPos: ChannelPositions) {
        val findLastPlayingChannel = if (helpViewModel.currentPlayingChannelPosition != null) {
            tvChannelsAdapter?.currentList?.firstOrNull { it.catAndChannelAccount == helpViewModel.currentPlayingChannelPosition!!.catAndChannelAccount }
        } else {
            null
        }
        val findLastPlayingChannelPosition =
            tvChannelsAdapter?.currentList?.indexOf(findLastPlayingChannel)
        helpViewModel.currentPlayingChannelPosition = channelPos
        helpViewModel.currentPlayingChannel = channelPos.tvchannel.target
        helpViewModel.currentPlayingTvCategory = helpViewModel.currentFocusedTvCategory
        helpViewModel.currentPlayingTvAccount = helpViewModel.currentFocusedTvAccount
        if (findLastPlayingChannelPosition != null) {
            tvChannelsAdapter!!.notifyItemChanged(findLastPlayingChannelPosition)
        }
        val positionClickedChannel = tvChannelsAdapter?.currentList!!.indexOf(channelPos)

        tvChannelsAdapter!!.notifyItemChanged(positionClickedChannel)
    }

    fun resetChannelQualityInfo(channel: TvChannelOB) {
        if (!helpViewModel.isTvFullScreen) {
            binding.tvTvchannelname.visibility = View.VISIBLE
        }
        binding.tvTvchannelname.text = channel.showingName
        binding.tvAudio.visibility = View.INVISIBLE
        binding.tvFps.visibility = View.INVISIBLE
        binding.tvChannelQuality.visibility = View.INVISIBLE
        binding.tvFullscreenAudio.visibility = View.INVISIBLE
        binding.tvFullscreenFps.visibility = View.INVISIBLE
        binding.tvFullscreenChannelQuality.visibility = View.INVISIBLE
        binding.videoView.visibility = View.VISIBLE
        binding.relLayoutChannelInfo.visibility = View.VISIBLE
        binding.playingError.visibility = View.INVISIBLE
    }

    fun cancelChannelLoadJob() {
        channelLoadJob?.cancel()
    }

    private var channelLoadJob: Job? = null

    fun changedChannelSourceCheck(channelPos: ChannelPositions) {
        showProgressBar()
        channelLoadJob?.cancel()
        channelLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val channel = channelPos.tvchannel.target
            val channelAccount = channel.account.target
            if (channelAccount.isStalker) {
                getChannelLinkData(channel.cmd, channel.playlistId!!, channelPos)
            } else if (channelAccount.isXtream) {
                if (channelAccount.xtreamUseDefaultType) {
                    val url = "${channelAccount.stalkerUrl}/live/${channelAccount.username}/${channelAccount.macAddress}/${channel.channelId}.ts"
                    switchChannel(url)
                } else {
                    val url = "${channelAccount.stalkerUrl}/live/${channelAccount.username}/${channelAccount.macAddress}/${channel.channelId}.${channelAccount.xtreamOtherStreamType}"

                    switchChannel(url)
                }
            }
        }
    }

    fun resetPlayingChannel(channelPos: ChannelPositions) {
        if (helpViewModel.currentPlayingChannelPosition != null) {
            val findLastPlayingChannel = tvChannelsAdapter?.currentList?.firstOrNull { it.catAndChannelAccount == helpViewModel.currentPlayingChannelPosition!!.catAndChannelAccount }
            if (findLastPlayingChannel != null) {
                val findLastPlayingChannelPosition =
                    tvChannelsAdapter?.currentList?.indexOf(findLastPlayingChannel)
                if (findLastPlayingChannelPosition != null) {
                    binding.rvLayoutTvChannels.findViewHolderForAdapterPosition(
                        findLastPlayingChannelPosition
                    )?.itemView?.isActivated = false
                    tvChannelsAdapter!!.notifyItemChanged(findLastPlayingChannelPosition)
                }
            }
        }

        val positionClickedChannel = tvChannelsAdapter?.currentList!!.indexOf(channelPos)
        binding.rvLayoutTvChannels.findViewHolderForAdapterPosition(positionClickedChannel)?.itemView?.isActivated =
            true
        tvChannelsAdapter!!.notifyItemChanged(positionClickedChannel)
    }

    fun showPlayerForCatchupd(catchupUrl: String, channelPos: ChannelPositions) {
        val channel = channelPos.tvchannel.target
        binding.tvAudio.visibility = View.INVISIBLE
        binding.tvFps.visibility = View.INVISIBLE
        binding.tvChannelQuality.visibility = View.INVISIBLE
        binding.tvTvchannelname.visibility = View.VISIBLE
        binding.tvTvchannelname.text = channel.showingName
        binding.videoView.visibility = View.VISIBLE
        binding.relLayoutChannelInfo.visibility = View.VISIBLE
        binding.playingError.visibility = View.INVISIBLE
        showProgressBar()
        switchChannel(catchupUrl)
    }

    private val onChannelLongClickListener = TvChannelsAdapter.OnLongClickListener { channel, position ->
        showChannelOptionsContainer()
    }

    fun showMainMenu() {
        (requireActivity() as? MainActivity)?.showMenu()
    }

    fun hideMainMenu() {
        (requireActivity() as? MainActivity)?.hideMenu()
    }

    fun openMainMenu() {
        helpViewModel.isTvAccountsMenuFocused = false
        helpViewModel.isTvCategoryMenuFocused = false
        helpViewModel.isTvChannelsMenuFocused = false
        helpViewModel.isTvAccountMenuOpened = false
        (requireActivity() as? MainActivity)?.openMenu()
        (requireActivity() as? MainActivity)?.toggleVisibilityOfMainContainer(false)
        (requireActivity() as? MainActivity)?.lastSelectFocus()
    }

    fun hideFullScreenChannelEpg() {
        binding.containerFullscreenEpgInfo.visibility = View.INVISIBLE
    }

    fun showFullScreenChannelEpg() {
        binding.containerFullscreenEpgInfo.visibility = View.VISIBLE
    }

    var isFirstOpenDetailEpgFullScreen = true

    fun showFullScreenChannelSelectorEpg() {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.add(R.id.container_fullscreen_epgInfo, FullScreenChannelSelectorEpg())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.containerFullscreenEpgInfo.visibility = View.VISIBLE
        val slideIn = AnimationUtils.loadAnimation(this@TvChannelsFragment.requireActivity(), R.anim.slide_in_right)
        binding.containerFullscreenEpgInfo.startAnimation(slideIn)
    }

    fun closeFullScreenChannelSelectorEpg() {
        val fullscreenepgInfoFragment = parentFragmentManager.findFragmentById(R.id.container_fullscreen_epgInfo)
        if (fullscreenepgInfoFragment is FullScreenChannelSelectorEpg) {
            val slideOut = AnimationUtils.loadAnimation(this@TvChannelsFragment.requireActivity(), R.anim.slide_out_to_right)
            binding.containerFullscreenEpgInfo.visibility = View.GONE
            binding.containerFullscreenEpgInfo.startAnimation(slideOut)
            fullscreenepgInfoFragment.closeFragment()
        }
    }

    fun showEpgPreview(tvChannel: TvChannelOB) {
        if (binding.relLayoutEpg.isInvisible) {
            binding.relLayoutEpg.visibility = View.VISIBLE
        }
        tvChannel.account.target.epgsources.reset()
        resetEpgPreview()
        val timeOffSet = tvChannel.epgTimeOffSet ?: helpViewModel.currentFocusedChannPosition?.tvcategory?.target?.epgTimeOffSet ?: tvChannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0
        val currentTimeMillis = (System.currentTimeMillis() / 1000).plus(calculateTimeOffsetInSeconds(timeOffSet))

        binding.tvEpgTvchannelname.text = tvChannel.showingName
        val epgChId = tvChannel.linkedEpgChannel?.target?.chEpgId ?: if (tvChannel.account.target.usePlaylistEpg) {
            tvChannel.epgChannel?.target?.chEpgId
        } else {
            null
        }
        if (epgChId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val heuteListe = withContext(Dispatchers.IO) {
                    epgDataBox.query(
                        EpgDataOB_.epgChId.equal(epgChId)
                            .and(EpgDataOB_.stopTimestamp.greater(currentTimeMillis))
                    )
                        .order(EpgDataOB_.startTimestamp)
                        .build().find(0, 4)
                }
                val currentProgram =
                    heuteListe.firstOrNull()
                val nextEpgData =
                    if (heuteListe.size >= 2) {
                        heuteListe[1]
                    } else {
                        null
                    }
                if (currentProgram != null) {
                    helpViewModel.currentSelectedEpgForSelectedChannel = currentProgram
                    binding.epgpreviewDivider.visibility = View.VISIBLE
                    binding.tvNoepgavailable.visibility = View.GONE
                    binding.relLayoutCurrentProgram.visibility = View.VISIBLE
                    binding.tvShowFullEpg.visibility = View.VISIBLE
                    binding.tvDescription.visibility = View.VISIBLE
                    binding.tvShowFullEpg.isFocusable = true
                    binding.tvShowFullEpg.isFocusableInTouchMode = true
                    binding.tvCurrentStartTime.visibility = View.VISIBLE
                    binding.tvCurrentEndTime.visibility = View.VISIBLE
                    binding.tvCurrentProgram.visibility = View.VISIBLE
                    binding.tvDescription.visibility = View.VISIBLE
                    // Verarbeite currentEpgData und nextEpgData nach Bedarf und binde sie an die UI
                    // Zum Beispiel: tvProgramInfo.text = "${currentEpgData.name} - ${currentEpgData.descr}"
                    binding.tvCurrentStartTime.text =
                        formatUnixTimestampToTime(currentProgram.startTimestamp ?: 0, timeOffSet)
                    val endTime =
                        formatUnixTimestampToTime(currentProgram.stopTimestamp ?: 0, timeOffSet)
                    binding.tvCurrentEndTime.text = " - ${endTime}"
                    binding.tvCurrentProgram.text = currentProgram.name
                    binding.tvCurrentProgram.isSelected = true
                    if (currentProgram.sub_title.isEmpty()) {
                        binding.tvCurrentSubtitle.visibility = View.GONE
                    } else {
                        binding.tvCurrentSubtitle.visibility = View.VISIBLE
                        binding.tvCurrentSubtitle.text = currentProgram.sub_title
                    }
                    if (currentProgram.descr.isEmpty()) {
                        binding.tvDescription.text = resources.getString(R.string.no_description)
                    } else {
                        binding.tvDescription.text = currentProgram.descr
                    }

                    if (nextEpgData != null) {
                        binding.tvNextStartTime.visibility = View.VISIBLE
                        binding.tvNextEndTime.visibility = View.VISIBLE
                        binding.tvNextProgram.visibility = View.VISIBLE
                        binding.tvNextStartTime.text =
                            formatUnixTimestampToTime(nextEpgData.startTimestamp ?: 0, timeOffSet)

                        val nextEndTime =
                            formatUnixTimestampToTime(nextEpgData.stopTimestamp ?: 0, timeOffSet)
                        binding.tvNextEndTime.text = " - ${nextEndTime}"
                        binding.tvNextProgram.text = if (nextEpgData.name.isNullOrEmpty()) {
                            resources.getString(R.string.no_information)
                        } else {
                            nextEpgData.name
                        }
                        binding.tvNextsubtitle.visibility =
                            if (nextEpgData.sub_title.isNullOrEmpty()) {
                                View.GONE
                            } else {
                                binding.tvNextsubtitle.text = nextEpgData.sub_title
                                View.VISIBLE
                            }
                        val overnextProgam = if (heuteListe.size >= 3) {
                            heuteListe[2]
                        } else {
                            null
                        }
                        if (overnextProgam != null) {
                            binding.tvOvernextStartTime.visibility = View.VISIBLE
                            binding.tvOvernextEndTime.visibility = View.VISIBLE
                            binding.tvOvernextProgram.visibility = View.VISIBLE
                            binding.tvOvernextStartTime.text = formatUnixTimestampToTime(
                                overnextProgam.startTimestamp ?: 0,
                                timeOffSet
                            )
                            val overnextEndTime = formatUnixTimestampToTime(
                                overnextProgam.stopTimestamp ?: 0,
                                timeOffSet
                            )
                            binding.tvOvernextEndTime.text = " - ${overnextEndTime}"
                            binding.tvOvernextProgram.text =
                                if (overnextProgam.name.isEmpty()) {
                                    resources.getString(R.string.no_information)
                                } else {
                                    overnextProgam.name
                                }
                            binding.tvOvernextsubtitle.visibility =
                                if (overnextProgam.sub_title.isEmpty()) {
                                    View.GONE
                                } else {
                                    binding.tvOvernextsubtitle.text = overnextProgam.sub_title
                                    View.VISIBLE
                                }

                        } else {
                            binding.tvOvernextStartTime.text = ""
                            binding.tvOvernextEndTime.text = ""
                            binding.tvOvernextProgram.text = ""
                            binding.tvOvernextsubtitle.text = ""
                        }
                    } else {
                        binding.tvNextStartTime.text = ""
                        binding.tvNextEndTime.text = ""
                    }
                } else {
                }
            }
        } else {
            binding.tvCurrentProgram.isSelected = false
            binding.tvCurrentSubtitle.isSelected = false
            binding.epgpreviewDivider.visibility = View.GONE
            binding.tvCurrentStartTime.text = ""
            binding.tvCurrentEndTime.text = ""
            binding.tvCurrentSubtitle.visibility = View.GONE
            binding.tvCurrentProgram.text = resources.getString(R.string.no_information)
            binding.tvDescription.text = resources.getString(R.string.no_description)
            binding.tvDescription.visibility = View.INVISIBLE
            binding.tvOvernextsubtitle.visibility = View.GONE
            binding.tvNextsubtitle.visibility = View.GONE
            binding.tvOvernextEndTime.visibility = View.GONE
            binding.tvOvernextStartTime.visibility = View.GONE
            binding.tvOvernextProgram.visibility = View.GONE
            binding.tvNextProgram.visibility = View.GONE
            binding.tvNextEndTime.visibility = View.GONE
            binding.tvNextStartTime.visibility = View.GONE
            binding.tvShowFullEpg.isFocusable = false
            binding.tvShowFullEpg.isFocusableInTouchMode = false
            binding.tvShowFullEpg.visibility = View.INVISIBLE
            binding.tvNoepgavailable.visibility = View.VISIBLE
        }
    }

    fun resetEpgPreview() {
        binding.tvNoepgavailable.visibility = View.INVISIBLE
        binding.tvEpgTvchannelname.text = ""
        binding.tvCurrentProgram.isSelected = false
        binding.epgpreviewDivider.visibility = View.GONE
        binding.tvCurrentStartTime.text = ""
        binding.tvCurrentEndTime.text = ""
        binding.tvCurrentSubtitle.visibility = View.GONE
        binding.tvCurrentProgram.text = ""
        binding.tvDescription.text = ""
        binding.tvDescription.visibility = View.INVISIBLE
        binding.tvOvernextsubtitle.visibility = View.GONE
        binding.tvNextsubtitle.visibility = View.GONE
        binding.tvOvernextEndTime.visibility = View.GONE
        binding.tvOvernextStartTime.visibility = View.GONE
        binding.tvOvernextProgram.visibility = View.GONE
        binding.tvNextProgram.visibility = View.GONE
        binding.tvNextEndTime.visibility = View.GONE
        binding.tvNextStartTime.visibility = View.GONE
    }

    fun formatUnixTimestampToTime(unixTimestamp: Long, timeOffset: Int): String {
        try {
            // Konvertiere den Unix-Zeitstempel in ein Date-Objekt
            val date = Date(unixTimestamp * 1000)

            // Erstelle ein SimpleDateFormat-Objekt für das gewünschte Zeitformat
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            // Berechne den Zeitversatz in Stunden (positiv oder negativ)
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.add(Calendar.HOUR_OF_DAY, timeOffset)

            // Gib das formatierte Datum und die Uhrzeit zurück
            return timeFormat.format(calendar.time)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    fun removeFocusFromShortEpg() {
        binding.tvShowFullEpg.isFocusable = false
        binding.tvShowFullEpg.isFocusableInTouchMode = false
    }

    fun setFocusToShortEpg() {
        if (helpViewModel.currentFocusedChannPosition != null) {
            val thisChannelPosEnabled = tvChannelsAdapter?.currentList?.indexOf(helpViewModel.currentFocusedChannPosition)
            thisChannelPosEnabled?.let {
                binding.rvLayoutTvChannels.findViewHolderForAdapterPosition(
                    it
                )?.itemView?.isSelected
            }
        }
        binding.tvShowFullEpg.isFocusable = true
        binding.tvShowFullEpg.isFocusableInTouchMode = true
        binding.tvShowFullEpg.requestFocus()
    }

    fun setFirstFocusToAccounts() {
        helpViewModel.isTvAccountsMenuFocused = true
        val currentCat = tvAccountCategoryAdapter.currentList.firstOrNull { it is AccountTvCategory.TvCategory && it.id == helpViewModel.currentFocusedTvCategory?.id }
        val pos = tvAccountCategoryAdapter.currentList.indexOf(currentCat)

        if (pos != -1 && currentCat is AccountTvCategory.TvCategory && !helpViewModel.isTvAccountFocused) {
            resetSelectedTvCategory(pos)
            binding.rvLayoutTvAccountsMenu.setSelectedPosition(pos)
            binding.rvLayoutTvAccountsMenu.post {
                binding.rvLayoutTvAccountsMenu.requestFocus()
            }
        } else {
            binding.rvLayoutTvAccountsMenu.requestFocus()
        }
    }


    fun setFocusToVideoView() {
        binding.containerFullscreenChannelchange.visibility = View.GONE
        helpViewModel.isTvFullScreen = true
        binding.videoView.isFocusable = true
        binding.videoView.isFocusableInTouchMode = true
        binding.videoView.requestFocus()
        if (helpViewModel.currentPlayingChannel != null && helpViewModel.currentPlayingChannel!!.reltvcategory.target.idByAccountData != helpViewModel.currentFocusedTvCategory?.idByAccountData) {
            helpViewModel.currentFocusedTvCategory =
                helpViewModel.currentPlayingChannel!!.reltvcategory.target
            updateChannelList()
            val currAccount = tvAccountCategoryAdapter.currentList.firstOrNull { it is AccountTvCategory.Account && it.id == helpViewModel.currentPlayingTvAccount?.id }
            val categoryPosition =
                tvAccountCategoryAdapter.currentList.indexOf(currAccount)
            binding.rvLayoutTvAccountsMenu.setSelectedPosition(categoryPosition)
            if (helpViewModel.currentPlayingChannelPosition != null && helpViewModel.currentPlayingChannelPosition!!.playlistId != helpViewModel.currentFocusedTvAccount?.id) {
                binding.rvLayoutTvAccountsMenu.setSelectedPosition(categoryPosition)
            }
        }
    }

    fun setFocusToTvChannelsFromAccount() {
        if (tvChannelsAdapter?.currentList.isNullOrEmpty()) {
            binding.rvLayoutTvAccountsMenu.requestFocus()
        } else {
            hideMainMenu()
            setTvAccountsVisibilityAnimated(false)
            binding.rvLayoutTvChannels.requestFocus()
        }
    }

    fun setFocusToTvChannels() {
        helpViewModel.focusShowEpgOrDescription = false
        if (!helpViewModel.assignChannelToEpgActive) {
            helpViewModel.isChannelOptionsContainerOpened = false
        }
        tvChannelsAdapter?.isHandled = false
        if (tvChannelsAdapter?.currentList?.isNotEmpty() == true) {
            val currentChannel = tvChannelsAdapter?.currentList?.firstOrNull { it.tvchannel.target.id == helpViewModel.currentFocusedChannel?.id }
            val currentChannelPosition = tvChannelsAdapter?.currentList?.indexOf(currentChannel)
            if (currentChannelPosition != null) {
                tvChannelsAdapter?.notifyItemChanged(currentChannelPosition)
            }
            if (helpViewModel.assignChannelToEpgActive) {
                binding.rvLayoutTvChannels.requestFocus()
            }
            hideMainMenu()
            binding.relLayoutEpg.requestLayout()
            binding.rvPreviewFullEpg.visibility = View.GONE
            binding.rvLayoutFullEpg.visibility = View.GONE
            binding.relLayoutEpg.visibility = View.VISIBLE
            binding.containerChannelOptions.visibility = View.GONE
            helpViewModel.isTvChannelsMenuFocused = true
            binding.rvLayoutTvChannels.requestFocus()
        } else {
            setTvAccountsVisibilityAnimated(true)
            resetEpgPreview()
            focusToTvAccountFromChannel()
            Toast.makeText(
                this@TvChannelsFragment.requireActivity(),
                "No channels found!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun notifyChannelAdapterAssignEpgActive() {
        val thisChannel = tvChannelsAdapter?.currentList?.firstOrNull { it.id == helpViewModel.currentFocusedChannPosition?.id }
        val thisChannelPos = tvChannelsAdapter?.currentList?.indexOf(thisChannel)
        if (thisChannelPos != null) {
            tvChannelsAdapter?.notifyItemChanged(thisChannelPos)
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

    fun getCurrentTimeWithMilliseconds(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS")
        return sdf.format(Date())  // Gibt die aktuelle Zeit im gewünschten Format zurück
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
                                    binding.tvErrorChannelPlay.text = response.message
                                    val handler = Handler(Looper.getMainLooper())
                                    handler.postDelayed({
                                        if (binding.tvErrorChannelPlay.visibility == View.VISIBLE) {
                                            clearPlayingChannel()
                                            binding.tvErrorChannelPlay.visibility = View.GONE // TextView ausblenden
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
                        binding.tvErrorChannelPlay.text = "Can't get new token!"
                        val handler = Handler(Looper.getMainLooper())
                        handler.postDelayed({
                            if (binding.tvErrorChannelPlay.visibility == View.VISIBLE) {
                                clearPlayingChannel()
                                binding.tvErrorChannelPlay.visibility = View.GONE // TextView ausblenden
                            }
                        }, 5000) // 5000 Millisekunden = 5 Sekunden
                    }
                }
            }
        }
    }

    fun clearPlayingChannel() {
        val currentPosition = tvChannelsAdapter?.currentList?.indexOf(helpViewModel.currentPlayingChannelPosition)
        if (currentPosition != null) {
            tvChannelsAdapter?.notifyItemChanged(currentPosition)
        }
        binding.tvTvchannelname.text = ""
        stopWatchTimer()
        helpViewModel.currentPlayingChannelPosition = null
        helpViewModel.currentPlayingChannel = null
        helpViewModel.currentlyPlayingUrl = ""
        player?.stop()
        helpViewModel.isCurrentlyPlayingTv = false
        hideProgressBar()
        hideFullScreenProgressBar()
    }

    fun refreshLists() {
        if (helpViewModel.currentFocusedTvAccount != helpViewModel.fullScreenFocusedAccount) {
            showChannelList(helpViewModel.fullScreenFocusedTvCategory!!.id)
            val currAccount = tvAccountCategoryAdapter.currentList.firstOrNull { it is AccountTvCategory.Account && it.id == helpViewModel.fullScreenFocusedAccount?.id }
            val accountposition = tvAccountCategoryAdapter.currentList.indexOf(currAccount)
            helpViewModel.fullScreenFocusedAccount?.id?.let { updateAccount(it) }
            onAccountClicked(accountposition)

            binding.rvLayoutTvAccountsMenu.setSelectedPosition(accountposition)
            val currentPosition = tvChannelsAdapter?.currentList!!.indexOf(helpViewModel.currentPlayingChannelPosition)
            if (currentPosition != -1) {
                tvChannelsAdapter?.notifyItemChanged(currentPosition)
            }
            val newPosition = tvChannelsAdapter?.currentList!!.indexOf(helpViewModel.fullScreenFocusedChannelPosition)
            tvChannelsAdapter?.notifyItemChanged(newPosition)
            helpViewModel.currentPlayingChannelPosition = helpViewModel.fullScreenFocusedChannelPosition
            helpViewModel.currentPlayingChannel = helpViewModel.fullScreenFocusedChannel
            helpViewModel.currentFocusedChannPosition = helpViewModel.fullScreenFocusedChannelPosition
            helpViewModel.currentFocusedChannel = helpViewModel.fullScreenFocusedChannel
            helpViewModel.currentPlayingTvAccount = helpViewModel.fullScreenFocusedAccount
        } else {
            if (helpViewModel.currentFocusedTvCategory != helpViewModel.fullScreenFocusedTvCategory) {
                showChannelList(helpViewModel.fullScreenFocusedTvCategory!!.id)
                val currentPosition = tvChannelsAdapter?.currentList!!.indexOf(helpViewModel.currentPlayingChannelPosition)
                if (currentPosition != -1) {
                    tvChannelsAdapter?.notifyItemChanged(currentPosition)
                }
                val newPosition = tvChannelsAdapter?.currentList!!.indexOf(helpViewModel.fullScreenFocusedChannelPosition)
                tvChannelsAdapter?.notifyItemChanged(newPosition)
                val currCat = tvAccountCategoryAdapter.currentList.firstOrNull { it is AccountTvCategory.TvCategory && it.id == helpViewModel.fullScreenFocusedTvCategory?.id }
                val catposition = tvAccountCategoryAdapter.currentList.indexOf(currCat)
                binding.rvLayoutTvAccountsMenu.setSelectedPosition(catposition)
                helpViewModel.currentPlayingChannelPosition = helpViewModel.fullScreenFocusedChannelPosition
                helpViewModel.currentPlayingChannel = helpViewModel.fullScreenFocusedChannel
                helpViewModel.currentFocusedChannPosition = helpViewModel.fullScreenFocusedChannelPosition
                helpViewModel.currentFocusedChannel = helpViewModel.fullScreenFocusedChannel
                helpViewModel.currentPlayingTvCategory = helpViewModel.fullScreenFocusedTvCategory
            } else {
                val currentPosition = tvChannelsAdapter?.currentList!!.indexOf(helpViewModel.currentPlayingChannelPosition)
                if (currentPosition != -1) {
                    binding.rvLayoutTvChannels.findViewHolderForAdapterPosition(currentPosition)?.itemView?.isActivated = false
                    tvChannelsAdapter?.notifyItemChanged(currentPosition)
                }
                val newPosition = tvChannelsAdapter?.currentList!!.indexOf(helpViewModel.fullScreenFocusedChannelPosition)
                binding.rvLayoutTvChannels.findViewHolderForAdapterPosition(newPosition)?.itemView?.isActivated = true
                tvChannelsAdapter?.notifyItemChanged(newPosition)
                helpViewModel.currentPlayingChannelPosition = helpViewModel.fullScreenFocusedChannelPosition
                helpViewModel.currentPlayingChannel = helpViewModel.fullScreenFocusedChannel
                helpViewModel.currentFocusedChannPosition = helpViewModel.fullScreenFocusedChannelPosition
                helpViewModel.currentFocusedChannel = helpViewModel.fullScreenFocusedChannel
            }
        }
    }


    var resumeFromBackground = false

    fun initializePlayer() {
        if (player == null || resumeFromBackground) {
            resumeFromBackground = false
            player = ExoPlayer.Builder(this@TvChannelsFragment.requireActivity())
                .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(50000, 50000, 1500, 2000).build())
                .build()

            binding.videoView.player = player
            binding.videoView.useController = false

            player?.addAnalyticsListener(EventLogger())
            // Player-Ereignis-Listener
        }
    }

    fun roundToNearestStandardFps(calculatedFps: Double): Int {
        // Liste der typischen FPS-Werte
        val standardFpsValues = listOf(25, 30, 50, 60, 120)

        // Suche den nächsten Wert
        return standardFpsValues.minByOrNull { kotlin.math.abs(it - calculatedFps) } ?: calculatedFps.toInt()
    }


    private var isFirstPlayingChannel = true

    fun switchChannel(url: String) {
        if (helpViewModel.isPlayingCatchup) {
            player?.stop()
            binding.seekBar.setPosition(0)
            Log.d("CATCHUP STALKER", "CATCHUPURL SWITCH: $url")
        }
        lastFpsToShow = 0
        helpViewModel.currentlyPlayingUrl = url
        binding.videoView.visibility = View.VISIBLE
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
            binding.tvFullscreenChannelQuality.text = resolution
            if (helpViewModel.isTvFullScreen) {
                binding.tvFullscreenChannelQuality.visibility = View.VISIBLE
            } else {
                binding.tvChannelQuality.visibility = View.VISIBLE
            }
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            val audioFormat = getAudioFormatDescription(format.channelCount)
            binding.tvAudio.text = audioFormat
            binding.tvFullscreenAudio.text = audioFormat
            if (helpViewModel.isTvFullScreen) {
                binding.tvFullscreenAudio.visibility = View.VISIBLE
            } else {
                binding.tvAudio.visibility = View.VISIBLE
            }
        }
        override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
            if (helpViewModel.isTvFullScreen) {
                hideFullScreenProgressBar()
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    delay(500) // 1,5 Sekunden warten
                    showFullScreenProgressBar()
                }
            } else {
                hideProgressBar()
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    delay(500) // 1,5 Sekunden warten
                    showProgressBar()
                }
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
                if (helpViewModel.isTvFullScreen) {
                    binding.fullScreenProgressBar.visibility = View.GONE
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.fullScreenProgressBar.visibility =
                            View.VISIBLE // Zurücksetzen der Sichtbarkeit
                    }, 250) // 500 Millisekunden = 0,5 Sekunden
                } else {
                    binding.playerProgressBar.visibility = View.GONE
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.playerProgressBar.visibility =
                            View.VISIBLE // Zurücksetzen der Sichtbarkeit
                    }, 250) // 500 Millisekunden = 0,5 Sekunden
                }
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
                                binding.tvFps.text = "FPS: $fpsToShow"
                                binding.tvFullscreenFps.text = "FPS: $fpsToShow"
                                if (helpViewModel.isTvFullScreen && helpViewModel.currentHudFocusedChannel == helpViewModel.currentPlayingChannel) {
                                    binding.tvFullscreenFps.visibility = View.VISIBLE
                                } else {
                                    if (!helpViewModel.isTvFullScreen) {
                                        binding.tvFps.visibility = View.VISIBLE
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
                    binding.tvErrorChannelPlay.text = ""
                    binding.tvErrorChannelPlay.visibility = View.GONE
                    helpViewModel.isCurrentlyPlayingTv = true
                    retryCount = 0
                    hideProgressBar()
                    hideFullScreenProgressBar()
                    if (isFirstPlayingChannel && helpViewModel.isTvFullScreen && binding.containerFullscreenChannelchange.visibility != View.VISIBLE) {
                        showHudContainer()
                        isFirstPlayingChannel = false
                    }
                    if (helpViewModel.isPlayingCatchup && helpViewModel.catchupEpgData != null) {
                        val start = helpViewModel.catchupEpgData?.startTimestamp ?: 0L
                        val end = helpViewModel.catchupEpgData?.stopTimestamp ?: 0L
                        val duration = (end - start) * 1000
                        catchupDuration = duration
                        binding.tvTotalTimeCatchup.text = formatTime(duration)
                        binding.seekBar.setDuration(duration)
                        startPeriodicExoPlayerUpdate()
                    }
                }
                Player.STATE_BUFFERING -> {
                    showProgressBar()
                }
                Player.STATE_IDLE -> {
                }
                Player.STATE_ENDED -> {
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
        Log.d("TV TIMER", "WIRD AUSGEFÜHRT FÜR: ${helpViewModel.currentPlayingChannel?.showingName}")
        watchTimeJob?.cancel() // vorherigen Timer beenden
        watchTimeJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            delay(100)
            var lastUpdateTime = System.currentTimeMillis()
            while (isActive) {
                delay(1000L)
                val currentTime = System.currentTimeMillis()
                val elapsedSeconds = maxOf(0, (currentTime - lastUpdateTime) / 1000)

                if (elapsedSeconds > 0) {
                    helpViewModel.currentPlayingChannelPosition?.tvchannel?.target?.let {
                        it.timeWatched += elapsedSeconds
                    }
                    lastUpdateTime = currentTime
                }
            }
        }
    }


    fun stopWatchTimer() {
        Log.d("TV TIMER", "WIRD GESTOPPT FÜR: ${helpViewModel.currentPlayingChannel?.showingName}")
            watchTimeJob?.cancel()
            watchTimeJob = null
            helpViewModel.currentPlayingChannelPosition?.let {
                manualPositionsBox.put(it) // In Room/Firebase/Repo
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
            binding.tvErrorChannelPlay.text = errorMessage
            binding.tvErrorChannelPlay.visibility = View.VISIBLE
            binding.videoView.useController = false
            if (!helpViewModel.isPlayingCatchup) {
                if (retryCount < maxRetries && helpViewModel.currentPlayingChannelPosition != null) {
                    retryCount++
                    val currentErrorText = binding.tvErrorChannelPlay.text.toString()
                    binding.tvErrorChannelPlay.text =
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
                    binding.tvErrorChannelPlay.text =
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
                    binding.tvErrorChannelPlay.text =
                        "Stream could not be started. Please try again later."
                    errorHandler.postDelayed({
                        if (binding.tvErrorChannelPlay.visibility == View.VISIBLE) {
                            clearPlayingChannel()
                            binding.tvErrorChannelPlay.visibility = View.GONE // TextView ausblenden
                            if (helpViewModel.isTvFullScreen) {
                                setVideoViewNotFullScreen()
                                binding.rvLayoutTvChannels.requestFocus()
                            } else {
                                if (helpViewModel.isFullEpgContainerOpened) {
                                    binding.rvLayoutFullEpg.requestFocus()
                                } else {
                                    binding.rvLayoutTvChannels.requestFocus()
                                }
                            }
                        }
                    }, 5000) // 5000 Millisekunden = 5 Sekunden
                }
            } else {
                Toast.makeText(this@TvChannelsFragment.requireActivity(), "Can't play catchup link!", Toast.LENGTH_SHORT).show()
                binding.tvErrorChannelPlay.text = "${cause?.message}"
            }
        }
    }

    private val updateCatchupHandler = Handler(Looper.getMainLooper())
    private val updateCatchupRunnable = object : Runnable {
        override fun run() {
            player?.let {
                binding.tvCurrentTimeCatchup.text = formatTime(it.currentPosition)
                binding.seekBar.setPosition(it.currentPosition)
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
    }

    fun showFullEpgContainer() {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.rv_layout_FullEpg, FullEpgFragment())
        transaction.addToBackStack(null)
        transaction.commit()

        if (helpViewModel.currentFocusedChannPosition != null && !helpViewModel.isFullScreenFullEpg) {
            helpViewModel.isFullEpgContainerOpened = true
            binding.relLayoutEpg.visibility = View.INVISIBLE
            binding.rvLayoutFullEpg.visibility = View.VISIBLE
            binding.rvLayoutFullEpg.requestFocus()
        }
    }

    fun makeFullEpgVisible() {
        helpViewModel.isFullEpgContainerOpened = true
        binding.rvLayoutFullEpg.visibility = View.VISIBLE
        binding.rvPreviewFullEpg.visibility = View.VISIBLE
    }

    private fun showChannelOptionsContainer() {
        setLayoutAlphaExcludingFragments()
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_ChannelOptions, ChannelOptionsFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.containerChannelOptions.visibility = View.VISIBLE
        binding.containerChannelOptions.requestFocus()
        helpViewModel.isChannelOptionsContainerOpened = true
    }

    private fun showCategoryOptionsContainer() {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_AssignChannelToEpg, CategoryOptionsFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.containerAssignChannelToEpg.visibility = View.VISIBLE
        binding.containerAssignChannelToEpg.requestFocus()
        helpViewModel.isCategoryManagementOpened = true
        tvAccountCategoryAdapter.isHandled = false
        hideMainMenu()
    }

    fun makeChannelOptionsContainerInvisible() {
        binding.containerChannelOptions.visibility = View.INVISIBLE
    }

    fun makeChannelOptionsContainerVisible() {
        setLayoutAlphaExcludingFragments()
        val currentChannelPosition = tvChannelsAdapter?.currentList?.indexOf(helpViewModel.currentFocusedChannPosition)
        if (currentChannelPosition != null) {
            binding.rvLayoutTvChannels.setSelectedPosition(currentChannelPosition)
            helpViewModel.currentFocusedChannPosition?.let { showEpgPreview(it.tvchannel.target) }
            tvChannelsAdapter?.notifyItemChanged(currentChannelPosition)
        }
        binding.containerChannelOptions.visibility = View.VISIBLE
    }

    fun closeChannelOptionsContainer() {
        tvChannelsAdapter?.isHandled = false
        resetLayoutAlpha()
        binding.containerChannelOptions.visibility = View.GONE
        helpViewModel.isChannelOptionsContainerOpened = false
    }

    fun closeCategoryOptionsContainer() {
        tvAccountCategoryAdapter.isLongPressBackOnce = true
        resetLayoutAlpha()
        binding.containerAssignChannelToEpg.visibility = View.GONE
        helpViewModel.isCategoryManagementOpened = false
        helpViewModel.currentFocusedTvAccount?.tvcategories?.reset()
        showMainMenu()
        if (helpViewModel.currentFocusedTvAccount?.tvcategories?.filter { it.favorite }.isNullOrEmpty()) {
            helpViewModel.isTvAccountMenuOpened = true
            binding.tvNoTvCategories.visibility = View.VISIBLE
            val userAccountQuery = accountBox.query(Accounts_.isUserCategories.equal(true)).build()
            val userAccount = userAccountQuery.findFirst()
            userAccountQuery.close()
            if (userAccount != null) {
                val allPlaylistCatQuery = tvCatBox.query(TvCategoryOB_.playlistId.equal(userAccount.id)).build()
                val allPlaylistCats = allPlaylistCatQuery.find()
                allPlaylistCatQuery.close()
                if (allPlaylistCats.isEmpty()) {
                    userAccount.isSelected = false
                    accountBox.put(userAccount)
                }
            }
            binding.rvLayoutTvAccountsMenu.requestFocus()
        } else {
            binding.rvLayoutTvAccountsMenu.requestFocus()
        }
    }

    fun setFocusToAssignEpg() {
        val assignEpgContainer = parentFragmentManager.findFragmentById(R.id.container_AssignChannelToEpg)
        if (assignEpgContainer is AssingChannelToEpgFragment) {
            assignEpgContainer.setFocusToEpgChannels()
        }
    }

    fun resetFocusedAssignEpgChannel() {
        tvChannelsAdapter?.passfocusedAssignChannel = null
    }

    fun setFocusedAssignEpgChannel() {
        if (helpViewModel.currentFocusedChannPosition != null) {
            tvChannelsAdapter?.passfocusedAssignChannel =
                helpViewModel.currentFocusedChannPosition!!
            val position =
                tvChannelsAdapter?.currentList?.indexOf(helpViewModel.currentFocusedChannPosition!!)
            if (position != null) {
                tvChannelsAdapter?.notifyItemChanged(position)
            }
        }
    }

    private var focusAssignEpgHandler = Handler(Looper.getMainLooper())
    private var focusAssignEpgRunnable: Runnable? = null

    fun refreshEpgChannelListWithChannel(tvChannel: ChannelPositions) {
        if (tvChannel.tvchannel.target.id != helpViewModel.currentAssignEpgChannel?.id) {
            val assignEpgContainer =
                parentFragmentManager.findFragmentById(R.id.container_AssignChannelToEpg)
            if (assignEpgContainer is AssingChannelToEpgFragment) {
                assignEpgContainer.resetEpgListCheck()
                // Entferne vorherige Callbacks, falls vorhanden
                focusAssignEpgRunnable?.let { focusAssignEpgHandler.removeCallbacks(it) }
                // Definiere den neuen Runnable
                focusAssignEpgRunnable = Runnable {
                    if (tvChannel.tvchannel.target.id != helpViewModel.currentAssignEpgChannel?.id) {
                        val oldChannel =
                            tvChannelsAdapter?.currentList?.firstOrNull { it.id == helpViewModel.currentAssignEpgChannel?.id }
                        val oldChannelPosition = tvChannelsAdapter?.currentList?.indexOf(oldChannel)
                        helpViewModel.currentAssignEpgChannel = tvChannel.tvchannel.target
                        if (oldChannelPosition != null) {
                            tvChannelsAdapter?.notifyItemChanged(oldChannelPosition)
                        }
                        val currentChannelPosition =
                            tvChannelsAdapter?.currentList?.indexOf(tvChannel)
                        if (currentChannelPosition != null) {
                            tvChannelsAdapter?.notifyItemChanged(currentChannelPosition)
                        }
                        assignEpgContainer.checkNewChannel()
                    }
                }
                // Starte den Runnable mit einer Verzögerung von 500 ms
                focusAssignEpgHandler.postDelayed(focusAssignEpgRunnable!!, 500)
            }
        }
    }

    fun showChannelsWithNoEpg() {
        val filteredChannels = tvChannelsAdapter?.currentList?.toList()?.filter { it.tvchannel.target.linkedEpgChannel?.target == null }
        if (!filteredChannels.isNullOrEmpty()) {
            tvChannelsAdapter?.submitList(filteredChannels)
            val assignEpgChannelContainer =
                parentFragmentManager.findFragmentById(R.id.container_AssignChannelToEpg)
            if (assignEpgChannelContainer is AssingChannelToEpgFragment) {
                helpViewModel.currentAssignEpgChannel =
                    tvChannelsAdapter?.currentList?.firstOrNull()?.tvchannel?.target
                if (helpViewModel.currentAssignEpgChannel != null) {
                    assignEpgChannelContainer.checkNewChannel()
                }
            }
        }
    }

    fun showChannelsWithAndWithoutEpg() {
        val list = tvChannelsAdapter?.thisList
        tvChannelsAdapter?.submitList(list)
        val currentChann = tvChannelsAdapter?.currentList?.firstOrNull { it.id == helpViewModel.currentFocusedChannPosition?.id }
        val position = tvChannelsAdapter?.currentList?.indexOf(currentChann)
        if (position != null) {
            binding.rvLayoutTvChannels.post {
                binding.rvLayoutTvChannels.setSelectedPosition(position)
            }
        }
    }

    fun closeAssignEpgFull() {
        val assignEpgContainer = parentFragmentManager.findFragmentById(R.id.container_AssignChannelToEpg)
        if (assignEpgContainer is AssingChannelToEpgFragment) {
            assignEpgContainer.cancelCheckChannelJob()
            assignEpgContainer.closeFragment()
        }
    }

    fun stopPlayer() {
        player?.stop()
    }

    private fun hideProgressBar() {
        progressBar!!.visibility = View.INVISIBLE
    }

    fun showProgressBar() {
        progressBar!!.visibility = View.VISIBLE
    }

    private fun hideFullScreenProgressBar() {
        fullScreenProgressBar!!.visibility = View.INVISIBLE
    }

    private fun showFullScreenProgressBar() {
        fullScreenProgressBar!!.visibility = View.VISIBLE
    }

    fun setVisibilityAssignChannelEpg(visibility: Boolean) {
        if (visibility) {
            helpViewModel.assignChannelToEpgActive = true
            binding.containerAssignChannelToEpg.visibility = View.VISIBLE
            changeFragment(AssingChannelToEpgFragment())
            binding.containerAssignChannelToEpg.requestFocus()
        } else {
            binding.containerAssignChannelToEpg.visibility = View.GONE
        }
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_AssignChannelToEpg, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
        binding.containerAssignChannelToEpg.requestFocus()
    }

    private fun showChangeFragmentInFullscreen(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_fullscreen_channelchange, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
        binding.containerFullscreenChannelchange.visibility = View.VISIBLE
        binding.containerFullscreenChannelchange.requestFocus()
    }

    // Setze den Alpha-Wert für das gesamte Layout, außer für das FragmentContainerView
    fun setLayoutAlphaExcludingFragments() {
        binding.rvLayoutTvChannels.alpha = 0.5F
        binding.relLayoutEpg.alpha = 0.5F
        binding.rvLayoutTvAccountsMenu.alpha = 0.5F
    }

    fun setMinimalAlpha() {
        binding.relLayoutEpg.alpha = 0.75F
        binding.rvLayoutTvChannels.alpha = 0.75F
        binding.rvLayoutTvAccountsMenu.alpha = 0.75F
    }

    fun resetLayoutAlpha() {
        binding.rvLayoutTvChannels.alpha = 1.0F
        binding.relLayoutEpg.alpha = 1.0F
        binding.rvLayoutTvAccountsMenu.alpha = 1.0F
    }

    fun showDetailEpgContainer() {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.rv_preview_FullEpg, DetailEpgFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.rvPreviewFullEpg.visibility = View.VISIBLE
    }

    fun showFullScreenFullEpg() {
        closeFullScreenChannelSelectorEpg()
        showFullEpgContainer()
    }

    fun hideFullEpgAndDetailEpgContainer() {
        binding.rvPreviewFullEpg.visibility = View.INVISIBLE
        binding.rvLayoutFullEpg.visibility = View.INVISIBLE
    }

    fun visibleFullEpgAndDetailEpgContainer() {
        binding.rvLayoutFullEpg.visibility = View.VISIBLE
        val fullepgFragment = parentFragmentManager.findFragmentById(R.id.rv_layout_FullEpg)
        if (fullepgFragment is FullEpgFragment) {
            fullepgFragment.setFocusToEpgData()
        }
    }

    fun openChannelFromGlobalSearch(channelPos: ChannelPositions) {
        val channel = channelPos.tvchannel.target
        if (helpViewModel.isPlayingCatchup) {
            helpViewModel.lastPlayingCatchupEpgId = ""
            helpViewModel.isPlayingCatchup = false
        }
        helpViewModel.currentPlayingTvAccount = channelPos.tvcategory.target.tvaccount.target
        helpViewModel.currentPlayingTvCategory = channelPos.tvcategory.target
        Log.d("CHECKMALSUCHE", "START: ${channelPos.tvcategory.target}")

        helpViewModel.currentPlayingTvCategory?.let {
            showChannelList(it.id)
        }

        changingPlayingChannel(channelPos)
        setVideoViewFullScreen()
    }

    fun closeDetailEpgContainer() {
        binding.rvPreviewFullEpg.visibility = View.INVISIBLE
        if ( helpViewModel.epgPreviewEpgDetail) {
            binding.tvDescription.requestFocus()
            helpViewModel.epgPreviewEpgDetail = false
        } else {
            binding.tvShowFullEpg.requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // UI-Referenzen aufheben, um Memory Leaks zu vermeiden
        binding.tvShowFullEpg.setOnClickListener(null)
        tvChannelsAdapter?.stopRunnable()
        binding.rvLayoutTvChannels.setOnClickListener(null)
        binding.rvLayoutTvChannels.setOnLongClickListener(null)
        binding.rvLayoutTvChannels.adapter = null
        binding.rvLayoutTvAccountsMenu.adapter = null
        // Setze UI-Referenzen auf null
        progressBar = null
        fullScreenProgressBar = null
        tvChannelsAdapter = null

        // ViewModel-Referenzen aufheben
        helpViewModel.lastPlayedChannel = null
        helpViewModel.currentPlayingEpgProgramId = ""
        if (player != null) {
            releasePlayer()
        }
        accountSubscription?.cancel()
        tvCatSubscription?.cancel()

        // Handler & Player-Listener entfernen
        errorHandler.removeCallbacksAndMessages(null)
        handler.removeCallbacks(hideHudRunnable)
        player?.removeListener(handlePlaybackStateListener)
        player?.removeListener(playerErrorListener)
        player?.removeAnalyticsListener(analyticsListener)
        player?.clearVideoFrameMetadataListener(videoFrameMetadataListener)
        parentFragmentManager.popBackStack()
        // Wichtig: Binding hier null setzen!
        _binding = null
    }

    fun closeFragment() {
        parentFragmentManager.popBackStack()
    }
    
    override fun onStop() {
        super.onStop()
        stopWatchTimer()
        releasePlayer()
        player = null
        helpViewModel.currentPlayingEpgProgramId = ""
    }

    override fun onResume() {
        super.onResume()
        initializePlayer()
        if (helpViewModel.currentlyPlayingUrl.isNotEmpty() && helpViewModel.currentPlayingChannelPosition != null && !helpViewModel.wasTvSectionOpened ||
            helpViewModel.isCurrentlyPlayingTv) {
            tvChannelsAdapter?.notifyDataSetChanged()
            resumeFromBackground = true
            binding.videoView.visibility = View.VISIBLE
            switchChannel(helpViewModel.currentlyPlayingUrl)
        }
    }
}