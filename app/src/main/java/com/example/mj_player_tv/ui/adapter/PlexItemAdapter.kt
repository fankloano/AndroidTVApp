package com.example.mj_player_tv.ui.adapter


import android.annotation.SuppressLint
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.help.Movie
import com.example.mj_player_tv.databinding.RvItemMoviesBinding
import com.example.mj_player_tv.network.model.plex.items.Metadata
import com.example.mj_player_tv.ui.MoviesFragment
import com.example.mj_player_tv.ui.PlexFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class PlexItemAdapter(private val onClickListener: PlexItemAdapter.OnClickListener, private val onLongClickListener: PlexItemAdapter.OnLongClickListener, private val fragment: PlexFragment, private val helpViewModel: HelpViewModel) : PagingDataAdapter<Metadata, PlexItemAdapter.ViewHolder>(
    MANAGE_ITEM_COMPERATOR) {


    inner class ViewHolder(val binding: RvItemMoviesBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Metadata) {
            binding.apply {
                tvMovies.text = item.title

                val images = item.Image
                val url = helpViewModel.currentPlexAccount?.stalkerUrl
                val token = helpViewModel.currentPlexAccount?.token
                val image = images?.firstOrNull { it.type == "coverPoster" }?.url
                val imageUrl = "$url$image?X-Plex-Token=$token"
                if (image != null && url != null) {
                    ivMovies.load(imageUrl)
                }

                binding.tvIsFavorite.visibility = if (item.isFavorite == true) View.VISIBLE else View.INVISIBLE

                binding.tvIsFullyWatched.visibility = if (item.type == "movie") {
                    if (item.viewCount == 1) View.VISIBLE else View.INVISIBLE
                } else if (item.type == "show") {
                    if (item.leafCount == item.viewedLeafCount) View.VISIBLE else View.INVISIBLE
                } else {
                    View.INVISIBLE
                }

                binding.tvIsPartlyWatched.visibility = if (item.type == "movie") {
                    if (item.viewCount != 1 && item.viewOffset != 0L) View.VISIBLE else View.INVISIBLE
                } else if (item.type == "show") {
                    if (item.viewedLeafCount != 0 && item.leafCount != item.viewedLeafCount) View.VISIBLE else View.INVISIBLE
                } else {
                    View.INVISIBLE
                }

                binding.cardviewTvchannel.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                        // Rufen Sie die Funktion im Fragment auf
                        if (bindingAdapterPosition % 7 == 0) {
                            fragment.showCategories()
                            fragment.setFocusToPlexCategories()
                            return@setOnKeyListener true
                        }
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.showCategories()
                        fragment.setFocusToPlexCategories()
                        return@setOnKeyListener true
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                        if (bindingAdapterPosition <= 6) {
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
        val item = getItem(position)!!
        holder.bind(item)
        holder.binding.cardviewTvchannel.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvMovies.isSelected = hasFocus
            if (hasFocus && helpViewModel.currentPlexItemId != item.guid) {
                fragment.resetDetailsUi()
                fragment.updateUi(item, position + 1)
            } else {
            }
        }
        holder.binding.cardviewTvchannel.setOnClickListener {
            onClickListener.onClick(item)
        }
        holder.binding.cardviewTvchannel.setOnLongClickListener {

            true
        }
    }

    companion object {
        private val MANAGE_ITEM_COMPERATOR = object : DiffUtil.ItemCallback<Metadata>() {
            override fun areItemsTheSame(oldItem: Metadata, newItem: Metadata) =
                oldItem.key == newItem.key


            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: Metadata, newItem: Metadata) =
                oldItem.key == newItem.key
        }
    }

    class OnClickListener(val clickListener: (item: Metadata) -> Unit) {
        fun onClick(item: Metadata) = clickListener(item)
    }

    class OnLongClickListener(val longClickListener: (item: Metadata, position: Int) -> Unit) {
        fun onLongClick(item: Metadata, position: Int) = longClickListener(item, position)
    }
}