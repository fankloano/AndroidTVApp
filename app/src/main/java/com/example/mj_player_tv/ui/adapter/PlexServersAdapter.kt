package com.example.mj_player_tv.ui.adapter

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.Accounts_
import com.example.mj_player_tv.database.entity.MyObjectBox
import com.example.mj_player_tv.databinding.RvItemPlaylistBinding
import com.example.mj_player_tv.databinding.RvItemPlexserverBinding
import com.example.mj_player_tv.network.model.plex.library.PlexGetUserLibraries
import com.example.mj_player_tv.network.model.plex.resources.PlexGetUserResources
import com.example.mj_player_tv.viewmodel.HelpViewModel
import io.objectbox.Box
import java.text.SimpleDateFormat
import java.util.Date

class PlexServersAdapter(private val onClickListener: PlexServersAdapter.OnClickListener, private val helpViewModel: HelpViewModel, private val accountBox: Box<Accounts>) : ListAdapter<PlexGetUserResources, PlexServersAdapter.ViewHolder>(
    ACCOUNT_COMPERATOR) {

    val accounts = accountBox.query(
        Accounts_.isPlex.equal(true)
    ).build().find().map { it.plexClientIdentifier }

    inner class ViewHolder(val binding: RvItemPlexserverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: PlexGetUserResources) {
            binding.apply {
                tvPlexServerName.text = data.name

                if (accounts.contains(data.clientIdentifier)) {
                    ivPlexadded.visibility = View.VISIBLE
                } else {
                    ivPlexadded.visibility = View.INVISIBLE
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemPlexserverBinding.inflate(
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
        val data = getItem(position)!!
        holder.bind(data)
        holder.itemView.setOnClickListener {
            onClickListener.onClick(data)
        }
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvPlexServerName.isSelected = hasFocus
        }
    }

    companion object {
        private val ACCOUNT_COMPERATOR = object : DiffUtil.ItemCallback<PlexGetUserResources>() {
            override fun areItemsTheSame(oldItem: PlexGetUserResources, newItem: PlexGetUserResources) =
                oldItem.name == newItem.name


            override fun areContentsTheSame(oldItem: PlexGetUserResources, newItem: PlexGetUserResources) =
                oldItem.name == newItem.name
        }
    }

    class OnClickListener(val clickListener: (server: PlexGetUserResources) -> Unit) {
        fun onClick(server: PlexGetUserResources) = clickListener(server)
    }
}