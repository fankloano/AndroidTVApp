package com.example.mj_player_tv.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.transition.TransitionManager
import coil.load
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.AudioCategoryOB
import com.example.mj_player_tv.database.entity.MovieCategoryOB
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.MovieOB_
import com.example.mj_player_tv.database.entity.PlexCategoryOB
import com.example.mj_player_tv.database.entity.PlexCategoryOB_
import com.example.mj_player_tv.database.entity.SeriesCategoryOB
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.database.entity.SeriesOB_
import com.example.mj_player_tv.database.help.AccountPlexCategory
import com.example.mj_player_tv.databinding.FragmentPlexBinding
import com.example.mj_player_tv.network.model.plex.items.Metadata
import com.example.mj_player_tv.ui.adapter.PlexAccountCategoryAdapter
import com.example.mj_player_tv.ui.adapter.PlexAccountsAdapter
import com.example.mj_player_tv.ui.adapter.PlexItemAdapter
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.PlexViewModel
import com.example.mj_player_tv.viewmodel.PlexViewModelFactory
import com.rubensousa.dpadrecyclerview.spacing.DpadGridSpacingDecoration
import io.objectbox.Box
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


@UnstableApi
class PlexFragment : Fragment(R.layout.fragment_plex) {

    private var _binding: FragmentPlexBinding? = null

    private val binding get() = _binding!!

    private lateinit var plexAccountsAdapter: PlexAccountsAdapter

    private lateinit var plexCategoriesAdapter: PlexAccountCategoryAdapter

    private lateinit var plexItemAdapter: PlexItemAdapter

