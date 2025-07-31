package com.example.mj_player_tv

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.Settings
import com.example.mj_player_tv.databinding.ActivityMainBinding
import com.example.mj_player_tv.repository.MatchEpgProcessState
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.ui.HomeFragment
import com.example.mj_player_tv.ui.MoviesFragment
import com.example.mj_player_tv.ui.SeriesFragment
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.ui.WatchHistoryFragment
import com.example.mj_player_tv.ui.WatchListFragment
import com.example.mj_player_tv.ui.adapter.EpgUpdateAdapter
import com.example.mj_player_tv.ui.adapter.PlaylistUpdateAdapter
import com.example.mj_player_tv.ui.settings.SettingsFragment
import com.example.mj_player_tv.utils.Common
import com.example.mj_player_tv.utils.Constants
import com.example.mj_player_tv.viewmodel.EpgUpdateViewModel
import com.example.mj_player_tv.viewmodel.EpgUpdateViewModelFactory
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.PlaylistUpdateViewModel
import com.example.mj_player_tv.viewmodel.PlaylistUpdateViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import com.rubensousa.dpadrecyclerview.spacing.DpadLinearSpacingDecoration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess
import android.Manifest
import android.util.Log
import com.example.mj_player_tv.database.entity.Accounts_
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.ui.PlexFragment
import com.example.mj_player_tv.viewmodel.PlexViewModel
import com.example.mj_player_tv.viewmodel.PlexViewModelFactory
import io.sentry.Sentry
import androidx.core.view.isGone
import com.example.mj_player_tv.ui.WatchlistStatsFragment


class MainActivity : FragmentActivity(), View.OnFocusChangeListener {

    private lateinit var activityMainBinding: ActivityMainBinding
    private var isSideMenuEnabled = false
    private var lastSelectedMenu: View? = null
    private var lastOpenedFragment: String? = null

    private var playlistUpdateAdapter: PlaylistUpdateAdapter? = null

    private var epgUpdateAdapter: EpgUpdateAdapter? = null

    val settingsBox = ObjectBox.store.boxFor(Settings::class.java)
    val accountBox = ObjectBox.store.boxFor(Accounts::class.java)
    val programmeBox = ObjectBox.store.boxFor(Programme::class.java)

    private val viewModel: HelpViewModel by viewModels() {
        HelpViewModelFactory(
            this.application
        )
    }

    private val stalkerViewModel: StalkerViewModel by viewModels() {
        StalkerViewModelFactory(
            this.application
        )
    }

    private val plexViewModel: PlexViewModel by viewModels() {
        PlexViewModelFactory(
            this.application
        )
    }

    private val playlistUpdateViewModel: PlaylistUpdateViewModel by viewModels() {
        PlaylistUpdateViewModelFactory(
            this.application
        )
    }

    private val epgUpdateViewModel: EpgUpdateViewModel by viewModels() {
        EpgUpdateViewModelFactory(
            this.application
        )
    }

