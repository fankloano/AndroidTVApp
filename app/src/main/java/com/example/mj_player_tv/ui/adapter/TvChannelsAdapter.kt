package com.example.mj_player_tv.ui.adapter


import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
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
import com.example.mj_player_tv.database.help.TvChannelWithEpg
import com.example.mj_player_tv.databinding.RvItemTvchannelsBinding
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.viewmodel.HelpViewModel
import io.objectbox.Box
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
class TvChannelsAdapter(
    private val onClickListener: TvChannelsAdapter.OnClickListener,
    private val onLongClickListener: TvChannelsAdapter.OnLongClickListener,
    private val fragment: TvChannelsFragment,
    private val helpViewModel: HelpViewModel,
    private val epgDataBox: Box<EpgDataOB>
) : ListAdapter<TvChannelWithEpg, TvChannelsAdapter.ViewHolder>(
    MANAGE_TVCHANNELS_COMPERATOR) {

    private val fragmentRef = WeakReference(fragment)

    private var longPressRunnable: Runnable? = null

    private val longPressHandler = Handler()
    private var isLongPress = false

    var passfocusedAssignChannel: ChannelPositions? = null
    var isLongPressBackOnce = true

    private var keyDownStartTime: Long = 0
    private val longPressDuration: Long = 500

    var isHandled = false // Flag zur Vermeidung doppelter Aktionen

    private var currentFocusedChannel: TvChannelWithEpg? = null
    private var currentSelectedChannelId: Long = 0L

    private var startedPosition = -1

    var thisList: MutableList<TvChannelWithEpg> = mutableListOf()

    inner class ViewHolder(val binding: RvItemTvchannelsBinding) : RecyclerView.ViewHolder(binding.root) {
        private val handler = Handler(Looper.getMainLooper())
        private var progressUpdater: Runnable? = null
        fun bind(tvChannelWithEpg: TvChannelWithEpg) {
            binding.apply {
                val tvchannelPos = tvChannelWithEpg.tvChannelPosition
                val tvchannel = tvchannelPos.tvchannel.target
                val account = tvchannel.account.target
                val playlistEpgActive = account.usePlaylistEpg
                val linkedEpgChannel = tvchannel.linkedEpgChannel?.target
                tvTvchannelname.text = tvchannel.showingName
                val image = tvchannel.logo
                val epgLogo = linkedEpgChannel?.icon?.firstOrNull()

                ivNew.visibility = if (tvchannel.newChannel) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                if (tvchannel.account.target!!.useEpgLogos) {
                        if (!epgLogo.isNullOrEmpty() && (linkedEpgChannel.isExternalEpg || tvchannel.alwaysUsesExternalEpg)) {
                            ivChannellogoImage.visibility = View.VISIBLE
                            ivChannellogoImage.load(epgLogo)
                        } else {
                            if (image.isNotEmpty()) {
                                ivChannellogoImage.visibility = View.VISIBLE
                                ivChannellogoImage.load(image)
                            } else {
                                ivChannellogoImage.visibility = View.INVISIBLE
                            }
                        }
                } else {
                    if (image.isNotEmpty()) {
                        ivChannellogoImage.visibility = View.VISIBLE
                        ivChannellogoImage.load(image)
                    } else {
                        ivChannellogoImage.visibility = View.INVISIBLE
                    }
                }

                binding.ivFavorite.visibility =
                    if (tvchannel.isFavorite) View.VISIBLE else View.INVISIBLE

                binding.ivCatchup.visibility =
                    if (tvchannel.enable_tv_archive == 1) View.VISIBLE else View.INVISIBLE

                    val currentTimeSec = System.currentTimeMillis() / 1000

                    val timeOffSetSec = calculateTimeOffsetInSeconds(
                        tvchannel.epgTimeOffSet
                            ?: tvchannelPos.tvcategory.target.epgTimeOffSet
                            ?: tvchannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                            ?: 0
                    )
                    // Hilfsfunktion für verschobene Zeiten
                    fun EpgDataOB.shiftedStart() = (this.startTimestamp) + timeOffSetSec
                    fun EpgDataOB.shiftedStop()  = (this.stopTimestamp) + timeOffSetSec
                    val epg = tvChannelWithEpg.epgList
                    val currentProgram = epg.firstOrNull { it.shiftedStart() <= currentTimeSec && it.shiftedStop() > currentTimeSec }
                    val nextProgram = epg.firstOrNull { it.shiftedStart() > currentTimeSec }

                // EPG-ID bestimmen

                    // Verarbeite die gefilterte EpgData-Liste nach Bedarf und binde sie an die UI
                    // Hier ist ein Beispiel, wie du die Informationen in die UI einbinden könntest:
                        if (currentProgram != null) {
                            // --- Aktuelles Programm ---
                            val startTime = formatUnixTimestampToTime(currentProgram.shiftedStart())
                            val endTime   = formatUnixTimestampToTime(currentProgram.shiftedStop())

                            tvCurrentStartTime.text = startTime
                            tvCurrentEndTime.text = " - $endTime"
                            tvCurrentProgram.text = currentProgram.name
                            ivReminder.visibility = if (currentProgram.isRemembered) View.VISIBLE else View.GONE

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
                                        if (tvchannelPos.catAndChannelAccount == helpViewModel.currentFocusedChannPosition?.catAndChannelAccount) {
                                            fragmentRef.get()?.takeIf { it.isAdded }
                                                ?.showEpgPreview(tvchannelPos)
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
                            // Nächstes Programm anzeigen (falls vorhanden)
                            tvNextProgram.text = nextProgram?.name ?: itemView.context.getString(R.string.no_information)

                        } else if (nextProgram != null) {
                            // --- Kein aktuelles, aber ein nächstes Programm ---
                            // --- Kein aktuelles, aber ein nächstes Programm ---
                            val nextStartTime = formatUnixTimestampToTime(nextProgram.shiftedStart())
                            val nextEndTime   = formatUnixTimestampToTime(nextProgram.shiftedStop())

                            tvCurrentProgram.text = itemView.context.getString(R.string.no_information)
                            tvCurrentStartTime.text = formatUnixTimestampToTime(currentTimeSec)
                            tvCurrentEndTime.text = " - $nextStartTime"
                            tvNextProgram.text = nextProgram.name

// Countdown bis nächstes Programm startet
                            val untilNext = nextProgram.shiftedStart() - currentTimeSec
                            val duration = nextProgram.shiftedStop() - nextProgram.shiftedStart()
                            progressBar.max = 100
                            progressBar.progress = (((currentTimeSec - nextProgram.shiftedStart()) * 100) / duration).toInt().coerceIn(0, 100)

// Progress-Updater für den Countdown
                            progressUpdater?.let { handler.removeCallbacks(it) }
                            progressUpdater = object : Runnable {
                                override fun run() {
                                    val now = System.currentTimeMillis() / 1000
                                    if (now >= nextProgram.shiftedStart()) {
                                        notifyItemChanged(bindingAdapterPosition)
                                        fragmentRef.get()?.takeIf { it.isAdded }?.showEpgPreview(tvchannelPos)
                                    }
                                    handler.postDelayed(this, 10000)
                                }
                            }
                            handler.post(progressUpdater!!)
                        } else {
                            // --- Weder aktuelles noch nächstes Programm ---
                            tvCurrentProgram.text = itemView.context.getString(R.string.no_information)
                            tvNextProgram.text = itemView.context.getString(R.string.no_information)
                            tvCurrentStartTime.text = formatUnixTimestampToTime(currentTimeSec)
                            tvCurrentEndTime.text = " - " + formatUnixTimestampToTime(currentTimeSec + 1800)
                            progressBar.progress = 0
                }

                binding.cardViewTvchannel.setOnClickListener {
                    binding.cardViewTvchannel.requestFocus()
                    handleCenterShortPress(tvChannelWithEpg)
                }

                binding.cardViewTvchannel.setOnKeyListener { view, keyCode, event ->
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> {
                            // Prüfe, ob assignChannelToEpg aktiviert ist
                            if (helpViewModel.assignChannelToEpgActive) {
                                // Nur erlaubte Tasten behandeln
                                when (keyCode) {
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (bindingAdapterPosition < itemCount - 1) {
                                            itemView.focusSearch(View.FOCUS_DOWN)?.requestFocus()
                                            return@setOnKeyListener true
                                        }
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (bindingAdapterPosition > 0) {
                                            itemView.focusSearch(View.FOCUS_UP)?.requestFocus()
                                            return@setOnKeyListener true
                                        }
                                    }

                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        handleRightShortPressAssignEpg()
                                        return@setOnKeyListener true
                                    }

                                    KeyEvent.KEYCODE_BACK -> {
                                        handleBackShortPressAssignEpg()
                                        return@setOnKeyListener true
                                    }
                                    else -> return@setOnKeyListener true // Ignoriere alle anderen Tasten
                                }
                            } else {

                                // Standardverarbeitung, wenn assignChannelToEpg nicht aktiv ist
                                if (!isHandled) {
                                    isLongPress = false
                                    keyDownStartTime = SystemClock.elapsedRealtime()

                                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                        when (keyCode) {
                                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                if (helpViewModel.changeChannelOrder) {
                                                    if (helpViewModel.isNowChangingChannelOrder) {
                                                        handleSortDownShortPress()
                                                    } else {
                                                        handleDownShortPress(tvChannelWithEpg)
                                                    }
                                                    return@setOnKeyListener true
                                                } else {
                                                    return@setOnKeyListener false
                                                }
                                            }

                                            KeyEvent.KEYCODE_DPAD_UP -> {
                                                if (helpViewModel.changeChannelOrder) {
                                                    if (helpViewModel.isNowChangingChannelOrder) {
                                                        handleSortUpShortPress()
                                                    } else {
                                                        handleUpShortPress()
                                                    }
                                                    return@setOnKeyListener true
                                                } else {
                                                    return@setOnKeyListener false
                                                }
                                            }
                                        }
                                    } else {
                                        // Long press handling
                                        longPressRunnable = Runnable {
                                            if (!isLongPress) {
                                                isLongPress = true
                                                isHandled = true // Markiere als verarbeitet

                                                when (keyCode) {

                                                    KeyEvent.KEYCODE_DPAD_CENTER -> {
                                                        handleCenterLongPress(tvchannelPos)
                                                        if (longPressRunnable != null) {
                                                            longPressHandler.removeCallbacks(longPressRunnable!!)
                                                        }
                                                    }

                                                    KeyEvent.KEYCODE_BACK -> {
                                                        handleBackLongPress()
                                                        if (longPressRunnable != null) {
                                                            longPressHandler.removeCallbacks(longPressRunnable!!)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        // Schedule the long press action after 500ms
                                        if (longPressRunnable != null) {
                                            longPressHandler.postDelayed(
                                                longPressRunnable!!,
                                                longPressDuration
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        KeyEvent.ACTION_UP -> {
                            if (helpViewModel.assignChannelToEpgActive) {
                                isHandled = false
                                return@setOnKeyListener true
                            } else {
                                val pressDuration = SystemClock.elapsedRealtime() - keyDownStartTime

                                if (longPressRunnable != null) {
                                    longPressHandler.removeCallbacks(longPressRunnable!!)
                                }

                                if (helpViewModel.assignChannelToEpgActive) {
                                    // Verarbeite keine weiteren Tasten
                                    return@setOnKeyListener true
                                }

                                if (pressDuration < longPressDuration && !isLongPress) {
                                    when (keyCode) {
                                        KeyEvent.KEYCODE_DPAD_LEFT -> handleLeftShortPress()
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> handleRightShortPress(tvchannel, playlistEpgActive)

                                        KeyEvent.KEYCODE_BACK -> {
                                            if (!helpViewModel.fullScreenFromAbside) {
                                                handleBackShortPress(tvChannelWithEpg)
                                            }
                                        }

                                        KeyEvent.KEYCODE_DPAD_CENTER -> handleCenterShortPress(
                                            tvChannelWithEpg
                                        )
                                    }
                                }

                                if (helpViewModel.fullScreenFromAbside && keyCode == KeyEvent.KEYCODE_BACK && isLongPressBackOnce) {
                                    helpViewModel.fullScreenFromAbside = false
                                    fragment.setFocusToVideoView()
                                    isLongPressBackOnce = false
                                }

                                isHandled = false
                                isLongPress = false
                            }
                        }
                    }
                    true
                }
            }
        }


        private fun handleCenterShortPress(tvChannelWithEpg: TvChannelWithEpg) {
            val tvChannelPos = tvChannelWithEpg.tvChannelPosition
            if (!helpViewModel.changeChannelOrder) {
                onClickListener.onClick(tvChannelPos, bindingAdapterPosition)
            } else {
                startedPosition = bindingAdapterPosition
                if (helpViewModel.isNowChangingChannelOrder) {
                    fragment.changeChOrderInformation()
                    helpViewModel.isNowChangingChannelOrder = false
                    refreshItem(tvChannelWithEpg)
                    fragment.saveCurrentList(tvChannelPos)
                } else {
                    startedPosition = bindingAdapterPosition
                    helpViewModel.isNowChangingChannelOrder = true
                    currentSelectedChannelId = tvChannelWithEpg.id
                    fragment.changeChOrderInfoMoving()
                    refreshItem(tvChannelWithEpg)
                }
            }
        }

        private fun handleBackShortPressAssignEpg() {
            fragment.closeAssignEpgFull()
        }

        private fun handleBackShortPress(tvChannelWithEpg: TvChannelWithEpg) {
            if (!helpViewModel.changeChannelOrder) {
                fragmentRef.get()?.takeIf { it.isAdded }
                    ?.setTvAccountsVisibilityAnimated(true)
                fragmentRef.get()?.takeIf { it.isAdded }?.removeFocusFromShortEpg()
                fragmentRef.get()?.takeIf { it.isAdded }
                    ?.focusToTvAccountFromChannel()
            } else {
                if (helpViewModel.isNowChangingChannelOrder) {
                    fragment.changeChOrderInformation()
                    helpViewModel.isNowChangingChannelOrder = false
                    refreshItem(tvChannelWithEpg)
                } else {
                    helpViewModel.changeChannelOrder = false
                    currentSelectedChannelId = 0L
                    refreshItem(tvChannelWithEpg)
                    fragment.closeChOrder()
                }
            }
        }

        private fun handleLeftShortPress() {
            if (!helpViewModel.changeChannelOrder) {
                fragmentRef.get()?.takeIf { it.isAdded }
                    ?.setTvAccountsVisibilityAnimated(true)
                fragmentRef.get()?.takeIf { it.isAdded }?.removeFocusFromShortEpg()
                fragmentRef.get()?.takeIf { it.isAdded }
                    ?.focusToTvAccountFromChannel()
            } else {
                if (helpViewModel.isNowChangingChannelOrder) {
                    fragment.changeChOrderInformation()
                    helpViewModel.isNowChangingChannelOrder = false
                } else {
                    helpViewModel.changeChannelOrder = false
                    fragment.closeChOrder()
                }
            }
        }

        private fun handleRightShortPress(tvchannel: TvChannelOB, isplaylistEpgActive: Boolean) {
            if (!helpViewModel.changeChannelOrder) {
                if (tvchannel.linkedEpgChannel?.target != null || (isplaylistEpgActive && tvchannel.epgChannel?.target != null)) {
                    helpViewModel.focusShowEpgOrDescription = true
                    notifyItemChanged(bindingAdapterPosition)
                    fragmentRef.get()?.takeIf { it.isAdded }?.setFocusToShortEpg()
                }
            }
        }

        private fun handleRightShortPressAssignEpg() {
            fragmentRef.get()?.takeIf { it.isAdded }?.setFocusToAssignEpg()
        }

        private fun handleSortUpShortPress() {
            if (helpViewModel.isNowChangingChannelOrder) {
                moveItemUp(bindingAdapterPosition)
            }
        }

        private fun handleDownShortPress(tvChannelWithEpg: TvChannelWithEpg) {
            // Prüfen, ob es ein nächstes Element gibt
            if (bindingAdapterPosition < itemCount - 1) {
                // Fokussiere das nächste Element automatisch
                itemView.focusSearch(View.FOCUS_DOWN)?.requestFocus()
                if (helpViewModel.changeChannelOrder) {
                    currentSelectedChannelId = currentFocusedChannel?.id ?: 0L
                    notifyItemChanged(bindingAdapterPosition)
                    val newPosition = currentList.indexOf(currentFocusedChannel)
                    notifyItemChanged(newPosition)
                }
                    notifyItemChanged(bindingAdapterPosition)
                    val newPosition = currentList.indexOf(currentFocusedChannel)
                    notifyItemChanged(newPosition)
            }
        }

        private fun handleUpShortPress() {
            // Prüfen, ob es ein nächstes Element gibt
            if (bindingAdapterPosition <= itemCount - 1) {
                itemView.focusSearch(View.FOCUS_UP)?.requestFocus()
                if (helpViewModel.changeChannelOrder) {
                    currentSelectedChannelId = currentFocusedChannel?.id ?: 0L
                    notifyItemChanged(bindingAdapterPosition)
                    val newPosition = currentList.indexOf(currentFocusedChannel)
                    notifyItemChanged(newPosition)
                }
                    notifyItemChanged(bindingAdapterPosition)
                    val newPosition = currentList.indexOf(currentFocusedChannel)
                    notifyItemChanged(newPosition)
            }
        }

        private fun handleSortDownShortPress() {
            if (helpViewModel.isNowChangingChannelOrder) {
                moveItemDown(bindingAdapterPosition)
            }
        }


        private fun handleCenterLongPress(tvchannelPos: ChannelPositions) {
            if (!helpViewModel.changeChannelOrder) {
                // Remove the long press callback if the key was released before 500ms
                if (longPressRunnable != null) {
                    longPressHandler.removeCallbacks(longPressRunnable!!)
                }
                helpViewModel.isChannelOptionsContainerOpened = true
                notifyItemChanged(bindingAdapterPosition)
                onLongClickListener.onLongClick(tvchannelPos, bindingAdapterPosition)
            }
        }

        private fun handleBackLongPress() {
            if (helpViewModel.isCurrentlyPlayingTv && isLongPressBackOnce) {
                // Remove the long press callback if the key was released before 500ms
                if (longPressRunnable != null) {
                    longPressHandler.removeCallbacks(longPressRunnable!!)
                }
                fragment.setVideoViewFullScreenWithoutFocus()
                helpViewModel.fullScreenFromAbside = true
            }
        }

        fun cleanup() {
            progressUpdater?.let { handler.removeCallbacks(it) }
            progressUpdater = null
            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
            longPressRunnable = null
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.cleanup()
        super.onViewRecycled(holder)
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        holder.cleanup()
        super.onViewDetachedFromWindow(holder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvItemTvchannelsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val viewHolder = ViewHolder(binding)
        return viewHolder
    }

    @UnstableApi
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tvchannelWithEpg = getItem(position)!!

        holder.bind(tvchannelWithEpg)
        val tvchannelPos = tvchannelWithEpg.tvChannelPosition
        val tvchannel = tvchannelPos.tvchannel.target

        if (currentSelectedChannelId == tvchannelWithEpg.id) {
            if (helpViewModel.changeChannelOrder) {
                if (helpViewModel.isNowChangingChannelOrder) {
                    holder.binding.borderOrderselectchannel.visibility = View.INVISIBLE
                    holder.binding.borderAnimatedOrderchannel.visibility = View.VISIBLE
                    val animation = AnimationUtils.loadAnimation(fragment.requireActivity(), R.anim.blinked_border)
                    holder.binding.borderAnimatedOrderchannel.startAnimation(animation)
                } else {
                    holder.binding.borderAnimatedOrderchannel.visibility = View.INVISIBLE
                    holder.binding.borderAnimatedOrderchannel.clearAnimation()
                    holder.binding.borderOrderselectchannel.visibility = View.VISIBLE
                }
            } else {
                holder.binding.borderAnimatedOrderchannel.visibility = View.INVISIBLE
                holder.binding.borderAnimatedOrderchannel.clearAnimation()
                holder.binding.borderOrderselectchannel.visibility = View.INVISIBLE
            }
        } else {
                holder.binding.borderAnimatedOrderchannel.visibility = View.INVISIBLE
                holder.binding.borderAnimatedOrderchannel.clearAnimation()
                holder.binding.borderOrderselectchannel.visibility = View.INVISIBLE
        }
        if (helpViewModel.assignChannelToEpgActive) {
            val isTrue = passfocusedAssignChannel?.id == tvchannelPos.id
            holder.binding.cardViewTvchannel.isActivated = isTrue
            holder.binding.cardViewTvchannel.isSelected = isTrue
        } else {
            holder.binding.cardViewTvchannel.isSelected = false
            if (helpViewModel.isChannelOptionsContainerOpened || helpViewModel.focusShowEpgOrDescription) {
                if (helpViewModel.currentFocusedChannel?.id == tvchannel.id) {
                    holder.binding.cardViewTvchannel.isActivated = true
                } else {
                    holder.binding.cardViewTvchannel.isActivated = false
                }
            } else {
                holder.binding.cardViewTvchannel.isActivated = false
            }
        }

        holder.binding.tvTvchannelname.isActivated = helpViewModel.currentPlayingChannelPosition?.id == tvchannelPos.id

        holder.binding.cardViewTvchannel.setOnFocusChangeListener { _, hasFocus ->
            holder.binding.tvTvchannelname.isSelected = hasFocus
            if (hasFocus) {
                currentFocusedChannel = tvchannelWithEpg
                if (helpViewModel.assignChannelToEpgActive) {
                    helpViewModel.currentAssignChannelPosition = tvchannelPos
                    updateFocusedAssignChannel(tvchannelPos)
                    fragment.refreshEpgChannelListWithChannel(tvchannelPos)
                    fragment.showEpgPreview(tvchannelPos)
                    holder.binding.borderAssignepgtoChannel.visibility = View.VISIBLE
                } else {
                    fragment.setCurrentFocusedChannel(tvchannelPos)
                    fragment.showEpgPreview(tvchannelPos)
                }
            } else {
                holder.binding.borderAssignepgtoChannel.visibility = View.INVISIBLE
            }
        }
    }

    companion object {
        private val MANAGE_TVCHANNELS_COMPERATOR = object : DiffUtil.ItemCallback<TvChannelWithEpg>() {
            override fun areItemsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg) =
                oldItem.tvChannelPosition.catAndChannelAccount == newItem.tvChannelPosition.catAndChannelAccount

            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: TvChannelWithEpg, newItem: TvChannelWithEpg) =
                        oldItem.epgList.firstOrNull()?.id == newItem.epgList.firstOrNull()?.id &&
                        oldItem.tvChannelPosition.tvchannel.target.linkedEpgChannel?.target?.id == newItem.tvChannelPosition.tvchannel.target?.linkedEpgChannel?.target?.id &&
                        oldItem.tvChannelPosition.tvchannel.target.showingName == newItem.tvChannelPosition.tvchannel.target.showingName &&
                        oldItem.tvChannelPosition.tvchannel.target.usesPlaylistEpg == newItem.tvChannelPosition.tvchannel.target.usesPlaylistEpg &&
                        oldItem.tvChannelPosition.isSelected == newItem.tvChannelPosition.isSelected &&
                        oldItem.tvChannelPosition.position == newItem.tvChannelPosition.position &&
                        oldItem.tvChannelPosition.tvchannel.target.epgSourceId == newItem.tvChannelPosition.tvchannel.target.epgSourceId &&
                        oldItem.tvChannelPosition.tvchannel.target.usesExternalEpg == newItem.tvChannelPosition.tvchannel.target.usesExternalEpg &&
                        oldItem.tvChannelPosition.tvchannel.target.isFavorite == newItem.tvChannelPosition.tvchannel.target.isFavorite &&
                        oldItem.tvChannelPosition.tvchannel.target.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet == newItem.tvChannelPosition.tvchannel.target.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet &&
                        oldItem.tvChannelPosition.tvchannel.target.account.target.useEpgLogos == newItem.tvChannelPosition.tvchannel.target.account.target.useEpgLogos &&
                        oldItem.tvChannelPosition.tvchannel.target.account.target.usePlaylistEpg == newItem.tvChannelPosition.tvchannel.target.account.target.usePlaylistEpg
        }
    }

    class OnClickListener(val clickListener: (tvchannel: ChannelPositions, position: Int) -> Unit) {
        fun onClick(tvchannel: ChannelPositions, position: Int) = clickListener(tvchannel, position)
    }

    class OnLongClickListener(val longClickListener: (tvchannel: ChannelPositions, position: Int) -> Unit) {
        fun onLongClick(tvchannel: ChannelPositions, position: Int) = longClickListener(tvchannel, position)
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

    fun calculateTimeOffsetInSeconds(offset: Int): Int {
        return offset * 3600
    }

    fun moveItemUp(position: Int) {
        if (position <= 0) return

        val mutableList = currentList.toMutableList()
        val temp = mutableList[position]
        mutableList[position] = mutableList[position - 1]
        mutableList[position - 1] = temp

        submitList(mutableList)
    }

    fun moveItemDown(position: Int) {
        if (position >= currentList.size - 1) return

        val mutableList = currentList.toMutableList()
        val temp = mutableList[position]
        mutableList[position] = mutableList[position + 1]
        mutableList[position + 1] = temp

        submitList(mutableList)
    }


    fun setCurrentChanneldId(tvchannelPos: ChannelPositions) {
        val tvChannelWithEpg = currentList.firstOrNull { it.tvChannelPosition.catAndChannelAccount == tvchannelPos.catAndChannelAccount }
        currentSelectedChannelId = tvChannelWithEpg?.id ?: 0L
        startedPosition = currentList.indexOf(tvChannelWithEpg)
        helpViewModel.isNowChangingChannelOrder = true
        notifyItemChanged(currentList.indexOf(tvChannelWithEpg))
    }

    fun submitListToUse(channelList: List<TvChannelWithEpg>? ) {
        thisList.clear()
        thisList = channelList?.toMutableList() ?: mutableListOf()
    }

    fun refreshItem(tvChannelWithEpg: TvChannelWithEpg) {
        val itemPosition = currentList.indexOf(tvChannelWithEpg)
        notifyItemChanged(itemPosition)
    }

    fun updateFocusedAssignChannel(tvchannelPos: ChannelPositions) {
        val tvChannelWithEpg = currentList.firstOrNull { it.tvChannelPosition.id == tvchannelPos.id }
        val oldPosition = currentList.indexOfFirst { it.tvChannelPosition.id == passfocusedAssignChannel?.id }
        val newPosition = currentList.indexOf(tvChannelWithEpg)
        passfocusedAssignChannel = tvchannelPos
        notifyItemChanged(oldPosition)
        notifyItemChanged(newPosition)
    }
}