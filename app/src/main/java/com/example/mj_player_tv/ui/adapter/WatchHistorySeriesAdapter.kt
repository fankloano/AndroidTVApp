package com.example.mj_player_tv.ui.adapter


import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.databinding.RvItemGlobalsearchseriesBinding
import com.example.mj_player_tv.databinding.RvItemHistoryVodBinding
import com.example.mj_player_tv.databinding.RvItemMoviesBinding
import com.example.mj_player_tv.databinding.RvItemSeriesBinding
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.ui.SearchMovieByCategoryFragment
import com.example.mj_player_tv.ui.SearchSeriesByCategoryFragment
import com.example.mj_player_tv.ui.WatchHistoryFragment
import com.example.mj_player_tv.ui.WatchListFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class WatchHistorySeriesAdapter(private val onClickListener: WatchHistorySeriesAdapter.OnClickListener, private val fragment: WatchHistoryFragment, private val helpViewModel: HelpViewModel) : ListAdapter<SeriesOB, WatchHistorySeriesAdapter.ViewHolder>(
    MANAGE_SERIESCATEGORY_COMPERATOR) {

    var currentAccount: Accounts? = null

    inner class ViewHolder(val binding: RvItemHistoryVodBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(serie: SeriesOB) {
            binding.apply {

                setSeriesDetailsNotImages(serie)

                val image = serie.screenshot_uri
                if (!image.isNullOrEmpty()) {
                    ivVodImage.load(image)
                }

                binding.ivFavorite.visibility = if (serie.isFavorite) View.VISIBLE else View.INVISIBLE

                binding.cardViewVod.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        helpViewModel.currentSelectedWatchHistory = "SERIE"
                        fragment.focusToMovieOrSerie()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition < 4) {
                        helpViewModel.currentSelectedWatchHistory = "SERIE"
                        fragment.focusToMovieOrSerie()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition  == (itemCount - 1)) {
                        binding.cardViewVod.requestFocus()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
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
        val serie = getItem(position)!!
        holder.bind(serie)
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
            onClickListener.onClick(serie)
        }
    }

    companion object {
        private val MANAGE_SERIESCATEGORY_COMPERATOR = object : DiffUtil.ItemCallback<SeriesOB>() {
            override fun areItemsTheSame(oldItem: SeriesOB, newItem: SeriesOB) =
                oldItem.idByAccountData == newItem.idByAccountData


            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: SeriesOB, newItem: SeriesOB) =
                oldItem.idByAccountData == newItem.idByAccountData
        }
    }

    class OnClickListener(val clickListener: (serie: SeriesOB) -> Unit) {
        fun onClick(serie: SeriesOB) = clickListener(serie)
    }

}