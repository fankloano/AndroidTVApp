package com.example.mj_player_tv.ui.epg

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.recyclerview.widget.RecyclerView

class CustomEpgHorizontalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalGridView(context, attrs, defStyleAttr) {

    var synchronizer: EpgScrollSynchronizer? = null

    init {
        windowAlignment = WINDOW_ALIGN_NO_EDGE
        windowAlignmentOffset = 0
        windowAlignmentOffsetPercent = WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
        itemAlignmentOffsetPercent = 0f
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        event ?: return super.dispatchKeyEvent(null)

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {

                if (!isScrollEnabled) isScrollEnabled = true

                    // 2. Finde das NEUE fokussierte Kind.
                    val focusedChildAfter = this.focusedChild

                    // 3. Wenn es ein neues fokussiertes Kind gibt:
                    if (focusedChildAfter != null) {

                        // Der Wert, um den die gesamte HGV verschoben werden MUSS,
                        // damit das fokussierte Element am linken Rand (Position 0) ist,
                        // ist die Negation der aktuellen linken Koordinate des Elements.
                        // (z.B. Element ist bei x=200, wir müssen um -200 scrollen)

                        val deltaToMove = focusedChildAfter.left

                        // Prüfen Sie, ob wir bereits an der gewünschten Stelle sind (delta=0)
                        if (deltaToMove != 0) {

                            // 4. 🔥 Wichtig: Scrollen Sie den Master selbst um dieses Delta.
                            // Dies wird den onScrolled-Listener zuverlässig mit dx=deltaToMove auslösen!
                            scrollBy(deltaToMove, 0)
                            synchronizer?.syncAllViewsToMaster(this, deltaToMove)
                            // Verhindern Sie, dass der Sync-Listener sofort auf diesen Scroll-Aufruf reagiert,
                            // falls Ihre isSyncScrolling Logik zu langsam ist.
                            // Aber der onScrolled Listener sollte das Scrollen jetzt zuverlässig starten.
                    }
                }
                // Wir müssen das Event nicht als "handled" markieren, da wir nur die Scroll-Action
                // erzwingen, nachdem das Framework den Fokus verschoben hat.
                true
            } else {
                false
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
