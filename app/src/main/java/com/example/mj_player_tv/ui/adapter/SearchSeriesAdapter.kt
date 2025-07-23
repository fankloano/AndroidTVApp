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
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.databinding.RvItemMoviesBinding
import com.example.mj_player_tv.ui.SearchMovieByCategoryFragment
import com.example.mj_player_tv.ui.SearchSeriesByCategoryFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class SearchSeriesAdapter(private val onClickListener: SearchSeriesAdapter.OnClickListener, private val fragment: SearchSeriesByCategoryFragment, private val helpViewModel: HelpViewModel) : ListAdapter<SeriesOB, SearchSeriesAdapter.ViewHolder>(
    MANAGE_SERIESCATEGORY_COMPERATOR) {

    var currentAccount: Accounts? = null

    inner class ViewHolder(val binding: RvItemMoviesBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(serie: SeriesOB) {
            binding.apply {
                tvMovies.text = serie.seriesName

                val image = serie.screenshot_uri
                if (!image.isNullOrEmpty()) {
                    ivMovies.load(image)
                }

                binding.tvIsFavorite.visibility = if (serie.isFavorite) View.VISIBLE else View.INVISIBLE

                binding.tvIsFullyWatched.visibility = if (serie.isCompletelyWatched) View.VISIBLE else View.INVISIBLE

                binding.tvIsPartlyWatched.visibility = if (serie.isPartlyWatched) View.VISIBLE else View.INVISIBLE

                binding.cardviewMovie.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.closeFragment()
                        return@setOnKeyListener true
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                        if (bindingAdapterPosition <= 6) {
                            fragment.focusToSearchText()
                            return@setOnKeyListener true
                        }
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                        if (bindingAdapterPosition == itemCount - 1) {
                            binding.cardviewMovie.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                    return@setOnKeyListener false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemMoviesBinding.inflate(
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
        holder.binding.cardviewMovie.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvMovies.isSelected = hasFocus
        }
        holder.binding.cardviewMovie.setOnClickListener {
            onClickListener.onClick(movie)
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