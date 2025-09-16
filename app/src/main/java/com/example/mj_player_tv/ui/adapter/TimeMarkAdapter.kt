package com.example.mj_player_tv.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R

class TimeMarkAdapter(
    private val times: List<String>
) : RecyclerView.Adapter<TimeMarkAdapter.TimeMarkViewHolder>() {

    inner class TimeMarkViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeMarkViewHolder {
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.rv_item_tvguide_timeline, parent, false) as TextView
        return TimeMarkViewHolder(textView)
    }

    override fun onBindViewHolder(holder: TimeMarkViewHolder, position: Int) {
        holder.textView.text = times[position]
    }

    override fun getItemCount(): Int = times.size
}
