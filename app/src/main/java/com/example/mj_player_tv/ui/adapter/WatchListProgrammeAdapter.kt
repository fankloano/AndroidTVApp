package com.example.mj_player_tv.ui.adapter


import android.annotation.SuppressLint
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.database.help.Movie
import com.example.mj_player_tv.database.help.WatchlistDisplayItem
import com.example.mj_player_tv.databinding.RvItemMoviesBinding
import com.example.mj_player_tv.databinding.RvItemWatchlistProgrammeBinding
import com.example.mj_player_tv.ui.MoviesFragment
import com.example.mj_player_tv.ui.WatchListFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import org.threeten.bp.DateTimeException
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
class WatchListProgrammeAdapter(private val onClickListener: WatchListProgrammeAdapter.OnClickListener, private val fragment: WatchListFragment, private val helpViewModel: HelpViewModel) : ListAdapter<WatchlistDisplayItem.ProgramItem, WatchListProgrammeAdapter.ViewHolder>(
    MANAGE_MOVIECATEGORY_COMPERATOR) {

    var currentAccount: Accounts? = null

    inner class ViewHolder(val binding: RvItemWatchlistProgrammeBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WatchlistDisplayItem.ProgramItem) {
            binding.apply {
                val epgdata = item.programs.epgData.target
                val tvChannel = item.programs.tvchannels.target
                val image = tvChannel.logo
                val linkedEpgChannel = tvChannel.linkedEpgChannel?.target
                val epgLogo = linkedEpgChannel?.icon?.firstOrNull()
                tvProgram.text = epgdata.name

                tvChannelName.text = tvChannel.showingName

                if (epgdata.sub_title.isNotEmpty()) {
                    tvSubTitleProgram.visibility = View.VISIBLE
                    tvSubTitleProgram.text = epgdata.sub_title
                } else {
                    tvSubTitleProgram.visibility = View.GONE
                    tvSubTitleProgram.text = ""
                }


                if (tvChannel.account.target!!.useEpgLogos) {
                    if (!epgLogo.isNullOrEmpty() && (linkedEpgChannel.isExternalEpg || tvChannel.alwaysUsesExternalEpg)) {
                        ivTvchannelLogo.visibility = View.VISIBLE
                        ivTvchannelLogo.load(epgLogo)
                    } else {
                        if (image.isNotEmpty()) {
                            ivTvchannelLogo.visibility = View.VISIBLE
                            ivTvchannelLogo.load(image)
                        } else {
                            ivTvchannelLogo.visibility = View.INVISIBLE
                        }
                    }
                } else {
                    if (image.isNotEmpty()) {
                        ivTvchannelLogo.visibility = View.VISIBLE
                        ivTvchannelLogo.load(image)
                    } else {
                        ivTvchannelLogo.visibility = View.INVISIBLE
                    }
                }

                val timeOffSet = tvChannel.epgTimeOffSet ?: tvChannel.reltvcategory.target?.epgTimeOffSet ?: tvChannel?.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0

                val startTime = formatUnixTimestampToTime(epgdata.startTimestamp!!, timeOffSet)
                val endTime = formatUnixTimestampToTime(epgdata.stopTimestamp!!, timeOffSet)
                binding.tvStartTime.text = startTime
                binding.tvEndTime.text = " - ${endTime}"

                binding.tvDate.text = formatDate(epgdata.datum)

                binding.cardviewWlPr.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToPlaylist()
                        fragment.resetProgramDetails()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition == 0) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }
            }
        }

        fun formatUnixTimestampToTime(unixTimestamp: Long, timeOffset: Int): String {
            try {
                // Konvertiere den Unix-Zeitstempel in ein Date-Objekt
                val date = Date(unixTimestamp * 1000)

                // Erstelle ein SimpleDateFormat-Objekt für das gewünschte Zeitformat
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                // Berechne den Zeitversatz in Stunden (positiv oder negativ)
                val calendar = Calendar.getInstance()
                calendar.time = date
                calendar.add(Calendar.HOUR_OF_DAY, timeOffset)

                // Gib das formatierte Datum und die Uhrzeit zurück
                return timeFormat.format(calendar.time)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ""
        }

        fun formatDate(isoDate: String): String {
            // Parse das Eingangsdatum
            return try {
                val inputFormatter = DateTimeFormatter.ISO_LOCAL_DATE
                val date = LocalDate.parse(isoDate, inputFormatter)

                // Definiere das gewünschte Ausgabeformat
                val outputFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.getDefault())
                    date.format(outputFormatter)
            } catch (e: DateTimeException) {
                ""
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemWatchlistProgrammeBinding.inflate(
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
        holder.binding.cardviewWlPr.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvProgram.isSelected = hasFocus
            holder.binding.tvSubTitleProgram.isSelected = hasFocus
            if (hasFocus) {
                holder.binding.overlayFull.visibility = View.GONE
                fragment.setProgramDetails(item.programs)
            } else {
                holder.binding.overlayFull.visibility = View.VISIBLE
            }
        }
        holder.binding.cardviewWlPr.setOnClickListener {
            onClickListener.onClick(item)
        }
    }

    companion object {
        private val MANAGE_MOVIECATEGORY_COMPERATOR = object : DiffUtil.ItemCallback<WatchlistDisplayItem.ProgramItem>() {
            override fun areItemsTheSame(oldItem: WatchlistDisplayItem.ProgramItem, newItem: WatchlistDisplayItem.ProgramItem) =
                oldItem.programs.epgForCh == newItem.programs.epgForCh


            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: WatchlistDisplayItem.ProgramItem, newItem: WatchlistDisplayItem.ProgramItem) =
                newItem.programs.epgForCh == newItem.programs.epgForCh
        }
    }

    class OnClickListener(val clickListener: (programm: WatchlistDisplayItem.ProgramItem) -> Unit) {
        fun onClick(programm: WatchlistDisplayItem.ProgramItem) = clickListener(programm)
    }

}