    @OptIn(UnstableApi::class) override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)

        checkNotificationPermission()
        preparePlaylistUpdateRecyclerview()
        prepareEpgUpdateRecyclerview()

        if (viewModel.isFirstAppStart) {
            if ((accountBox.query(
                    Accounts_.isPlex.equal(true)
                ).build().findFirst()) != null) {
                activityMainBinding.btnPlex.visibility = View.VISIBLE
            } else {
                activityMainBinding.btnPlex.visibility = View.GONE
            }
            viewModel.getUserAccount(applicationContext)
            val settings = settingsBox.all
            if (settings.isEmpty()) {
                val newSettings = Settings(
                    0,
                    0,
                    mutableListOf(),
                    mutableListOf(),
                    mutableListOf(),
                    mutableListOf(),
                    mutableListOf(),
                    mutableListOf(),
                    mutableListOf()
                )
                settingsBox.put(newSettings)
                viewModel.getSettings()
            } else {
                viewModel.getSettings()
            }
            viewModel.isFirstAppStart = false
        }


        CoroutineScope(Dispatchers.IO).launch {
            viewModel.getTvAccounts()
            viewModel.getMovieAccounts()
            viewModel.getSeriesAccounts()
            viewModel.getPlexAccounts()
            viewModel.deleteOldEpgData()
            viewModel.checkForUpdates()
            stalkerViewModel.updateTokenAndProfile()
            plexViewModel.updatePlexTokens()
            viewModel.removeOldRemindedPrograms()
        }

        setContentView(activityMainBinding.root)

        setUpListeners()
        setUpInitialLayout()

        viewModel.plexAccountsLiveData.observe(this) { accounts ->
            if (accounts.isEmpty()) {
                activityMainBinding.btnPlex.visibility = View.GONE
            } else {
                activityMainBinding.btnPlex.visibility = View.VISIBLE
            }
        }

        activityMainBinding.btnSearch.setOnClickListener { it ->
            toggleActivateOnMenu(it)
            toggleVisibilityOfMainContainer(true)
            supportFragmentManager.findFragmentByTag("GlobalSearch")?.let {
                supportFragmentManager.beginTransaction().remove(it).commitNowAllowingStateLoss()
            }
            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.overlayContainer, GlobalSearchFragment(), "GlobalSearch")
            transaction.addToBackStack("GlobalSearchFragment")
            transaction.commit()
            closeMenu()
            val mainFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
            if (mainFragment is TvChannelsFragment) {
                mainFragment.clearPlayingChannel()
            }
            lastOpenedFragment = Constants.FRAGMENT_SEARCH
        }

        activityMainBinding.btnSearch.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                closeMenu()
                val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
                if (containerFragment is TvChannelsFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnTv)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFirstFocusToAccounts()
                } else if (containerFragment is MoviesFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnMovies)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToMoviesAccount()
                } else if (containerFragment is SeriesFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnSeries)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToSeriesAccount()
                } else if (containerFragment is WatchlistStatsFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnWatchstats)
                    toggleVisibilityOfMainContainer(true)
                    closeMenu()
                    hideMenu()
                    containerFragment.focusToLast()
                }
                val overlayFragment = supportFragmentManager.findFragmentById(R.id.overlayContainer)
                if (overlayFragment is GlobalSearchFragment) {
                    closeMenu()
                    hideMenu()
                    overlayFragment.focusToSearchBar()
                }
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                openClosingDialog()
            }
            return@setOnKeyListener false
        }

        activityMainBinding.btnHome.setOnClickListener {
            val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
            if (containerFragment is TvChannelsFragment) {
                containerFragment.stopWatchTimer()
                containerFragment.closeFragment()
            }
            resetNavHostFragment()
            toggleActivateOnMenu(it)
            toggleVisibilityOfMainContainer(true)
            closeMenu()
            changeFragment(HomeFragment())
            lastOpenedFragment = Constants.FRAGMENT_HOME
        }

        activityMainBinding.btnHome.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                closeMenu()
                val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
                if (containerFragment is TvChannelsFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnTv)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFirstFocusToAccounts()
                } else if (containerFragment is MoviesFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnMovies)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToMoviesAccount()
                } else if (containerFragment is SeriesFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnSeries)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToSeriesAccount()
                } else if (containerFragment is WatchlistStatsFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnWatchstats)
                    toggleVisibilityOfMainContainer(true)
                    closeMenu()
                    hideMenu()
                    containerFragment.focusToLast()
                }
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                openClosingDialog()
            }
            return@setOnKeyListener false
        }

        activityMainBinding.btnTv.setOnClickListener {
            closeMenu()
            val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
            if (containerFragment is TvChannelsFragment) {
                toggleActivateOnMenu(activityMainBinding.btnTv)
                toggleSelectedMenu(activityMainBinding.btnTv)
                viewModel.isTvAccountMenuOpened = true
                toggleVisibilityOfMainContainer(true)
                containerFragment.setFirstFocusToAccounts()
            } else {
                closeMenu()
                resetNavHostFragment()
                toggleActivateOnMenu(it)
                viewModel.firstOpenTvChFrag = true
                toggleVisibilityOfMainContainer(true)
                viewModel.isTvAccountMenuOpened = true
                lastOpenedFragment = Constants.FRAGMENT_TV
                changeFragment(TvChannelsFragment())
                activityMainBinding.navHostFragment.requestFocus()
            }
        }

        activityMainBinding.btnTv.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    closeMenu()
                    val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
                    if (containerFragment is TvChannelsFragment) {
                        toggleActivateOnMenu(activityMainBinding.btnTv)
                        viewModel.isTvAccountMenuOpened = true
                        toggleVisibilityOfMainContainer(true)
                        containerFragment.setFirstFocusToAccounts()
                    } else if (containerFragment is MoviesFragment) {
                        toggleActivateOnMenu(activityMainBinding.btnMovies)
                        toggleVisibilityOfMainContainer(true)
                        containerFragment.setFocusToMoviesAccount()
                    } else if (containerFragment is SeriesFragment) {
                        toggleActivateOnMenu(activityMainBinding.btnSeries)
                        toggleVisibilityOfMainContainer(true)
                        containerFragment.setFocusToSeriesAccount()
                    } else if (containerFragment is WatchlistStatsFragment) {
                        toggleActivateOnMenu(activityMainBinding.btnWatchstats)
                        toggleVisibilityOfMainContainer(true)
                        closeMenu()
                        hideMenu()
                        containerFragment.focusToLast()
                    }
                    return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                openClosingDialog()
            }
            return@setOnKeyListener false
        }

        activityMainBinding.btnMovies.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                closeMenu()
                val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
                if (containerFragment is TvChannelsFragment) {
                    toggleActivateOnMenu( activityMainBinding.btnTv)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFirstFocusToAccounts()
                } else if (containerFragment is MoviesFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnMovies)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToMoviesAccount()
                } else if (containerFragment is SeriesFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnSeries)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToSeriesAccount()
                } else if (containerFragment is WatchlistStatsFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnWatchstats)
                    toggleVisibilityOfMainContainer(true)
                    closeMenu()
                    hideMenu()
                    containerFragment.focusToLast()
                }
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                openClosingDialog()
            }
            return@setOnKeyListener false
        }

        activityMainBinding.btnMovies.setOnClickListener {
            val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
            if (containerFragment is TvChannelsFragment) {
                containerFragment.stopWatchTimer()
                containerFragment.closeFragment()
            }
            resetNavHostFragment()
            toggleActivateOnMenu(it)
            closeMenu()
            toggleVisibilityOfMainContainer(true)
            viewModel.isMovieAccountMenuOpened = true
            lastOpenedFragment = Constants.FRAGMENT_MOVIE
            changeFragment(MoviesFragment())
            activityMainBinding.navHostFragment.requestFocus()
        }

        activityMainBinding.btnSeries.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                closeMenu()
                val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
                if (containerFragment is TvChannelsFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnTv)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFirstFocusToAccounts()
                } else if (containerFragment is MoviesFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnMovies)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToMoviesAccount()
                } else if (containerFragment is SeriesFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnSeries)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToSeriesAccount()
                } else if (containerFragment is WatchlistStatsFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnWatchstats)
                    toggleVisibilityOfMainContainer(true)
                    closeMenu()
                    hideMenu()
                    containerFragment.focusToLast()
                }
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                openClosingDialog()
            }
            return@setOnKeyListener false
        }

        activityMainBinding.btnSeries.setOnClickListener {
            val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
            if (containerFragment is TvChannelsFragment) {
                containerFragment.stopWatchTimer()
                containerFragment.closeFragment()
            }
            resetNavHostFragment()
            toggleActivateOnMenu(it)
            closeMenu()
            toggleVisibilityOfMainContainer(true)
            viewModel.isSeriesAccountMenuOpened = true
            lastOpenedFragment = Constants.FRAGMENT_SERIES
            changeFragment(SeriesFragment())
            activityMainBinding.navHostFragment.requestFocus()
        }

        activityMainBinding.btnPlex.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                closeMenu()
                val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
                if (containerFragment is TvChannelsFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnTv)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFirstFocusToAccounts()
                } else if (containerFragment is MoviesFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnMovies)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToMoviesAccount()
                } else if (containerFragment is SeriesFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnSeries)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToSeriesAccount()
                } else if (containerFragment is PlexFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnPlex)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToPlexAccounts()
                } else if (containerFragment is WatchlistStatsFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnWatchstats)
                    toggleVisibilityOfMainContainer(true)
                    closeMenu()
                    hideMenu()
                    containerFragment.focusToLast()
                }
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                openClosingDialog()
            }
            return@setOnKeyListener false
        }

        activityMainBinding.btnPlex.setOnClickListener {
            val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
            if (containerFragment is TvChannelsFragment) {
                containerFragment.stopWatchTimer()
                containerFragment.closeFragment()
            }
            resetNavHostFragment()
            toggleActivateOnMenu(it)
            closeMenu()
            toggleVisibilityOfMainContainer(true)
            viewModel.isPlexAccountMenuOpened = true
            lastOpenedFragment = Constants.FRAGMENT_PLEX
            changeFragment(PlexFragment())
            activityMainBinding.navHostFragment.requestFocus()
        }

        activityMainBinding.btnWatchstats.setOnClickListener {
            val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
            if (containerFragment is TvChannelsFragment) {
                containerFragment.stopWatchTimer()
                containerFragment.closeFragment()
            }
            resetNavHostFragment()
            toggleActivateOnMenu(it)
            toggleVisibilityOfMainContainer(true)
            closeMenu()
            hideMenu()
            changeFragment(WatchlistStatsFragment())
            lastOpenedFragment = Constants.FRAGMENT_WATCHLIST
        }

        activityMainBinding.btnWatchstats.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                closeMenu()
                val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
                if (containerFragment is TvChannelsFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnTv)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFirstFocusToAccounts()
                } else if (containerFragment is MoviesFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnMovies)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToMoviesAccount()
                } else if (containerFragment is SeriesFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnSeries)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToSeriesAccount()
                } else if (containerFragment is WatchlistStatsFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnWatchstats)
                    toggleVisibilityOfMainContainer(true)
                    closeMenu()
                    hideMenu()
                    containerFragment.focusToLast()
                }
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                openClosingDialog()
            }
            return@setOnKeyListener false
        }


        activityMainBinding.btnSettings.setOnClickListener {
            toggleActivateOnMenu(it)
            closeMenu()
            showSettingsFragment(SettingsFragment())
            lastOpenedFragment = Constants.FRAGMENT_SETTINGS
        }

        activityMainBinding.btnSettings.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                closeMenu()
                val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
                if (containerFragment is TvChannelsFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnTv)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFirstFocusToAccounts()
                } else if (containerFragment is MoviesFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnMovies)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToMoviesAccount()
                } else if (containerFragment is SeriesFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnSeries)
                    toggleVisibilityOfMainContainer(true)
                    containerFragment.setFocusToSeriesAccount()
                } else if (containerFragment is WatchlistStatsFragment) {
                    toggleActivateOnMenu(activityMainBinding.btnWatchstats)
                    toggleVisibilityOfMainContainer(true)
                    closeMenu()
                    hideMenu()
                    containerFragment.focusToLast()
                }
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                openClosingDialog()
            }
            return@setOnKeyListener false
        }

        playlistUpdateViewModel.playlistUpdateState.observe(this) { stalkerUpdateProcessState ->
                if (stalkerUpdateProcessState.isNotEmpty()) {
                    if (activityMainBinding.rvLayoutPlaylistUpdate.isGone) {
                        activityMainBinding.rvLayoutPlaylistUpdate.visibility = View.VISIBLE
                    }
                    stalkerUpdateProcessState.forEach {
                        Log.d("WORKER UPDATE HEEELP", "${it?.playlistName} = ${it?.playlistStatus}")
                    }
                    playlistUpdateAdapter?.submitList(stalkerUpdateProcessState)
                } else {
                    playlistUpdateAdapter?.submitList(emptyList())
                    activityMainBinding.rvLayoutPlaylistUpdate.visibility = View.GONE
                }
        }


        epgUpdateViewModel.epgUpdateState.observe(this) { epgUpdateProcessState ->
            if (epgUpdateProcessState.isNotEmpty()) {
                if (activityMainBinding.rvLayoutEpgUpdate.isGone) {
                    activityMainBinding.rvLayoutEpgUpdate.visibility = View.VISIBLE
                }
                epgUpdateAdapter?.submitList(epgUpdateProcessState)
            } else {
                epgUpdateAdapter?.submitList(emptyList())
                activityMainBinding.rvLayoutEpgUpdate.visibility = View.GONE
            }

        }

        lifecycleScope.launch {
            viewModel.matchEpgProcessState.collect { matchEpgProcessState ->
                    when (matchEpgProcessState) {
                        is MatchEpgProcessState.Loading -> {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "EPG Matching started!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is MatchEpgProcessState.Success -> {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "EPG Matching finished successfully!", Toast.LENGTH_SHORT).show()
                            }
                            viewModel.resetMatchEpgProcessState()
                        }
                        is MatchEpgProcessState.Error -> {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "EPG Matching failed!", Toast.LENGTH_SHORT).show()
                            }
                            viewModel.resetMatchEpgProcessState()
                        }
                        else -> {}
                }
            }
        }
    }

    fun preparePlaylistUpdateRecyclerview() {
        playlistUpdateAdapter = PlaylistUpdateAdapter(playlistUpdateViewModel)
        activityMainBinding.rvLayoutPlaylistUpdate.apply {
            adapter = playlistUpdateAdapter
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(true, true)
            addItemDecoration(
                DpadLinearSpacingDecoration.create(
                    itemSpacing = 4
                )
            )
        }
    }

    fun prepareEpgUpdateRecyclerview() {
        epgUpdateAdapter = EpgUpdateAdapter(epgUpdateViewModel)
        activityMainBinding.rvLayoutEpgUpdate.apply {
            adapter = epgUpdateAdapter
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(true, true)
            addItemDecoration(
                DpadLinearSpacingDecoration.create(
                    itemSpacing = 4
                )
            )
        }
    }

    fun toggleVisibilityOfMainContainer(visible: Boolean) {
        if (visible) {
            activityMainBinding.navHostFragment.alpha = 1F
        } else {
            activityMainBinding.navHostFragment.alpha = 0.7F
        }
    }

    @OptIn(UnstableApi::class)
    fun checkTvChannelsFragmentFromGlobalSearch() {
        val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
        if (containerFragment is TvChannelsFragment) {

        } else {
            makeNavHostInvisible()
            closeMenu()
            hideMenu()
            resetNavHostFragment()
            toggleActivateOnMenu(activityMainBinding.btnTv)
            viewModel.firstOpenTvChFrag = true
            toggleVisibilityOfMainContainer(true)
            viewModel.isTvAccountMenuOpened = true
            lastOpenedFragment = Constants.FRAGMENT_TV
            changeFragment(TvChannelsFragment())
        }
    }

    private fun makeNavHostInvisible() {
        activityMainBinding.navHostFragment.visibility = View.INVISIBLE
    }

    fun makeNavHostVisible() {
        activityMainBinding.navHostFragment.visibility = View.VISIBLE
    }

    private fun toggleSelectedMenu(view: View) {
        lastSelectedMenu?.isSelected = false

        view.isSelected = true
        lastSelectedMenu = view
    }

    private fun toggleActivateOnMenu(view: View) {
        // Aktualisiere lastActivatedView
        lastSelectedMenu?.isActivated = false

        view.isActivated = true
        lastSelectedMenu = view
    }

    private fun setUpInitialLayout() {
        lastSelectedMenu = activityMainBinding.btnHome
        activityMainBinding.btnHome.requestFocus()
        changeFragment(HomeFragment())
        openMenu()
        toggleVisibilityOfMainContainer(false)
    }

    fun openMenu() {
        lastSelectedMenu?.isSelected = false
        activityMainBinding.linLayoutMenu.visibility = View.VISIBLE

        val targetWidth = Common.getWidthInPercent(this, 20)

        val layoutParams = activityMainBinding.linLayoutMenu.layoutParams
        val startWidth = layoutParams.width
        ValueAnimator.ofInt(startWidth, targetWidth).apply {
            duration = 150
            addUpdateListener { animation ->
                layoutParams.width = animation.animatedValue as Int
                activityMainBinding.linLayoutMenu.layoutParams = layoutParams
            }
            start()
        }

        // Stelle sicher, dass das Layout neu gezeichnet wird
        activityMainBinding.constMain.requestLayout()
        lastSelectedMenu?.requestFocus()
        isSideMenuEnabled = true
    }

    private fun setUpListeners() {
        activityMainBinding.btnSearch.onFocusChangeListener = this

        activityMainBinding.btnHome.onFocusChangeListener = this

        activityMainBinding.btnTv.onFocusChangeListener = this

        activityMainBinding.btnMovies.onFocusChangeListener = this

        activityMainBinding.btnSeries.onFocusChangeListener = this

        activityMainBinding.btnSettings.onFocusChangeListener = this

    }

    private fun closeMenu() {
        val targetWidth = Common.getWidthInPercent(this, 5)

        val layoutParams = activityMainBinding.linLayoutMenu.layoutParams
        val startWidth = layoutParams.width
        ValueAnimator.ofInt(startWidth, targetWidth).apply {
            duration = 120
            addUpdateListener { animation ->
                layoutParams.width = animation.animatedValue as Int
                activityMainBinding.linLayoutMenu.layoutParams = layoutParams
            }
            start()
        }

        // Stelle sicher, dass das Layout nach der Breitenänderung neu gezeichnet wird
        activityMainBinding.constMain.requestLayout()

        isSideMenuEnabled = false
        lastSelectedMenu?.requestFocus()
    }


    fun hideMenu() {
        activityMainBinding.linLayoutMenu.visibility = View.GONE
    }

    fun showMenu() {
        activityMainBinding.linLayoutMenu.visibility = View.VISIBLE
    }

    fun addPlaylistError(error: String) {
        activityMainBinding.tvErrorAddPlaylist.text = error
        activityMainBinding.tvErrorAddPlaylist.visibility = View.VISIBLE

        lifecycleScope.launch {
            delay(3000)
            closePlaylistError()
        }
    }

    fun closePlaylistError() {
        activityMainBinding.tvErrorAddPlaylist.visibility = View.GONE
    }

    private fun showSettingsFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.settings_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
        viewModel.isSettingsContainerOpened = true
        activityMainBinding.navHostFragment.alpha = 0.5F
        activityMainBinding.navHostFragment.isFocusable = false
        activityMainBinding.navHostFragment.isFocusableInTouchMode = false
        activityMainBinding.settingsContainer.visibility = View.VISIBLE
        closeMenu()
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.navHostFragment, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
        closeMenu()
    }

    @OptIn(UnstableApi::class)
    private fun resetNavHostFragment() {
        val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
        if (containerFragment is TvChannelsFragment) {
            viewModel.wasTvSectionOpened = true
            viewModel.currentPlayingChannelPosition = null
            viewModel.currentPlayingChannel = null
            viewModel.isCurrentlyPlayingTv = false
        } else if (containerFragment is MoviesFragment) {
            containerFragment.resetNameAndQuantity()
        }
        supportFragmentManager.popBackStackImmediate()
    }

    fun makeMainFragmentFullyVisibile() {
        activityMainBinding.navHostFragment.alpha = 1F
        activityMainBinding.navHostFragment.isFocusable = true
        activityMainBinding.navHostFragment.isFocusableInTouchMode = true
        activityMainBinding.settingsContainer.visibility = View.GONE
        openMenu()
        lastSelectFocus()
    }

    fun makeAddPlaylistContainerVisible() {
        activityMainBinding.addPlaylistFragment.visibility = View.VISIBLE
    }

    fun makeAddPlaylistContainerInvisible() {
        activityMainBinding.addPlaylistFragment.visibility = View.GONE
    }

    @OptIn(UnstableApi::class)
    fun openTvChannelsFragmentFromGlobalSearch(channelPos: ChannelPositions) {
        val containerFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment)
        if (containerFragment is TvChannelsFragment) {
            toggleActivateOnMenu(activityMainBinding.btnTv)
            toggleSelectedMenu(activityMainBinding.btnTv)
            viewModel.isTvAccountMenuOpened = true
            toggleVisibilityOfMainContainer(true)
            containerFragment.openChannelFromGlobalSearch(channelPos)
        } else {
            closeMenu()
            resetNavHostFragment()
            toggleActivateOnMenu(activityMainBinding.btnTv)
            viewModel.firstOpenTvChFrag = true
            toggleVisibilityOfMainContainer(true)
            viewModel.isTvAccountMenuOpened = true
            lastOpenedFragment = Constants.FRAGMENT_TV
            changeFragment(TvChannelsFragment())
            activityMainBinding.navHostFragment.requestFocus()
        }
    }


    fun openClosingDialog() {
        val alertDialogBuilder = AlertDialog.Builder(this)

        alertDialogBuilder.setMessage("Close App?")

        alertDialogBuilder.setPositiveButton("Yes") { _, _ ->
            exitProcess(0)
        }

        alertDialogBuilder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
            lastSelectFocus()
        }

        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    override fun onFocusChange(view: View?, hasFocus: Boolean) {
        // Hier wird aufgerufen, wenn sich der Fokus auf einem Menüpunkt ändert
        if (hasFocus) {
            if (view != null) {
            }
        }
    }

    fun lastSelectFocus() {
        lastSelectedMenu?.requestFocus()
    }

    // Fügen Sie dies in Ihrer MainActivity/Startscreen-Activity hinzu:
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Berechtigung anfordern
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATIONS
                )
            }
        }
    }

    // Companion object der Activity:
    companion object {
        const val REQUEST_CODE_NOTIFICATIONS = 1001
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}