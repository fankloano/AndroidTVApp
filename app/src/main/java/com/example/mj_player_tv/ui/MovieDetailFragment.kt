package com.example.mj_player_tv.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.databinding.FragmentMovieDetailBinding
import com.example.mj_player_tv.network.model.plex.items.Metadata
import com.example.mj_player_tv.utils.Resource
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.HelpViewModelFactory
import com.example.mj_player_tv.viewmodel.MoviesViewModel
import com.example.mj_player_tv.viewmodel.MoviesViewModelFactory
import com.example.mj_player_tv.viewmodel.PlexViewModel
import com.example.mj_player_tv.viewmodel.PlexViewModelFactory
import com.example.mj_player_tv.viewmodel.StalkerViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModelFactory
import com.example.mj_player_tv.viewmodel.XtreamViewModel
import com.example.mj_player_tv.viewmodel.XtreamViewModelFactory
import io.objectbox.Box
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@UnstableApi
class MovieDetailFragment : Fragment(R.layout.fragment_movie_detail) {

    private var _binding: FragmentMovieDetailBinding? = null

    private val binding get() = _binding!!

    private val movieBox: Box<MovieOB> = ObjectBox.store.boxFor(MovieOB::class.java)

    private var clickFavoriteOrWatched = false

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
        _binding = FragmentMovieDetailBinding.inflate(inflater, container, false)
        val view = binding.root
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (helpViewModel.currentFocusedMovie != null) {

            if (helpViewModel.isSearchContainerOpened) {
                getMovieImage()
            } else {
                if (helpViewModel.currentMovieImage != null) {
                    binding.ivMovieposter.load(helpViewModel.currentMovieImage)
                }
            }

            binding.tvMovietitle.text = helpViewModel.currentFocusedMovie!!.movieName
            binding.tvMovietitle.isSelected = true

            binding.constMovies.requestFocus()

            binding.tvActors.text = helpViewModel.currentFocusedMovie!!.actors?.ifEmpty {
                "n/a"
            } ?: "n/a"

            binding.tvDirectors.text = helpViewModel.currentFocusedMovie!!.director?.ifEmpty {
                "n/a"
            } ?: "n/a"
            binding.tvMoviedescription.text = helpViewModel.currentFocusedMovie!!.description?.ifEmpty {
                "No description available"
            } ?:  "No description available"
            binding.tvCategories.text = helpViewModel.currentFocusedMovie!!.genres_str?.ifEmpty {
                ""
            } ?: ""

            binding.tvMoviedescription.post {
                val layout = binding.tvMoviedescription.layout
                Log.d("TEXTVIEW_HEIGHT", "Height of TextView: ${binding.tvMoviedescription.height}")
                if (layout != null) {
                    val isTextTruncated = layout.height - binding.tvMoviedescription.height > 0
                    if (isTextTruncated) {
                        binding.tvMoviedescription.isVerticalScrollBarEnabled = true
                        binding.tvMoviedescription.movementMethod = ScrollingMovementMethod()
                        binding.gradientView.visibility = View.VISIBLE
                        binding.ivMoretext.visibility = View.VISIBLE
                    } else {
                        binding.tvMoviedescription.isVerticalScrollBarEnabled = false
                        binding.tvMoviedescription.movementMethod = null
                        binding.tvMoviedescription.scrollTo(0, 0) // Zurück zur Ausgangsposition
                        binding.gradientView.visibility = View.GONE
                        binding.ivMoretext.visibility = View.GONE
                    }
                }
            }

            binding.smallRating.rating = if (!helpViewModel.currentFocusedMovie!!.rating_imdb.isNullOrEmpty()) {
                val formattedRating = formatRating(helpViewModel.currentFocusedMovie!!.rating_imdb)
                val ratingValue = formattedRating.toFloatOrNull() ?: 0.0f
                (ratingValue / 2.0f)
            } else {
                0.0f
            }
            binding.tvRating.text = formatRating(helpViewModel.currentFocusedMovie!!.rating_imdb).ifEmpty {
                "0.0"
            }
            binding.tvReleaseyear.text = if (helpViewModel.currentFocusedMovie!!.movieYear.isNotEmpty()) {
                if (helpViewModel.currentFocusedMovie!!.movieYear.length >= 4) {
                    helpViewModel.currentFocusedMovie!!.movieYear.substring(0, 4)
                } else {
                    "n/a"
                }
            } else {
                "n/a"
            }
            binding.tvAge.text = helpViewModel.currentFocusedMovie!!.age?.ifEmpty {
                "n/a"
            } ?: "n/a"

            binding.ivFavorite.visibility = if (helpViewModel.currentFocusedMovie!!.isFavorite) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }

