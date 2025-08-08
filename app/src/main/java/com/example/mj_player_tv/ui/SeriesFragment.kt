package com.example.mj_player_tv.ui

import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import coil.load
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.SeasonsOB
import com.example.mj_player_tv.database.entity.SeriesCategoryOB
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.database.help.AccountMovieCategory
import com.example.mj_player_tv.database.help.AccountSeriesCategory
import com.example.mj_player_tv.database.help.AccountTvCategory
import com.example.mj_player_tv.databinding.FragmentSeriesBinding
import com.example.mj_player_tv.ui.adapter.SeriesAccountCategoryAdapter
import com.example.mj_player_tv.ui.adapter.SeriesAdapter
import com.example.mj_player_tv.ui.adapter.StalkerSeriesAdapter
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.SeriesViewModel
import com.example.mj_player_tv.viewmodel.SeriesViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import com.rubensousa.dpadrecyclerview.FocusableDirection
import com.rubensousa.dpadrecyclerview.spacing.DpadGridSpacingDecoration
import io.objectbox.Box
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@UnstableApi
class SeriesFragment : Fragment(R.layout.fragment_series) {

    private var _binding: FragmentSeriesBinding? = null

    private val binding get() = _binding!!

    private lateinit var seriesAccountCategoryAdapter: SeriesAccountCategoryAdapter

    private lateinit var seriesAdapter: SeriesAdapter

    private lateinit var stalkerSeriesdapter: StalkerSeriesAdapter

    private var fullAccountList = listOf<AccountSeriesCategory>()
    private var expandedAccountId: Long? = null
    private var currentList = listOf<AccountSeriesCategory>()

    private var isFirstOpen = true


    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val seriesCatBox: Box<SeriesCategoryOB> = ObjectBox.store.boxFor(SeriesCategoryOB::class.java)

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
        _binding = FragmentSeriesBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareRecyclerView()

        prepareSeriesRecyclerView()

        prepareStalkerSeriesRecyclerView()

        var accountsList = listOf<AccountSeriesCategory>()

        helpViewModel.seriesAccountsWithCategoriesLiveData.observe(viewLifecycleOwner) { accounts ->
            if (accounts.isEmpty()) {
                if (isFirstOpen) {
                    binding.tvNoSeriesCategories.visibility = View.VISIBLE
                    binding.rvLayoutSeriesAccountsMenu.visibility = View.INVISIBLE
                    openMainMenu()
                    if (isFirstOpen) {
                        isFirstOpen = false
                    }
                }
            } else {
                binding.tvNoSeriesCategories.visibility = View.INVISIBLE
                binding.rvLayoutSeriesAccountsMenu.visibility = View.VISIBLE
                fullAccountList = accounts
                if (expandedAccountId != null) {

                    val flatList = mutableListOf<AccountSeriesCategory>()
                    fullAccountList.forEach { account ->
                        flatList.add(account)
                        if (account is AccountSeriesCategory.Account && account.id == expandedAccountId) {
                            flatList.addAll(account.categories)
                        }
                    }
                    currentList = flatList
                } else {
                    currentList = fullAccountList
                }

                if (isFirstOpen && accountsList != accounts) {
                    accountsList = accounts
                    submitCollapsedSeriesList()
                } else {
                    if (accountsList != accounts) {
                        accountsList = accounts
                        seriesAccountCategoryAdapter.submitList(currentList)
                    }
                }
            }
        }


        stalkerViewModel.totalSeries.observe(viewLifecycleOwner) { totalSeries ->
            if (totalSeries != 0) {
                binding.tvSeriesTotalQuantity.visibility = View.VISIBLE
                binding.tvSeriesQuantity.visibility = View.VISIBLE
                binding.tvSeriesCategoryName.visibility = View.VISIBLE
                binding.tvSeriesCategoryName.text = helpViewModel.currentSeriesCategoryOB?.showingName
                binding.tvSeriesTotalQuantity.text = "/ ${totalSeries}"
                binding.tvSeriesQuantity.text = "1"
            }
        }

        stalkerSeriesdapter.addLoadStateListener { loadState ->
            val isFirstPageLoaded = loadState.source.refresh is LoadState.NotLoading
            val hasItems = stalkerSeriesdapter.itemCount > 0
            val isEndOfPagination = loadState.append.endOfPaginationReached && !hasItems

            if (isFirstPageLoaded && hasItems && firstOpenCategory) {
                binding.loadSeriesProgressBar.visibility = View.GONE
                binding.rvLayoutStalkerSeries.setSelectedPosition(0)
                val firstSerie = stalkerSeriesdapter.snapshot().firstOrNull()
                if (firstSerie != null) {
                    updateUi(firstSerie)
                    binding.linLayoutSeriesoptions.visibility = View.VISIBLE
                }
                firstOpenCategory = false
            } else {
                if (isEndOfPagination) {
                    binding.loadSeriesProgressBar.visibility = View.GONE
                    Toast.makeText(this@SeriesFragment.requireActivity(), "No series found!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val dp30 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 30f, resources.displayMetrics
        ).toInt()

        binding.btnSortserie.setOnFocusChangeListener { _, hasFocus ->
            val params = binding.btnSortserie.layoutParams
            if (hasFocus) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.btnSortserie.layoutParams = params
            } else {
                params.width = dp30
                binding.btnSortserie.layoutParams = params
            }
        }

        binding.btnSortserie.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                setSeriesAccountsVisibilityAnimated(true)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                if (helpViewModel.currentSeriesAccount!!.isStalker) {
                    binding.rvLayoutStalkerSeries.requestFocus()
                } else {
                    binding.rvLayoutSeries.requestFocus()
                }
                return@setOnKeyListener true
            }
            false
        }

        binding.btnSortserie.setOnClickListener {
            openSortDialog()
        }


