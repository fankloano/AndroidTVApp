package com.example.mj_player_tv.ui.adapter

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.SeriesOB
import com.example.mj_player_tv.database.help.Movie
import com.example.mj_player_tv.database.help.Serie
import com.example.mj_player_tv.databinding.RvItemMoviesBinding
import com.example.mj_player_tv.databinding.RvItemSeriesBinding
import com.example.mj_player_tv.ui.MoviesFragment
import com.example.mj_player_tv.ui.SeriesFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel

@UnstableApi
class StalkerSeriesAdapter(
    private val onClickListener: OnClickListener,
    private val onLongClickListener: OnLongClickListener,
    private val fragment: SeriesFragment,
    private val helpViewModel: HelpViewModel
) : PagingDataAdapter<SeriesOB, StalkerSeriesAdapter.ViewHolder>(SERIE_COMPARATOR) {

    var currentAccount: Accounts? = null

    inner class ViewHolder(val binding: RvItemSeriesBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(serie: SeriesOB?) {
            if (serie == null) {
                return
            }
            binding.apply {
                tvSeries.text = serie.seriesName

                val image = serie.screenshot_uri
                if (!image.isNullOrEmpty()) {
                    ivSeries.load(image)
                }

                binding.tvIsFavorite.visibility = if (serie.isFavorite) View.VISIBLE else View.INVISIBLE
                binding.tvIsFullyWatched.visibility = if (serie.isCompletelyWatched) View.VISIBLE else View.INVISIBLE
                binding.tvIsPartlyWatched.visibility = if (serie.isPartlyWatched) View.VISIBLE else View.INVISIBLE

                binding.cardviewSerie.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                        if (bindingAdapterPosition % 7 == 0) {
                            fragment.setSeriesAccountsVisibilityAnimated(true)
                            return@setOnKeyListener true
                        }
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.setSeriesAccountsVisibilityAnimated(true)
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
                            binding.cardviewSerie.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                    return@setOnKeyListener false
                }
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemSeriesBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val serie = getItem(position)
        holder.bind(serie)
        holder.binding.cardviewSerie.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvSeries.isSelected = hasFocus
            if (hasFocus) {
                serie?.let { fragment.updateUi(it) }
            }
        }
        holder.binding.cardviewSerie.setOnClickListener {
            serie?.let { onClickListener.onClick(it) }
        }
    }

    companion object {
        private val SERIE_COMPARATOR = object : DiffUtil.ItemCallback<SeriesOB>() {
            override fun areItemsTheSame(oldItem: SeriesOB, newItem: SeriesOB) =
                oldItem.idByAccountData == newItem.idByAccountData

            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: SeriesOB, newItem: SeriesOB) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (serie: SeriesOB) -> Unit) {
        fun onClick(serie: SeriesOB) = clickListener(serie)
    }

    class OnLongClickListener(val longClickListener: (serie: SeriesOB, position: Int) -> Unit) {
        fun onLongClick(serie: SeriesOB, position: Int) = longClickListener(serie, position)
    }
}
