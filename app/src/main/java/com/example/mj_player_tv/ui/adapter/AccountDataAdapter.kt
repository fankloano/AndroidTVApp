package com.example.mj_player_tv.ui.adapter

import android.annotation.SuppressLint
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.databinding.RvItemPlaylistBinding
import com.example.mj_player_tv.viewmodel.HelpViewModel
import java.text.SimpleDateFormat
import java.util.Date

class AccountDataAdapter(private val onClickListener: AccountDataAdapter.OnClickListener, private val helpViewModel: HelpViewModel) : ListAdapter<Accounts, AccountDataAdapter.ViewHolder>(
    ACCOUNT_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: Accounts) {
            binding.apply {
                Log.d("ACCOUNTS", "${data.name} = ${data.expiryDate}")
                val playlistName = data.name
                tvAccountName.text = playlistName
                val expiryString = data.expiryDate
                if (expiryString.isNotEmpty()) {
                    if (expiryString == "Unlimited") {
                        tvAccountExpiry.text = "Unlimited"
                    } else {
                        if (data.isStalker) {
                            val pattern = "(\\w+\\s\\d{1,2}),\\s(\\d{4}),\\s(.+)"
                            val regex = Regex(pattern)
                            val matchResult = regex.find(expiryString)
                            if (matchResult != null) {
                                val (dateString, year, _) = matchResult.destructured // Zeit wird ignoriert
                                val outputString = "$dateString, $year"
                                tvAccountExpiry.text = outputString
                            }
                        } else if (data.isXtream) {
                            val outputString = formatUnixTimestamp(expiryString)
                            tvAccountExpiry.text = outputString
                        } else {

                        }
                    }
                } else {
                    binding.tvAccountExpiry.text = "No Information"
                }
                if (data.isStalker) {
                    tvProvider.text = "Stalker Portal"
                } else if (data.isXtream) {
                    tvProvider.text = "Xtream Codes"
                } else {
                    tvProvider.text = "Plex Account"
                }
                binding.cardviewPlaylist.setOnKeyListener { v, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                        if (bindingAdapterPosition +1 == itemCount) {
                            binding.cardviewPlaylist.nextFocusDownId = R.id.cardview_playlist
                        }
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                        binding.cardviewPlaylist.nextFocusLeftId = R.id.cardview_playlist
                    }
                    return@setOnKeyListener false
                }
            }
        }

        @SuppressLint("SimpleDateFormat")
        fun formatUnixTimestamp(timestampString: String): String {
            // Versuche, den String in einen Long-Wert zu konvertieren
            val timestamp = timestampString.toLongOrNull()
                ?: return "Invalid timestamp"  // Fehlerbehandlung, wenn der String keine gültige Zahl ist

            // Erstelle ein Date-Objekt aus dem Unix-Zeitstempel
            val date = Date(timestamp * 1000) // Unix-Zeitstempel ist in Sekunden, Date erwartet Millisekunden

            // Erstelle einen SimpleDateFormat-Formatter für das gewünschte Datumsformat
            val sdf = SimpleDateFormat("MMMM d, yyyy")

            // Formatieren des Date-Objekts
            return sdf.format(date)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemPlaylistBinding.inflate(
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
        holder.itemView.setOnClickListener {
            onClickListener.onClick(accountdata)
        }
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvAccountName.isSelected = hasFocus
            holder.binding.tvAccountExpiry.isSelected = hasFocus
            holder.binding.tvProvider.isSelected = hasFocus
        }
    }

    companion object {
        private val ACCOUNT_COMPERATOR = object : DiffUtil.ItemCallback<Accounts>() {
            override fun areItemsTheSame(oldItem: Accounts, newItem: Accounts) =
                oldItem.totalAccountData == newItem.totalAccountData


            override fun areContentsTheSame(oldItem: Accounts, newItem: Accounts) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (account: Accounts) -> Unit) {
        fun onClick(account: Accounts) = clickListener(account)
    }
}