        binding.btnSearchserie.setOnFocusChangeListener { _, hasFocus ->
            val params = binding.btnSearchserie.layoutParams
            if (hasFocus) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.btnSearchserie.layoutParams = params
            } else {
                params.width = dp30
                binding.btnSearchserie.layoutParams = params
            }
        }

        binding.btnSearchserie.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                setSeriesAccountsVisibilityAnimated(true)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                if (helpViewModel.currentSeriesAccount!!.isStalker) {
                    binding.rvLayoutStalkerSeries.requestFocus()
                } else {
                    binding.rvLayoutSeries.requestFocus()
                }
                return@setOnKeyListener true
            }
            false
        }

        binding.btnSearchserie.setOnClickListener {
            if (helpViewModel.currentSeriesAccount!!.isXtream) {
                xtreamViewModel.seriesSearchList = seriesAdapter.currentList
            }
            openSeriesSearchFragment()
        }

        binding.sortByAdded.setOnClickListener {
            val sortByCurrent = helpViewModel.currentSeriesCategoryOB?.sortSeriesBy ?: helpViewModel.currentSeriesAccount?.sortSeriesBy ?: helpViewModel.settings?.sortSeriesBy ?: "added"
            if (sortByCurrent != "added") {
                sortMoviesByAdded()
                binding.sortByAdded.requestFocus()
            } else {
                binding.menuSortOptions.visibility = View.GONE
                if (helpViewModel.currentSeriesAccount!!.isStalker) {
                    binding.rvLayoutStalkerSeries.requestFocus()
                } else {
                    binding.rvLayoutSeries.requestFocus()
                }
            }
        }

        binding.sortByAdded.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                val slideOut = AnimationUtils.loadAnimation(this@SeriesFragment.requireActivity(), R.anim.slide_out_to_right)
                binding.menuSortOptions.visibility = View.GONE
                binding.menuSortOptions.startAnimation(slideOut)
                if (helpViewModel.currentSeriesAccount!!.isStalker) {
                    binding.rvLayoutStalkerSeries.requestFocus()
                } else {
                    binding.rvLayoutSeries.requestFocus()
                }
                return@setOnKeyListener true
            }
            false
        }

        binding.sortByName.setOnClickListener {
            val sortByCurrent = helpViewModel.currentSeriesCategoryOB?.sortSeriesBy ?: helpViewModel.currentSeriesAccount?.sortSeriesBy ?: helpViewModel.settings?.sortSeriesBy ?: "added"
            if (sortByCurrent != "name") {
                sortMoviesByName()
                binding.sortByName.requestFocus()
            } else {
                binding.menuSortOptions.visibility = View.GONE
                if (helpViewModel.currentSeriesAccount!!.isStalker) {
                    binding.rvLayoutStalkerSeries.requestFocus()
                } else {
                    binding.rvLayoutSeries.requestFocus()
                }
            }
        }

        binding.sortByName.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                val slideOut = AnimationUtils.loadAnimation(this@SeriesFragment.requireActivity(), R.anim.slide_out_to_right)
                binding.menuSortOptions.visibility = View.GONE
                binding.menuSortOptions.startAnimation(slideOut)
                if (helpViewModel.currentSeriesAccount!!.isStalker) {
                    binding.rvLayoutStalkerSeries.requestFocus()
                } else {
                    binding.rvLayoutSeries.requestFocus()
                }
                return@setOnKeyListener true
            }
            false
        }

        binding.sortByRating.setOnClickListener {
            val sortByCurrent = helpViewModel.currentSeriesCategoryOB?.sortSeriesBy ?: helpViewModel.currentSeriesAccount?.sortSeriesBy ?: helpViewModel.settings?.sortSeriesBy ?: "added"
            if (sortByCurrent != "rating") {
                sortMoviesByRating()
                binding.sortByRating.requestFocus()
            } else {
                binding.menuSortOptions.visibility = View.GONE
                if (helpViewModel.currentSeriesAccount!!.isStalker) {
                    binding.rvLayoutStalkerSeries.requestFocus()
                } else {
                    binding.rvLayoutSeries.requestFocus()
                }
            }
        }

        binding.sortByRating.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                val slideOut = AnimationUtils.loadAnimation(this@SeriesFragment.requireActivity(), R.anim.slide_out_to_right)
                binding.menuSortOptions.visibility = View.GONE
                binding.menuSortOptions.startAnimation(slideOut)
                if (helpViewModel.currentSeriesAccount!!.isStalker) {
                    binding.rvLayoutStalkerSeries.requestFocus()
                } else {
                    binding.rvLayoutSeries.requestFocus()
                }
                return@setOnKeyListener true
            }
            false
        }

        seriesViewModel.updateSerieRVRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                updateSingleSerie()
                seriesViewModel.clearUpdateSerieInRV()
            }
        }

        seriesViewModel.focusToSeriesRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                binding.overlayLayout.visibility = View.GONE
                val account = helpViewModel.currentSeriesAccount
                if (account != null) {
                    if (account.isXtream) {
                        binding.rvLayoutSeries.requestFocus()
                    } else {
                        binding.rvLayoutStalkerSeries.requestFocus()
                    }
                }
                seriesViewModel.clearFocusToSeries()
            }
        }

    }

    private fun openSortDialog() {
        val slideIn = AnimationUtils.loadAnimation(this@SeriesFragment.requireActivity(), R.anim.slide_in_right)
        binding.menuSortOptions.visibility = View.VISIBLE
        binding.menuSortOptions.startAnimation(slideIn)
        val sortByCurrent = helpViewModel.currentSeriesCategoryOB?.sortSeriesBy ?: helpViewModel.currentSeriesAccount?.sortSeriesBy ?: helpViewModel.settings?.sortSeriesBy ?: "added"
        when (sortByCurrent) {
            "added" -> binding.sortByAdded.requestFocus()
            "name" -> binding.sortByName.requestFocus()
            "rating" -> binding.sortByRating.requestFocus()
        }
    }

    private fun sortMoviesByAdded() {
        resetVisibility()
        helpViewModel.currentSeriesCategoryOB?.sortSeriesBy = "added"
        if (helpViewModel.currentMovieAccount!!.isStalker) {
            reloadStalkerSeries("added")
        } else {
            val moviesList = seriesAdapter.currentList.sortedBy { it.added }
            seriesAdapter.submitList(null)
            reloadXtreamSeries(moviesList)
        }
    }

    private fun sortMoviesByName() {
        resetVisibility()
        helpViewModel.currentSeriesCategoryOB?.sortSeriesBy = "name"
        if (helpViewModel.currentMovieAccount!!.isStalker) {
            reloadStalkerSeries("name")
        } else {
            val moviesList = seriesAdapter.currentList.sortedBy { it.seriesName }
            seriesAdapter.submitList(null)
            reloadXtreamSeries(moviesList)
        }
    }

    private fun sortMoviesByRating() {
        resetVisibility()
        helpViewModel.currentSeriesCategoryOB?.sortSeriesBy = "rating"
        if (helpViewModel.currentMovieAccount!!.isStalker) {
            reloadStalkerSeries("rating")
        } else {
            val moviesList = seriesAdapter.currentList.sortedByDescending { it.rating_imdb?.toDoubleOrNull() ?: 0.0 }
            seriesAdapter.submitList(null)
            reloadXtreamSeries(moviesList)
        }
    }

    private fun reloadXtreamSeries(series: List<SeriesOB>) {
        binding.loadSeriesProgressBar.visibility = View.GONE
        binding.tvSeriesTotalQuantity.visibility = View.VISIBLE
        binding.tvSeriesQuantity.visibility = View.VISIBLE
        binding.tvSeriesCategoryName.visibility = View.VISIBLE
        binding.tvSeriesTotalQuantity.text = "/ ${series.size}"
        binding.tvSeriesCategoryName.text = helpViewModel.currentMovieCategoryOB!!.showingName
        binding.rvLayoutSeries.visibility = View.VISIBLE
        seriesAdapter.submitList(series)
        binding.rvLayoutSeries.post {
            binding.rvLayoutSeries.setSelectedPosition(0)
            val firstMovie = series.first()
            updateUi(firstMovie)
            binding.linLayoutSeriesoptions.visibility = View.VISIBLE
        }
    }

    private fun resetVisibility() {
        binding.linLayoutSeriesoptions.visibility = View.INVISIBLE
        if (helpViewModel.currentMovieAccount!!.isStalker) {
            binding.rvLayoutStalkerSeries.visibility = View.GONE
        }
        helpViewModel.currentMovieImage = null
        binding.loadSeriesProgressBar.visibility = View.VISIBLE
        binding.rvLayoutSeries.visibility = View.GONE
        binding.tvSeriesQuantity.text = ""
        binding.tvRemainingTime.text = ""
        firstOpenCategory = true
        binding.tvSeriesCategoryName.text = ""
        binding.tvSeriesTotalQuantity.text = ""
        resetDetailsUi()
    }

    private fun reloadStalkerSeries(sortBy: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            stalkerSeriesdapter.submitData(PagingData.empty()) // Adapter leeren
            stalkerViewModel.getSeriesByCategory(
                helpViewModel.currentSeriesAccount!!,
                helpViewModel.currentSeriesCategoryOB!!.seriesCatId,
                seriesBox,
                sortBy
            )
                .collectLatest {
                    binding.progressBar.visibility = View.GONE
                    binding.rvLayoutStalkerSeries.visibility = View.VISIBLE
                    stalkerSeriesdapter.submitData(it)
                }
        }
    }

    private fun prepareRecyclerView() {
        seriesAccountCategoryAdapter = SeriesAccountCategoryAdapter(::onAccountClicked, { currentList }, helpViewModel, this, onSeriesCategoryLongClickListener)
        binding.rvLayoutSeriesAccountsMenu.apply {
            adapter = seriesAccountCategoryAdapter
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

    private val onSeriesCategoryLongClickListener = SeriesAccountCategoryAdapter.OnLongClickListener { view, position ->

    }

    private fun submitCollapsedSeriesList() {
        currentList = fullAccountList
        seriesAccountCategoryAdapter.submitList(currentList)
        binding.rvLayoutSeriesAccountsMenu.post {
            if (!isAdded || view == null) return@post

            if (isFirstOpen) {
                if (helpViewModel.clickedSeriesAccountId != 0L && helpViewModel.clickedSeriesAccountPosition != - 1) {
                    onAccountClicked(helpViewModel.clickedSeriesAccountPosition)
                } else {
                    isFirstOpen = false
                    binding.rvLayoutSeriesAccountsMenu.requestFocus()
                }
            } else {
                binding.rvLayoutSeriesAccountsMenu.requestFocus()
            }
        }
    }

    private fun onAccountClicked(position: Int) {
        val item = seriesAccountCategoryAdapter.currentList[position] as AccountSeriesCategory.Account

        if (expandedAccountId == item.id) {
            expandedAccountId = null
            seriesAccountCategoryAdapter.selectedSeriesCategoryId = 0L
            helpViewModel.clickedSeriesAccountId = 0L
            helpViewModel.clickedSeriesAccountPosition = -1
            seriesAccountCategoryAdapter.notifyItemChanged(position)
            submitCollapsedSeriesList()
            binding.rvLayoutSeriesAccountsMenu.post {
                binding.rvLayoutSeriesAccountsMenu.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
            }
            return
        }
        expandedAccountId = item.id

        val oldAccount = seriesAccountCategoryAdapter.currentList.firstOrNull {
            it is AccountSeriesCategory.Account && it.id == helpViewModel.clickedSeriesAccountId
        } as? AccountSeriesCategory.Account
        val oldAccountPosition = seriesAccountCategoryAdapter.currentList.indexOf(oldAccount)
        val newAccount = seriesAccountCategoryAdapter.currentList.firstOrNull {
            it is AccountSeriesCategory.Account && it.id == item.id
        }as? AccountSeriesCategory.Account
        val newAccountPosition = seriesAccountCategoryAdapter.currentList.indexOf(newAccount)

        seriesAccountCategoryAdapter.notifyItemChanged(oldAccountPosition)

        helpViewModel.clickedSeriesAccountId = item.id
        helpViewModel.clickedSeriesAccountPosition = position

        seriesAccountCategoryAdapter.notifyItemChanged(newAccountPosition)

        val flatList = mutableListOf<AccountSeriesCategory>()
        fullAccountList.forEach { account ->
            if (account is AccountSeriesCategory.Account) {
                flatList.add(account)
                if (account.id == item.id) {
                    flatList.addAll(account.categories)
                }
            }
        }

        currentList = flatList
        seriesAccountCategoryAdapter.submitList(flatList) {
            binding.rvLayoutSeriesAccountsMenu.post {
                val list = seriesAccountCategoryAdapter.currentList
                val clickedAccount = seriesAccountCategoryAdapter.currentList.firstOrNull {
                    it is AccountSeriesCategory.Account && it.id == item.id
                } as? AccountSeriesCategory.Account
                val clickedAccountPosition = seriesAccountCategoryAdapter.currentList.indexOf(clickedAccount)
                // Scroll zu Account, falls notwendig
                binding.rvLayoutSeriesAccountsMenu.scrollToPosition(clickedAccountPosition)

                // WICHTIG: Stelle sicher, dass die Kategorie darunter aufgebaut wird
                if (position + 1 < list.size &&
                    list[position + 1] is AccountSeriesCategory.SeriesCategory
                ) {

                    // Kein requestFocus()! Nur sicherstellen, dass ViewHolder aufgebaut ist.
                    binding.rvLayoutSeriesAccountsMenu.post {
                        binding.rvLayoutSeriesAccountsMenu
                            .findViewHolderForAdapterPosition(clickedAccountPosition)
                        // Nichts weiter tun – dadurch ist das Item bereit für Fokus per DPAD_DOWN
                    }
                }
                if (isFirstOpen) {
                    val focusedCategoryId = helpViewModel.currentSeriesCategoryOB?.id ?: 0L
                    if (focusedCategoryId != 0L) {
                        val categoryPosition = list.indexOfFirst {
                            it is AccountSeriesCategory.SeriesCategory && it.id == focusedCategoryId
                        }

                        if (categoryPosition != -1) {
                            binding.rvLayoutSeriesAccountsMenu.setSelectedPosition(categoryPosition)
                            binding.rvLayoutSeriesAccountsMenu.post {
                                binding.rvLayoutSeriesAccountsMenu
                                    .findViewHolderForAdapterPosition(categoryPosition)
                                    ?.itemView?.requestFocus()
                            }
                        }
                    }
                }
            }
        }

        if (item.categories.isEmpty()) {
            helpViewModel.clickedSeriesAccountId = 0L
            helpViewModel.clickedSeriesAccountPosition = -1
            seriesAccountCategoryAdapter.notifyItemChanged(position)
            Toast.makeText(this@SeriesFragment.requireActivity(), "No categories enabled!", Toast.LENGTH_SHORT).show()
        }
    }


    private fun prepareSeriesRecyclerView() {
        seriesAdapter = SeriesAdapter(onSeriesClickListener, onSeriesLongClickListener,this, helpViewModel)
        binding.rvLayoutSeries.apply {
            adapter = seriesAdapter
            addItemDecoration(
                DpadGridSpacingDecoration.create(
                    itemSpacing = 16,
                    edgeSpacing = 7,
                    perpendicularItemSpacing = 14
                )
            )
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setFocusOutAllowed(true, false)
            setFocusOutSideAllowed(true, false)
            setSmoothFocusChangesEnabled(false)
        }
    }


    private fun prepareStalkerSeriesRecyclerView() {
        stalkerSeriesdapter = StalkerSeriesAdapter(onStalkerSeriesClickListener, onStalkerSeriesLongClickListener,this, helpViewModel)
        binding.rvLayoutStalkerSeries.apply {
            adapter = stalkerSeriesdapter
            addItemDecoration(
                DpadGridSpacingDecoration.create(
                    itemSpacing = 16,
                    edgeSpacing = 7,
                    perpendicularItemSpacing = 14
                )
            )
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setFocusOutAllowed(true, false)
            setFocusOutSideAllowed(true, false)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private val onStalkerSeriesClickListener = StalkerSeriesAdapter.OnClickListener { serie ->
        viewLifecycleOwner.lifecycleScope.launch {
            if (serie.idByAccountData == helpViewModel.currentFocusedSerie?.idByAccountData) {
                seriesViewModel.openedSameSeries = true
            }
            helpViewModel.currentFocusedSerie = serie
            helpViewModel.currentSeriesImage = if (!serie.backdropPath.isNullOrEmpty()) {
                serie.backdropPath
            } else if (!serie.screenshot_uri.isNullOrEmpty()) {
                serie.screenshot_uri
            } else {
                ""
            }
            openSeriesDetailFragment()
        }
    }

    private val onStalkerSeriesLongClickListener = StalkerSeriesAdapter.OnLongClickListener { movie, position ->

    }

    fun updateAccount(accountId: Long) {
        helpViewModel.currentSeriesAccount = accountBox.get(accountId)
    }

    private val onSeriesClickListener = SeriesAdapter.OnClickListener { serie ->
        viewLifecycleOwner.lifecycleScope.launch {
            if (serie.idByAccountData == helpViewModel.currentFocusedSerie?.idByAccountData) {
                seriesViewModel.openedSameSeries = true
            }
            helpViewModel.currentFocusedSerie = serie
            helpViewModel.currentSeriesImage = if (!serie.backdropPath.isNullOrEmpty()) {
                serie.backdropPath
            } else if (!serie.screenshot_uri.isNullOrEmpty()) {
                serie.screenshot_uri
            } else {
                ""
            }
            openSeriesDetailFragment()
        }
    }

    private val onSeriesLongClickListener = SeriesAdapter.OnLongClickListener { serie, position ->

    }

    fun updatePlayingSerie(serie: SeriesOB) {
        if (helpViewModel.currentSeriesAccount!!.isStalker) {
            val serieToUpdate = stalkerSeriesdapter.snapshot().items.firstOrNull { it.idByAccountData == serie.idByAccountData }
            if (serieToUpdate != null) {
                helpViewModel.currentFocusedSerie = serie
                val seriesPosition = stalkerSeriesdapter.snapshot().items.indexOf(serieToUpdate)
                stalkerSeriesdapter.notifyItemChanged(seriesPosition)
                setSeriesDetailsNotImages(serieToUpdate)
            }
        } else if (helpViewModel.currentSeriesAccount!!.isXtream) {
            val serieToUpdate = seriesAdapter.currentList.firstOrNull { it.idByAccountData == serie.idByAccountData }
            if (serieToUpdate != null) {
                helpViewModel.currentFocusedSerie = serie
                val seriesPosition = seriesAdapter.currentList.indexOf(serieToUpdate)
                seriesAdapter.notifyItemChanged(seriesPosition)
                setSeriesDetailsNotImages(serieToUpdate)
            }
        } else {

        }
    }

    fun updateUi(serie: SeriesOB) {
        if (serie.idByAccountData != helpViewModel.currentFocusedSerie?.idByAccountData) {
            helpViewModel.currentFocusedSerie = serie
            resetDetailsUi()
            setDetailsUi(serie)
        }
    }

    var currentTmdbSerieDetailJob: Job? = null

    fun setDetailsUi(serie: SeriesOB) {
        helpViewModel.currentTmdBSeriesDetails = null
        val settings = helpViewModel.settings
        if (settings != null) {
            val chPos = if (helpViewModel.currentSeriesAccount?.isStalker == true) {
                val currMovie = stalkerSeriesdapter.snapshot().firstOrNull { it?.idByAccountData == serie.idByAccountData }
                stalkerSeriesdapter.snapshot().indexOf(currMovie) + 1
            } else {
                val currMovie = seriesAdapter.currentList.firstOrNull { it?.idByAccountData == serie.idByAccountData }
                seriesAdapter.currentList.indexOf(currMovie) + 1
            }
            binding.tvSeriesQuantity.text = "$chPos "
            binding.ivFavorite.visibility = if (serie.isFavorite) {
                View.VISIBLE
            } else {
                View.GONE
            }
            setSeriesDetailsNotImages(serie)
            if ((helpViewModel.currentSeriesAccount!!.isXtream && !xtreamViewModel.seriesCache.containsKey(serie.idByAccountData)) ||
                helpViewModel.currentSeriesAccount!!.isStalker && !stalkerViewModel.seriesCache.containsKey(serie.idByAccountData)) {
                getSeriesDetailInfo(serie)
            } else {
                if (helpViewModel.currentSeriesAccount!!.isXtream) {
                    helpViewModel.focusedSeasons = xtreamViewModel.seriesCache[serie.idByAccountData]?.first
                    serie.totalSeasons = helpViewModel.focusedSeasons?.size ?: 1
                    binding.tvTotSeasons.visibility = View.VISIBLE
                    binding.tvTotSeasons.text = if (serie.totalSeasons == 1) {
                        "1 Season"
                    } else {
                        "${serie.totalSeasons} Seasons"
                    }
                    helpViewModel.focusedEpisodes = xtreamViewModel.seriesCache[serie.idByAccountData]?.second?.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))?.toMutableList()
                } else {
                    helpViewModel.focusedSeasons = stalkerViewModel.seriesCache[serie.idByAccountData]?.first
                    serie.totalSeasons = helpViewModel.focusedSeasons?.size ?: 1
                    binding.tvTotSeasons.visibility = View.VISIBLE
                    binding.tvTotSeasons.text = if (serie.totalSeasons == 1) {
                        "1 Season"
                    } else {
                        "${serie.totalSeasons} Seasons"
                    }
                    helpViewModel.focusedEpisodes = stalkerViewModel.seriesCache[serie.idByAccountData]?.second?.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))?.toMutableList()
                }
            }
            currentTmdbSerieDetailJob?.cancel()
            currentTmdbSerieDetailJob = viewLifecycleOwner.lifecycleScope.launch {
                    if (serie.backdropPath.isNullOrEmpty()) {
                        if (!serie.tmdb_id.isNullOrEmpty() && settings.tmdbApiKey.isNotEmpty()) {
                            if (serie.tmdb_id.startsWith("tt")) {
                                val tmdbSeriesDetailsByImdbId =
                                    helpViewModel.getTmdbMovieDetailsByImdb(
                                        url = "https://api.themoviedb.org/3/find/",
                                        imdbId = serie.tmdb_id,
                                        apiKey = settings.tmdbApiKey
                                    ).await()
                                when (tmdbSeriesDetailsByImdbId) {
                                    is Resource.Success -> {
                                        val movie = tmdbSeriesDetailsByImdbId.data?.movie_results?.firstOrNull()
                                        val backdropPath = movie?.backdrop_path
                                        val posterPath = movie?.poster_path
                                        val screenshotUri = serie.screenshot_uri

                                        val backdropImageUrl = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
                                        val posterImageUrl = posterPath?.let { "https://image.tmdb.org/t/p/original$it" }

                                        val imageToLoad = when {
                                            !backdropImageUrl.isNullOrEmpty() -> backdropImageUrl
                                            !posterImageUrl.isNullOrEmpty() -> posterImageUrl
                                            !screenshotUri.isNullOrEmpty() -> screenshotUri
                                            else -> null
                                        }

                                        if (imageToLoad != null) {
                                            binding.ivSeriesposter.visibility = View.VISIBLE
                                            binding.ivSeriesposter.load(imageToLoad)
                                        } else {
                                            binding.ivSeriesposter.visibility = View.INVISIBLE
                                        }

                                        serie.backdropPath = imageToLoad ?: ""
                                    }

                                    is Resource.Error -> {
                                        val seriesPoster = serie.screenshot_uri
                                        if (!seriesPoster.isNullOrEmpty()) {
                                            binding.ivSeriesposter.visibility = View.VISIBLE
                                            binding.ivSeriesposter.load(seriesPoster)
                                        } else {
                                            binding.ivSeriesposter.visibility = View.INVISIBLE
                                        }
                                    }
                                }
                            } else {
                                val tmdbSerieDetails = helpViewModel.getTmdbSeriesDetails(
                                    url = "https://api.themoviedb.org/3/tv/",
                                    seriesId = serie.tmdb_id.toInt(),
                                    apiKey = settings.tmdbApiKey
                                ).await()
                                when (tmdbSerieDetails) {
                                    is Resource.Success -> {
                                        if (serie.backdropPath.isNullOrEmpty()) {
                                            val data = tmdbSerieDetails.data
                                            val backdropPath = data?.backdrop_path
                                            val posterPath = data?.poster_path
                                            val screenshotUri = serie.screenshot_uri

                                            val backdropImageUrl = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
                                            val posterImageUrl = posterPath?.let { "https://image.tmdb.org/t/p/original$it" }

                                            val imageToLoad = when {
                                                !backdropImageUrl.isNullOrEmpty() -> backdropImageUrl
                                                !posterImageUrl.isNullOrEmpty() -> posterImageUrl
                                                !screenshotUri.isNullOrEmpty() -> screenshotUri
                                                else -> null
                                            }

                                            if (imageToLoad != null) {
                                                binding.ivSeriesposter.visibility = View.VISIBLE
                                                binding.ivSeriesposter.load(imageToLoad)
                                            } else {
                                                binding.ivSeriesposter.visibility = View.INVISIBLE
                                            }

                                            serie.backdropPath = imageToLoad ?: ""
                                        } else {
                                            binding.ivSeriesposter.visibility = View.VISIBLE
                                            binding.ivSeriesposter.load(serie.backdropPath)
                                        }
                                    }
                                    is Resource.Error -> {
                                        val seriesPoster = serie.screenshot_uri
                                        if (!seriesPoster.isNullOrEmpty()) {
                                            binding.ivSeriesposter.visibility = View.VISIBLE
                                            binding.ivSeriesposter.load(seriesPoster)
                                        } else {
                                            binding.ivSeriesposter.visibility = View.INVISIBLE
                                        }
                                    }
                                }
                            }
                        } else {
                            if (!serie.screenshot_uri.isNullOrEmpty()) {
                                binding.ivSeriesposter.visibility = View.VISIBLE
                                binding.ivSeriesposter.load(serie.screenshot_uri)
                            } else {
                                binding.ivSeriesposter.visibility = View.INVISIBLE
                            }
                        }
                    } else {
                        binding.ivSeriesposter.visibility = View.VISIBLE
                        binding.ivSeriesposter.load(serie.backdropPath)
                    }
            }
        }
    }

    var seriesDetailJob: Job? = null

    fun getSeriesDetailInfo(serie: SeriesOB) {
        seriesDetailJob?.cancel()
        seriesDetailJob = helpViewModel.viewModelScope.launch {
            if (helpViewModel.currentSeriesAccount!!.isXtream) {
                val seasons =
                    xtreamViewModel.getXtreamSerieDetails(serie, helpViewModel.currentSeriesAccount!!)
                serie.totalSeasons = seasons.size
                helpViewModel.focusedSeasons = seasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                    .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE }).toMutableList()
                helpViewModel.focusedEpisodes = xtreamViewModel.episodesList.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })).toMutableList()
                binding.tvTotSeasons.text = if (seasons.isEmpty()) {
                    binding.tvTotSeasons.visibility = View.INVISIBLE
                    ""
                } else {
                    binding.tvTotSeasons.visibility = View.VISIBLE
                    if (seasons.size == 1) {
                        "1 Season"
                    } else {
                        "${seasons.size} Seasons"
                    }
                }
            } else {
                stalkerViewModel.seriesDetailData.postValue(mutableListOf())
                stalkerViewModel.getSeriesDetail(serie, helpViewModel.currentSeriesAccount!!)
                stalkerViewModel.seriesDetailData.observe(viewLifecycleOwner) { seasons ->
                    serie.totalSeasons = seasons.size
                    helpViewModel.focusedSeasons = seasons.sortedWith(compareBy<SeasonsOB> { it.seasonNumber.toIntOrNull() == null || it.seasonNumber.toIntOrNull() == 0 }
                        .thenBy { it.seasonNumber.toIntOrNull() ?: Int.MAX_VALUE }).toMutableList()
                    helpViewModel.focusedEpisodes = stalkerViewModel.episodesList.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })).toMutableList()
                    binding.tvTotSeasons.text = if (seasons.isEmpty()) {
                        binding.tvTotSeasons.visibility = View.INVISIBLE
                        ""
                    } else {
                        binding.tvTotSeasons.visibility = View.VISIBLE
                        if (seasons.size == 1) {
                            "1 Season"
                        } else {
                            "${seasons.size} Seasons"
                        }
                    }
                }
            }
            binding.tvTotSeasons.text = if (serie.totalSeasons != 0) {
                binding.tvTotSeasons.visibility = View.VISIBLE
                if (serie.totalSeasons == 1) {
                    "${serie.totalSeasons} Season"
                } else {
                    "${serie.totalSeasons} Seasons"
                }
            } else {
                binding.tvTotSeasons.visibility = View.INVISIBLE
                ""
            }
        }
    }

    fun setSeriesDetailsNotImages(serie: SeriesOB) {
        binding.tvSeriestitle.text = if (serie.seriesName.isNotEmpty()) {
            binding.tvSeriestitle.visibility = View.VISIBLE
            binding.tvSeriestitle.isSelected = true
            serie.seriesName
        } else {
            "No Title!"
        }
        binding.tvReleaseyear.text = if (!serie.seriesYear.isNullOrEmpty()) {
            binding.tvReleaseyear.visibility = View.VISIBLE
            val year = if (serie.seriesYear.length >= 4) serie.seriesYear.substring(0, 4) else "n/a"
            year
        } else {
            binding.tvReleaseyear.visibility = View.VISIBLE
            "n/a"
        }
        binding.tvCategories.text = if (!serie.genres_str.isNullOrEmpty()) {
            binding.tvCategories.visibility = View.VISIBLE
            serie.genres_str
        } else {
            binding.tvCategories.visibility = View.INVISIBLE
            ""
        }
        binding.tvSeriesdescription.text = if (!serie.description.isNullOrEmpty()) {
            binding.tvSeriesdescription.visibility = View.VISIBLE
            serie.description
        } else {
            "No description available"
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = serie.seriesPercentagePlayed.toInt()

        binding.tvRating.text = if (!serie.rating_imdb.isNullOrEmpty()) {
            binding.tvRating.visibility = View.VISIBLE
            val formattedRating = formatRating(serie.rating_imdb)
            formattedRating
        } else {
            binding.tvRating.visibility = View.VISIBLE
            "0.0"
        }
        binding.smallRating.rating = if (!serie.rating_imdb.isNullOrEmpty()) {
            binding.smallRating.visibility = View.VISIBLE
            val formattedRating = formatRating(serie.rating_imdb)
            val ratingValue = formattedRating.toFloatOrNull() ?: 0.0f
            (ratingValue / 2.0f) // Teile die Bewertung durch 2, um sie auf die Skala der 5 Sterne anzupassen
        } else {
            binding.smallRating.visibility = View.VISIBLE
            0.0f // Wenn die Bewertung leer oder null ist, setze die Bewertung auf 0
        }
        binding.tvActor.visibility = View.VISIBLE
        binding.tvActors.text = if (!serie.actors.isNullOrEmpty()) {
            binding.tvActors.visibility = View.VISIBLE
            serie.actors
        } else {
            binding.tvActors.visibility = View.VISIBLE
            "n/a"
        }
        binding.tvDirector.visibility = View.VISIBLE
        binding.tvDirectors.text = if (!serie.director.isNullOrEmpty()) {
            binding.tvDirectors.visibility = View.VISIBLE
            serie.director
        } else {
            binding.tvDirectors.visibility = View.VISIBLE
            "n/a"
        }
        binding.tvAge.text = if (!serie.age.isNullOrEmpty()) {
            binding.tvAge.visibility = View.VISIBLE
            serie.age
        } else {
            binding.tvAge.visibility = View.GONE
            ""
        }
        if (serie.seriesPercentagePlayed != 0.0) {
            if (serie.isCompletelyWatched) {
                binding.progressBar.progress = 100
                binding.tvRemainingTime.visibility = View.VISIBLE
                binding.tvRemainingTime.text = "Completed!"
            } else if (serie.isPartlyWatched) {
                binding.tvRemainingTime.visibility = View.VISIBLE
                // Schritt 1: Berechne den Fortschritt in Prozent
                val progressPercentage = serie.seriesPercentagePlayed * 100  // Wandelt den Fortschritt in Prozent um (von 0.0 bis 100.0)

// Schritt 2: Runden auf maximal 2 Dezimalstellen
                val formattedPercentage = String.format("%.2f", progressPercentage)  // Formatierung auf 2 Dezimalstellen

// Schritt 3: Update der ProgressBar
                val progressBarPercentage = (serie.seriesPercentagePlayed * 100).toInt()  // ProgressBar erwartet einen Integer zwischen 0 und 100
                binding.progressBar.progress = progressBarPercentage

// Schritt 4: Anzeige des Fortschritts als Text
                binding.tvRemainingTime.text = "$formattedPercentage% watched.."  // Zeigt den Fortschritt als Text im Prozentformat an

            } else {
                binding.tvRemainingTime.visibility = View.INVISIBLE
            }
        } else {
            binding.tvRemainingTime.visibility = View.INVISIBLE
        }
    }

    fun resetDetailsUi() {
        binding.ivFavorite.visibility = View.INVISIBLE
        binding.ivSeriesposter.visibility = View.INVISIBLE
        binding.tvSeriestitle.visibility = View.INVISIBLE
        binding.tvReleaseyear.visibility = View.INVISIBLE
        binding.tvCategories.visibility = View.INVISIBLE
        binding.tvSeriesdescription.visibility = View.INVISIBLE
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvRating.visibility = View.INVISIBLE
        binding.smallRating.visibility = View.INVISIBLE
        binding.tvActor.visibility = View.INVISIBLE
        binding.tvActors.visibility = View.INVISIBLE
        binding.tvDirectors.visibility = View.INVISIBLE
        binding.tvDirector.visibility = View.INVISIBLE
        binding.tvAge.visibility = View.INVISIBLE
        binding.tvTotSeasons.visibility = View.INVISIBLE
        binding.tvRemainingTime.visibility = View.INVISIBLE
    }

    private fun formatDuration(duration: Int, accountId: Long): String {
        val currentAccount = accountBox.get(accountId)
        return if (currentAccount.isStalker) {
            if (duration < 60) {
                "$duration min"
            } else {
                val hours = duration / 60
                val minutes = duration % 60
                "${hours}h ${minutes}min"
            }
        } else if (currentAccount.isXtream) {
                val minutes = (duration / 60) % 60
                val hours = duration / 3600
                "${hours}h ${minutes}min"
        } else {
            ""
        }
    }

    fun formatRating(rating: String?): String {
        val ratingValue = rating?.toFloatOrNull()
        return when {
            ratingValue == null -> ""
            ratingValue == ratingValue.toInt().toFloat() -> String.format("%.1f", ratingValue).replace(",", ".")
            else -> String.format("%.1f", ratingValue).replace(",", ".")
        }
    }

    fun focusToSortButton() {
        binding.btnSortserie.requestFocus()
    }

    fun setSeriesAccountsVisibilityAnimated(isVisible: Boolean) {
        val accountsRecyclerView = binding.linLayoutSeriesAccountsMenu
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.constSeries.findViewById<ConstraintLayout>(R.id.const_series))

        if (isVisible) {
            constraintSet.clear(accountsRecyclerView.id, ConstraintSet.END)
            constraintSet.connect(accountsRecyclerView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            binding.rvLayoutSeriesAccountsMenu.isFocusable = true
            binding.rvLayoutSeriesAccountsMenu.isFocusableInTouchMode = true
            helpViewModel.isSeriesAccountMenuOpened = false
            binding.rvLayoutSeriesAccountsMenu.requestFocus()
            showMainMenu()
        } else {
            binding.rvLayoutSeriesAccountsMenu.isFocusable = false
            binding.rvLayoutSeriesAccountsMenu.isFocusableInTouchMode = false
            constraintSet.clear(accountsRecyclerView.id, ConstraintSet.START)
            constraintSet.connect(accountsRecyclerView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.START)
            hideMainMenu()
        }

        val transition = ChangeBounds()
        transition.duration = 100 // Ändere die Dauer nach Bedarf

        TransitionManager.beginDelayedTransition(binding.constSeries.findViewById(R.id.const_series), transition)
        constraintSet.applyTo(binding.constSeries.findViewById(R.id.const_series))
    }

    fun showMainMenu() {
        (requireActivity() as? MainActivity)?.showMenu()
    }

    fun hideMainMenu() {
        (requireActivity() as? MainActivity)?.hideMenu()
    }

    fun selectLastCategory(position: Int, catId: Long) {
        if (!helpViewModel.isSeriesAccountFocused) {
            seriesAccountCategoryAdapter.selectedSeriesCategoryId = catId
            seriesAccountCategoryAdapter.notifyItemChanged(position)
        }
    }

    fun openMainMenu() {
        helpViewModel.isSeriesAccountMenuOpened = false
        (requireActivity() as? MainActivity)?.openMenu()
        (requireActivity() as? MainActivity)?.toggleVisibilityOfMainContainer(false)
        (requireActivity() as? MainActivity)?.lastSelectFocus()
    }


    fun setFocusToSeries() {
        if (seriesAdapter.currentList.isEmpty() && ::stalkerSeriesdapter.isInitialized && stalkerSeriesdapter.snapshot().isEmpty()) {
            Toast.makeText(this@SeriesFragment.requireActivity(), "No series loaded yet!", Toast.LENGTH_SHORT).show()
            setSeriesAccountsVisibilityAnimated(true)
            binding.rvLayoutSeriesAccountsMenu.requestFocus()
        } else {
            if (helpViewModel.currentSeriesAccount!!.isXtream) {
                binding.rvLayoutSeries.requestFocus()
            } else {
                binding.rvLayoutStalkerSeries.requestFocus()
            }
        }
    }

    fun setFocusToSeriesAccount() {
        val currentCat = seriesAccountCategoryAdapter.currentList.firstOrNull { it is AccountSeriesCategory.SeriesCategory && it.id == helpViewModel.currentSeriesCategoryOB?.id }
        val pos = seriesAccountCategoryAdapter.currentList.indexOf(currentCat)

        if (pos != -1 && currentCat is AccountSeriesCategory.SeriesCategory && !helpViewModel.isSeriesAccountFocused) {
            resetSelectedSeriesCategory(pos)

            binding.rvLayoutSeriesAccountsMenu.setSelectedPosition(pos)
            // Sichere RecyclerView-Interaktion im UI-Thread
            binding.rvLayoutSeriesAccountsMenu.post {
                binding.rvLayoutSeriesAccountsMenu.requestFocus()
            }
        } else {
            binding.rvLayoutSeriesAccountsMenu.requestFocus()
        }
    }

    fun resetSelectedSeriesCategory(position: Int) {
        seriesAccountCategoryAdapter.selectedSeriesCategoryId = 0L
        seriesAccountCategoryAdapter.notifyItemChanged(position)
    }

    var firstOpenCategory = true

    fun loadSeriesForCategory(seriesCategoryId: Long) {
        if (helpViewModel.currentSeriesCategoryOB?.id != seriesCategoryId || isFirstOpen) {
            binding.linLayoutSeriesoptions.visibility = View.INVISIBLE
            val thisCategory = seriesCatBox.get(seriesCategoryId)
            binding.linLayoutSeriesoptions.visibility = View.INVISIBLE
            if (thisCategory.seriesaccount.target.isStalker || helpViewModel.currentSeriesCategoryOB?.seriesaccount?.target?.isStalker == true) {
                binding.rvLayoutStalkerSeries.visibility = View.GONE
            } else {
                seriesAdapter.submitList(null)
            }
            firstOpenCategory = true
            isFirstOpen = false
            helpViewModel.currentSeriesImage = null
            binding.loadSeriesProgressBar.visibility = View.VISIBLE
            binding.rvLayoutSeries.visibility = View.GONE
            helpViewModel.currentSeriesCategoryOB = thisCategory
            helpViewModel.playingSerie = null
            val account = thisCategory.seriesaccount.target
            binding.tvSeriesCategoryName.text = ""
            binding.tvSeriesTotalQuantity.text = ""
            binding.tvSeriesQuantity.text = ""
            resetDetailsUi()
            if (account != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                if (account.isStalker) {
                        val sortBy = helpViewModel.currentSeriesCategoryOB?.sortSeriesBy ?: helpViewModel.currentSeriesAccount?.sortSeriesBy ?: helpViewModel.settings?.sortSeriesBy ?: "added"
                        stalkerSeriesdapter.submitData(PagingData.empty())
                        binding.rvLayoutStalkerSeries.visibility = View.VISIBLE
                        stalkerViewModel.getSeriesByCategory(
                            account,
                            thisCategory.seriesCatId,
                            seriesBox,
                            sortBy
                        ).collectLatest {
                            binding.progressBar.visibility = View.GONE
                            binding.rvLayoutStalkerSeries.visibility = View.VISIBLE
                            stalkerSeriesdapter.submitData(it)
                        }
                } else {
                        val series = xtreamViewModel.getSeriesByCategory(
                            account,
                            thisCategory.seriesCatId
                        ).await()
                        if (series.isNotEmpty()) {
                            binding.loadSeriesProgressBar.visibility = View.GONE
                            binding.tvSeriesTotalQuantity.visibility = View.VISIBLE
                            binding.tvSeriesQuantity.visibility = View.VISIBLE
                            binding.tvSeriesCategoryName.visibility = View.VISIBLE
                            binding.tvSeriesTotalQuantity.text = "/ ${series.size}"
                            binding.tvSeriesCategoryName.text = thisCategory.showingName
                            binding.rvLayoutSeries.visibility = View.VISIBLE
                            seriesAdapter.submitList(series)
                            binding.rvLayoutSeries.post {
                                binding.rvLayoutSeries.setSelectedPosition(0)
                                val firstSerie = series.first()
                                updateUi(firstSerie)
                                binding.linLayoutSeriesoptions.visibility = View.VISIBLE
                            }
                        } else {
                            binding.loadSeriesProgressBar.visibility = View.GONE
                            binding.rvLayoutSeries.visibility = View.INVISIBLE
                            binding.rvLayoutSeriesAccountsMenu.requestFocus()
                            Toast.makeText(this@SeriesFragment.requireActivity(), "No series found!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    fun updateSingleSerie() {
        if (helpViewModel.currentSeriesAccount!!.isStalker) {
            val serie = stalkerSeriesdapter.snapshot().items.firstOrNull { it.idByAccountData == helpViewModel.currentFocusedSerie?.idByAccountData }
            if (serie != null) {
                val seriesPosition = stalkerSeriesdapter.snapshot().items.indexOf(serie)
                stalkerSeriesdapter.notifyItemChanged(seriesPosition)
                setDetailsUi(serie)
            }
        } else if (helpViewModel.currentSeriesAccount!!.isXtream) {
            val serie = seriesAdapter.currentList.firstOrNull { it.idByAccountData == helpViewModel.currentFocusedSerie?.idByAccountData }
            if (serie != null) {
                val seriesPosition = seriesAdapter.currentList.indexOf(serie)
                seriesAdapter.notifyItemChanged(seriesPosition)
                setDetailsUi(serie)
            }
        }
    }

    fun openSeriesSearchFragment() {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_series_info, SearchSeriesByCategoryFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.containerSeriesInfo.visibility = View.VISIBLE
        binding.containerSeriesInfo.requestFocus()
    }

    fun openSeriesDetailFragment() {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_series_info, SeriesDetailFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.overlayLayout.visibility = View.VISIBLE
        binding.containerSeriesInfo.visibility = View.VISIBLE
        binding.containerSeriesInfo.requestFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.tvSeriesQuantity.text = ""
        binding.tvSeriesTotalQuantity.text = ""
        binding.tvSeriesQuantity.visibility = View.INVISIBLE
        binding.tvSeriesTotalQuantity.visibility = View.INVISIBLE
        binding.tvSeriesCategoryName.visibility = View.INVISIBLE
        helpViewModel.currentFocusedSerie = null
        parentFragmentManager.popBackStack()
        _binding = null
    }
}