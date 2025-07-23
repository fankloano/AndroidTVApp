package com.example.mj_player_tv.ui.adapter



import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
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
                    val timeOffSet =
                        tvchannel.epgTimeOffSet ?: tvchannelPos.tvcategory.target?.epgTimeOffSet
                        ?: tvchannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0
                    val timeOffSetSeconds = calculateTimeOffsetInSeconds(timeOffSet)
                    val currentTimeMillis =
                        (System.currentTimeMillis() / 1000).plus(timeOffSetSeconds)
                    val currentTime = System.currentTimeMillis()
                    val epgChid = tvchannel.linkedEpgChannel?.target?.chEpgId
                    if (epgChid != null) {
                        val currentProgram =
                            withContext(Dispatchers.IO) {
                                epgDataBox.query(
                                    EpgDataOB_.epgChId.equal(epgChid)
                                        .and(EpgDataOB_.startTimestamp.less(currentTimeMillis))
                                        .and(EpgDataOB_.stopTimestamp.greater(currentTimeMillis))
                                )
                                    .build().findFirst()
                            }
                        val currentTimeString =
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime)
                        val halfHourLaterTime = currentTime + (30 * 60 * 1000)
                        val halfHourLaterTimeString =
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(halfHourLaterTime)
                        val startTime = if (currentProgram != null) {
                            formatUnixTimestampToTime(currentProgram.startTimestamp!!, timeOffSet)
                        } else {
                            currentTimeString
                        }
                        val endTime = if (currentProgram != null) {
                            formatUnixTimestampToTime(currentProgram.stopTimestamp!!, timeOffSet)
                        } else {
                            halfHourLaterTimeString
                        }
                        withContext(Dispatchers.Main) {
                            tvCurrentProgram.text = currentProgram?.name ?: "No Information"
                            tvCurrentStartTime.text = startTime
                            tvCurrentEndTime.text = " - ${endTime}"
                            if (currentProgram != null) {
                                hasCurrentEpg = true
                                val duration =
                                    ((currentProgram.stopTimestamp!! + timeOffSetSeconds).minus(
                                        currentProgram.startTimestamp!! + timeOffSetSeconds
                                    ))
                                progressBar.max = 100
                                val progress =
                                    (((System.currentTimeMillis() / 1000) - (currentProgram.startTimestamp!! + timeOffSetSeconds)) * 100 / duration).toInt()
                                progressBar.progress = progress
                                progressUpdater?.let { handler.removeCallbacks(it) }
                                progressUpdater = object : Runnable {
                                    override fun run() {
                                        val currentTimeRun = System.currentTimeMillis() / 1000
                                        if (currentTimeRun >= (currentProgram.stopTimestamp!! + timeOffSetSeconds)) {
                                            notifyItemChanged(bindingAdapterPosition)
                                        } else {
                                            val altProgress =
                                                ((currentTimeRun - (currentProgram.startTimestamp!! + timeOffSetSeconds)) * 100 / duration).toInt()
                                            progressBar.progress = altProgress
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
                        val currentTimeString =
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime)
                        val halfHourLaterTime = currentTime + (30 * 60 * 1000)
                        val halfHourLaterTimeString =
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(halfHourLaterTime)
                        withContext(Dispatchers.Main) {
                            tvCurrentProgram.text = "No Information"
                            tvCurrentStartTime.text = currentTimeString.toString()
                            tvCurrentEndTime.text = " - ${halfHourLaterTimeString}"
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
                    fragment.showChannelEpg(tvchannel)

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

        fun calculateTimeOffsetInSeconds(timeOffset: Int): Long {
            return (timeOffset * 3600).toLong()
        }

        fun stopRunnable() {
            progressUpdater?.let { handler.removeCallbacks(it) }
        }
    }