package com.example.mj_player_tv.ui.adapter


import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.MovieCategoryOB
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.RvItemGlobalsearchHistoryBinding
import com.example.mj_player_tv.databinding.RvItemMoviecategoryBinding
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.ui.MoviesFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import com.example.mj_player_tv.viewmodel.StalkerViewModel

@UnstableApi
class GlobalSearchHistoryAdapter(private val onclicklistener: GlobalSearchHistoryAdapter.OnClickListener, private val onLongClickListener: OnLongClickListener, private val fragment: GlobalSearchFragment, private val helpViewModel: HelpViewModel) : ListAdapter<String, GlobalSearchHistoryAdapter.ViewHolder>(
    SEARCHHISTORY_COMPERATOR) {

    inner class ViewHolder(val binding: RvItemGlobalsearchHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        @UnstableApi
        fun bind(searchTerm: String) {
            binding.apply {
                tvSearchterm.text = searchTerm
            }

            binding.rvLinearSearchHistory.setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    // Rufen Sie die Funktion im Fragment auf


                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemGlobalsearchHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)
        return viewHolder
    }

    @UnstableApi
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val searchterm = getItem(position)!!
        holder.bind(searchterm)

        holder.binding.rvLinearSearchHistory.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvSearchterm.isSelected = hasFocus
        }

        holder.binding.rvLinearSearchHistory.setOnClickListener {
            onclicklistener.onClick(searchterm)
        }

        holder.binding.rvLinearSearchHistory.setOnLongClickListener {
            onLongClickListener.onLongClick(searchterm)
            true
        }
    }


    companion object {
        private val SEARCHHISTORY_COMPERATOR = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String) =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: String, newItem: String) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (searchterm: String) -> Unit) {
        fun onClick(searchterm: String) = clickListener(searchterm)
    }

    class OnLongClickListener(val onglongclicklistener: (searchterm: String) -> Unit) {
        fun onLongClick(searchterm: String) = onglongclicklistener(searchterm)
    }
}