package com.example.mj_player_tv.ui

import android.os.Bundle
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
import com.example.mj_player_tv.database.entity.MovieCategoryOB
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.help.AccountMovieCategory
import com.example.mj_player_tv.databinding.FragmentMoviesBinding
import com.example.mj_player_tv.ui.adapter.MovieAccountCategoryAdapter
import com.example.mj_player_tv.ui.adapter.MoviesAdapter
import com.example.mj_player_tv.ui.adapter.StalkerMoviesAdapter
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.MoviesViewModel
import com.example.mj_player_tv.viewmodel.MoviesViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import com.rubensousa.dpadrecyclerview.FocusableDirection
import com.rubensousa.dpadrecyclerview.ParentAlignment
import com.rubensousa.dpadrecyclerview.spacing.DpadGridSpacingDecoration
import io.objectbox.Box
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@UnstableApi
class MoviesFragment : Fragment(R.layout.fragment_movies) {

    private var _binding: FragmentMoviesBinding? = null

    private val binding get() = _binding!!

    private lateinit var movieAccountCategoryAdapter: MovieAccountCategoryAdapter

    private lateinit var moviesAdapter: MoviesAdapter

    private lateinit var stalkerMoviesAdapter: StalkerMoviesAdapter

    private var beforeSearchList: MutableList<MovieOB> = mutableListOf()

    private var fullAccountList = listOf<AccountMovieCategory>()
    private var expandedAccountId: Long? = null
    private var currentList = listOf<AccountMovieCategory>()

    private var isFirstOpen = true

    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

    private val movieCatBox: Box<MovieCategoryOB> = ObjectBox.store.boxFor(MovieCategoryOB::class.java)

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
        _binding = FragmentMoviesBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareRecyclerView()

        prepareMoviesRecyclerView()

        prepareStalkerMoviesRecyclerView()

        var accountsList = listOf<AccountMovieCategory>()

        helpViewModel.movieAccountsWithCategoriesLiveData.observe(viewLifecycleOwner) { accounts ->
            if (accounts.isEmpty()) {
                if (isFirstOpen) {
                    binding.tvNoMovieCategories.visibility = View.VISIBLE
                    binding.rvLayoutMovieAccountsMenu.visibility = View.INVISIBLE
                    openMainMenu()
                    if (isFirstOpen) {
                        isFirstOpen = false
                    }
                }
            } else {
                binding.tvNoMovieCategories.visibility = View.INVISIBLE
                binding.rvLayoutMovieAccountsMenu.visibility = View.VISIBLE
                fullAccountList = accounts
                if (expandedAccountId != null) {

                    val flatList = mutableListOf<AccountMovieCategory>()
                    fullAccountList.forEach { account ->
                        flatList.add(account)
                        if (account is AccountMovieCategory.Account && account.id == expandedAccountId) {
                            flatList.addAll(account.categories)
                        }
                    }
                    currentList = flatList
                } else {
                    currentList = fullAccountList
                }

                if (isFirstOpen && accountsList != accounts) {
                    accountsList = accounts
                    submitCollapsedMovieList()
                } else {
                    if (accountsList != accounts) {
                        accountsList = accounts
                        movieAccountCategoryAdapter.submitList(currentList)
                    }
                }
            }
        }

        stalkerViewModel.totalMovies.observe(viewLifecycleOwner) { totalMovies ->
            if (totalMovies > 0) {
                binding.tvMovieTotalQuantity.visibility = View.VISIBLE
                binding.tvMovieQuantity.visibility = View.VISIBLE
                binding.tvMovieCategoryName.visibility = View.VISIBLE
                binding.tvMovieCategoryName.text = helpViewModel.currentMovieCategoryOB?.showingName
                binding.tvMovieTotalQuantity.text = "/ ${totalMovies}"
                binding.tvMovieQuantity.text = "1"
            }
        }

        stalkerMoviesAdapter.addLoadStateListener { loadState ->
            val isFirstPageLoaded = loadState.source.refresh is LoadState.NotLoading
            val hasItems = stalkerMoviesAdapter.itemCount > 0
            val isEndOfPagination = loadState.append.endOfPaginationReached && !hasItems

            if (isFirstPageLoaded && hasItems && firstOpenCategory) {
                binding.loadMoviesProgressBar.visibility = View.GONE
                binding.rvLayoutStalkerMovies.setSelectedPosition(0)
                val firstMovie = stalkerMoviesAdapter.snapshot().firstOrNull()
                if (firstMovie != null) {
                    updateUi(firstMovie)
                    binding.linLayoutMovieoptions.visibility = View.VISIBLE
                }
                firstOpenCategory = false
            } else {
                if (isEndOfPagination) {
                    binding.loadMoviesProgressBar.visibility = View.GONE
                    Toast.makeText(this@MoviesFragment.requireActivity(), "No movies found!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val dp30 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 30f, resources.displayMetrics
        ).toInt()

        binding.btnSortmovie.setOnFocusChangeListener { _, hasFocus ->
            val params = binding.btnSortmovie.layoutParams
            if (hasFocus) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.btnSortmovie.layoutParams = params
            } else {
                params.width = dp30
                binding.btnSortmovie.layoutParams = params
            }
        }

        binding.btnSortmovie.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                setMovieAccountsVisibilityAnimated(true)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                if (helpViewModel.currentMovieAccount!!.isStalker) {
                    binding.rvLayoutStalkerMovies.requestFocus()
                } else {
                    binding.rvLayoutMovies.requestFocus()
                }
                return@setOnKeyListener true
            }
            false
        }

        binding.btnSortmovie.setOnClickListener {
            openSortDialog()
        }


