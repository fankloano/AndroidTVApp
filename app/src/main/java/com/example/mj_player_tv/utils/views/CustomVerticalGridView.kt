package com.example.mj_player_tv.utils.views

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.VerticalGridView

class CustomVerticalGridView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : VerticalGridView(context, attrs, defStyleAttr) {

    // Listener für Item-Selektierbarkeit
    private var onItemSelectableListener: ((position: Int) -> Boolean)? = null

    // Listener für Child-Fokuswechsel
    private var onChildFocusListener: ((oldChild: View?, newChild: View) -> Unit)? = null

    // Steuerungsflags
    private var fastScrollingEnabled = true
    private var cyclingEnabled = true
    private var smoothScrolling = true

    // intern: verhindert wiederholte FastScroll-Aktionen
    private var isFastScrolling = false

    init {
        // KeyEvent-Interceptor für DPAD-Steuerung
        setOnKeyInterceptListener { event ->
            handleKeyEvent(event)
        }
    }

    // --------- Public Setter ---------
    fun setOnItemSelectableListener(listener: ((position: Int) -> Boolean)?) {
        onItemSelectableListener = listener
    }

    fun setOnChildFocusListener(listener: ((oldChild: View?, newChild: View) -> Unit)?) {
        onChildFocusListener = listener
    }

    fun setFastScrollingEnabled(enabled: Boolean) {
        fastScrollingEnabled = enabled
    }

    fun setCyclingEnabled(enabled: Boolean) {
        cyclingEnabled = enabled
    }

    fun setSmoothScrolling(enabled: Boolean) {
        smoothScrolling = enabled
    }

    // --------- Fokus und KeyEvent Handling ---------
    override fun requestChildFocus(child: View, focused: View) {
        onChildFocusListener?.invoke(getFocusedChild(), child)
        super.requestChildFocus(child, focused)
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        if (adapter == null) return false
        val itemCount = adapter!!.itemCount
        if (itemCount < 2) return false

        val isUp = event.keyCode == KeyEvent.KEYCODE_DPAD_UP
        val isDown = event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
        val direction = if (isUp) -1 else if (isDown) 1 else 0
        if (direction == 0) return false

        val currentPos = selectedPosition
        var targetPos = currentPos + direction

        // Zyklisches Scrollen
        if (targetPos < 0) {
            targetPos = if (cyclingEnabled) itemCount - 1 else 0
        } else if (targetPos >= itemCount) {
            targetPos = if (cyclingEnabled) 0 else itemCount - 1
        }

        // Prüfen, ob Item selektierbar ist
        if (onItemSelectableListener?.invoke(targetPos) == false) {
            // überspringen bis nächstes selektierbares Item
            var tempPos = targetPos
            for (i in 0 until itemCount) {
                tempPos += direction
                if (tempPos < 0 || tempPos >= itemCount) break
                if (onItemSelectableListener?.invoke(tempPos) != false) {
                    targetPos = tempPos
                    break
                }
            }
        }

        // Scrollen
        if (smoothScrolling) {
            setSelectedPositionSmooth(targetPos)
        } else {
            setSelectedPosition(targetPos)
        }

        return true
    }

    // --------- Override addView für verschachtelte Views ---------
    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        try {
            super.addView(child, index, params)
        } catch (e: IllegalStateException) {
            // Falls View schon einen Parent hat
            (child.parent as? ViewGroup)?.removeView(child)
            super.addView(child, index, params)
        }
    }
}

