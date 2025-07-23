package com.example.mj_player_tv.ui.adapter


import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.databinding.RvItemHistoryVodBinding
import com.example.mj_player_tv.databinding.RvItemMoviesBinding
import com.example.mj_player_tv.ui.WatchHistoryFragment
import com.example.mj_player_tv.ui.WatchListFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import io.objectbox.Box

@UnstableApi
class WatchHistoryMoviesAdapter(
    private val onClickListener: WatchHistoryMoviesAdapter.OnClickListener,
    private val fragment: WatchHistoryFragment,
    private val helpViewModel: HelpViewModel,
    private val accountBox: Box<Accounts>,
    private val onLongClickListener: WatchHistoryMoviesAdapter.OnLongClickListener
) : ListAdapter<MovieOB, WatchHistoryMoviesAdapter.ViewHolder>(
    MANAGE_MOVIECATEGORY_COMPERATOR) {

    var currentAccount: Accounts? = null

    inner class ViewHolder(val binding: RvItemHistoryVodBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(movie: MovieOB) {
            binding.apply {

                val image = movie.screenshot_uri
                if (!image.isNullOrEmpty()) {
                    ivVodImage.load(image)
                }

                setMovieDetailsNotImages(movie)

                binding.ivFavorite.visibility = if (movie.isFavorite) View.VISIBLE else View.INVISIBLE


                binding.cardViewVod.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        helpViewModel.currentSelectedWatchHistory = "MOVIE"
                        fragment.focusToMovieOrSerie()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition < 4) {
                        helpViewModel.currentSelectedWatchHistory = "MOVIE"
                        fragment.focusToMovieOrSerie()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition == (itemCount - 1)) {
                        binding.cardViewVod.requestFocus()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemHistoryVodBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val viewHolder = ViewHolder(binding)
        return viewHolder
    }

    @UnstableApi
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val movie = getItem(position)!!
        holder.bind(movie)
        holder.binding.cardViewVod.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvTitle.isSelected = hasFocus
            holder.binding.progressBar.isSelected = hasFocus
            if (hasFocus) {
                holder.binding.overlayFull.visibility = View.INVISIBLE
            } else {
                holder.binding.overlayFull.visibility = View.VISIBLE
            }
        }
        holder.binding.cardViewVod.setOnClickListener {
            onClickListener.onClick(movie)
        }

        holder.binding.cardViewVod.setOnLongClickListener {
            onLongClickListener.onLongClick(movie)
            true
        }
    }

    companion object {
        private val MANAGE_MOVIECATEGORY_COMPERATOR = object : DiffUtil.ItemCallback<MovieOB>() {
            override fun areItemsTheSame(oldItem: MovieOB, newItem: MovieOB) =
                oldItem.idByAccountData == newItem.idByAccountData


            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: MovieOB, newItem: MovieOB) =
                oldItem.idByAccountData == newItem.idByAccountData
        }
    }

    class OnClickListener(val clickListener: (movie: MovieOB) -> Unit) {
        fun onClick(movie: MovieOB) = clickListener(movie)
    }

    class OnLongClickListener(val onlongclickListener: (movie: MovieOB) -> Unit) {
        fun onLongClick(movie: MovieOB) = onlongclickListener(movie)
    }

}