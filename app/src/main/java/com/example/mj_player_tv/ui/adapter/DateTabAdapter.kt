package com.example.mj_player_tv.ui.adapter

import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.databinding.RvItemFullepgTabsBinding
import com.example.mj_player_tv.ui.FullEpgFragment
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.TextStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@UnstableApi
class DateTabAdapter(private val fragment: FullEpgFragment, private val helpViewModel: HelpViewModel) : ListAdapter<String, DateTabAdapter.ViewHolder>(
    DATE_COMPERATOR) {

    private var selectedDate: String = ""

    inner class ViewHolder(val binding: RvItemFullepgTabsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(date: String) {
            val formattedDate = formatDate(date)
            binding.tvDateTab.text = formattedDate
            binding.mainTab.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    fragment.closeFragment()
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    fragment.setFocusToEpgData()
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
        }

        private fun formatDate(date: String): String {
            // Definiere das Format des Eingabestrings, da es im Format 'yyyy-MM-dd' ist
            val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

            // Parse das Datum im richtigen Format
            val parsedDate = LocalDate.parse(date, inputFormatter)

            // Hole die Monatsabbreviation und den Wochentag
            val monthAbbreviation = parsedDate.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            val dayOfWeekAbbreviation = parsedDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            val dayOfMonth = parsedDate.dayOfMonth

            return "$monthAbbreviation | $dayOfWeekAbbreviation $dayOfMonth"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemFullepgTabsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val date = getItem(position)
        holder.bind(date)
        holder.binding.mainTab.isSelected = date == selectedDate
        holder.binding.mainTab.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                Log.d("FULLEPGFOKUS", "TAB FOCUSED: $date")
                fragment.checkIfExternStalkerOrXtream(date)
            }
        }
    }

    companion object {
        private val DATE_COMPERATOR = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String) =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: String, newItem: String) =
                oldItem == newItem
        }
    }

    fun updateSelectedPosition(newDate: String) {
        val previousPosition = currentList.indexOf(selectedDate)
        selectedDate = newDate
        val newPosition = currentList.indexOf(newDate)
        // Benachrichtige Adapter über Änderungen

        notifyItemChanged(previousPosition)
        notifyItemChanged(newPosition)
    }
}