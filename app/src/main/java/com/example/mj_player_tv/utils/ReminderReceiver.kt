package com.example.mj_player_tv.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil.load
import com.example.mj_player_tv.MainActivity
import com.example.mj_player_tv.R
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Programme
import com.example.mj_player_tv.repository.HelpRepository
import com.example.mj_player_tv.repository.PlaylistUpdateRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val programmeId = intent.getLongExtra("programme_id", -1)

        if (programmeId == -1L) {
            Log.e("ReminderReceiver", "Ungültige ID")
            return
        }

        // ObjectBox-Datenbank abrufen
        val programmeBox = ObjectBox.store.boxFor(Programme::class.java)
        val programme = programmeBox.get(programmeId)

        if (programme != null) {
            showOverlay(context, programme)
        } else {
            Log.e("ReminderReceiver", "Programme nicht gefunden: $programmeId")
        }
    }

    @SuppressLint("InflateParams")
    private fun showOverlay(context: Context, programme: Programme) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,  // Breite
            WindowManager.LayoutParams.WRAP_CONTENT,  // Höhe
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        // Setze die Y-Position oben (0) und zentriere das Overlay horizontal
        layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        layoutParams.y = 0  // Positioniere das Overlay oben

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.overlay_reminder, null)

        val tvReminderText = view.findViewById<TextView>(R.id.tv_reminderText)
        val tvReminderTime = view.findViewById<TextView>(R.id.tv_reminderTime)
        val channelLogo = view.findViewById<ImageView>(R.id.iv_channelLogo)
        val closeBtn = view.findViewById<TextView>(R.id.iv_closeReminder)
        val playBtn = view.findViewById<TextView>(R.id.iv_playVideo)
        val tvchannelPos = programme.tvchannels.target
        val tvchannel = tvchannelPos.tvchannel.target
        val linkedEpgChannel = tvchannel.linkedEpgChannel?.target
        val image = tvchannel.logo
        val epgLogo = linkedEpgChannel?.icon?.firstOrNull()

        // Beispieltext setzen
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

        val title = programme.epgData.target?.name ?: "Program"
        val channel = tvchannel.showingName
        tvReminderText.text = "$title"
        tvReminderTime.text = "starts in 5mins on: $channel"

        // Schließen-Button
        var isRemoved = false

        closeBtn.setOnClickListener {
            if (!isRemoved) {
                windowManager.removeView(view)
                isRemoved = true
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isRemoved) {
                try {
                    windowManager.removeView(view)
                    isRemoved = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }, 10_000)

        // View anzeigen
        windowManager.addView(view, layoutParams)

        playBtn.post {
            playBtn.requestFocus()
        }

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
}