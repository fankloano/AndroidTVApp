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
) : ListAdapter<ChannelPositions, TvChannelsAdapter.ViewHolder>(
    MANAGE_TVCHANNELS_COMPERATOR) {

    private val fragmentRef = WeakReference(fragment)
    private val handler = Handler(Looper.getMainLooper())

    private var progressUpdater: Runnable? = null

    private var longPressRunnable: Runnable? = null

    private val longPressHandler = Handler()
    private var isLongPress = false

    var passfocusedAssignChannel: ChannelPositions? = null
    var isLongPressBackOnce = true

    private var keyDownStartTime: Long = 0
    private val longPressDuration: Long = 500

    var isHandled = false // Flag zur Vermeidung doppelter Aktionen

    private var currentFocusedChannel: ChannelPositions? = null
    private var currentSelectedChannelId: String = ""

    var epgForChannelCache: MutableMap<Long, MutableList<EpgDataOB>> = mutableMapOf()

    private var startedPosition = -1

    var thisList: MutableList<ChannelPositions> = mutableListOf()

    inner class ViewHolder(val binding: RvItemTvchannelsBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tvchannelPos: ChannelPositions) {
            binding.apply {
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

                CoroutineScope(Dispatchers.IO).launch {
                    val currentTimeMillis = System.currentTimeMillis() / 1000
                    val currentTime = System.currentTimeMillis()// Zeit in Sekunden seit 1970
                    val currentTimeString =
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime)
                    val halfHourLaterTime = currentTime + (30 * 60 * 1000)
                    val halfHourLaterTimeString =
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(halfHourLaterTime)
                    // Filtere die EpgData-Liste für die aktuelle und die nächsten Sendungen
                    tvchannel.account.target.epgsources.reset()
                    val timeOffSet = tvchannel.epgTimeOffSet ?: tvchannel.reltvcategory.target.epgTimeOffSet ?: tvchannel.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet ?: 0

                    val timeOffSetSeconds = calculateTimeOffsetInSeconds(timeOffSet)
                    val currenTimePlusTimeOffSet = currentTimeMillis.plus(timeOffSetSeconds)
                    val chEpgId = tvchannel.linkedEpgChannel?.target?.chEpgId ?: if (playlistEpgActive) {
                        tvchannel.epgChannel?.target?.chEpgId
                    } else {
                        null
                    }
                    val usedEpgData = if (
                        !epgForChannelCache.containsKey(tvchannel.id) ||
                        epgForChannelCache[tvchannel.id].isNullOrEmpty() ||
                        epgForChannelCache[tvchannel.id]?.all { it.stopTimestamp?.let { timestamp -> timestamp > currenTimePlusTimeOffSet } == true } != true
                    ) {
                        if (tvchannel.account.target.isXtream) {
                            val epg = chEpgId?.let {
                                epgDataBox.query(EpgDataOB_.epgChId.equal(it)).order(EpgDataOB_.startTimestamp).build()
                            }?.find()


                        }
                        // Datenbankabruf durchführen, wenn der Key nicht existiert oder die stopTimestamp-Bedingung nicht erfüllt ist
                        val epg = chEpgId?.let {
                            epgDataBox.query(
                                EpgDataOB_.epgChId.equal(it).and(
                                    EpgDataOB_.stopTimestamp.greater(currenTimePlusTimeOffSet)
                                )
                            )
                                .order(EpgDataOB_.startTimestamp)
                                .build()
                                .find(0, 3)
                        }
                        // Speichern der neuen EpgDaten im Cache
                        epgForChannelCache[tvchannel.id] = epg?.toMutableList() ?: mutableListOf()
                        epg
                    } else {

                        // Wenn der Cache gültige Daten hat, verwenden
                        epgForChannelCache[tvchannel.id]
                    }


                    val currentProgram = usedEpgData?.firstOrNull()

                    val nextEpgData = if (usedEpgData != null && usedEpgData.size > 1) {
                        usedEpgData[1]
                    } else {
                        null
                    }
                    // Verarbeite die gefilterte EpgData-Liste nach Bedarf und binde sie an die UI
                    // Hier ist ein Beispiel, wie du die Informationen in die UI einbinden könntest:
                    withContext(Dispatchers.Main) {
                        if (currentProgram != null) {

                            val currentStartTime = formatUnixTimestampToTime(currentProgram.startTimestamp!!, timeOffSet)

                            val currentEndTime = formatUnixTimestampToTime(currentProgram.stopTimestamp!!, timeOffSet)

                            tvCurrentStartTime.text = currentStartTime
                            tvCurrentEndTime.text = " - ${currentEndTime}"
                            tvCurrentProgram.text = currentProgram.name
                            if (currentProgram.isRemembered) {
                                ivReminder.visibility = View.VISIBLE
                            } else {
                                ivReminder.visibility = View.GONE
                            }
                            val duration =
                                ((currentProgram.stopTimestamp!! + timeOffSetSeconds).minus(
                                    currentProgram.startTimestamp!! + timeOffSetSeconds
                                ))
                            progressBar.max = 100
                            val progress =
                                ((currentTimeMillis - (currentProgram.startTimestamp!! + timeOffSetSeconds)) * 100 / duration).toInt()
                            progressBar.progress = progress
                            progressUpdater?.let { handler.removeCallbacks(it) }
                            progressUpdater = object : Runnable {
                                override fun run() {
                                    val currentTimeRun = System.currentTimeMillis() / 1000
                                    if (currentTimeRun >= (currentProgram.stopTimestamp!! + timeOffSetSeconds)) {
                                        notifyItemChanged(bindingAdapterPosition)
                                        fragment.checkSingleChannelEpg(tvchannelPos)
                                        if (helpViewModel.currentFocusedChannPosition?.catAndChannelAccount == tvchannelPos.catAndChannelAccount) {
                                            fragmentRef.get()?.takeIf { it.isAdded }
                                                ?.showEpgPreview(tvchannel)
                                        }
                                    } else {
                                        val altProgress =
                                            ((currentTimeRun - (currentProgram.startTimestamp!! + timeOffSetSeconds)) * 100 / duration).toInt()
                                        progressBar.progress = altProgress
                                    }
                                    handler.postDelayed(this, 10000)
                                }
                            }
                            handler.post(progressUpdater!!)


                            if (nextEpgData != null) {
                                tvNextProgram.text = nextEpgData.name
                            } else {
                                tvNextProgram.text =
                                    itemView.context.getString(R.string.no_information)
                            }
                        } else {
                            ivReminder.visibility = View.GONE
                            tvCurrentProgram.text =
                                itemView.context.getString(R.string.no_information)
                            tvCurrentStartTime.text = currentTimeString
                            if (nextEpgData != null) {
                                tvNextProgram.text = nextEpgData.name
                                val duration =
                                    (currentTimeMillis - (nextEpgData.startTimestamp!! + timeOffSetSeconds))
                                progressBar.max = 100
                                val progress =
                                    (((nextEpgData.startTimestamp!! + timeOffSetSeconds) - System.currentTimeMillis() / 1000) * 100 / duration).toInt()
                                progressBar.progress = progress

                                val nextStartTme = formatUnixTimestampToTime(nextEpgData.startTimestamp!!, timeOffSet)

                                tvCurrentEndTime.text = " - ${nextStartTme}"
                                progressUpdater?.let { handler.removeCallbacks(it) }
                                progressUpdater = object : Runnable {
                                    override fun run() {
                                        if (currentTimeMillis >= (nextEpgData.startTimestamp!! + timeOffSetSeconds)) {
                                            notifyItemChanged(bindingAdapterPosition)
                                            if (helpViewModel.currentFocusedChannPosition?.catAndChannelAccount == tvchannelPos.catAndChannelAccount) {
                                                fragmentRef.get()?.takeIf { it.isAdded }
                                                    ?.showEpgPreview(tvchannel)
                                            }
                                        }
                                        handler.postDelayed(this, 10000)
                                    }
                                }
                                handler.post(progressUpdater!!)
                            } else {
                                tvCurrentProgram.text =
                                    itemView.context.getString(R.string.no_information)
                                tvNextProgram.text =
                                    itemView.context.getString(R.string.no_information)
                                progressBar.progress = 0
                                tvCurrentEndTime.text = " - $halfHourLaterTimeString"
                            }
                        }
                    }
                }

                binding.cardViewTvchannel.setOnClickListener {
                    binding.cardViewTvchannel.requestFocus()
                    handleCenterShortPress(tvchannelPos)
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
                                                        handleDownShortPress(tvchannel)
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
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> handleRightShortPress(
                                            tvchannel,
                                            true
                                        )

                                        KeyEvent.KEYCODE_BACK -> {
                                            if (!helpViewModel.fullScreenFromAbside) {
                                                handleBackShortPress(tvchannelPos)
                                            }
                                        }

                                        KeyEvent.KEYCODE_DPAD_CENTER -> handleCenterShortPress(
                                            tvchannelPos
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

        private fun handleCenterShortPress(tvchannel: ChannelPositions) {
            if (!helpViewModel.changeChannelOrder) {
                onClickListener.onClick(tvchannel, bindingAdapterPosition)
            } else {
                startedPosition = bindingAdapterPosition
                if (helpViewModel.isNowChangingChannelOrder) {
                    fragment.changeChOrderInformation()
                    helpViewModel.isNowChangingChannelOrder = false
                    refreshItem(tvchannel)
                    fragment.saveCurrentList(tvchannel)
                } else {
                    startedPosition = bindingAdapterPosition
                    helpViewModel.isNowChangingChannelOrder = true
                    currentSelectedChannelId = tvchannel.catAndChannelAccount
                    fragment.changeChOrderInfoMoving()
                    refreshItem(tvchannel)
                }
            }
        }

        private fun handleBackShortPressAssignEpg() {
            fragment.closeAssignEpgFull()
        }

        private fun handleBackShortPress(tvchannelPos: ChannelPositions) {
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
                    refreshItem(tvchannelPos)
                } else {
                    helpViewModel.changeChannelOrder = false
                    currentSelectedChannelId = ""
                    refreshItem(tvchannelPos)
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

        private fun handleRightShortPress(tvchannel: TvChannelOB, isNotEmpty: Boolean) {
            if (!helpViewModel.changeChannelOrder) {
                if (tvchannel.linkedEpgChannel?.target != null && isNotEmpty) {
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

        private fun handleDownShortPress(tvchannel: TvChannelOB) {
            // Prüfen, ob es ein nächstes Element gibt
            if (bindingAdapterPosition < itemCount - 1) {
                // Fokussiere das nächste Element automatisch
                itemView.focusSearch(View.FOCUS_DOWN)?.requestFocus()
                if (helpViewModel.changeChannelOrder) {
                    currentSelectedChannelId = currentFocusedChannel?.catAndChannelAccount ?: ""
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
                    currentSelectedChannelId = currentFocusedChannel?.catAndChannelAccount ?: ""
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
        val tvchannelPos = getItem(position)!!
        holder.bind(tvchannelPos)
        val tvchannel = tvchannelPos.tvchannel.target

        if (currentSelectedChannelId == tvchannelPos.catAndChannelAccount) {
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
                currentFocusedChannel = tvchannelPos
                if (helpViewModel.assignChannelToEpgActive) {
                    helpViewModel.currentAssignChannelPosition = tvchannelPos
                    updateFocusedAssignChannel(tvchannelPos)
                    fragment.refreshEpgChannelListWithChannel(tvchannelPos)
                    fragment.showEpgPreview(tvchannel)
                    holder.binding.borderAssignepgtoChannel.visibility = View.VISIBLE
                } else {
                    fragment.setCurrentFocusedChannel(tvchannelPos)
                    fragment.showEpgPreview(tvchannel)
                }
            } else {
                holder.binding.borderAssignepgtoChannel.visibility = View.INVISIBLE
            }
        }
    }

    companion object {
        private val MANAGE_TVCHANNELS_COMPERATOR = object : DiffUtil.ItemCallback<ChannelPositions>() {
            override fun areItemsTheSame(oldItem: ChannelPositions, newItem: ChannelPositions) =
                oldItem.catAndChannelAccount == newItem.catAndChannelAccount


            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: ChannelPositions, newItem: ChannelPositions) =
                        oldItem.tvchannel.target.linkedEpgChannel?.target?.id == newItem.tvchannel.target?.linkedEpgChannel?.target?.id &&
                        oldItem.tvchannel.target.showingName == newItem.tvchannel.target.showingName &&
                        oldItem.tvchannel.target.usesPlaylistEpg == newItem.tvchannel.target.usesPlaylistEpg &&
                        oldItem.isSelected == newItem.isSelected &&
                        oldItem.position == newItem.position &&
                        oldItem.tvchannel.target.epgSourceId == newItem.tvchannel.target.epgSourceId &&
                        oldItem.tvchannel.target.usesExternalEpg == newItem.tvchannel.target.usesExternalEpg &&
                        oldItem.tvchannel.target.isFavorite == newItem.tvchannel.target.isFavorite &&
                        oldItem.tvchannel.target.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet == newItem.tvchannel.target.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet &&
                        oldItem.tvchannel.target.account.target.useEpgLogos == newItem.tvchannel.target.account.target.useEpgLogos &&
                        oldItem.tvchannel.target.account.target.usePlaylistEpg == newItem.tvchannel.target.account.target.usePlaylistEpg
        }
    }

    class OnClickListener(val clickListener: (tvchannel: ChannelPositions, position: Int) -> Unit) {
        fun onClick(tvchannel: ChannelPositions, position: Int) = clickListener(tvchannel, position)
    }

    class OnLongClickListener(val longClickListener: (tvchannel: ChannelPositions, position: Int) -> Unit) {
        fun onLongClick(tvchannel: ChannelPositions, position: Int) = longClickListener(tvchannel, position)
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
        currentSelectedChannelId = tvchannelPos.catAndChannelAccount
        startedPosition = currentList.indexOf(tvchannelPos)
        helpViewModel.isNowChangingChannelOrder = true
        notifyItemChanged(currentList.indexOf(tvchannelPos))
    }

    fun submitListToUse(channelList: List<ChannelPositions>? ) {
        thisList.clear()
        thisList = channelList?.toMutableList() ?: mutableListOf()
    }

    fun refreshItem(tvchannelPos: ChannelPositions) {
        val itemPosition = currentList.indexOf(tvchannelPos)
        notifyItemChanged(itemPosition)
    }

    fun updateFocusedAssignChannel(channelPositions: ChannelPositions) {
        val oldPosition = passfocusedAssignChannel.let { currentList.indexOf(it) }
        val newPosition = currentList.indexOf(channelPositions)
        passfocusedAssignChannel = channelPositions
        notifyItemChanged(oldPosition)
        notifyItemChanged(newPosition)
    }
}