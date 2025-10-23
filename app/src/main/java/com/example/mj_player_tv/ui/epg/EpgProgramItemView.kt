package com.example.mj_player_tv.ui.epg

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.ui.epg.util.EpgUtil

class EpgProgramItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RelativeLayout(context, attrs, defStyle) {

    private lateinit var titleView: TextView
    private lateinit var subTitleView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var reminderIcon: ImageView

    var programData: EpgDataOB? = null
        private set

    override fun onFinishInflate() {
        super.onFinishInflate()

        // 🔹 Hier ist der richtige Moment für findViewById
        titleView = findViewById(R.id.tv_epgProgram)
        subTitleView = findViewById(R.id.tv_epgSubTitleProgram)
        progressBar = findViewById(R.id.epgProgressBar)
        reminderIcon = findViewById(R.id.iv_epgReminder)
    }

    /** Daten an View binden */
    fun bind(epg: EpgDataOB, timelineStart: Long) {
        programData = epg
        titleView.text = epg.name
        subTitleView.text = epg.sub_title
        reminderIcon.visibility = if (epg.isRemembered) VISIBLE else INVISIBLE

        val calculateWidth = EpgUtil.convertMillisToPixel(epg.startTimestamp * 1000, epg.stopTimestamp * 1000)

        Log.d("CALCULATE EPG ITEMVIEW","${epg.name} = $calculateWidth")
        // Breite anhand der Programmdauer setzen
        val startTime = if (epg.startTimestamp * 1000 < timelineStart) timelineStart else epg.startTimestamp * 1000
        layoutParams = layoutParams.apply {
            width = EpgUtil.convertMillisToPixel(startTime, epg.stopTimestamp * 1000)
        }
        if (epg.isLiveShow) {
            val now = System.currentTimeMillis()
            val start = epg.startTimestamp * 1000
            val stop = epg.stopTimestamp * 1000
            progressBar.visibility = VISIBLE
            progressBar.progress = ((now - start) / (stop - start).toFloat() * 100).toInt()
        } else {
            progressBar.visibility = INVISIBLE
        }
    }

    private val EpgDataOB.isLiveShow: Boolean
        get() = startTimestamp < System.currentTimeMillis() / 1000 && stopTimestamp > System.currentTimeMillis() / 1000

}
