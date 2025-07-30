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
import com.example.mj_player_tv.databinding.RvItemGlobalsearchmoviesBinding
import com.example.mj_player_tv.databinding.RvItemMoviesBinding
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.ui.SearchMovieByCategoryFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class GlobalSearchMoviesAdapter(private val onClickListener: GlobalSearchMoviesAdapter.OnClickListener, private val fragment: GlobalSearchFragment, private val helpViewModel: HelpViewModel) : ListAdapter<MovieOB, GlobalSearchMoviesAdapter.ViewHolder>(
    MANAGE_MOVIECATEGORY_COMPERATOR) {

    var currentAccount: Accounts? = null

    inner class ViewHolder(val binding: RvItemGlobalsearchmoviesBinding) : RecyclerView.ViewHolder(binding.root) {

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
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    if ((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) {
                        if (bindingAdapterPosition in 0..6) {
                            fragment.focusToPlaylist()
                            return@setOnKeyListener true
                        }
                    }
                    return@setOnKeyListener false
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemGlobalsearchmoviesBinding.inflate(
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
        }
        holder.binding.cardviewTvchannel.setOnClickListener {
            onClickListener.onClick(movie)
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

}