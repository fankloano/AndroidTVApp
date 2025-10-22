package com.example.mj_player_tv.ui.epg

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.database.help.TimeLineData
import com.example.mj_player_tv.databinding.VgItemEpgTimelineBinding
import java.util.concurrent.TimeUnit

// TimeAdapter.kt

// 1. Erbe von RecyclerView.Adapter, nicht ListAdapter
class EpgTimeLineAdapter : RecyclerView.Adapter<EpgTimeLineAdapter.TimeViewHolder>() {

    // 2. Speichere nur die Startzeit, nicht die ganze Liste
    private var startUtcMs: Long = 0L
    private val timeStepMinutes = 30
    private val stepWidthPx = timeStepMinutes * EpgUtils.minuteToPixel

    // 3. Der ViewHolder bleibt gleich (fast, siehe Schritt 2)
    class TimeViewHolder(private val binding: VgItemEpgTimelineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.isFocusable = false
        }

        // Diese Methode passen wir in Schritt 2 an
        fun bind(time: String, width: Int) {
            binding.root.layoutParams.width = width
            binding.tvTime.text = time
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeViewHolder {
        val binding = VgItemEpgTimelineBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TimeViewHolder(binding)
    }

    // 4. Melde eine "unendliche" Anzahl an Items
    override fun getItemCount(): Int = Integer.MAX_VALUE

    // 5. DAS IST DER WICHTIGSTE TEIL: Berechne den Inhalt live
    override fun onBindViewHolder(holder: TimeViewHolder, position: Int) {
        // Berechne die Zeit für DIESE Position
        val timeStepMs = TimeUnit.MINUTES.toMillis(timeStepMinutes.toLong())
        val currentItemTimeMs = startUtcMs + (position * timeStepMs)

        // Formatiere die Zeit (mit deiner Joda-Time Logik)
        val timeDate = org.joda.time.DateTime(currentItemTimeMs)
        val timeString = timeDate.toString("HH:mm")

        // Binde die berechneten Daten
        holder.bind(timeString, stepWidthPx)
    }

    // 6. Füge eine Methode hinzu, um die Startzeit zu setzen
    fun updateStartTime(newStartMs: Long) {
        this.startUtcMs = newStartMs
        // Hier ist notifyDataSetChanged() korrekt, da sich die Basis für
        // ALLE Berechnungen geändert hat.
        notifyDataSetChanged()
    }
}