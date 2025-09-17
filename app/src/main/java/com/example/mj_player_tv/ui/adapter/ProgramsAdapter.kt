package com.example.mj_player_tv.ui.adapter

import android.util.Log
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.utils.views.ProgramTextView
import com.example.mj_player_tv.viewmodel.TvGuideViewModel

class ProgramsAdapter(
    private val tvGuideViewModel: TvGuideViewModel
) : ListAdapter<EpgDataOB, ProgramsAdapter.ProgramViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<EpgDataOB>() {
        override fun areItemsTheSame(oldItem: EpgDataOB, newItem: EpgDataOB): Boolean {
            // Programme sind eindeutig durch Startzeit + Ende
            return oldItem.startTimestamp == newItem.startTimestamp && oldItem.stopTimestamp == newItem.stopTimestamp
        }

        override fun areContentsTheSame(oldItem: EpgDataOB, newItem: EpgDataOB): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgramViewHolder {
        val tv = ProgramTextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.WRAP_CONTENT,
                RecyclerView.LayoutParams.MATCH_PARENT
            )
        }
        return ProgramViewHolder(tv)
    }

    override fun onBindViewHolder(holder: ProgramViewHolder, position: Int) {
        val item = getItem(position)

        // Berechne Start- und Endzeit relativ zur Timeline
        val visibleStart = maxOf(item.startTimestamp ?: 0L, tvGuideViewModel.timeLineStartSec)
        val visibleEnd = item.stopTimestamp ?: tvGuideViewModel.timeLineStartSec

        // Dauer in Minuten für die sichtbare Länge
        val durationMinutes = ((visibleEnd - visibleStart) / 60).toInt().coerceAtLeast(1)
        val startOffsetMinutes = ((visibleStart - tvGuideViewModel.timeLineStartSec) / 60).toInt()

        // Setze Breite in Pixeln
        holder.textView.layoutParams.width = (durationMinutes * 5f).toInt()
        val modStart = startOffsetMinutes % 30
        val modEnd = (startOffsetMinutes + durationMinutes) % 30
        val startX = startOffsetMinutes * 5
        val width = durationMinutes * 5
       Log.d(
            "ProgramsCheck",
            "Program=${item.name} " +
                    "| StartMin=$startOffsetMinutes EndMin=${startOffsetMinutes + durationMinutes} " +
                    "| StartX=$startX Width=$width " +
                    "| AlignStart=${if (modStart == 0) "OK" else "OFF($modStart)"} " +
                    "| AlignEnd=${if (modEnd == 0) "OK" else "OFF($modEnd)"}")

        // Text
        holder.textView.text = item.name
    }

    class ProgramViewHolder(val textView: ProgramTextView) : RecyclerView.ViewHolder(textView)

}
