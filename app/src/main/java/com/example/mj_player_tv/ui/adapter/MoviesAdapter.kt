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
import com.example.mj_player_tv.database.help.Movie
import com.example.mj_player_tv.databinding.RvItemMoviesBinding
import com.example.mj_player_tv.ui.MoviesFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class MoviesAdapter(private val onClickListener: MoviesAdapter.OnClickListener, private val onLongClickListener: MoviesAdapter.OnLongClickListener, private val fragment: MoviesFragment, private val helpViewModel: HelpViewModel) : ListAdapter<MovieOB, MoviesAdapter.ViewHolder>(
    MANAGE_MOVIECATEGORY_COMPERATOR) {

    var currentAccount: Accounts? = null

    inner class ViewHolder(val binding: RvItemMoviesBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(movie: MovieOB) {
            binding.apply {
                tvMovies.text = movie.movieName

                val image = movie.screenshot_uri
                if (!image.isNullOrEmpty()) {
                    ivMovies.load(image)
                }

                binding.tvIsFavorite.visibility = if (movie.isFavorite) View.VISIBLE else View.INVISIBLE

                binding.tvIsFullyWatched.visibility = if (movie.isCompletelyWatched) View.VISIBLE else View.INVISIBLE

                binding.tvIsPartlyWatched.visibility = if (movie.isPartlyWatched) View.VISIBLE else View.INVISIBLE

                binding.cardviewTvchannel.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                        // Rufen Sie die Funktion im Fragment auf
                        if (bindingAdapterPosition % 7 == 0) {
                            fragment.setMovieAccountsVisibilityAnimated(true)
                            return@setOnKeyListener true
                        }
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.setMovieAccountsVisibilityAnimated(true)
                        return@setOnKeyListener true
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                        if (bindingAdapterPosition <= 6) {
                            fragment.focusToSortButton()
                            return@setOnKeyListener true
                        }
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                        if (bindingAdapterPosition == itemCount - 1) {
                            binding.cardviewTvchannel.requestFocus()
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
        holder.binding.cardviewTvchannel.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvMovies.isSelected = hasFocus
            if (hasFocus) {
                fragment.updateUi(movie)
            } else {
            }
        }
        holder.binding.cardviewTvchannel.setOnClickListener {
            onClickListener.onClick(movie)
        }
        holder.binding.cardviewTvchannel.setOnLongClickListener {
            onLongClickListener.onLongClick(movie, position)
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

    class OnLongClickListener(val longClickListener: (movie: MovieOB, position: Int) -> Unit) {
        fun onLongClick(movie: MovieOB, position: Int) = longClickListener(movie, position)
    }
}