            binding.progressBar.visibility = View.VISIBLE
            binding.progressBar.progress = helpViewModel.currentFocusedMovie!!.percentagePlayed.toInt()
            binding.tvDuration.text = if (helpViewModel.currentFocusedMovie!!.movieTime != null) {
                formatDuration(helpViewModel.currentFocusedMovie!!.movieTime!!, helpViewModel.currentMovieAccount!!)
            } else {
                "0min"
            }

            if (helpViewModel.currentFocusedMovie!!.isPartlyWatched) {
                binding.tvRemainingTime.visibility = View.VISIBLE

                // Prüfe, ob die movieTime in Minuten oder Sekunden ist
                val movieTimeInMinutes = if (helpViewModel.currentMovieAccount!!.isXtream) {
                    (helpViewModel.currentFocusedMovie!!.movieTime ?: 0) / 60 // Sekunden zu Minuten umrechnen
                } else {
                    helpViewModel.currentFocusedMovie!!.movieTime ?: 0 // Bereits in Minuten
                }

                // Berechne die verbleibende Zeit
                val remainingTimeMinutes = movieTimeInMinutes - (movieTimeInMinutes * helpViewModel.currentFocusedMovie!!.percentagePlayed)

                // Formatierung der verbleibenden Zeit
                val remainingTimeText = if (remainingTimeMinutes < 60) {
                    "${remainingTimeMinutes.toInt()}min remaining"
                } else {
                    val hours = remainingTimeMinutes.toInt() / 60
                    val minutes = remainingTimeMinutes.toInt() % 60
                    "${hours}h ${minutes}min remaining"
                }

                binding.tvRemainingTime.text = remainingTimeText
                binding.progressBar.progress = (helpViewModel.currentFocusedMovie!!.percentagePlayed * 100).toInt()
            } else if (helpViewModel.currentFocusedMovie!!.isCompletelyWatched) {
                binding.tvRemainingTime.text = "Completed!"
                binding.progressBar.progress = 100
            } else {
                binding.tvRemainingTime.visibility = View.INVISIBLE
            }
            openMovieSettings(helpViewModel.currentFocusedMovie!!)
    }

        val dp30 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 35f, resources.displayMetrics
        ).toInt()

        binding.btnPlay.setOnFocusChangeListener { _, hasFocus ->
            val params = binding.btnPlay.layoutParams
            if (hasFocus) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.btnPlay.layoutParams = params
            } else {
                params.width = dp30
                binding.btnPlay.layoutParams = params
            }
        }

        binding.btnPlay.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                closeFragment()
                return@setOnKeyListener true
            }
            false
        }

        binding.btnPlay.setOnClickListener {
            playMovie()
        }

        binding.btnPlay.setOnLongClickListener {
            if (helpViewModel.currentFocusedMovie != null) {
                showPlayerSelectionDialog()
            }
            true
        }

        binding.btnaddFavorite.setOnFocusChangeListener { _, hasFocus ->
            val params = binding.btnaddFavorite.layoutParams
            if (hasFocus) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.btnaddFavorite.layoutParams = params
            } else {
                params.width = dp30
                binding.btnaddFavorite.layoutParams = params
            }
        }

        binding.btnaddFavorite.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                closeFragment()
                return@setOnKeyListener true
            }
            false
        }

        binding.btnaddFavorite.setOnClickListener {
            clickFavoriteOrWatched = true
            viewLifecycleOwner.lifecycleScope.launch {
                if (helpViewModel.currentFocusedMovie != null) {
                    if (!helpViewModel.currentFocusedMovie!!.isFavorite) {
                        helpViewModel.currentFocusedMovie!!.isFavorite = true
                        binding.btnaddFavorite.text = "Remove from Watchlist"
                        binding.btnaddFavorite.isSelected = true
                        helpViewModel.currentFocusedMovie?.let { movie ->
                            helpViewModel.clickedPlexMovieItem?.isFavorite = true
                            movie.movieAccount.target = helpViewModel.currentMovieAccount
                            movie.moviecat.target = helpViewModel.currentMovieCategoryOB
                            movieBox.put(movie)
                            if (helpViewModel.currentMovieAccount?.isPlex == true) {
                                plexViewModel.addItemToPlexWatchlist(helpViewModel.currentMovieAccount!!, movie.movieId)
                            }
                        }
                        binding.btnaddFavorite.requestFocus()
                        updateMovieInRV()
                    } else {
                        helpViewModel.currentFocusedMovie!!.isFavorite = false
                        binding.btnaddFavorite.text = "Add to Watchlist"
                        binding.btnaddFavorite.isSelected = false
                        if (helpViewModel.currentFocusedMovie != null) {
                            helpViewModel.currentFocusedMovie?.let { movie ->
                                if (helpViewModel.currentFocusedMovie!!.isPartlyWatched && helpViewModel.currentFocusedMovie!!.isCompletelyWatched) {
                                    helpViewModel.clickedPlexMovieItem?.isFavorite = false
                                    movie.movieAccount.target =
                                        helpViewModel.currentMovieAccount
                                    movie.moviecat.target = helpViewModel.currentMovieCategoryOB
                                    movieBox.put(movie)
                                } else {
                                    helpViewModel.currentFocusedMovie?.let {
                                        movieBox.remove(it)
                                    }
                                }
                                if (helpViewModel.currentMovieAccount?.isPlex == true) {
                                    plexViewModel.removeItemFromWatchlist(
                                        helpViewModel.currentMovieAccount!!,
                                        movie.movieId
                                    )
                                }
                            }
                        }
                    }
                    binding.btnaddFavorite.requestFocus()
                    updateMovieInRV()
                }
            }
        }

        binding.btnaddWatched.setOnFocusChangeListener { _, hasFocus ->
            val params = binding.btnaddWatched.layoutParams
            if (hasFocus) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.btnaddWatched.layoutParams = params
            } else {
                params.width = dp30
                binding.btnaddWatched.layoutParams = params
            }
        }

        binding.btnaddWatched.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                closeFragment()
                return@setOnKeyListener true
            }
            false
        }

        binding.btnaddWatched.setOnClickListener {
            clickFavoriteOrWatched = true
            if (helpViewModel.currentFocusedMovie != null) {
                Log.d("TEST PLEX UPDATE", "START: ${helpViewModel.currentMovieAccount}")
                viewLifecycleOwner.lifecycleScope.launch {
                    if (!helpViewModel.currentFocusedMovie!!.isCompletelyWatched) {
                        helpViewModel.currentFocusedMovie!!.isCompletelyWatched = true
                        helpViewModel.currentFocusedMovie!!.isPartlyWatched = false
                        binding.btnaddWatched.text = "Mark as unwatched"
                        binding.btnPlay.text = "Re-Watch"
                        helpViewModel.currentFocusedMovie!!.percentagePlayed = 1.0
                        helpViewModel.currentFocusedMovie!!.currentPosition = 0L
                        binding.btnaddWatched.isSelected = true
                        helpViewModel.currentFocusedMovie?.let { movie ->
                            movie.movieAccount.target = helpViewModel.currentMovieAccount
                            movie.moviecat.target = helpViewModel.currentMovieCategoryOB
                            movieBox.put(movie)
                            if (helpViewModel.currentMovieAccount?.isPlex == true) {
                                helpViewModel.currentMovieAccount?.let { account ->
                                    helpViewModel.clickedPlexMovieItem?.viewCount = 1
                                    helpViewModel.clickedPlexMovieItem?.viewOffset = 0L
                                    plexViewModel.markItemAsWatched(account, movie.plexRatingKey ?: "")
                                }
                            }
                        }
                        updateMovieInRV()
                    } else {
                        helpViewModel.currentFocusedMovie!!.isCompletelyWatched = false
                        binding.btnaddWatched.text = "Mark as watched"
                        binding.btnPlay.text = "Play Movie"
                        helpViewModel.currentFocusedMovie!!.percentagePlayed = 0.0
                        helpViewModel.currentFocusedMovie!!.currentPosition = 0L
                        binding.btnaddWatched.isSelected = false
                        if (helpViewModel.currentFocusedMovie!!.isFavorite) {
                            helpViewModel.currentFocusedMovie?.let {
                                it.movieAccount.target = helpViewModel.currentMovieAccount
                                it.moviecat.target = helpViewModel.currentMovieCategoryOB
                                movieBox.put(it)
                            }
                        } else {
                            helpViewModel.currentFocusedMovie?.let {
                                movieBox.remove(it)
                            }
                        }
                        if (helpViewModel.currentMovieAccount?.isPlex == true) {
                            helpViewModel.currentFocusedMovie?.let { movie ->
                                helpViewModel.currentMovieAccount?.let { account ->
                                    helpViewModel.clickedPlexMovieItem?.viewCount = 0
                                    helpViewModel.clickedPlexMovieItem?.viewOffset = 0L
                                    plexViewModel.markItemAsNotWatched(account, movie.plexRatingKey ?: "")
                                }
                            }
                        }
                    }
                    binding.btnaddWatched.requestFocus()
                    updateMovieInRV()
                }
            }
        }

        binding.btnInfo.setOnFocusChangeListener { _, hasFocus ->
            val params = binding.btnInfo.layoutParams
            if (hasFocus) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.btnInfo.layoutParams = params
            } else {
                params.width = dp30
                binding.btnInfo.layoutParams = params
            }
        }

        binding.btnInfo.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                closeFragment()
                return@setOnKeyListener true
            }
            false
        }

        binding.constMovies.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                closeFragment()
                return@setOnKeyListener true
            }
            false
        }

        binding.tvMoviedescription.setOnKeyListener { _, keyCode, event ->
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    val layout = binding.tvMoviedescription.layout

                    // Überprüfen, ob der Text abgeschnitten ist (abgeschnitten, wenn die letzte Zeile mehr als die Höhe des TextViews hinausgeht)
                    val isTextTruncated = layout.height - binding.tvMoviedescription.height > 0

                    // Wenn der Text abgeschnitten ist, Scrollen zulassen
                    if (isTextTruncated) {

                        when (keyCode) {
                            KeyEvent.KEYCODE_BACK -> {
                                binding.tvMoviedescription.scrollTo(0, 0)
                                binding.gradientView.visibility = View.VISIBLE
                                binding.ivMoretext.visibility = View.VISIBLE
                                binding.btnPlay.requestFocus()
                                return@setOnKeyListener true
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                // Scroll nach oben
                                val newScrollY = (binding.tvMoviedescription.scrollY - 50).coerceAtLeast(0)
                                binding.tvMoviedescription.scrollTo(0, newScrollY)
                                return@setOnKeyListener true // Verhindern, dass der Rest ausgeführt wird
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                // Berechne den maximalen Scrollbereich
                                val maxScrollY = binding.tvMoviedescription.layout.height - binding.tvMoviedescription.height
                                val newScrollY = (binding.tvMoviedescription.scrollY + 50).coerceAtMost(maxScrollY + (2 * binding.tvMoviedescription.lineHeight))  // 2 Zeilen Puffer
                                binding.tvMoviedescription.scrollTo(0, newScrollY)
                                return@setOnKeyListener true
                            }
                            else -> return@setOnKeyListener false // Falls keine relevante Taste gedrückt wird
                        }
                    } else {
                        // Wenn der Text nicht abgeschnitten ist, führ die Rückkehr zur ursprünglichen Aktion aus
                        binding.btnPlay.requestFocus()
                        return@setOnKeyListener true
                    }
                }

                else -> {false}
            }
        }

        moviesViewModel.focusRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                updateAndFocusPlayButton()
                moviesViewModel.clearFocusOnPlayMovie()
            }
        }
    }

    private fun openMovieSettings(movie: MovieOB) {
        binding.relLayoutMovieSettings.visibility = View.VISIBLE
        if (movie.isFavorite) {
            binding.btnaddFavorite.isSelected = true
            binding.btnaddFavorite.text = "Remove from Watchlist"
        } else {
            binding.btnaddFavorite.isSelected = false
            binding.btnaddFavorite.text = "Add to Watchlist"
        }
        if (movie.isPartlyWatched) {
            binding.btnaddWatched.isSelected = false
            binding.btnPlay.text = "Continue.."
        } else if (movie.isCompletelyWatched) {
            binding.btnaddWatched.isSelected = true
            binding.btnaddWatched.text = "Mark as unwatched"
            binding.btnPlay.text = "Re-Watch"
        } else {
            binding.btnPlay.text = "Play Movie"
            binding.btnaddWatched.isSelected = false
            binding.btnaddWatched.text = "Mark as watched"
        }
        if (!helpViewModel.movieFullScreenOpened && !clickFavoriteOrWatched) {
            binding.btnPlay.requestFocus()
        }
        if (clickFavoriteOrWatched) {
            clickFavoriteOrWatched = false
        }
    }

    fun updateMovieInRV() {
        if (helpViewModel.isWatchlistContainerOpened || helpViewModel.isWatchHistoryContainerOpened) {
            moviesViewModel.requestUpdateMovieInRV()
        } else {
            binding.ivFavorite.visibility = if (helpViewModel.currentFocusedMovie!!.isFavorite) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
            updateMovieRunningTime()
            moviesViewModel.requestUpdateMovieInRV()
        }
    }

    fun focusDescription() {
        binding.tvMoviedescription.post {
            val layout = binding.tvMoviedescription.layout
            if (layout != null) {
                val totalLines = layout.lineCount
                val lastLineBottom = layout.getLineBottom(totalLines - 1)

                // Wenn der Text abgeschnitten ist oder 1-2 Zeilen weniger angezeigt werden, dann als abgeschnitten betrachten
                val tolerance = 4 // Toleranz für 1-2 Zeilen weniger
                val isTextTruncated = lastLineBottom > (binding.tvMoviedescription.height - tolerance)

                if (isTextTruncated) {
                    binding.gradientView.visibility = View.GONE
                    binding.ivMoretext.visibility = View.GONE
                    binding.tvMoviedescription.requestFocus()
                } else {
                    return@post
                }
            }
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

    private fun formatDuration(duration: Int, currentAccount: Accounts): String {

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
            else ->  {
                val totalMinutes = duration / 1000 / 60
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60
                formatTime(hours, minutes)
            }
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


    private fun playMovie() {
        if (helpViewModel.currentFocusedMovie != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val account = helpViewModel.currentMovieAccount
                if (account?.isPlex == true) {
                    helpViewModel.clickedPlexMovieItem?.let {
                        val sameItems = plexViewModel.getPlexSameItems(account, "movie", it)
                        if (sameItems.isNotEmpty()) {
                            // auch die geklickte Version hinzufügen (falls noch nicht in der Liste)
                            val allVersions = buildList {
                                add(helpViewModel.clickedPlexMovieItem)
                                addAll(sameItems)
                            }

                            if (allVersions.size > 1) {
                                showPlexVersionDialog(allVersions)
                            } else {
                                openFullScreenMovie(PlayMovieFragment())
                            }
                        } else {
                            openFullScreenMovie(PlayMovieFragment())
                        }
                    }
                } else {
                    openFullScreenMovie(PlayMovieFragment())
                }
            }
        }
    }

    fun openFullScreenMovie(fragment: Fragment) {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.fullscreen_movie, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
        helpViewModel.movieFullScreenOpened = true
        binding.fullscreenMovie.visibility = View.VISIBLE
    }

    fun closeFullScreenMovie() {
        helpViewModel.movieFullScreenOpened = false
        binding.fullscreenMovie.visibility = View.GONE
        helpViewModel.currentFocusedMovie?.let {
            openMovieSettings(it)
        }
    }

    private fun showPlayerSelectionDialog() {
        val settings = helpViewModel.settings
        if (settings != null) {
            if (settings.playMoviesWithVlc) {
                binding.vlcPlayerIcon.visibility = View.VISIBLE
                binding.exoPlayerIcon.visibility = View.INVISIBLE
                binding.linLayoutSelectVLCPlayer.requestFocus()
            } else {
                binding.exoPlayerIcon.visibility = View.VISIBLE
                binding.vlcPlayerIcon.visibility = View.INVISIBLE
                binding.linLayoutSelectExoPlayer.requestFocus()
            }
            binding.linlayoutSelectmovieplayer.visibility = View.VISIBLE
        }
    }

    private fun closePlayerSelectionDialog() {
        binding.linlayoutSelectmovieplayer.visibility = View.GONE
        binding.btnPlay.requestFocus()
    }

    var currentTmdbMovieDetailJob: Job? = null

    private fun getMovieImage() {
        currentTmdbMovieDetailJob?.cancel()
        currentTmdbMovieDetailJob = viewLifecycleOwner.lifecycleScope.launch {
            if (helpViewModel.currentMovieAccount!!.isXtream) {
                val movieToUse =
                    if (xtreamViewModel.modifiedXtreamMovies.contains(helpViewModel.currentFocusedMovie!!.idByAccountData)) {
                        helpViewModel.currentFocusedMovie
                    } else {
                        xtreamViewModel.getXtreamMovieDetails(helpViewModel.currentFocusedMovie!!, helpViewModel.currentMovieAccount!!)
                    }
                if (!movieToUse?.backdropPath.isNullOrEmpty()) {
                    if (!movieToUse?.tmdb_id.isNullOrEmpty() && helpViewModel.settings!!.tmdbApiKey.isNotEmpty()) {
                        if (movieToUse?.tmdb_id!!.startsWith("tt")) {
                            val tmdbMovieDetailsByImdbId =
                                helpViewModel.getTmdbMovieDetailsByImdb(
                                    url = "https://api.themoviedb.org/3/find/",
                                    imdbId = movieToUse.tmdb_id!!,
                                    apiKey = helpViewModel.settings?.tmdbApiKey ?: ""
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
                                    val moviePoster = movieToUse.screenshot_uri
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
                                movieId = movieToUse.tmdb_id!!.toInt(),
                                apiKey = helpViewModel.settings?.tmdbApiKey ?: ""
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
                                    val moviePoster = movieToUse.screenshot_uri
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
                        if (!movieToUse!!.screenshot_uri.isNullOrEmpty()) {
                            binding.ivMovieposter.visibility = View.VISIBLE
                            binding.ivMovieposter.load(movieToUse.screenshot_uri)
                            helpViewModel.currentMovieImage = movieToUse.screenshot_uri
                        } else {
                            helpViewModel.currentMovieImage = ""
                            binding.ivMovieposter.visibility = View.INVISIBLE
                        }
                    }
                } else {
                    binding.ivMovieposter.visibility = View.VISIBLE
                    binding.ivMovieposter.load(movieToUse!!.backdropPath)
                    helpViewModel.currentMovieImage = movieToUse.backdropPath
                }
            } else {
                if (helpViewModel.currentFocusedMovie!!.tmdb_id!!.isNotEmpty() && helpViewModel.settings!!.tmdbApiKey.isNotEmpty()) {
                    if (helpViewModel.currentFocusedMovie!!.tmdb_id!!.startsWith("tt")) {
                        val tmdbMovieDetailsByImdbId = helpViewModel.getTmdbMovieDetailsByImdb(
                            url = "https://api.themoviedb.org/3/find/",
                            imdbId = helpViewModel.currentFocusedMovie!!.tmdb_id!!,
                            apiKey = helpViewModel.settings!!.tmdbApiKey
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
                                val moviePoster = helpViewModel.currentFocusedMovie!!.screenshot_uri
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
                            movieId = helpViewModel.currentFocusedMovie!!.tmdb_id!!.toInt(),
                            apiKey = helpViewModel.settings?.tmdbApiKey ?: ""
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
                                val moviePoster = helpViewModel.currentFocusedMovie!!.screenshot_uri
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
                    if (!helpViewModel.currentFocusedMovie!!.screenshot_uri.isNullOrEmpty()) {
                        binding.ivMovieposter.visibility = View.VISIBLE
                        binding.ivMovieposter.load(helpViewModel.currentFocusedMovie!!.screenshot_uri)
                        helpViewModel.currentMovieImage = helpViewModel.currentFocusedMovie!!.screenshot_uri
                    } else {
                        binding.ivMovieposter.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }

    fun updateMovieRunningTime() {
        binding.progressBar.progress = helpViewModel.currentFocusedMovie!!.percentagePlayed.toInt()
        binding.tvDuration.text = if (helpViewModel.currentFocusedMovie!!.movieTime != null) {
            formatDuration(helpViewModel.currentFocusedMovie!!.movieTime!!, helpViewModel.currentMovieAccount!!)
        } else {
            "0min"
        }

        if (helpViewModel.currentFocusedMovie!!.isPartlyWatched) {
            binding.tvRemainingTime.visibility = View.VISIBLE

            // Prüfe, ob die movieTime in Minuten oder Sekunden ist
            val movieTimeInMinutes = if (helpViewModel.currentMovieAccount!!.isXtream && helpViewModel.currentMovieAccount!!.isPlex) {
                (helpViewModel.currentFocusedMovie!!.movieTime ?: 0) / 60 // Sekunden zu Minuten umrechnen
            } else {
                helpViewModel.currentFocusedMovie!!.movieTime ?: 0 // Bereits in Minuten
            }

            // Berechne die verbleibende Zeit
            val remainingTimeMinutes = movieTimeInMinutes - (movieTimeInMinutes * helpViewModel.currentFocusedMovie!!.percentagePlayed)

            // Formatierung der verbleibenden Zeit
            val remainingTimeText = if (remainingTimeMinutes < 60) {
                "${remainingTimeMinutes.toInt()}min remaining"
            } else {
                val hours = remainingTimeMinutes.toInt() / 60
                val minutes = remainingTimeMinutes.toInt() % 60
                "${hours}h ${minutes}min remaining"
            }

            binding.tvRemainingTime.text = remainingTimeText
            binding.progressBar.progress = (helpViewModel.currentFocusedMovie!!.percentagePlayed * 100).toInt()
        } else if (helpViewModel.currentFocusedMovie!!.isCompletelyWatched) {
            binding.tvRemainingTime.visibility = View.VISIBLE
            binding.tvRemainingTime.text = "Completed!"
            binding.progressBar.progress = 100
        } else {
            binding.tvRemainingTime.visibility = View.INVISIBLE
        }
    }

    private fun showPlexVersionDialog(items: List<Metadata?>) {
        val titles = items.map { item ->
            val resolution = item?.Media?.firstOrNull()?.videoResolution.orEmpty()
            if (resolution.equals("4k", ignoreCase = true)) {
                "4K"
            } else {
                "${resolution}p"
            }
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Select Version")
            .setItems(titles) { _, which ->
                val selectedItem = items[which]
                if (selectedItem != helpViewModel.clickedPlexMovieItem) {
                    selectedItem?.let {
                        helpViewModel.currentFocusedMovie = convertToMovie(selectedItem)
                        openFullScreenMovie(PlayMovieFragment())
                    }
                } else {
                    openFullScreenMovie(PlayMovieFragment())
                }
            }
            .setNegativeButton("Close", null)
            .show()
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

    private fun updateAndFocusPlayButton() {
        val movie = helpViewModel.currentFocusedMovie
        if (movie != null) {
            binding.btnPlay.text = if (movie.isCompletelyWatched) {
                "Re-Watch"
            } else if (movie.isPartlyWatched) {
                "Continue.."
            } else {
                "Play Movie"
            }
        }
        binding.btnPlay.requestFocus()
    }

    fun closeFragment() {
        // Gehe zurück zum vorherigen Fragment im Back Stack
        moviesViewModel.requestFocusToMovies()
        parentFragmentManager.popBackStack()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}