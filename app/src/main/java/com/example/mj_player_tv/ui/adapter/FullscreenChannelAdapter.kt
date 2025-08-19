package com.example.mj_player_tv.ui.adapter



import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgDataOB_
import com.example.mj_player_tv.database.entity.TvChannelOB
import com.example.mj_player_tv.databinding.RvItemFullscreenTvchannelsBinding
import com.example.mj_player_tv.network.externalepg.Channel
import com.example.mj_player_tv.ui.FullScreenSelectorFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import io.objectbox.Box
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


@UnstableApi
class FullscreenChannelAdapter(
    private val fragment: FullScreenSelectorFragment,
    private val onClickListener: FullscreenChannelAdapter.OnClickListener,
    private val helpViewModel: HelpViewModel,
    private val epgDataBox: Box<EpgDataOB>
) : ListAdapter<ChannelPositions, FullscreenChannelAdapter.ViewHolder>(
    TVCATEGORY_COMPERATOR) {

    val handler = Handler(Looper.getMainLooper())

    private var progressUpdater: Runnable? = null

    var isFirstLoad = true

    inner class ViewHolder(val binding: RvItemFullscreenTvchannelsBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tvchannelPos: ChannelPositions) {
            binding.apply {
                var hasCurrentEpg = false
                val tvchannel = tvchannelPos.tvchannel.target
                val account = tvchannel.account.target
                val playlistEpgActive = account.usePlaylistEpg
                val linkedEpgChannel = tvchannel.linkedEpgChannel?.target
                tvTvchannelname.text = tvchannel.showingName
                val image = tvchannel.logo
                val epgLogo = linkedEpgChannel?.icon?.firstOrNull()

                if (tvchannel.account.target!!.useEpgLogos) {
                    if (!epgLogo.isNullOrEmpty() && (linkedEpgChannel.isExternalEpg || tvchannel.alwaysUsesExternalEpg)) {
                        ivChannellogoImage.visibility = View.VISIBLE
                        ivChannellogoImage.load(epgLogo)
                        Log.d("LOGO FOR CHANNEL", "${tvchannel.showingName} = EPGLOGO: YES OK")
                    } else {
                        if (image.isNotEmpty()) {
                            ivChannellogoImage.visibility = View.VISIBLE
                            ivChannellogoImage.load(image)
                            Log.d("LOGO FOR CHANNEL", "${tvchannel.showingName} = EPGLOGO: YES BUT EMPTY")
                        } else {
                            ivChannellogoImage.visibility = View.INVISIBLE
                            Log.d("LOGO FOR CHANNEL", "${tvchannel.showingName} = EPGLOGO: NO LOGO")
                        }
                    }
                } else {
                    if (image.isNotEmpty()) {
                        ivChannellogoImage.visibility = View.VISIBLE
                        ivChannellogoImage.load(image)
                        Log.d("LOGO FOR CHANNEL", "${tvchannel.showingName} = PLLOGO: YES BUT EMPTY")
                    } else {
                        ivChannellogoImage.visibility = View.INVISIBLE
                        Log.d("LOGO FOR CHANNEL", "${tvchannel.showingName} = PLLOGO: NO LOGO")
                    }
                }

                CoroutineScope(Dispatchers.IO).launch {

                    val currentTimeSec = System.currentTimeMillis() / 1000

                    val timeOffSetSec = calculateTimeOffsetInSeconds(
                        tvchannel.epgTimeOffSet
                            ?: tvchannelPos.tvcategory.target.epgTimeOffSet
                            ?: tvchannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                            ?: 0
                    )

                    // Hilfsfunktion für verschobene Zeiten
                    fun EpgDataOB.shiftedStart() = (this.startTimestamp ?: 0) + timeOffSetSec
                    fun EpgDataOB.shiftedStop()  = (this.stopTimestamp ?: 0) + timeOffSetSec

                    val epgChid = tvchannel.linkedEpgChannel?.target?.chEpgId ?: if (playlistEpgActive) {
                        tvchannel.epgChannel?.target?.chEpgId
                    } else null

                    if (epgChid != null) {
                        val currentProgram =
                            withContext(Dispatchers.IO) {
                                epgDataBox.query(
                                    EpgDataOB_.epgChId.equal(epgChid)
                                        .and(EpgDataOB_.startTimestamp.less(currentTimeSec - timeOffSetSec))
                                        .and(EpgDataOB_.stopTimestamp.greater(currentTimeSec - timeOffSetSec))
                                )
                                    .build().findFirst()
                            }

                        withContext(Dispatchers.Main) {
                            if (currentProgram != null) {
                                val startTime = formatUnixTimestampToTime(currentProgram.shiftedStart())
                                val endTime   = formatUnixTimestampToTime(currentProgram.shiftedStop())
                                tvCurrentStartTime.text = startTime
                                tvCurrentEndTime.text = " - $endTime"
                                tvCurrentProgram.text = currentProgram.name
                                hasCurrentEpg = true
                                val duration = currentProgram.shiftedStop() - currentProgram.shiftedStart()
                                val progress = ((currentTimeSec - currentProgram.shiftedStart()) * 100 / duration).toInt()
                                progressBar.isInvisible = false
                                progressBar.max = 100
                                progressBar.progress = progress.coerceIn(0, 100)

                                // Progress-Updater
                                progressUpdater?.let {
                                    handler.removeCallbacks(it)
                                    progressUpdater = null
                                }
                                progressUpdater = object : Runnable {
                                    override fun run() {
                                        val now = System.currentTimeMillis() / 1000
                                        if (now >= currentProgram.shiftedStop()) {
                                            if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                                                notifyItemChanged(bindingAdapterPosition)
                                            }
                                        } else {
                                            val currentProgress = progressBar.progress
                                            val newProgress = ((now - currentProgram.shiftedStart()) * 100 /
                                                    (currentProgram.shiftedStop() - currentProgram.shiftedStart())).toInt()

                                            // Sicherstellen, dass es im UI-Thread landet
                                            if (currentProgress != newProgress) {
                                                notifyItemChanged(bindingAdapterPosition)
                                            }
                                        }
                                        handler.postDelayed(this, 10000)
                                    }
                                }
                                handler.post(progressUpdater!!)
                            } else {
                                hasCurrentEpg = false
                                progressBar.progress = 0
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            tvCurrentProgram.text = itemView.context.getString(R.string.no_information)
                            tvCurrentStartTime.text = formatUnixTimestampToTime(currentTimeSec)
                            tvCurrentEndTime.text = " - " + formatUnixTimestampToTime(currentTimeSec + 1800)
                            progressBar.progress = 0
                        }
                    }
                }


                binding.cardViewTvchannel.setOnKeyListener { v, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                        // Rufen Sie die Funktion im Fragment auf
                        fragment.focusToTvCategoryFromChannel()
                        fragment.setTvChannelsVisibilityAnimated(false)
                        fragment.setTvCategoriesVisibilityAnimated(true)
                        return@setOnKeyListener true
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                        // Rufen Sie die Funktion im Fragment auf
                        if (tvchannel.linkedEpgChannel?.target != null) {
                            if (hasCurrentEpg) {
                                fragment.showFullScreenEpg()
                            }
                        }
                        return@setOnKeyListener true
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                        // Rufen Sie die Funktion im Fragment auf
                        fragment.closeFragment()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }
            }
        }
        fun unbind() {
            progressUpdater?.let { handler.removeCallbacks(it) }
            progressUpdater = null
        }
    }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = RvItemFullscreenTvchannelsBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

            val viewHolder = ViewHolder(binding)
            return viewHolder
        }


        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tvchannelPos = getItem(position)!!
            holder.bind(tvchannelPos)
            val tvchannel = tvchannelPos.tvchannel.target
            holder.binding.tvTvchannelname.isActivated = tvchannel.idByAccountData == helpViewModel.currentPlayingChannel?.idByAccountData

            holder.binding.cardViewTvchannel.setOnFocusChangeListener { _, hasFocus ->
                holder.binding.tvTvchannelname.isSelected = hasFocus
                holder.binding.tvCurrentProgram.isSelected = hasFocus
                if (hasFocus) {
                    holder.binding.constCurrentchannelInfo.alpha = 1F
                    helpViewModel.fullScreenFocusedChannelPosition = tvchannelPos
                    fragment.showChannelEpg(tvchannelPos)

                } else {
                    holder.binding.constCurrentchannelInfo.alpha = 0.7F
                }
            }

            holder.binding.cardViewTvchannel.setOnClickListener {
                onClickListener.onClick(tvchannelPos)
            }

        }

        class OnClickListener(val clickListener: (tvchannel: ChannelPositions) -> Unit) {
            fun onClick(tvchannel: ChannelPositions) = clickListener(tvchannel)
        }

        companion object {
            private val TVCATEGORY_COMPERATOR = object : DiffUtil.ItemCallback<ChannelPositions>() {
                override fun areItemsTheSame(oldItem: ChannelPositions, newItem: ChannelPositions) =
                    oldItem.catAndChannelAccount == newItem.catAndChannelAccount


                override fun areContentsTheSame(oldItem: ChannelPositions, newItem: ChannelPositions) =
                    oldItem == newItem
            }
        }

        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            holder.unbind()
        }

    fun formatUnixTimestampToTime(unixTimestamp: Long): String {
        return try {
            val date = Date(unixTimestamp * 1000) // Timestamp in Sekunden → ms
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            timeFormat.format(date)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

        fun calculateTimeOffsetInSeconds(timeOffset: Int): Long {
            return (timeOffset * 3600).toLong()
        }

        fun stopRunnable() {
            progressUpdater?.let { handler.removeCallbacks(it) }
        }
    }