    private val movieCatBox: Box<MovieCategoryOB> = ObjectBox.store.boxFor(MovieCategoryOB::class.java)
    private val movieBox: Box<MovieOB> = ObjectBox.store.boxFor(MovieOB::class.java)
    private val seriesCatBox: Box<SeriesCategoryOB> = ObjectBox.store.boxFor(SeriesCategoryOB::class.java)
    private val seriesBox: Box<SeriesOB> = ObjectBox.store.boxFor(SeriesOB::class.java)
    private val audioCatBox: Box<AudioCategoryOB> = ObjectBox.store.boxFor(AudioCategoryOB::class.java)
    private val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)
    private val plexCatBox: Box<PlexCategoryOB> = ObjectBox.store.boxFor(PlexCategoryOB::class.java)

    var firstOpen = true

    var firstOpenCategory = true

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlexBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prepareAccountsRecyclerView()
        preparePlexCategoryRecyclerView()
        preparePlexItemRecyclerView()

        helpViewModel.plexAccountsLiveData.observe(viewLifecycleOwner) { accounts ->
            if (accounts.isEmpty()) {
                binding.rvLayoutPlexAccountsMenu.visibility = View.INVISIBLE
                openMainMenu()
            } else {
                binding.rvLayoutPlexAccountsMenu.visibility = View.VISIBLE
                plexAccountsAdapter.submitList(accounts)
                if (helpViewModel.isPlexAccountMenuOpened || firstOpen) {
                    binding.rvLayoutPlexAccountsMenu.requestFocus()
                    if (firstOpen) {
                        firstOpen = false
                    }
                }
            }
        }

        plexItemAdapter.addLoadStateListener { loadState ->
            val isFirstPageLoaded = loadState.source.refresh is LoadState.NotLoading
            val hasItems = plexItemAdapter.itemCount > 0
            val isEndOfPagination = loadState.append.endOfPaginationReached && !hasItems

            if (isFirstPageLoaded && hasItems && firstOpenCategory) {
                binding.loadItemsProgressBar.visibility = View.INVISIBLE
                binding.rvLayoutPlexItems.setSelectedPosition(0)
                val firstMovie = plexItemAdapter.snapshot().firstOrNull()
                if (firstMovie != null) {
                    updateUi(firstMovie, 1)
                }
                firstOpenCategory = false
            } else {
                if (isEndOfPagination) {
                    Toast.makeText(this@PlexFragment.requireActivity(), "No items found!", Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

    private fun prepareAccountsRecyclerView() {
        plexAccountsAdapter = PlexAccountsAdapter(helpViewModel, this)
        binding.rvLayoutPlexAccountsMenu.apply {
            adapter = plexAccountsAdapter
            setHasFixedSize(false)
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(true, true)
        }
    }

    private fun preparePlexCategoryRecyclerView() {
        plexCategoriesAdapter = PlexAccountCategoryAdapter (this)
        binding.rvLayoutPlexCategoriesMenu.apply {
            adapter = plexCategoriesAdapter
            setHasFixedSize(false)
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(true, true)
        }
    }

    fun showCategories(account: Accounts) {
        if (helpViewModel.currentPlexAccount?.id != account.id) {
            binding.tvAllAccounts.text = account.name
            helpViewModel.currentPlexAccount = account
            val categoriesQuery = plexCatBox.query(
                PlexCategoryOB_.playlistId.equal(account.id).and(PlexCategoryOB_.favorite.equal(true))
            ).build()
            val categories = categoriesQuery.find()
            categoriesQuery.close()
            val groupedCategories = groupCategoriesByType(categories)
            plexCategoriesAdapter.submitList(groupedCategories)
        }
    }

    fun groupCategoriesByType(categories: List<PlexCategoryOB>): List<AccountPlexCategory> {
        val result = mutableListOf<AccountPlexCategory>()

        val movies = categories.filter { it.isMovie && !it.isAudio }
        val series = categories.filter { !it.isMovie && !it.isAudio }
        val audio = categories.filter { it.isAudio }

        if (movies.isNotEmpty()) {
            result.add(AccountPlexCategory.Header("Movie"))
            result.addAll(movies.map {
                AccountPlexCategory.PlexCategory(
                    id = it.id,
                    name = it.title,
                    parentId = it.playlistId ?: 0L,
                    plexCategoryId = it.plexCatId,
                    isMovie = it.isMovie,
                    isAudio = it.isAudio,
                    isFavoriteCategory = it.favorite
                )
            })
        }

        if (series.isNotEmpty()) {
            result.add(AccountPlexCategory.Header("Series"))
            result.addAll(series.map {
                AccountPlexCategory.PlexCategory(
                    id = it.id,
                    name = it.title,
                    parentId = it.playlistId ?: 0L,
                    plexCategoryId = it.plexCatId,
                    isMovie = it.isMovie,
                    isAudio = it.isAudio,
                    isFavoriteCategory = it.favorite
                )
            })
        }

        if (audio.isNotEmpty()) {
            result.add(AccountPlexCategory.Header("Audio"))
            result.addAll(audio.map {
                AccountPlexCategory.PlexCategory(
                    id = it.id,
                    name = it.title,
                    parentId = it.playlistId ?: 0L,
                    plexCategoryId = it.plexCatId,
                    isMovie = it.isMovie,
                    isAudio = it.isAudio,
                    isFavoriteCategory = it.favorite
                )
            })
        }

        return result
    }

    fun loadPlexCategoryItems(category: AccountPlexCategory.PlexCategory) {
        if (helpViewModel.clickedPlexCategoryId != category.id) {
            helpViewModel.currentPlexItemId = ""
            viewLifecycleOwner.lifecycleScope.launch {
                helpViewModel.currentPlexAccount?.let { account ->
                    plexItemAdapter.submitData(PagingData.empty())
                    firstOpenCategory = true
                    plexViewModel.getItemBySection(account, category.plexCategoryId.toInt())
                        .collectLatest {
                            binding.rvLayoutPlexItems.visibility = View.VISIBLE
                            plexItemAdapter.submitData(it)
                        }
                }
                when (category.isMovie) {
                    true -> {
                        helpViewModel.currentMovieAccount = accountBox.get(category.parentId)
                    }

                    false -> {
                        if (category.isAudio) {
                            helpViewModel.currentAudioAccount = accountBox.get(category.parentId)
                        } else {
                            helpViewModel.currentSeriesAccount = accountBox.get(category.parentId)
                        }
                    }
                }
            }
            // Optional: ViewModel-Update
            helpViewModel.clickedPlexCategoryId = category.id
        }
    }

    private fun preparePlexItemRecyclerView() {
        plexItemAdapter = PlexItemAdapter(onPlexItemClickListener, onPlexItemLongClickListener,this, helpViewModel)
        binding.rvLayoutPlexItems.apply {
            adapter = plexItemAdapter
            addItemDecoration(
                DpadGridSpacingDecoration.create(
                    itemSpacing = 16,
                    edgeSpacing = 7,
                    perpendicularItemSpacing = 14
                )
            )
            setFocusOutAllowed(false, false)
            setFocusOutSideAllowed(true, true)
        }
    }

    private val onPlexItemClickListener = PlexItemAdapter.OnClickListener { item ->
        if (item.type == "movie") {
            checkMovie(item)
        } else if (item.type == "show") {
            checkSerie(item)
        }
    }

    private val onPlexItemLongClickListener = PlexItemAdapter.OnLongClickListener { item, position ->

    }

    fun updateUi(item: Metadata, position: Int) {

        helpViewModel.currentPlexItemId = item.guid ?: ""
        binding.tvPlextitle.text = if (!item.title.isNullOrEmpty()) {
            binding.tvPlextitle.visibility = View.VISIBLE
            item.title
        } else {
            "No Title!"
        }
        binding.ivFavorite.visibility = if (item.isFavorite == true) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
        binding.tvItemQuantity.text = position.toString()
        val backgroundImage = item.Image?.firstOrNull { it.type == "background" }?.url

        if (backgroundImage != null) {
            binding.ivPlexposter.visibility = View.VISIBLE
            val imageUrl = "${helpViewModel.currentPlexAccount?.stalkerUrl}$backgroundImage?X-Plex-Token=${helpViewModel.currentPlexAccount?.token}"
            binding.ivPlexposter.load(imageUrl)

        } else {
            if (item.Image != null) {
                val otherImage = item.Image.firstOrNull()?.url
                if (otherImage != null) {
                    val imageUrl = "${helpViewModel.currentPlexAccount?.stalkerUrl}$otherImage?X-Plex-Token=${helpViewModel.currentPlexAccount?.token}"
                    binding.ivPlexposter.load(imageUrl)
                } else {
                    binding.ivPlexposter.visibility = View.INVISIBLE
                }
            } else {
                binding.ivPlexposter.visibility = View.INVISIBLE
            }
        }
        binding.tvPlextitle.isSelected = true
        binding.tvDuration.text = if (item.duration != 0L) {
            binding.tvDuration.visibility = View.VISIBLE
            val durationText = formatDuration(item.duration!!)
            durationText
        } else {
            binding.tvDuration.visibility = View.VISIBLE
            "0min"
        }
        binding.tvReleaseyear.text = if (item.year != 0) {
            binding.tvReleaseyear.visibility = View.VISIBLE
            item.year.toString()
        } else {
            binding.tvReleaseyear.visibility = View.VISIBLE
            "n/a"
        }

        binding.tvCategories.text = if (!item.Genre.isNullOrEmpty()) {
            binding.tvCategories.visibility = View.VISIBLE
            val genres = item.Genre.map {
                it.tag
            }
            genres.joinToString(", ")
        } else {
            binding.tvCategories.visibility = View.INVISIBLE
            ""
        }
        binding.tvMoviedescription.text = if (!item.summary.isNullOrEmpty()) {
            binding.tvMoviedescription.visibility = View.VISIBLE
            item.summary
        } else {
            binding.tvMoviedescription.visibility = View.VISIBLE
            "No description available"
        }

        val percentWatched: Int = when {
            item.viewCount != null && item.viewCount!! > 0 -> 100
            item.viewOffset != null && item.duration > 0 -> ((item.viewOffset!!.toDouble() / item.duration) * 100).toInt()
            else -> 0
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = percentWatched

        binding.tvRating.text = if (item.audienceRating != null) {
            binding.tvRating.visibility = View.VISIBLE
            item.audienceRating.toString()
        } else {
            binding.tvRating.visibility = View.VISIBLE
            "0.0"
        }

        binding.smallRating.rating = if (item.audienceRating != null) {
            binding.smallRating.visibility = View.VISIBLE
            (item.audienceRating / 2.0).toFloat() // IMDb ist auf 10er-Skala → 5er-Skala umrechnen
        } else {
            binding.smallRating.visibility = View.VISIBLE
            0.0f
        }

        binding.tvActor.visibility = View.VISIBLE
        binding.tvActors.text = if (!item.Role.isNullOrEmpty()) {
            binding.tvActors.visibility = View.VISIBLE
            val roles = item.Role.map {
                it.tag
            }
            roles.joinToString(", ")
        } else {
            binding.tvActors.visibility = View.VISIBLE
            "n/a"
        }
        binding.tvDirector.visibility = View.VISIBLE
        binding.tvDirectors.text = if (!item.Director.isNullOrEmpty()) {
            binding.tvDirectors.visibility = View.VISIBLE
            val directors = item.Director.map {
                it.tag
            }
            directors.joinToString(", ")
        } else {
            binding.tvDirectors.visibility = View.VISIBLE
            "n/a"
        }
        binding.tvAge.text = if (!item.contentRating.isNullOrEmpty()) {
            binding.tvAge.visibility = View.VISIBLE
            item.contentRating
        } else {
            binding.tvAge.visibility = View.GONE
            ""
        }

        if (item.type == "show") {
            binding.tvTotSeasons.text = if (item.childCount != 0) {
                binding.tvTotSeasons.visibility = View.VISIBLE
                if (item.childCount == 1) {
                    "1 Season"
                } else {
                    "${item.childCount} Seasons"
                }
            } else {
                binding.tvTotSeasons.visibility = View.GONE
                ""
            }
        } else {
            binding.tvTotSeasons.visibility = View.GONE
        }
        if (item.type == "movie") {
            if (item.viewCount != 1 && item.viewOffset != null && item.viewOffset != 0L) {
                binding.tvRemainingTime.visibility = View.VISIBLE

                // Prüfe, ob die movieTime in Minuten oder Sekunden ist
                val remainingMillis = if (item.viewOffset != null && item.duration > 0) {
                    item.duration - item.viewOffset!!
                } else {
                    item.duration
                }

                // Berechne die verbleibende Zeit
                binding.tvRemainingTime.text = "${formatDuration(remainingMillis)} remaining"

                binding.progressBar.progress = (percentWatched * 100).toInt()
            } else if (item.viewCount == 1) {
                binding.tvRemainingTime.visibility = View.VISIBLE
                binding.tvRemainingTime.text = "Completed!"
                binding.progressBar.progress = 100
            } else {
                binding.tvRemainingTime.visibility = View.INVISIBLE
            }
        } else if (item.type == "show") {
            if (item.viewedLeafCount == item.leafCount) {
                binding.progressBar.progress = 100
                binding.tvRemainingTime.visibility = View.VISIBLE
                binding.tvRemainingTime.text = "Completed!"
            } else {
                binding.tvRemainingTime.visibility = View.INVISIBLE
            }
        }
    }

    fun resetDetailsUi() {
        binding.ivFavorite.visibility = View.INVISIBLE
        binding.ivPlexposter.visibility = View.INVISIBLE
        binding.tvPlextitle.visibility = View.INVISIBLE
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

    fun formatDuration(durationMs: Long): String {
        val totalMinutes = durationMs / 1000 / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

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

    fun showAccountName() {
        binding.tvAllAccounts.visibility = View.VISIBLE
    }

    fun hideAccountName() {
        binding.tvAllAccounts.visibility = View.INVISIBLE
    }

    fun hideAccountMenu() {
        hideMainMenu()
        helpViewModel.isPlexAccountMenuOpened = false
        val constraintLayout = binding.constPlex

        // Neues ConstraintSet auf Basis des aktuellen Layouts
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        constraintSet.clear(binding.linLayoutPlexAccountsMenu.id, ConstraintSet.START)
        constraintSet.connect(binding.linLayoutPlexAccountsMenu.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.START)
        // Optional: Wenn du ganz sicher sein willst, dass es "frei hängt", auch END zu irgendwas anderem clearen
        // Übergang animieren
        TransitionManager.beginDelayedTransition(constraintLayout)
        constraintSet.applyTo(constraintLayout)

        if (plexCategoriesAdapter.currentList.isNotEmpty()) {
            binding.rvLayoutPlexCategoriesMenu.requestFocus()
        } else {
            binding.rvLayoutPlexAccountsMenu.requestFocus()
        }
    }

    fun showAccountMenu() {
        showMainMenu()
        val constraintLayout = binding.constPlex
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)
        constraintSet.clear(R.id.linLayout_plexAccounts_menu, ConstraintSet.END)
        constraintSet.connect(
            R.id.linLayout_plexAccounts_menu,
            ConstraintSet.START,
            ConstraintSet.PARENT_ID,
            ConstraintSet.START
        )

        TransitionManager.beginDelayedTransition(constraintLayout)
        constraintSet.applyTo(constraintLayout)

        binding.rvLayoutPlexAccountsMenu.requestFocus()
    }

    fun hideCategories() {
        if (plexItemAdapter.snapshot().isNotEmpty()) {
            val constraintLayout = binding.constPlex
            val constraintSet = ConstraintSet()
            constraintSet.clone(constraintLayout)

            // Kategorien-Menü ausblenden (nach rechts raus)
            constraintSet.clear(binding.linLayoutPlexCategoriesMenu.id, ConstraintSet.START)
            constraintSet.connect(
                binding.linLayoutPlexCategoriesMenu.id,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )

            TransitionManager.beginDelayedTransition(constraintLayout)
            constraintSet.applyTo(constraintLayout)

            // Fokus ggf. zurück auf Account-Menü setzen
            binding.rvLayoutPlexItems.requestFocus()
        } else {
            binding.rvLayoutPlexCategoriesMenu.requestFocus()
        }
    }


    fun showCategories() {

        val constraintLayout = binding.constPlex
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        // Kategorien-Menü rechts neben dem Account-Menü einblenden
        constraintSet.clear(binding.linLayoutPlexCategoriesMenu.id, ConstraintSet.END)
        constraintSet.connect(
            binding.linLayoutPlexCategoriesMenu.id,
            ConstraintSet.START,
            binding.linLayoutPlexAccountsMenu.id,
            ConstraintSet.END
        )

        TransitionManager.beginDelayedTransition(constraintLayout)
        constraintSet.applyTo(constraintLayout)

        // Fokus ggf. auf Kategorien setzen
        if (plexCategoriesAdapter.currentList.isNotEmpty()) {
            binding.rvLayoutPlexCategoriesMenu.requestFocus()
        }
    }



    fun setFocusToPlexAccounts() {
        hideAccountName()
        binding.rvLayoutPlexAccountsMenu.requestFocus()
    }

    fun setFocusToPlexCategories() {
        showAccountName()
        binding.rvLayoutPlexCategoriesMenu.requestFocus()
    }

    fun setFocusToPlexItems() {
        if (plexItemAdapter.snapshot().isEmpty()) {
            binding.rvLayoutPlexCategoriesMenu.requestFocus()
        } else {
            binding.rvLayoutPlexItems.requestFocus()
        }
    }

    fun showMainMenu() {
        (requireActivity() as? MainActivity)?.showMenu()
    }

    fun openMainMenu() {
        helpViewModel.isPlexAccountMenuOpened = false
        (requireActivity() as? MainActivity)?.openMenu()
        (requireActivity() as? MainActivity)?.toggleVisibilityOfMainContainer(false)
        (requireActivity() as? MainActivity)?.lastSelectFocus()
    }

    fun hideMainMenu() {
        (requireActivity() as? MainActivity)?.hideMenu()
    }

    fun updateSingleMovie(movie: MovieOB) {
        binding.ivFavorite.visibility = if (movie.isFavorite) {
            View.VISIBLE
        } else {
            View.GONE
        }
        val moviePosition = plexItemAdapter.snapshot().items.indexOf(helpViewModel.clickedPlexMovieItem)
        plexItemAdapter.notifyItemChanged(moviePosition)
        helpViewModel.clickedPlexMovieItem?.let { updateUi(it, moviePosition) }

    }

    private fun checkMovie(item: Metadata) {
        if (item.type == "movie") {
            val inDatabase = movieBox.query(
                MovieOB_.idByAccountData.equal("${item.ratingKey}_${helpViewModel.currentPlexAccount?.id}")
            ).build().findFirst()
            helpViewModel.currentMovieAccount = helpViewModel.currentPlexAccount
            if (inDatabase != null) {
                helpViewModel.currentFocusedMovie = inDatabase
                helpViewModel.clickedPlexMovieItem = item
                openItemDetailFragment(MovieDetailFragment())
            } else {
                helpViewModel.currentFocusedMovie = convertToMovie(item)
                openItemDetailFragment(MovieDetailFragment())
            }
        }
    }

    private fun checkSerie(item: Metadata) {
        if (item.type == "show") {
            val inDatabase = seriesBox.query(
                SeriesOB_.idByAccountData.equal("${item.ratingKey}_${helpViewModel.currentPlexAccount?.id}")
            ).build().findFirst()
            helpViewModel.currentSeriesAccount = helpViewModel.currentPlexAccount
            if (inDatabase != null) {
                helpViewModel.currentFocusedSerie = inDatabase
                helpViewModel.clickedPlexMovieItem = item
                openItemDetailFragment(SeriesDetailFragment())
            } else {
               //TODO Converttoserie, opendetailfragment
            }
        }
    }

    private fun convertToMovie(itemData: Metadata): MovieOB {
        helpViewModel.clickedPlexMovieItem = itemData
        val cmd = "${helpViewModel.currentPlexAccount?.stalkerUrl}${itemData.Media?.first()?.Part?.first()?.key}?X-Plex-Token=${helpViewModel.currentPlexAccount?.token}"
        val coverPoster = itemData.Image?.firstOrNull { it.type == "coverPoster" }?.url
        val background = itemData.Image?.firstOrNull { it.type == "background" }?.url
        val genres = itemData.Genre?.joinToString(", ") {
            it.tag
        }
        val actors = itemData.Role?.joinToString(", ") {
            it.tag
        }
        val directors = itemData.Director?.joinToString(", ") {
            it.tag
        }
        val percentWatched: Double = when {
            itemData.viewCount != null && itemData.viewCount != 0 -> 1.0
            itemData.viewOffset != null && itemData.duration != 0L && itemData.duration != null -> itemData.viewOffset!!.toDouble() / itemData.duration
            else -> 0.0
        }
        val guidKey = itemData.guid?.substringAfterLast("/")
        return MovieOB(
            id = 0,
            idByAccountData = "${guidKey}_${helpViewModel.currentPlexAccount?.id}",
            movieId = guidKey ?: "",
            relatedMovieCategoryId = helpViewModel.currentPlexMovieCategory?.movieCatId ?: "",
            accountName = helpViewModel.currentPlexAccount?.name ?: "",
            accountId = helpViewModel.currentPlexAccount?.id,
            movieName = itemData.title,
            movieCmd = cmd,
            movieTime = itemData.duration?.toInt(),
            movieYear = itemData.year.toString(),
            rate = "",
            rating_imdb = itemData.audienceRating.toString(),
            screenshot_uri = "${helpViewModel.currentPlexAccount?.stalkerUrl}$coverPoster?X-Plex-Token=${helpViewModel.currentPlexAccount?.token}",
            backdropPath = "${helpViewModel.currentPlexAccount?.stalkerUrl}$background?X-Plex-Token=${helpViewModel.currentPlexAccount?.token}",
            genres_str = genres ?: "",
            actors = actors ?: "",
            added = (itemData.addedAt ?: "").toString(),
            age = itemData.contentRating ?: "",
            description = itemData.summary ?: "",
            director = directors ?: "",
            tmdb_id = "",
            o_name = itemData.originalTitle ?: "",
            currentPosition = 0L, // Platzhalter für aktuelle Position
            isFavorite = false,
            isCompletelyWatched = itemData.viewCount == 1,
            isPartlyWatched = itemData.viewOffset != 0L && itemData.viewCount != 1,
            percentagePlayed = percentWatched,
            plexRatingKey = itemData.ratingKey
        )
    }

    fun setFullVisibility() {
        binding.rvLayoutPlexItems.requestFocus()
    }

    fun openItemDetailFragment(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.container_plexitem_info, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
        binding.containerPlexitemInfo.visibility = View.VISIBLE
        binding.containerPlexitemInfo.requestFocus()
    }


    override fun onDestroy() {
        super.onDestroy()
        helpViewModel.currentPlexAccount = null
        helpViewModel.clickedPlexCategoryId = 0L
        helpViewModel.currentMovieAccount = null
        _binding = null
    }
}