package com.example.mj_player_tv.ui.adapter

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.EpgSource
import com.example.mj_player_tv.databinding.RvItemEpgsourceBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EpgSourcesAdapter(private val onClickListener: EpgSourcesAdapter.OnClickListener, private val helpViewModel: HelpViewModel) : ListAdapter<EpgSource, EpgSourcesAdapter.ViewHolder>(
    ACCOUNT_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemEpgsourceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(epgSource: EpgSource) {
            binding.apply {
                if (!epgSource.name.isNullOrEmpty()) {
                    tvEpgsourceName.text = epgSource.name
                } else {
                    tvEpgsourceName.text = epgSource.url
                }

                if (epgSource.lastUpdatedDate != 0L) {
                    tvLastUpdated.text = convertUnixTimestampToDateTime(epgSource.lastUpdatedDate)
                } else {
                    tvLastUpdated.text = "Not updated yet"
                }

                val drawableResId = if (epgSource.updateSuccessful) {
                    R.drawable.ic_check_green // Drawable für erfolgreichen Zustand
                } else {
                    R.drawable.ic_error_red // Drawable für nicht erfolgreichen Zustand
                }
                ivUpdatesuccess.setImageResource(drawableResId)

                if (epgSource.isExternalEpg) {
                    tvExternalOrPlaylist.text = "External Epg"
                } else {
                    tvExternalOrPlaylist.text = "Playlist Epg"
                }
                binding.cardviewEpgsource.setOnKeyListener { v, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                        if (bindingAdapterPosition +1 == itemCount) {
                            binding.cardviewEpgsource.nextFocusDownId = R.id.cardview_epgsource
                        }
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                        binding.cardviewEpgsource.nextFocusLeftId = R.id.cardview_epgsource
                    }
                    return@setOnKeyListener false
                }
            }
            binding.tvEpgsourceName.postDelayed({ binding.tvEpgsourceName.isSelected = true }, 2000)
        }

        fun convertUnixTimestampToDateTime(unixTimestamp: Long): String {
            val date = Date(unixTimestamp * 1000) // Unix-Timestamp in Millisekunden umwandeln
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) // Format festlegen
            return dateFormat.format(date) // Datum in String umwandeln
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemEpgsourceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)

        if (viewType == 0) {
            // Setze den Fokus auf das erste Element
            viewHolder.itemView.nextFocusUpId = R.id.relLayout_addpl_btn
        }

        return viewHolder
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val accountdata = getItem(position)!!
        holder.bind(accountdata)
        holder.binding.cardviewEpgsource.setOnClickListener {
            onClickListener.onClick(accountdata, position)
        }
        holder.binding.cardviewEpgsource.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvEpgsourceName.isSelected = hasFocus
        }
    }

    companion object {
        private val ACCOUNT_COMPERATOR = object : DiffUtil.ItemCallback<EpgSource>() {
            override fun areItemsTheSame(oldItem: EpgSource, newItem: EpgSource) =
                oldItem.id == newItem.id


            override fun areContentsTheSame(oldItem: EpgSource, newItem: EpgSource) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (account: EpgSource, position: Int) -> Unit) {
        fun onClick(account: EpgSource, position: Int) = clickListener(account, position)
    }
}