        binding.btnSearchmovie.setOnFocusChangeListener { _, hasFocus ->
            val params = binding.btnSearchmovie.layoutParams
            if (hasFocus) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.btnSearchmovie.layoutParams = params
            } else {
                params.width = dp30
                binding.btnSearchmovie.layoutParams = params
            }
        }

        binding.btnSearchmovie.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                setMovieAccountsVisibilityAnimated(true)
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                if (helpViewModel.currentMovieAccount!!.isStalker) {
                    binding.rvLayoutStalkerMovies.requestFocus()
                } else {
                    binding.rvLayoutMovies.requestFocus()
                }
                return@setOnKeyListener true
            }
            false
        }

        binding.btnSearchmovie.setOnClickListener {
            if (helpViewModel.currentMovieAccount!!.isXtream) {
                xtreamViewModel.movieSearchList = moviesAdapter.currentList
            }
            openMovieSearchFragment()
        }

        binding.sortByAdded.setOnClickListener {
            val sortByCurrent = helpViewModel.currentMovieCategoryOB?.sortMoviesBy ?: helpViewModel.currentMovieAccount?.sortMoviesBy ?: helpViewModel.settings?.sortMoviesBy ?: "added"
            if (sortByCurrent != "added") {
                sortMoviesByAdded()
                binding.sortByAdded.requestFocus()
            } else {
                binding.menuSortOptions.visibility = View.GONE
                if (helpViewModel.currentMovieAccount!!.isStalker) {
                    binding.rvLayoutStalkerMovies.requestFocus()
                } else {
                    binding.rvLayoutMovies.requestFocus()
                }
            }
        }

        binding.sortByAdded.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                val slideOut = AnimationUtils.loadAnimation(this@MoviesFragment.requireActivity(), R.anim.slide_out_to_right)
                binding.menuSortOptions.visibility = View.GONE
                binding.menuSortOptions.startAnimation(slideOut)
                if (helpViewModel.currentMovieAccount!!.isStalker) {
                    binding.rvLayoutStalkerMovies.requestFocus()
                } else {
                    binding.rvLayoutMovies.requestFocus()
                }
                return@setOnKeyListener true
            }
            false
        }

        binding.sortByName.setOnClickListener {
            val sortByCurrent = helpViewModel.currentMovieCategoryOB?.sortMoviesBy ?: helpViewModel.currentMovieAccount?.sortMoviesBy ?: helpViewModel.settings?.sortMoviesBy ?: "added"
            if (sortByCurrent != "name") {
                sortMoviesByName()
                binding.sortByName.requestFocus()
            } else {
                binding.menuSortOptions.visibility = View.GONE
                if (helpViewModel.currentMovieAccount!!.isStalker) {
                    binding.rvLayoutStalkerMovies.requestFocus()
                } else {
                    binding.rvLayoutMovies.requestFocus()
                }
            }
        }

        binding.sortByName.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                val slideOut = AnimationUtils.loadAnimation(this@MoviesFragment.requireActivity(), R.anim.slide_out_to_right)
                binding.menuSortOptions.visibility = View.GONE
                binding.menuSortOptions.startAnimation(slideOut)
                if (helpViewModel.currentMovieAccount!!.isStalker) {
                    binding.rvLayoutStalkerMovies.requestFocus()
                } else {
                    binding.rvLayoutMovies.requestFocus()
                }
                return@setOnKeyListener true
            }
            false
        }

        binding.sortByRating.setOnClickListener {
            val sortByCurrent = helpViewModel.currentMovieCategoryOB?.sortMoviesBy ?: helpViewModel.currentMovieAccount?.sortMoviesBy ?: helpViewModel.settings?.sortMoviesBy ?: "added"
            if (sortByCurrent != "rating") {
                sortMoviesByRating()
                binding.sortByRating.requestFocus()
            } else {
                binding.menuSortOptions.visibility = View.GONE
                if (helpViewModel.currentMovieAccount!!.isStalker) {
                    binding.rvLayoutStalkerMovies.requestFocus()
                } else {
                    binding.rvLayoutMovies.requestFocus()
                }
            }
        }

        binding.sortByRating.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                val slideOut = AnimationUtils.loadAnimation(this@MoviesFragment.requireActivity(), R.anim.slide_out_to_right)
                binding.menuSortOptions.visibility = View.GONE
                binding.menuSortOptions.startAnimation(slideOut)
                if (helpViewModel.currentMovieAccount!!.isStalker) {
                    binding.rvLayoutStalkerMovies.requestFocus()
                } else {
                    binding.rvLayoutMovies.requestFocus()
                }
                return@setOnKeyListener true
            }
            false
        }

        moviesViewModel.updateMovieRVRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                updateSingleMovie()
                moviesViewModel.clearUpdateOnMovieRV()
            }
        }

        moviesViewModel.focusToMoviesRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                setFocusToMovies()
                moviesViewModel.clearFocusToMovies()
            }
        }
    }

    private fun prepareRecyclerView() {
        movieAccountCategoryAdapter = MovieAccountCategoryAdapter(::onAccountClicked, { currentList }, helpViewModel, this, onMovieCategoryLongClickListener)
        binding.rvLayoutMovieAccountsMenu.apply {
            adapter = movieAccountCategoryAdapter
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

    private fun prepareMoviesRecyclerView() {
        moviesAdapter = MoviesAdapter(onMovieClickListener, onMovieLongClickListener,this, helpViewModel)
        binding.rvLayoutMovies.apply {
            adapter = moviesAdapter
            addItemDecoration(
                DpadGridSpacingDecoration.create(
                    itemSpacing = 16,
                    edgeSpacing = 7,
                    perpendicularItemSpacing = 14
                )
            )
            setFocusOutAllowed(true, false)
            setFocusOutSideAllowed(true, false)
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private val onMovieCategoryLongClickListener = MovieAccountCategoryAdapter.OnLongClickListener { view, position ->

    }

    private fun prepareStalkerMoviesRecyclerView() {
        stalkerMoviesAdapter = StalkerMoviesAdapter(onStalkerMovieClickListener, onStalkerMovieLongClickListener,this, helpViewModel)
        binding.rvLayoutStalkerMovies.apply {
            adapter = stalkerMoviesAdapter
            addItemDecoration(
                DpadGridSpacingDecoration.create(
                    itemSpacing = 16,
                    edgeSpacing = 7,
                    perpendicularItemSpacing = 14
                )
            )
            ParentAlignment(offset = 0, fraction = 0f)
            ParentAlignment(edge = ParentAlignment.Edge.NONE)
            setFocusableDirection(FocusableDirection.CONTINUOUS)
            setFocusOutAllowed(true, false)
            setFocusOutSideAllowed(true, false)
            setSmoothFocusChangesEnabled(false)
        }
    }

    private fun submitCollapsedMovieList() {
        currentList = fullAccountList
        movieAccountCategoryAdapter.submitList(currentList)
        binding.rvLayoutMovieAccountsMenu.post {
            if (!isAdded || view == null) return@post

            if (isFirstOpen) {
                if (helpViewModel.clickedMovieAccountId != 0L && helpViewModel.clickedMovieAccountPosition != -1) {
                    onAccountClicked(helpViewModel.clickedMovieAccountPosition)
                } else {
                    isFirstOpen = false
                    binding.rvLayoutMovieAccountsMenu.requestFocus()
                }
            } else {
                binding.rvLayoutMovieAccountsMenu.requestFocus()
            }
        }

    }

    private fun onAccountClicked(position: Int) {
        val item = movieAccountCategoryAdapter.currentList[position] as AccountMovieCategory.Account

        if (expandedAccountId == item.id) {
            expandedAccountId = null
            movieAccountCategoryAdapter.selectedMovieCategoryId = 0L
            helpViewModel.clickedMovieAccountId = 0L
            helpViewModel.clickedMovieAccountPosition = -1
            movieAccountCategoryAdapter.notifyItemChanged(position)
            submitCollapsedMovieList()
            binding.rvLayoutMovieAccountsMenu.post {
                binding.rvLayoutMovieAccountsMenu.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
            }
            return
        }
        expandedAccountId = item.id

        val oldAccount = movieAccountCategoryAdapter.currentList.firstOrNull {
            it is AccountMovieCategory.Account && it.id == helpViewModel.clickedMovieAccountId
        } as? AccountMovieCategory.Account
        val oldAccountPosition = movieAccountCategoryAdapter.currentList.indexOf(oldAccount)
        val newAccount = movieAccountCategoryAdapter.currentList.firstOrNull {
            it is AccountMovieCategory.Account && it.id == item.id
        } as? AccountMovieCategory.Account
        val newAccountPosition = movieAccountCategoryAdapter.currentList.indexOf(newAccount)

        movieAccountCategoryAdapter.notifyItemChanged(oldAccountPosition)

        helpViewModel.clickedMovieAccountId = item.id
        helpViewModel.clickedMovieAccountPosition = position

        movieAccountCategoryAdapter.notifyItemChanged(newAccountPosition)

        val flatList = mutableListOf<AccountMovieCategory>()
        fullAccountList.forEach { account ->
            if (account is AccountMovieCategory.Account) {
                flatList.add(account)
                if (account.id == item.id) {
                    flatList.addAll(account.categories)
                }
            }
        }

        currentList = flatList
        movieAccountCategoryAdapter.submitList(flatList) {
            binding.rvLayoutMovieAccountsMenu.post {
                val list = movieAccountCategoryAdapter.currentList
                val clickedAccount = movieAccountCategoryAdapter.currentList.firstOrNull {
                    it is AccountMovieCategory.Account && it.id == item.id
                } as? AccountMovieCategory.Account
                val clickedAccountPosition = movieAccountCategoryAdapter.currentList.indexOf(clickedAccount)
                // Scroll zu Account-Position
                binding.rvLayoutMovieAccountsMenu.setSelectedPosition(clickedAccountPosition)

                // WICHTIG: Stelle sicher, dass die Kategorie darunter aufgebaut wird
                if (position + 1 < list.size && list[position + 1] is AccountMovieCategory.MovieCategory) {
                    binding.rvLayoutMovieAccountsMenu.post {
                        binding.rvLayoutMovieAccountsMenu
                            .findViewHolderForAdapterPosition(clickedAccountPosition)
                    }
                }

                // 👉 FOKUS auf gespeicherte Kategorie (nur beim Wiedereintritt)
                if (isFirstOpen) {
                    val focusedCategoryId = helpViewModel.currentMovieCategoryOB?.id ?: 0L
                    if (focusedCategoryId != 0L) {
                        val categoryPosition = list.indexOfFirst {
                            it is AccountMovieCategory.MovieCategory && it.id == focusedCategoryId
                        }

                        if (categoryPosition != -1) {
                            binding.rvLayoutMovieAccountsMenu.setSelectedPosition(categoryPosition)
                            binding.rvLayoutMovieAccountsMenu.post {
                                binding.rvLayoutMovieAccountsMenu
                                    .findViewHolderForAdapterPosition(categoryPosition)
                                    ?.itemView?.requestFocus()
                            }
                        }
                    }
                }
            }
        }

        if (item.categories.isEmpty()) {
            helpViewModel.clickedMovieAccountId = 0L
            helpViewModel.clickedMovieAccountPosition = -1
            movieAccountCategoryAdapter.notifyItemChanged(position)
            Toast.makeText(this@MoviesFragment.requireActivity(), "No categories enabled!", Toast.LENGTH_SHORT).show()
        }
    }


    private val onStalkerMovieClickListener = StalkerMoviesAdapter.OnClickListener { movie ->
        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.currentFocusedMovie = movie
            openMovieDetailFragment()
        }
    }

    private val onStalkerMovieLongClickListener = StalkerMoviesAdapter.OnLongClickListener { movie, position ->

    }

    private val onMovieClickListener = MoviesAdapter.OnClickListener { movie ->
        viewLifecycleOwner.lifecycleScope.launch {
            helpViewModel.currentFocusedMovie = movie
            openMovieDetailFragment()
        }
    }

    private val onMovieLongClickListener = MoviesAdapter.OnLongClickListener { movie, position ->

    }

    fun updateAccount(accountId: Long) {
        helpViewModel.currentMovieAccount = accountBox.get(accountId)
    }

    private fun openSortDialog() {
        val slideIn = AnimationUtils.loadAnimation(this@MoviesFragment.requireActivity(), R.anim.slide_in_right)
        binding.menuSortOptions.visibility = View.VISIBLE
        binding.menuSortOptions.startAnimation(slideIn)
        val sortByCurrent = helpViewModel.currentMovieCategoryOB?.sortMoviesBy ?: helpViewModel.currentMovieAccount?.sortMoviesBy ?: helpViewModel.settings?.sortMoviesBy ?: "added"
        when (sortByCurrent) {
            "added" -> binding.sortByAdded.requestFocus()
            "name" -> binding.sortByName.requestFocus()
            "rating" -> binding.sortByRating.requestFocus()
        }
    }

    private fun sortMoviesByAdded() {
        resetVisibility()
        helpViewModel.currentMovieCategoryOB?.sortMoviesBy = "added"
        if (helpViewModel.currentMovieAccount!!.isStalker) {
            reloadStalkerMovies("added")
        } else {
            val moviesList = moviesAdapter.currentList.sortedBy { it.added }
            moviesAdapter.submitList(null)
            reloadXtreamMovies(moviesList)
        }
    }

    private fun sortMoviesByName() {
        resetVisibility()
        helpViewModel.currentMovieCategoryOB?.sortMoviesBy = "name"
        if (helpViewModel.currentMovieAccount!!.isStalker) {
            reloadStalkerMovies("name")
        } else {
            val moviesList = moviesAdapter.currentList.sortedBy { it.movieName }
            moviesAdapter.submitList(null)
            reloadXtreamMovies(moviesList)
        }
    }

    private fun sortMoviesByRating() {
        resetVisibility()
        helpViewModel.currentMovieCategoryOB?.sortMoviesBy = "rating"
        if (helpViewModel.currentMovieAccount!!.isStalker) {
            reloadStalkerMovies("rating")
        } else {
            val moviesList = moviesAdapter.currentList.sortedByDescending { it.rating_imdb?.toDoubleOrNull() ?: 0.0 }
            moviesAdapter.submitList(null)
            reloadXtreamMovies(moviesList)
        }
    }

    private fun reloadXtreamMovies(movies: List<MovieOB>) {
        binding.loadMoviesProgressBar.visibility = View.GONE
        binding.tvMovieTotalQuantity.visibility = View.VISIBLE
        binding.tvMovieQuantity.visibility = View.VISIBLE
        binding.tvMovieCategoryName.visibility = View.VISIBLE
        binding.tvMovieTotalQuantity.text = "/ ${movies.size}"
        binding.tvMovieCategoryName.text = helpViewModel.currentMovieCategoryOB!!.showingName
        binding.rvLayoutMovies.visibility = View.VISIBLE
        moviesAdapter.submitList(movies)
        binding.rvLayoutMovies.post {
            binding.rvLayoutMovies.setSelectedPosition(0)
            val firstMovie = movies.first()
            updateUi(firstMovie)
            binding.linLayoutMovieoptions.visibility = View.VISIBLE
            binding.rvLayoutMovies.requestFocus()
        }
    }

    private fun resetVisibility() {
        binding.linLayoutMovieoptions.visibility = View.INVISIBLE
        if (helpViewModel.currentMovieAccount!!.isStalker) {
            binding.rvLayoutStalkerMovies.visibility = View.GONE
        }
        helpViewModel.currentMovieImage = null
        binding.loadMoviesProgressBar.visibility = View.VISIBLE
        binding.rvLayoutMovies.visibility = View.GONE
        binding.tvMovieQuantity.text = ""
        binding.tvRemainingTime.text = ""
        firstOpenCategory = true
        binding.tvMovieCategoryName.text = ""
        binding.tvMovieTotalQuantity.text = ""
        resetDetailsUi()
    }

    private fun reloadStalkerMovies(sortBy: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            stalkerMoviesAdapter.submitData(PagingData.empty()) // Adapter leeren
            stalkerViewModel.getMoviesByCategory(
                helpViewModel.currentMovieAccount!!,
                helpViewModel.currentMovieCategoryOB!!.movieCatId,
                movieBox,
                sortBy
            )
                .collectLatest {
                    binding.progressBar.visibility = View.GONE
                    binding.rvLayoutStalkerMovies.visibility = View.VISIBLE
                    stalkerMoviesAdapter.submitData(it)
                }
        }
    }

    fun updateUi(movie: MovieOB) {
        if (helpViewModel.currentFocusedMovie?.idByAccountData != movie.idByAccountData) {
            helpViewModel.currentFocusedMovie = movie
            resetDetailsUi()
            setDetailsUi(movie)
        }
    }

    var currentTmdbMovieDetailJob: Job? = null

    fun setDetailsUi(movie: MovieOB) {
        val settings = helpViewModel.settings
        if (settings != null) {
            val chPos = if (helpViewModel.currentMovieAccount?.isStalker == true) {
                val currMovie = stalkerMoviesAdapter.snapshot().firstOrNull { it?.idByAccountData == movie.idByAccountData }
                stalkerMoviesAdapter.snapshot().indexOf(currMovie) + 1
            } else {
                val currMovie = moviesAdapter.currentList.firstOrNull { it?.idByAccountData == movie.idByAccountData }
                moviesAdapter.currentList.indexOf(currMovie) + 1
            }
            binding.tvMovieQuantity.text = "$chPos "
            binding.ivFavorite.visibility = if (movie.isFavorite) {
                View.VISIBLE
            } else {
                View.GONE
            }
            setMovieDetailsNotImages(movie)
            currentTmdbMovieDetailJob?.cancel()
            currentTmdbMovieDetailJob = viewLifecycleOwner.lifecycleScope.launch {
                if (helpViewModel.currentMovieAccount!!.isXtream) {
                    if (!xtreamViewModel.modifiedXtreamMovies.contains(movie.idByAccountData)) {
                        val thisMovie = xtreamViewModel.getXtreamMovieDetails(movie, helpViewModel.currentMovieAccount!!)
                        movie.movieTime = thisMovie.movieTime
                        movie.director = thisMovie.director
                        movie.actors = thisMovie.actors
                        movie.description = thisMovie.description
                        movie.age = thisMovie.age
                        movie.country = thisMovie.country
                        movie.genres_str = thisMovie.genres_str
                        movie.backdropPath = thisMovie.backdropPath
                        movie.tmdb_id = thisMovie.tmdb_id.toString()
                        xtreamViewModel.modifiedXtreamMovies.add(movie.idByAccountData)
                    }
                    if (!movie.backdropPath.isNullOrEmpty()) {
                        if (!movie.tmdb_id.isNullOrEmpty() && settings.tmdbApiKey.isNotEmpty()) {
                            if (movie.tmdb_id!!.startsWith("tt")) {
                                val tmdbMovieDetailsByImdbId =
                                    helpViewModel.getTmdbMovieDetailsByImdb(
                                        url = "https://api.themoviedb.org/3/find/",
                                        imdbId = movie.tmdb_id!!,
                                        apiKey = settings.tmdbApiKey
                                    ).await()
                                when (tmdbMovieDetailsByImdbId) {
                                    is Resource.Success -> {
                                        val backgroundImage =
                                            tmdbMovieDetailsByImdbId.data?.movie_results?.first()?.backdrop_path.let { "https://image.tmdb.org/t/p/original$it" }
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(backgroundImage)
                                        helpViewModel.currentMovieImage = backgroundImage
                                    }

                                    is Resource.Error -> {
                                        val moviePoster = movie.screenshot_uri
                                        if (!moviePoster.isNullOrEmpty()) {
                                            binding.ivMovieposter.visibility = View.VISIBLE
                                            binding.ivMovieposter.load(moviePoster)
                                            helpViewModel.currentMovieImage = moviePoster
                                        } else {
                                            helpViewModel.currentMovieImage = ""
                                            binding.ivMovieposter.visibility = View.INVISIBLE
                                        }
                                    }
                                }
                            } else {
                                val tmdbMovieDetails = helpViewModel.getTmdbMovieDetails(
                                    url = "https://api.themoviedb.org/3/movie/",
                                    movieId = movie.tmdb_id!!.toInt(),
                                    apiKey = settings.tmdbApiKey
                                ).await()
                                when (tmdbMovieDetails) {
                                    is Resource.Success -> {
                                        val backgroundImage =
                                            tmdbMovieDetails.data?.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(backgroundImage)
                                        helpViewModel.currentMovieImage = backgroundImage
                                    }

                                    is Resource.Error -> {
                                        val moviePoster = movie.screenshot_uri
                                        if (!moviePoster.isNullOrEmpty()) {
                                            binding.ivMovieposter.visibility = View.VISIBLE
                                            binding.ivMovieposter.load(moviePoster)
                                            helpViewModel.currentMovieImage = moviePoster
                                        } else {
                                            helpViewModel.currentMovieImage = ""
                                            binding.ivMovieposter.visibility = View.INVISIBLE
                                        }
                                    }
                                }
                            }
                        } else {
                            if (!movie.screenshot_uri.isNullOrEmpty()) {
                                binding.ivMovieposter.visibility = View.VISIBLE
                                binding.ivMovieposter.load(movie.screenshot_uri)
                                helpViewModel.currentMovieImage = movie.screenshot_uri
                            } else {
                                helpViewModel.currentMovieImage = ""
                                binding.ivMovieposter.visibility = View.INVISIBLE
                            }
                        }
                    } else {
                        binding.ivMovieposter.visibility = View.VISIBLE
                        binding.ivMovieposter.load(movie.backdropPath)
                        helpViewModel.currentMovieImage = movie.backdropPath
                    }
                    setMovieDetailsNotImages(movie)
                } else {
                    if (movie.tmdb_id!!.isNotEmpty() && settings.tmdbApiKey.isNotEmpty()) {
                        if (movie.tmdb_id!!.startsWith("tt")) {
                            val tmdbMovieDetailsByImdbId = helpViewModel.getTmdbMovieDetailsByImdb(
                                url = "https://api.themoviedb.org/3/find/",
                                imdbId = movie.tmdb_id!!,
                                apiKey = settings.tmdbApiKey
                            ).await()
                            when (tmdbMovieDetailsByImdbId) {
                                is Resource.Success -> {
                                    val backgroundImage =
                                        tmdbMovieDetailsByImdbId.data?.movie_results?.first()?.backdrop_path.let { "https://image.tmdb.org/t/p/original$it" }
                                    binding.ivMovieposter.visibility = View.VISIBLE
                                    binding.ivMovieposter.load(backgroundImage)
                                    helpViewModel.currentMovieImage = backgroundImage
                                }

                                is Resource.Error -> {
                                    val moviePoster = movie.screenshot_uri
                                    if (!moviePoster.isNullOrEmpty()) {
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(moviePoster)
                                        helpViewModel.currentMovieImage = moviePoster
                                    } else {
                                        binding.ivMovieposter.visibility = View.INVISIBLE
                                    }
                                }
                            }
                        } else {
                            val tmdbMovieDetails = helpViewModel.getTmdbMovieDetails(
                                url = "https://api.themoviedb.org/3/movie/",
                                movieId = movie.tmdb_id!!.toInt(),
                                apiKey = settings.tmdbApiKey
                            ).await()
                            when (tmdbMovieDetails) {
                                is Resource.Success -> {
                                    val backgroundImage =
                                        tmdbMovieDetails.data?.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
                                    binding.ivMovieposter.visibility = View.VISIBLE
                                    binding.ivMovieposter.load(backgroundImage)
                                    helpViewModel.currentMovieImage = backgroundImage
                                }

                                is Resource.Error -> {
                                    val moviePoster = movie.screenshot_uri
                                    if (!moviePoster.isNullOrEmpty()) {
                                        binding.ivMovieposter.visibility = View.VISIBLE
                                        binding.ivMovieposter.load(moviePoster)
                                        helpViewModel.currentMovieImage = moviePoster
                                    } else {
                                        binding.ivMovieposter.visibility = View.INVISIBLE
                                    }
                                }
                            }
                        }
                    } else {
                        currentTmdbMovieDetailJob?.cancel()
                        if (!movie.screenshot_uri.isNullOrEmpty()) {
                            binding.ivMovieposter.visibility = View.VISIBLE
                            binding.ivMovieposter.load(movie.screenshot_uri)
                            helpViewModel.currentMovieImage = movie.screenshot_uri
                        } else {
                            binding.ivMovieposter.visibility = View.INVISIBLE
                        }
                    }
                }
            }
        }
    }

    fun setMovieDetailsNotImages(movie: MovieOB) {
        binding.tvMovietitle.text = if (!movie.movieName.isNullOrEmpty()) {
            binding.tvMovietitle.visibility = View.VISIBLE
            movie.movieName
        } else {
            "No Title!"
        }
        binding.tvMovietitle.isSelected = true
        binding.tvDuration.text = if (movie.movieTime != null) {
            binding.tvDuration.visibility = View.VISIBLE
            val durationText = formatDuration(movie.movieTime!!, movie.accountId!!)
            durationText
        } else {
            binding.tvDuration.visibility = View.VISIBLE
            "0min"
        }
        binding.tvReleaseyear.text = if (movie.movieYear.isNotEmpty()) {
            binding.tvReleaseyear.visibility = View.VISIBLE
            val year = if (movie.movieYear.length >= 4) movie.movieYear.substring(0, 4) else "n/a"
            year
        } else {
            binding.tvReleaseyear.visibility = View.VISIBLE
            "n/a"
        }
        binding.tvCategories.text = if (!movie.genres_str.isNullOrEmpty()) {
            binding.tvCategories.visibility = View.VISIBLE
            movie.genres_str
        } else {
            binding.tvCategories.visibility = View.INVISIBLE
            ""
        }
        binding.tvMoviedescription.text = if (!movie.description.isNullOrEmpty()) {
            binding.tvMoviedescription.visibility = View.VISIBLE
            movie.description
        } else {
            "No description available"
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = movie.percentagePlayed.toInt()

        binding.tvRating.text = if (!movie.rating_imdb.isNullOrEmpty()) {
            binding.tvRating.visibility = View.VISIBLE
            val formattedRating = formatRating(movie.rating_imdb)
            formattedRating
        } else {
            binding.tvRating.visibility = View.VISIBLE
            "0.0"
        }
        binding.smallRating.rating = if (!movie.rating_imdb.isNullOrEmpty()) {
            binding.smallRating.visibility = View.VISIBLE
            val formattedRating = formatRating(movie.rating_imdb)
            val ratingValue = formattedRating.toFloatOrNull() ?: 0.0f
            (ratingValue / 2.0f) // Teile die Bewertung durch 2, um sie auf die Skala der 5 Sterne anzupassen
        } else {
            binding.smallRating.visibility = View.VISIBLE
            0.0f // Wenn die Bewertung leer oder null ist, setze die Bewertung auf 0
        }
        binding.tvActor.visibility = View.VISIBLE
        binding.tvActors.text = if (!movie.actors.isNullOrEmpty()) {
            binding.tvActors.visibility = View.VISIBLE
            movie.actors
        } else {
            binding.tvActors.visibility = View.VISIBLE
            "n/a"
        }
        binding.tvDirector.visibility = View.VISIBLE
        binding.tvDirectors.text = if (!movie.director.isNullOrEmpty()) {
            binding.tvDirectors.visibility = View.VISIBLE
            movie.director
        } else {
            binding.tvDirectors.visibility = View.VISIBLE
            "n/a"
        }
        binding.tvAge.text = if (!movie.age.isNullOrEmpty()) {
            binding.tvAge.visibility = View.VISIBLE
            movie.age
        } else {
            binding.tvAge.visibility = View.INVISIBLE
            ""
        }

        if (movie.isPartlyWatched) {
            binding.tvRemainingTime.visibility = View.VISIBLE

            // Prüfe, ob die movieTime in Minuten oder Sekunden ist
            val movieTimeInMinutes = if (helpViewModel.currentMovieAccount!!.isXtream) {
                (movie.movieTime ?: 0) / 60 // Sekunden zu Minuten umrechnen
            } else {
                movie.movieTime ?: 0 // Bereits in Minuten
            }

            // Berechne die verbleibende Zeit
            val remainingTimeMinutes = movieTimeInMinutes - (movieTimeInMinutes * movie.percentagePlayed)

            // Formatierung der verbleibenden Zeit
            val remainingTimeText = if (remainingTimeMinutes < 60) {
                "${remainingTimeMinutes.toInt()}min remaining"
            } else {
                val hours = remainingTimeMinutes.toInt() / 60
                val minutes = remainingTimeMinutes.toInt() % 60
                "${hours}h ${minutes}min remaining"
            }

            binding.tvRemainingTime.text = remainingTimeText
            binding.progressBar.progress = (movie.percentagePlayed * 100).toInt()
        } else if (movie.isCompletelyWatched) {
            binding.tvRemainingTime.visibility = View.VISIBLE
            binding.tvRemainingTime.text = "Completed!"
            binding.progressBar.progress = 100
        } else {
            binding.tvRemainingTime.visibility = View.INVISIBLE
        }
    }

    fun resetDetailsUi() {
        binding.ivFavorite.visibility = View.INVISIBLE
        binding.ivMovieposter.visibility = View.INVISIBLE
        binding.tvMovietitle.visibility = View.INVISIBLE
        binding.tvDuration.visibility = View.INVISIBLE
        binding.tvReleaseyear.visibility = View.INVISIBLE
        binding.tvCategories.visibility = View.INVISIBLE
        binding.tvMoviedescription.visibility = View.INVISIBLE
        binding.progressBar.visibility = View.INVISIBLE
        binding.tvRating.visibility = View.INVISIBLE
        binding.smallRating.visibility = View.INVISIBLE
        binding.tvActor.visibility = View.INVISIBLE
        binding.tvActors.visibility = View.INVISIBLE
        binding.tvDirectors.visibility = View.INVISIBLE
        binding.tvDirector.visibility = View.INVISIBLE
        binding.tvAge.visibility = View.INVISIBLE
    }

    private fun formatDuration(duration: Int, accountId: Long): String {
        val currentAccount = accountBox.get(accountId)

        return when {
            currentAccount.isStalker -> {  // Dauer kommt in MINUTEN
                val hours = duration / 60
                val minutes = duration % 60
                formatTime(hours, minutes)
            }
            currentAccount.isXtream -> {   // Dauer kommt in SEKUNDEN
                val hours = duration / 3600
                val minutes = (duration % 3600) / 60
                formatTime(hours, minutes)
            }
            else -> ""
        }
    }

    // Hilfsfunktion für saubere Formatierung
    private fun formatTime(hours: Int, minutes: Int): String {
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
            hours > 0 -> "${hours}h"
            else -> "${minutes}min"
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
        binding.btnSortmovie.requestFocus()
    }

    fun setMovieAccountsVisibilityAnimated(isVisible: Boolean) {
        val accountsRecyclerView = binding.linLayoutMovieAccountsMenu
        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.constMovies.findViewById<ConstraintLayout>(R.id.const_movies))

        if (isVisible) {
            constraintSet.clear(accountsRecyclerView.id, ConstraintSet.END)
            constraintSet.connect(accountsRecyclerView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            binding.rvLayoutMovieAccountsMenu.isFocusable = true
            binding.rvLayoutMovieAccountsMenu.isFocusableInTouchMode = true
            helpViewModel.isTvCategoryMenuFocused = false
            binding.rvLayoutMovieAccountsMenu.requestFocus()
            showMainMenu()
        } else {
            binding.rvLayoutMovieAccountsMenu.isFocusable = false
            binding.rvLayoutMovieAccountsMenu.isFocusableInTouchMode = false
            constraintSet.clear(accountsRecyclerView.id, ConstraintSet.START)
            constraintSet.connect(accountsRecyclerView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.START)
            hideMainMenu()
        }

        val transition = ChangeBounds()
        transition.duration = 100 // Ändere die Dauer nach Bedarf

        TransitionManager.beginDelayedTransition(binding.constMovies.findViewById(R.id.const_movies), transition)
        constraintSet.applyTo(binding.constMovies.findViewById(R.id.const_movies))
    }

    fun showMainMenu() {
        (requireActivity() as? MainActivity)?.showMenu()
    }

    fun hideMainMenu() {
        (requireActivity() as? MainActivity)?.hideMenu()
    }

    fun selectLastCategory(position: Int, catId: Long) {
        if (!helpViewModel.isMovieAccountFocused) {
            movieAccountCategoryAdapter.selectedMovieCategoryId = catId
            movieAccountCategoryAdapter.notifyItemChanged(position)
        }
    }

    fun openMainMenu() {
        helpViewModel.isMovieAccountMenuOpened = false
        (requireActivity() as? MainActivity)?.openMenu()
        (requireActivity() as? MainActivity)?.toggleVisibilityOfMainContainer(false)
        (requireActivity() as? MainActivity)?.lastSelectFocus()
    }

    fun setFocusToMovies() {
        if (moviesAdapter.currentList.isEmpty() && ::stalkerMoviesAdapter.isInitialized && stalkerMoviesAdapter.snapshot().isEmpty()) {
            Toast.makeText(this@MoviesFragment.requireActivity(), "No movies loaded yet!", Toast.LENGTH_SHORT).show()
            setMovieAccountsVisibilityAnimated(true)
            binding.rvLayoutMovieAccountsMenu.requestFocus()
        } else {
            if (helpViewModel.currentMovieAccount!!.isXtream) {
                binding.rvLayoutMovies.requestFocus()
            } else {
                binding.rvLayoutStalkerMovies.requestFocus()
            }
        }
    }

    fun resetSelectedMovieCategory(position: Int) {
        movieAccountCategoryAdapter.selectedMovieCategoryId = 0L
        movieAccountCategoryAdapter.notifyItemChanged(position)
    }

    fun setFocusToMoviesAccount() {
        val currentCat = movieAccountCategoryAdapter.currentList
            .firstOrNull { it is AccountMovieCategory.MovieCategory && it.id == helpViewModel.currentMovieCategoryOB?.id }

        val pos = movieAccountCategoryAdapter.currentList.indexOf(currentCat)

        if (pos != -1 && currentCat is AccountMovieCategory.MovieCategory && !helpViewModel.isMovieAccountFocused) {
            resetSelectedMovieCategory(pos)

            binding.rvLayoutMovieAccountsMenu.setSelectedPosition(pos)
            // Sichere RecyclerView-Interaktion im UI-Thread
            binding.rvLayoutMovieAccountsMenu.post {
                binding.rvLayoutMovieAccountsMenu.requestFocus()
            }
        } else {
            binding.rvLayoutMovieAccountsMenu.requestFocus()
        }
    }


    var firstOpenCategory = true

    fun loadMoviesForCategory(movieCategoryId: Long) {
        if (helpViewModel.currentMovieCategoryOB?.id != movieCategoryId || isFirstOpen) {
            val thisMovieCategory = movieCatBox.get(movieCategoryId)
            binding.linLayoutMovieoptions.visibility = View.INVISIBLE
            if (thisMovieCategory.movieaccount.target.isStalker || helpViewModel.currentMovieCategoryOB?.movieaccount?.target?.isStalker == true) {
                binding.rvLayoutStalkerMovies.visibility = View.GONE
            } else {
                binding.rvLayoutMovies.visibility = View.GONE
                moviesAdapter.submitList(null)
            }
            firstOpenCategory = true
            isFirstOpen = false
            helpViewModel.currentMovieImage = null
            binding.loadMoviesProgressBar.visibility = View.VISIBLE
            binding.rvLayoutMovies.visibility = View.GONE
            binding.tvMovieCategoryName.text = ""
            binding.tvMovieTotalQuantity.text = ""
            binding.tvMovieQuantity.text = ""
            binding.tvRemainingTime.text = ""
            helpViewModel.currentMovieCategoryOB = thisMovieCategory
            val account = thisMovieCategory.movieaccount.target
            resetDetailsUi()
            if (account != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (account.isStalker) {
                        val sortBy = helpViewModel.currentMovieCategoryOB?.sortMoviesBy ?: account.sortMoviesBy ?: helpViewModel.settings?.sortMoviesBy ?: "added"
                        stalkerMoviesAdapter.submitData(PagingData.empty()) // Adapter leeren
                        stalkerViewModel.getMoviesByCategory(account, thisMovieCategory.movieCatId, movieBox, sortBy)
                            .collectLatest {
                                binding.progressBar.visibility = View.GONE
                                binding.rvLayoutStalkerMovies.visibility = View.VISIBLE
                                stalkerMoviesAdapter.submitData(it)
                            }
                    } else {
                        xtreamViewModel.modifiedXtreamMovies = mutableListOf()
                        val movies = xtreamViewModel.getMoviesByCategory(
                            account,
                            thisMovieCategory.movieCatId
                        ).await()
                        if (movies.isNotEmpty()) {
                            binding.loadMoviesProgressBar.visibility = View.GONE
                            binding.tvMovieTotalQuantity.visibility = View.VISIBLE
                            binding.tvMovieQuantity.visibility = View.VISIBLE
                            binding.tvMovieCategoryName.visibility = View.VISIBLE
                            binding.tvMovieTotalQuantity.text = "/ ${movies.size}"
                            binding.tvMovieCategoryName.text = thisMovieCategory.showingName
                            binding.rvLayoutMovies.visibility = View.VISIBLE
                            moviesAdapter.submitList(movies)
                            binding.rvLayoutMovies.post {
                                binding.rvLayoutMovies.setSelectedPosition(0)
                                val firstMovie = movies.first()
                                updateUi(firstMovie)
                                binding.linLayoutMovieoptions.visibility = View.VISIBLE
                            }
                        } else {
                            binding.loadMoviesProgressBar.visibility = View.GONE
                            binding.rvLayoutMovies.visibility = View.INVISIBLE
                            binding.rvLayoutMovieAccountsMenu.requestFocus()
                            Toast.makeText(this@MoviesFragment.requireActivity(), "No movies found!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    fun updatePlayingMovie(movieOB: MovieOB) {
        if (helpViewModel.currentMovieAccount!!.isStalker) {
            val movie = stalkerMoviesAdapter.snapshot().items.firstOrNull { it.idByAccountData == movieOB.idByAccountData }
            if (movie != null) {
                movie.movieTime = movieOB.movieTime
                movie.currentPosition = movieOB.currentPosition
                movie.percentagePlayed = movieOB.percentagePlayed
                movie.isPartlyWatched = movieOB.isPartlyWatched
                movie.isCompletelyWatched = movieOB.isCompletelyWatched
                val moviePosition = stalkerMoviesAdapter.snapshot().items.indexOf(movie)
                stalkerMoviesAdapter.notifyItemChanged(moviePosition)
                setMovieDetailsNotImages(movie)
            }
        } else if (helpViewModel.currentMovieAccount!!.isXtream) {
            val movie = moviesAdapter.currentList.firstOrNull { it.idByAccountData == movieOB.idByAccountData }
            if (movie != null) {
                movie.movieTime = movieOB.movieTime
                movie.currentPosition = movieOB.currentPosition
                movie.percentagePlayed = movieOB.percentagePlayed
                movie.isPartlyWatched = movieOB.isPartlyWatched
                movie.isCompletelyWatched = movieOB.isCompletelyWatched
                val moviePosition = moviesAdapter.currentList.indexOf(movie)
                moviesAdapter.notifyItemChanged(moviePosition)
                setMovieDetailsNotImages(movie)
            }
        } else {

        }
    }

    fun updateSingleMovie() {
        val movie = helpViewModel.currentFocusedMovie
        if (movie != null) {
            binding.ivFavorite.visibility = if (movie.isFavorite) {
                View.VISIBLE
            } else {
                View.GONE
            }
            if (helpViewModel.currentMovieAccount!!.isStalker) {
                val moviePosition = stalkerMoviesAdapter.snapshot().items.indexOf(movie)
                stalkerMoviesAdapter.notifyItemChanged(moviePosition)
                setMovieDetailsNotImages(movie)
            } else if (helpViewModel.currentMovieAccount!!.isXtream) {
                val moviePosition = moviesAdapter.currentList.indexOf(movie)
                moviesAdapter.notifyItemChanged(moviePosition)
                setMovieDetailsNotImages(movie)
            } else {

            }
        }
    }

    fun setFullVisibility() {
        binding.overlayLayout.visibility = View.GONE
        if (helpViewModel.currentMovieAccount!!.isStalker) {
            binding.rvLayoutStalkerMovies.requestFocus()
        } else {
            binding.rvLayoutMovies.requestFocus()
        }
    }

    fun openMovieDetailFragment() {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_movie_info, MovieDetailFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.containerMovieInfo.visibility = View.VISIBLE
        binding.containerMovieInfo.requestFocus()
    }

    fun openMovieSearchFragment() {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_movie_info, SearchMovieByCategoryFragment())
        transaction.addToBackStack(null)
        transaction.commit()
        binding.containerMovieInfo.visibility = View.VISIBLE
        binding.containerMovieInfo.requestFocus()
    }

    fun resetNameAndQuantity() {
        binding.tvMovieQuantity.text = ""
        binding.tvMovieTotalQuantity.text = ""
        binding.tvMovieCategoryName.text = ""
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.tvMovieQuantity.text = ""
        binding.tvMovieTotalQuantity.text = ""
        binding.tvMovieQuantity.visibility = View.INVISIBLE
        binding.tvMovieTotalQuantity.visibility = View.INVISIBLE
        binding.tvMovieCategoryName.visibility = View.INVISIBLE
        helpViewModel.currentFocusedMovie = null
        parentFragmentManager.popBackStack()
        _binding = null
    }
}