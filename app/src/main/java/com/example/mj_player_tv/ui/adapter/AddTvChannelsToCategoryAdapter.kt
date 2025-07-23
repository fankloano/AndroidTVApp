package com.example.mj_player_tv.ui.adapter


import android.app.AlertDialog
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.RvItemAddchannelsPlaylistsBinding
import com.example.mj_player_tv.databinding.RvItemManageTvchannelsBinding
import com.example.mj_player_tv.ui.settings.AddChannelsToUserCategoryChannelsFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import io.objectbox.Box

@UnstableApi
class AddTvChannelsToCategoryAdapter(private val onClickListener: AddTvChannelsToCategoryAdapter.OnClickListener, private val helpViewModel: HelpViewModel, private val fragment: AddChannelsToUserCategoryChannelsFragment, private val manualPosBox: Box<ChannelPositions>) : ListAdapter<ChannelPositions, AddTvChannelsToCategoryAdapter.ViewHolder>(
    MANAGE_TVCHANNELS_COMPERATOR) {

    var channelListToAdd: MutableList<TvChannelOB>? = mutableListOf()

    var channelListAlreadyInCategory: MutableList<TvChannelOB>? = mutableListOf()

    inner class ViewHolder(val binding: RvItemAddchannelsPlaylistsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tvchannelPos: ChannelPositions) {
            binding.apply {
                val tvchannel = tvchannelPos.tvchannel.target
                tvTvChannels.text = tvchannel.showingName

            }
            val tvchannel = tvchannelPos.tvchannel.target
            binding.relLayoutItemManage.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.action == KeyEvent.ACTION_DOWN) {
                    if (binding.cbTvChannels.isChecked) {
                        if (channelListAlreadyInCategory != null && channelListAlreadyInCategory?.contains(tvchannel) == true) {
                            openRemoveChannelDialog(tvchannelPos)
                        } else {
                            channelListToAdd?.remove(tvchannel)
                            notifyItemChanged(bindingAdapterPosition)
                        }
                    } else {
                        if (channelListAlreadyInCategory != null && channelListAlreadyInCategory?.contains(tvchannel) == true) {
                            fragment.showAlreadyInCategoryToast()
                        } else {
                            channelListToAdd?.add(tvchannel)
                            notifyItemChanged(bindingAdapterPosition)
                        }
                    }
                    return@setOnKeyListener true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    fragment.focusToMenu()
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemAddchannelsPlaylistsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val viewHolder = ViewHolder(binding)

        if (viewType == 0) {
            // Setze den Fokus auf das erste Element
            viewHolder.itemView.nextFocusUpId = R.id.btn_selectAll
        }

        return viewHolder
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tvChannel = getItem(position)!!
        holder.bind(tvChannel)
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = true

        // Zustände berechnen
        val isInAlreadyCategory = channelListAlreadyInCategory?.contains(tvChannel.tvchannel.target) == true
        val isInAddList = channelListToAdd?.contains(tvChannel.tvchannel.target) == true
        val isChecked = isInAlreadyCategory || isInAddList

        // Zustände setzen
        holder.binding.relLayoutItemManage.isActivated = isInAlreadyCategory
        holder.binding.cbTvChannels.isChecked = isChecked
        holder.binding.cbTvChannels.isActivated = isChecked

        holder.binding.relLayoutItemManage.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvTvChannels.isSelected = hasFocus
            holder.binding.cbTvChannels.isSelected = hasFocus
        }
    }

    companion object {
        private val MANAGE_TVCHANNELS_COMPERATOR = object : DiffUtil.ItemCallback<ChannelPositions>() {
            override fun areItemsTheSame(oldItem: ChannelPositions, newItem: ChannelPositions) =
                oldItem.catAndChannelAccount == newItem.catAndChannelAccount


            override fun areContentsTheSame(oldItem: ChannelPositions, newItem: ChannelPositions) =
                oldItem == newItem
        }
    }

    class OnClickListener(val clickListener: (tvChannel: ChannelPositions) -> Unit) {
        fun onClick(tvChannel: ChannelPositions) = clickListener(tvChannel)
    }

    fun passChannelList(channelList: List<TvChannelOB>) {
        channelListToAdd?.clear()
        channelListToAdd?.addAll(channelList)
    }

    fun retrieveChannelListToAdd(): List<TvChannelOB>? {
        return channelListToAdd
    }

    fun removeChannelFromCategory(tvchannelPos: ChannelPositions) {
        val positionToRemove = helpViewModel.categoryToAddChannelsInto?.tvChannelLink?.firstOrNull {
            it.id == tvchannelPos.id
        }
        if (positionToRemove != null) {
            manualPosBox.remove(positionToRemove)
        }
        channelListAlreadyInCategory?.remove(tvchannelPos.tvchannel.target)
        val itsPosition = currentList.indexOf(tvchannelPos)
        notifyItemChanged(itsPosition)
    }

    fun openRemoveChannelDialog(tvChannelPos: ChannelPositions) {
        val alertDialogBuilder = AlertDialog.Builder(fragment.requireActivity())

        alertDialogBuilder.setMessage("Remove channel from ${helpViewModel.categoryToAddChannelsInto?.showingName} ?")

        alertDialogBuilder.setPositiveButton("Yes") { _, _ ->
            removeChannelFromCategory(tvChannelPos)
        }

        alertDialogBuilder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }

        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }
}