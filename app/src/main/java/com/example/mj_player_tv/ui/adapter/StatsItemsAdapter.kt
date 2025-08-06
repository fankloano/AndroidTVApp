package com.example.mj_player_tv.ui.adapter

import android.content.Intent
import android.net.Uri
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.isGone
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.database.entity.Programme_
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.database.help.GlobalSearchDisplayItem
import com.example.mj_player_tv.database.help.StatsDisplayItem
import com.example.mj_player_tv.database.help.WatchlistDisplayItem
import com.example.mj_player_tv.databinding.RvItemGlobalsearchProgramsBinding
import com.example.mj_player_tv.databinding.RvItemGlobalsearchTvchannelBinding
import com.example.mj_player_tv.databinding.RvItemGlobalsearchmoviesBinding
import com.example.mj_player_tv.databinding.RvItemGlobalsearchseriesBinding
import com.example.mj_player_tv.databinding.RvItemHistoryTvchannelBinding
import com.example.mj_player_tv.databinding.RvItemHistoryVodBinding
import com.example.mj_player_tv.databinding.RvItemMoviesBinding
import com.example.mj_player_tv.databinding.RvItemSeriesBinding
import com.example.mj_player_tv.databinding.RvItemWatchlistProgrammeBinding
import com.example.mj_player_tv.databinding.RvItemWatchlistProgramsBinding
import com.example.mj_player_tv.ui.FullEpgFragment
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.ui.WatchHistoryFragment
import com.example.mj_player_tv.ui.WatchListFragment
import com.example.mj_player_tv.ui.WatchlistStatsFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import io.objectbox.Box
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
class StatsItemsAdapter(
    private val helpViewModel: HelpViewModel,
    private val fragment: WatchHistoryFragment,
    private val accountBox: Box<Accounts>,
    private val onItemClick: (StatsDisplayItem, View) -> Unit
) : ListAdapter<StatsDisplayItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_MOVIES = 1
        private const val TYPE_SERIES = 2
        private const val TYPE_TVCHANNELS = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is StatsDisplayItem.MovieItem -> TYPE_MOVIES
            is StatsDisplayItem.SeriesItem -> TYPE_SERIES
            is StatsDisplayItem.TvChannelItem -> TYPE_TVCHANNELS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_MOVIES -> {
                val binding = RvItemHistoryVodBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                MoviesViewHolder(binding, helpViewModel, fragment)
            }
            TYPE_SERIES -> {
                val binding = RvItemHistoryVodBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SeriesViewHolder(binding, helpViewModel, fragment)
            }
            TYPE_TVCHANNELS -> {
                val binding = RvItemHistoryTvchannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                TvChannelsViewHolder(binding, helpViewModel, fragment)
            }
            else -> throw IllegalArgumentException("Invalid viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is StatsDisplayItem.MovieItem -> (holder as MoviesViewHolder).bind(item)
            is StatsDisplayItem.SeriesItem -> (holder as SeriesViewHolder).bind(item)
            is StatsDisplayItem.TvChannelItem -> (holder as TvChannelsViewHolder).bind(item)
        }
    }

    inner class MoviesViewHolder(
        private val binding: RvItemHistoryVodBinding,
        private val helpViewModel: HelpViewModel,
        private val fragment: WatchHistoryFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(movieItem: StatsDisplayItem.MovieItem) {
            val movie = movieItem.movie
            binding.apply {

                val image = movie.screenshot_uri
                if (!image.isNullOrEmpty()) {
                    ivVodImage.load(image)
                }

                setMovieDetailsNotImages(movie)

                binding.ivFavorite.visibility = if (movie.isFavorite) View.VISIBLE else View.INVISIBLE


                binding.cardViewVod.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToCategory()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition < 4) {
                        fragment.focusToCategory()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition == (itemCount - 1)) {
                        binding.cardViewVod.requestFocus()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }

                binding.cardViewVod.setOnFocusChangeListener { _, hasFocus ->
                    binding.tvTitle.isSelected = hasFocus
                    binding.progressBar.isSelected = hasFocus
                    if (hasFocus) {
                        binding.overlayFull.visibility = View.INVISIBLE
                    } else {
                        binding.overlayFull.visibility = View.VISIBLE
                    }
                }

                binding.cardViewVod.setOnClickListener {
                    onItemClick.invoke(movieItem, itemView)
                }
            }
        }

        fun setMovieDetailsNotImages(movie: MovieOB) {
            binding.tvTitle.text = if (!movie.movieName.isNullOrEmpty()) {
                binding.tvTitle.visibility = View.VISIBLE
                movie.movieName
            } else {
                "No Title!"
            }
            binding.tvAccount.text = movie.movieAccount.target.name

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
                binding.tvVoddescription.visibility = View.INVISIBLE
                ""
            }
            binding.tvVoddescription.text = if (!movie.description.isNullOrEmpty()) {
                binding.tvVoddescription.visibility = View.VISIBLE
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
                val movieTimeInMinutes = if (movie.movieAccount.target!!.isXtream) {
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

    }

    inner class SeriesViewHolder(
        private val binding: RvItemHistoryVodBinding,
        private val helpViewModel: HelpViewModel,
        private val fragment: WatchHistoryFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(seriesItem: StatsDisplayItem.SeriesItem) {
            val serie = seriesItem.series
            binding.apply {

                setSeriesDetailsNotImages(serie)

                val image = serie.screenshot_uri
                if (!image.isNullOrEmpty()) {
                    ivVodImage.load(image)
                }

                binding.ivFavorite.visibility =
                    if (serie.isFavorite) View.VISIBLE else View.INVISIBLE

                binding.cardViewVod.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToCategory()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition < 4) {
                        fragment.focusToCategory()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition == (itemCount - 1)) {
                        binding.cardViewVod.requestFocus()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }

                binding.cardViewVod.setOnFocusChangeListener { _, hasFocus ->
                    binding.tvTitle.isSelected = hasFocus
                    binding.progressBar.isSelected = hasFocus
                    if (hasFocus) {
                        binding.overlayFull.visibility = View.INVISIBLE
                    } else {
                        binding.overlayFull.visibility = View.VISIBLE
                    }
                }

                binding.cardViewVod.setOnClickListener {
                    onItemClick.invoke(seriesItem, itemView)
                }
            }
        }

        fun setSeriesDetailsNotImages(serie: SeriesOB) {
            binding.tvDuration.visibility = View.GONE
            binding.tvTitle.text = if (serie.seriesName.isNotEmpty()) {
                binding.tvTitle.visibility = View.VISIBLE
                serie.seriesName
            } else {
                "No Title!"
            }
            binding.tvAccount.text = serie.seriesAccount.target.name
            binding.tvReleaseyear.text = if (!serie.seriesYear.isNullOrEmpty()) {
                binding.tvReleaseyear.visibility = View.VISIBLE
                val year =
                    if (serie.seriesYear.length >= 4) serie.seriesYear.substring(0, 4) else "n/a"
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
            binding.tvVoddescription.text = if (!serie.description.isNullOrEmpty()) {
                binding.tvVoddescription.visibility = View.VISIBLE
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

            binding.tvAge.text = if (!serie.age.isNullOrEmpty()) {
                binding.tvAge.visibility = View.VISIBLE
                serie.age
            } else {
                binding.tvAge.visibility = View.GONE
                ""
            }
            if (binding.tvDuration.isGone && serie.seriesYear.isNullOrEmpty() && serie.age.isNullOrEmpty()) {
                binding.linLayoutMoreInfos.visibility = View.GONE
            } else {
                binding.linLayoutMoreInfos.visibility = View.VISIBLE
            }
            if (serie.seriesPercentagePlayed != 0.0) {
                if (serie.isCompletelyWatched) {
                    binding.progressBar.progress = 100
                    binding.tvRemainingTime.visibility = View.VISIBLE
                    binding.tvRemainingTime.text = "Completed!"
                } else if (serie.isPartlyWatched) {
                    binding.tvRemainingTime.visibility = View.VISIBLE
                    // Schritt 1: Berechne den Fortschritt in Prozent
                    val progressPercentage =
                        serie.seriesPercentagePlayed * 100  // Wandelt den Fortschritt in Prozent um (von 0.0 bis 100.0)

// Schritt 2: Runden auf maximal 2 Dezimalstellen
                    val formattedPercentage = String.format(
                        "%.2f",
                        progressPercentage
                    )  // Formatierung auf 2 Dezimalstellen

// Schritt 3: Update der ProgressBar
                    val progressBarPercentage =
                        (serie.seriesPercentagePlayed * 100).toInt()  // ProgressBar erwartet einen Integer zwischen 0 und 100
                    binding.progressBar.progress = progressBarPercentage

// Schritt 4: Anzeige des Fortschritts als Text
                    binding.tvRemainingTime.text =
                        "$formattedPercentage% watched.."  // Zeigt den Fortschritt als Text im Prozentformat an

                } else {
                    binding.tvRemainingTime.visibility = View.INVISIBLE
                }
            } else {
                binding.tvRemainingTime.visibility = View.INVISIBLE
            }
        }

        fun formatRating(rating: String?): String {
            val ratingValue = rating?.toFloatOrNull()
            return when {
                ratingValue == null -> ""
                ratingValue == ratingValue.toInt().toFloat() -> String.format("%.1f", ratingValue)
                    .replace(",", ".")

                else -> String.format("%.1f", ratingValue).replace(",", ".")
            }
        }
    }

    inner class TvChannelsViewHolder(
        private val binding: RvItemHistoryTvchannelBinding,
        private val helpViewModel: HelpViewModel,
        private val fragment: WatchHistoryFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(channelItem: StatsDisplayItem.TvChannelItem) {
            val tvchannel = channelItem.tvchannel
            binding.apply {
                tvChannelname.text = tvchannel.showingName
                tvChannelcategory.text = "[ ${tvchannel.reltvcategory.target.showingName} ]"

                tvAccount.text = tvchannel.account.target.name

                val linkedEpgChannel = tvchannel.linkedEpgChannel?.target

                val image = tvchannel.logo
                val epgLogo = linkedEpgChannel?.icon?.firstOrNull()

                if (tvchannel.account.target!!.useEpgLogos) {
                    if (!epgLogo.isNullOrEmpty() && (linkedEpgChannel.isExternalEpg || tvchannel.alwaysUsesExternalEpg)) {
                        ivTvchannelImage.visibility = View.VISIBLE
                        ivTvchannelImage.load(epgLogo)
                    } else {
                        if (image.isNotEmpty()) {
                            ivTvchannelImage.visibility = View.VISIBLE
                            ivTvchannelImage.load(image)
                        } else {
                            ivTvchannelImage.visibility = View.INVISIBLE
                        }
                    }
                } else {
                    if (image.isNotEmpty()) {
                        ivTvchannelImage.visibility = View.VISIBLE
                        ivTvchannelImage.load(image)
                    } else {
                        ivTvchannelImage.visibility = View.INVISIBLE
                    }
                }

                if (tvchannel.isFavorite) {
                    ivTvchannelFavorite.visibility = View.VISIBLE
                } else {
                    ivTvchannelFavorite.visibility = View.INVISIBLE
                }

                tvPlayingtimeCount.text = formatWatchTime(tvchannel.timeWatched)

                binding.cardViewTv.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToCategory()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition < 4) {
                        fragment.focusToCategory()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }

                binding.cardViewTv.setOnFocusChangeListener { _, hasFocus ->
                    binding.tvChannelname.isSelected = hasFocus
                    binding.tvPlayingtime.isSelected = hasFocus
                    binding.tvPlayingtimeCount.isSelected = hasFocus
                    binding.tvAccount.isSelected = hasFocus
                    binding.tvChannelcategory.isSelected = hasFocus
                    if (hasFocus) {
                        binding.overlayFull.visibility = View.GONE
                    } else {
                        binding.overlayFull.visibility = View.VISIBLE
                    }
                }

                binding.cardViewTv.setOnClickListener {
                    onItemClick.invoke(channelItem, itemView)
                }
            }
        }
        fun formatWatchTime(secondsTotal: Long): String {
            val hours = secondsTotal / 3600
            val minutes = (secondsTotal % 3600) / 60
            val seconds = secondsTotal % 60

            return buildString {
                if (hours > 0) append("${hours}h ")
                if (minutes > 0 || hours > 0) append("${minutes}min ")
                append("${seconds}sec")
            }.trim()
        }
    }


    class DiffCallback : DiffUtil.ItemCallback<StatsDisplayItem>() {
        override fun areItemsTheSame(oldItem: StatsDisplayItem, newItem: StatsDisplayItem): Boolean {
            val result = when {
                oldItem is StatsDisplayItem.MovieItem && newItem is StatsDisplayItem.MovieItem ->
                    oldItem.movie.idByAccountData == newItem.movie.idByAccountData
                oldItem is StatsDisplayItem.SeriesItem && newItem is StatsDisplayItem.SeriesItem ->
                    oldItem.series.idByAccountData == newItem.series.idByAccountData

                oldItem is StatsDisplayItem.TvChannelItem && newItem is StatsDisplayItem.TvChannelItem ->
                    oldItem.tvchannel.idByAccountData == newItem.tvchannel.idByAccountData
                else -> false
            }
            return result
        }

        override fun areContentsTheSame(oldItem: StatsDisplayItem, newItem: StatsDisplayItem): Boolean {
            val result = oldItem == newItem
            return result
        }
    }

}
