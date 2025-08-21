package com.example.mj_player_tv.ui.adapter

import android.content.Intent
import android.net.Uri
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.ChannelPositions
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.MovieOB
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.database.entity.Programme_
import com.example.mj_player_tv.database.help.GlobalSearchDisplayItem
import com.example.mj_player_tv.databinding.RvItemGlobalsearchProgramsBinding
import com.example.mj_player_tv.databinding.RvItemGlobalsearchTvchannelBinding
import com.example.mj_player_tv.databinding.RvItemGlobalsearchmoviesBinding
import com.example.mj_player_tv.databinding.RvItemGlobalsearchseriesBinding
import com.example.mj_player_tv.ui.FullEpgFragment
import com.example.mj_player_tv.ui.GlobalSearchFragment
import com.example.mj_player_tv.ui.TvChannelsFragment
import com.example.mj_player_tv.utils.LoadingDialogHelper
import com.example.mj_player_tv.viewmodel.HelpViewModel
import io.objectbox.Box

@UnstableApi
class GlobalSearchItemsAdapter(
    private val helpViewModel: HelpViewModel,
    private val fragment: GlobalSearchFragment,
    private val programmeBox: Box<Programme>,
    private val epgDataBox: Box<EpgDataOB>,
    private val onItemClick: (GlobalSearchDisplayItem) -> Unit,
    private val onItemLongClick: (GlobalSearchDisplayItem, View) -> Unit
) : ListAdapter<GlobalSearchDisplayItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_TVCHANNELS = 1
        private const val TYPE_MOVIES = 2
        private const val TYPE_SERIES = 3
        private const val TYPE_PROGRAMS = 4
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is GlobalSearchDisplayItem.ChannelItem -> TYPE_TVCHANNELS
            is GlobalSearchDisplayItem.MovieItem -> TYPE_MOVIES
            is GlobalSearchDisplayItem.SeriesItem -> TYPE_SERIES
            is GlobalSearchDisplayItem.ProgramItem -> TYPE_PROGRAMS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TVCHANNELS -> {
                val binding = RvItemGlobalsearchTvchannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                TvChannelsViewHolder(binding, helpViewModel, fragment)
            }
            TYPE_MOVIES -> {
                val binding = RvItemGlobalsearchmoviesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                MoviesViewHolder(binding, helpViewModel, fragment)
            }
            TYPE_SERIES -> {
                val binding = RvItemGlobalsearchseriesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SeriesViewHolder(binding, helpViewModel, fragment)
            }
            TYPE_PROGRAMS -> {
                val binding = RvItemGlobalsearchProgramsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ProgramsViewHolder(binding, helpViewModel, fragment)
            }
            else -> throw IllegalArgumentException("Invalid viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GlobalSearchDisplayItem.ChannelItem -> (holder as TvChannelsViewHolder).bind(item)
            is GlobalSearchDisplayItem.MovieItem -> (holder as MoviesViewHolder).bind(item)
            is GlobalSearchDisplayItem.SeriesItem -> (holder as SeriesViewHolder).bind(item)
            is GlobalSearchDisplayItem.ProgramItem -> (holder as ProgramsViewHolder).bind(item)
        }
    }

    inner class TvChannelsViewHolder(
        private val binding: RvItemGlobalsearchTvchannelBinding,
        private val helpViewModel: HelpViewModel,
        private val fragment: GlobalSearchFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(channelItem: GlobalSearchDisplayItem.ChannelItem) {
            binding.apply {
                val tvchannel = channelItem.channel.tvchannel.target

                binding.tvChannel.text = tvchannel.showingName

                val linkedEpgChannel = tvchannel.linkedEpgChannel?.target

                val image = tvchannel.logo
                val epgLogo = linkedEpgChannel?.icon?.firstOrNull()

                if (tvchannel.account.target!!.useEpgLogos) {
                    if (!epgLogo.isNullOrEmpty() && (linkedEpgChannel.isExternalEpg || tvchannel.alwaysUsesExternalEpg)) {
                        ivtvChannel.visibility = View.VISIBLE
                        ivtvChannel.load(epgLogo)
                    } else {
                        if (image.isNotEmpty()) {
                            ivtvChannel.visibility = View.VISIBLE
                            ivtvChannel.load(image)
                        } else {
                            ivtvChannel.visibility = View.INVISIBLE
                        }
                    }
                } else {
                    if (image.isNotEmpty()) {
                        ivtvChannel.visibility = View.VISIBLE
                        ivtvChannel.load(image)
                    } else {
                        ivtvChannel.visibility = View.INVISIBLE
                    }
                }

                if (tvchannel.isFavorite) {
                    tvIsFavorite.visibility = View.VISIBLE
                } else {
                    tvIsFavorite.visibility = View.INVISIBLE
                }
                binding.cardviewTvchannel.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition in 0..6) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }

                binding.cardviewTvchannel.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        binding.overlayFull.visibility = View.GONE
                    } else {
                        binding.overlayFull.visibility = View.VISIBLE
                    }
                }

                cardviewTvchannel.setOnClickListener {
                    onItemClick(channelItem)
                }
            }
        }
    }

    inner class MoviesViewHolder(
        private val binding: RvItemGlobalsearchmoviesBinding,
        private val helpViewModel: HelpViewModel,
        private val fragment: GlobalSearchFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(movieItem: GlobalSearchDisplayItem.MovieItem) {
            val movie = movieItem.movie
            binding.apply {
                tvMovies.text = movie.movieName

                val image = movie.screenshot_uri
                if (!image.isNullOrEmpty()) {
                    ivMovies.load(image)
                } else {
                    Log.d("GLOBIMOVIEBILDI", "${movie.movieName} ||| NO IMAGE")
                }

                binding.tvIsFavorite.visibility = if (movie.isFavorite) View.VISIBLE else View.INVISIBLE

                binding.tvIsFullyWatched.visibility = if (movie.isCompletelyWatched) View.VISIBLE else View.INVISIBLE

                binding.tvIsPartlyWatched.visibility = if (movie.isPartlyWatched) View.VISIBLE else View.INVISIBLE

                binding.cardviewTvchannel.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition in 0..6) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }
                cardviewTvchannel.setOnClickListener {
                    onItemClick(movieItem)
                }

                cardviewTvchannel.setOnLongClickListener {
                    onItemLongClick(movieItem, cardviewTvchannel)
                    true
                }
            }
        }
    }

    inner class SeriesViewHolder(
        private val binding: RvItemGlobalsearchseriesBinding,
        private val helpViewModel: HelpViewModel,
        private val fragment: GlobalSearchFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(seriesItem: GlobalSearchDisplayItem.SeriesItem) {
            val serie = seriesItem.series
            binding.apply {
                tvSeries.text = serie.seriesName

                val image = serie.screenshot_uri
                if (!image.isNullOrEmpty()) {
                    ivSeries.load(image)
                }

                binding.tvIsFavorite.visibility = if (serie.isFavorite) View.VISIBLE else View.INVISIBLE

                binding.tvIsFullyWatched.visibility = if (serie.isCompletelyWatched) View.VISIBLE else View.INVISIBLE

                binding.tvIsPartlyWatched.visibility = if (serie.isPartlyWatched) View.VISIBLE else View.INVISIBLE

                binding.cardviewSerie.setOnKeyListener { _, keyCode, event ->
                    if ((keyCode == KeyEvent.KEYCODE_BACK) && event.action == KeyEvent.ACTION_DOWN) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    if (((keyCode == KeyEvent.KEYCODE_DPAD_UP) && event.action == KeyEvent.ACTION_DOWN) && bindingAdapterPosition in 0..6) {
                        fragment.focusToPlaylist()
                        return@setOnKeyListener true
                    }
                    return@setOnKeyListener false
                }
                cardviewSerie.setOnClickListener {
                    onItemClick(seriesItem)
                }
                cardviewSerie.setOnLongClickListener {
                    onItemLongClick(seriesItem, cardviewSerie)
                    true
                }
            }
        }
    }

    inner class ProgramsViewHolder(
        private val binding: RvItemGlobalsearchProgramsBinding,
        private val helpViewModel: HelpViewModel,
        private val fragment: GlobalSearchFragment
    ) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var epgAdapter: GlobalSearchEpgListAdapter
        private lateinit var channelAdapter: GlobalSearchProgramsChannelAdapter

        private var currentChannel: ChannelPositions? = null

        fun bind(item: GlobalSearchDisplayItem.ProgramItem) {
            val channelList = item.programs.map { it.first }
            val programMap = item.programs.toMap()

            // 1. Kanal-RecyclerView (links)
            channelAdapter = GlobalSearchProgramsChannelAdapter (
                onChannelFocused = { selectedChannel ->
                    if (currentChannel != selectedChannel) {
                        val epgs = programMap[selectedChannel] ?: emptyList()
                        epgAdapter.submitList(epgs.sortedBy { it.startTimestamp })
                        binding.tvSelectedChannel.text =
                            selectedChannel.tvchannel.target.showingName
                        binding.tvSelectedTvCategory.text =
                            "in ${selectedChannel.tvcategory.target.showingName}"
                        updateDetail(epgs.firstOrNull())
                        currentChannel = selectedChannel
                    }
                },
                onRightClicked = {
                    focusToEpglist()
                },
                fragment
            )


            binding.recyclerEpg.adapter = channelAdapter
            channelAdapter.submitList(channelList)

            // 2. EPG-Liste (mittig)
            epgAdapter = GlobalSearchEpgListAdapter(
                onEpgFocused = { selectedEpg ->
                    updateDetail(selectedEpg)
                },
                onEpgClicked = { clickedEpg, thisview ->
                    showProgramPopup(clickedEpg, currentChannel ?: item.programs.first().first, thisview)
                },
                onRighClicked = {
                    checkForDescrTextViewLenght()
                },
                helpViewModel = helpViewModel,
                fragment// ✅ Übergib hier das ViewModel
            )

            binding.recyclerEpglist.adapter = epgAdapter

            // Initialauswahl: Erster Kanal
            val firstChannel = channelList.firstOrNull()
            val initialEpgs = programMap[firstChannel] ?: emptyList()

            epgAdapter.submitList(initialEpgs)
            binding.tvSelectedChannel.text = firstChannel?.tvchannel?.target?.showingName.orEmpty()
            binding.tvSelectedTvCategory.text = "in ${firstChannel?.tvcategory?.target?.showingName.orEmpty()}"
            updateDetail(initialEpgs.firstOrNull())

            binding.tvDetailepgDescription.post {
                val layout = binding.tvDetailepgDescription.layout
                if (layout != null) {
                    val isTextTruncated = layout.height - binding.tvDetailepgDescription.height > 0
                    if (isTextTruncated) {
                        binding.tvDetailepgDescription.isVerticalScrollBarEnabled = true
                        binding.tvDetailepgDescription.movementMethod = ScrollingMovementMethod()
                        binding.gradientView.visibility = View.VISIBLE
                        binding.ivMoretext.visibility = View.VISIBLE
                    } else {
                        binding.tvDetailepgDescription.isVerticalScrollBarEnabled = false
                        binding.tvDetailepgDescription.movementMethod = null
                        binding.tvDetailepgDescription.scrollTo(0, 0) // Zurück zur Ausgangsposition
                        binding.gradientView.visibility = View.GONE
                        binding.ivMoretext.visibility = View.GONE
                    }
                }
            }
            binding.tvDetailepgDescription.setOnKeyListener { _, keyCode, event ->
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        val layout = binding.tvDetailepgDescription.layout

                        // Überprüfen, ob der Text abgeschnitten ist (abgeschnitten, wenn die letzte Zeile mehr als die Höhe des TextViews hinausgeht)
                        val isTextTruncated = layout.height - binding.tvDetailepgDescription.height > 0

                        // Wenn der Text abgeschnitten ist, Scrollen zulassen
                        if (isTextTruncated) {

                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    binding.tvDetailepgDescription.scrollTo(0, 0)
                                    binding.gradientView.visibility = View.VISIBLE
                                    binding.ivMoretext.visibility = View.VISIBLE
                                    binding.recyclerEpglist.requestFocus()
                                    return@setOnKeyListener true
                                }
                                KeyEvent.KEYCODE_BACK -> {
                                    binding.tvDetailepgDescription.scrollTo(0, 0)
                                    binding.gradientView.visibility = View.VISIBLE
                                    binding.ivMoretext.visibility = View.VISIBLE
                                    binding.recyclerEpglist.requestFocus()
                                    return@setOnKeyListener true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    // Scroll nach oben
                                    val newScrollY = (binding.tvDetailepgDescription.scrollY - 50).coerceAtLeast(0)
                                    binding.tvDetailepgDescription.scrollTo(0, newScrollY)
                                    return@setOnKeyListener true // Verhindern, dass der Rest ausgeführt wird
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    // Berechne den maximalen Scrollbereich
                                    val maxScrollY = binding.tvDetailepgDescription.layout.height - binding.tvDetailepgDescription.height
                                    val newScrollY = (binding.tvDetailepgDescription.scrollY + 50).coerceAtMost(maxScrollY + (2 * binding.tvDetailepgDescription.lineHeight))  // 2 Zeilen Puffer
                                    binding.tvDetailepgDescription.scrollTo(0, newScrollY)
                                    return@setOnKeyListener true
                                }
                                else -> return@setOnKeyListener false // Falls keine relevante Taste gedrückt wird
                            }
                        } else {
                            return@setOnKeyListener true
                        }
                    }

                    else -> {false}
                }
            }

            binding.tvDetailepgDescription.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    val animation = AnimationUtils.loadAnimation(binding.tvDetailepgDescription.context, R.anim.blinked_border)
                    binding.descriptionborder.visibility = View.VISIBLE
                    binding.descriptionborder.startAnimation(animation)
                } else {
                    binding.descriptionborder.visibility = View.GONE
                    binding.descriptionborder.clearAnimation()
                }
            }
        }

        private fun focusToEpglist() {
            if (!epgAdapter.currentList.isNullOrEmpty()) {
                binding.recyclerEpglist.requestFocus()
            } else {
                return
            }
        }

        private fun updateDetail(epg: EpgDataOB?) {
            if (epg == null) {
                binding.tvDetailepgName.text = ""
                binding.tvDetailepgSubtitle.text = ""
                binding.tvDetailepgDescription.text = ""
                return
            }
            binding.tvDetailepgName.text = epg.name
            binding.tvDetailepgSubtitle.text = epg.sub_title ?: ""
            binding.tvDetailepgSubtitle.visibility = if (epg.sub_title.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }
            binding.tvDetailepgDescription.text = if (epg.descr.isNotEmpty()) {
                epg.descr
            } else {
                "No description available"
            }
        }

        private fun checkForDescrTextViewLenght() {
            binding.tvDetailepgDescription.post {
                val canScroll = binding.tvDetailepgDescription.layout?.let { layout ->
                    val scrollRange = layout.height
                    val actualHeight = binding.tvDetailepgDescription.height
                    scrollRange > actualHeight
                } ?: false

                if (canScroll) {
                    binding.gradientView.visibility = View.GONE
                    binding.ivMoretext.visibility = View.GONE
                    binding.tvDetailepgDescription.requestFocus()
                } else {
                    binding.recyclerEpglist.requestFocus()
                }
            }
        }

        private fun showProgramPopup(program: EpgDataOB, tvchannelPos: ChannelPositions, anchor: View) {
            val context = anchor.context
            val popupView = LayoutInflater.from(context).inflate(R.layout.menu_popup_program, null)
            val widthInDp = 400
            val widthInPx = (widthInDp * popupView.context.resources.displayMetrics.density).toInt()
            val popupWindow = PopupWindow(
                popupView,
                widthInPx,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )
            popupWindow.elevation = 8f
            val itemName = popupView.findViewById<TextView>(R.id.itemName)
            itemName.text = tvchannelPos.tvchannel.target.showingName
            itemName.isSelected = true
            // Views aus dem Layout holen
            val playOption = popupView.findViewById<TextView>(R.id.optionRemove)
            val replayOption = popupView.findViewById<TextView>(R.id.optionReplay)
            val reminderOption = popupView.findViewById<TextView>(R.id.optionFullWatched)
            val epgName = popupView.findViewById<TextView>(R.id.programName)
            val channelLogo = popupView.findViewById<ImageView>(R.id.itemLogo)
            epgName.visibility = View.VISIBLE
            epgName.text = program.name
            epgName.isSelected = true

            val tvchannel = tvchannelPos.tvchannel.target
            val currentTime = System.currentTimeMillis() / 1000
            val isProgramFinished = (program.stopTimestamp ?: 0L) < currentTime
            val isProgramNotStarted = (program.startTimestamp ?: 0) > currentTime
            val isCatchupChannel = tvchannel.enable_tv_archive == 1
            val linkedEpgChannel = tvchannel.linkedEpgChannel?.target
            val image = tvchannel.logo
            val epgLogo = linkedEpgChannel?.icon?.firstOrNull()
            if (tvchannel.account.target!!.useEpgLogos) {
                if (!epgLogo.isNullOrEmpty() && (linkedEpgChannel.isExternalEpg || tvchannel.alwaysUsesExternalEpg)) {
                    channelLogo.visibility = View.VISIBLE
                    channelLogo.load(epgLogo)
                } else {
                    if (image.isNotEmpty()) {
                        channelLogo.visibility = View.VISIBLE
                        channelLogo.load(image)
                    } else {
                        channelLogo.visibility = View.INVISIBLE
                    }
                }
            } else {
                if (image.isNotEmpty()) {
                    channelLogo.visibility = View.VISIBLE
                    channelLogo.load(image)
                } else {
                    channelLogo.visibility = View.INVISIBLE
                }
            }
            if (isProgramFinished && !isCatchupChannel) {
                return
            }

            val isProgramCurrentlyPlaying = (((program.stopTimestamp ?: 0L) > currentTime &&
                    currentTime >= (program.startTimestamp ?: 0)))

            playOption.visibility = if (isProgramCurrentlyPlaying) View.VISIBLE else View.GONE
            replayOption.visibility = if (isCatchupChannel && (isProgramCurrentlyPlaying || isProgramFinished)) View.VISIBLE else View.GONE
            reminderOption.visibility = if (isProgramNotStarted) View.VISIBLE else View.GONE


            replayOption.text = when {
                isProgramFinished -> "Rewatch"
                isProgramCurrentlyPlaying && isCatchupChannel -> "Play from beginning"
                else -> ""
            }

            val isProgrammReminded = programmeBox.query(
                Programme_.epgForCh.equal("${program.idByAccountData}_${tvchannel.idByAccountData}")
            ).build().findFirst()

            reminderOption.text = if (isProgrammReminded != null) {
                "Remove reminder"
            } else {
                "Set reminder"
            }

            if (isProgramCurrentlyPlaying) {
                playOption.requestFocus()
            } else {
                if (isCatchupChannel && isProgramFinished) {
                    replayOption.requestFocus()
                } else {
                    if (isProgramNotStarted) {
                        reminderOption.requestFocus()
                    }
                }
            }

            // Click Listener
            playOption.setOnClickListener {
                playProgram(tvchannelPos)
                popupWindow.dismiss()
            }
            replayOption.setOnClickListener {
                replayProgram(tvchannelPos, program)
                popupWindow.dismiss()
            }
            reminderOption.setOnClickListener {
                checkReminder(program, tvchannelPos, anchor)
                popupWindow.dismiss()
            }

            // Position mittig über dem Item
            val location = IntArray(2)
            anchor.getLocationOnScreen(location)
            val anchorX = location[0]
            val anchorY = location[1]
            popupView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupWidth = popupView.measuredWidth
            val popupHeight = popupView.measuredHeight

            val xPos = anchorX + (anchor.width / 2) - (popupWidth / 2)
            val yPos = anchorY + (anchor.height / 2) - (popupHeight / 2)

            popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, xPos, yPos)

            // Hintergrund abdunkeln
            fragment.showDimOverlay()
            popupWindow.setOnDismissListener {
                fragment.removeDimOverlay()
                binding.recyclerEpglist.requestFocus()
            }
        }


        private fun playProgram(tvchannelPos: ChannelPositions) {
                fragment.playChannel(tvchannelPos)
        }

        private fun replayProgram(tvchannelPos: ChannelPositions, clickedEpg: EpgDataOB) {
                fragment.replayProgram(tvchannelPos, clickedEpg)
        }

        private fun checkReminder(epg: EpgDataOB, tvChannelPos: ChannelPositions, view: View) {
            val tvChannel = tvChannelPos.tvchannel.target
            val isProgramme = programmeBox.query(Programme_.epgForCh.equal("${epg.idByAccountData}_${tvChannel.idByAccountData}")).build().findFirst()
            if (isProgramme != null) {
                programmeBox.remove(isProgramme)
                epg.isRemembered = false
                epgDataBox.put(epg)
                val currentEpgPos = epgAdapter.currentList.indexOf(epg)
                epgAdapter.notifyItemChanged(currentEpgPos)
            } else {
                val timeOffSet =
                    tvChannel?.epgTimeOffSet ?: tvChannelPos.tvcategory.target?.epgTimeOffSet
                    ?: tvChannel?.linkedEpgChannel?.target?.epgsource?.target?.timeOffSet
                    ?: 0
                val thisProgramme = Programme(
                    0,
                    "${epg.idByAccountData}_${tvChannel.idByAccountData}",
                    epg.startTimestamp ?: 0L,
                    epg.stopTimestamp ?: 0L,
                    helpViewModel.settings?.tvReminderTime ?: 10L
                )
                programmeBox.put(thisProgramme)
                thisProgramme.apply {
                    epgData.target = epg
                    tvchannels.target = tvChannelPos
                }
                programmeBox.put(thisProgramme)
                epg.isRemembered = true
                epgDataBox.put(epg)
                val currentEpgPos = epgAdapter.currentList.indexOf(epg)
                epgAdapter.notifyItemChanged(currentEpgPos)
                if (!android.provider.Settings.canDrawOverlays(view.context)) {
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + fragment.requireContext().packageName)
                    )
                    fragment.requireActivity().startActivity(intent)
                }
                helpViewModel.setReminder(fragment.requireContext(), thisProgramme, timeOffSet)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<GlobalSearchDisplayItem>() {
        override fun areItemsTheSame(oldItem: GlobalSearchDisplayItem, newItem: GlobalSearchDisplayItem): Boolean {
            val result = when {
                oldItem is GlobalSearchDisplayItem.ChannelItem && newItem is GlobalSearchDisplayItem.ChannelItem ->
                    oldItem.channel.catAndChannelAccount == newItem.channel.catAndChannelAccount
                oldItem is GlobalSearchDisplayItem.MovieItem && newItem is GlobalSearchDisplayItem.MovieItem ->
                    oldItem.movie.idByAccountData == newItem.movie.idByAccountData
                oldItem is GlobalSearchDisplayItem.SeriesItem && newItem is GlobalSearchDisplayItem.SeriesItem ->
                    oldItem.series.idByAccountData == newItem.series.idByAccountData
                oldItem is GlobalSearchDisplayItem.ProgramItem && newItem is GlobalSearchDisplayItem.ProgramItem -> {
                    val oldIds = oldItem.programs.map { it.first.id }
                    val newIds = newItem.programs.map { it.first.id }
                    oldIds == newIds
                }
                else -> false
            }
            return result
        }

        override fun areContentsTheSame(oldItem: GlobalSearchDisplayItem, newItem: GlobalSearchDisplayItem): Boolean {
            val result = oldItem == newItem
            return result
        }
    }

}
